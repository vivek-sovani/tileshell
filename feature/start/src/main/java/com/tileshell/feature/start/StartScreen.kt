package com.tileshell.feature.start

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.Wallpapers
import com.tileshell.core.design.tiltOnPress
import com.tileshell.core.design.wallpaperBackground

/**
 * The real Start screen: persisted tiles packed by the dense packer, rendered
 * as monoline glyphs on accent fills with lowercase lower-left labels (icon
 * only on small), over the aurora wallpaper. Tapping an app tile launches it
 * via LauncherApps. Insets follow FR-1 (status bar at top, room at the bottom).
 */
@Composable
fun StartScreen(
    modifier: Modifier = Modifier,
    viewModel: StartViewModel = viewModel(),
) {
    val tiles by viewModel.tiles.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val specs = remember(tiles) { tiles.map { TileSpec(it.id, it.size) } }
    val byId = remember(tiles) { tiles.associateBy { it.id } }

    val scrollState = rememberScrollState()
    // Home press (delivered via the ViewModel) scrolls Start back to the top.
    LaunchedEffect(Unit) {
        viewModel.homeRequests.collect { scrollState.animateScrollTo(0) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .wallpaperBackground(Wallpapers.Aurora),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            DenseTileGrid(tiles = specs, modifier = Modifier.fillMaxWidth()) { spec ->
                byId[spec.id]?.let { model ->
                    TileView(model) { onTileClick(context, model) }
                }
            }
            // FR-1 bottom breathing room (prototype home-scroll padding-bottom:74px).
            Spacer(Modifier.height(74.dp))
        }
    }
}

@Composable
private fun TileView(tile: TileModel, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .tiltOnPress()
            .background(TileAccents.forId(tile.colorId))
            .clickable(onClick = onClick),
    ) {
        when (tile) {
            is TileModel.App -> AppTileContent(tile)
            is TileModel.Folder -> FolderTileContent(tile)
        }
    }
}

@Composable
private fun AppTileContent(tile: TileModel.App) {
    if (tile.size == TileSize.SMALL) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = TileIcons[tile.iconKey],
                contentDescription = tile.label,
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(11.dp)) {
            Icon(
                imageVector = TileIcons[tile.iconKey],
                contentDescription = tile.label,
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.weight(1f))
            TileLabel(tile.label.orEmpty())
        }
    }
}

@Composable
private fun FolderTileContent(tile: TileModel.Folder) {
    Column(modifier = Modifier.fillMaxSize().padding(9.dp)) {
        // 2×2 mini-grid of the first four child glyphs (folder face — refined in S16).
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            for (rowIndex in 0 until 2) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    for (colIndex in 0 until 2) {
                        val child = tile.children.getOrNull(rowIndex * 2 + colIndex)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(2.dp)
                                .background(Color(0x2E000000)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (child != null) {
                                Icon(
                                    imageVector = TileIcons[child.iconKey],
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        TileLabel(tile.name)
    }
}

@Composable
private fun TileLabel(text: String) {
    Text(
        text = text.lowercase(),
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
    )
}

private fun onTileClick(context: Context, tile: TileModel) {
    when (tile) {
        is TileModel.App ->
            if (!launchApp(context, tile.packageName, tile.activityName)) {
                Toast.makeText(
                    context,
                    "couldn't open ${tile.label ?: "app"}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        // Folder overlay arrives in S16; tapping is inert for now.
        is TileModel.Folder -> Unit
    }
}

/** Launch an app's main activity via LauncherApps; false if it can't be started. */
private fun launchApp(context: Context, packageName: String, activityName: String): Boolean = try {
    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    launcherApps.startMainActivity(
        ComponentName(packageName, activityName),
        Process.myUserHandle(),
        null,
        null,
    )
    true
} catch (e: Exception) {
    false
}
