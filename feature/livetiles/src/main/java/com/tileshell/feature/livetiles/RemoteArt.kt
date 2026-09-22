package com.tileshell.feature.livetiles

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
import java.net.URL

// Small process-wide cache so switching between the podcasts/radio pages and
// "now playing" doesn't re-fetch the same artwork/favicon repeatedly.
private val remoteArtCache = LruCache<String, Bitmap>(48)

/**
 * Loads and decodes a plain remote image [url] (a podcast/show's own artwork,
 * or a radio station's favicon) off the main thread — null until ready, and
 * on any failure, so the caller just shows its own no-art fallback. No disk
 * cache (unlike the feed's own `rememberRemoteImage`, `:feature:start` isn't
 * reachable from here anyway) — this is a much smaller, rarer volume of
 * images than a news feed's constant thumbnail churn, so an in-memory cache
 * alone is enough.
 */
@Composable
fun rememberRemoteArt(url: String?): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf(url?.let { remoteArtCache.get(it) }) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank() || bitmap != null) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) {
            runCatching { URL(url).openStream().use(BitmapFactory::decodeStream) }.getOrNull()
        }
        if (decoded != null) {
            remoteArtCache.put(url, decoded)
            bitmap = decoded
        }
    }
    return bitmap?.asImageBitmap()
}
