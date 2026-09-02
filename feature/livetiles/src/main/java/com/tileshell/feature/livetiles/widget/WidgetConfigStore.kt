package com.tileshell.feature.livetiles.widget

import android.content.Context

/**
 * Per-widget-instance non-colour configuration — the calendar-system widget's
 * picked system id (`"hindu"`, `"islamic"`, ...), the stock widget's full
 * [com.tileshell.core.data.StockTile.Selection] (single symbol, sector
 * basket, or custom multi-stock list — the same 3-way model the in-app tile
 * uses), and the commodity widget's picked ticker symbol + display name, all
 * genuine required per-instance data with no sensible default. Same plain-
 * `SharedPreferences` shape as [WidgetColorStore] — a handful of string
 * values doesn't need DataStore.
 *
 * The stock selection is stored as [com.tileshell.core.data.StockTile
 * .encodeSingle]/`encodeCategory`/`encodeMultiStock`'s own output verbatim
 * (decoded back via [com.tileshell.core.data.StockTile.decode]) — reusing
 * the in-app tile's already-unit-tested codec rather than re-deriving a
 * widget-only encoding for the same 3-way selection. Commodity stays a plain
 * symbol/display-name pair (its own in-app tile has no basket/multi concept
 * to preserve — see [com.tileshell.core.data.CommodityTile]'s own doc
 * comment), stored as two separate keys since [com.tileshell.core.data
 * .CommodityTile.encode]'s `"commodity:"` prefix is meant for a *shared*
 * tile-model column that also has to identify the tile kind — a per-
 * appWidgetId preference key already carries that distinction in its own
 * name, so re-adding the same prefix inside the value would be redundant.
 */
object WidgetConfigStore {
    private const val PREFS = "tileshell_widget_config"

    fun calendarSystemId(context: Context, appWidgetId: Int): String? =
        prefs(context).getString(calsysKey(appWidgetId), null)

    fun setCalendarSystemId(context: Context, appWidgetId: Int, systemId: String) {
        prefs(context).edit().putString(calsysKey(appWidgetId), systemId).apply()
    }

    fun stockSelectionEncoded(context: Context, appWidgetId: Int): String? =
        prefs(context).getString(stockKey(appWidgetId), null)

    fun setStockSelectionEncoded(context: Context, appWidgetId: Int, encoded: String) {
        prefs(context).edit().putString(stockKey(appWidgetId), encoded).apply()
    }

    fun commoditySymbol(context: Context, appWidgetId: Int): Pair<String, String>? {
        val symbol = prefs(context).getString(commoditySymbolKey(appWidgetId), null) ?: return null
        val name = prefs(context).getString(commodityNameKey(appWidgetId), null) ?: return null
        return symbol to name
    }

    fun setCommoditySymbol(context: Context, appWidgetId: Int, symbol: String, displayName: String) {
        prefs(context).edit()
            .putString(commoditySymbolKey(appWidgetId), symbol)
            .putString(commodityNameKey(appWidgetId), displayName)
            .apply()
    }

    /** Same "reuse the in-app tile's own codec" choice as stock — [com.tileshell.core.data.SportsTile] has only one selection shape (a league + team), so its `encode`/`decode` needs no widget-side reworking at all. */
    fun sportsSelectionEncoded(context: Context, appWidgetId: Int): String? =
        prefs(context).getString(sportsKey(appWidgetId), null)

    fun setSportsSelectionEncoded(context: Context, appWidgetId: Int, encoded: String) {
        prefs(context).edit().putString(sportsKey(appWidgetId), encoded).apply()
    }

    /**
     * A countdown widget's target date + label — same two-key shape as
     * commodity's symbol/display-name pair, for the same reason: [com
     * .tileshell.core.data.CountdownTile]'s own `"countdown:"`-prefixed
     * encoding is meant for a shared `TileModel` column that also has to
     * identify the tile kind, which a per-appWidgetId key already does by
     * its own name.
     */
    fun countdown(context: Context, appWidgetId: Int): Pair<String, String>? {
        val isoDate = prefs(context).getString(countdownDateKey(appWidgetId), null) ?: return null
        val label = prefs(context).getString(countdownLabelKey(appWidgetId), "") ?: ""
        return isoDate to label
    }

    fun setCountdown(context: Context, appWidgetId: Int, targetIsoDate: String, label: String) {
        prefs(context).edit()
            .putString(countdownDateKey(appWidgetId), targetIsoDate)
            .putString(countdownLabelKey(appWidgetId), label)
            .apply()
    }

    /** Called from each provider's `onDeleted` so a removed widget's config doesn't linger forever. */
    fun clear(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove(calsysKey(appWidgetId))
            .remove(stockKey(appWidgetId))
            .remove(commoditySymbolKey(appWidgetId))
            .remove(commodityNameKey(appWidgetId))
            .remove(sportsKey(appWidgetId))
            .remove(countdownDateKey(appWidgetId))
            .remove(countdownLabelKey(appWidgetId))
            .apply()
    }

    private fun calsysKey(appWidgetId: Int) = "calsys_$appWidgetId"
    private fun stockKey(appWidgetId: Int) = "stock_selection_$appWidgetId"
    private fun commoditySymbolKey(appWidgetId: Int) = "commodity_symbol_$appWidgetId"
    private fun commodityNameKey(appWidgetId: Int) = "commodity_name_$appWidgetId"
    private fun sportsKey(appWidgetId: Int) = "sports_selection_$appWidgetId"
    private fun countdownDateKey(appWidgetId: Int) = "countdown_date_$appWidgetId"
    private fun countdownLabelKey(appWidgetId: Int) = "countdown_label_$appWidgetId"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
