package com.tileshell.feature.start.feed

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// Small process-wide cache of decoded thumbnails so a slide back to the feed (or a
// recompose) doesn't refetch. Bounded by count — thumbnails are small.
private val thumbnailCache = LruCache<String, Bitmap>(48)

/**
 * Loads and decodes a remote article thumbnail [url] off the main thread, returning
 * null until it is ready (and on any failure, so the card degrades to no image).
 * Decoded bitmaps are cached process-wide by URL. No third-party image library —
 * a plain `HttpURLConnection` + `BitmapFactory`, downsampled to a sensible width.
 */
@Composable
fun rememberRemoteImage(url: String?): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf(url?.let { thumbnailCache.get(it) }) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank() || bitmap != null) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) { fetchBitmap(url) }
        if (decoded != null) {
            thumbnailCache.put(url, decoded)
            bitmap = decoded
        }
    }
    return bitmap?.asImageBitmap()
}

private fun fetchBitmap(url: String): Bitmap? = runCatching {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 8_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "TileShell/1.0")
    }
    try {
        if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
        val bytes = conn.inputStream.use { it.readBytes() }
        // Downsample to ~720px wide — feed cards are ≤ screen width.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, 720) }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } finally {
        conn.disconnect()
    }
}.getOrNull()

private fun sampleSizeFor(srcWidth: Int, targetWidth: Int): Int {
    if (srcWidth <= 0 || targetWidth <= 0) return 1
    var sample = 1
    while (srcWidth / (sample * 2) >= targetWidth) sample *= 2
    return sample
}
