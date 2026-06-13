package com.tileshell.feature.start

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tileshell.core.data.AppLauncher
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.TileSize
import com.tileshell.feature.applist.AppListScreen
import com.tileshell.core.design.DarkColorTokens
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.Wallpapers
import com.tileshell.core.design.tiltOnPress
import com.tileshell.core.design.wallpaperBackground
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The real Start screen and its App-list page, joined by the finger-following
 * pager (FR-6). Start renders persisted tiles packed by the dense packer as
 * monoline glyphs on accent fills with lowercase lower-left labels (icon only
 * on small), over the aurora wallpaper. Swiping left brings in the App-list
 * page (placeholder until S9): Start parallaxes −22% and fades to 0.4, the
 * page commits past 50%, otherwise springs back. Tapping an app tile launches
 * it via LauncherApps. Insets follow FR-1.
 */
@Composable
fun StartScreen(
    modifier: Modifier = Modifier,
    viewModel: StartViewModel = viewModel(),
) {
    val tiles by viewModel.tiles.collectAsStateWithLifecycle()
    val swipeEnabled by viewModel.swipeEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val specs = remember(tiles) { tiles.map { TileSpec(it.id, it.size) } }
    val byId = remember(tiles) { tiles.associateBy { it.id } }

    val scrollState = rememberScrollState()
    // 0 = Start, 1 = App list.
    val progress = remember { Animatable(0f) }
    val settleSpec = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    fun settleTo(target: Float) {
        scope.launch {
            progress.animateTo(target, settleSpec)
            viewModel.setAppList(target >= 0.5f)
        }
    }

    // Home press collapses to Start and scrolls the grid to the top.
    LaunchedEffect(Unit) {
        viewModel.homeRequests.collect {
            viewModel.setAppList(false)
            scope.launch { progress.animateTo(0f, settleSpec) }
            scrollState.animateScrollTo(0)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .wallpaperBackground(Wallpapers.Aurora),
    ) {
        val widthPx = constraints.maxWidth.toFloat()

        // Horizontal pager gesture. Detection runs in the Initial pass so a
        // dominant horizontal drag is claimed before the vertical grid scroll
        // (a child) can consume it; vertical drags pass straight through.
        val pager = Modifier.pointerInput(swipeEnabled, widthPx) {
            if (!swipeEnabled) return@pointerInput
            val slop = 12.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val base = progress.value
                var horizontal = false
                var decided = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    if (!decided) {
                        if (abs(dx) > slop && abs(dx) > abs(dy) * 1.2f) {
                            decided = true
                            horizontal = true
                        } else if (abs(dy) > slop) {
                            decided = true // vertical → leave it to the grid scroll
                        }
                    }
                    if (horizontal) {
                        change.consume()
                        val target = (base - dx / widthPx).coerceIn(0f, 1f)
                        scope.launch { progress.snapTo(target) }
                    }
                    if (!change.pressed) break
                }
                if (horizontal) settleTo(if (progress.value >= 0.5f) 1f else 0f)
            }
        }

        Box(modifier = Modifier.fillMaxSize().then(pager)) {
            // Start page: parallaxes left and fades as the app list comes in.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -0.22f * widthPx * progress.value
                        alpha = 1f - 0.6f * progress.value
                    },
            ) {
                StartPage(
                    specs = specs,
                    byId = byId,
                    scrollState = scrollState,
                    chevronVisible = swipeEnabled,
                    onTile = { onTileClick(context, it) },
                    onChevron = { settleTo(1f) },
                )
            }

            // App-list page: slides in from the right.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = widthPx * (1f - progress.value) }
                    .background(DarkColorTokens.bg),
            ) {
                AppListScreen(
                    modifier = Modifier.fillMaxSize(),
                    onPinned = { settleTo(0f) },
                )
            }
        }
    }
}

@Composable
private fun StartPage(
    specs: List<TileSpec>,
    byId: Map<String, TileModel>,
    scrollState: androidx.compose.foundation.ScrollState,
    chevronVisible: Boolean,
    onTile: (TileModel) -> Unit,
    onChevron: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            DenseTileGrid(tiles = specs, modifier = Modifier.fillMaxWidth()) { spec ->
                byId[spec.id]?.let { model ->
                    TileView(model) { onTile(model) }
                }
            }
            // FR-1 bottom breathing room (prototype home-scroll padding-bottom:74px).
            Spacer(Modifier.height(74.dp))
        }

        // App-list affordance (prototype .allapps-btn): hidden in edit mode.
        if (chevronVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 14.dp, bottom = 26.dp)
                    .size(40.dp)
                    .clickable(onClick = onChevron),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TileIcons["chevron"],
                    contentDescription = "open app list",
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(28.dp),
                )
            }
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
            if (!AppLauncher.launch(context, tile.packageName, tile.activityName)) {
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
