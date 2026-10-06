package com.tileshell.feature.livetiles

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** [AutoLibrarySource] over the device's MediaStore, via [LocalMusicLibrary]. */
internal class LocalLibrarySource(private val context: Context) : AutoLibrarySource {
    override suspend fun hasAudioAccess(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun tracks() = LocalMusicLibrary.tracks(context)
    override suspend fun albums() = LocalMusicLibrary.albums(context)
    override suspend fun playlists() = LocalMusicLibrary.playlists(context)
    override suspend fun tracksForAlbum(albumId: Long) = LocalMusicLibrary.tracksForAlbum(context, albumId)
    override suspend fun tracksForPlaylist(playlistId: Long) = LocalMusicLibrary.tracksForPlaylist(context, playlistId)
}
