package com.tileshell.core.data.clock

import org.json.JSONArray
import org.json.JSONObject

/**
 * One step of a timer set. A [remainder] step takes whatever is left of its
 * part's total once the other steps are counted (a 21 minute kriya of 6, 5, 4
 * and "the rest" leaves 6), so changing the total only changes that step.
 */
data class TimerStep(val name: String = "", val seconds: Int = 0, val remainder: Boolean = false)

/** A part of a set ("preparatory", "kriya"): its steps, and the total the [TimerStep.remainder] step fills, if it has one. */
data class TimerPart(val name: String, val steps: List<TimerStep>, val totalSeconds: Int? = null)

/** A saved sequence of timers for a practice or workout. */
data class TimerSet(val id: String, val name: String, val parts: List<TimerPart>)

/** A step as the session runs it. [label] is "kriya · step 2 of 4", [sub] what comes after it. */
data class FlatStep(val label: String, val partName: String, val ms: Long)

/**
 * The steps of [set] in running order, with the remainder step sized (never
 * below zero) and zero-length steps left out. Pure.
 */
fun flatten(set: TimerSet): List<FlatStep> = set.parts.flatMap { part ->
    val resolved = resolveSeconds(part)
    val counted = part.steps.indices.filter { resolved[it] > 0 }
    counted.mapIndexed { n, i ->
        val step = part.steps[i]
        // "kriya · step 2 of 4" for an unnamed step; a named one reads "kriya · inhale".
        val stepText = if (step.name.isBlank()) "step ${n + 1} of ${counted.size}" else step.name
        FlatStep(
            label = listOf(part.name.trim(), stepText).filter { it.isNotEmpty() }.joinToString(" · "),
            partName = part.name,
            ms = resolved[i] * 1000L,
        )
    }
}

/** Each step's seconds in [part], the remainder step filled in. */
internal fun resolveSeconds(part: TimerPart): List<Int> {
    val fixed = part.steps.filter { !it.remainder }.sumOf { it.seconds }
    val rest = ((part.totalSeconds ?: 0) - fixed).coerceAtLeast(0)
    return part.steps.map { if (it.remainder) rest else it.seconds }
}

fun totalMs(set: TimerSet): Long = flatten(set).sumOf { it.ms }

/** "29:00" / "1:05:00": a length for lists and the editor. */
fun formatDuration(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/**
 * What was typed in a duration field, in seconds: "6" is six minutes, "6:30"
 * six and a half, "1:05:00" an hour and five minutes, "90s" ninety seconds.
 * Null for nothing usable.
 */
fun parseDuration(text: String): Int? {
    val t = text.trim().lowercase()
    if (t.isEmpty()) return null
    if (t.endsWith("s") && t.dropLast(1).toIntOrNull() != null) return t.dropLast(1).toInt().takeIf { it >= 0 }
    val parts = t.split(":")
    if (parts.size > 3 || parts.any { it.isBlank() || it.toIntOrNull() == null || it.toInt() < 0 }) return null
    val n = parts.map { it.toInt() }
    return when (n.size) {
        1 -> n[0] * 60
        2 -> n[0] * 60 + n[1]
        else -> n[0] * 3600 + n[1] * 60 + n[2]
    }
}

fun encodeTimerSets(sets: List<TimerSet>): String = JSONArray().also { arr ->
    sets.forEach { set ->
        arr.put(
            JSONObject().put("id", set.id).put("name", set.name).put(
                "parts",
                JSONArray().also { pa ->
                    set.parts.forEach { part ->
                        pa.put(
                            JSONObject().put("name", part.name).apply { part.totalSeconds?.let { put("total", it) } }.put(
                                "steps",
                                JSONArray().also { sa ->
                                    part.steps.forEach { st ->
                                        sa.put(JSONObject().put("name", st.name).put("s", st.seconds).put("rest", st.remainder))
                                    }
                                },
                            ),
                        )
                    }
                },
            ),
        )
    }
}.toString()

fun decodeTimerSets(raw: String?): List<TimerSet> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val parts = o.optJSONArray("parts")?.let { pa ->
                (0 until pa.length()).mapNotNull { j ->
                    val p = pa.optJSONObject(j) ?: return@mapNotNull null
                    val steps = p.optJSONArray("steps")?.let { sa ->
                        (0 until sa.length()).mapNotNull { k ->
                            val s = sa.optJSONObject(k) ?: return@mapNotNull null
                            TimerStep(s.optString("name"), s.optInt("s"), s.optBoolean("rest"))
                        }
                    }.orEmpty()
                    TimerPart(p.optString("name"), steps, if (p.has("total")) p.optInt("total") else null)
                }
            }.orEmpty()
            TimerSet(o.optString("id").ifEmpty { return@mapNotNull null }, o.optString("name"), parts)
        }
    }.getOrDefault(emptyList())
}
