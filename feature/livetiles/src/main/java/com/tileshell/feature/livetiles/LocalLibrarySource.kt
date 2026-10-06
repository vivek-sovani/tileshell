package com.tileshell.feature.livetiles

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first

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

    override suspend fun subscriptions() = PodcastStore.subscriptions(context).first()
    override suspend fun recentEpisodes() = MusicRecents.episodes(context).first()
    override suspend fun favoriteStations() = RadioFavoritesStore.favorites(context).first()
    override suspend fun recentStations() = MusicRecents.stations(context).first()

    override suspend fun show(feedUrl: String): PodcastShow? {
        val now = System.currentTimeMillis()
        shows[feedUrl]?.takeIf { now - it.first < SHOW_CACHE_MS }?.let { return it.second }
        return fetchPodcastFeed(feedUrl)?.also { shows[feedUrl] = now to it }
    }

    private companion object {
        const val SHOW_CACHE_MS = 5 * 60_000L

        /** Process-wide, so paging through a long show doesn't refetch its feed each time. */
        val shows = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, PodcastShow>>()
    }
}
