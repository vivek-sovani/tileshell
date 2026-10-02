package com.tileshell.feature.keyboard

import java.io.File

/** One copied piece of text. */
data class Clip(val text: String, val at: Long, val pinned: Boolean = false)

/**
 * The clipboard panel's clips (canvas "Clipboard"): kept for an hour unless
 * pinned, newest first, at most [MAX]. Stored only in the app's own files
 * (`keyboard_clips.txt`), never sent anywhere; text a copying app marks
 * sensitive (a password) is never kept. Pure apart from the file, so it's
 * unit-tested with [now] passed in.
 */
class ClipStore(private val file: File?) {

    private val clips = ArrayList<Clip>()

    init {
        file?.takeIf { it.exists() }?.let { f ->
            runCatching { f.readLines() }.getOrDefault(emptyList()).forEach { line ->
                val parts = line.split('\t', limit = 3)
                if (parts.size == 3) {
                    val at = parts[0].toLongOrNull() ?: return@forEach
                    clips += Clip(unescape(parts[2]), at, pinned = parts[1] == "p")
                }
            }
        }
    }

    /** The clips still kept at [now], newest first (pinned ones always). */
    fun list(now: Long): List<Clip> {
        val before = clips.size
        clips.removeAll { !it.pinned && now - it.at > KEEP_MS }
        if (clips.size != before) save()
        return clips.sortedByDescending { it.at }
    }

    fun add(text: String, now: Long) {
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > MAX_LENGTH) return
        val pinned = clips.firstOrNull { it.text == clean }?.pinned == true
        clips.removeAll { it.text == clean }
        clips += Clip(clean, now, pinned)
        // Over the cap, the oldest unpinned clip goes.
        while (clips.size > MAX) {
            val oldest = clips.filter { !it.pinned }.minByOrNull { it.at } ?: break
            clips.remove(oldest)
        }
        save()
    }

    fun togglePin(text: String) {
        val i = clips.indexOfFirst { it.text == text }
        if (i >= 0) clips[i] = clips[i].copy(pinned = !clips[i].pinned)
        save()
    }

    fun delete(text: String) {
        clips.removeAll { it.text == text }
        save()
    }

    /** "clear": everything not pinned. */
    fun clearUnpinned() {
        clips.removeAll { !it.pinned }
        save()
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(clips.joinToString("") { "${it.at}\t${if (it.pinned) "p" else "-"}\t${escape(it.text)}\n" })
            tmp.renameTo(f)
        }
    }

    companion object {
        const val KEEP_MS = 60 * 60 * 1000L
        const val MAX = 30
        private const val MAX_LENGTH = 5_000

        /** A copy this recent is offered as a one-tap paste in the strip. */
        const val FRESH_MS = 60 * 1000L

        internal fun escape(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")
        internal fun unescape(s: String): String {
            val out = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    out.append(
                        when (s[i + 1]) {
                            'n' -> '\n'
                            't' -> '\t'
                            else -> s[i + 1]
                        },
                    )
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}
