package com.tileshell.feature.start

import android.content.ComponentName
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.core.graphics.drawable.toBitmap
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
 * [iconShape] (see [IconCellGlyph]'s doc comment for the adaptive-vs-legacy
 * split), with a lowercase label beneath — no tile fill, no chrome, no live
 * face. A sibling of
 * [TileView], not a variant of it: everything about layout, persistence,
 * drag/drop, folders and the app drawer is shared with tile mode, and this
 * composable only ever exists at SMALL, so it doesn't need TileView's
 * fill/glass/wallpaper/live-face machinery at all.
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
    onResizeDragBy: (dxPx: Float, dyPx: Float, axis: ResizeAxis) -> Unit = { _, _, _ -> },
    onResizeDragEnd: () -> Unit = {},
) {
    val a11yLabel = tileAccessibilityLabel(tile, badgeCount, editMode, selected)
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
    // Hide the label at 6 columns — a 1×1 cell is too narrow there for icon
    // plus text without truncating (see LauncherSettings.HomeStyle's design
    // notes / DECISIONS.md "cells stay square").
    val showLabel = columns < 6

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
            IconCellGlyph(tile = tile, tint = tokens.fg, shape = iconShape)
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
            // icon, not a per-tile accent), no resize corner control (the drag
            // handles below are the resize affordance in ICONS mode).
            TileControls(showColor = false, dotColor = tokens.fg, nextSizeIsLarger = true)
            if (resizeHandlesEnabled) {
                TileResizeHandles(
                    onDragStart = onResizeDragStart,
                    onDragBy = onResizeDragBy,
                    onDragEnd = onResizeDragEnd,
                )
            }
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
 * The app's own icon, masked to [shape] (see `IconShape.toComposeShape()`)
 * when it isn't [IconShape.ORIGINAL] — or the monoline glyph fallback for an
 * app with no resolvable real icon.
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
    val useAppIcon = !TileIcons.hasIcon(tile.iconKey)
    val loaded: MaskableIcon? = if (useAppIcon) rememberMaskableIcon(tile.packageName, tile.activityName) else null
    val composeShape = shape.toComposeShape()

    when {
        !useAppIcon || loaded == null -> {
            Icon(
                imageVector = TileIcons[tile.iconKey],
                contentDescription = tile.label,
                tint = tint,
                modifier = Modifier.size(32.dp),
            )
        }
        composeShape == null -> {
            // ORIGINAL: unmasked, exactly as tile mode's StaticTileGlyph shows it.
            Image(
                bitmap = loaded.bitmap,
                contentDescription = tile.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(40.dp),
            )
        }
        loaded.isAdaptive -> {
            Image(
                bitmap = loaded.bitmap,
                contentDescription = tile.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(40.dp).clip(composeShape),
            )
        }
        else -> {
            val plateColor = dominantIconColor(loaded.bitmap) ?: tint
            Box(
                modifier = Modifier.size(40.dp).clip(composeShape).background(plateColor),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = loaded.bitmap,
                    contentDescription = tile.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
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
