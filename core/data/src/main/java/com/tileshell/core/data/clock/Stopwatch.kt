package com.tileshell.core.data.clock

/**
 * A stopwatch as plain numbers: [runningSince] (epoch ms, null when stopped),
 * the time gathered before that ([accumulatedMs]) and the lap marks (elapsed
 * ms at each press, oldest first). Absolute times again, so it keeps counting
 * while the hub is closed and the app is gone.
 */
data class StopwatchState(
    val runningSince: Long? = null,
    val accumulatedMs: Long = 0L,
    val laps: List<Long> = emptyList(),
) {
    fun elapsedMs(now: Long): Long = accumulatedMs + (runningSince?.let { (now - it).coerceAtLeast(0L) } ?: 0L)
    val running: Boolean get() = runningSince != null
}

fun StopwatchState.start(now: Long): StopwatchState = if (running) this else copy(runningSince = now)

fun StopwatchState.stop(now: Long): StopwatchState =
    if (running) copy(runningSince = null, accumulatedMs = elapsedMs(now)) else this

fun StopwatchState.lap(now: Long): StopwatchState = if (running) copy(laps = laps + elapsedMs(now)) else this

fun StopwatchState.reset(): StopwatchState = StopwatchState()

/** Each lap's own length (the gap since the one before). */
fun StopwatchState.lapLengths(): List<Long> = laps.mapIndexed { i, at -> at - (laps.getOrNull(i - 1) ?: 0L) }

/** "00:12.43" below an hour, "1:02:03.40" above. */
fun formatStopwatch(ms: Long): String {
    val total = ms.coerceAtLeast(0L)
    val hundredths = (total % 1000) / 10
    val s = total / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, sec, hundredths) else "%02d:%02d.%02d".format(m, sec, hundredths)
}

fun encodeStopwatch(s: StopwatchState): String = listOf(s.runningSince ?: -1L, s.accumulatedMs).joinToString(",") + "|" + s.laps.joinToString(",")

fun decodeStopwatch(raw: String?): StopwatchState {
    if (raw.isNullOrBlank()) return StopwatchState()
    return runCatching {
        val (head, laps) = raw.split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val nums = head.split(",").map { it.toLong() }
        StopwatchState(
            runningSince = nums[0].takeIf { it >= 0 },
            accumulatedMs = nums[1],
            laps = laps.split(",").filter { it.isNotBlank() }.map { it.toLong() },
        )
    }.getOrDefault(StopwatchState())
}
