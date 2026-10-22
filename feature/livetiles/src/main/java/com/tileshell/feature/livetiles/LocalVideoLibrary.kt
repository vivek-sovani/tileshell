package com.tileshell.feature.livetiles

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Point
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.LruCache
import android.util.Size
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One video found in a folder the user chose. [uri] is a document URI built
 * from that folder's tree grant, so reading it needs no storage permission.
 */
data class LocalVideo(
    val uri: Uri,
    val name: String,
    val mime: String,
    val modified: Long,
    val folder: String,
)

/** A video the user opened from the hub, kept so "recent" can list it again. */
data class VideoRecent(val uri: String, val name: String, val mime: String, val openedAt: Long)

/**
 * The folders the user gave the videos page (persisted tree URIs from the
 * system folder picker) and the videos opened from it. Plain preferences, on
 * the phone only; a video file itself is never copied.
 */
object VideoPrefs {
    private const val PREFS = "tileshell.prefs"
    private const val KEY_FOLDERS = "video_folders"
    private const val KEY_RECENTS = "video_recents"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun folders(context: Context): List<Uri> =
        prefs(context).getString(KEY_FOLDERS, "").orEmpty()
            .split('\n').filter { it.isNotBlank() }.map(Uri::parse)

    fun addFolder(context: Context, tree: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val next = (folders(context) + tree).distinct()
        prefs(context).edit().putString(KEY_FOLDERS, next.joinToString("\n")).apply()
    }

    fun removeFolder(context: Context, tree: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val next = folders(context).filter { it != tree }
        prefs(context).edit().putString(KEY_FOLDERS, next.joinToString("\n")).apply()
    }

    fun recents(context: Context): List<VideoRecent> =
        decodeVideoRecents(prefs(context).getString(KEY_RECENTS, "").orEmpty())

    fun recordOpened(context: Context, video: LocalVideo, now: Long = System.currentTimeMillis()) {
        val next = pushVideoRecent(
            recents(context),
            VideoRecent(video.uri.toString(), video.name, video.mime, now),
        )
        prefs(context).edit().putString(KEY_RECENTS, encodeVideoRecents(next)).apply()
    }
}

const val MAX_VIDEO_RECENTS = 20

/** The newest first list with [entry] on top and any earlier copy of it dropped. */
fun pushVideoRecent(list: List<VideoRecent>, entry: VideoRecent, max: Int = MAX_VIDEO_RECENTS): List<VideoRecent> =
    (listOf(entry) + list.filter { it.uri != entry.uri }).take(max)

fun encodeVideoRecents(list: List<VideoRecent>): String =
    list.joinToString("\n") { listOf(it.openedAt, it.uri, it.name, it.mime).joinToString("\t") { f -> f.toString().replace('\t', ' ').replace('\n', ' ') } }

/** Tolerant of a damaged line: it is skipped, the rest still read. */
fun decodeVideoRecents(text: String): List<VideoRecent> =
    text.split('\n').mapNotNull { line ->
        val f = line.split('\t')
        val at = f.getOrNull(0)?.toLongOrNull()
        if (f.size < 4 || at == null || f[1].isBlank()) null else VideoRecent(f[1], f[2], f[3], at)
    }

/** "1:12:08" with hours, "3:15" without — a video can be hours long. */
fun formatVideoDuration(durationMs: Long): String {
    val total = (durationMs / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}

/** A video's own thumbnail and length, found once per file and kept. */
class VideoMeta(val thumb: Bitmap?, val durationMs: Long)

object LocalVideoLibrary {
    private const val MAX_DEPTH = 4
    private const val MAX_VIDEOS = 1000
    private val META_CACHE = LruCache<String, VideoMeta>(160)

    /** Name of the folder the user picked, for the folders list. */
    fun folderLabel(context: Context, tree: Uri): String = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: tree.lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: "folder"

    /** Every video under the chosen folders, newest first. Off the main thread. */
    suspend fun scan(context: Context, trees: List<Uri>): List<LocalVideo> = withContext(Dispatchers.IO) {
        val out = ArrayList<LocalVideo>()
        for (tree in trees) {
            if (out.size >= MAX_VIDEOS) break
            runCatching {
                val rootId = DocumentsContract.getTreeDocumentId(tree)
                walk(context, tree, rootId, folderLabel(context, tree), 0, out)
            }
        }
        out.distinctBy { it.uri }.sortedByDescending { it.modified }
    }

    private fun walk(context: Context, tree: Uri, parentId: String, folder: String, depth: Int, out: MutableList<LocalVideo>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val subfolders = ArrayList<Pair<String, String>>()
        context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext() && out.size < MAX_VIDEOS) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: continue
                when {
                    mime == DocumentsContract.Document.MIME_TYPE_DIR ->
                        if (depth < MAX_DEPTH && !name.startsWith(".")) subfolders += id to name
                    mime.startsWith("video/") ->
                        out += LocalVideo(
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                            name = name,
                            mime = mime,
                            modified = c.getLong(3),
                            folder = folder,
                        )
                }
            }
        }
        for ((id, name) in subfolders) {
            if (out.size >= MAX_VIDEOS) break
            runCatching { walk(context, tree, id, name, depth + 1, out) }
        }
    }

    /** Thumbnail and length for [video]; null thumbnail when the file gives no frame. */
    suspend fun meta(context: Context, video: LocalVideo): VideoMeta {
        val key = video.uri.toString()
        META_CACHE.get(key)?.let { return it }
        val meta = withContext(Dispatchers.IO) {
            var duration = 0L
            var thumb: Bitmap? = runCatching {
                if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(video.uri, Size(480, 300), null)
                else DocumentsContract.getDocumentThumbnail(context.contentResolver, video.uri, Point(480, 300), null)
            }.getOrNull()
            runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, video.uri)
                    duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    if (thumb == null) thumb = r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    r.release()
                }
            }
            VideoMeta(thumb, duration)
        }
        META_CACHE.put(key, meta)
        return meta
    }

    /**
     * Opens [video] in whatever app the phone has set for video (the system's
     * own choice, or its chooser when none is set), with read access granted
     * to just this file.
     */
    fun open(context: Context, video: LocalVideo) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(video.uri, video.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onSuccess { VideoPrefs.recordOpened(context, video) }
            .onFailure { Toast.makeText(context, "no video player found", Toast.LENGTH_SHORT).show() }
    }
}
