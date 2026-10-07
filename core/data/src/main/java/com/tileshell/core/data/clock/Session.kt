package com.tileshell.core.data.clock

import org.json.JSONArray
import org.json.JSONObject

/** One step of a running [Session]: what to call it and how long it lasts. */
data class SessionStep(val label: String, val ms: Long, val part: String = "")

/**
 * A running timer or timer set. Everything is an absolute clock time
 * ([stepEndsAt]) rather than a count that ticks, so it stays right however
 * long the phone sleeps; the alarm for [stepEndsAt] is what moves it on.
 * While paused, [pausedRemainingMs] holds what was left of the current step
 * and [stepEndsAt] means nothing.
 */
data class Session(
    val id: String,
    val title: String,
    val steps: List<SessionStep>,
    val index: Int,
    val stepEndsAt: Long,
    val pausedRemainingMs: Long? = null,
    val startedAt: Long = stepEndsAt,
) {
    val paused: Boolean get() = pausedRemainingMs != null

    /** A quick timer, as against a timer set. */
    val isTimer: Boolean get() = id.startsWith("timer-")
    val current: SessionStep get() = steps[index]
    val next: SessionStep? get() = steps.getOrNull(index + 1)

    /** What is left of the current step. */
    fun remainingMs(now: Long): Long = pausedRemainingMs ?: (stepEndsAt - now).coerceAtLeast(0L)

    /** What is left of the whole session: the current step and every step after it. */
    fun totalRemainingMs(now: Long): Long = remainingMs(now) + steps.drop(index + 1).sumOf { it.ms }

    fun totalMs(): Long = steps.sumOf { it.ms }
}

/** Starts a session whose first step begins at [now]. */
fun startSession(id: String, title: String, steps: List<SessionStep>, now: Long): Session? {
    val usable = steps.filter { it.ms > 0 }
    if (usable.isEmpty()) return null
    return Session(id, title, usable, index = 0, stepEndsAt = now + usable[0].ms, startedAt = now)
}

fun Session.pause(now: Long): Session = if (paused) this else copy(pausedRemainingMs = remainingMs(now))

fun Session.resume(now: Long): Session = pausedRemainingMs?.let { copy(stepEndsAt = now + it, pausedRemainingMs = null) } ?: this

/** The result of moving a session on: the session now (null when it has finished) and how many steps ended. */
data class Advance(val session: Session?, val stepsEnded: Int)

/**
 * Moves on past every step that has ended by [now] (the alarm can be late, or
 * the phone off). Each next step starts where the last one ended, so nothing
 * drifts. A paused session doesn't move. Pure.
 */
fun Session.advance(now: Long): Advance {
    if (paused || now < stepEndsAt) return Advance(this, 0)
    var i = index
    var endsAt = stepEndsAt
    var ended = 0
    while (now >= endsAt) {
        ended++
        i++
        if (i >= steps.size) return Advance(null, ended)
        endsAt += steps[i].ms
    }
    return Advance(copy(index = i, stepEndsAt = endsAt), ended)
}

/** Skips the current step: the next one starts now (paused stays paused at its full length); null when it was the last. */
fun Session.skip(now: Long): Session? {
    val i = index + 1
    if (i >= steps.size) return null
    return if (paused) copy(index = i, pausedRemainingMs = steps[i].ms) else copy(index = i, stepEndsAt = now + steps[i].ms)
}

fun encodeSessions(sessions: List<Session>): String = JSONArray().also { arr ->
    sessions.forEach { s ->
        arr.put(
            JSONObject().put("id", s.id).put("title", s.title).put("i", s.index).put("end", s.stepEndsAt).put("start", s.startedAt)
                .apply { s.pausedRemainingMs?.let { put("paused", it) } }
                .put("steps", JSONArray().also { sa -> s.steps.forEach { sa.put(JSONObject().put("l", it.label).put("ms", it.ms).put("p", it.part)) } }),
        )
    }
}.toString()

fun decodeSessions(raw: String?): List<Session> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { n ->
            val o = arr.optJSONObject(n) ?: return@mapNotNull null
            val steps = o.optJSONArray("steps")?.let { sa ->
                (0 until sa.length()).mapNotNull { k -> sa.optJSONObject(k)?.let { SessionStep(it.optString("l"), it.optLong("ms"), it.optString("p")) } }
            }.orEmpty()
            val index = o.optInt("i")
            if (steps.isEmpty() || index !in steps.indices) return@mapNotNull null
            Session(
                id = o.optString("id").ifEmpty { return@mapNotNull null },
                title = o.optString("title"),
                steps = steps,
                index = index,
                stepEndsAt = o.optLong("end"),
                pausedRemainingMs = if (o.has("paused")) o.optLong("paused") else null,
                startedAt = o.optLong("start"),
            )
        }
    }.getOrDefault(emptyList())
}
