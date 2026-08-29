package com.tileshell.feature.start

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.settings.LauncherSettings
import com.tileshell.core.data.settings.TileColorSource
import com.tileshell.core.data.settings.TilePackMode
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.WallpaperGradient
import com.tileshell.core.design.colorTokens
import com.tileshell.feature.livetiles.NotificationSnapshot

/**
 * A live, non-interactive preview of the REAL Start screen — real tiles via
 * [TileView] (real icons, live-face content, per-tile accents, folders, widget
 * stacks — the exact same renderer Start itself uses), composited over
 * [wallpaperImage] cropped exactly the way the real wallpaper will be
 * ([alignX]/[alignY]/[zoom]) — shown inside [WallpaperCropOverlay] the way
 * OneUI / most Android launchers preview "your actual home screen on this
 * wallpaper" rather than just the bare photo — fills the whole overlay
 * exactly like the real screen would (no phone-frame chrome), so what you see
 * here is what Start will actually look like once "use this" is confirmed.
 * Every [TileView] call passes `readOnly = true`, which strips every
 * touch-consuming gesture the tile would normally attach (press-tilt,
 * tap/long-press, music transport controls, a widget stack's swipe-to-flip)
 * — see [TileView]'s own doc on that param — so this whole layer is purely
 * visual and never competes with the crop overlay's own pinch/drag gesture
 * underneath it. For the same reason this doesn't scroll even if the layout
 * is taller than one screen (a scrollable layer here would fight that same
 * drag-to-reposition gesture) — it clips instead, matching what's actually
 * visible on Start without scrolling.
 *
 * Deliberately simplified in two ways versus the real Start screen, to keep
 * this cheap enough to redraw every drag/pinch frame: live faces render
 * frozen ([liveActive] = false, no flip/animation) and app-icon-derived tile
 * colour skips the per-tile dominant-colour extraction (falls back to the
 * plain resolved accent) — both are visually inert or a close approximation,
 * never a functional gap.
 */
@Composable
fun WallpaperStartPreview(
    tiles: List<TileModel>,
    settings: LauncherSettings,
    accent: Color,
    wallpaper: WallpaperGradient,
    wallpaperAccent: Color?,
    notifications: NotificationSnapshot,
    darkTheme: Boolean,
    glassLine: Color,
    wallpaperImage: ImageBitmap,
    alignX: Float,
    alignY: Float,
    zoom: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier.clipToBounds(),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        if (widthPx <= 0f || heightPx <= 0f) return@BoxWithConstraints

        if (settings.tiledWallpaper) {
            // Matches the real Start screen's own "wallpaper behind tiles" branch
            // exactly (StartScreen.kt): the screen itself goes flat dark, and each
            // tile below draws its own window into the photo (tiledWallpaper=true
            // is threaded into every TileView call). Drawing the full photo here
            // too — like the non-tiled branch does — would show it bleeding through
            // every gap between tiles instead of the real dark grid-separator look.
            Box(modifier = Modifier.fillMaxSize().background(colorTokens(darkTheme).bg))
        } else {
            // Full-bleed background photo, cropped with the exact same geometry as
            // the crop overlay's own gesture layer and the real WallpaperBackground —
            // what shows through a glass/translucent tile, or behind a folder's own
            // mini-grid gaps, either way.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val geo = wallpaperCropGeometry(
                            wallpaperImage.width.toFloat(), wallpaperImage.height.toFloat(), size.width, size.height, alignX, alignY, zoom,
                        )
                        onDrawBehind {
                            clipRect {
                                translate(left = geo.left, top = geo.top) {
                                    scale(geo.scale, pivot = Offset.Zero) {
                                        drawImage(wallpaperImage)
                                    }
                                }
                            }
                        }
                    },
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val specs = remember(tiles) { tiles.map { TileSpec(it.id, it.size) } }
            val byId = remember(tiles) { tiles.associateBy { it.id } }
            val slots = remember(tiles) { tiles.associate { it.id to it.gridSlot } }
            val slotOf = remember(slots) { { id: String -> slots[id] } }

            DenseTileGrid(
                tiles = specs,
                columns = settings.columns,
                gapPx = widthPx * (settings.tileGap / 393f),
                slotOf = if (settings.tilePackMode == TilePackMode.STICKY) slotOf else null,
                modifier = Modifier.fillMaxWidth(),
            ) { spec, slot, sizePx ->
                val model = byId[spec.id] ?: return@DenseTileGrid
                val tileOverrideId = when (model) {
                    is TileModel.App -> model.accentOverride
                    is TileModel.Folder -> model.accentOverride
                }
                val tileAccent = when {
                    tileOverrideId != null -> TileAccents.colorForOverride(tileOverrideId, settings.accentId)
                    wallpaperAccent != null -> wallpaperAccent
                    else -> accent
                }
                val badgeCount = when (model) {
                    is TileModel.App -> notifications.badgeFor(model.packageName)
                    is TileModel.Folder -> model.children.map { it.packageName }.distinct().sumOf { notifications.badgeFor(it) }
                }
                Box(
                    modifier = Modifier
                        .offset { slot }
                        .size(with(density) { sizePx.width.toDp() }, with(density) { sizePx.height.toDp() }),
                ) {
                    TileView(
                        tile = model,
                        index = 0,
                        editMode = false,
                        selected = false,
                        dragging = false,
                        mergeTarget = false,
                        homeStyle = settings.homeStyle,
                        accent = tileAccent,
                        glass = settings.glass,
                        transparency = settings.transparency,
                        glassLine = glassLine,
                        tiledWallpaper = settings.tiledWallpaper,
                        wallpaper = wallpaper,
                        wallpaperPhoto = wallpaperImage,
                        wallpaperAlignX = alignX,
                        wallpaperAlignY = alignY,
                        wallpaperZoom = zoom,
                        // No scroll offset to subtract — this preview never scrolls
                        // (see the doc comment above), so a tile's on-screen origin
                        // is just its packed slot.
                        wallpaperOrigin = {
                            Offset(slot.x.toFloat(), slot.y.toFloat())
                        },
                        fullWidth = widthPx,
                        fullHeight = heightPx,
                        jigglePhase = 0f,
                        flipped = false,
                        liveActive = false,
                        notifications = notifications,
                        badgeCount = badgeCount,
                        darkTheme = darkTheme,
                        canMoveBack = false,
                        canMoveForward = false,
                        inlineFolderLaunch = false,
                        appIconColors = settings.tileColorSource == TileColorSource.APP_ICON,
                        wallpaperAccent = wallpaperAccent,
                        readOnly = true,
                        onTap = {},
                        onLongPress = {},
                        onResize = {},
                        onUnpin = {},
                        onSelect = {},
                        onExitEdit = {},
                        onMove = {},
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
