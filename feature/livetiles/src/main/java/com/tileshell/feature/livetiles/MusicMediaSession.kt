package com.tileshell.feature.livetiles

import android.content.Context
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The one [MediaSessionCompat] for the music hub. Android Auto has to browse
 * before anything plays, so the session can't belong to
 * [LocalMusicPlaybackService] (which only lives while something is loaded):
 * [AutoMediaBrowserService] hands this token to the car, and the playback
 * service keeps it up to date and shows it in its notification. All transport
 * callbacks go straight to [LocalMusicPlayer].
 */
object MusicMediaSession {
    private var session: MediaSessionCompat? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** What the car may ask for even when nothing is playing. */
    const val IDLE_ACTIONS = PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH

    /** The heart button on now playing: favourites the show or station that is playing. */
    const val ACTION_TOGGLE_FAVORITE = "com.tileshell.music.TOGGLE_FAVORITE"

    private suspend fun start(context: Context, resolve: suspend (AutoLibrarySource) -> AutoPlayRequest?) {
        val request = runCatching { resolve(LocalLibrarySource(context)) }.getOrNull()
        when (request) {
            is AutoPlayRequest.Tracks -> LocalMusicPlayer.playQueue(context, request.tracks, request.startIndex)
            is AutoPlayRequest.Episodes -> LocalMusicPlayer.playEpisodes(context, request.show, request.episodes, request.startIndex)
            is AutoPlayRequest.Station -> LocalMusicPlayer.playStation(context, request.station, request.favorites)
            null -> Unit
        }
    }

    /** Whether [item] is already a favourite; null when it can't be (a library track). */
    fun isFavorite(item: PlayableAudio?, subs: List<PodcastSubscription>, stations: List<FavoriteStation>): Boolean? =
        when (item) {
            is PlayableAudio.Episode -> PodcastStore.isSubscribed(subs, item.show.feedUrl)
            is PlayableAudio.RadioStream -> RadioFavoritesStore.isFavorite(stations, item.station.stationId)
            else -> null
        }

    private suspend fun toggleFavorite(context: Context) {
        when (val item = LocalMusicPlayer.state.value.item) {
            is PlayableAudio.Episode -> {
                if (PodcastStore.isSubscribed(PodcastStore.subscriptions(context).first(), item.show.feedUrl)) {
                    PodcastStore.unsubscribe(context, item.show.feedUrl)
                } else {
                    PodcastStore.subscribe(context, item.show)
                }
            }
            is PlayableAudio.RadioStream -> {
                val s = item.station
                if (RadioFavoritesStore.isFavorite(RadioFavoritesStore.favorites(context).first(), s.stationId)) {
                    RadioFavoritesStore.removeFavorite(context, s.stationId)
                } else {
                    RadioFavoritesStore.addFavorite(context, FavoriteStation(s.stationId, s.name, s.streamUrl, s.faviconUrl, System.currentTimeMillis()))
                }
            }
            else -> Unit
        }
    }

    fun idleState(): PlaybackStateCompat =
        PlaybackStateCompat.Builder().setActions(IDLE_ACTIONS).setState(PlaybackStateCompat.STATE_NONE, 0L, 0f).build()

    @Synchronized
    fun get(context: Context): MediaSessionCompat =
        session ?: create(context.applicationContext).also { session = it }

    private fun create(context: Context): MediaSessionCompat =
        MediaSessionCompat(context, "TileShellMusicHub").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                // Bluetooth headsets and car kits send separate play and pause
                // keys: play only resumes and pause only pauses (a toggle here
                // made "pause" restart already-paused audio). These fire only
                // for actions advertised in the playback state.
                override fun onPlay() { LocalMusicPlayer.resume() }
                override fun onPause() { LocalMusicPlayer.pause() }
                override fun onFastForward() { LocalMusicPlayer.seekBy(SEEK_FORWARD_MS) }
                override fun onRewind() { LocalMusicPlayer.seekBy(-SEEK_BACK_MS) }
                override fun onSeekTo(pos: Long) { LocalMusicPlayer.seekTo(pos) }
                override fun onSkipToNext() { LocalMusicPlayer.next(context) }
                override fun onSkipToPrevious() { LocalMusicPlayer.previous(context) }
                override fun onStop() { LocalMusicPlayer.release() }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    val id = mediaId?.let { AutoMediaId.parse(it) } ?: return
                    scope.launch { start(context) { AutoBrowseTree.resolvePlay(id, it) } }
                }

                override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                    scope.launch { start(context) { AutoVoiceSearch.resolve(query.orEmpty(), it) } }
                }

                override fun onCustomAction(action: String?, extras: Bundle?) {
                    if (action == ACTION_TOGGLE_FAVORITE) scope.launch { toggleFavorite(context) }
                }
            })
            setPlaybackState(idleState())
        }
}
