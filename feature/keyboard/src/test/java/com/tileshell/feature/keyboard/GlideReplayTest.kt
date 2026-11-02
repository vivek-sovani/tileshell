package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/**
 * Replays real swipes from the glide test against the decoder as it is now. Put the
 * shared text of a glide test (the part from "GLIDE 1" on) in
 * any .txt file in `feature/keyboard/glide-recordings` (kept out of git) and run with `-i`: it prints
 * how the current decoder does on those swipes, first / top three, and every miss.
 * With no recordings it only checks the file format.
 */
class GlideReplayTest {

    @Test
    fun `a recording reads back as it was written`() {
        val centres = mapOf('a' to Pt(10f, 20f), 'b' to Pt(30f, 20f))
        val sample = GlideSample(listOf(Pt(1.5f, 2.5f), Pt(3f, 4f)), centres, 90f).also { it.decoded = listOf("ab", "ba") }
        val text = GlideLog.export(listOf("ab" to sample))
        val back = GlideLog.parse("some report\n\n$text".substringAfter("some report\n\n"))
        assertNotNull(back)
        assertEquals(90f, back!!.keyWidth, 0f)
        assertEquals(centres, back.centres)
        assertEquals("ab", back.swipes.single().word)
        assertEquals(listOf("ab", "ba"), back.swipes.single().decoded)
        assertEquals(listOf(Pt(1.5f, 2.5f), Pt(3f, 4f)), back.swipes.single().path)
        assertEquals(null, GlideLog.parse("not a recording"))
    }

    @Test
    fun `replay recorded swipes`() {
        val files = File("glide-recordings").listFiles { f -> f.extension == "txt" }.orEmpty()
        if (files.isEmpty()) return
        val english = File("src/main/assets/keyboard/en_words.txt").bufferedReader().useLines { WordList.parse(it) }
        for (file in files) {
            val text = file.readText()
            val rec = GlideLog.parse(text.substring(text.indexOf("GLIDE 1").coerceAtLeast(0))) ?: continue
            val decoder = SwipeDecoder(rec.centres, rec.keyWidth)
            var first = 0
            var three = 0
            val misses = ArrayList<String>()
            for (s in rec.swipes) {
                val got = decoder.decode(s.path, english).map { it.word.lowercase() }
                if (got.firstOrNull() == s.word) first++ else misses += "${s.word}→${got.take(3)} (recorded ${s.decoded.take(2)})"
                if (s.word in got.take(3)) three++
            }
            println("REPLAY ${file.name}: ${rec.swipes.size} swipes, first ${"%.0f".format(first * 100.0 / rec.swipes.size)}%, top three ${"%.0f".format(three * 100.0 / rec.swipes.size)}%")
            misses.forEach { println("REPLAY   miss $it") }
        }
    }
}
