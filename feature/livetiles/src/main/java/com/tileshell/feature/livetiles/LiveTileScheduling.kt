package com.tileshell.feature.livetiles

import kotlinx.coroutines.delay

/**
 * Waits until the next wall-clock instant that's a multiple of [intervalMs] —
 * e.g. every tile polling on the same [intervalMs] wakes at the same
 * real-world moments (every :00/:01:00/... for a 60 s interval), instead of
 * each tile's own loop drifting off whenever it happened to first mount.
 * This is what "refresh every tile in a category together" (Personalize's
 * "live data refresh") actually means in practice — one coalesced wave of
 * network calls instead of several independently-timed ones, which is what
 * actually costs battery (each separate wake promotes the radio out of
 * idle), not the data transferred.
 */
internal suspend fun delayUntilNextRefresh(intervalMs: Long) {
    val now = System.currentTimeMillis()
    delay(intervalMs - (now % intervalMs))
}
