package com.tileshell.feature.livetiles

import android.content.Context
import com.tileshell.core.data.toggleMarked
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the user marked "on tile" in the markets hub: watchlist symbols (shown
 * by the stocks, commodities, currencies and crypto tiles, each taking those
 * of its own kind) and indices (shown by the markets tile). In the shared
 * prefs file, carried by the manual backup. Indices are `null` until the user
 * first marks one, which means "just the NIFTY 50".
 */
object MarketsTileMarks {
    private const val PREFS = "tileshell.prefs"
    private const val KEY_SYMBOLS = "markets_tile_symbols"
    private const val KEY_INDICES = "markets_tile_indices"

    private val symbols = MutableStateFlow<List<String>?>(null)
    private val indices = MutableStateFlow<List<String>?>(null)
    private var indicesLoaded = false

    fun symbolsFlow(context: Context): StateFlow<List<String>> {
        if (symbols.value == null) symbols.value = readList(context, KEY_SYMBOLS) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        return symbols.asStateFlow() as StateFlow<List<String>>
    }

    /** `null` while the user has never marked an index. */
    fun indicesFlow(context: Context): StateFlow<List<String>?> {
        if (!indicesLoaded) {
            indices.value = readList(context, KEY_INDICES)
            indicesLoaded = true
        }
        return indices.asStateFlow()
    }

    fun toggleSymbol(context: Context, symbol: String) {
        val next = toggleMarked(symbolsFlow(context).value, symbol)
        save(context, KEY_SYMBOLS, next)
        symbols.value = next
    }

    fun toggleIndex(context: Context, symbol: String) {
        // Marking from the unsaved default starts from "just the NIFTY 50".
        val next = toggleMarked(indicesFlow(context).value ?: listOf("^NSEI"), symbol)
        save(context, KEY_INDICES, next)
        indices.value = next
    }

    /** Re-reads the stored marks (after a backup restore wrote them). */
    fun reload(context: Context) {
        symbols.value = readList(context, KEY_SYMBOLS) ?: emptyList()
        indices.value = readList(context, KEY_INDICES)
        indicesLoaded = true
    }

    private fun save(context: Context, key: String, list: List<String>) {
        // An emptied list is one blank line, so it isn't mistaken for "never saved".
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, list.joinToString("\n").ifEmpty { "\n" }).apply()
    }

    private fun readList(context: Context, key: String): List<String>? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }
}
