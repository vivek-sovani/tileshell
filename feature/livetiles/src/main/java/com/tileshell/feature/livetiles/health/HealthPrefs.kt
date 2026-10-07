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
)

/** The health hub's settings, in `tileshell.prefs` (so a backup carries them). */
object HealthPrefs {
    private const val PREFS = "tileshell.prefs"
    const val KEY_GOAL = "health_goal"
    const val KEY_HEIGHT = "health_height_cm"
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
        val next = transform(current(context)).let { it.copy(goal = it.goal.coerceIn(MIN_GOAL, MAX_GOAL), heightCm = it.heightCm.coerceIn(100, 230)) }
        prefs(context).edit().putInt(KEY_GOAL, next.goal).putInt(KEY_HEIGHT, next.heightCm).apply()
        _settings.value = next
    }

    private fun read(context: Context): HealthSettings {
        val p = prefs(context)
        return HealthSettings(goal = p.getInt(KEY_GOAL, DEFAULT_GOAL), heightCm = p.getInt(KEY_HEIGHT, DEFAULT_HEIGHT_CM))
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** The health tile's app marks (`health_tile_apps`). */
object HealthTileMarks : com.tileshell.feature.livetiles.shopping.HubTileMarks("health_tile_apps")
