package com.tileshell.feature.livetiles

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
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

/**
 * Read-only [MediaStore] browsing for the music hub's "library" page — tracks,
 * albums and playlists already on the device, no network. Needs
 * `READ_MEDIA_AUDIO` (API 33+) or `READ_EXTERNAL_STORAGE` (below it), asked
 * contextually by the library page itself, not upfront at app start (there's
 * no justification for the ask before the user has ever opened it — same
 * reasoning `StepsTile`'s `ACTIVITY_RECOGNITION` ask already follows).
 *
 * [playlists] is best-effort: `MediaStore.Audio.Playlists` is a legacy
 * provider most apps have stopped writing to since scoped storage, so on many
 * modern devices/OEM skins this simply comes back empty — that's a real
 * absence of data, not a bug, and the library page shows an empty state for it
 * rather than an error.
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
}
