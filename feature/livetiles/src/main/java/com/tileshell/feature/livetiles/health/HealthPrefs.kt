package com.tileshell.feature.livetiles.health

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HealthSettings(
    /** Steps a day to aim for. */
    val goal: Int = DEFAULT_GOAL,
    /** Height, for the distance estimate. */
    val heightCm: Int = DEFAULT_HEIGHT_CM,
    /** Bedtime reminder, minutes after midnight; -1 = off. */
    val bedtimeMinute: Int = -1,
    /** At each kilometre (mile) of a walk: 0 nothing, 1 a beep, 2 a beep and the time said aloud. */
    val kmAlert: Int = 2,
    /** Record a walk's route with GPS (needs the location permission; the walk works without it). */
    val recordRoute: Boolean = true,
    /** Walk details draw OpenStreetMap tiles under the route (fetched when a walk is opened). */
    val mapTiles: Boolean = true,
)

/** The health hub's settings, in `tileshell.prefs` (so a backup carries them). */
object HealthPrefs {
    private const val PREFS = "tileshell.prefs"
    const val KEY_GOAL = "health_goal"
    const val KEY_HEIGHT = "health_height_cm"
    const val KEY_BEDTIME = "health_bedtime_min"
    const val KEY_KM_ALERT = "health_km_alert"
    const val KEY_ROUTE = "health_route"
    const val KEY_MAP = "health_map_tiles"
    /** Set once the location question has been put, so a walk never asks twice. */
    const val KEY_ROUTE_ASKED = "health_route_asked"
    const val MIN_GOAL = 1_000
    const val MAX_GOAL = 30_000

    private val _settings = MutableStateFlow<HealthSettings?>(null)

    fun settings(context: Context): StateFlow<HealthSettings?> {
        if (_settings.value == null) _settings.value = read(context)
        return _settings.asStateFlow()
    }

    fun current(context: Context): HealthSettings = _settings.value ?: read(context).also { _settings.value = it }

    /** Re-reads the stored values (after a backup restore wrote them). */
    fun reload(context: Context) {
        _settings.value = read(context)
    }

    fun update(context: Context, transform: (HealthSettings) -> HealthSettings) {
        val next = transform(current(context)).let { it.copy(goal = it.goal.coerceIn(MIN_GOAL, MAX_GOAL), heightCm = it.heightCm.coerceIn(100, 230), bedtimeMinute = it.bedtimeMinute.coerceIn(-1, 1439), kmAlert = it.kmAlert.coerceIn(0, 2)) }
        prefs(context).edit().putInt(KEY_GOAL, next.goal).putInt(KEY_HEIGHT, next.heightCm).putInt(KEY_BEDTIME, next.bedtimeMinute).putInt(KEY_KM_ALERT, next.kmAlert).putBoolean(KEY_ROUTE, next.recordRoute).putBoolean(KEY_MAP, next.mapTiles).apply()
        _settings.value = next
    }

    private fun read(context: Context): HealthSettings {
        val p = prefs(context)
        return HealthSettings(goal = p.getInt(KEY_GOAL, DEFAULT_GOAL), heightCm = p.getInt(KEY_HEIGHT, DEFAULT_HEIGHT_CM), bedtimeMinute = p.getInt(KEY_BEDTIME, -1), kmAlert = p.getInt(KEY_KM_ALERT, 2), recordRoute = p.getBoolean(KEY_ROUTE, true), mapTiles = p.getBoolean(KEY_MAP, true))
    }

    fun routeAsked(context: Context): Boolean = prefs(context).getBoolean(KEY_ROUTE_ASKED, false)

    fun setRouteAsked(context: Context, asked: Boolean) {
        prefs(context).edit().putBoolean(KEY_ROUTE_ASKED, asked).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** The health tile's app marks (`health_tile_apps`). */
object HealthTileMarks : com.tileshell.feature.livetiles.shopping.HubTileMarks("health_tile_apps")
