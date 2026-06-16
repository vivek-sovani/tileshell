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

private fun fetchBitmap(rawUrl: String): Bitmap? = runCatching {
    val bytes = readBytesFollowingRedirects(rawUrl) ?: return null
    // Downsample to ~720px wide — feed cards are ≤ screen width.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, 720) }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}.getOrNull()

/**
 * GETs [rawUrl], following up to 4 redirects manually so cross-protocol hops
 * (http↔https) — which `HttpURLConnection` refuses to auto-follow and which many
 * news image CDNs use — are handled. Returns the body bytes, or null.
 */
private fun readBytesFollowingRedirects(rawUrl: String): ByteArray? {
    var url = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
    repeat(4) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) TileShell/1.0")
            setRequestProperty("Accept", "image/*,*/*")
        }
        try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> return conn.inputStream.use { it.readBytes() }
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER,
                307,
                308,
                -> {
                    val location = conn.getHeaderField("Location") ?: return null
                    url = URL(URL(url), location).toString()
                }
                else -> return null
            }
        } finally {
            conn.disconnect()
        }
    }
    return null
}

private fun sampleSizeFor(srcWidth: Int, targetWidth: Int): Int {
    if (srcWidth <= 0 || targetWidth <= 0) return 1
    var sample = 1
    while (srcWidth / (sample * 2) >= targetWidth) sample *= 2
    return sample
}
