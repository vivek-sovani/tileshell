package com.tileshell.feature.livetiles

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One on-device audio file, as browsed by the music hub's "library" page. */
data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
) {
    val contentUri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}

data class LocalAlbum(val id: Long, val title: String, val artist: String, val trackCount: Int)

data class LocalPlaylist(val id: Long, val name: String)

/** Result of [LocalMusicLibrary.addTrackToPlaylist] — lets the caller tell a
 * fresh add apart from a no-op repeat and word its own confirmation off it. */
enum class AddTrackResult { ADDED, ALREADY_PRESENT, FAILED }

/**
 * [MediaStore] browsing for the music hub's "library" page — tracks, albums
 * and playlists already on the device, no network. Needs `READ_MEDIA_AUDIO`
 * (API 33+) or `READ_EXTERNAL_STORAGE` (below it), asked contextually by the
 * library page itself, not upfront at app start (there's no justification for
 * the ask before the user has ever opened it — same reasoning `StepsTile`'s
 * `ACTIVITY_RECOGNITION` ask already follows).
 *
 * [playlists] can come back empty on a device that has never had one created
 * through any app — `MediaStore.Audio.Playlists` is a legacy provider most
 * apps stopped *reading themselves back from* after scoped storage landed
 * (they moved to their own private playlist stores instead), so it's
 * genuinely quiet on a lot of devices until something writes to it. It does
 * still work for both reading and writing on current Android (verified
 * end-to-end — create/rename/delete/add-member/remove-member — against a
 * real API 36 device), which is why [createPlaylist] and friends below write
 * through this same table rather than a separate TileShell-only store: a
 * playlist created here is a real device playlist, visible to any other app
 * that still reads this table too. Create/rename/delete operate on the
 * playlist's own item `Uri` (`.../playlists/<id>`, not the bulk collection
 * `Uri` with a `WHERE` clause) — confirmed live that update/delete throws
 * `IllegalArgumentException: ... isn't part of well-defined collection` under
 * scoped storage when aimed at the collection `Uri` instead, even though
 * insert and the members sub-collection tolerate it fine.
 */
object LocalMusicLibrary {

    suspend fun tracks(context: Context): List<LocalTrack> = withContext(Dispatchers.IO) {
        val out = mutableListOf<LocalTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    out += LocalTrack(
                        id = cursor.getLong(idCol),
                        title = cursor.getString(titleCol) ?: "untitled",
                        artist = cursor.getString(artistCol) ?: "",
                        album = cursor.getString(albumCol) ?: "",
                        albumId = cursor.getLong(albumIdCol),
                        durationMs = cursor.getLong(durationCol),
                    )
                }
            }
        }
        out
    }

    /** A single track by its [MediaStore] id, or null if it no longer exists — used to
     * replay a "history" entry (the file may since have been deleted/moved). */
    suspend fun trackById(context: Context, id: Long): LocalTrack? = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media._ID} = ?",
                arrayOf(id.toString()),
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                LocalTrack(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)),
                    title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "untitled",
                    artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "",
                    album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "",
                    albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)),
                    durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                )
            }
        }.getOrNull()
    }

    /** A single track by exact title+artist — the fallback for a "history" entry
     * recorded before [PlayedTrack.localTrackId] existed, so those older rows
     * stay replayable too instead of going permanently dead. */
    suspend fun findByTitleArtist(context: Context, title: String, artist: String): LocalTrack? =
        withContext(Dispatchers.IO) {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
            )
            runCatching {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Audio.Media.TITLE} = ? AND ${MediaStore.Audio.Media.ARTIST} = ?",
                    arrayOf(title, artist),
                    null,
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    LocalTrack(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)),
                        title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "untitled",
                        artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "",
                        album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "",
                        albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)),
                        durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                    )
                }
            }.getOrNull()
        }

    suspend fun albums(context: Context): List<LocalAlbum> = withContext(Dispatchers.IO) {
        val out = mutableListOf<LocalAlbum>()
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS,
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Albums.ALBUM} ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
                val countCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
                while (cursor.moveToNext()) {
                    out += LocalAlbum(
                        id = cursor.getLong(idCol),
                        title = cursor.getString(titleCol) ?: "untitled",
                        artist = cursor.getString(artistCol) ?: "",
                        trackCount = cursor.getInt(countCol),
                    )
                }
            }
        }
        out
    }

    suspend fun playlists(context: Context): List<LocalPlaylist> = withContext(Dispatchers.IO) {
        val out = mutableListOf<LocalPlaylist>()
        val projection = arrayOf(MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME)
        runCatching {
            @Suppress("DEPRECATION")
            context.contentResolver.query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Playlists.NAME} ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME)
                while (cursor.moveToNext()) {
                    out += LocalPlaylist(id = cursor.getLong(idCol), name = cursor.getString(nameCol) ?: "untitled")
                }
            }
        }
        out
    }

    suspend fun tracksForAlbum(context: Context, albumId: Long): List<LocalTrack> =
        tracks(context).filter { it.albumId == albumId }

    suspend fun tracksForPlaylist(context: Context, playlistId: Long): List<LocalTrack> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<LocalTrack>()
            @Suppress("DEPRECATION")
            val membersUri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)
            val projection = arrayOf(
                MediaStore.Audio.Playlists.Members.AUDIO_ID,
                MediaStore.Audio.Playlists.Members.TITLE,
                MediaStore.Audio.Playlists.Members.ARTIST,
                MediaStore.Audio.Playlists.Members.ALBUM,
                MediaStore.Audio.Playlists.Members.ALBUM_ID,
                MediaStore.Audio.Playlists.Members.DURATION,
            )
            runCatching {
                @Suppress("DEPRECATION")
                val playOrder = MediaStore.Audio.Playlists.Members.PLAY_ORDER
                context.contentResolver.query(membersUri, projection, null, null, "$playOrder ASC")?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.ALBUM)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.ALBUM_ID)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.DURATION)
                    while (cursor.moveToNext()) {
                        out += LocalTrack(
                            id = cursor.getLong(idCol),
                            title = cursor.getString(titleCol) ?: "untitled",
                            artist = cursor.getString(artistCol) ?: "",
                            album = cursor.getString(albumCol) ?: "",
                            albumId = cursor.getLong(albumIdCol),
                            durationMs = cursor.getLong(durationCol),
                        )
                    }
                }
            }
            out
        }

    /** Creates a new, empty playlist named [name] (trimmed; a blank name is
     * rejected — returns null without writing anything). Returns the new
     * playlist's id, so the caller can navigate straight into it. */
    @Suppress("DEPRECATION")
    suspend fun createPlaylist(context: Context, name: String): Long? = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext null
        runCatching {
            val nowSeconds = System.currentTimeMillis() / 1000
            val values = ContentValues().apply {
                put(MediaStore.Audio.Playlists.NAME, trimmed)
                put(MediaStore.Audio.Playlists.DATE_ADDED, nowSeconds)
                put(MediaStore.Audio.Playlists.DATE_MODIFIED, nowSeconds)
            }
            val uri = context.contentResolver.insert(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull()
        }.getOrNull()
    }

    /** Renames playlist [playlistId] to [name] (trimmed; blank is rejected).
     * True if a row was actually updated. */
    @Suppress("DEPRECATION")
    suspend fun renamePlaylist(context: Context, playlistId: Long, name: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext false
        runCatching {
            val values = ContentValues().apply { put(MediaStore.Audio.Playlists.NAME, trimmed) }
            val itemUri = ContentUris.withAppendedId(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, playlistId)
            context.contentResolver.update(itemUri, values, null, null) > 0
        }.getOrElse { false }
    }

    /** Deletes playlist [playlistId] itself (not the tracks in it — just the
     * playlist and its membership rows, same as deleting it in any other
     * music app). True if a row was actually deleted. */
    @Suppress("DEPRECATION")
    suspend fun deletePlaylist(context: Context, playlistId: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val itemUri = ContentUris.withAppendedId(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, playlistId)
            context.contentResolver.delete(itemUri, null, null) > 0
        }.getOrElse { false }
    }

    /** Appends [trackId] to the end of playlist [playlistId] — a no-op
     * ([AddTrackResult.ALREADY_PRESENT]) if it's already in there. Nothing in
     * this app's playlist UI shows whether a track is already in a given
     * playlist before you tap "add," so silently inserting a second copy
     * would just look like the tap did nothing new; the caller words its
     * confirmation off this result instead. */
    @Suppress("DEPRECATION")
    suspend fun addTrackToPlaylist(context: Context, playlistId: Long, trackId: Long): AddTrackResult =
        withContext(Dispatchers.IO) {
            val existing = tracksForPlaylist(context, playlistId)
            if (existing.any { it.id == trackId }) return@withContext AddTrackResult.ALREADY_PRESENT
            runCatching {
                val membersUri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Playlists.Members.AUDIO_ID, trackId)
                    // 1-based: the provider inserts a member at position
                    // PLAY_ORDER − 1, so `existing.size` (0-based) landed the
                    // new track *before* the current last one (seen on-device).
                    put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, existing.size + 1)
                }
                if (context.contentResolver.insert(membersUri, values) != null) {
                    AddTrackResult.ADDED
                } else {
                    AddTrackResult.FAILED
                }
            }.getOrElse { AddTrackResult.FAILED }
        }

    /**
     * Removes every occurrence of [trackId] from [playlist] and returns the
     * playlist as it now exists — **under a new id**, since this rebuilds it:
     * deletes the playlist row, recreates it with the same name, and re-adds
     * every remaining track in order. Null if nothing changed or a step failed.
     *
     * Why not just delete the member row: on this device (Android 16, API 36)
     * a normal app's delete against `Playlists.Members` — the bulk
     * `audio_id = ?` form *and* the per-row `.../members/<_id>` item URI —
     * always matches zero rows and throws nothing, even on a playlist this
     * app owns (confirmed on-device via logging; only privileged `adb shell`
     * can delete members). Inserting members and deleting the whole playlist
     * both still work for an app, so removal is built from those two.
     */
    @Suppress("DEPRECATION")
    suspend fun rebuildPlaylistWithout(context: Context, playlist: LocalPlaylist, trackId: Long): LocalPlaylist? =
        withContext(Dispatchers.IO) {
            val current = tracksForPlaylist(context, playlist.id)
            val remaining = current.filterNot { it.id == trackId }
            if (remaining.size == current.size) return@withContext null
            if (!deletePlaylist(context, playlist.id)) return@withContext null
            val newId = createPlaylist(context, playlist.name) ?: return@withContext null
            runCatching {
                val membersUri = MediaStore.Audio.Playlists.Members.getContentUri("external", newId)
                remaining.forEachIndexed { index, track ->
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Playlists.Members.AUDIO_ID, track.id)
                        put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, index + 1)
                    }
                    context.contentResolver.insert(membersUri, values)
                }
            }
            LocalPlaylist(newId, playlist.name)
        }

    /**
     * An album's cover art, decoded off the main thread. Unlike an external
     * app's now-playing artwork, this needs no cross-app URI grant — it's our
     * own [READ_MEDIA_AUDIO]-covered query against MediaStore. API 29+ uses
     * the generic thumbnail loader (works directly on the album's own
     * content URI); below that, the legacy `ALBUM_ART` file-path column.
     * Null when the album genuinely has no art, not just on any failure the
     * caller can't distinguish.
     */
    suspend fun loadAlbumArt(context: Context, albumId: Long, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val albumUri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
                context.contentResolver.loadThumbnail(albumUri, Size(sizePx, sizePx), null)
            } else {
                @Suppress("DEPRECATION")
                val projection = arrayOf(MediaStore.Audio.Albums.ALBUM_ART)
                val path = context.contentResolver.query(
                    ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId),
                    projection,
                    null,
                    null,
                    null,
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                path?.let { BitmapFactory.decodeFile(it) }
            }
        }.getOrNull()
    }
}

/**
 * An album's cover art as an [ImageBitmap], cached per (albumId, sizePx) for
 * the composition's lifetime. Null while loading or when the album has none.
 */
@Composable
fun rememberLocalAlbumArt(context: Context, albumId: Long, sizePx: Int = 200): ImageBitmap? {
    val bitmap by produceState<Bitmap?>(initialValue = null, albumId, sizePx) {
        value = if (albumId <= 0) null else LocalMusicLibrary.loadAlbumArt(context, albumId, sizePx)
    }
    return bitmap?.asImageBitmap()
}
