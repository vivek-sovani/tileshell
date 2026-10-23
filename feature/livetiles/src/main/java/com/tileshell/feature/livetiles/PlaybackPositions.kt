package com.tileshell.feature.livetiles

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A resume is not worth it inside the first seconds of an item. */
const val RESUME_MIN_MS = 5_000L

/** …nor inside the last seconds: that item counts as finished. */
const val RESUME_END_MARGIN_MS = 10_000L

/** How many podcast episodes keep a saved position; the least recently played go first. */
const val PODCAST_POSITIONS_CAP = 200

/**
 * Where to start [saved] ms into an item of [durationMs] (0 = unknown): the
 * saved position, or 0 when it is too close to the start to matter or so
 * close to the end that the item was effectively finished. Pure, unit-tested.
 */
fun resumeStartMs(saved: Long, durationMs: Long): Long = when {
    saved < RESUME_MIN_MS -> 0L
    durationMs > 0 && saved > durationMs - RESUME_END_MARGIN_MS -> 0L
    else -> saved
}

/** The keys to drop so that at most [cap] remain, oldest [entries] value (time) first. Pure, unit-tested. */
fun positionsToPrune(entries: Map<String, Long>, cap: Int): List<String> =
    if (entries.size <= cap) emptyList() else entries.entries.sortedBy { it.value }.take(entries.size - cap).map { it.key }

/** An episode's saved state value meaning "played to the end". */
const val EPISODE_PLAYED = -1L

/**
 * The line under an episode: "played", the time left (or the position when
 * the length is unknown) for one part-way through, null for one not started.
 * [state] is [PlaybackPositions.podcastStates]'s value for the episode.
 */
fun episodeProgressLabel(state: Long?, durationMs: Long?): String? = when {
    state == null -> null
    state == EPISODE_PLAYED -> "played"
    durationMs != null && durationMs > state -> "${formatTrackDuration(durationMs - state)} left"
    else -> "at ${formatTrackDuration(state)}"
}

/**
 * The queue index of the episode to play after the one at [currentIndex]
 * finishes: the nearest newer episode of the queue that is not already
 * played ([states]: guid → saved state, see [PlaybackPositions.podcastStates]),
 * or null when there is none (the newest is reached). Newer is by publish
 * date; episodes without a date go by feed order, which is newest first.
 * Pure, unit-tested.
 */
fun nextEpisodeIndex(queue: List<PlayableAudio>, currentIndex: Int, states: Map<String, Long>): Int? {
    val episodes = queue.withIndex().filter { it.value is PlayableAudio.Episode }
        .sortedWith(compareBy<IndexedValue<PlayableAudio>>({ (it.value as PlayableAudio.Episode).episode.publishedMillis }, { -it.index }))
    val at = episodes.indexOfFirst { it.index == currentIndex }
    if (at < 0) return null
    return episodes.drop(at + 1).firstOrNull { (it.value as PlayableAudio.Episode).episode.guid.let { g -> states[g] != EPISODE_PLAYED } }?.index
}

/**
 * Where playback stopped, so playing an item again carries on instead of
 * starting over, and which podcast episodes are already played. Every podcast
 * episode keeps its own state (the newest [PODCAST_POSITIONS_CAP]): a position,
 * or [EPISODE_PLAYED]; of the local library only the last song played is kept.
 * Radio is live and keeps nothing. Stored on the phone only, in its own
 * preferences file (not in backups); read and written on the main thread (a
 * few small keys).
 */
object PlaybackPositions {
    private const val FILE = "tileshell.playback"
    private const val KEY_LOCAL = "local"
    private const val PODCAST_PREFIX = "p:"

    private val _podcastStates = MutableStateFlow<Map<String, Long>>(emptyMap())
    private var loaded = false

    /** Episode guid → saved position in ms, or [EPISODE_PLAYED]; absent = not started. Call [ensureLoaded] first. */
    val podcastStates: StateFlow<Map<String, Long>> = _podcastStates.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        _podcastStates.value = prefs(context).all
            .filterKeys { it.startsWith(PODCAST_PREFIX) }
            .mapNotNull { (k, v) -> (v as? String)?.substringBefore('|')?.toLongOrNull()?.let { k.removePrefix(PODCAST_PREFIX) to it } }
            .toMap()
    }

    private fun setState(guid: String, value: Long?) {
        _podcastStates.value = if (value == null) _podcastStates.value - guid else _podcastStates.value + (guid to value)
    }

    /** The position to start [item] at, in ms (0 = from the beginning; a played episode starts over). */
    fun resumeMs(context: Context, item: PlayableAudio): Long {
        val p = prefs(context)
        val saved = when (item) {
            is PlayableAudio.Episode -> p.getString(PODCAST_PREFIX + item.episode.guid, null)?.substringBefore('|')?.toLongOrNull()
            is PlayableAudio.Local -> p.getString(KEY_LOCAL, null)?.let { raw ->
                raw.substringBefore('|').takeIf { it == item.id }?.let { raw.substringAfter('|').toLongOrNull() }
            }
            is PlayableAudio.RadioStream -> null
        } ?: return 0L
        return if (saved == EPISODE_PLAYED) 0L else resumeStartMs(saved, item.durationMs)
    }

    /**
     * Records that [item] is at [positionMs] of [durationMs] (0 = unknown). A
     * podcast episode in its last seconds becomes played; one in its first
     * seconds is left as it was (a played episode started again stays played
     * until it is part-way through). The local "last song" is always
     * overwritten, so it is the last one played.
     */
    fun save(context: Context, item: PlayableAudio, positionMs: Long, durationMs: Long) {
        val p = prefs(context)
        when (item) {
            is PlayableAudio.Local -> p.edit().putString(KEY_LOCAL, "${item.id}|$positionMs").apply()
            is PlayableAudio.Episode -> {
                val guid = item.episode.guid
                val key = PODCAST_PREFIX + guid
                val duration = durationMs.takeIf { it > 0 } ?: item.durationMs
                val stored = when {
                    positionMs < RESUME_MIN_MS -> return
                    duration > 0 && positionMs > duration - RESUME_END_MARGIN_MS -> EPISODE_PLAYED
                    else -> positionMs
                }
                val edit = p.edit().putString(key, "$stored|${System.currentTimeMillis()}")
                if (!p.contains(key)) {
                    val times = p.all.filterKeys { it.startsWith(PODCAST_PREFIX) }
                        .mapValues { (it.value as? String)?.substringAfter('|')?.toLongOrNull() ?: 0L }
                    positionsToPrune(times + (key to Long.MAX_VALUE), PODCAST_POSITIONS_CAP).forEach {
                        edit.remove(it)
                        setState(it.removePrefix(PODCAST_PREFIX), null)
                    }
                }
                edit.apply()
                setState(guid, stored)
            }
            is PlayableAudio.RadioStream -> Unit
        }
    }

    /** [item] played to its end: an episode is marked played; a song just starts from the beginning next time. */
    fun markFinished(context: Context, item: PlayableAudio) {
        val p = prefs(context)
        when (item) {
            is PlayableAudio.Episode -> {
                p.edit().putString(PODCAST_PREFIX + item.episode.guid, "$EPISODE_PLAYED|${System.currentTimeMillis()}").apply()
                setState(item.episode.guid, EPISODE_PLAYED)
            }
            is PlayableAudio.Local ->
                if (p.getString(KEY_LOCAL, null)?.substringBefore('|') == item.id) p.edit().remove(KEY_LOCAL).apply()
            is PlayableAudio.RadioStream -> Unit
        }
    }
}
