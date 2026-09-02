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
import com.tileshell.core.data.StockTile
import com.tileshell.core.data.fetchStockQuote
import com.tileshell.core.data.fetchStockSparkline
import com.tileshell.core.data.formatStockChangePercent
import com.tileshell.core.data.formatStockPrice
import com.tileshell.core.data.indexDisplayNameFor
import com.tileshell.core.data.indexTickerFor
import com.tileshell.core.data.stockCategoryFor
import com.tileshell.feature.livetiles.R
import java.util.concurrent.TimeUnit

private const val POSITIVE_GREEN = 0xFF35C759.toInt()
private const val NEGATIVE_RED = 0xFFFF453A.toInt()

private data class MemberRowIds(val row: Int, val name: Int, val price: Int, val change: Int)

private val MEMBER_ROW_IDS_FULL = listOf(
    MemberRowIds(R.id.widget_member_row_1, R.id.widget_member_name_1, R.id.widget_member_price_1, R.id.widget_member_change_1),
    MemberRowIds(R.id.widget_member_row_2, R.id.widget_member_name_2, R.id.widget_member_price_2, R.id.widget_member_change_2),
    MemberRowIds(R.id.widget_member_row_3, R.id.widget_member_name_3, R.id.widget_member_price_3, R.id.widget_member_change_3),
    MemberRowIds(R.id.widget_member_row_4, R.id.widget_member_name_4, R.id.widget_member_price_4, R.id.widget_member_change_4),
    MemberRowIds(R.id.widget_member_row_5, R.id.widget_member_name_5, R.id.widget_member_price_5, R.id.widget_member_change_5),
    MemberRowIds(R.id.widget_member_row_6, R.id.widget_member_name_6, R.id.widget_member_price_6, R.id.widget_member_change_6),
)

/**
 * Builds + pushes the stock widget's [RemoteViews]. Mirrors the in-app
 * tile's full [StockTile.Selection] model — a single symbol, a curated
 * sector basket, or a custom multi-stock list — rather than the single-
 * symbol-only scope this widget shipped with initially: a [StockTile
 * .Selection.Single] renders the same front (name/price/change/market) +
 * sparkline-back shape as before; [StockTile.Selection.Category]/
 * [StockTile.Selection.MultiStock] render the group's own tracked index on
 * the front (there's no one member that represents the whole group — same
 * reasoning as the in-app tile's `StockIndexFront`) and a fixed member-list
 * back instead of a sparkline (RemoteViews has no per-item adapter view
 * simple enough for a handful of rows — a fixed 6-row layout with unused
 * rows hidden mirrors the same "hide the row" idiom [WeatherWidgetRefreshWorker]
 * already uses for its 7-day forecast).
 *
 * No cache layer, unlike weather — the in-app tile has none either (it polls
 * Yahoo directly from a `LaunchedEffect`), so the worker just fetches inline
 * in [doWork] the same way. Periodic cadence uses [CLOSED_MARKET_REFRESH_MS]
 * (15 min) flat: WorkManager's own periodic floor is already 15 min, so
 * [com.tileshell.core.data.effectiveMarketRefreshMs]'s market-hours-aware
 * *faster* cadence can't be honoured by a periodic widget job anyway — only
 * its *slower* outside-hours floor matters here, and that's already what 15
 * min flat gives.
 */
class StockWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        pushAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC = "tileshell_stock_widget_refresh"
        private const val UNIQUE_NOW = "tileshell_stock_widget_refresh_now"

        fun ensureScheduled(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<StockWidgetRefreshWorker>(CLOSED_MARKET_REFRESH_MS, TimeUnit.MILLISECONDS).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_PERIODIC)
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<StockWidgetRefreshWorker>().build(),
            )
        }

        suspend fun pushAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StockAppWidgetProvider::class.java))
            if (ids.isEmpty()) return

            ids.forEach { id ->
                val selection = WidgetConfigStore.stockSelectionEncoded(context, id)?.let { StockTile.decode(it) }
                val minWidthDp = manager.getAppWidgetOptions(id)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                val (accent, onAccent) = resolveWidgetAccent(context, id)
                val compact = isCompactWidget(minWidthDp)
                val views = when (selection) {
                    null -> buildEmptyRemoteViews(context, id, accent, onAccent, compact)
                    is StockTile.Selection.Single -> buildSingleRemoteViews(context, id, selection, accent, onAccent, compact)
                    is StockTile.Selection.Category -> buildGroupRemoteViews(
                        context, id, accent, onAccent, compact,
                        displayName = selection.displayName,
                        indexTicker = stockCategoryFor(selection.categoryId)?.indexTicker ?: "^GSPC",
                        indexDisplayName = stockCategoryFor(selection.categoryId)?.indexDisplayName ?: selection.displayName,
                        members = stockCategoryFor(selection.categoryId)?.symbols?.map { it.symbol to it.displayName } ?: emptyList(),
                    )
                    is StockTile.Selection.MultiStock -> {
                        val primary = selection.symbols.firstOrNull()
                        buildGroupRemoteViews(
                            context, id, accent, onAccent, compact,
                            displayName = selection.displayName,
                            indexTicker = primary?.let { indexTickerFor(it.first) } ?: "^GSPC",
                            indexDisplayName = primary?.let { indexDisplayNameFor(it.first) } ?: selection.displayName,
                            members = selection.symbols,
                        )
                    }
                }
                manager.updateAppWidget(id, views)
            }
        }

        private fun buildEmptyRemoteViews(context: Context, appWidgetId: Int, accent: Int, onAccent: Int, compact: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, if (compact) R.layout.widget_stock_compact else R.layout.widget_stock)
            views.setImageViewBitmap(R.id.widget_bg, accentGradientBitmap(accent))
            // Rounds the widget's own corners (see widget_rounded_background.xml) —
            // the background drawable provides an Outline; clipToOutline clips
            // widget_bg's full-bleed gradient (and everything else) to it.
            views.setBoolean(R.id.widget_root, "setClipToOutline", true)
            views.setOnClickPendingIntent(R.id.widget_settings, reconfigurePendingIntent(context, appWidgetId))
            views.setOnClickPendingIntent(R.id.widget_root, reconfigurePendingIntent(context, appWidgetId))
            setBaseColors(views, onAccent, compact)
            views.setTextColor(R.id.widget_hilo, onAccent)
            views.setTextViewText(R.id.widget_name, "no stock picked")
            views.setTextViewText(R.id.widget_price, "tap the gear to choose one")
            views.setTextViewText(R.id.widget_change, "")
            views.setTextViewText(R.id.widget_back_name, "no stock picked")
            views.setTextViewText(R.id.widget_hilo, "")
            return views
        }

        private suspend fun buildSingleRemoteViews(
            context: Context,
            appWidgetId: Int,
            selection: StockTile.Selection.Single,
            accent: Int,
            onAccent: Int,
            compact: Boolean,
        ): RemoteViews {
            val layout = if (compact) R.layout.widget_stock_compact else R.layout.widget_stock
            val views = RemoteViews(context.packageName, layout)
            views.setImageViewBitmap(R.id.widget_bg, accentGradientBitmap(accent))
            // Rounds the widget's own corners (see widget_rounded_background.xml) —
            // the background drawable provides an Outline; clipToOutline clips
            // widget_bg's full-bleed gradient (and everything else) to it.
            views.setBoolean(R.id.widget_root, "setClipToOutline", true)
            views.setOnClickPendingIntent(R.id.widget_settings, reconfigurePendingIntent(context, appWidgetId))
            setBaseColors(views, onAccent, compact)

            val (symbol, displayName) = selection
            views.setOnClickPendingIntent(R.id.widget_root, stockAppPendingIntent(context, appWidgetId, displayName))
            views.setTextViewText(R.id.widget_name, displayName)
            views.setTextViewText(R.id.widget_back_name, displayName)
            views.setTextColor(R.id.widget_hilo, onAccent)

            val quote = fetchStockQuote(symbol)
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
                val sparkline = fetchStockSparkline(symbol)
                if (sparkline.size >= 2) {
                    views.setViewVisibility(R.id.widget_sparkline, View.VISIBLE)
                    views.setImageViewBitmap(R.id.widget_sparkline, sparklineBitmap(sparkline, changeColor))
                } else {
                    views.setViewVisibility(R.id.widget_sparkline, View.GONE)
                }
            }
            return views
        }

        private suspend fun buildGroupRemoteViews(
            context: Context,
            appWidgetId: Int,
            accent: Int,
            onAccent: Int,
            compact: Boolean,
            displayName: String,
            indexTicker: String,
            indexDisplayName: String,
            members: List<Pair<String, String>>,
        ): RemoteViews {
            val layout = if (compact) R.layout.widget_stock_group_compact else R.layout.widget_stock_group
            val views = RemoteViews(context.packageName, layout)
            views.setImageViewBitmap(R.id.widget_bg, accentGradientBitmap(accent))
            // Rounds the widget's own corners (see widget_rounded_background.xml) —
            // the background drawable provides an Outline; clipToOutline clips
            // widget_bg's full-bleed gradient (and everything else) to it.
            views.setBoolean(R.id.widget_root, "setClipToOutline", true)
            views.setOnClickPendingIntent(R.id.widget_settings, reconfigurePendingIntent(context, appWidgetId))
            setBaseColors(views, onAccent, compact)

            views.setOnClickPendingIntent(R.id.widget_root, stockAppPendingIntent(context, appWidgetId, displayName))
            views.setTextViewText(R.id.widget_name, indexDisplayName)
            views.setTextViewText(R.id.widget_back_name, displayName)

            val indexQuote = fetchStockQuote(indexTicker)
            if (indexQuote == null) {
                views.setTextViewText(R.id.widget_price, "no data")
                views.setTextViewText(R.id.widget_change, "")
            } else {
                val changeColor = if (indexQuote.change < 0) NEGATIVE_RED else POSITIVE_GREEN
                val sign = if (indexQuote.change >= 0) "+" else ""
                views.setTextViewText(R.id.widget_price, formatStockPrice(indexQuote.price, indexQuote.currency))
                views.setTextViewText(
                    R.id.widget_change,
                    "$sign${formatStockPrice(indexQuote.change, "")} (${formatStockChangePercent(indexQuote.changePercent)})",
                )
                views.setTextColor(R.id.widget_change, changeColor)
                if (!compact) views.setTextViewText(R.id.widget_market, if (indexQuote.marketOpen) "market open" else "market closed")
            }

            val rowIds = MEMBER_ROW_IDS_FULL.take(if (compact) 4 else 6)
            val memberQuotes = members.take(rowIds.size).map { (symbol, name) -> Triple(symbol, name, fetchStockQuote(symbol)) }
            rowIds.forEachIndexed { index, ids ->
                val entry = memberQuotes.getOrNull(index)
                if (entry == null) {
                    views.setViewVisibility(ids.row, View.GONE)
                    return@forEachIndexed
                }
                val (_, name, quote) = entry
                views.setViewVisibility(ids.row, View.VISIBLE)
                views.setTextViewText(ids.name, name)
                views.setTextColor(ids.name, onAccent)
                if (quote != null) {
                    views.setTextViewText(ids.price, formatStockPrice(quote.price, quote.currency))
                    views.setTextColor(ids.price, onAccent)
                    views.setTextViewText(ids.change, formatStockChangePercent(quote.changePercent))
                    views.setTextColor(ids.change, if (quote.change < 0) NEGATIVE_RED else POSITIVE_GREEN)
                } else {
                    views.setTextViewText(ids.price, "")
                    views.setTextViewText(ids.change, "…")
                    views.setTextColor(ids.change, onAccent)
                }
            }
            return views
        }

        private fun setBaseColors(views: RemoteViews, onAccent: Int, compact: Boolean) {
            views.setTextColor(R.id.widget_name, onAccent)
            views.setTextColor(R.id.widget_price, onAccent)
            views.setTextColor(R.id.widget_back_name, onAccent)
            if (!compact) views.setTextColor(R.id.widget_market, onAccent)
        }
    }
}
