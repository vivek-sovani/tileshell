package com.tileshell.feature.livetiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.data.AppCatalogRepository
import com.tileshell.core.data.AppCategories
import com.tileshell.core.data.AppEntry
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileIcons
import kotlinx.coroutines.delay

/** One app on the music + videos apps tile, with its pending notification count. */
data class MediaApp(val packageName: String, val label: String, val badge: Int)

/**
 * The installed video apps and music apps, as the hub's apps page splits them
 * ([AppCategories.isVideoApp] / [AppCategories.isMusicApp]): one entry per
 * package and per name, apps with notifications first, then by name.
 */
fun mediaAppsOf(catalog: List<AppEntry>, badges: Map<String, Int>): Pair<List<MediaApp>, List<MediaApp>> {
    fun pick(match: (AppEntry) -> Boolean) = catalog.filter(match)
        .distinctBy { it.packageName }
        .distinctBy { it.label.trim().lowercase() }
        .map { MediaApp(it.packageName, it.label, badges[it.packageName] ?: 0) }
        .sortedWith(compareByDescending<MediaApp> { it.badge }.thenBy { it.label.lowercase() })
    return pick(AppCategories::isVideoApp) to pick(AppCategories::isMusicApp)
}

// How long each face stays up before the tile turns, like the people apps tile.
private const val MEDIA_APPS_FLIP_MS = 15_000L

/**
 * The tile for the music + videos hub's "apps" page ("pin" on that page):
 * the video and streaming apps on one face, the music apps on the other, each
 * a tappable icon with its notification badge. A tap anywhere else opens the
 * hub on its apps page. With only one kind installed it shows just that face.
 */
@Composable
fun MediaAppsTileFace(size: TileSize, active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { AppCatalogRepository(context) }
    val catalog by repository.apps.collectAsStateWithLifecycle(initialValue = emptyList())
    val snapshot by NotificationCenter.snapshot.collectAsStateWithLifecycle()
    val (video, music) = remember(catalog, snapshot) { mediaAppsOf(catalog, snapshot.badges) }
    val color = LocalTileFaceColor.current
    if (video.isEmpty() && music.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(TileIcons["mediaapps"], contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
        }
        return
    }
    val canFlip = video.isNotEmpty() && music.isNotEmpty()
    var flipped by remember { mutableStateOf(false) }
    LaunchedEffect(active, canFlip) {
        if (!active || !canFlip) {
            flipped = false
            return@LaunchedEffect
        }
        while (true) {
            delay(MEDIA_APPS_FLIP_MS)
            flipped = !flipped
        }
    }
    val frontIsVideo = video.isNotEmpty()
    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { MediaAppIconGrid(if (frontIsVideo) video else music, if (frontIsVideo) "video" else "music", size) },
        back = { MediaAppIconGrid(music, "music", size) },
    )
}

@Composable
private fun MediaAppIconGrid(apps: List<MediaApp>, label: String, size: TileSize) {
    val context = LocalContext.current
    val color = LocalTileFaceColor.current
    val columns = size.cols.coerceAtLeast(1)
    val rows = size.rows.coerceAtLeast(1)
    val showLabel = rows >= 2
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = if (showLabel) 18.dp else 6.dp),
        ) {
            apps.take(columns * rows).chunked(columns).forEach { rowApps ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    rowApps.forEach { app ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { openApp(context, app.packageName) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            MediaAppIcon(app, color)
                        }
                    }
                    repeat(columns - rowApps.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (showLabel) {
            Text(
                label,
                color = color,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun MediaAppIcon(app: MediaApp, color: Color) {
    val icon = rememberMonochromeAppIcon(app.packageName, sizePx = iconPx(28.dp))
    Box(modifier = Modifier.size(34.dp)) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = app.label,
                colorFilter = ColorFilter.tint(color),
                modifier = Modifier.size(28.dp).align(Alignment.Center),
            )
        }
        if (app.badge > 0) {
            OutlinedCountBadge(
                count = app.badge,
                ring = color,
                fill = Color(0xFF111111),
                modifier = Modifier.align(Alignment.TopEnd),
                diameter = 16.dp,
            )
        }
    }
}
