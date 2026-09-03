package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tileshell.core.data.CLOSED_MARKET_REFRESH_MS
import com.tileshell.core.data.fetchCommodityQuote
import com.tileshell.core.data.fetchCommoditySparkline
import com.tileshell.core.data.formatStockChangePercent
import com.tileshell.core.data.formatStockPrice
import com.tileshell.feature.livetiles.R
import java.util.concurrent.TimeUnit

private const val POSITIVE_GREEN = 0xFF35C759.toInt()
private const val NEGATIVE_RED = 0xFFFF453A.toInt()

/**
 * Builds + pushes the commodity/currency widget's [RemoteViews] — same shell
 * shape as [StockWidgetRefreshWorker] (they share the same Yahoo-backed
 * [com.tileshell.core.data.StockQuote] model and formatters), but the
 * in-app tile here already is single-symbol-only (see [com.tileshell.core
 * .data.CommodityTile]'s own doc comment: "one tile is always exactly one
 * symbol"), so no scoping decision was needed the way [StockAppWidgetProvider]
 * required.
 */
class CommodityWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        pushAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC = "tileshell_commodity_widget_refresh"
        private const val UNIQUE_NOW = "tileshell_commodity_widget_refresh_now"

        fun ensureScheduled(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CommodityWidgetRefreshWorker>(CLOSED_MARKET_REFRESH_MS, TimeUnit.MILLISECONDS).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_PERIODIC)
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CommodityWidgetRefreshWorker>().build(),
            )
        }

        suspend fun pushAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CommodityAppWidgetProvider::class.java))
            if (ids.isEmpty()) return

            ids.forEach { id ->
                val picked = WidgetConfigStore.commoditySymbol(context, id)
                val minWidthDp = manager.getAppWidgetOptions(id)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                val (accent, onAccent) = resolveWidgetAccent(context, id)
                val views = buildRemoteViews(context, id, picked, accent, onAccent, isCompactWidget(minWidthDp))
                manager.updateAppWidget(id, views)
            }
        }

        private suspend fun buildRemoteViews(
            context: Context,
            appWidgetId: Int,
            picked: Pair<String, String>?,
            accent: Int,
            onAccent: Int,
            compact: Boolean,
        ): RemoteViews {
            val layout = if (compact) R.layout.widget_commodity_compact else R.layout.widget_commodity
            val views = RemoteViews(context.packageName, layout)
            views.setImageViewBitmap(R.id.widget_bg, accentGradientBitmap(accent))
            // Rounds the widget's own corners (see widget_rounded_background.xml) —
            // the background drawable provides an Outline; clipToOutline clips
            // widget_bg's full-bleed gradient (and everything else) to it.
            views.setBoolean(R.id.widget_root, "setClipToOutline", true)
            views.setOnClickPendingIntent(R.id.widget_settings, reconfigurePendingIntent(context, appWidgetId))
            setBaseColors(views, onAccent, compact)
            views.setImageViewResource(R.id.widget_icon, commodityIconRes(picked?.first))

            if (picked == null) {
                views.setOnClickPendingIntent(R.id.widget_root, reconfigurePendingIntent(context, appWidgetId))
                views.setTextViewText(R.id.widget_name, "no commodity picked")
                views.setTextViewText(R.id.widget_price, "tap the gear to choose one")
                views.setTextViewText(R.id.widget_change, "")
                views.setTextViewText(R.id.widget_back_name, "no commodity picked")
                views.setTextViewText(R.id.widget_hilo, "")
                return views
            }

            val (symbol, displayName) = picked
            views.setOnClickPendingIntent(R.id.widget_root, commodityAppPendingIntent(context, appWidgetId, displayName))
            views.setTextViewText(R.id.widget_name, displayName)
            views.setTextViewText(R.id.widget_back_name, displayName)

            val quote = fetchCommodityQuote(symbol)
            if (quote == null) {
                views.setTextViewText(R.id.widget_price, "no data")
                views.setTextViewText(R.id.widget_change, "")
                views.setTextViewText(R.id.widget_hilo, "no data yet")
                return views
            }

            val changeColor = if (quote.change < 0) NEGATIVE_RED else POSITIVE_GREEN
            val sign = if (quote.change >= 0) "+" else ""
            views.setTextViewText(R.id.widget_price, formatStockPrice(quote.price, quote.currency))
            views.setTextViewText(
                R.id.widget_change,
                "$sign${formatStockPrice(quote.change, "")} (${formatStockChangePercent(quote.changePercent)})",
            )
            views.setTextColor(R.id.widget_change, changeColor)
            views.setTextViewText(
                R.id.widget_hilo,
                "high ${formatStockPrice(quote.dayHigh, quote.currency)} · low ${formatStockPrice(quote.dayLow, quote.currency)}",
            )
            if (!compact) {
                views.setTextViewText(R.id.widget_market, if (quote.marketOpen) "market open" else "market closed")
                val sparkline = fetchCommoditySparkline(symbol)
                if (sparkline.size >= 2) {
                    views.setViewVisibility(R.id.widget_sparkline, View.VISIBLE)
                    views.setImageViewBitmap(R.id.widget_sparkline, sparklineBitmap(sparkline, changeColor))
                } else {
                    views.setViewVisibility(R.id.widget_sparkline, View.GONE)
                }
            }
            return views
        }

        private fun setBaseColors(views: RemoteViews, onAccent: Int, compact: Boolean) {
            views.setTextColor(R.id.widget_name, onAccent)
            views.setTextColor(R.id.widget_price, onAccent)
            views.setTextColor(R.id.widget_back_name, onAccent)
            views.setTextColor(R.id.widget_hilo, onAccent)
            views.setInt(R.id.widget_icon, "setColorFilter", onAccent)
            if (!compact) views.setTextColor(R.id.widget_market, onAccent)
        }

        /** Resolves [commodityIconKeyFor]'s pure key to a real drawable — user-requested per-category icon. */
        private fun commodityIconRes(symbol: String?): Int = when (symbol?.let { commodityIconKeyFor(it) }) {
            "metal" -> R.drawable.ic_widget_commodity_metal
            "energy" -> R.drawable.ic_widget_commodity_energy
            "currency" -> R.drawable.ic_widget_commodity_currency
            else -> R.drawable.ic_widget_commodity
        }
    }
}
