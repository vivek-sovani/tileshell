package com.tileshell.feature.livetiles

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
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
    /** Bumped on every seek, so observers (the media session) re-read the position. */
    val seekVersion: Int = 0,
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
    // MediaPlayer reports an error (and stops) if its position or duration is
    // read before it has prepared, so those reads wait for this.
    @Volatile private var prepared = false
    // For restarting an item left stopped by [stopOnCurrent].
    private var appContext: Context? = null
    // Items that failed in a row; once the whole queue has failed, stop
    // instead of skipping round it forever.
    private var consecutiveErrors = 0
    // Gapless: the next local track, prepared ahead and chained to [player].
    private var nextPlayer: MediaPlayer? = null
    private var nextIndex = -1
    private var nextReady = false
    private val _gapless = MutableStateFlow(true)
    private val _overlapMs = MutableStateFlow(DEFAULT_OVERLAP_MS)
    private var gaplessLoaded = false
    // Crossfade: the outgoing track, still fading out under the new one.
    private var fadingPlayer: MediaPlayer? = null
    private var fadeStartMs = 0L
    private var fadeLengthMs = 0L
    private var tickerContext: Context? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            tick()
            if (player != null || fadingPlayer != null) handler.postDelayed(this, nextTickDelay())
        }
    }
    private val _state = MutableStateFlow(LocalPlayback())
    val state: StateFlow<LocalPlayback> = _state.asStateFlow()

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false
    // Set when the audio outputs changed while paused (Bluetooth or headphones
    // came or went). A paused MediaPlayer can stay on the output it was opened
    // on, so resuming it after the Buds dropped and the car connected played
    // out of the phone. Resuming reopens a fresh player at the same position.
    private var routeStale = false

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

    /**
     * Starts a radio station with the favourite stations as its queue, so
     * next/previous step through them (see [radioQueue]).
     */
    fun playStation(context: Context, station: RadioStationRef, favorites: List<RadioStationRef> = emptyList()) {
        val (queue, index) = radioQueue(station, favorites)
        playItems(context, queue.map { PlayableAudio.RadioStream(it) }, index)
    }

    private fun playItems(context: Context, queue: List<PlayableAudio>, startIndex: Int) {
        if (startIndex !in queue.indices) return
        if (!requestAudioFocus(context)) return
        ContextCompat.startForegroundService(
            context.applicationContext,
            Intent(context.applicationContext, LocalMusicPlaybackService::class.java),
        )
        consecutiveErrors = 0
        appContext = context.applicationContext
        playAt(context, queue, startIndex)
    }

    private fun playAt(context: Context, queue: List<PlayableAudio>, index: Int, startAtMs: Long = 0L) {
        releasePlayerOnly()
        routeStale = false
        val item = queue[index]
        _state.value = LocalPlayback(item = item, playing = false, queue = queue, queueIndex = index)
        val mp = newPlayer(context, item)
        if (mp == null) {
            _state.value = LocalPlayback()
            return
        }
        mp.setOnPreparedListener {
            prepared = true
            consecutiveErrors = 0
            if (startAtMs > 0) runCatching { it.seekTo(startAtMs.toInt()) }
            runCatching { it.start() }
            _state.value = _state.value.copy(playing = true)
            // Here rather than in a UI effect so an episode auto-advanced
            // to in the background still lands in the podcasts tab's recents.
            MusicRecents.record(context, item)
            preloadNext(context)
            startTicker(context)
        }
        attachPlaybackListeners(context, mp, item)
        if (runCatching { mp.prepareAsync() }.isSuccess) {
            player = mp
        } else {
            runCatching { mp.release() }
            _state.value = LocalPlayback()
        }
    }

    /** A player with [item] set as its source, not yet preparing; null if the
     * source can't be set. */
    private fun newPlayer(context: Context, item: PlayableAudio): MediaPlayer? {
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
        }.isSuccess
        if (!ok) runCatching { mp.release() }
        return if (ok) mp else null
    }

    private fun attachPlaybackListeners(context: Context, mp: MediaPlayer, item: PlayableAudio) {
        // A radio queue is the favourites list: a station that fails or
        // drops out stays selected, paused, instead of the player hopping
        // on to the next favourite (which flickered through the whole list).
        // Next/previous are only for the user to press.
        val isRadio = item is PlayableAudio.RadioStream
        mp.setOnCompletionListener { if (isRadio) stopOnCurrent() else onItemFinished(context) }
        mp.setOnErrorListener { _, _, _ ->
            consecutiveErrors++
            when {
                isRadio -> stopOnCurrent()
                consecutiveErrors >= _state.value.queue.size.coerceAtLeast(1) -> release()
                else -> next(context)
            }
            true
        }
    }

    /**
     * The current item played to its end. With gapless on, the next track
     * was already prepared and chained with [MediaPlayer.setNextMediaPlayer],
     * so it is already playing: just adopt it. Otherwise start the next one.
     */
    private fun onItemFinished(context: Context) {
        val np = nextPlayer
        if (np == null || !nextReady || nextIndex !in _state.value.queue.indices) {
            next(context)
            return
        }
        player?.let { runCatching { it.release() } }
        adoptNext(context, np)
    }

    /** Makes the already-started [nextPlayer] the current one. */
    private fun adoptNext(context: Context, np: MediaPlayer) {
        val s = _state.value
        val item = s.queue[nextIndex]
        player = np
        prepared = true
        _state.value = s.copy(item = item, queueIndex = nextIndex, playing = true, seekVersion = s.seekVersion + 1)
        nextPlayer = null
        nextReady = false
        nextIndex = -1
        attachPlaybackListeners(context, np, item)
        MusicRecents.record(context, item)
        preloadNext(context)
    }

    /**
     * Overlap: once the playing track is within the overlap of its end, the
     * prepared next track starts quietly and the two cross-fade, so there's
     * no silence even when a file ends with a couple of seconds of it.
     */
    private fun tick() {
        val context = tickerContext ?: return
        val fading = fadingPlayer
        if (fading != null) {
            val t = ((SystemClock.elapsedRealtime() - fadeStartMs).toFloat() / fadeLengthMs).coerceIn(0f, 1f)
            runCatching { fading.setVolume(1f - t, 1f - t) }
            player?.let { runCatching { it.setVolume(t, t) } }
            if (t >= 1f) finishFade()
            return
        }
        val overlap = _overlapMs.value
        val cur = player ?: return
        val np = nextPlayer ?: return
        if (overlap <= 0 || !nextReady || !prepared || !_state.value.playing) return
        val duration = durationMs()
        if (duration < overlap * 3) return
        if (duration - positionMs() > overlap) return
        // Start the crossfade: the old player finishes on its own terms and is
        // released when the fade completes.
        cur.setOnCompletionListener(null)
        cur.setOnErrorListener(null)
        runCatching { np.setVolume(0f, 0f); np.start() }.onFailure { return }
        fadingPlayer = cur
        fadeStartMs = SystemClock.elapsedRealtime()
        fadeLengthMs = overlap.toLong()
        adoptNext(context, np)
    }

    /** Checks often only near a track's end or during a fade; otherwise
     * sleeps until close to it, so a long track costs a few wake-ups. */
    private fun nextTickDelay(): Long {
        if (fadingPlayer != null) return 50L
        if (nextPlayer == null || _overlapMs.value <= 0) return 2_000L
        val remaining = durationMs() - positionMs() - _overlapMs.value
        return (remaining - 500L).coerceIn(TICK_MS, 5_000L)
    }

    /** Ends a crossfade at once: the old track stops, the new one at full volume. */
    private fun finishFade() {
        val fading = fadingPlayer ?: return
        fadingPlayer = null
        runCatching { fading.release() }
        player?.let { runCatching { it.setVolume(1f, 1f) } }
    }

    private fun startTicker(context: Context) {
        tickerContext = context.applicationContext
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, TICK_MS)
    }

    /**
     * Gapless: prepares the queue's next local track now and chains it to the
     * playing one, so it starts the instant this one ends, with no silence.
     * Local files only — preloading a podcast episode would download it early,
     * and radio has no "next" to run on into.
     */
    private fun preloadNext(context: Context) {
        releaseNextOnly()
        val cur = player ?: return
        val s = _state.value
        if (!gaplessEnabled(context) || !prepared || s.queue.size < 2) return
        if (s.item !is PlayableAudio.Local) return
        val index = (s.queueIndex + 1) % s.queue.size
        val item = s.queue[index] as? PlayableAudio.Local ?: return
        val np = newPlayer(context, item) ?: return
        nextPlayer = np
        nextIndex = index
        np.setOnPreparedListener {
            if (nextPlayer !== np) return@setOnPreparedListener
            // With an overlap the ticker starts it; without, chain it so it
            // runs straight on when this one ends.
            if (player === cur && prepared &&
                (_overlapMs.value > 0 || runCatching { cur.setNextMediaPlayer(np) }.isSuccess)
            ) {
                nextReady = true
            } else {
                releaseNextOnly()
            }
        }
        np.setOnErrorListener { _, _, _ ->
            if (nextPlayer === np) releaseNextOnly()
            true
        }
        if (runCatching { np.prepareAsync() }.isFailure) releaseNextOnly()
    }

    private fun releaseNextOnly() {
        val np = nextPlayer ?: return
        if (prepared && nextReady && _overlapMs.value <= 0) player?.let { runCatching { it.setNextMediaPlayer(null) } }
        runCatching { np.release() }
        nextPlayer = null
        nextReady = false
        nextIndex = -1
    }

    /** Gapless playback between local tracks (default on), from now playing. */
    fun gapless(context: Context): StateFlow<Boolean> {
        gaplessEnabled(context)
        return _gapless.asStateFlow()
    }

    fun setGapless(context: Context, on: Boolean) {
        gaplessLoaded = true
        _gapless.value = on
        prefs(context).edit().putBoolean(KEY_GAPLESS, on).apply()
        if (on) preloadNext(context) else releaseNextOnly()
    }

    /** How long consecutive tracks overlap (cross-fade) with gapless on; 0 = none. */
    fun overlapMs(context: Context): StateFlow<Int> {
        gaplessEnabled(context)
        return _overlapMs.asStateFlow()
    }

    fun setOverlapMs(context: Context, ms: Int) {
        gaplessEnabled(context)
        releaseNextOnly()
        _overlapMs.value = ms
        prefs(context).edit().putInt(KEY_OVERLAP, ms).apply()
        preloadNext(context)
    }

    private fun gaplessEnabled(context: Context): Boolean {
        if (!gaplessLoaded) {
            gaplessLoaded = true
            val p = prefs(context)
            _gapless.value = p.getBoolean(KEY_GAPLESS, true)
            _overlapMs.value = p.getInt(KEY_OVERLAP, DEFAULT_OVERLAP_MS)
        }
        return _gapless.value
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("tileshell.music", Context.MODE_PRIVATE)

    private const val KEY_GAPLESS = "gapless"
    private const val KEY_OVERLAP = "overlap_ms"
    private const val TICK_MS = 150L

    fun togglePlayPause() {
        finishFade()
        val mp = player ?: return restartCurrent()
        // start/pause while still preparing puts MediaPlayer in an error state.
        if (!prepared) return
        runCatching {
            if (mp.isPlaying) {
                mp.pause()
                _state.value = _state.value.copy(playing = false)
            } else {
                if (routeStale && reopenCurrent()) return
                mp.start()
                _state.value = _state.value.copy(playing = true)
            }
        }
    }

    /** Pauses for [android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY]: the
     * output just went away, so the next resume reopens on the new one. */
    fun pauseForNoisy() {
        pause()
        if (player != null) routeStale = true
    }

    /** An output device was added or removed. Only matters while paused; a
     * playing player is moved by the system. */
    fun onOutputsChanged() {
        val mp = player ?: return
        if (!runCatching { mp.isPlaying }.getOrDefault(false)) routeStale = true
    }

    /** Reopens the current item at its position so it plays on today's
     * output; false if there's nothing to reopen. */
    private fun reopenCurrent(): Boolean {
        val context = appContext ?: return false
        val s = _state.value
        if (s.item == null || s.queueIndex !in s.queue.indices) return false
        // Radio is live: start the stream afresh.
        val position = if (s.item is PlayableAudio.RadioStream) 0L else positionMs()
        playAt(context, s.queue, s.queueIndex, position)
        return true
    }

    /**
     * Pauses if playing, and never resumes — unlike [togglePlayPause]. For the
     * headphones/Bluetooth-disconnected case ([android.media.AudioManager
     * .ACTION_AUDIO_BECOMING_NOISY]), where a toggle could wrongly *start*
     * playback that was already paused. Also clears a pending focus-regain
     * resume, so a later focus gain doesn't restart it through the speaker.
     */
    fun pause() {
        finishFade()
        val mp = player ?: return
        resumeOnFocusGain = false
        runCatching {
            if (mp.isPlaying) {
                mp.pause()
                _state.value = _state.value.copy(playing = false)
            }
        }
    }

    /**
     * Starts playback if paused, and never pauses — the counterpart of
     * [pause], for a headset's separate "play" button (a toggle there would
     * pause what's already playing).
     */
    fun resume() {
        val mp = player ?: return restartCurrent()
        resumeOnFocusGain = false
        if (!prepared) return
        if (routeStale && !runCatching { mp.isPlaying }.getOrDefault(false) && reopenCurrent()) return
        runCatching {
            if (!mp.isPlaying) {
                mp.start()
                _state.value = _state.value.copy(playing = true)
            }
        }
    }

    /** Current position in ms (0 when nothing is loaded). */
    fun positionMs(): Long = if (!prepared) 0L else runCatching { player?.currentPosition?.toLong() }.getOrNull() ?: 0L

    /** Track length in ms from the player itself, 0 when unknown (a live stream). */
    fun durationMs(): Long = if (!prepared) 0L else runCatching { player?.duration?.toLong() }.getOrNull()?.takeIf { it > 0 } ?: 0L

    /** Radio is live, so it can't seek; tracks and podcast episodes can. */
    fun canSeek(): Boolean = _state.value.item.let { it != null && it !is PlayableAudio.RadioStream } && durationMs() > 0

    fun seekTo(ms: Long) {
        finishFade()
        val mp = player ?: return
        if (!canSeek()) return
        val target = ms.coerceIn(0L, durationMs())
        runCatching { mp.seekTo(target.toInt()) }
        _state.value = _state.value.copy(seekVersion = _state.value.seekVersion + 1)
    }

    /** Jumps back (negative) or forward by [deltaMs]. */
    fun seekBy(deltaMs: Long) = seekTo(positionMs() + deltaMs)

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

    private fun restartCurrent() {
        val s = _state.value
        val context = appContext ?: return
        if (s.item == null || s.queueIndex !in s.queue.indices) return
        playItems(context, s.queue, s.queueIndex)
    }

    /** Keeps the current item showing, paused, with nothing loaded; play
     * starts it again from scratch. */
    private fun stopOnCurrent() {
        releasePlayerOnly()
        _state.value = _state.value.copy(playing = false)
    }

    private fun releasePlayerOnly() {
        finishFade()
        releaseNextOnly()
        prepared = false
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

/** Now playing's skip buttons, and a headset's fast-forward / rewind keys. */
const val SEEK_BACK_MS = 10_000L
const val SEEK_FORWARD_MS = 30_000L

/** Now playing's overlap choices between tracks, in ms; 0 is plain gapless. */
val TRACK_OVERLAP_CHOICES = listOf(0, 1_000, 2_000, 3_000)
const val DEFAULT_OVERLAP_MS = 2_000

/**
 * A radio station's playback queue: the favourite stations, so next/previous
 * move through them. A station that isn't a favourite goes first, then the
 * favourites. Returns the queue and the playing station's index. Pure,
 * unit-tested.
 */
internal fun radioQueue(station: RadioStationRef, favorites: List<RadioStationRef>): Pair<List<RadioStationRef>, Int> {
    val favs = favorites.distinctBy { it.stationId }
    val at = favs.indexOfFirst { it.stationId == station.stationId }
    return if (at >= 0) favs to at else (listOf(station) + favs) to 0
}
