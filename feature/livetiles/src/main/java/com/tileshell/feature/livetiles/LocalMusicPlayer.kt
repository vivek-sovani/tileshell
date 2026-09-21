package com.tileshell.feature.livetiles

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the music hub's local-library playback is currently doing. [track]/
 * [queue]/[queueIndex] describe the playlist/album/track selection that
 * started this playback (e.g. tapping an album queues its tracks in order).
 * `track == null` means nothing is playing.
 */
data class LocalPlayback(
    val track: LocalTrack? = null,
    val playing: Boolean = false,
    val queue: List<LocalTrack> = emptyList(),
    val queueIndex: Int = -1,
)

/**
 * A single, in-process [MediaPlayer] for the music hub's "library" page —
 * deliberately **in-hub-only playback**: no foreground service, no
 * notification, no [android.media.session.MediaSession] of its own, so it
 * does not survive leaving the hub and does not appear in [MediaCenter] (that
 * reads *other* apps' sessions via the notification-listener grant; this is
 * TileShell playing its own audio, a different mechanism entirely). Full
 * background playback with lock-screen controls is a larger follow-up, not
 * built here.
 *
 * One shared instance (not per-composable) so playback survives the hub's own
 * pivot-page navigation; [release] is called when the hub screen itself is
 * torn down (see `MusicHubScreen`'s `DisposableEffect`), which is what
 * actually stops playback on leaving the hub.
 */
object LocalMusicPlayer {
    private var player: MediaPlayer? = null
    private val _state = MutableStateFlow(LocalPlayback())
    val state: StateFlow<LocalPlayback> = _state.asStateFlow()

    /** Starts playing [queue] from [startIndex], replacing whatever was playing. */
    fun playQueue(context: Context, queue: List<LocalTrack>, startIndex: Int) {
        if (startIndex !in queue.indices) return
        playAt(context, queue, startIndex)
    }

    private fun playAt(context: Context, queue: List<LocalTrack>, index: Int) {
        releasePlayerOnly()
        val track = queue[index]
        _state.value = LocalPlayback(track = track, playing = false, queue = queue, queueIndex = index)
        val mp = MediaPlayer()
        val ok = runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            mp.setDataSource(context, track.contentUri)
            mp.setOnPreparedListener {
                runCatching { it.start() }
                _state.value = _state.value.copy(playing = true)
            }
            mp.setOnCompletionListener { next(context) }
            mp.setOnErrorListener { _, _, _ -> next(context); true }
            mp.prepareAsync()
        }.isSuccess
        if (ok) {
            player = mp
        } else {
            runCatching { mp.release() }
            _state.value = LocalPlayback()
        }
    }

    fun togglePlayPause() {
        val mp = player ?: return
        runCatching {
            if (mp.isPlaying) {
                mp.pause()
                _state.value = _state.value.copy(playing = false)
            } else {
                mp.start()
                _state.value = _state.value.copy(playing = true)
            }
        }
    }

    fun next(context: Context) {
        val s = _state.value
        if (s.queue.isEmpty()) return
        playAt(context, s.queue, (s.queueIndex + 1) % s.queue.size)
    }

    fun previous(context: Context) {
        val s = _state.value
        if (s.queue.isEmpty()) return
        val prevIndex = if (s.queueIndex <= 0) s.queue.size - 1 else s.queueIndex - 1
        playAt(context, s.queue, prevIndex)
    }

    private fun releasePlayerOnly() {
        player?.let { runCatching { it.release() } }
        player = null
    }

    /** Stops and releases playback entirely — called when the hub screen closes. */
    fun release() {
        releasePlayerOnly()
        _state.value = LocalPlayback()
    }
}
