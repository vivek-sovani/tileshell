package com.tileshell.feature.livetiles.shopping

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which shopping apps the tile's back face shows ("on tile"): the entry ids the user marked on the hub's apps page. With
 * none marked the tile shows all of them. Kept in `tileshell.prefs` (so a backup carries it); [appsOnTile] is pure.
 */
object ShoppingTileMarks {
    private const val PREFS = "tileshell.prefs"
    const val KEY = "shopping_tile_apps"

    private val _marks = MutableStateFlow<Set<String>?>(null)

    fun marks(context: Context): StateFlow<Set<String>?> {
        if (_marks.value == null) _marks.value = read(context)
        return _marks.asStateFlow()
    }

    /** Re-reads the stored marks (after a backup restore wrote them). */
    fun reload(context: Context) {
        _marks.value = read(context)
    }

    fun toggle(context: Context, id: String) {
        val current = _marks.value ?: read(context)
        val next = if (id in current) current - id else current + id
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, next.joinToString("\n")).apply()
        _marks.value = next
    }

    private fun read(context: Context): Set<String> =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            .orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

/** The apps the tile shows: the marked ones (in the apps' own order), or all when none is marked or none marked is still installed. Pure. */
fun appsOnTile(apps: List<ShoppingApp>, marks: Set<String>): List<ShoppingApp> {
    if (marks.isEmpty()) return apps
    val marked = apps.filter { it.packageName in marks }
    return marked.ifEmpty { apps }
}
