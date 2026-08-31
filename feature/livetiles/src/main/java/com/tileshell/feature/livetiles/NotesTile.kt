package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.NoteItem
import com.tileshell.core.data.NotePreview
import com.tileshell.core.data.NoteRepository
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.notePreview
import com.tileshell.core.design.LocalTileFaceColor

/** What the tile actually renders — how many notes exist, and a short preview of the most recent ones. */
data class NotesSummary(val count: Int, val preview: List<NotePreview>)

/**
 * Pure — notes are assumed already sorted most-recently-edited first (the
 * repository's own query order), so taking the first [maxPreview] is already
 * "most recent first."
 */
fun notesSummary(notes: List<NoteItem>, maxPreview: Int = 2): NotesSummary =
    NotesSummary(count = notes.size, preview = notes.take(maxPreview).map { notePreview(it.text) })

/**
 * How many *separate notes* a tile of this size shows, so a bigger tile shows
 * more of the notepad instead of just one note's snippet stretched out into
 * empty space — the previous version only ever showed the single latest note,
 * however tall the tile was. Reuses [previewColumnsFor] (from `TasksTile.kt`,
 * same module) for the 3+ column tiles' side-by-side layout.
 */
fun maxPreviewForNotes(size: TileSize): Int = when (size) {
    TileSize.MEDIUM -> 2
    TileSize.WIDE, TileSize.WIDE_MEDIUM -> 4
    TileSize.LARGE -> 4
    TileSize.TALL_MEDIUM -> 3
    TileSize.XLARGE -> 8
    else -> 1
}

/**
 * How many lines of a note's snippet a tile of this size has room for. The
 * previous version hardcoded a single line regardless of size, which left a
 * lot of a bigger tile's own height empty above the "notes" footer instead of
 * actually showing more of the note — scales with [TileSize.rows] the same
 * way `StickyNoteTile.kt`'s `maxLinesForStickyNote` does.
 */
fun snippetLinesFor(size: TileSize): Int = when {
    size.rows >= 4 -> 6
    size.rows == 3 -> 4
    size.rows == 2 -> 2
    else -> 1
}

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

/**
 * The live notes tile: front shows a short preview of the most-recently-edited
 * notes (how many depends on the tile's size — see [maxPreviewForNotes]), back
 * shows how many notes exist in total. Tapping the tile always opens the notes
 * list (see `NotesSheet`) — unlike Tasks, a note has no per-item toggle to mark
 * directly from the tile, so this face is pure display like most others in
 * this package.
 */
@Composable
fun NotesTileFace(size: TileSize, flipped: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { NoteRepository.create(context) }
    val notes by repository.notes.collectAsState(initial = emptyList())
    val summary = remember(notes, size) { notesSummary(notes, maxPreviewForNotes(size)) }

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { NotesFront(summary, size) },
        back = { NotesBack(summary, size) },
    )
}

@Composable
private fun NotesFront(summary: NotesSummary, size: TileSize) {
    val narrow = size.narrowLive
    val short = size.shortLive

    if (narrow) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = summary.count.toString(),
                color = FaceText,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-1).sp,
                textAlign = TextAlign.Center,
            )
            Text("notes", color = FaceText.copy(alpha = 0.82f), fontSize = 11.sp, textAlign = TextAlign.Center)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(if (short) 6.dp else 11.dp)) {
        if (summary.preview.isEmpty()) {
            Text(
                text = "no notes yet",
                color = FaceText.copy(alpha = 0.7f),
                fontSize = if (short) 12.sp else 14.sp,
            )
        } else if (previewColumnsFor(size) == 2 && summary.preview.size > 1) {
            // Wide enough for two columns and more than one note to show —
            // reads left-to-right, top-to-bottom, same as Tasks' 2-column
            // checklist preview.
            val snippetLines = snippetLinesFor(size)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.preview.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        rowItems.forEach { note ->
                            Box(modifier = Modifier.weight(1f)) {
                                NotePreviewBlock(note, short, snippetLines)
                            }
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            // Single column: fewer notes are competing for the tile's height,
            // so each one gets more snippet lines instead of the space above
            // the "notes" footer sitting empty.
            val snippetLines = snippetLinesFor(size) * (if (summary.preview.size <= 1) 2 else 1)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.preview.forEach { note -> NotePreviewBlock(note, short, snippetLines) }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("notes", color = FaceText.copy(alpha = 0.82f), fontSize = 12.sp)
    }
}

@Composable
private fun NotePreviewBlock(note: NotePreview, short: Boolean, snippetMaxLines: Int) {
    Column {
        Text(
            text = note.title,
            color = FaceText,
            fontSize = if (short) 14.sp else 15.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (note.snippet.isNotEmpty() && !short) {
            Text(
                text = note.snippet,
                color = FaceText.copy(alpha = 0.7f),
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                maxLines = snippetMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NotesBack(summary: NotesSummary, size: TileSize) {
    val narrow = size.narrowLive
    val headline = if (summary.count == 0) "no notes yet" else "${summary.count} note${if (summary.count == 1) "" else "s"}"
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow) 4.dp else 11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = headline,
            color = FaceText,
            fontSize = if (narrow) 20.sp else 26.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.5).sp,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "tap to manage",
            color = FaceText.copy(alpha = 0.65f),
            fontSize = if (narrow) 11.sp else 13.sp,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}
