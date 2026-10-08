package com.tileshell.feature.start

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.Wallpapers
import com.tileshell.feature.livetiles.NotificationSnapshot
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Debug-only tile gallery (MainActivity `debug.gallery=<iconKey>[|activityName]`, debug builds only): one live tile at every
 * size, rendered through the real [TileView] at a 5-column phone's cell size, to check that text fits and the space
 * is used. Not reachable in a release build.
 */
object TileGalleryState {
    /** "iconKey", "iconKey|activityName" or "iconKey|activityName|package" (so notification faces find their notifications); null = closed. */
    val request = MutableStateFlow<String?>(null)
}

private const val GALLERY_CELL_DP = 72
private const val GALLERY_GAP_DP = 3

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TileGalleryLayer() {
    val request by TileGalleryState.request.collectAsState()
    val req = request ?: return
    val parts = req.split("|")
    val key = parts[0]
    val activity = parts.getOrElse(1) { "" }
    val pkg = parts.getOrElse(2) { "" }
    BackHandler { TileGalleryState.request.value = null }
    // Everything is drawn at half scale (density and text together, so fit is exactly the real one) with the front
    // and the back side by side, so one screenshot covers every size of both faces.
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density * 0.5f, d.fontScale),
    ) {
        Column(Modifier.fillMaxSize().background(Color(0xFFECE9E4)).statusBarsPadding().verticalScroll(rememberScrollState()).padding(8.dp)) {
            Text("$key · front | back", fontSize = 16.sp, color = Color.Black)
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(false, true).forEach { back ->
                    FlowRow(Modifier.size(390.dp, 1800.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TileSize.entries.forEach { size ->
                            val w = (size.cols * GALLERY_CELL_DP + (size.cols - 1) * GALLERY_GAP_DP).dp
                            val h = (size.rows * GALLERY_CELL_DP + (size.rows - 1) * GALLERY_GAP_DP).dp
                            Column {
                                Text(size.name.lowercase(), fontSize = 10.sp, color = Color.Black)
                                Box(Modifier.size(w, h)) {
                                    GalleryTile(key, activity, pkg, size, back)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryTile(key: String, activity: String, pkg: String, size: TileSize, back: Boolean) {
    val snapshot by com.tileshell.feature.livetiles.NotificationCenter.snapshot.collectAsState()
    TileView(
        tile = TileModel.App(
            id = "gallery-$key-${size.name}", position = 0, size = size, colorId = "blue",
            packageName = pkg, activityName = activity, label = key, iconKey = key,
        ),
        index = 0, editMode = false, selected = false, dragging = false, mergeTarget = false,
        accent = TileAccents.forId("blue"), glass = false, transparency = 0.55f, glassLine = Color.Transparent,
        tiledWallpaper = false, wallpaper = Wallpapers.Mono, wallpaperPhoto = null,
        wallpaperAlignX = 0.5f, wallpaperAlignY = 0.5f, wallpaperZoom = 1f, wallpaperOrigin = { Offset.Zero },
        fullWidth = 0f, fullHeight = 0f, jigglePhase = { 0f },
        flipped = back, liveActive = true, notifications = snapshot, badgeCount = snapshot.badges[pkg] ?: 0,
        darkTheme = true, canMoveBack = false, canMoveForward = false,
        onTap = {}, onLongPress = {}, onResize = {}, onUnpin = {}, onSelect = {}, onExitEdit = {}, onMove = {},
    )
}
