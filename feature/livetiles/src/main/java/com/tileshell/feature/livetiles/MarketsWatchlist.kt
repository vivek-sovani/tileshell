package com.tileshell.feature.livetiles

import android.content.Context
import com.tileshell.core.data.DEFAULT_WATCHLIST
import com.tileshell.core.data.WatchSymbol
import com.tileshell.core.data.decodeWatchlist
import com.tileshell.core.data.encodeWatchlist
import com.tileshell.core.data.watchlistWith
import com.tileshell.core.data.watchlistWithout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The markets hub's watchlist: stocks, commodities, currencies and crypto by
 * Yahoo symbol, in the shared prefs file (carried by the manual backup).
 * Never saved means [DEFAULT_WATCHLIST]; once edited, the saved list wins,
 * even when it's empty.
 */
object MarketsWatchlist {
    private const val PREFS = "tileshell.prefs"
    private const val KEY = "markets_watchlist"

    private val state = MutableStateFlow<List<WatchSymbol>?>(null)

    fun flow(context: Context): StateFlow<List<WatchSymbol>> {
        if (state.value == null) state.value = read(context)
        @Suppress("UNCHECKED_CAST")
        return state.asStateFlow() as StateFlow<List<WatchSymbol>>
    }

    fun current(context: Context): List<WatchSymbol> = state.value ?: read(context).also { state.value = it }

    fun add(context: Context, item: WatchSymbol) = save(context, watchlistWith(current(context), item))

    fun remove(context: Context, symbol: String) = save(context, watchlistWithout(current(context), symbol))

    fun reset(context: Context) = save(context, DEFAULT_WATCHLIST)

    /** Re-reads the stored list (after a backup restore wrote it). */
    fun reload(context: Context) {
        state.value = read(context)
    }

    private fun save(context: Context, list: List<WatchSymbol>) {
        // An emptied list is stored as one blank line so it isn't mistaken for "never saved".
        val raw = encodeWatchlist(list).ifEmpty { "\n" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
        state.value = list
    }

    private fun read(context: Context): List<WatchSymbol> =
        decodeWatchlist(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)) ?: DEFAULT_WATCHLIST
}
