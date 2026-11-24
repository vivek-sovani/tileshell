package com.tileshell.feature.livetiles.health

import com.tileshell.core.data.TileSize
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Pure decisions of the health tile: its layout class, which back faces have something to say, the walk confirm flow. */

enum class HealthTileKind { SMALL, MEDIUM, WIDE, LARGE }

/** Small and one-column shapes keep the small face; wide and large show everything at once (no flip). */
fun healthTileKind(size: TileSize): HealthTileKind = when {
    size == TileSize.SMALL || size.cols == 1 -> HealthTileKind.SMALL
    size.cols >= 3 && size.rows >= 3 -> HealthTileKind.LARGE
    size.cols >= 4 -> HealthTileKind.WIDE
    else -> HealthTileKind.MEDIUM
}

enum class BackFace { SLEEP, AIR, LAST_WALK, APPS }

data class BackInputs(val sleep: Boolean, val air: Boolean, val lastWalk: Boolean, val apps: Boolean)

/** The back faces that have something to say, in turn order: sleep, air, last walk, apps. */
fun backFaces(i: BackInputs): List<BackFace> = buildList {
    if (i.sleep) add(BackFace.SLEEP)
    if (i.air) add(BackFace.AIR)
    if (i.lastWalk) add(BackFace.LAST_WALK)
    if (i.apps) add(BackFace.APPS)
}

/** The face for the [turn]th flip to the back (round robin over [faces]); null when there is none. */
fun backFaceFor(faces: List<BackFace>, turn: Int): BackFace? = if (faces.isEmpty()) null else faces[Math.floorMod(turn, faces.size)]

/** Sleep shows from waking (the estimate has ended) until noon, for the night that ended this morning. */
fun sleepFaceEligible(est: SleepEstimate?, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    if (est == null || est.end > nowMs) return false
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    val end = Instant.ofEpochMilli(est.end).atZone(zone)
    return now.hour < 12 && end.toLocalDate() == now.toLocalDate()
}

/** Air shows only when it is worth a look: AQI moderate or worse (51+), or UV 6+. */
fun airFaceEligible(aqi: Int?, uv: Double?): Boolean = (aqi ?: 0) >= 51 || (uv ?: 0.0) >= 6.0

/** A walk today or yesterday. */
fun lastWalkEligible(walks: List<Walk>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Walk? {
    val last = walks.maxByOrNull { it.startMs } ?: return null
    val day = Instant.ofEpochMilli(last.startMs).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return last.takeIf { day == today || day == today.minusDays(1) }
}

/** "5 pm" for the hour 0..23, as in "walk after 5 pm". */
fun hourLabel(hour: Int): String = "${if (hour % 12 == 0) 12 else hour % 12} ${if (hour % 24 < 12) "am" else "pm"}"

/** "86 moderate" with the long "for sensitive groups" shortened. */
fun airBandShort(aqi: Int): String = "$aqi ${aqiBand(aqi).label.substringBefore(" for")}"

fun streakLabel(days: Int): String? = when {
    days <= 0 -> null
    days == 1 -> "1 day at goal"
    else -> "$days days at goal"
}

/** "24 min · 4 floors" (floors left out when none). */
fun walkTimeFloors(w: Walk): String = buildList {
    add(spokenDuration(w.activeMs))
    if (w.floors > 0) add("${w.floors} floor${if (w.floors == 1) "" else "s"}")
}.joinToString(" · ")

fun plusSteps(steps: Int): String = String.format(Locale.US, "+%,d steps", steps)

/** What the tile face shows over the walk: nothing, the "stop this walk?" question, or the saved summary for [SAVED_MS]. */
sealed interface WalkUi {
    data object None : WalkUi
    data object Confirm : WalkUi
    data class Saved(val walk: Walk, val atMs: Long) : WalkUi
}

const val SAVED_MS = 10_000L

sealed interface WalkUiEvent {
    data object StopTapped : WalkUiEvent
    data object KeepGoing : WalkUiEvent
    data class SaveTapped(val walk: Walk?, val nowMs: Long) : WalkUiEvent
    /** A clock tick or the walk ending elsewhere (the notification, the hub): [running] is the walk's state now. */
    data class Tick(val nowMs: Long, val running: Boolean) : WalkUiEvent
}

fun reduceWalkUi(state: WalkUi, e: WalkUiEvent): WalkUi = when (e) {
    WalkUiEvent.StopTapped -> if (state is WalkUi.None) WalkUi.Confirm else state
    WalkUiEvent.KeepGoing -> if (state is WalkUi.Confirm) WalkUi.None else state
    is WalkUiEvent.SaveTapped -> if (state is WalkUi.Confirm) (if (e.walk != null) WalkUi.Saved(e.walk, e.nowMs) else WalkUi.None) else state
    is WalkUiEvent.Tick -> when {
        // The walk ended elsewhere while asking: nothing to confirm any more.
        state is WalkUi.Confirm && !e.running -> WalkUi.None
        state is WalkUi.Saved && e.nowMs - state.atMs >= SAVED_MS -> WalkUi.None
        // A new walk begun while the summary shows replaces it.
        state is WalkUi.Saved && e.running -> WalkUi.None
        else -> state
    }
}

/** Days at goal for the last-walk face, from the history including today's live count. */
fun daysAtGoalLabel(history: List<DaySteps>, today: Long, liveSteps: Int?, goal: Int): String? =
    streakLabel(streak(if (liveSteps != null) recordDay(history, today, liveSteps) else history, today, goal))

