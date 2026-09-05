package com.tileshell.feature.start

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.tileshell.core.data.CommodityTile
import com.tileshell.core.data.CountdownTile
import com.tileshell.core.data.StockTile
import com.tileshell.core.data.FolderChild
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.settings.IconShape
import com.tileshell.core.data.settings.LiveRefreshRate
import com.tileshell.core.design.Glass
import com.tileshell.core.design.SquircleShape
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens
import com.tileshell.core.design.isLightBackground
import com.tileshell.feature.livetiles.BatterySmallFace
import com.tileshell.feature.livetiles.CalendarSmallFace
import com.tileshell.feature.livetiles.CalendarSystemSmallFace
import com.tileshell.feature.livetiles.ClockSmallFace
import com.tileshell.feature.livetiles.CountdownSmallFace
import com.tileshell.feature.livetiles.FlashlightSmallFace
import com.tileshell.feature.livetiles.NotificationSnapshot
import com.tileshell.feature.livetiles.StepsSmallFace
import com.tileshell.feature.livetiles.StockSmallFace
import com.tileshell.feature.livetiles.CommoditySmallFace
import com.tileshell.feature.livetiles.WeatherSmallFace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Corner radius for the accent-filled "mini tile" [IconCellView] renders for
 *  weather/calendar/clock — a fixed, modest rounding, not tied to the tile-mode
 *  corner-radius setting, since this is the one place ICONS mode borrows tile
 *  mode's filled-square look rather than a masked icon. */
private val LIVE_ICON_CORNER_RADIUS = 8.dp

/**
 * The ICONS-home-style renderer for a 1×1 cell (`LauncherSettings.homeStyle
 * == HomeStyle.ICONS`, size == SMALL): the app's own icon, masked to
 * [iconShape] (see [maskedOrGlyphIcon]'s doc comment for the adaptive-vs-legacy
 * split), with a lowercase label beneath — no tile fill, no chrome. A sibling
 * of [TileView], not a variant of it: everything about layout, persistence,
 * drag/drop, folders and the app drawer is shared with tile mode, and this
 * composable only ever exists at SMALL, so it doesn't need TileView's
 * fill/glass/wallpaper machinery at all.
 *
 * Three iconKeys are the exception to "no live face, no fill": weather/
 * calendar/clock stay live even at 1×1 (user-requested — a real Android
 * launcher's dynamic calendar/weather icons are the precedent, and the user
 * asked for these to render "just like tile mode"), so instead of the
 * icon+label layout every other app gets, these three fill the whole cell
 * with an [accent]-coloured mini tile (rounded to [LIVE_ICON_CORNER_RADIUS])
 * holding the exact same [WeatherSmallFace]/[CalendarSmallFace]/
 * [ClockSmallFace] tile mode's own SMALL tile uses — same content, same font
 * sizes, no separate label underneath (there isn't one in tile mode either).
 *
 * [resizeHandlesEnabled]/[onResizeDragStart]/[onResizeDragBy]/[onResizeDragEnd]
 * mirror the same gesture-based drag resize [TileView] exposes — growing an
 * icon past SMALL is exactly how it turns into a live tile (see
 * `LauncherSettings.HomeStyle`'s doc comment).
 */
@Composable
internal fun IconCellView(
    tile: TileModel.App,
    editMode: Boolean,
    selected: Boolean,
    dragging: Boolean,
    index: Int,
    jigglePhase: Float,
    darkTheme: Boolean,
    columns: Int,
    badgeCount: Int,
    notifications: NotificationSnapshot,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSelect: () -> Unit,
    onExitEdit: () -> Unit,
    onUnpin: () -> Unit,
    onMove: (direction: Int) -> Unit,
    canMoveBack: Boolean,
    canMoveForward: Boolean,
    iconShape: IconShape = IconShape.ORIGINAL,
    themedIcons: Boolean = false,
    stockRefreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    commodityRefreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    accent: Color = Color.Gray,
    liveActive: Boolean = false,
    resizeHandlesEnabled: Boolean = false,
    onResizeDragStart: () -> Unit = {},
    onResizeDragBy: (dxPx: Float, dyPx: Float) -> Unit = { _, _ -> },
    onResizeDragEnd: () -> Unit = {},
    // Shows the corner colour dot — the only affordance that opens the tile
    // colour picker sheet, which is also where the "show as icon"/"show as
    // tile" toggle lives (StartScreen.kt's TileColorPicker). Previously always
    // false here ("icon colour comes from the app's own icon"), which was
    // correct while this composable only ever rendered a SMALL cell with no
    // toggle to reach — now that a single app tile can also render here at
    // MEDIUM+ (via displayAsIcon), the dot needs to be reachable so that
    // toggle has an entry point; the caller decides when that applies.
    showColorDot: Boolean = false,
) {
    val tokens = colorTokens(darkTheme)
    IconCellChrome(
        a11yLabel = tileAccessibilityLabel(tile, badgeCount, editMode, selected),
        editMode = editMode,
        selected = selected,
        dragging = dragging,
        index = index,
        jigglePhase = jigglePhase,
        // Badge is drawn on the icon's own corner below instead of the whole
        // cell's corner — the two only coincide when the icon fills the cell
        // edge-to-edge (the weather/calendar/clock live-face branches); once
        // a real app icon is centred with room to spare (a stretched
        // displayAsIcon tile, or a label underneath), a cell-corner badge
        // reads as floating away from the icon it's meant to belong to
        // (user-reported). So the chrome's own generic badge is always off
        // here — see each branch below for where it actually renders.
        badgeCount = 0,
        darkTheme = darkTheme,
        onTap = onTap,
        onLongPress = onLongPress,
        onSelect = onSelect,
        onExitEdit = onExitEdit,
        onUnpin = onUnpin,
        onMove = onMove,
        canMoveBack = canMoveBack,
        canMoveForward = canMoveForward,
        resizeHandlesEnabled = resizeHandlesEnabled,
        onResizeDragStart = onResizeDragStart,
        onResizeDragBy = onResizeDragBy,
        onResizeDragEnd = onResizeDragEnd,
        showColorDot = showColorDot,
    ) {
        when (tile.iconKey) {
            "weather" -> LiveIconTile(accent, badgeCount, darkTheme) {
                WeatherSmallFace(
                    fallback = {
                        IconCellGlyph(tile = tile, tint = tokens.fg, shape = iconShape, size = 40.dp, glyphSize = 32.dp, themedIcons = themedIcons, accent = accent)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            "calendar" -> LiveIconTile(accent, badgeCount, darkTheme) {
                CalendarSmallFace(active = liveActive, modifier = Modifier.fillMaxSize())
            }
            "clock" -> LiveIconTile(accent, badgeCount, darkTheme) {
                ClockSmallFace(active = liveActive, modifier = Modifier.fillMaxSize())
            }
            "battery" -> LiveIconTile(accent, badgeCount, darkTheme) {
                BatterySmallFace(modifier = Modifier.fillMaxSize())
            }
            "flashlight" -> LiveIconTile(accent, badgeCount, darkTheme) {
                FlashlightSmallFace(interactive = !editMode, modifier = Modifier.fillMaxSize())
            }
            "countdown" -> LiveIconTile(accent, badgeCount, darkTheme) {
                val (isoDate, _) = CountdownTile.decode(tile.activityName) ?: ("" to "")
                CountdownSmallFace(targetIsoDate = isoDate, modifier = Modifier.fillMaxSize())
            }
            "steps" -> LiveIconTile(accent, badgeCount, darkTheme) {
                StepsSmallFace(
                    fallback = { IconCellGlyph(tile = tile, tint = tokens.fg, shape = iconShape, size = 40.dp, glyphSize = 32.dp, themedIcons = themedIcons, accent = accent) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            "stock" -> LiveIconTile(accent, badgeCount, darkTheme) {
                StockSmallFace(
                    selection = StockTile.decode(tile.activityName),
                    fallback = { IconCellGlyph(tile = tile, tint = tokens.fg, shape = iconShape, size = 40.dp, glyphSize = 32.dp, themedIcons = themedIcons, accent = accent) },
                    active = liveActive,
                    refreshRate = stockRefreshRate,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            "commodity" -> LiveIconTile(accent, badgeCount, darkTheme) {
                CommoditySmallFace(
                    symbol = CommodityTile.decode(tile.activityName)?.first,
                    fallback = { IconCellGlyph(tile = tile, tint = tokens.fg, shape = iconShape, size = 40.dp, glyphSize = 32.dp, themedIcons = themedIcons, accent = accent) },
                    active = liveActive,
                    refreshRate = commodityRefreshRate,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            "calsys" -> LiveIconTile(accent, badgeCount, darkTheme) {
                CalendarSystemSmallFace(modifier = Modifier.fillMaxSize())
            }
            else -> {
                // Hide the label at 6 columns — a 1×1 cell is too narrow there
                // for icon plus text without truncating (see
                // LauncherSettings.HomeStyle's design notes / DECISIONS.md
                // "cells stay square").
                val showLabel = columns < 6
                BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // The icon fills as much of the actual cell as it can —
                    // computed from the real measured size handed down by the
                    // grid (which already varies by TileSize, column count and
                    // screen width) rather than a fixed dp table, so a
                    // stretched icon (MEDIUM/LARGE/XLARGE via displayAsIcon)
                    // reads as genuinely bigger, not a small glyph floating in
                    // a big cell (user-reported).
                    val reserveForLabel = if (showLabel) 20.dp else 0.dp
                    val span = minOf(maxWidth, (maxHeight - reserveForLabel).coerceAtLeast(0.dp))
                    val size = (span * 0.82f).coerceAtLeast(24.dp)
                    val glyphSize = size * 0.72f
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Badged on the icon's own box, not the whole cell —
                        // see the badgeCount = 0 comment on the IconCellChrome
                        // call above for why (user-reported).
                        Box(modifier = Modifier.size(size)) {
                            IconCellGlyph(tile = tile, tint = tokens.fg, shape = iconShape, size = size, glyphSize = glyphSize, themedIcons = themedIcons, accent = accent)
                            if (badgeCount > 0) {
                                // Scales with the icon itself (18dp at a SMALL
                                // 40dp icon, up to a cap at the biggest
                                // stretched sizes) rather than a fixed size
                                // that reads as too small on a big icon
                                // (user-reported: "should be proportionate").
                                val badgeDiameter = (size * 0.28f).coerceIn(18.dp, 34.dp)
                                // Sits right on the icon's corner, roughly a
                                // third overlapping outside its bounds — the
                                // usual "badge on the corner" look — rather
                                // than inset inward, which read as floating
                                // inside the icon at small sizes especially
                                // (user-reported).
                                NotificationBadge(
                                    count = badgeCount,
                                    dark = darkTheme,
                                    small = true,
                                    sizeOverride = badgeDiameter,
                                    cornerInset = false,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = badgeDiameter * 0.35f, y = -badgeDiameter * 0.35f),
                                )
                            }
                        }
                        if (showLabel) {
                            Spacer(Modifier.size(4.dp))
                            Text(
                                text = (tile.label ?: tile.iconKey ?: "").lowercase(),
                                color = tokens.fg,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** An [accent]-filled, rounded-square "mini tile" occupying the whole icon
 *  cell — see [IconCellView]'s doc comment on why weather/calendar/clock
 *  render this way instead of a masked icon. Fills the entire cell edge-to-
 *  edge, so a corner badge here is already at the "icon's" own corner (there
 *  being no separate icon vs. cell distinction for these three). */
@Composable
private fun LiveIconTile(accent: Color, badgeCount: Int, darkTheme: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(LIVE_ICON_CORNER_RADIUS))
            .background(accent),
    ) {
        content()
        if (badgeCount > 0) {
            NotificationBadge(
                count = badgeCount,
                dark = darkTheme,
                small = true,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

/**
 * The ICONS-home-style renderer for a **closed folder** at SMALL: the same
 * shaped-icon treatment as [IconCellView], but the glyph area is a 2×2
 * mini-grid of up to four children's icons (each masked to [iconShape] too)
 * instead of one app icon — mirrors [FolderTileContent]'s mini-grid at icon
 * scale, minus the accent fill. A folder at MEDIUM+ still renders via
 * [FolderTileContent], and a widget stack (whose own `size` is always WIDE
 * or LARGE, never SMALL — see `TileModel.Folder.stackSize`) never reaches
 * this composable at all, so no explicit stack guard is needed here.
 *
 * Every callback and the outer chrome is identical to [IconCellView]'s — a
 * SMALL folder still taps to expand inline (`onTap` already branches on tile
 * type at the call site) and long-presses to select for edit exactly like an
 * app icon does, so both share [IconCellChrome].
 */
@Composable
internal fun IconFolderCell(
    tile: TileModel.Folder,
    editMode: Boolean,
    selected: Boolean,
    dragging: Boolean,
    index: Int,
    jigglePhase: Float,
    darkTheme: Boolean,
    columns: Int,
    badgeCount: Int,
    notifications: NotificationSnapshot,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSelect: () -> Unit,
    onExitEdit: () -> Unit,
    onUnpin: () -> Unit,
    onMove: (direction: Int) -> Unit,
    canMoveBack: Boolean,
    canMoveForward: Boolean,
    iconShape: IconShape = IconShape.ORIGINAL,
    resizeHandlesEnabled: Boolean = false,
    onResizeDragStart: () -> Unit = {},
    onResizeDragBy: (dxPx: Float, dyPx: Float) -> Unit = { _, _ -> },
    onResizeDragEnd: () -> Unit = {},
) {
    val tokens = colorTokens(darkTheme)
    IconCellChrome(
        a11yLabel = tileAccessibilityLabel(tile, badgeCount, editMode, selected),
        editMode = editMode,
        selected = selected,
        dragging = dragging,
        index = index,
        jigglePhase = jigglePhase,
        badgeCount = badgeCount,
        darkTheme = darkTheme,
        onTap = onTap,
        onLongPress = onLongPress,
        onSelect = onSelect,
        onExitEdit = onExitEdit,
        onUnpin = onUnpin,
        onMove = onMove,
        canMoveBack = canMoveBack,
        canMoveForward = canMoveForward,
        resizeHandlesEnabled = resizeHandlesEnabled,
        onResizeDragStart = onResizeDragStart,
        onResizeDragBy = onResizeDragBy,
        onResizeDragEnd = onResizeDragEnd,
    ) {
        Box(modifier = Modifier.size(40.dp)) {
            val cellSize = 18.dp
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (rowIndex in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (colIndex in 0 until 2) {
                            val child = tile.children.getOrNull(rowIndex * 2 + colIndex)
                            Box(modifier = Modifier.size(cellSize)) {
                                if (child != null) {
                                    IconFolderChildGlyph(child = child, tint = tokens.fg, size = cellSize)
                                    val childBadge = notifications.badgeFor(child.packageName)
                                    if (childBadge > 0) {
                                        FolderChildBadge(
                                            count = childBadge,
                                            dark = darkTheme,
                                            modifier = Modifier.align(Alignment.TopEnd),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (columns < 6) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = tile.name.lowercase(),
                color = tokens.fg,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

/**
 * The chrome shared by every ICONS-mode cell (currently [IconCellView] and
 * [IconFolderCell]): edit-mode dim/scale/jiggle, tap/long-press gesture,
 * TalkBack semantics (mirrors [TileView]'s own block so screen-reader
 * behaviour doesn't diverge between tile mode and icons mode), the
 * notification badge, and the selected-tile corner control + resize handles.
 * [content] draws only the glyph + label — everything position/gesture/
 * accessibility-related lives here once instead of per cell type.
 */
@Composable
private fun IconCellChrome(
    a11yLabel: String,
    editMode: Boolean,
    selected: Boolean,
    dragging: Boolean,
    index: Int,
    jigglePhase: Float,
    badgeCount: Int,
    darkTheme: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSelect: () -> Unit,
    onExitEdit: () -> Unit,
    onUnpin: () -> Unit,
    onMove: (direction: Int) -> Unit,
    canMoveBack: Boolean,
    canMoveForward: Boolean,
    resizeHandlesEnabled: Boolean,
    onResizeDragStart: () -> Unit,
    onResizeDragBy: (dxPx: Float, dyPx: Float) -> Unit,
    onResizeDragEnd: () -> Unit,
    showColorDot: Boolean = false,
    content: @Composable () -> Unit,
) {
    val tokens = colorTokens(darkTheme)
    val alpha by animateFloatAsState(
        targetValue = if (editMode && !selected && !dragging) 0.45f else 1f,
        label = "iconCellAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (dragging) 1.08f else if (selected) 1.04f else 1f,
        label = "iconCellScale",
    )
    val rotation = if (editMode && !dragging) (if (index % 2 == 0) jigglePhase else -jigglePhase) else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
            .then(
                if (editMode) Modifier else Modifier.tileGesture(onTap = onTap, onLongPress = onLongPress),
            )
            // Gesture-based resize (drag from the tile's bottom-right corner)
            // — see StartScreen.kt's tileStretchGesture doc comment.
            .then(
                if (selected && editMode && resizeHandlesEnabled) {
                    Modifier.tileStretchGesture(
                        onDragStart = onResizeDragStart,
                        onDragBy = onResizeDragBy,
                        onDragEnd = onResizeDragEnd,
                    )
                } else {
                    Modifier
                },
            )
            .clearAndSetSemantics {
                contentDescription = a11yLabel
                role = Role.Button
                if (editMode) {
                    onClick(label = "select") { onSelect(); true }
                    customActions = buildList {
                        add(CustomAccessibilityAction("unpin") { onUnpin(); true })
                        if (canMoveBack) add(CustomAccessibilityAction("move back") { onMove(-1); true })
                        if (canMoveForward) add(CustomAccessibilityAction("move forward") { onMove(1); true })
                        add(CustomAccessibilityAction("done editing") { onExitEdit(); true })
                    }
                } else {
                    onClick(label = "launch") { onTap(); true }
                    customActions = listOf(CustomAccessibilityAction("customize") { onLongPress(); true })
                }
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            content()
        }
        if (badgeCount > 0) {
            NotificationBadge(
                count = badgeCount,
                dark = darkTheme,
                small = true,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        if (selected && editMode) {
            // The colour dot is the entry point to the tile colour picker
            // sheet — which, for an icon-mode app tile, is also where the
            // "show as icon"/"show as tile" toggle lives (see showColorDot's
            // own doc comment); picking an accent colour itself is still a
            // no-op for a masked real icon. Resize has no visible control at
            // all now — see the tileStretchGesture modifier above.
            TileControls(showColor = showColorDot, dotColor = tokens.fg)
        }
    }
}

/**
 * The result of loading an app's real launcher icon for masking purposes.
 * [bitmap] is exactly what the OS itself renders for this icon (an adaptive
 * icon comes back already clipped to the device's own icon mask — a circle
 * on stock AOSP/Pixel) — used whenever [IconShape.ORIGINAL] is selected, or
 * `HomeStyle.TILES` suppresses masking outright, since "original" means
 * "however the device actually shows it," not "our own unmasked composite."
 * [unmaskedBitmap] is only meaningfully different for an adaptive icon: the
 * background/foreground layers drawn with no OS mask applied, needed so our
 * own [IconShape] clip (squircle/rounded/square) has clean, un-pre-clipped
 * content to work with (see [unmaskedIconBitmap]'s doc comment) — for a
 * legacy (non-adaptive) icon the two fields are identical, since
 * `toBitmap()` never applied any mask for those to begin with.
 */
internal data class MaskableIcon(
    val bitmap: ImageBitmap,
    val unmaskedBitmap: ImageBitmap,
    val isAdaptive: Boolean,
    val monochromeBitmap: ImageBitmap?,
)

/**
 * Loads [packageName]/[activityName]'s launcher icon, tagging whether the
 * source `Drawable` was an [AdaptiveIconDrawable] — minSdk is 26, the same
 * API level `AdaptiveIconDrawable` shipped in, so no version gate is needed.
 * A near-duplicate of [rememberTileAppIcon] (which tile mode's
 * `StaticTileGlyph` still uses unmodified) rather than an extension of it,
 * since tile mode never needs this adaptive/legacy distinction at all — only
 * icon-shape masking does.
 */
@Composable
internal fun rememberMaskableIcon(packageName: String, activityName: String, sizePx: Int = 96): MaskableIcon? {
    val context = LocalContext.current
    return produceState<MaskableIcon?>(null, packageName, activityName, sizePx) {
        value = withContext(Dispatchers.IO) {
            fun load(drawable: android.graphics.drawable.Drawable): MaskableIcon {
                val isAdaptive = drawable is AdaptiveIconDrawable
                val osBitmap = drawable.toBitmap(width = sizePx, height = sizePx).asImageBitmap()
                val rawBitmap = if (isAdaptive) unmaskedIconBitmap(drawable, sizePx) else osBitmap
                return MaskableIcon(osBitmap, rawBitmap, isAdaptive, monochromeIconBitmap(drawable, sizePx))
            }
            runCatching {
                load(context.packageManager.getActivityIcon(ComponentName(packageName, activityName)))
            }.recoverCatching {
                // See rememberTileAppIcon's doc comment: a dead seasonal
                // activity-alias throws on getActivityIcon even though the
                // app itself is installed fine — fall back to its real icon.
                load(context.packageManager.getApplicationIcon(packageName))
            }.getOrNull()
        }
    }.value
}

/**
 * A flattened [sizePx]×[sizePx] bitmap of [drawable], deliberately bypassing
 * [AdaptiveIconDrawable]'s own `draw()` — which always clips to the OS's
 * device-wide icon mask (a circle on stock AOSP/Pixel) before we ever get a
 * chance to apply our own [IconShape]. Calling `drawable.toBitmap()` directly
 * bakes that OS mask into the pixels, so a subsequent clip to a squircle/
 * rounded-rect shape only trims the already-circular content and still reads
 * as a circle — confirmed on-device (an emulator's default circular mask)
 * after this file's original "clip the flattened bitmap" approach shipped.
 * Fixed by drawing the adaptive icon's background/foreground layers
 * ourselves at the full bounds with no mask path applied, matching the
 * standard technique other Android launchers use to re-mask adaptive icons.
 * [sizePx] is the caller's actual on-screen size (not a fixed 96px) — a
 * "show as icon" tile stretched up to 120dp+ needs a source bitmap decoded at
 * (or above) that real resolution, or the masked result reads visibly blurred
 * once scaled up from a small fixed source (user-reported).
 */
private fun unmaskedIconBitmap(drawable: android.graphics.drawable.Drawable, sizePx: Int): ImageBitmap {
    if (drawable !is AdaptiveIconDrawable) return drawable.toBitmap(width = sizePx, height = sizePx).asImageBitmap()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    listOfNotNull(drawable.background, drawable.foreground).forEach { layer ->
        layer.setBounds(0, 0, sizePx, sizePx)
        layer.draw(canvas)
    }
    return bitmap.asImageBitmap()
}

/**
 * See `:feature:applist`'s `AppListIcon.kt#monochromeIconBitmap` for the full
 * rationale — flattens the Android 13+ themed-icon layer to an untinted alpha
 * mask; the caller ([maskedOrGlyphIcon]) tints it via a Compose `ColorFilter`
 * at render time. Null below API 33, for a non-adaptive icon, or when the app
 * declared no monochrome layer.
 */
private fun monochromeIconBitmap(drawable: android.graphics.drawable.Drawable, sizePx: Int): ImageBitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val mono = (drawable as? AdaptiveIconDrawable)?.monochrome ?: return null
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    mono.setBounds(0, 0, sizePx, sizePx)
    mono.draw(canvas)
    return bitmap.asImageBitmap()
}

/**
 * The masked-or-glyph rendering shared by a top-level app icon and a folder
 * mini-grid child: the parent app's own real launcher icon whenever one is
 * resolvable, masked to [shape] via the adaptive-vs-legacy split documented
 * on [IconCellGlyph]; [iconKey]'s monoline glyph only as the fallback for a
 * tile with no real app behind it (a blank-package tile like weather/
 * calendar/personalize, or a real icon that fails to load).
 *
 * This deliberately diverges from tile mode's `StaticTileGlyph`, which shows
 * the stylized WP glyph for any tile whose `iconKey` matches a known
 * category ([TileIcons.hasIcon]) even when a real app icon exists — correct
 * for the WP look, but wrong for ICONS mode's whole premise (a normal
 * Android-style launcher, where every icon is the app's own): a role-matched
 * tile (mail/weather/camera/etc.) used to show the generic category glyph
 * here too instead of the actual installed app's icon.
 */
@Composable
private fun maskedOrGlyphIcon(
    iconKey: String?,
    label: String?,
    packageName: String,
    activityName: String,
    tint: Color,
    shape: IconShape,
    size: Dp,
    glyphSize: Dp,
    // The tile's own resolved accent — only consulted when [themedIcons] is
    // true and the app actually has a monochrome layer to tint; distinct
    // from [tint] (the plain fg/dim glyph colour used for the no-real-icon
    // fallback and the legacy-icon plate default).
    themedIcons: Boolean = false,
    accent: Color = tint,
) {
    val useAppIcon = packageName.isNotBlank()
    // Decode at the actual on-screen resolution, not a fixed 96px — a "show
    // as icon" tile stretched up to 120dp+ needs a source bitmap that large
    // (or bigger) to avoid visibly blurring once scaled up (user-reported).
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(96)
    val loaded: MaskableIcon? = if (useAppIcon) rememberMaskableIcon(packageName, activityName, sizePx) else null
    val composeShape = shape.toComposeShape()
    val mono = loaded?.monochromeBitmap

    when {
        useAppIcon && themedIcons && mono != null -> {
            val plateShape = composeShape ?: CircleShape
            Box(
                modifier = Modifier.size(size).clip(plateShape).background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = mono,
                    contentDescription = label,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(Glass.faceTextColor(isLightBackground(accent))),
                    // Full size, not shrunk further — a monochrome layer already
                    // carries its own adaptive-icon safe-zone inset, matching the
                    // isAdaptive branch below (also drawn at full [size]).
                    modifier = Modifier.size(size),
                )
            }
        }
        !useAppIcon || loaded == null -> {
            Icon(
                imageVector = TileIcons[iconKey],
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(glyphSize),
            )
        }
        composeShape == null -> {
            // ORIGINAL: unmasked, exactly as tile mode's StaticTileGlyph shows it.
            Image(
                bitmap = loaded.bitmap,
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )
        }
        loaded.isAdaptive -> {
            Image(
                bitmap = loaded.unmaskedBitmap,
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size).clip(composeShape),
            )
        }
        else -> {
            val plateColor = dominantIconColor(loaded.bitmap) ?: tint
            Box(
                modifier = Modifier.size(size).clip(composeShape).background(plateColor),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = loaded.bitmap,
                    contentDescription = label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(size * 0.65f),
                )
            }
        }
    }
}

/**
 * The app's own icon, masked to [shape] when it isn't [IconShape.ORIGINAL],
 * or the monoline glyph fallback for an app with no resolvable real icon.
 *
 * Masking only works cleanly on an adaptive icon, whose background layer
 * already fills the whole square by OS convention: clipping the flattened
 * bitmap straight to [shape] reads as a proper mask. A legacy (pre-adaptive)
 * icon has no such guarantee — clipping it directly would slice off
 * whatever content sits in the corners — so it instead sits, unclipped and
 * at a smaller inset scale, on a shaped "plate" tinted from its own dominant
 * colour (falling back to [tint] if that extraction fails). This is a
 * deliberate simplification of manually decomposing and recompositing an
 * `AdaptiveIconDrawable`'s background/foreground layers at the standard
 * 66/108 safe-zone scale, chosen because that finer approach can't be
 * verified without a device attached to this environment, while clip-vs-plate
 * is the same visual outcome real Android launchers show for this exact
 * adaptive/legacy split and is simple enough to trust without on-device
 * verification. Revisit if on-device testing shows the plate reads wrong.
 */
@Composable
private fun IconCellGlyph(
    tile: TileModel.App,
    tint: Color,
    shape: IconShape,
    size: Dp,
    glyphSize: Dp,
    themedIcons: Boolean = false,
    accent: Color = tint,
) {
    maskedOrGlyphIcon(
        iconKey = tile.iconKey,
        label = tile.label,
        packageName = tile.packageName,
        activityName = tile.activityName,
        tint = tint,
        shape = shape,
        size = size,
        glyphSize = glyphSize,
        themedIcons = themedIcons,
        accent = accent,
    )
}

/**
 * A folder mini-grid cell's child icon — always [IconShape.ORIGINAL], never
 * the ambient [IconShape] the top-level icons use. User-reported: at 18dp a
 * masked/plated icon (the colour plate `maskedOrGlyphIcon` draws behind a
 * legacy, non-adaptive icon once a real shape is selected) reads as a
 * cluttered "square border" crammed into a cell already this small — the
 * mini-grid just isn't the size that masking was designed for. Top-level
 * icons are unaffected; this only ever narrows what a *folder's children*
 * show. For the same reason, this never opts into themed icons either — an
 * accent-filled plate at 18dp is exactly the same "square border" clutter
 * this doc comment already describes for real IconShape masking.
 */
@Composable
private fun IconFolderChildGlyph(child: FolderChild, tint: Color, size: Dp) {
    maskedOrGlyphIcon(
        iconKey = child.iconKey,
        label = child.label,
        packageName = child.packageName,
        activityName = child.activityName,
        tint = tint,
        shape = IconShape.ORIGINAL,
        size = size,
        glyphSize = size * 0.7f,
    )
}

/**
 * The [Shape] each [IconShape] masks to, or `null` for [IconShape.ORIGINAL]
 * (the caller skips masking entirely rather than clipping to a rectangle,
 * which would be a no-op that only costs a composition). Lives here rather
 * than in `:core:design` alongside [SquircleShape]: `IconShape` itself is
 * the persisted setting and belongs in `:core:data` next to `HomeStyle`/
 * `TileFill` (see `LauncherSettings.kt`'s doc comment), and `:core:design`
 * has no dependency on `:core:data` (nor vice versa — every existing
 * persisted-style enum follows the same split), so the mapping has to live
 * in a module that depends on both, which `:feature:start` already does.
 *
 * A pure, trivial mapping — safe to unit-test by comparing the returned
 * shapes' *values* (not runtime class: Compose Foundation's `CircleShape`
 * is itself defined as `RoundedCornerShape(50)`, so CIRCLE and ROUNDED are
 * the same class with different corner percentages) — constructing a
 * `Shape` doesn't touch `Path`/`Canvas` at all; only calling `createOutline`
 * on one does, which this project's plain-JVM unit tests can't exercise
 * (see `Squircle.kt`'s doc comment).
 */
internal fun IconShape.toComposeShape(): Shape? = when (this) {
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUIRCLE -> SquircleShape()
    IconShape.ROUNDED -> RoundedCornerShape(percent = 30)
    IconShape.SQUARE -> RectangleShape
    IconShape.ORIGINAL -> null
}
