package com.tileshell.feature.start

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tileshell.core.data.TileModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One pinned app tile, reduced to what [notificationMutedTileIds] needs. */
internal data class OwnedTile(val id: String, val packageName: String, val activityName: String)

/**
 * Notifications come per app, not per launcher entry. When several tiles are
 * pinned for one package (Amazon, Amazon Pay and Amazon Now are all one app),
 * every one of them used to show the same badge and message. Only the owner
 * keeps them: the tile for the app's main launcher entry ([defaultActivity]),
 * else the first tile pinned. Returns the ids of the other tiles. Pure.
 */
internal fun notificationMutedTileIds(tiles: List<OwnedTile>, defaultActivity: (String) -> String?): Set<String> =
    tiles.filter { it.packageName.isNotBlank() }
        .groupBy { it.packageName }
        .filterValues { it.size > 1 }
        .flatMap { (pkg, group) ->
            val main = defaultActivity(pkg)
            val owner = group.firstOrNull { main != null && sameActivity(it.activityName, main) } ?: group.first()
            group.filter { it !== owner }.map { it.id }
        }
        .toSet()

private fun sameActivity(a: String, b: String): Boolean =
    a == b || a.substringAfterLast('.') == b.substringAfterLast('.')

/** Ids of the Start tiles that must not repeat their app's notifications. */
@Composable
internal fun rememberNotificationMutedTileIds(models: Collection<TileModel>): Set<String> {
    val context = LocalContext.current
    val tiles = remember(models) {
        models.filterIsInstance<TileModel.App>().map { OwnedTile(it.id, it.packageName, it.activityName) }
    }
    val shared = remember(tiles) { tiles.filter { it.packageName.isNotBlank() }.groupBy { it.packageName }.filterValues { it.size > 1 } }
    if (shared.isEmpty()) return emptySet()
    val defaults by produceState<Map<String, String?>>(initialValue = emptyMap(), shared.keys) {
        value = withContext(Dispatchers.IO) { shared.keys.associateWith { launcherActivity(context, it) } }
    }
    return remember(tiles, defaults) { notificationMutedTileIds(tiles) { defaults[it] } }
}

private fun launcherActivity(context: Context, packageName: String): String? =
    runCatching { context.packageManager.getLaunchIntentForPackage(packageName)?.component?.className }.getOrNull()
