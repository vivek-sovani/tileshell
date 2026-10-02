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
    private val centres: Map<Char, Pt>,
    /** One key's width, the unit distances are measured in. */
    private val keyWidth: Float,
) {

    data class Result(val word: String, val score: Float)

    /**
     * The best words for [path] (finger samples, first to last), best first.
     * [lexicon] supplies candidates by their first letter.
     */
    fun decode(path: List<Pt>, lexicon: Lexicon, limit: Int = 4): List<Result> {
        if (path.size < 2 || centres.isEmpty()) return emptyList()
        val user = resample(path, SAMPLES)
        val starts = nearKeys(path.first(), START_RADIUS)
        val ends = nearKeys(path.last(), END_RADIUS).toSet()
        val results = ArrayList<Result>()
        for (first in starts) {
            for (entry in lexicon.startingWith(first.toString())) {
                val word = entry.word
                val lower = word.lowercase()
                if (lower.length < 2 || entry.blocked || entry.freq < MIN_FREQ) continue
                if (lower.last() !in ends) continue
                val keys = keyPath(lower) ?: continue
                if (!followsPath(keys, user)) continue
                val ideal = resample(keys.map { centres.getValue(it) }, SAMPLES)
                val distance = meanDistance(user, ideal) / keyWidth
                val score = distance * DISTANCE_WEIGHT + (255 - entry.freq) / 255f * FREQUENCY_WEIGHT
                results += Result(word, score)
            }
        }
        return results.distinctBy { it.word.lowercase() }.sortedBy { it.score }.take(limit)
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

    /** Every letter's key passes near the path, in order — a cheap filter before scoring. */
    private fun followsPath(keys: List<Char>, user: List<Pt>): Boolean {
        var i = 0
        for (k in keys) {
            val c = centres.getValue(k)
            while (i < user.size && dist(user[i], c) > keyWidth * FOLLOW_RADIUS) i++
            if (i == user.size) return false
        }
        return true
    }

    private fun nearKeys(p: Pt, radius: Float): List<Char> =
        centres.entries.filter { dist(it.value, p) <= keyWidth * radius }
            .sortedBy { dist(it.value, p) }.map { it.key }

    companion object {
        private const val SAMPLES = 32
        private const val START_RADIUS = 0.9f
        private const val END_RADIUS = 1.1f
        private const val FOLLOW_RADIUS = 1.05f
        /** Rare words would only crowd out the ones meant. */
        private const val MIN_FREQ = 40
        private const val DISTANCE_WEIGHT = 1f
        private const val FREQUENCY_WEIGHT = 0.6f

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
