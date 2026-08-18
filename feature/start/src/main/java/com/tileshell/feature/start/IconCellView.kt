package com.tileshell.feature.start

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileModel
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens
import com.tileshell.feature.livetiles.NotificationSnapshot

/**
 * The ICONS-home-style renderer for a 1×1 cell (`LauncherSettings.homeStyle
 * == HomeStyle.ICONS`, size == SMALL): the app's own icon, masked to
 * [IconShape] once Stage 4 lands (plain/unmasked for now), with a lowercase
 * label beneath — no tile fill, no chrome, no live face. A sibling of
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
            IconCellGlyph(tile = tile, tint = tokens.fg)
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

/** The app's own icon (masked once Stage 4 lands), or the monoline glyph fallback. */
@Composable
private fun IconCellGlyph(tile: TileModel.App, tint: Color) {
    val useAppIcon = !TileIcons.hasIcon(tile.iconKey)
    val appIcon: ImageBitmap? = if (useAppIcon) rememberTileAppIcon(tile.packageName, tile.activityName) else null
    if (useAppIcon && appIcon != null) {
        Image(
            bitmap = appIcon,
            contentDescription = tile.label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(40.dp),
        )
    } else {
        Icon(
            imageVector = TileIcons[tile.iconKey],
            contentDescription = tile.label,
            tint = tint,
            modifier = Modifier.size(32.dp),
        )
    }
}
