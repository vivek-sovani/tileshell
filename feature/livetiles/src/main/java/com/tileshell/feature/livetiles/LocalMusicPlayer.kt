package com.tileshell.feature.livetiles

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Anything [LocalMusicPlayer] can stream: an on-device library file, a remote
 * podcast episode, or a live internet radio stream. All three funnel through
 * the same [MediaPlayer]/audio-focus/foreground-service machinery — this only
 * abstracts the handful of fields playback actually needs, so the player
 * itself doesn't need to know which kind it's holding. [durationMs] is `0`
 * for [RadioStream] (a live stream has no fixed length; the UI treats `0` as
 * "don't show a duration").
 */
sealed interface PlayableAudio {
    val id: String
    val title: String
    val subtitle: String
    val durationMs: Long
    val contentUri: Uri

    data class Local(val track: LocalTrack) : PlayableAudio {
        override val id get() = "local:${track.id}"
        override val title get() = track.title
        override val subtitle get() = track.artist
        override val durationMs get() = track.durationMs
        override val contentUri: Uri get() = track.contentUri
    }

    data class Episode(val show: PodcastSubscription, val episode: PodcastEpisode) : PlayableAudio {
        override val id get() = "podcast:${episode.guid}"
        override val title get() = episode.title
        override val subtitle get() = show.title
        override val durationMs get() = episode.durationMs ?: 0L
        override val contentUri: Uri get() = Uri.parse(episode.audioUrl)
    }

    data class RadioStream(val station: RadioStationRef) : PlayableAudio {
        override val id get() = "radio:${station.stationId}"
        override val title get() = station.name
        override val subtitle get() = "radio"
        override val durationMs get() = 0L
        override val contentUri: Uri get() = Uri.parse(station.streamUrl)
    }
}

/** The handful of fields [PlayableAudio.RadioStream] needs — accepts either a
 * fresh [RadioStation] search result or an already-[FavoriteStation]. */
data class RadioStationRef(val stationId: String, val name: String, val streamUrl: String, val faviconUrl: String?) {
    constructor(station: RadioStation) : this(station.stationId, station.name, station.streamUrl, station.faviconUrl)
    constructor(station: FavoriteStation) : this(station.stationId, station.name, station.streamUrl, station.faviconUrl)
}

/**
 * What the music hub is currently playing. [item]/[queue]/[queueIndex]
 * describe the selection that started this playback (e.g. tapping an album
 * queues its tracks in order; a podcast episode or radio station always
 * queues alone). `item == null` means nothing is playing.
 */
data class LocalPlayback(
    val item: PlayableAudio? = null,
    val playing: Boolean = false,
    val queue: List<PlayableAudio> = emptyList(),
    val queueIndex: Int = -1,
)

/**
 * A single, in-process [MediaPlayer] for the music hub's "library"/"podcasts"/
 * "radio" pages — local files and remote (podcast/radio) URLs are both valid
 * [MediaPlayer] data sources, so one player instance covers all three. Plays
 * **in the background**: starting anything brings up
 * [LocalMusicPlaybackService] (a real foreground service with its own
 * [android.media.session.MediaSession]), so playback, lock-screen/
 * notification controls, and audio-focus handling all survive leaving the
 * hub screen — the hub itself only ever reflects [state], it doesn't own
 * playback's lifetime. [release] fully stops playback (used by the
 * notification's stop action / swipe-to-dismiss, and a genuine audio-focus
 * loss); merely closing the hub screen does not call it.
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

    /** Starts playing a local-library queue from [startIndex]. */
    fun playQueue(context: Context, queue: List<LocalTrack>, startIndex: Int) {
        playItems(context, queue.map { PlayableAudio.Local(it) }, startIndex)
    }

    /** Starts playing a podcast show's episode queue from [startIndex]. */
    fun playEpisodes(context: Context, show: PodcastSubscription, episodes: List<PodcastEpisode>, startIndex: Int) {
        playItems(context, episodes.map { PlayableAudio.Episode(show, it) }, startIndex)
    }

    /** Starts a single live radio station — there's no meaningful "queue" for
     * a live stream, so next/previous are simply unavailable (a queue of one). */
    fun playStation(context: Context, station: RadioStationRef) {
        playItems(context, listOf(PlayableAudio.RadioStream(station)), 0)
    }

    private fun playItems(context: Context, queue: List<PlayableAudio>, startIndex: Int) {
        if (startIndex !in queue.indices) return
        if (!requestAudioFocus(context)) return
        ContextCompat.startForegroundService(
            context.applicationContext,
            Intent(context.applicationContext, LocalMusicPlaybackService::class.java),
        )
        playAt(context, queue, startIndex)
    }

    private fun playAt(context: Context, queue: List<PlayableAudio>, index: Int) {
        releasePlayerOnly()
        val item = queue[index]
        _state.value = LocalPlayback(item = item, playing = false, queue = queue, queueIndex = index)
        val mp = MediaPlayer()
        val ok = runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            // Keeps decoding/output alive while the CPU would otherwise sleep
            // with the screen off — needed since playback is expected to
            // outlive the hub screen and Start being on-screen at all.
            mp.setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            // A plain content:// URI (local track) and an https:// URI (podcast
            // episode / radio stream) both work through this same overload —
            // MediaPlayer resolves either directly.
            mp.setDataSource(context, item.contentUri)
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
