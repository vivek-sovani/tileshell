package com.tileshell.feature.livetiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * One reading of the battery. [currentMa] is the draw in mA (positive =
 * discharging, negative = charging) when the device reports it, else null.
 */
data class BatterySample(
    val time: Long,
    val level: Int,
    val charging: Boolean,
    val screenOn: Boolean,
    val currentMa: Int? = null,
)

/** One line per sample: `time,level,charging,screenOn,currentMa`. Pure. */
fun encodeBatterySample(s: BatterySample): String =
    "${s.time},${s.level},${if (s.charging) 1 else 0},${if (s.screenOn) 1 else 0},${s.currentMa ?: ""}"

/** Parses one [encodeBatterySample] line; null for anything malformed. Pure. */
fun decodeBatterySample(line: String): BatterySample? {
    val p = line.trim().split(',')
    if (p.size < 4) return null
    val time = p[0].toLongOrNull() ?: return null
    val level = p[1].toIntOrNull()?.takeIf { it in 0..100 } ?: return null
    return BatterySample(time, level, p[2] == "1", p[3] == "1", p.getOrNull(4)?.toIntOrNull())
}

/** How long the log keeps samples: a week, plus a day of slack. */
const val BATTERY_HISTORY_KEEP_MS = 8L * 24 * 60 * 60 * 1000

/**
 * The samples since the phone was last unplugged: everything after the last
 * charging sample (or the whole log when it has never been seen charging).
 * Empty while charging. Pure.
 */
fun samplesSinceUnplug(samples: List<BatterySample>): List<BatterySample> {
    if (samples.isEmpty() || samples.last().charging) return emptyList()
    val lastCharging = samples.indexOfLast { it.charging }
    return samples.drop(lastCharging + 1)
}

/**
 * Recent drain in % per hour, from the discharging samples in the last
 * [windowMs]. Null until there's at least 30 minutes and a 1% drop to go on,
 * so a just-unplugged phone doesn't show a wild guess. Pure.
 */
fun drainRatePerHour(samples: List<BatterySample>, nowMillis: Long, windowMs: Long = 3 * 3_600_000L): Double? {
    val recent = samplesSinceUnplug(samples).filter { it.time >= nowMillis - windowMs }
    if (recent.size < 2) return null
    val first = recent.first()
    val last = recent.last()
    val hours = (last.time - first.time) / 3_600_000.0
    val drop = first.level - last.level
    if (hours < 0.5 || drop < 1) return null
    return drop / hours
}

/** Hours until empty at [ratePerHour], or null without a rate. Pure. */
fun hoursLeft(level: Int, ratePerHour: Double?): Double? =
    ratePerHour?.takeIf { it > 0 }?.let { level / it }

/** "about 9 h left" / "about 45 min left". Pure. */
fun timeLeftLabel(hours: Double?): String? = when {
    hours == null -> null
    hours >= 1.5 -> "about ${hours.toInt()} h left"
    else -> "about ${(hours * 60).toInt().coerceAtLeast(1)} min left"
}

/**
 * The most recent stretch on battery: [samplesSinceUnplug] while discharging,
 * or, while charging, the run that ended when it was plugged in — so the split
 * still says something useful on the charger. Pure.
 */
fun lastDischargeRun(samples: List<BatterySample>): List<BatterySample> {
    if (samples.isEmpty()) return emptyList()
    if (!samples.last().charging) return samplesSinceUnplug(samples)
    val end = samples.indexOfLast { !it.charging }
    if (end < 0) return emptyList()
    val head = samples.subList(0, end + 1)
    val start = head.indexOfLast { it.charging } + 1
    // Include the first charging sample, so the run ends where plugging in did.
    return samples.subList(start, end + 2)
}

/** How the battery was used since unplugging, split by screen state. */
data class ScreenSplit(val onMillis: Long, val onDrop: Int, val offMillis: Long, val offDrop: Int)

/**
 * Splits the discharge since unplugging by screen state: each gap between two
 * samples counts toward the screen state of the earlier one (screen on/off is
 * logged as it happens, so that's the state for the whole gap), up to [nowMillis]
 * for the last. Pure.
 */
fun screenSplit(samples: List<BatterySample>, nowMillis: Long): ScreenSplit {
    val run = lastDischargeRun(samples)
    var onMs = 0L; var offMs = 0L; var onDrop = 0; var offDrop = 0
    run.forEachIndexed { i, s ->
        val next = run.getOrNull(i + 1)
        if (s.charging) return@forEachIndexed
        val span = ((next?.time ?: nowMillis) - s.time).coerceAtLeast(0)
        val drop = ((s.level - (next?.level ?: s.level))).coerceAtLeast(0)
        if (s.screenOn) { onMs += span; onDrop += drop } else { offMs += span; offDrop += drop }
    }
    return ScreenSplit(onMs, onDrop, offMs, offDrop)
}

/**
 * Average drain in % per hour with the screen on and off, over the whole log
 * (every gap between two discharging samples, by the earlier one's screen
 * state). Null for a state with under 15 minutes of data. Pure.
 */
fun screenRates(samples: List<BatterySample>): Pair<Double?, Double?> {
    var onMs = 0L; var offMs = 0L; var onDrop = 0; var offDrop = 0
    samples.zipWithNext().forEach { (a, b) ->
        if (a.charging || b.charging) return@forEach
        val span = b.time - a.time
        if (span <= 0 || span > 3 * 3_600_000L) return@forEach
        val drop = (a.level - b.level).coerceAtLeast(0)
        if (a.screenOn) { onMs += span; onDrop += drop } else { offMs += span; offDrop += drop }
    }
    fun rate(ms: Long, drop: Int) = if (ms < 15 * 60_000L) null else drop / (ms / 3_600_000.0)
    return rate(onMs, onDrop) to rate(offMs, offDrop)
}

/**
 * % used per calendar day (device-local) over the last [days] days, oldest
 * first: the sum of every drop between consecutive discharging samples that
 * started that day. Pure given [nowMillis].
 */
fun dailyUsage(samples: List<BatterySample>, nowMillis: Long, days: Int = 7): List<Pair<Long, Int>> {
    val dayStarts = (days - 1 downTo 0).map { startOfDay(nowMillis, -it) }
    val totals = IntArray(days)
    samples.zipWithNext().forEach { (a, b) ->
        if (a.charging || b.charging) return@forEach
        val drop = a.level - b.level
        if (drop <= 0) return@forEach
        val index = dayStarts.indexOfLast { a.time >= it }
        if (index >= 0) totals[index] += drop
    }
    return dayStarts.mapIndexed { i, start -> start to totals[i] }
}

private fun startOfDay(nowMillis: Long, offsetDays: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, offsetDays)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** Normalises BatteryManager's current reading to mA, positive = draining.
 * Devices disagree on units (µA per the docs, mA on many Samsungs) and sign. Pure. */
fun normaliseCurrentMa(raw: Int, charging: Boolean): Int? {
    if (raw == 0 || raw == Int.MIN_VALUE) return null
    val ma = if (abs(raw) > 20_000) raw / 1000 else raw
    return if (charging) -abs(ma) else abs(ma)
}

/**
 * The phone's battery log, and what records into it. Recording happens on
 * every 1% level change, screen on/off and plug/unplug (a receiver on the
 * application context, alive while TileShell's process is — it's the home
 * screen and holds the notification listener), plus [BatteryLogWorker] every
 * 15 minutes as a fallback. No network, no wake locks; one short line per event.
 */
object BatteryLog {
    private const val FILE = "battery_log.txt"
    private val _samples = MutableStateFlow<List<BatterySample>>(emptyList())

    /** The log, oldest first; loaded on first [ensureStarted]. */
    val samples: StateFlow<List<BatterySample>> = _samples.asStateFlow()

    @Volatile private var started = false
    private val lock = Any()

    // Loading, the broadcast receiver and every file write run on this thread,
    // never the main thread (the battery broadcast is frequent while charging).
    private val thread by lazy { HandlerThread("tileshell-battery-log").apply { start() } }
    private val handler by lazy { Handler(thread.looper) }

    /** Starts recording for this process (idempotent, returns at once). */
    fun ensureStarted(context: Context) {
        if (started) return
        synchronized(lock) {
            if (started) return
            started = true
        }
        val app = context.applicationContext
        handler.post {
            synchronized(lock) { _samples.value = load(app) }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    when (intent.action) {
                        Intent.ACTION_BATTERY_CHANGED -> record(app, intent, onlyIfChanged = true)
                        else -> record(app, null, onlyIfChanged = false)
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            runCatching { app.registerReceiver(receiver, filter, null, handler) }
            BatteryLogWorker.ensureScheduled(app)
        }
    }

    /**
     * Appends a reading. With [onlyIfChanged] (the frequent battery broadcast),
     * only a new level or charging state is written — otherwise that broadcast
     * would log every voltage or temperature tick.
     */
    fun record(context: Context, batteryIntent: Intent? = null, onlyIfChanged: Boolean = false) {
        val app = context.applicationContext
        val sticky = batteryIntent ?: runCatching { app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val sample = readSample(app, sticky) ?: return
        synchronized(lock) {
            val current = _samples.value
            val last = current.lastOrNull()
            if (onlyIfChanged && last != null && last.level == sample.level && last.charging == sample.charging) return
            val pruned = current.filter { it.time >= sample.time - BATTERY_HISTORY_KEEP_MS }
            val next = pruned + sample
            _samples.value = next
            runCatching {
                val file = File(app.filesDir, FILE)
                if (pruned.size != current.size) {
                    file.writeText(next.joinToString("\n", postfix = "\n") { encodeBatterySample(it) })
                } else {
                    file.appendText(encodeBatterySample(sample) + "\n")
                }
            }
        }
    }

    private fun readSample(context: Context, sticky: Intent?): BatterySample? {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = runCatching { bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }.getOrNull()
            ?.takeIf { it in 0..100 } ?: return null
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val charging = plugged || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val screenOn = runCatching { context.getSystemService(PowerManager::class.java)?.isInteractive }.getOrNull() ?: false
        val raw = runCatching { bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrNull() ?: 0
        return BatterySample(System.currentTimeMillis(), level, charging, screenOn, normaliseCurrentMa(raw, charging))
    }

    private fun load(context: Context): List<BatterySample> = runCatching {
        val cutoff = System.currentTimeMillis() - BATTERY_HISTORY_KEEP_MS
        File(context.filesDir, FILE).takeIf { it.exists() }?.readLines()
            ?.mapNotNull { decodeBatterySample(it) }
            ?.filter { it.time >= cutoff }
            ?.sortedBy { it.time }
    }.getOrNull().orEmpty()
}

/** The 15-minute fallback recording, for when no screen or battery event
 * happens for a while. Local only; no constraints. */
class BatteryLogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        BatteryLog.ensureStarted(applicationContext)
        BatteryLog.record(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "tileshell_battery_log"

        fun ensureScheduled(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BatteryLogWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}
