package com.tileshell.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    (a shl 24) or (r shl 16) or (g shl 8) or b

private fun alphaOf(pixel: Int): Int = (pixel ushr 24) and 0xFF
private fun rgbOf(pixel: Int): Int = pixel and 0xFFFFFF

class MonochromeTest {

    @Test
    fun `empty input returns empty output`() {
        assertEquals(0, synthesizeMonochromeMask(IntArray(0)).size)
    }

    @Test
    fun `real transparency is used as-is, rgb forced to white`() {
        // A small opaque glyph on a genuinely transparent-majority field — a
        // real adaptive-icon foreground layer's glyph silhouette.
        val pixels = IntArray(100) { i -> if (i < 20) argb(255, 10, 20, 30) else argb(0, 10, 20, 30) }
        val mask = synthesizeMonochromeMask(pixels)
        for (i in 0 until 20) {
            assertEquals(255, alphaOf(mask[i]))
            assertEquals(0xFFFFFF, rgbOf(mask[i]))
        }
        for (i in 20 until 100) {
            assertEquals(0, alphaOf(mask[i]))
        }
    }

    @Test
    fun `fully opaque dark glyph on light field becomes opaque ink`() {
        // Mostly white (light field) with a solid black glyph block — no
        // transparency at all, so this must fall back to luminance.
        val pixels = IntArray(100) { i -> if (i < 20) argb(255, 0, 0, 0) else argb(255, 255, 255, 255) }
        val mask = synthesizeMonochromeMask(pixels)
        // Black glyph pixels become fully opaque "ink".
        assertEquals(255, alphaOf(mask[0]))
        // White field pixels fade to transparent.
        assertEquals(0, alphaOf(mask[99]))
    }

    @Test
    fun `fully opaque light glyph on dark field becomes opaque ink`() {
        val pixels = IntArray(100) { i -> if (i < 20) argb(255, 255, 255, 255) else argb(255, 0, 0, 0) }
        val mask = synthesizeMonochromeMask(pixels)
        // White glyph pixels become fully opaque "ink" on a mostly-dark field.
        assertEquals(255, alphaOf(mask[0]))
        // Black field pixels fade to transparent.
        assertEquals(0, alphaOf(mask[99]))
    }

    @Test
    fun `fully transparent input stays fully transparent`() {
        val pixels = IntArray(16) { argb(0, 0, 0, 0) }
        val mask = synthesizeMonochromeMask(pixels)
        assertTrue(mask.all { alphaOf(it) == 0 })
    }

    @Test
    fun `light wordmark on a moderately bright coloured fill is not lost`() {
        // Regression for the reported "details lost" bug (HP / Kissan Connect /
        // Sadhguru-style logos): a majority-area coloured fill (moderately
        // bright, e.g. orange) with a minority-area near-white wordmark. The
        // old "darker/lighter than the whole-image average" rule picked the
        // bright-average fill as ink and made the actual wordmark vanish —
        // minority-cluster selection must pick the wordmark instead, whichever
        // side is lighter.
        val fill = argb(255, 230, 140, 20) // luma ~153
        val wordmark = argb(255, 255, 255, 255) // luma 255
        val pixels = IntArray(100) { i -> if (i < 90) fill else wordmark }
        val mask = synthesizeMonochromeMask(pixels)
        assertEquals(255, alphaOf(mask[99])) // wordmark: fully opaque ink
        assertEquals(0, alphaOf(mask[0])) // fill: transparent field
    }

    @Test
    fun `dark wordmark on a moderately dim coloured fill is not lost`() {
        val fill = argb(255, 30, 40, 90) // luma ~42, moderately dim, not near-black
        val wordmark = argb(255, 0, 0, 0) // luma 0
        val pixels = IntArray(100) { i -> if (i < 90) fill else wordmark }
        val mask = synthesizeMonochromeMask(pixels)
        assertEquals(255, alphaOf(mask[99])) // wordmark: fully opaque ink
        assertEquals(0, alphaOf(mask[0])) // fill: transparent field
    }

    @Test
    fun `low-contrast ink still reaches full opacity via contrast stretch`() {
        // Ink and field are close in luma (low absolute contrast) — without
        // stretching, the minority cluster would render at a washed, barely
        // visible mid-alpha instead of a legible glyph.
        val field = argb(255, 150, 150, 150)
        val ink = argb(255, 130, 130, 130)
        val pixels = IntArray(100) { i -> if (i < 90) field else ink }
        val mask = synthesizeMonochromeMask(pixels)
        assertEquals(255, alphaOf(mask[99]))
        assertEquals(0, alphaOf(mask[0]))
    }

    @Test
    fun `anti-aliased edges alone do not count as meaningful transparency`() {
        // A fully opaque legacy icon with a thin partial-alpha edge ring —
        // should still route through the luminance fallback, not the raw-alpha
        // path (which would silhouette only the edge fuzz).
        val pixels = IntArray(100) { i ->
            when {
                i < 2 -> argb(128, 0, 0, 0) // <3% partial-alpha edge pixels
                i < 20 -> argb(255, 0, 0, 0)
                else -> argb(255, 255, 255, 255)
            }
        }
        assertTrue(!hasMeaningfulTransparency(pixels))
    }

    @Test
    fun `transparent-majority glyph-on-field pattern is meaningful`() {
        val pixels = IntArray(100) { i -> if (i < 30) argb(255, 0, 0, 0) else argb(0, 0, 0, 0) }
        assertTrue(hasMeaningfulTransparency(pixels))
    }

    @Test
    fun `opaque-majority rounded-icon corner pattern is not meaningful`() {
        // Regression for the reported "details lost" bug (Sadhguru, Kissan
        // Connect): both are plain rounded/circular *legacy* icons — ~80-95%
        // opaque body, transparent only at the corner rounding — confirmed via
        // on-device instrumentation logging exactly this alpha pattern
        // (opaqueFrac ~0.80-0.95). That corner rounding is real transparency
        // but carries no glyph information; treating it as "meaningful" and
        // using raw alpha as the final silhouette produced a flat, detail-free
        // blob instead of routing through the luminance path that can
        // actually see the icon's internal wordmark contrast.
        val pixels = IntArray(100) { i -> if (i < 85) argb(255, 0, 0, 0) else argb(0, 0, 0, 0) }
        assertTrue(!hasMeaningfulTransparency(pixels))
    }

    @Test
    fun `uniform alpha is degenerate`() {
        // Regression for the reported Google Drive bug: a real declared
        // Android 13+ monochrome layer that itself renders as a solid,
        // fully-opaque filled plate with no shape variation at all — must be
        // treated as unusable so the caller falls back to synthesizing one.
        assertTrue(isUniformAlpha(IntArray(100) { argb(255, 0, 0, 0) }))
        assertTrue(isUniformAlpha(IntArray(100) { argb(0, 0, 0, 0) }))
    }

    @Test
    fun `varying alpha is not degenerate`() {
        val pixels = IntArray(100) { i -> if (i < 30) argb(255, 0, 0, 0) else argb(0, 0, 0, 0) }
        assertTrue(!isUniformAlpha(pixels))
    }

    @Test
    fun `opaque-majority rounded legacy icon still shows its wordmark via luminance`() {
        // End-to-end version of the regression above: a rounded icon (red
        // fill, transparent-corner rounding) with a white wordmark occupying
        // part of the opaque body — must still resolve through the luminance
        // path to the wordmark, not a blank/solid result.
        val transparentCorner = argb(0, 200, 30, 30)
        val fill = argb(255, 200, 30, 30) // luma ~85
        val wordmark = argb(255, 255, 255, 255) // luma 255
        val pixels = IntArray(100) { i ->
            when {
                i < 15 -> transparentCorner // corner rounding, minority
                i < 25 -> wordmark // wordmark, minority of the opaque body
                else -> fill // majority fill
            }
        }
        val mask = synthesizeMonochromeMask(pixels)
        assertEquals(255, alphaOf(mask[20])) // wordmark: fully opaque ink
        assertEquals(0, alphaOf(mask[99])) // fill: transparent field
        assertEquals(0, alphaOf(mask[0])) // corner: stays transparent
    }

    @Test
    fun `near-50-50 transparent padding does not swamp the opaque content`() {
        // Regression for the reported "BOBCARD" bug: an icon close to a 50/50
        // split between real (but not transparent-majority, so the alpha-as-
        // silhouette path never triggers) transparent padding and opaque
        // content — confirmed on-device (~48% transparent / ~52% opaque). The
        // transparent pixels' own RGB (0,0,0 here, as a decoder commonly
        // leaves for alpha-0) must never be folded into the same luminance
        // histogram as the real content: doing so let the black padding form
        // its own "minority" cluster, so minority-cluster selection picked
        // the (already-zero-alpha) padding as ink and the real, fully-opaque
        // wordmark was left classified as "field" — opacity zero, i.e. the
        // whole icon rendered blank despite having genuine content.
        val transparentPadding = argb(0, 0, 0, 0)
        val fill = argb(255, 200, 30, 30) // luma ~85, majority of the opaque half
        val wordmark = argb(255, 255, 255, 255) // luma 255, minority of the opaque half
        val pixels = IntArray(100) { i ->
            when {
                i < 48 -> transparentPadding
                i < 58 -> wordmark
                else -> fill
            }
        }
        val mask = synthesizeMonochromeMask(pixels)
        assertEquals(255, alphaOf(mask[50])) // wordmark: fully opaque ink
        assertEquals(0, alphaOf(mask[99])) // fill: transparent field
        assertEquals(0, alphaOf(mask[0])) // padding: stays transparent
    }

    @Test
    fun `a colourful logo inset on a transparent field is not flattened to a solid block`() {
        // Regression for the reported "DJI Mimo / Subway Surf / Tata CLiQ
        // Fashion / Tata Play / Microsoft 365 Admin" bug: each is a small
        // legacy icon comfortably inset on a transparent-majority canvas — a
        // real, correctly-detected silhouette shape — but the opaque content
        // itself is a coloured logo mark on a differently-coloured fill, not
        // a single flat colour. Using raw alpha as the final silhouette
        // (correct for a genuinely single-colour glyph) discarded that
        // colour information and solid-filled the whole inset square, so it
        // rendered as a flat, detail-free block instead of the real glyph.
        val transparentPadding = argb(0, 0, 0, 0)
        val fill = argb(255, 200, 30, 30) // luma ~85, majority of the opaque inset
        val logoMark = argb(255, 255, 255, 255) // luma 255, minority of the opaque inset
        val pixels = IntArray(100) { i ->
            when {
                i < 56 -> transparentPadding // transparent majority overall
                i < 68 -> logoMark
                else -> fill
            }
        }
        val mask = synthesizeMonochromeMask(pixels)
        assertEquals(255, alphaOf(mask[60])) // logo mark: fully opaque ink
        assertEquals(0, alphaOf(mask[99])) // fill: transparent field
        assertEquals(0, alphaOf(mask[0])) // padding: stays transparent
    }

    @Test
    fun `a genuinely single-colour glyph inset on a transparent field still uses alpha as-is`() {
        // The counterpart case: no internal colour contrast at all within the
        // opaque inset, so alpha really is the only shape signal available —
        // must still solid-fill via alpha, not be routed into a luminance
        // split that has nothing real to separate.
        val transparentPadding = argb(0, 0, 0, 0)
        val glyph = argb(255, 10, 20, 30)
        val pixels = IntArray(100) { i -> if (i < 56) transparentPadding else glyph }
        val mask = synthesizeMonochromeMask(pixels)
        assertEquals(255, alphaOf(mask[99]))
        assertEquals(0xFFFFFF, rgbOf(mask[99]))
        assertEquals(0, alphaOf(mask[0]))
    }

    private fun canvas(width: Int, height: Int, opaque: (x: Int, y: Int) -> Boolean): IntArray =
        IntArray(width * height) { i -> if (opaque(i % width, i / width)) argb(255, 255, 255, 255) else argb(0, 0, 0, 0) }

    @Test
    fun `opaqueBounds is null for a fully transparent canvas`() {
        val pixels = canvas(10, 10) { _, _ -> false }
        assertEquals(null, opaqueBounds(pixels, 10, 10))
    }

    @Test
    fun `opaqueBounds finds a small inked square inside a much larger canvas`() {
        // The real-world shape of the bug: a small logo mark (here 4x4)
        // centred in a much bigger canvas (40x40), everything else transparent
        // (the fill colour synthesizeMonochromeMask discarded).
        val pixels = canvas(40, 40) { x, y -> x in 18..21 && y in 18..21 }
        val bounds = opaqueBounds(pixels, 40, 40)
        assertEquals(listOf(18, 18, 22, 22), bounds?.toList())
    }

    @Test
    fun `paddedSquareCrop tightens a sparse silhouette instead of leaving it at full canvas size`() {
        val bounds = intArrayOf(18, 18, 22, 22) // 4x4 ink in a 40x40 canvas
        val crop = paddedSquareCrop(bounds, 40, 40)
        val cropSide = crop[2] - crop[0]
        assertEquals(crop[3] - crop[1], cropSide) // square
        assertTrue("crop ($cropSide) should be much smaller than the full canvas (40)", cropSide < 20)
        assertTrue("crop should still fully contain the original bounds", crop[0] <= 18 && crop[2] >= 22)
    }

    @Test
    fun `paddedSquareCrop on a silhouette that already fills the canvas stays close to the full size`() {
        val bounds = intArrayOf(1, 1, 39, 39) // near-full 40x40 canvas
        val crop = paddedSquareCrop(bounds, 40, 40)
        val cropSide = crop[2] - crop[0]
        assertTrue("crop ($cropSide) should stay close to the full canvas (40)", cropSide >= 36)
    }

    @Test
    fun `paddedSquareCrop never exceeds the canvas bounds`() {
        val bounds = intArrayOf(0, 0, 40, 40) // exactly the full canvas
        val crop = paddedSquareCrop(bounds, 40, 40)
        assertTrue(crop[0] >= 0 && crop[1] >= 0 && crop[2] <= 40 && crop[3] <= 40)
    }

    @Test
    fun `end to end - a small logo on a big discarded fill crops to a small tight square`() {
        // Reproduces the actual reported bug: WhatsApp-shaped icon (a small
        // white "ink" mark on a solid-colour fill that gets discarded as
        // "field") ends up occupying only a small corner of its own canvas
        // once masked — opaqueBounds + paddedSquareCrop is what lets a caller
        // crop that down so ContentScale.Fit can scale it back up to fill the
        // tile's icon box the same way the plain full-colour icon already does.
        val size = 32
        val pixels = IntArray(size * size) { i ->
            val x = i % size
            val y = i / size
            if (x in 13..18 && y in 13..18) argb(255, 255, 255, 255) else argb(255, 20, 140, 60)
        }
        val mask = synthesizeMonochromeMask(pixels)
        val bounds = opaqueBounds(mask, size, size)
        assertTrue(bounds != null)
        val crop = paddedSquareCrop(bounds!!, size, size)
        val cropSide = crop[2] - crop[0]
        assertTrue("crop ($cropSide) should be meaningfully smaller than the full canvas ($size)", cropSide < size - 8)
    }
}
