package com.tileshell.feature.keyboard

import kotlin.math.hypot
import kotlin.math.max

/** A point on the keyboard, in pixels. */
data class Pt(val x: Float, val y: Float)

/**
 * Swipe typing (canvas "Swipe typing"): turns the finger's path across the
 * letter keys into words. Each candidate word is drawn as the ideal path
 * through its keys' centres; the word whose path lies closest to the finger's,
 * weighted by how common it is, wins. Pure — key centres come in — so it's
 * unit-tested with a synthetic layout.
 */
class SwipeDecoder(
    /** Letter → key centre. */
    val centres: Map<Char, Pt>,
    /** One key's width, the unit distances are measured in. */
    val keyWidth: Float,
    /**
     * Corner rounding applied to both the finger's path and each word's ideal path
     * before they are compared (a moving average over ±[smoothing] of the 32 samples):
     * a hurried swipe cuts corners, and a word whose ideal path is rounded the same
     * way still matches it.
     */
    private val smoothing: Int = 0,
    /** How near the path must pass to a letter's key (key widths) for the letter to count as visited. */
    private val followRadius: Float = FOLLOW_RADIUS,
    /** How many of a word's letters the path may miss. */
    private val maxMissed: Int = 1,
) {

    data class Result(val word: String, val score: Float)

    /**
     * The best words for [path] (finger samples, first to last), best first.
     * [lexicon] supplies candidates by their first letter. [likely] are the words the
     * text before makes likely next, most likely first: a word on it gets a bonus
     * (more the nearer the top), which settles swipes whose shapes are alike
     * ("too" / "to", "these" / "there") by what was just written.
     */
    fun decode(path: List<Pt>, lexicon: Lexicon, limit: Int = 4, likely: List<String> = emptyList()): List<Result> {
        if (path.size < 2 || centres.isEmpty()) return emptyList()
        val user = smooth(resample(path, SAMPLES), smoothing)
        val userLength = length(path)
        val starts = nearKeys(path.first(), START_RADIUS)
        val ends = nearKeys(path.last(), END_RADIUS).toSet()
        val results = ArrayList<Result>()
        for (first in starts) {
            for (entry in lexicon.startingWith(first.toString())) {
                val word = entry.word
                val lower = word.lowercase()
                if (lower.length < 2 || entry.blocked) continue
                if (lower.last() !in ends) continue
                val keys = keyPath(lower) ?: continue
                // Every letter's key near the path — one may fall just outside on a
                // long zigzag; it costs, but doesn't rule the word out.
                val missed = missedKeys(keys, user)
                if (missed > maxMissed) continue
                val idealPoints = keys.map { centres.getValue(it) }
                val idealRaw = resample(idealPoints, SAMPLES)
                val ideal = smooth(idealRaw, smoothing)
                // A long swipe isn't a short word whose path happens to share its shape.
                val lengthMismatch = kotlin.math.abs(
                    kotlin.math.ln((userLength + keyWidth) / (length(idealPoints) + keyWidth)),
                )
                val distance = meanDistance(user, ideal) / keyWidth
                // Where a swipe starts and ends is the most deliberate part of it.
                val ends = (dist(path.first(), idealRaw.first()) + dist(path.last(), idealRaw.last())) / keyWidth
                val rank = likely.indexOf(lower)
                val context = if (rank < 0) 0f else CONTEXT_BONUS * (1f - rank / (likely.size + 1f))
                val score = distance * DISTANCE_WEIGHT + ends * END_WEIGHT + lengthMismatch * LENGTH_WEIGHT +
                    missed * MISSED_KEY_WEIGHT +
                    (255 - entry.freq) / 255f * FREQUENCY_WEIGHT - context
                results += Result(word, score)
            }
        }
        // A word can come twice (learned and bundled): keep its better score.
        return results.sortedBy { it.score }.distinctBy { it.word.lowercase() }.take(limit)
    }

    /** The word's keys, a doubled letter counted once (the finger doesn't move). */
    private fun keyPath(word: String): List<Char>? {
        val keys = ArrayList<Char>(word.length)
        for (ch in word) {
            if (ch !in centres) {
                if (ch == '\'' || ch == '-') continue
                return null
            }
            if (keys.lastOrNull() != ch) keys += ch
        }
        return keys
    }

    /**
     * How many of the word's keys the path doesn't pass near, in order — a
     * cheap filter before scoring. A missed key doesn't advance the search,
     * so later letters can still be found.
     */
    private fun missedKeys(keys: List<Char>, user: List<Pt>): Int {
        var i = 0
        var missed = 0
        for (k in keys) {
            val c = centres.getValue(k)
            var j = i
            while (j < user.size && dist(user[j], c) > keyWidth * followRadius) j++
            if (j == user.size) {
                missed++
                if (missed > maxMissed) return missed
            } else {
                i = j
            }
        }
        return missed
    }

    private fun nearKeys(p: Pt, radius: Float): List<Char> =
        centres.entries.filter { dist(it.value, p) <= keyWidth * radius }
            .sortedBy { dist(it.value, p) }.map { it.key }

    companion object {
        private const val SAMPLES = 32
        private const val START_RADIUS = 1.2f
        private const val END_RADIUS = 1.5f
        private const val FOLLOW_RADIUS = 1.3f
        private const val DISTANCE_WEIGHT = 1f
        private const val FREQUENCY_WEIGHT = 0.6f
        private const val END_WEIGHT = 0.35f
        private const val MISSED_KEY_WEIGHT = 0.4f
        private const val LENGTH_WEIGHT = 0.8f

        /** Taken off the score of the likeliest next word, in the same units as the distance (key widths). */
        private const val CONTEXT_BONUS = 0.5f

        /** Moving average over ±[k] points (the ends are held), or the points as they are for k = 0. */
        fun smooth(points: List<Pt>, k: Int): List<Pt> {
            if (k <= 0) return points
            return points.indices.map { i ->
                var x = 0f
                var y = 0f
                var n = 0
                for (d in -k..k) {
                    val p = points[(i + d).coerceIn(0, points.lastIndex)]
                    x += p.x
                    y += p.y
                    n++
                }
                Pt(x / n, y / n)
            }
        }

        fun length(points: List<Pt>): Float = points.zipWithNext().sumOf { (a, b) -> dist(a, b).toDouble() }.toFloat()

        fun dist(a: Pt, b: Pt): Float = hypot(a.x - b.x, a.y - b.y)

        fun meanDistance(a: List<Pt>, b: List<Pt>): Float =
            a.indices.sumOf { dist(a[it], b[it]).toDouble() }.toFloat() / a.size

        /** [n] points evenly spaced along the polyline (one point repeated if it has no length). */
        fun resample(points: List<Pt>, n: Int): List<Pt> {
            if (points.size == 1) return List(n) { points[0] }
            val total = points.zipWithNext().sumOf { (a, b) -> dist(a, b).toDouble() }.toFloat()
            if (total == 0f) return List(n) { points[0] }
            val step = total / (n - 1)
            val out = ArrayList<Pt>(n)
            out += points[0]
            var travelled = 0f
            var target = step
            for ((a, b) in points.zipWithNext()) {
                val seg = dist(a, b)
                while (seg > 0f && travelled + seg >= target && out.size < n - 1) {
                    val t = (target - travelled) / seg
                    out += Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                    target += step
                }
                travelled += seg
            }
            while (out.size < n) out += points.last()
            return out
        }

        /** True once a press has moved far enough to be a swipe, not a slip. */
        fun isSwipe(from: Pt, to: Pt, keyWidth: Float): Boolean = dist(from, to) > max(keyWidth * 0.8f, 1f)
    }
}
