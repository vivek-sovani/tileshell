package com.tileshell.feature.livetiles

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes a content [uri] to a tile-sized [ImageBitmap] off the main thread,
 * down-sampled to roughly [targetPx] on its shorter side so a full-res photo
 * doesn't blow the bitmap budget when rendered in a small tile. Returns null
 * while loading or if the URI can't be read (revoked / deleted grant) — callers
 * fall back to the static glyph. Reloads only when [uri] changes.
 */
@Composable
fun rememberTileBitmap(uri: String, targetPx: Int = 400): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, uri, targetPx) {
        value = withContext(Dispatchers.IO) { decodeSampled(context, uri, targetPx) }
    }
    return image
}

private fun decodeSampled(context: Context, uri: String, targetPx: Int): ImageBitmap? = runCatching {
    val parsed = Uri.parse(uri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(parsed)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetPx)
    }
    context.contentResolver.openInputStream(parsed)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
    }
}.getOrNull()

/** Largest power-of-two sample size that keeps the shorter side >= [targetPx]. */
internal fun sampleSizeFor(width: Int, height: Int, targetPx: Int): Int {
    val shorter = minOf(width, height)
    if (shorter <= 0 || targetPx <= 0) return 1
    var sample = 1
    while (shorter / (sample * 2) >= targetPx) sample *= 2
    return sample
}
