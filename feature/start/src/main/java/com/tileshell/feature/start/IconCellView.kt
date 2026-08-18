package com.tileshell.feature.start

import android.content.ComponentName
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.tileshell.core.data.FolderChild
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.settings.IconShape
import com.tileshell.core.design.SquircleShape
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens
import com.tileshell.feature.livetiles.NotificationSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The ICONS-home-style renderer for a 1×1 cell (`LauncherSettings.homeStyle
 * == HomeStyle.ICONS`, size == SMALL): the app's own icon, masked to
 * [iconShape] (see [maskedOrGlyphIcon]'s doc comment for the adaptive-vs-legacy
 * split), with a lowercase label beneath — no tile fill, no chrome, no live
 * face. A sibling of [TileView], not a variant of it: everything about
 * layout, persistence, drag/drop, folders and the app drawer is shared with
 * tile mode, and this composable only ever exists at SMALL, so it doesn't
 * need TileView's fill/glass/wallpaper/live-face machinery at all.
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
        IconCellGlyph(tile = tile, tint = tokens.fg, shape = iconShape)
        // Hide the label at 6 columns — a 1×1 cell is too narrow there for
        // icon plus text without truncating (see LauncherSettings.HomeStyle's
        // design notes / DECISIONS.md "cells stay square").
        if (columns < 6) {
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
                                    IconFolderChildGlyph(child = child, tint = tokens.fg, shape = iconShape, size = cellSize)
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
            // Gesture-based resize (two-finger stretch, or single-finger
            // corner drag as a fallback) — see StartScreen.kt's
            // tileStretchGesture doc comment for the full gesture design.
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
            // Only unpin — no colour dot (icon colour comes from the app's own
            // icon, not a per-tile accent). Resize has no visible control at
            // all now — see the tileStretchGesture modifier above.
            TileControls(showColor = false, dotColor = tokens.fg, nextSizeIsLarger = true)
        }
    }
}

/** The result of loading an app's real launcher icon for masking purposes. */
private data class MaskableIcon(val bitmap: ImageBitmap, val isAdaptive: Boolean)

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
private fun rememberMaskableIcon(packageName: String, activityName: String): MaskableIcon? {
    val context = LocalContext.current
    return produceState<MaskableIcon?>(null, packageName, activityName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getActivityIcon(ComponentName(packageName, activityName))
                MaskableIcon(drawable.toBitmap(width = 96, height = 96).asImageBitmap(), drawable is AdaptiveIconDrawable)
            }.recoverCatching {
                // See rememberTileAppIcon's doc comment: a dead seasonal
                // activity-alias throws on getActivityIcon even though the
                // app itself is installed fine — fall back to its real icon.
                val drawable = context.packageManager.getApplicationIcon(packageName)
                MaskableIcon(drawable.toBitmap(width = 96, height = 96).asImageBitmap(), drawable is AdaptiveIconDrawable)
            }.getOrNull()
        }
    }.value
}

/**
 * The masked-or-glyph rendering shared by a top-level app icon and a folder
 * mini-grid child: [iconKey]'s monoline glyph when there's no resolvable
 * real icon; otherwise the loaded icon at [size], masked to [shape] via the
 * adaptive-vs-legacy split documented on [IconCellGlyph].
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
) {
    val useAppIcon = !TileIcons.hasIcon(iconKey)
    val loaded: MaskableIcon? = if (useAppIcon) rememberMaskableIcon(packageName, activityName) else null
    val composeShape = shape.toComposeShape()

    when {
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
                bitmap = loaded.bitmap,
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
private fun IconCellGlyph(tile: TileModel.App, tint: Color, shape: IconShape) {
    maskedOrGlyphIcon(
        iconKey = tile.iconKey,
        label = tile.label,
        packageName = tile.packageName,
        activityName = tile.activityName,
        tint = tint,
        shape = shape,
        size = 40.dp,
        glyphSize = 32.dp,
    )
}

/** A folder mini-grid cell's masked child icon (see [maskedOrGlyphIcon]). */
@Composable
private fun IconFolderChildGlyph(child: FolderChild, tint: Color, shape: IconShape, size: Dp) {
    maskedOrGlyphIcon(
        iconKey = child.iconKey,
        label = child.label,
        packageName = child.packageName,
        activityName = child.activityName,
        tint = tint,
        shape = shape,
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
    IconShape.ORIGINAL -> null
}
