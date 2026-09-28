package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.tileshell.core.data.settings.LauncherSettings
import com.tileshell.core.data.settings.RefreshRatePrefs
import com.tileshell.feature.livetiles.FeedRefreshWorker
import com.tileshell.feature.livetiles.WeatherRefreshWorker

/**
 * Keeps background refreshes in step with personalize's "live data refresh":
 * mirrors the rates into [RefreshRatePrefs] and, when any changed, re-applies
 * them to the weather and news fetches and to each placed widget kind.
 * Widgets nobody placed are left unscheduled.
 */
object RefreshRateScheduler {
    fun sync(context: Context, settings: LauncherSettings) {
        val weather = RefreshRatePrefs.set(context, RefreshRatePrefs.WEATHER, settings.weatherRefreshRate)
        val news = RefreshRatePrefs.set(context, RefreshRatePrefs.NEWS, settings.newsRefreshRate)
        val stock = RefreshRatePrefs.set(context, RefreshRatePrefs.STOCK, settings.stockRefreshRate)
        val commodity = RefreshRatePrefs.set(context, RefreshRatePrefs.COMMODITY, settings.commodityRefreshRate)
        val sports = RefreshRatePrefs.set(context, RefreshRatePrefs.SPORTS, settings.sportsRefreshRate)

        if (weather) {
            WeatherRefreshWorker.reschedule(context)
            if (hasWidgets(context, WeatherAppWidgetProvider::class.java)) WeatherWidgetRefreshWorker.ensureScheduled(context)
        }
        if (news) FeedRefreshWorker.reschedule(context)
        if (stock && hasWidgets(context, StockAppWidgetProvider::class.java)) StockWidgetRefreshWorker.ensureScheduled(context)
        if (commodity && hasWidgets(context, CommodityAppWidgetProvider::class.java)) CommodityWidgetRefreshWorker.ensureScheduled(context)
        if (sports && hasWidgets(context, SportsAppWidgetProvider::class.java)) SportsWidgetRefreshWorker.ensureScheduled(context)
    }

    private fun hasWidgets(context: Context, provider: Class<*>): Boolean = runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider)).isNotEmpty()
    }.getOrDefault(false)
}
