package com.tileshell.core.data.settings

import android.content.Context

/**
 * A synchronous copy of the refresh-rate settings, for background schedulers
 * and widget workers that can't suspend on the settings DataStore (a widget
 * provider's onUpdate, a tile's first composition). Start mirrors the
 * settings into it whenever they change.
 */
object RefreshRatePrefs {
    private const val PREFS = "tileshell.prefs"
    const val WEATHER = "refresh_weather"
    const val NEWS = "refresh_news"
    const val STOCK = "refresh_stock"
    const val COMMODITY = "refresh_commodity"
    const val SPORTS = "refresh_sports"

    fun rate(context: Context, key: String): LiveRefreshRate {
        val name = prefs(context).getString(key, null) ?: return LiveRefreshRate.DEFAULT
        return LiveRefreshRate.entries.find { it.name == name } ?: LiveRefreshRate.DEFAULT
    }

    /** Writes [rate] under [key]; true when it changed. */
    fun set(context: Context, key: String, rate: LiveRefreshRate): Boolean {
        if (rate(context, key) == rate) return false
        prefs(context).edit().putString(key, rate.name).apply()
        return true
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
