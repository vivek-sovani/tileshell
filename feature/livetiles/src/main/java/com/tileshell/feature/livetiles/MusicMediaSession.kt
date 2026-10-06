package com.tileshell.feature.livetiles

import android.content.Context
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
                    scope.launch {
                        val request = runCatching { AutoBrowseTree.resolvePlay(id, LocalLibrarySource(context)) }.getOrNull()
                        when (request) {
                            is AutoPlayRequest.Tracks -> LocalMusicPlayer.playQueue(context, request.tracks, request.startIndex)
                            is AutoPlayRequest.Episodes ->
                                LocalMusicPlayer.playEpisodes(context, request.show, request.episodes, request.startIndex)
                            is AutoPlayRequest.Station -> LocalMusicPlayer.playStation(context, request.station, request.favorites)
                            null -> Unit
                        }
                    }
                }
            })
            setPlaybackState(idleState())
        }
}
