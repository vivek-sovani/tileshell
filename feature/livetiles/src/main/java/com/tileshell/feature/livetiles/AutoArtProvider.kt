package com.tileshell.feature.livetiles

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.io.FileOutputStream

/**
 * Serves local album art to Android Auto, which can't read MediaStore itself.
 * `content://<package>.autoart/album/<albumId>` and, for podcast and radio
 * images, `.../remote/<https url>`. Exported because the car's
 * host is another app, but only [AutoCallerPolicy] callers get a file.
 */
class AutoArtProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: throw FileNotFoundException()
        val uid = Binder.getCallingUid()
        if (uid != Process.myUid()) {
            val packages = context.packageManager.getPackagesForUid(uid)?.toList().orEmpty()
            val claimed = callingPackage ?: throw FileNotFoundException()
            if (!AutoCallerPolicy.isAllowed(claimed, uid, packages, context.packageName)) throw FileNotFoundException()
        }
        val segments = uri.pathSegments
        val bitmap = when {
            segments.size == 2 && segments[0] == "album" -> {
                val albumId = segments[1].toLongOrNull() ?: throw FileNotFoundException()
                runBlocking { LocalMusicLibrary.loadAlbumArt(context, albumId, ART_SIZE_PX) }
            }
            segments.size == 2 && segments[0] == "remote" -> remoteArt(context, segments[1])
            else -> null
        } ?: throw FileNotFoundException()
        val pipe = ParcelFileDescriptor.createPipe()
        Thread {
            runCatching {
                FileOutputStream(pipe[1].fileDescriptor).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            }
            runCatching { pipe[1].close() }
        }.start()
        return pipe[0]
    }

    /** A podcast/station image, downloaded once (https only), shrunk and cached on disk. */
    private fun remoteArt(context: Context, url: String): Bitmap? {
        if (!url.startsWith("https://")) return null
        val dir = File(context.cacheDir, "autoart").apply { mkdirs() }
        val file = File(dir, url.hashCode().toString() + ".jpg")
        if (!file.exists()) {
            val bytes = runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.inputStream.use { it.readNBytesCapped(MAX_REMOTE_BYTES) }
            }.getOrNull() ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= ART_SIZE_PX) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            FileOutputStream(file).use { decoded.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
        return BitmapFactory.decodeFile(file.path)
    }

    private fun java.io.InputStream.readNBytesCapped(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() <= max) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        if (out.size() > max) throw java.io.IOException("too large")
        return out.toByteArray()
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String = "image/jpeg"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    private companion object {
        const val ART_SIZE_PX = 256
        const val MAX_REMOTE_BYTES = 3_000_000
    }
}
