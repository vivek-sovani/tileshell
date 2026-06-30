package com.tileshell.feature.personalize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.BackupManager
import com.tileshell.core.data.LayoutSnapshot
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.db.TileEntity
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LayoutHistorySheet(
    visible: Boolean,
    snapshots: List<LayoutSnapshot>,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    onRestore: (LayoutSnapshot) -> Unit,
    onDelete: (String) -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "historySheetProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)

    BackHandler(enabled = visible) { onDismiss() }

    var restoreTarget by remember { mutableStateOf<LayoutSnapshot?>(null) }

    restoreTarget?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("restore this layout?") },
            text = { Text("this will replace your current start screen layout and settings.") },
            confirmButton = {
                TextButton(onClick = {
                    onRestore(snapshot)
                    restoreTarget = null
                    onDismiss()
                }) { Text("restore") }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) { Text("cancel") }
            },
        )
    }

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .graphicsLayer { translationY = size.height * (1f - progress) }
                    .background(tokens.sheet, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(bottom = 24.dp),
            ) {
                // drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tokens.fgDim.copy(alpha = 0.4f)),
                )

                Text(
                    text = "layout history",
                    color = tokens.fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )

                if (snapshots.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "no snapshots yet",
                            color = tokens.fgDim,
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        snapshots.forEach { snapshot ->
                            SnapshotRow(
                                snapshot = snapshot,
                                dark = dark,
                                accent = accent,
                                tokens = tokens,
                                onRestore = { restoreTarget = snapshot },
                                onDelete = { onDelete(snapshot.id) },
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotRow(
    snapshot: LayoutSnapshot,
    dark: Boolean,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val tiles = remember(snapshot.id) {
        runCatching { BackupManager.parseBackup(snapshot.json).tiles }
            .getOrDefault(emptyList())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // mini grid preview
        MiniGridPreview(
            tiles = tiles,
            accent = accent,
            dark = dark,
            modifier = Modifier
                .size(width = 72.dp, height = 72.dp)
                .clip(RoundedCornerShape(6.dp)),
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTimestamp(snapshot.timestamp),
                    color = tokens.fg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(8.dp))
                // label chip
                Text(
                    text = snapshot.label,
                    color = if (snapshot.label == "manual") accent else tokens.fgDim,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(
                            if (snapshot.label == "manual") accent.copy(alpha = 0.15f)
                            else tokens.fgDim.copy(alpha = 0.12f),
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            Text(
                text = "${snapshot.tileCount} tiles · ${snapshot.folderCount} folders",
                color = tokens.fgDim,
                fontSize = 12.sp,
            )
        }

        // delete button
        Text(
            text = "delete",
            color = tokens.fgDim,
            fontSize = 13.sp,
            modifier = Modifier
                .clickable(onClick = onDelete)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        )

        Spacer(Modifier.width(4.dp))

        // restore button
        Text(
            text = "restore",
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clickable(onClick = onRestore)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        )
    }
}

/**
 * Renders a compact visual grid preview of the given tiles. Each tile is a
 * coloured rectangle placed in a 4-column grid using a simple left-to-right,
 * top-to-bottom bin-packer (approximates the real DenseTileGrid layout).
 */
@Composable
private fun MiniGridPreview(
    tiles: List<TileEntity>,
    accent: Color,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val bg = if (dark) Color(0xFF0A0A0D) else Color(0xFFECE9E4)
    val positions = remember(tiles) { packTiles(tiles, columns = 4) }

    Canvas(modifier = modifier.background(bg)) {
        val columns = 4
        val cellW = size.width / columns
        val cellH = cellW  // square cells in the preview

        positions.forEach { (tileId, pos) ->
            val tile = tiles.find { it.id == tileId } ?: return@forEach
            val (col, row) = pos
            val tileW = tile.size.cols.coerceAtMost(columns - col)
            val tileH = tile.size.rows

            val tileColor = when {
                tile.accentOverride != null -> try {
                    Color(android.graphics.Color.parseColor(tile.accentOverride))
                } catch (_: Exception) { accent }
                else -> TileAccents.forId(tile.colorId).let {
                    if (it == TileAccents.Blue && tile.colorId != "blue") accent else it
                }
            }

            val gap = 1.5f
            drawRect(
                color = tileColor,
                topLeft = Offset(col * cellW + gap, row * cellH + gap),
                size = Size(tileW * cellW - gap * 2, tileH * cellH - gap * 2),
            )
        }
    }
}

/** Simple 2D bin-packer: places tiles left-to-right, top-to-bottom. */
private fun packTiles(tiles: List<TileEntity>, columns: Int): Map<String, Pair<Int, Int>> {
    val occupied = mutableSetOf<Pair<Int, Int>>()
    val positions = mutableMapOf<String, Pair<Int, Int>>()
    var searchRow = 0

    tiles.sortedBy { it.position }.forEach { tile ->
        val w = tile.size.cols.coerceAtMost(columns)
        val h = tile.size.rows
        var row = searchRow
        outer@ while (row < searchRow + 20) {
            for (col in 0..columns - w) {
                if ((0 until h).all { dr -> (0 until w).all { dc -> !occupied.contains(col + dc to row + dr) } }) {
                    positions[tile.id] = col to row
                    for (dr in 0 until h) for (dc in 0 until w) occupied.add(col + dc to row + dr)
                    break@outer
                }
            }
            row++
        }
    }
    return positions
}

private val TIMESTAMP_FMT = SimpleDateFormat("d MMM · h:mm a", Locale.getDefault())

private fun formatTimestamp(epochMs: Long): String =
    TIMESTAMP_FMT.format(Date(epochMs)).lowercase(Locale.getDefault())
