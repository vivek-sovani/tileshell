package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tileshell.core.data.NoteRepository
import kotlinx.coroutines.flow.map
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
 * The live sticky-note tile: a note pinned to Start. [title] (optional, bold)
 * and [text] come from the linked note (see [rememberNote]). Never flips (see [LiveFace.STICKYNOTE]) and has no on-tile interaction —
 * tapping the tile opens a small dedicated editor instead of typing in place.
 */
@Composable
fun StickyNoteTileFace(size: TileSize, text: String, modifier: Modifier = Modifier, title: String = "") {
    Column(modifier = modifier.fillMaxSize().padding(11.dp)) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                color = FaceText,
                fontSize = if (size == TileSize.LARGE || size == TileSize.XLARGE) 17.sp else 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (text.isBlank() && title.isBlank()) {
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
                maxLines = (maxLinesForStickyNote(size) - if (title.isNotBlank()) 1 else 0).coerceAtLeast(1),
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The live title and text of note [noteId] — what a sticky note tile (a note
 * pinned to Start) shows. Blank for a tile with no note yet, or whose note
 * was deleted.
 */
@Composable
fun rememberNote(noteId: Long?): Pair<String, String> {
    val context = LocalContext.current
    if (noteId == null) return "" to ""
    val flow = remember(noteId) {
        NoteRepository.create(context).notes.map { notes ->
            notes.firstOrNull { it.id == noteId }?.let { it.title to it.text } ?: ("" to "")
        }
    }
    val note by flow.collectAsState(initial = "" to "")
    return note
}
