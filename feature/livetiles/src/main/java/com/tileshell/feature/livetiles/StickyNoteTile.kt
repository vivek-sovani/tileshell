package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.LocalTileFaceColor

/**
 * How many lines of note text a tile of this size has room for, so a bigger
 * tile shows more of the note instead of clipping early. Pure so it's
 * unit-testable. Unlike every other face in this package there's no
 * `narrow`/`short` special case — this tile is one paragraph of free-flowing
 * text, which just wraps to whatever width it's given (a fixed-format value
 * like "6:30 am" clips awkwardly when squeezed; wrapped prose doesn't).
 */
fun maxLinesForStickyNote(size: TileSize): Int = when {
    size.rows >= 4 -> 16
    size.rows == 3 -> 10
    size.rows == 2 -> 6
    else -> 2
}

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

/**
 * The live sticky-note tile: the tile's [text] *is* the note — one note per
 * pinned tile (its text lives directly on the tile's own row, not in a shared
 * table — see `LayoutRepository.setTileText`), so there's no repository/Flow
 * to read here, just the value the caller already has from the tile model.
 * Never flips (see [LiveFace.STICKYNOTE]) and has no on-tile interaction —
 * tapping the tile opens a small dedicated editor instead of typing in place.
 */
@Composable
fun StickyNoteTileFace(size: TileSize, text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(11.dp)) {
        if (text.isBlank()) {
            Text(
                text = "tap to write a note",
                color = FaceText.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
        } else {
            Text(
                text = text,
                color = FaceText,
                fontSize = if (size == TileSize.LARGE || size == TileSize.XLARGE) 15.sp else 13.sp,
                lineHeight = if (size == TileSize.LARGE || size == TileSize.XLARGE) 20.sp else 17.sp,
                maxLines = maxLinesForStickyNote(size),
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
