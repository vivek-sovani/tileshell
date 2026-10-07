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
        }
    }

    /** Notes today's count (only ever raises a day; a no-op when nothing changes). */
    fun record(context: Context, epochDay: Long, steps: Int) {
        ensureLoaded(context)
        val app = context.applicationContext
        handler.post {
            val next = recordDay(_history.value, epochDay, steps)
            if (next == _history.value) return@post
            _history.value = next
            write(app, next)
        }
    }

    fun clear(context: Context) {
        val app = context.applicationContext
        handler.post {
            _history.value = emptyList()
            runCatching { File(app.filesDir, FILE).delete() }
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
