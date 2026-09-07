package com.tileshell.feature.start.feed

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// Small process-wide cache of decoded thumbnails so a slide back to the feed (or a
// recompose) doesn't refetch. Bounded by count — thumbnails are small.
private val thumbnailCache = LruCache<String, Bitmap>(48)

/**
 * Loads and decodes a remote article thumbnail [url] off the main thread, returning
 * null until it is ready (and on any failure, so the card degrades to no image).
 * Decoded bitmaps are cached process-wide by URL (in memory) and the downloaded
 * bytes are cached on disk ([diskCacheGet]/[diskCachePut]) — added after a real
 * on-device battery diagnosis found the news feed's own refresh cadence to be a
 * large contributor to TileShell's own radio time; thumbnails are re-fetched over
 * the network only once, ever, per url, not once per cold process. No third-party
 * image library — a plain `HttpURLConnection` + `BitmapFactory`, downsampled to a
 * sensible width.
 */
@Composable
fun rememberRemoteImage(url: String?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf(url?.let { thumbnailCache.get(it) }) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank() || bitmap != null) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) { fetchBitmap(context, url) }
        if (decoded != null) {
            thumbnailCache.put(url, decoded)
            bitmap = decoded
        }
    }
    return bitmap?.asImageBitmap()
}

private fun fetchBitmap(context: Context, rawUrl: String): Bitmap? = runCatching {
    val bytes = diskCacheGet(context, rawUrl)
        ?: readBytesFollowingRedirects(rawUrl)?.also { diskCachePut(context, rawUrl, it) }
        ?: return null
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

// -- Bounded on-disk cache -----------------------------------------------------
//
// Lives under Context.cacheDir (not filesDir): this is purely a re-derivable
// network cache, exactly the kind of content cacheDir exists for — the OS can
// reclaim it under storage pressure with no data loss, unlike filesDir.

private const val DISK_CACHE_SUBDIR = "feed_images"
private const val DISK_CACHE_MAX_BYTES = 20L * 1024 * 1024

private fun diskCacheDir(context: Context): File =
    File(context.cacheDir, DISK_CACHE_SUBDIR).apply { mkdirs() }

/** Filesystem-safe, fixed-length key for an arbitrary image URL. */
private fun cacheKeyFor(url: String): String =
    MessageDigest.getInstance("MD5").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

private fun diskCacheGet(context: Context, url: String): ByteArray? {
    val file = File(diskCacheDir(context), cacheKeyFor(url))
    if (!file.isFile) return null
    return runCatching {
        // Touch on read so eviction below is genuine least-recently-*used*,
        // not just least-recently-*downloaded*.
        file.setLastModified(System.currentTimeMillis())
        file.readBytes()
    }.getOrNull()
}

private fun diskCachePut(context: Context, url: String, bytes: ByteArray) {
    runCatching {
        val dir = diskCacheDir(context)
        File(dir, cacheKeyFor(url)).writeBytes(bytes)
        trimDiskCache(dir)
    }
}

/**
 * Evicts oldest-touched files once the cache directory's total size exceeds
 * [DISK_CACHE_MAX_BYTES]. Run inline after every write rather than on a
 * schedule — at feed-thumbnail volume (a few dozen images a day, each well
 * under 100 KB) an extra directory listing per write is negligible, and it
 * keeps the cache self-bounding with no separate cleanup job to maintain.
 */
private fun trimDiskCache(dir: File) {
    val files = dir.listFiles() ?: return
    var total = files.sumOf { it.length() }
    if (total <= DISK_CACHE_MAX_BYTES) return
    files.sortedBy { it.lastModified() }.forEach { file ->
        if (total <= DISK_CACHE_MAX_BYTES) return
        total -= file.length()
        file.delete()
    }
}
