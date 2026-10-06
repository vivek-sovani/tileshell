package com.tileshell.feature.livetiles

import android.content.Context
import com.tileshell.core.data.MoonTimes
import com.tileshell.core.data.Observance
import com.tileshell.core.data.ObservanceSettings
import com.tileshell.core.data.PanchangObservances
import com.tileshell.feature.livetiles.widget.CalendarSystemWidgetRefreshWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which tithis the Panchang tile and widget highlight, and whether festivals
 * show — in the shared prefs file so the widget worker reads the same thing.
 */
object PanchangPrefs {
    private const val PREFS = "tileshell.prefs"
    private const val KEY_HIGHLIGHTS = "panchang_highlights"
    private const val KEY_FESTIVALS = "panchang_festivals"
    private const val KEY_CUSTOM = "panchang_custom_tithis"
    private const val KEY_GRAHAN = "panchang_grahan"

    private val _settings = MutableStateFlow<ObservanceSettings?>(null)

    fun settings(context: Context): StateFlow<ObservanceSettings?> {
        if (_settings.value == null) _settings.value = read(context)
        return _settings.asStateFlow()
    }

    /** Re-reads the stored values (after a backup restore wrote them). */
    fun reload(context: Context) {
        _settings.value = read(context)
        runCatching { CalendarSystemWidgetRefreshWorker.refreshNow(context.applicationContext) }
    }

    fun current(context: Context): ObservanceSettings = _settings.value ?: read(context).also { _settings.value = it }

    fun update(context: Context, transform: (ObservanceSettings) -> ObservanceSettings) {
        val next = transform(current(context))
        prefs(context).edit()
            .putStringSet(KEY_HIGHLIGHTS, next.highlights)
            .putBoolean(KEY_FESTIVALS, next.festivals)
            .putStringSet(KEY_CUSTOM, next.customTithis)
            .putBoolean(KEY_GRAHAN, next.grahan)
            .apply()
        _settings.value = next
        // The widget shows the same strip.
        runCatching { CalendarSystemWidgetRefreshWorker.refreshNow(context.applicationContext) }
    }

    private fun read(context: Context): ObservanceSettings {
        val p = prefs(context)
        val defaults = ObservanceSettings()
        return ObservanceSettings(
            highlights = p.getStringSet(KEY_HIGHLIGHTS, null)?.toSet() ?: defaults.highlights,
            festivals = p.getBoolean(KEY_FESTIVALS, defaults.festivals),
            customTithis = p.getStringSet(KEY_CUSTOM, null)?.toSet() ?: emptySet(),
            grahan = p.getBoolean(KEY_GRAHAN, defaults.grahan),
        )
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** First moonrise after a time at [latitude]/[longitude], for Sankashti. */
internal fun moonriseAfter(latitude: Double, longitude: Double): (Long) -> Long? =
    { MoonTimes.nextMoonriseMoonset(it, latitude, longitude).moonriseMillis }

/**
 * The tile/widget strip for a day's observances: festival first, at most two
 * names, and the moonrise time on Sankashti ("संकष्टी चतुर्थी · चंद्रोदय ९:०२").
 * Null when there's nothing. Pure.
 */
internal fun observanceStripText(all: List<Observance>, sankashtiMoonrise: Long?): String? {
    // A grahan that can't be seen from here isn't worth the tile's space.
    val observances = all.filter { !it.grahan || it.visibleHere }
    if (observances.isEmpty()) return null
    val names = observances.take(2).joinToString(" · ") { it.name }
    val moon = if (observances.any { it.id == "sankashti" } && sankashtiMoonrise != null) {
        " · चंद्रोदय ${shortDevanagariTime(sankashtiMoonrise)}"
    } else {
        ""
    }
    return names + moon
}

/** Today's moonrise for the Sankashti strip: the first after 5 pm today. */
internal fun eveningMoonrise(nowMillis: Long, latitude: Double, longitude: Double): Long? {
    val day = PanchangObservances.startOfDay(nowMillis, java.util.TimeZone.getDefault())
    return MoonTimes.nextMoonriseMoonset(day + 17 * 3_600_000L, latitude, longitude).moonriseMillis
}

/** "७:४७" — the 12-hour time in Devanagari digits, without the day-part word (evening is implied). */
internal fun shortDevanagariTime(epochMillis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    val hour = (cal.get(java.util.Calendar.HOUR_OF_DAY) % 12).let { if (it == 0) 12 else it }
    val minute = cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
    return com.tileshell.core.data.PanchangDevanagari.digits(hour) + ":" +
        minute.map { com.tileshell.core.data.PanchangDevanagari.digits(it - '0') }.joinToString("")
}
