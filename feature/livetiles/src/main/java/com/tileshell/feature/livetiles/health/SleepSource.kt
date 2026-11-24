package com.tileshell.feature.livetiles.health

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.tileshell.feature.livetiles.BatterySample
import com.tileshell.feature.livetiles.UsageAccess
import java.time.LocalDate

/** App-resume times from the usage events (empty without usage access). */
internal fun resumeTimes(context: Context, from: Long, to: Long): List<Long> = runCatching {
    val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
    val events = usm.queryEvents(from, to) ?: return emptyList()
    val e = UsageEvents.Event()
    buildList { while (events.hasNextEvent()) { events.getNextEvent(e); if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) add(e.timeStamp) } }
}.getOrDefault(emptyList())

/** Last night's estimate from the battery log and, when granted, usage events. Call off the main thread. */
internal fun lastNightEstimate(context: Context, samples: List<BatterySample>, nowMs: Long = System.currentTimeMillis()): SleepEstimate? {
    val use = screenOnSpans(samples, nowMs) +
        if (UsageAccess.isGranted(context)) usageMoments(resumeTimes(context, nowMs - 2L * 86_400_000L, nowMs)) else emptyList()
    return nightEstimate(use, LocalDate.now().toEpochDay(), nowMs)
}
