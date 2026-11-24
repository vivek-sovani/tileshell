package com.tileshell.feature.livetiles.health

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

/**
 * The walk in progress. Owns the two sensor listeners and registers them only while a walk is running (not while
 * paused), so nothing listens otherwise. [WalkService] keeps the process in the foreground with its ongoing
 * notification while a walk runs, so counting goes on with the screen off. Finished walks are saved in
 * `files/walks.txt` (the last [KEEP_WALKS]) on this phone only.
 */
object WalkSession {
    private const val FILE = "walks.txt"
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
        ascent.restart()
        stepsAccum = 0
        activeBefore = 0L
        counterBase = null
        val now = System.currentTimeMillis()
        _state.value = WalkState(running = true, startMs = now, hasBarometer = hasBarometer(app))
        resume(app)
        runCatching { ContextCompat.startForegroundService(app, Intent(app, WalkService::class.java)) }
    }

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
        resumedAt = SystemClock.elapsedRealtime()
        register(app)
        _state.value = s.copy(paused = false)
    }

    /** Ends the walk, saves it (when it counted anything) and lets the service go. */
    fun stop(context: Context) {
        val app = context.applicationContext
        val s = _state.value
        if (!s.running) return
        val active = if (s.paused) activeBefore else activeBefore + SystemClock.elapsedRealtime() - resumedAt
        unregister(app)
        val end = System.currentTimeMillis()
        val walk = Walk(s.startMs, end, active, s.steps, s.floors)
        _state.value = WalkState()
        runCatching { app.stopService(Intent(app, WalkService::class.java)) }
        if (walk.steps > 0 || walk.activeMs > 60_000L) {
            handler.post {
                val next = addWalk(_walks.value, walk)
                _walks.value = next
                runCatching { File(app.filesDir, FILE).writeText(encodeWalks(next)) }
            }
        }
    }

    fun clearWalks(context: Context) {
        val app = context.applicationContext
        handler.post {
            _walks.value = emptyList()
            runCatching { File(app.filesDir, FILE).delete() }
        }
    }

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
                        counterBase = c
                        _state.value = _state.value.copy(steps = stepsAccum)
                    }
                    Sensor.TYPE_PRESSURE -> {
                        ascent.add(heightFromPressure(event.values[0]))
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
    }

    private fun unregister(context: Context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        listener?.let { runCatching { sm?.unregisterListener(it) } }
        listener = null
    }
}
