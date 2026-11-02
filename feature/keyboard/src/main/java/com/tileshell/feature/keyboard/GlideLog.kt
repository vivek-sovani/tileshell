package com.tileshell.feature.keyboard

/** One recorded swipe: the finger's path as the decoder read it, the layout it was made on, and what came out. */
class GlideSample(val path: List<Pt>, val centres: Map<Char, Pt>, val keyWidth: Float) {
    @Volatile
    var decoded: List<String>? = null
}

/**
 * The swipes of the glide test. Off unless the glide test screen is open (the
 * keyboard shares TileShell's process), so ordinary swiping is never recorded.
 * The keyboard hands over the path when a swipe lifts ([begin]) and the decoder's
 * words when they are known ([decoded], just before the word is put in the field).
 */
object GlideLog {
    @Volatile
    var recording = false

    @Volatile
    var latest: GlideSample? = null
        private set

    fun begin(path: List<Pt>, centres: Map<Char, Pt>, keyWidth: Float) {
        if (recording) latest = GlideSample(path, centres, keyWidth)
    }

    fun decoded(words: List<String>) {
        if (recording) latest?.decoded = words
    }

    fun clear() {
        latest = null
    }

    /**
     * The text of a recording session for sharing, one swipe a line, readable back by
     * [parse] (and by the offline replay test): the layout once, then
     * `word <TAB> what the decoder gave, comma separated <TAB> x,y;x,y;…` in pixels.
     */
    fun export(samples: List<Pair<String, GlideSample>>): String {
        val sb = StringBuilder("GLIDE 1\n")
        val first = samples.firstOrNull()?.second ?: return sb.toString()
        sb.append("keyWidth\t").append(first.keyWidth).append('\n')
        sb.append("centres\t").append(first.centres.entries.joinToString(";") { "${it.key},${it.value.x},${it.value.y}" }).append('\n')
        for ((word, s) in samples) {
            sb.append(word).append('\t').append(s.decoded.orEmpty().joinToString(",")).append('\t')
                .append(s.path.joinToString(";") { "${"%.1f".format(it.x)},${"%.1f".format(it.y)}" }).append('\n')
        }
        return sb.toString()
    }

    /** What [export] wrote: the layout and each (target word, decoded words, path); null if it isn't that format. */
    fun parse(text: String): Recording? {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.firstOrNull()?.trim() != "GLIDE 1") return null
        val keyWidth = lines.firstOrNull { it.startsWith("keyWidth\t") }?.substringAfter('\t')?.toFloatOrNull() ?: return null
        val centres = lines.firstOrNull { it.startsWith("centres\t") }?.substringAfter('\t')?.split(';')?.mapNotNull { part ->
            val v = part.split(',')
            if (v.size == 3 && v[0].length == 1) v[0][0] to Pt(v[1].toFloatOrNull() ?: return@mapNotNull null, v[2].toFloatOrNull() ?: return@mapNotNull null) else null
        }?.toMap() ?: return null
        val swipes = lines.drop(3).mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 3) return@mapNotNull null
            val path = parts[2].split(';').mapNotNull { p ->
                val xy = p.split(',')
                if (xy.size == 2) Pt(xy[0].toFloatOrNull() ?: return@mapNotNull null, xy[1].toFloatOrNull() ?: return@mapNotNull null) else null
            }
            Swipe(parts[0], parts[1].split(',').filter { it.isNotEmpty() }, path)
        }
        return Recording(keyWidth, centres, swipes)
    }

    class Swipe(val word: String, val decoded: List<String>, val path: List<Pt>)
    class Recording(val keyWidth: Float, val centres: Map<Char, Pt>, val swipes: List<Swipe>)
}
