package com.tileshell.feature.start

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Copies images the user picks (via the gallery photo picker) into the app's own
 * private storage so the wallpaper / live-photos selection survives a reboot.
 *
 * The gallery photo picker ([androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia])
 * gives a much nicer "open the phone's gallery" experience than the SAF document
 * browser, but — unlike `ACTION_OPEN_DOCUMENT` — its read grant is temporary and
 * cannot be persisted with `takePersistableUriPermission`. So rather than hold a
 * grant on the source image, we copy the bytes once into `filesDir` and store a
 * `file://` URI to our own copy. Reading it later via `contentResolver.openInputStream`
 * works for our own files with no permission, so the choice persists across reboots
 * and process death (supersedes the persistable-grant approach in DECISIONS S18/S23).
 */
object MediaImport {

    private const val WALLPAPER_DIR = "wallpaper"
    private const val PHOTOS_DIR = "livephotos"

    /**
     * Copies a picked wallpaper image into private storage, replacing any previous
     * one, and returns a `file://` URI to the copy (null if the copy failed). The
     * filename is timestamped so the URI changes on each pick — this busts the
     * bitmap cache keyed on the URI string, so re-picking actually re-decodes.
     * Call off the main thread.
     */
    fun importWallpaper(context: Context, source: Uri): Uri? {
        val dir = File(context.filesDir, WALLPAPER_DIR)
        clearDir(dir)
        dir.mkdirs()
        val dest = File(dir, "wp_${System.currentTimeMillis()}.jpg")
        return copy(context, source, dest)
    }

    /**
     * Copies the picked live-photos into private storage, replacing the previous
     * selection, and returns `file://` URIs to the copies (in pick order; failed
     * copies are dropped). Call off the main thread.
     */
    fun importPhotos(context: Context, sources: List<Uri>): List<String> {
        val dir = File(context.filesDir, PHOTOS_DIR)
        clearDir(dir)
        dir.mkdirs()
        val now = System.currentTimeMillis()
        return sources.mapIndexedNotNull { i, source ->
            copy(context, source, File(dir, "p_${now}_$i.jpg"))?.toString()
        }
    }

    /** Deletes all imported live-photos (used by "clear selected photos"). */
    fun clearPhotos(context: Context) {
        clearDir(File(context.filesDir, PHOTOS_DIR))
    }

    private fun copy(context: Context, source: Uri, dest: File): Uri? = runCatching {
        context.contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        Uri.fromFile(dest)
    }.getOrNull()

    private fun clearDir(dir: File) {
        if (dir.isDirectory) dir.listFiles()?.forEach { it.delete() }
    }
}
