package com.tileshell.feature.livetiles

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The last item the music player started (a song, podcast episode or radio
 * station), kept on the phone so Android Auto can offer it and carry on after
 * the player was let go (the car stopped, the phone was disconnected, the
 * process died). The position comes from [PlaybackPositions]; radio is live and
 * starts afresh. Stored in the same preferences file as the positions, not in
 * backups.
 */
object LastPlayed {
    private const val FILE = "tileshell.playback"
    private const val KEY = "last_item"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private const val QUEUE_KEY = "last_queue"
    private const val INDEX_KEY = "last_queue_index"
    private const val MAX_QUEUE = 300
    private var savedQueue: List<PlayableAudio>? = null

    fun save(context: Context, item: PlayableAudio) {
        runCatching { prefs(context).edit().putString(KEY, encode(item)).apply() }
    }

    /**
     * Keeps the playing queue too (a shuffled playlist keeps its order), so
     * the same list carries on after a disconnect. Long queues keep the
     * next [MAX_QUEUE] items from the playing one. The list is written only
     * when it changed; a track change just moves the index.
     */
    fun saveQueue(context: Context, queue: List<PlayableAudio>, index: Int) {
        if (index !in queue.indices) return
        runCatching {
            val edit = prefs(context).edit()
            if (savedQueue !== queue) {
                val window = if (queue.size <= MAX_QUEUE) queue
                else List(MAX_QUEUE) { queue[(index + it) % queue.size] }
                val start = if (queue.size <= MAX_QUEUE) index else 0
                edit.putString(QUEUE_KEY, JSONArray(window.map { encode(it) }).toString())
                edit.putInt(INDEX_KEY, start)
                savedQueue = queue
                queueBase = index
            } else {
                edit.putInt(INDEX_KEY, if (queue.size <= MAX_QUEUE) index else Math.floorMod(index - queueBase, queue.size))
            }
            edit.apply()
        }
    }
    private var queueBase = 0

    /** The saved queue and the playing index in it, or null. */
    fun loadQueue(context: Context): Pair<List<PlayableAudio>, Int>? = runCatching {
        val p = prefs(context)
        val arr = JSONArray(p.getString(QUEUE_KEY, null) ?: return null)
        val items = (0 until arr.length()).mapNotNull { decode(arr.getString(it)) }
        val i = p.getInt(INDEX_KEY, 0)
        if (items.size == arr.length() && i in items.indices) items to i else null
    }.getOrNull()

    fun load(context: Context): PlayableAudio? =
        runCatching { prefs(context).getString(KEY, null)?.let { decode(it) } }.getOrNull()

    internal fun encode(item: PlayableAudio): String = JSONObject().apply {
        when (item) {
            is PlayableAudio.Local -> {
                put("k", "local")
                put("id", item.track.id)
                put("title", item.track.title)
                put("artist", item.track.artist)
                put("album", item.track.album)
                put("albumId", item.track.albumId)
                put("dur", item.track.durationMs)
            }
            is PlayableAudio.Episode -> {
                put("k", "episode")
                put("feedUrl", item.show.feedUrl)
                put("showTitle", item.show.title)
                put("artwork", item.show.artworkUrl ?: JSONObject.NULL)
                put("subAt", item.show.subscribedAtMillis)
                put("guid", item.episode.guid)
                put("title", item.episode.title)
                put("desc", item.episode.description)
                put("audio", item.episode.audioUrl)
                put("dur", item.episode.durationMs ?: JSONObject.NULL)
                put("pub", item.episode.publishedMillis)
                put("img", item.episode.imageUrl ?: JSONObject.NULL)
            }
            is PlayableAudio.RadioStream -> {
                put("k", "radio")
                put("id", item.station.stationId)
                put("name", item.station.name)
                put("url", item.station.streamUrl)
                put("fav", item.station.faviconUrl ?: JSONObject.NULL)
            }
        }
    }.toString()

    internal fun decode(raw: String): PlayableAudio? {
        val o = JSONObject(raw)
        fun str(k: String) = if (o.isNull(k)) null else o.getString(k)
        return when (o.getString("k")) {
            "local" -> PlayableAudio.Local(
                LocalTrack(o.getLong("id"), o.getString("title"), o.getString("artist"), o.getString("album"), o.getLong("albumId"), o.getLong("dur")),
            )
            "episode" -> PlayableAudio.Episode(
                PodcastSubscription(o.getString("feedUrl"), o.getString("showTitle"), str("artwork"), o.optLong("subAt")),
                PodcastEpisode(
                    o.getString("guid"), o.getString("title"), o.optString("desc"), o.getString("audio"),
                    if (o.isNull("dur")) null else o.getLong("dur"), o.optLong("pub"), str("img"),
                ),
            )
            "radio" -> PlayableAudio.RadioStream(RadioStationRef(o.getString("id"), o.getString("name"), o.getString("url"), str("fav")))
            else -> null
        }
    }

    /** Starts [item] again (a podcast episode or song from where it was left). */
    fun play(context: Context, item: PlayableAudio) {
        val saved = loadQueue(context)
        if (saved != null && sameItem(saved.first[saved.second], item)) {
            LocalMusicPlayer.playSavedQueue(context, saved.first, saved.second)
            return
        }
        when (item) {
            is PlayableAudio.Local -> LocalMusicPlayer.playQueue(context, listOf(item.track), 0)
            is PlayableAudio.Episode -> LocalMusicPlayer.playEpisodes(context, item.show, listOf(item.episode), 0)
            is PlayableAudio.RadioStream -> LocalMusicPlayer.playStation(context, item.station)
        }
    }

    private fun sameItem(a: PlayableAudio, b: PlayableAudio) = encode(a) == encode(b)
}
