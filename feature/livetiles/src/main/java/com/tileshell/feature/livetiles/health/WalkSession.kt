package com.tileshell.feature.livetiles.health

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.tileshell.feature.livetiles.Units
import java.io.File

/** What the walk page shows. */
data class WalkState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val startMs: Long = 0L,
    val activeMs: Long = 0L,
    val steps: Int = 0,
    val floors: Int = 0,
    /** Whether this phone has a barometer; floors are hidden without one. */
    val hasBarometer: Boolean = false,
    /** Distance so far in metres: the GPS route when one is recording, else the step estimate. */
    val meters: Int = 0,
    /** A GPS route is being recorded. */
    val gps: Boolean = false,
)

/**
 * The walk in progress. Owns the two sensor listeners and registers them only while a walk is running (not while
 * paused), so nothing listens otherwise. [WalkService] keeps the process in the foreground with its ongoing
 * notification while a walk runs, so counting goes on with the screen off. Finished walks are saved in
 * `files/walks.txt` (the last [KEEP_WALKS]) on this phone only.
 */
object WalkSession {
    private const val FILE = "walks.txt"
    private const val MOVING_WINDOW_MS = 8_000L
    private val _state = MutableStateFlow(WalkState())
    val state: StateFlow<WalkState> = _state.asStateFlow()
    private val _walks = MutableStateFlow<List<Walk>>(emptyList())
    val walks: StateFlow<List<Walk>> = _walks.asStateFlow()

    private val thread by lazy { HandlerThread("health-walk").apply { start() } }
    private val handler by lazy { Handler(thread.looper) }
    private val ascent = AscentTracker()
    private var counterBase: Float? = null
    private var stepsAccum = 0
    private var activeBefore = 0L
    private var resumedAt = 0L
    private var listener: SensorEventListener? = null
    @Volatile private var walksLoaded = false
    private var route = RouteTracker()
    private val splits = ArrayList<Long>()
    private var intervalM = 1000.0
    private var nextMarkM = 1000.0
    private var lastStepAt = 0L
    private var heightCm = DEFAULT_HEIGHT_CM
    private var kmAlert = 2
    private var wantRoute = false
    private var announcer: WalkAnnouncer? = null
    private var locationListener: LocationListener? = null

    fun loadWalks(context: Context) {
        if (walksLoaded) return
        walksLoaded = true
        val app = context.applicationContext
        handler.post { _walks.value = runCatching { decodeWalks(File(app.filesDir, FILE).readText()) }.getOrDefault(emptyList()) }
    }

    fun canWalk(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED &&
            (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun hasBarometer(context: Context): Boolean =
        (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    fun start(context: Context) {
        val app = context.applicationContext
        if (_state.value.running || !canWalk(app)) return
        loadWalks(app)
        ascent.restart()
        stepsAccum = 0
        activeBefore = 0L
        counterBase = null
        route = RouteTracker()
        splits.clear()
        lastStepAt = 0L
        val settings = HealthPrefs.current(app)
        heightCm = settings.heightCm
        kmAlert = settings.kmAlert
        wantRoute = settings.recordRoute && canRoute(app)
        intervalM = if (Units.isImperial) 1609.344 else 1000.0
        nextMarkM = intervalM
        if (kmAlert > 0) announcer = WalkAnnouncer(app)
        val now = System.currentTimeMillis()
        _state.value = WalkState(running = true, startMs = now, hasBarometer = hasBarometer(app))
        resume(app)
        runCatching { ContextCompat.startForegroundService(app, Intent(app, WalkService::class.java)) }
    }

    /** Fine location is granted, so a route can be recorded. */
    fun canRoute(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun pause(context: Context) {
        val s = _state.value
        if (!s.running || s.paused) return
        activeBefore += SystemClock.elapsedRealtime() - resumedAt
        unregister(context.applicationContext)
        _state.value = s.copy(paused = true, activeMs = activeBefore)
    }

    fun resume(context: Context) {
        val app = context.applicationContext
        val s = _state.value
        if (!s.running) return
        counterBase = null
        ascent.restart()
        route.breakSegment()
        resumedAt = SystemClock.elapsedRealtime()
        register(app)
        _state.value = s.copy(paused = false)
    }

    /** Ends the walk, saves it (when it counted anything) and lets the service go. */
    fun stop(context: Context): Walk? {
        val app = context.applicationContext
        val s = _state.value
        if (!s.running) return null
        val active = if (s.paused) activeBefore else activeBefore + SystemClock.elapsedRealtime() - resumedAt
        unregister(app)
        announcer?.shutdown()
        announcer = null
        val end = System.currentTimeMillis()
        val points = route.points.toList()
        val hasRoute = points.size >= 2
        val walk = Walk(s.startMs, end, active, s.steps, s.floors, s.meters, splits.toList(), hasRoute)
        _state.value = WalkState()
        runCatching { app.stopService(Intent(app, WalkService::class.java)) }
        if (walk.steps > 0 || walk.activeMs > 60_000L) {
            // Shown at once (a list read right after stop sees it); the files follow on the worker thread.
            val before = _walks.value
            val next = addWalk(before, walk)
            _walks.value = next
            val dropped = (before + walk).filter { w -> next.none { it.startMs == w.startMs } }
            handler.post {
                runCatching { File(app.filesDir, FILE).writeText(encodeWalks(next)) }
                if (hasRoute && next.any { it.startMs == walk.startMs }) {
                    runCatching { routeFile(app.filesDir, walk.startMs).writeText(encodeRoute(points)) }
                }
                dropped.forEach { runCatching { routeFile(app.filesDir, it.startMs).delete() } }
            }
        }
        return walk
    }

    fun clearWalks(context: Context) {
        val app = context.applicationContext
        handler.post {
            _walks.value.forEach { runCatching { routeFile(app.filesDir, it.startMs).delete() } }
            _walks.value = emptyList()
            runCatching { File(app.filesDir, FILE).delete() }
        }
    }

    /** Removes one saved walk and its route. */
    fun deleteWalk(context: Context, startMs: Long) {
        val app = context.applicationContext
        handler.post {
            val next = _walks.value.filter { it.startMs != startMs }
            _walks.value = next
            runCatching { File(app.filesDir, FILE).writeText(encodeWalks(next)) }
            runCatching { routeFile(app.filesDir, startMs).delete() }
        }
    }

    /** The route saved with a walk (read off the main thread). */
    fun loadRoute(context: Context, startMs: Long): List<RoutePoint> =
        runCatching { decodeRoute(routeFile(context.applicationContext.filesDir, startMs).readText()) }.getOrDefault(emptyList())

    /** The elapsed walking time right now, for the ticking timer. */
    fun elapsedMs(): Long {
        val s = _state.value
        return if (!s.running) 0L else if (s.paused) activeBefore else activeBefore + SystemClock.elapsedRealtime() - resumedAt
    }

    private fun register(context: Context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_STEP_COUNTER -> {
                        val c = event.values.firstOrNull() ?: return
                        val base = counterBase
                        // The first reading after a start or resume is the starting point; a lower one is a reboot.
                        if (base != null && c >= base) stepsAccum += (c - base).toInt()
                        if (base != null && c > base) lastStepAt = SystemClock.elapsedRealtime()
                        counterBase = c
                        _state.value = _state.value.copy(steps = stepsAccum)
                        updateDistance()
                    }
                    Sensor.TYPE_PRESSURE -> {
                        // Climbing only counts while the walker is stepping: a lift or the weather is not floors.
                        ascent.add(heightFromPressure(event.values[0]), SystemClock.elapsedRealtime() - lastStepAt < MOVING_WINDOW_MS)
                        val f = ascent.floors
                        if (f != _state.value.floors) _state.value = _state.value.copy(floors = f)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = l
        sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let { runCatching { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_NORMAL, 0, handler) } }
        sm.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { runCatching { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_NORMAL, 0, handler) } }
        if (wantRoute) registerLocation(context)
    }

    /** Distance now, and a beep (and the time said) at each whole kilometre (mile). */
    private fun updateDistance() {
        val s = _state.value
        val gps = route.meters > 0
        val raw = if (gps) route.meters else s.steps * strideMeters(heightCm)
        val m = maxOf(raw, s.meters.toDouble())
        if (m.toInt() != s.meters || gps != s.gps) _state.value = s.copy(meters = m.toInt(), gps = gps)
        while (m >= nextMarkM) {
            val n = splits.size + 1
            val now = elapsedMs()
            splits += now
            nextMarkM += intervalM
            val last = splits.getOrNull(splits.size - 2) ?: 0L
            announcer?.announce(kmAnnouncement(n, now - last, now, Units.isImperial), kmAlert >= 2)
        }
    }

    @Suppress("MissingPermission")
    private fun registerLocation(context: Context) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (route.add(location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy else 50f, location.time)) updateDistance()
            }

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        locationListener = l
        runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3_000L, 3f, l, handler.looper) }
    }

    private fun unregister(context: Context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        listener?.let { runCatching { sm?.unregisterListener(it) } }
        listener = null
        locationListener?.let { l -> runCatching { (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.removeUpdates(l) } }
        locationListener = null
    }
}
