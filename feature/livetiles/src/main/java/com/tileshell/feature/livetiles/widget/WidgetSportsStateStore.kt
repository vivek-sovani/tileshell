package com.tileshell.feature.livetiles.widget

import android.content.Context

/**
 * Remembers, per placed sports widget, what its last rendered match looked
 * like — just enough for [com.tileshell.core.data.shouldFetchSports] to decide
 * whether the next periodic tick needs to touch the network at all.
 *
 * Persisted rather than held in memory because a widget refresh worker can
 * easily run in a freshly started process, which is exactly when a wrong
 * "nothing known, fetch everything" answer would be most expensive.
 *
 * Same plain-`SharedPreferences` shape as [WidgetColorStore] — three small
 * values per widget id, no DataStore needed.
 */
object WidgetSportsStateStore {
    private const val PREFS = "tileshell_widget_sports_state"

    /** The last rendered match's state/kick-off, and when it was fetched. */
    data class Snapshot(
        val state: String?,
        val kickoffMillis: Long?,
        val fetchedAtMillis: Long?,
    )

    fun snapshot(context: Context, appWidgetId: Int): Snapshot {
        val p = prefs(context)
        val fetchedAt = p.getLong(fetchedKey(appWidgetId), -1L)
        val kickoff = p.getLong(kickoffKey(appWidgetId), -1L)
        return Snapshot(
            state = p.getString(stateKey(appWidgetId), null),
            kickoffMillis = kickoff.takeIf { it > 0L },
            fetchedAtMillis = fetchedAt.takeIf { it > 0L },
        )
    }

    fun record(context: Context, appWidgetId: Int, state: String?, kickoffMillis: Long?, fetchedAtMillis: Long) {
        prefs(context).edit().apply {
            if (state == null) remove(stateKey(appWidgetId)) else putString(stateKey(appWidgetId), state)
            if (kickoffMillis == null) remove(kickoffKey(appWidgetId)) else putLong(kickoffKey(appWidgetId), kickoffMillis)
            putLong(fetchedKey(appWidgetId), fetchedAtMillis)
        }.apply()
    }

    /** Called from the provider's `onDeleted` so a removed widget doesn't linger. */
    fun clear(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove(stateKey(appWidgetId))
            .remove(kickoffKey(appWidgetId))
            .remove(fetchedKey(appWidgetId))
            .apply()
    }

    private fun stateKey(id: Int) = "state_$id"

    private fun kickoffKey(id: Int) = "kickoff_$id"

    private fun fetchedKey(id: Int) = "fetched_$id"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
