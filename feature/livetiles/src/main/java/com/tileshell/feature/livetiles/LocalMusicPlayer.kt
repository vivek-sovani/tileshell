package com.tileshell.feature.livetiles

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.PowerManager
import androidx.core.content.ContextCompat
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
 * A single, in-process [MediaPlayer] for the music hub's "library" page.
 * Plays **in the background**: starting a track brings up
 * [LocalMusicPlaybackService] (a real foreground service with its own
 * [android.media.session.MediaSession]), so playback, lock-screen/
 * notification controls, and audio-focus handling all survive leaving the
 * hub screen — the hub itself only ever reflects [state], it doesn't own
 * playback's lifetime. [release] fully stops playback (used by the
 * notification's stop action / swipe-to-dismiss, and when a queue genuinely
 * has nothing left to play); merely closing the hub screen does not call it.
 *
 * One shared instance (not per-composable) so playback survives the hub's own
 * pivot-page navigation and the service's independent lifecycle.
 */
object LocalMusicPlayer {
    private var player: MediaPlayer? = null
    private val _state = MutableStateFlow(LocalPlayback())
    val state: StateFlow<LocalPlayback> = _state.asStateFlow()

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> release()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                val mp = player
                if (mp != null && runCatching { mp.isPlaying }.getOrDefault(false)) {
                    resumeOnFocusGain = true
                    togglePlayPause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    togglePlayPause()
                }
            }
        }
    }

    /** Starts playing [queue] from [startIndex], replacing whatever was playing. */
    fun playQueue(context: Context, queue: List<LocalTrack>, startIndex: Int) {
        if (startIndex !in queue.indices) return
        if (!requestAudioFocus(context)) return
        ContextCompat.startForegroundService(
            context.applicationContext,
            Intent(context.applicationContext, LocalMusicPlaybackService::class.java),
        )
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
            // Keeps decoding/output alive while the CPU would otherwise sleep
            // with the screen off — needed now that playback is expected to
            // outlive the hub screen and Start being on-screen at all.
            mp.setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
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

    private fun requestAudioFocus(context: Context): Boolean {
        val manager = ContextCompat.getSystemService(context.applicationContext, AudioManager::class.java)
            ?: return false
        audioManager = manager
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        focusRequest?.let { manager.abandonAudioFocusRequest(it) }
        audioManager = null
        focusRequest = null
        resumeOnFocusGain = false
    }

    private fun releasePlayerOnly() {
        player?.let { runCatching { it.release() } }
        player = null
    }

    /**
     * Stops and releases playback entirely — called from the notification's
     * stop action / swipe-to-dismiss, or a genuine audio-focus loss to
     * another app. Not called just because the hub screen closed; that's the
     * whole point of background playback.
     */
    fun release() {
        releasePlayerOnly()
        abandonAudioFocus()
        _state.value = LocalPlayback()
    }
}
