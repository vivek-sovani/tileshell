package com.tileshell.feature.livetiles

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * Serves local album art to Android Auto, which can't read MediaStore itself.
 * `content://<package>.autoart/album/<albumId>`. Exported because the car's
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
        val albumId = if (segments.size == 2 && segments[0] == "album") segments[1].toLongOrNull() else null
        albumId ?: throw FileNotFoundException()
        val bitmap = runBlocking { LocalMusicLibrary.loadAlbumArt(context, albumId, ART_SIZE_PX) }
            ?: throw FileNotFoundException()
        val pipe = ParcelFileDescriptor.createPipe()
        Thread {
            runCatching {
                FileOutputStream(pipe[1].fileDescriptor).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
            }
            runCatching { pipe[1].close() }
        }.start()
        return pipe[0]
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String = "image/jpeg"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    private companion object {
        const val ART_SIZE_PX = 256
    }
}
