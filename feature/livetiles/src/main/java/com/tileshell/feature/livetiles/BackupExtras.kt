package com.tileshell.feature.livetiles

import android.content.Context
import com.tileshell.feature.livetiles.money.MoneyPrefs
import kotlinx.coroutines.flow.first
import org.json.JSONArray

/**
 * The manual backup's feature-owned data (podcast subscriptions, radio
 * favourites, recently played, music history, and a few small preferences),
 * each as one string under its own key so older backups and older builds can
 * skip what they don't know. Wallpaper image files are not included.
 */
object BackupExtras {
    private const val PODCASTS = "podcastSubscriptions"
    private const val RADIO_FAVORITES = "radioFavorites"
    private const val PODCAST_RECENTS = "podcastRecents"
    private const val RADIO_RECENTS = "radioRecents"
    private const val MUSIC_HISTORY = "musicHistory"
    private const val PREF_PREFIX = "pref."
    private const val PREFS_FILE = "tileshell.prefs"

    /** Preferences worth carrying over (the rest are caches or per-device state). */
    internal val PREF_KEYS = listOf(
        "money_tile_details", "money_lock", "money_read_bank_messages",
        "panchang_highlights", "panchang_festivals", "panchang_custom_tithis", "panchang_grahan",
        "productivity_quick_items",
        "hub_apps_people", "hub_apps_money", "hub_apps_productivity",
        "markets_watchlist", "markets_tile_symbols", "markets_tile_indices", "sports_fav_sports", "sports_fav_teams",
    )

    suspend fun export(context: Context): Map<String, String> {
        val app = context.applicationContext
        val out = linkedMapOf<String, String>()
        out[PODCASTS] = PodcastSubscriptionCodec.encode(PodcastStore.subscriptions(app).first())
        out[RADIO_FAVORITES] = FavoriteStationCodec.encode(RadioFavoritesStore.favorites(app).first())
        out[PODCAST_RECENTS] = RecentEpisodeCodec.encode(MusicRecents.episodes(app).first())
        out[RADIO_RECENTS] = FavoriteStationCodec.encode(MusicRecents.stations(app).first())
        out[MUSIC_HISTORY] = PlayedTrackCodec.encode(MusicHistory.history(app).first())
        val prefs = app.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        PREF_KEYS.forEach { key ->
            encodePref(prefs.all[key])?.let { out[PREF_PREFIX + key] = it }
        }
        return out
    }

    suspend fun restore(context: Context, extras: Map<String, String>) {
        val app = context.applicationContext
        extras[PODCASTS]?.let { replacePodcastSubscriptions(app, PodcastSubscriptionCodec.decode(it)) }
        extras[RADIO_FAVORITES]?.let { replaceRadioFavorites(app, FavoriteStationCodec.decode(it)) }
        if (extras.containsKey(PODCAST_RECENTS) || extras.containsKey(RADIO_RECENTS)) {
            replaceMusicRecents(
                app,
                extras[PODCAST_RECENTS]?.let { RecentEpisodeCodec.decode(it) },
                extras[RADIO_RECENTS]?.let { FavoriteStationCodec.decode(it) },
            )
        }
        extras[MUSIC_HISTORY]?.let { replaceMusicHistory(app, PlayedTrackCodec.decode(it)) }

        val edit = app.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit()
        var anyPref = false
        PREF_KEYS.forEach { key ->
            val value = extras[PREF_PREFIX + key]?.let { decodePref(it) } ?: return@forEach
            anyPref = true
            when (value) {
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is String -> edit.putString(key, value)
                is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        if (anyPref) {
            edit.commit()
            // These two keep a copy in memory; refresh it so the hubs show the restored choice now.
            MoneyPrefs.reload(app)
            HubAppChoices.reload(app)
            PanchangPrefs.reload(app)
            MarketsWatchlist.reload(app)
            MarketsTileMarks.reload(app)
            SportsFavoritesStore.reload(app)
        }
    }

    /** One preference value as `type:value`; null for a missing or unsupported value. Pure. */
    internal fun encodePref(value: Any?): String? = when (value) {
        null -> null
        is Boolean -> "b:$value"
        is Int -> "i:$value"
        is Long -> "l:$value"
        is Float -> "f:$value"
        is String -> "s:$value"
        is Set<*> -> "S:" + JSONArray(value.filterIsInstance<String>()).toString()
        else -> null
    }

    internal fun decodePref(encoded: String): Any? {
        if (encoded.length < 2 || encoded[1] != ':') return null
        val body = encoded.substring(2)
        return runCatching {
            when (encoded[0]) {
                'b' -> body.toBooleanStrict()
                'i' -> body.toInt()
                'l' -> body.toLong()
                'f' -> body.toFloat()
                's' -> body
                'S' -> JSONArray(body).let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() }
                else -> null
            }
        }.getOrNull()
    }
}
