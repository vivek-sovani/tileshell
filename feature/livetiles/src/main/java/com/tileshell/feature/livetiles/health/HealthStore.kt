package com.tileshell.feature.livetiles.health

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The health hub's step history: one line per day (`epochDay<TAB>steps`) in `files/steps_history.txt`, the last
 * [KEEP_DAYS] days, on this phone only and not in backups. Writes happen on a background thread.
 */
object HealthStore {
    private const val FILE = "steps_history.txt"

    private val _history = MutableStateFlow<List<DaySteps>>(emptyList())
    val history: StateFlow<List<DaySteps>> = _history.asStateFlow()

    private const val HOURLY_FILE = "steps_hourly.txt"
    private val _hourly = MutableStateFlow(HourlySteps())

    /** Steps by hour for the last [KEEP_HOURLY_DAYS] days (a separate file, so an older install's history still reads). */
    val hourly: StateFlow<HourlySteps> = _hourly.asStateFlow()

    @Volatile private var loaded = false
    private val thread by lazy { HandlerThread("health-store").apply { start() } }
    private val handler by lazy { Handler(thread.looper) }

    /** Loads the file once per process (idempotent), off the main thread. */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val app = context.applicationContext
        handler.post {
            val lines = runCatching { File(app.filesDir, FILE).readLines() }.getOrDefault(emptyList())
            val read = lines.mapNotNull { l ->
                val f = l.split('\t')
                val day = f.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val steps = f.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                DaySteps(day, steps)
            }
            // Anything recorded while loading is kept.
            _history.value = read.fold(_history.value) { acc, d -> recordDay(acc, d.epochDay, d.steps) }
            val hourlyRead = runCatching { decodeHourly(File(app.filesDir, HOURLY_FILE).readText()) }.getOrDefault(HourlySteps())
            // A reading made while loading wins over the file for its own day.
            val now = _hourly.value
            _hourly.value = HourlySteps(hourlyRead.hours + now.hours, hourlyRead.lastTotals + now.lastTotals)
        }
    }

    /** Notes today's count (only ever raises a day; a no-op when nothing changes). */
    fun record(context: Context, epochDay: Long, steps: Int) {
        ensureLoaded(context)
        val app = context.applicationContext
        handler.post {
            val next = recordDay(_history.value, epochDay, steps)
            val nextHourly = recordHourly(_hourly.value, epochDay, hourIndex(System.currentTimeMillis()), steps)
            if (nextHourly != _hourly.value) {
                _hourly.value = nextHourly
                runCatching { File(app.filesDir, HOURLY_FILE).writeText(encodeHourly(nextHourly)) }
            }
            if (next == _history.value) return@post
            _history.value = next
            write(app, next)
        }
    }

    fun clear(context: Context) {
        val app = context.applicationContext
        handler.post {
            _history.value = emptyList()
            _hourly.value = HourlySteps()
            runCatching { File(app.filesDir, FILE).delete() }
            runCatching { File(app.filesDir, HOURLY_FILE).delete() }
        }
    }

    private fun write(context: Context, list: List<DaySteps>) {
        runCatching {
            val file = File(context.filesDir, FILE)
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(list.joinToString("\n") { "${it.epochDay}\t${it.steps}" })
            tmp.renameTo(file)
        }
    }
}
