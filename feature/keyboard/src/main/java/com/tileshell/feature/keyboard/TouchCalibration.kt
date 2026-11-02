package com.tileshell.feature.keyboard

import android.content.Context
import kotlin.math.sqrt

/**
 * Where this person's thumbs land compared with where the keys are, learned from
 * how they type, in dp (a thumb is physical, so the unit doesn't depend on the
 * layout, width or orientation). Eight zones: left and right half of the keyboard
 * by four rows, since the left thumb, the right thumb and a reach to the top row
 * each land differently.
 *
 * Each zone keeps a running mean of (where the touch was − the centre of the key
 * it was given), shrunk towards the default offset (touches land [DEFAULT_DY] dp
 * above where they are aimed) until enough touches have been seen, and limited to
 * [MAX_OFFSET] dp. The touch is moved by the opposite of that mean before the key
 * is chosen. Only numbers are kept, never what was typed. Pure apart from [Store].
 */
class TouchCalibration {

    private val n = DoubleArray(ZONES)
    private val sx = DoubleArray(ZONES)
    private val sy = DoubleArray(ZONES)

    /** Adds one touch: [dx], [dy] dp from the centre of the key it went to. A [weight] above 1 for firmer evidence. */
    @Synchronized
    fun observe(zone: Int, dx: Float, dy: Float, weight: Float = 1f) {
        n[zone] += weight
        sx[zone] += weight * dx
        sy[zone] += weight * dy
        if (n[zone] > WINDOW) {
            val k = WINDOW / n[zone]
            n[zone] *= k
            sx[zone] *= k
            sy[zone] *= k
        }
    }

    /** How many dp to move a touch in [zone] (x right, y down) before choosing its key. */
    @Synchronized
    fun bias(zone: Int): Pair<Float, Float> {
        val mx = (sx[zone] + PRIOR_WEIGHT * DEFAULT_DX) / (n[zone] + PRIOR_WEIGHT)
        val my = (sy[zone] + PRIOR_WEIGHT * DEFAULT_DY) / (n[zone] + PRIOR_WEIGHT)
        return (-mx).toFloat().coerceIn(-MAX_OFFSET, MAX_OFFSET) to (-my).toFloat().coerceIn(-MAX_OFFSET, MAX_OFFSET)
    }

    /** What has been learned overall: touches seen and the mean landing offset, or null when none. */
    @Synchronized
    fun summary(): Summary? {
        val total = n.sum()
        if (total < 1.0) return null
        return Summary(total.toInt(), (sx.sum() / total).toFloat(), (sy.sum() / total).toFloat())
    }

    class Summary(val touches: Int, val meanDx: Float, val meanDy: Float)

    @Synchronized
    fun clear() {
        n.fill(0.0)
        sx.fill(0.0)
        sy.fill(0.0)
    }

    @Synchronized
    fun encode(): String = (0 until ZONES).joinToString(";") { "${n[it]},${sx[it]},${sy[it]}" }

    @Synchronized
    fun decode(text: String?) {
        clear()
        val zones = text?.split(';') ?: return
        if (zones.size != ZONES) return
        for ((z, part) in zones.withIndex()) {
            val v = part.split(',').mapNotNull { it.toDoubleOrNull() }
            if (v.size == 3 && v.all { it.isFinite() } && v[0] >= 0) {
                n[z] = v[0].coerceAtMost(WINDOW)
                sx[z] = v[1]
                sy[z] = v[2]
            }
        }
    }

    companion object {
        const val ZONES = 8

        /** Touches seen are remembered like this many most recent ones, so a changed grip is followed. */
        const val WINDOW = 600.0

        /** The default offset's worth of touches: how many real ones it takes to outweigh it. */
        const val PRIOR_WEIGHT = 40.0

        /** Touches land this far above where they are aimed unless this person's say otherwise. */
        const val DEFAULT_DX = 0.0
        const val DEFAULT_DY = -4.0
        const val MAX_OFFSET = 10f

        /** Zone of a touch at [x] of a key area [width] wide, on key row [row] (0 = top). */
        fun zoneOf(x: Float, width: Float, row: Int): Int =
            row.coerceIn(0, 3) * 2 + if (x < width / 2f) 0 else 1
    }
}

/**
 * Turns the stream of taps into calibration evidence, with no knowledge of what
 * was meant:
 *  - every letter touch counts, once the next key shows it was not taken back
 *    (a letter deleted at once was a slip, not a measure of the thumb);
 *  - a letter deleted at once and followed by a neighbouring key is firm
 *    evidence: the first touch was meant for that neighbour ([SLIP_WEIGHT]).
 * Positions are in dp. Pure, so it is unit-tested with a fake clock.
 */
class TapLearner(private val calibration: TouchCalibration) {

    private class Tap(val zone: Int, val tap: Pt, val centre: Pt)

    private var pending: Tap? = null
    private var slip: Tap? = null
    private var slipAt = 0L

    /** A letter touch at [tap], given to the key centred at [centre]; [keyWidth] decides who is a neighbour. */
    fun onLetter(zone: Int, tap: Pt, centre: Pt, keyWidth: Float, atMs: Long) {
        slip?.let { s ->
            val gap = distance(s.centre, centre)
            if (atMs - slipAt <= SLIP_MS && gap > 1f && gap <= NEIGHBOUR * keyWidth) {
                calibration.observe(s.zone, s.tap.x - centre.x, s.tap.y - centre.y, SLIP_WEIGHT)
            }
        }
        slip = null
        commitPending()
        pending = Tap(zone, tap, centre)
    }

    fun onBackspace(atMs: Long) {
        val p = pending
        pending = null
        if (p != null) {
            slip = p
            slipAt = atMs
        } else {
            slip = null
        }
    }

    /** Any other key (space, shift, symbols): the letters before it stand. */
    fun onOther() {
        slip = null
        commitPending()
    }

    private fun commitPending() {
        pending?.let { calibration.observe(it.zone, it.tap.x - it.centre.x, it.tap.y - it.centre.y) }
        pending = null
    }

    private fun distance(a: Pt, b: Pt): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private companion object {
        const val SLIP_MS = 3000L
        const val SLIP_WEIGHT = 3f

        /** A neighbour: no further than this many key widths between centres. */
        const val NEIGHBOUR = 1.7f
    }
}

/** The one calibration of the phone, kept in its own preferences file. */
object TouchCalibrationStore {
    private const val FILE = "keyboard_touch"
    private const val KEY = "zones"
    private const val SAVE_EVERY = 25

    val calibration = TouchCalibration()
    private var loaded = false
    private var unsaved = 0
    private var context: Context? = null

    @Synchronized
    fun get(context: Context): TouchCalibration {
        if (!loaded) {
            val app = context.applicationContext
            this.context = app
            calibration.decode(app.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null))
            loaded = true
        }
        return calibration
    }

    /** Counts a touch seen; saves every [SAVE_EVERY] of them. */
    @Synchronized
    fun touched() {
        if (++unsaved >= SAVE_EVERY) flush()
    }

    @Synchronized
    fun flush() {
        val app = context ?: return
        if (unsaved == 0) return
        unsaved = 0
        app.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, calibration.encode()).apply()
    }

    @Synchronized
    fun reset(context: Context) {
        get(context)
        calibration.clear()
        unsaved = 1
        flush()
    }
}
