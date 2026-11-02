package com.tileshell.feature.keyboard

import kotlin.math.sqrt

/** Where the letter keys sat when a tap landed: centres by label, and one key's width. */
class TapLayout(val centres: Map<String, Pt>, val keyWidth: Float)

/** One tap on a key: where it landed in the key area and which key it was given to. */
class TapRecord(val x: Float, val y: Float, val hit: String, val layout: TapLayout)

/**
 * The taps of a typing test. Off unless the typing test screen is open (the
 * keyboard shares TileShell's process), so ordinary typing is never recorded.
 */
object TapLog {
    @Volatile
    var recording = false

    private val taps = ArrayList<TapRecord>()

    fun add(tap: TapRecord) {
        if (recording) synchronized(taps) { taps += tap }
    }

    fun clear() = synchronized(taps) { taps.clear() }

    fun snapshot(): List<TapRecord> = synchronized(taps) { taps.toList() }

    const val BACKSPACE = "⌫"
}

/** How one test went at the level of single taps. */
class TapStats(
    val taps: Int,
    val wrong: Int,
    val backspaces: Int,
    /** Mean landing offset from the intended key's centre, in key widths (x right, y down). */
    val meanDx: Float,
    val meanDy: Float,
    /** Spread (standard deviation) of the landing, in key widths. */
    val sigmaX: Float,
    val sigmaY: Float,
    /** Mistakes as (intended, given), most frequent first. */
    val confusions: List<Pair<Pair<String, String>, Int>>,
) {
    val wrongRate: Float get() = if (taps == 0) 0f else wrong.toFloat() / taps
}

/**
 * Works out, from the taps alone, which key was meant: the test sentence is
 * known, so the tap that lands when the text so far has n characters was meant
 * for character n. A backspace takes the last character back, so a slip and its
 * retry count as one wrong tap. Independent of what the field shows (autocorrect
 * and other keyboards' changes don't matter). Pure, so it's unit-tested.
 */
object TapReplay {

    fun analyse(taps: List<TapRecord>, target: String): TapStats = analyseAll(listOf(taps to target))

    /** Several sentences pooled: each (taps, target) pair is replayed from empty text. */
    fun analyseAll(sessions: List<Pair<List<TapRecord>, String>>): TapStats {
        var counted = 0
        var wrong = 0
        var backspaces = 0
        val dx = ArrayList<Float>()
        val dy = ArrayList<Float>()
        val confusions = HashMap<Pair<String, String>, Int>()
        for ((taps, target) in sessions) {
            val typed = ArrayList<String>()
            for (tap in taps) {
                if (tap.hit == TapLog.BACKSPACE) {
                    backspaces++
                    if (typed.isNotEmpty()) typed.removeAt(typed.lastIndex)
                    continue
                }
                val intended = target.getOrNull(typed.size)?.toString()
                typed += tap.hit
                if (intended == null) continue
                counted++
                if (!tap.hit.equals(intended, ignoreCase = true)) {
                    wrong++
                    confusions.merge(intended to tap.hit.lowercase(), 1, Int::plus)
                }
                val centre = tap.layout.centres[intended.lowercase()] ?: continue
                dx += (tap.x - centre.x) / tap.layout.keyWidth
                dy += (tap.y - centre.y) / tap.layout.keyWidth
            }
        }
        return TapStats(
            taps = counted,
            wrong = wrong,
            backspaces = backspaces,
            meanDx = mean(dx),
            meanDy = mean(dy),
            sigmaX = deviation(dx),
            sigmaY = deviation(dy),
            confusions = confusions.entries.sortedByDescending { it.value }.map { it.key to it.value },
        )
    }

    private fun mean(v: List<Float>) = if (v.isEmpty()) 0f else v.sum() / v.size

    private fun deviation(v: List<Float>): Float {
        if (v.size < 2) return 0f
        val m = mean(v)
        return sqrt(v.sumOf { ((it - m) * (it - m)).toDouble() }.toFloat() / (v.size - 1))
    }
}
