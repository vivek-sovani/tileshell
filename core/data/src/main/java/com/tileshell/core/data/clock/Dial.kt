package com.tileshell.core.data.clock

/** The three hands of an analog dial, in degrees clockwise from 12. */
data class DialAngles(val hour: Float, val minute: Float, val second: Float)

fun dialAngles(hour: Int, minute: Int, second: Int): DialAngles =
    DialAngles(
        hour = ((hour % 12) + minute / 60f + second / 3600f) * 30f,
        minute = (minute + second / 60f) * 6f,
        second = second * 6f,
    )

/** Where a time of day sits on a 12-hour dial, in degrees clockwise from 12. */
fun dialAngle(hour: Int, minute: Int): Float = ((hour % 12) + minute / 60f) * 30f

/** How many degrees clockwise from [fromDeg] to [toDeg], 0 up to 360: the arc from the hour hand to the alarm. */
fun sweepBetween(fromDeg: Float, toDeg: Float): Float = ((toDeg - fromDeg) % 360f + 360f) % 360f

/** One arc of the set ring: where it starts and how far it runs, degrees clockwise from the top. */
data class RingArc(val startDeg: Float, val sweepDeg: Float)

/**
 * The ring for a set: one arc per step, each as long as its share of the whole,
 * with a small gap between steps and a wider one between parts (and at the top,
 * where the ring begins and ends), so the parts read as groups. Pure.
 */
fun ringArcs(steps: List<SessionStep>, stepGapDeg: Float = 3f, partGapDeg: Float = 10f): List<RingArc> {
    if (steps.isEmpty()) return emptyList()
    val gaps = steps.indices.map { i ->
        val next = steps.getOrNull(i + 1)
        when {
            next == null -> partGapDeg
            next.part != steps[i].part -> partGapDeg
            else -> stepGapDeg
        }
    }
    val total = steps.sumOf { it.ms }.toFloat()
    val room = 360f - gaps.sum()
    var at = partGapDeg / 2f
    return steps.mapIndexed { i, step ->
        val sweep = room * step.ms / total
        RingArc(at, sweep).also { at += sweep + gaps[i] }
    }
}
