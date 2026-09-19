package com.tileshell.core.design

/**
 * Pure per-pixel synthesis of an "untinted alpha mask" — the same shape a
 * real Android 13+ adaptive-icon monochrome layer produces (see
 * `AdaptiveIconDrawable.getMonochrome()`) — from an arbitrary app icon's own
 * ARGB pixels, for the common case where the app declares no such layer at
 * all (most installed apps, per DECISIONS.md "Themed icons: parked" — the
 * whole reason that feature shipped once and was immediately turned back
 * off). Callers tint the result with a single accent colour at render time
 * (e.g. Compose's `ColorFilter.tint`), exactly as they already do for a real
 * monochrome layer — this function only ever emits white-at-some-alpha
 * pixels, never colour, so both sources share one render path.
 *
 * [pixels] is a flattened ARGB_8888 bitmap in `Bitmap.getPixels`'s own packed
 * `0xAARRGGBB` int format — deliberately no `android.graphics`/Compose
 * `Color` import here, so this stays a plain, unit-testable Kotlin function
 * (this module has no Robolectric dependency; a real `Bitmap` can't be
 * exercised in a JVM unit test).
 *
 * Two source shapes need different treatment. An adaptive icon's isolated
 * *foreground* layer already carries real alpha transparency outside its own
 * glyph — the OS's adaptive-icon safe-zone convention requires it — so its
 * alpha channel alone is already the correct silhouette; used as-is whenever
 * the source [hasMeaningfulTransparency]. A fully (or nearly fully) opaque
 * source — a flat legacy square icon, or an adaptive foreground that bakes
 * its own background fill in — has no transparency to read, so the mask is
 * instead derived from luminance: [otsuThreshold] splits the icon's pixels
 * into two luminance clusters, and whichever cluster is the *minority* by
 * pixel count is treated as the "ink" (the glyph/wordmark), the majority as
 * the transparent field. This is deliberately not "darker half is ink" —  a
 * first version compared each pixel's luma only to the whole image's
 * average, which silently inverted on any icon whose fill colour reads
 * moderately bright (an orange/red/pink background with white text, e.g. a
 * "HP"/"Kissan Connect"/"Sadhguru"-style logo — user-reported "details
 * lost"): the coloured fill dominates by area and pulls the average toward
 * "light", so the old rule wrongly picked the fill as ink and made the
 * actual white wordmark vanish. A logo mark or wordmark is nearly always the
 * minority-area content sitting on a majority-area fill, regardless of which
 * one is lighter, which is what minority-cluster selection captures instead.
 * Luma weights match [perceivedLuminance]'s 299/587/114.
 *
 * The luminance split itself only ever looks at substantially opaque pixels
 * (alpha >= 128) — user-reported "BOBCARD" still rendered fully blank even
 * after the fixes above: that icon is genuinely close to a 50/50 split
 * between real transparency (padding, not meeting the transparent-majority
 * bar [hasMeaningfulTransparency] requires) and real opaque content. A
 * transparent pixel's RGB is typically meaningless (often literal black,
 * whatever the decoder leaves for alpha-0), and folding those pixels into
 * the same histogram as the real content skewed Otsu's split itself: with
 * roughly half "black" transparent padding and half opaque content, the
 * padding alone formed the low-luma cluster, so minority-cluster selection
 * picked the transparent padding as "ink" — which the final alpha multiply
 * correctly zeroes out, but that left the real, majority-classified,
 * genuinely-visible content with "field" opacity too, i.e. also zero. The
 * whole icon rendered blank. Restricting classification to the opaque
 * subset means the split is always computed from pixels that actually
 * carry colour information.
 */
fun synthesizeMonochromeMask(pixels: IntArray): IntArray {
    if (pixels.isEmpty()) return pixels
    if (hasMeaningfulTransparency(pixels)) {
        return IntArray(pixels.size) { i ->
            val alpha = (pixels[i] ushr 24) and 0xFF
            (alpha shl 24) or 0xFFFFFF
        }
    }
    val lumas = IntArray(pixels.size)
    var opaqueLumaCount = 0
    for (i in pixels.indices) {
        val p = pixels[i]
        val a = (p ushr 24) and 0xFF
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF
        lumas[i] = (r * 299 + g * 587 + b * 114) / 1000
        if (a >= 128) opaqueLumaCount++
    }
    if (opaqueLumaCount == 0) {
        // Nothing substantially opaque at all — no real content to show.
        return IntArray(pixels.size)
    }
    val opaqueLumas = IntArray(opaqueLumaCount)
    var w = 0
    for (i in pixels.indices) {
        if (((pixels[i] ushr 24) and 0xFF) >= 128) opaqueLumas[w++] = lumas[i]
    }
    val threshold = otsuThreshold(opaqueLumas)
    var lowCount = 0
    var minLuma = 255
    var maxLuma = 0
    for (l in opaqueLumas) {
        if (l <= threshold) lowCount++
        if (l < minLuma) minLuma = l
        if (l > maxLuma) maxLuma = l
    }
    val highCount = opaqueLumas.size - lowCount
    val inkIsLow = lowCount <= highCount
    // Contrast-stretched within the ink cluster only, so a low-contrast logo
    // (the ink and field close in luma) still reaches full 0..255 opacity
    // instead of a washed, barely-visible mid-alpha result — the other
    // failure mode behind "details lost". A single-valued cluster (span 0)
    // renders at full opacity rather than dividing by zero.
    val lowSpan = threshold - minLuma
    val highSpan = maxLuma - threshold
    return IntArray(pixels.size) { i ->
        val origAlpha = (pixels[i] ushr 24) and 0xFF
        val luma = lumas[i]
        val opacity = if (inkIsLow) {
            if (luma <= threshold) {
                if (lowSpan <= 0) 255 else (((threshold - luma) * 255) / lowSpan).coerceIn(0, 255)
            } else {
                0
            }
        } else {
            if (luma >= threshold) {
                if (highSpan <= 0) 255 else (((luma - threshold) * 255) / highSpan).coerceIn(0, 255)
            } else {
                0
            }
        }
        val alpha = (origAlpha * opacity) / 255
        (alpha shl 24) or 0xFFFFFF
    }
}

/**
 * True when [pixels]' alpha channel is essentially uniform across the whole
 * image — carrying no usable shape signal at all (entirely opaque, or
 * entirely transparent). Used to sanity-check an app's own Android 13+
 * monochrome layer before trusting it: user-reported Google Drive renders
 * as a solid filled plate with zero visible glyph even though it declares a
 * real monochrome layer (confirmed on-device — the drawn 96×96 bitmap is
 * uniformly opaque end to end, for reasons specific to that Drawable's own
 * rendering rather than anything this app controls). Callers fall back to
 * [synthesizeMonochromeMask] on the ordinary icon pixels when this is true,
 * rather than trusting a declared-but-broken native layer blindly.
 */
fun isUniformAlpha(pixels: IntArray): Boolean {
    if (pixels.isEmpty()) return true
    var minAlpha = 255
    var maxAlpha = 0
    for (p in pixels) {
        val a = (p ushr 24) and 0xFF
        if (a < minAlpha) minAlpha = a
        if (a > maxAlpha) maxAlpha = a
    }
    return (maxAlpha - minAlpha) < 10
}

/**
 * Otsu's method: the luminance threshold (0..255) that best separates
 * [lumas] into two clusters, by maximizing between-cluster variance. Standard
 * histogram-based algorithm — see [synthesizeMonochromeMask]'s doc comment
 * for why a single global average was not good enough on its own.
 */
internal fun otsuThreshold(lumas: IntArray): Int {
    if (lumas.isEmpty()) return 127
    val histogram = IntArray(256)
    for (l in lumas) histogram[l.coerceIn(0, 255)]++
    val total = lumas.size
    var sum = 0.0
    for (i in 0..255) sum += i.toDouble() * histogram[i]
    var sumB = 0.0
    var weightB = 0
    var maxVariance = -1.0
    var threshold = 127
    for (i in 0..255) {
        weightB += histogram[i]
        if (weightB == 0) continue
        val weightF = total - weightB
        if (weightF == 0) break
        sumB += i.toDouble() * histogram[i]
        val meanB = sumB / weightB
        val meanF = (sum - sumB) / weightF
        val variance = weightB.toDouble() * weightF.toDouble() * (meanB - meanF) * (meanB - meanF)
        if (variance > maxVariance) {
            maxVariance = variance
            threshold = i
        }
    }
    return threshold
}

/**
 * True when [pixels]' transparency pattern itself looks like a meaningful
 * glyph silhouette — a genuinely transparent *majority* with an opaque
 * minority sitting on it (a small icon comfortably inset on a mostly-empty
 * canvas). Deliberately stricter than "some of both": a plain rounded or
 * circular legacy icon — the overwhelmingly common shape for a non-adaptive
 * icon — is itself ~75-95% opaque with only thin transparent corner
 * rounding, which is real transparency but carries no glyph information at
 * all; treating that as "meaningful" and using it as the final silhouette
 * produces a flat, detail-free blob (user-reported "details lost" on
 * Sadhguru/Kissan Connect, both plain rounded legacy icons — confirmed via
 * on-device instrumentation logging exactly this alpha pattern). Requiring
 * transparent pixels to outnumber opaque ones excludes that shape while
 * still catching a real "small glyph on a big transparent field" design.
 */
internal fun hasMeaningfulTransparency(pixels: IntArray): Boolean {
    var transparentCount = 0
    var opaqueCount = 0
    for (p in pixels) {
        val a = (p ushr 24) and 0xFF
        if (a < 40) transparentCount++ else if (a > 215) opaqueCount++
    }
    val minMeaningful = (pixels.size * 0.03f).toInt().coerceAtLeast(1)
    return transparentCount >= minMeaningful && opaqueCount >= minMeaningful && transparentCount > opaqueCount
}
