package com.tileshell.feature.livetiles

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Who the favourites tile shows. Until the user has pinned or ordered anyone
 * ([arranged] false) it's every starred contact; after that, only the pinned
 * ones in the user's order. A pinned contact who was deleted or unstarred
 * drops out and everyone below moves up. Pure.
 */
internal fun favouritesTilePeople(
    starred: List<PersonSummary>,
    order: List<String>,
    arranged: Boolean,
): List<PersonSummary> {
    if (!arranged) return starred
    val byKey = starred.associateBy { it.lookupKey }
    return order.distinct().mapNotNull { byKey[it] }
}

/** The tile's pinned keys as an editable list: the user's order, or every
 * starred contact before they've arranged anything. Pure. */
internal fun favouritesTileKeys(
    starred: List<PersonSummary>,
    order: List<String>,
    arranged: Boolean,
): List<String> = favouritesTilePeople(starred, order, arranged).map { it.lookupKey }

/**
 * How many people the favourites tile holds for its height in grid rows:
 * one on a 1-row tile, four on a 2x2 (user-chosen, so names can be large),
 * then two more for every extra row. Width doesn't change it. Pure.
 */
internal fun favouritesTileCapacity(rows: Int): Int = when {
    rows <= 1 -> 1
    else -> 4 + (rows - 2) * 2
}

/** The first [capacity] people, and how many are left for "+ N more". Pure. */
internal fun favouritesTileSplit(people: List<PersonSummary>, capacity: Int): Pair<List<PersonSummary>, Int> {
    val fit = capacity.coerceAtLeast(0)
    return people.take(fit) to (people.size - fit).coerceAtLeast(0)
}

/** [list] with the item at [from] moved to [to]. Pure. */
internal fun <T> moveItem(list: List<T>, from: Int, to: Int): List<T> {
    if (from !in list.indices) return list
    val target = to.coerceIn(0, list.lastIndex)
    if (from == target) return list
    return list.toMutableList().apply { add(target, removeAt(from)) }
}

/**
 * How many people the pinned favourites tile last had room for, so the
 * hub's "arrange tile" can show where the "+ more" cut falls. Measured by the
 * tile itself; kept across restarts. Null until a tile has been shown.
 */
object FavouritesTileCapacity {
    private const val PREFS = "tileshell.prefs"
    private const val KEY = "favourites_tile_capacity"
    private val _capacity = MutableStateFlow<Int?>(null)
    private var loaded = false

    fun capacity(context: Context): StateFlow<Int?> {
        if (!loaded) {
            loaded = true
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (p.contains(KEY)) _capacity.value = p.getInt(KEY, 0)
        }
        return _capacity.asStateFlow()
    }

    fun report(context: Context, capacity: Int) {
        capacity(context)
        if (_capacity.value == capacity) return
        _capacity.value = capacity
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY, capacity).apply()
    }
}
