package com.tileshell.feature.livetiles

import android.content.Context
import com.tileshell.core.data.SportsFavorites
import com.tileshell.core.data.SportsTile
import com.tileshell.core.data.decodeFavoriteSports
import com.tileshell.core.data.decodeFavoriteTeams
import com.tileshell.core.data.encodeFavoriteSports
import com.tileshell.core.data.encodeFavoriteTeams
import com.tileshell.core.data.toggleFavoriteSport
import com.tileshell.core.data.toggleFavoriteTeam
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The sports and teams marked as favourites on the sports hub, in the shared
 * prefs file (carried by the manual backup). Several of each can be marked.
 */
object SportsFavoritesStore {
    private const val PREFS = "tileshell.prefs"
    private const val KEY_SPORTS = "sports_fav_sports"
    private const val KEY_TEAMS = "sports_fav_teams"

    private val state = MutableStateFlow<SportsFavorites?>(null)

    fun flow(context: Context): StateFlow<SportsFavorites> {
        if (state.value == null) state.value = read(context)
        @Suppress("UNCHECKED_CAST")
        return state.asStateFlow() as StateFlow<SportsFavorites>
    }

    fun current(context: Context): SportsFavorites = state.value ?: read(context).also { state.value = it }

    fun toggleSport(context: Context, slug: String) = save(context, toggleFavoriteSport(current(context), slug))

    fun toggleTeam(context: Context, team: SportsTile.Selection) = save(context, toggleFavoriteTeam(current(context), team))

    /** Re-reads the stored favourites (after a backup restore wrote them). */
    fun reload(context: Context) {
        state.value = read(context)
    }

    private fun save(context: Context, fav: SportsFavorites) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SPORTS, encodeFavoriteSports(fav.sports))
            .putString(KEY_TEAMS, encodeFavoriteTeams(fav.teams))
            .apply()
        state.value = fav
    }

    private fun read(context: Context): SportsFavorites {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return SportsFavorites(
            sports = decodeFavoriteSports(p.getString(KEY_SPORTS, null)),
            teams = decodeFavoriteTeams(p.getString(KEY_TEAMS, null)),
        )
    }
}
