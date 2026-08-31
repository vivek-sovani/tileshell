package com.tileshell.core.data

import android.content.Context
import com.tileshell.core.data.db.NoteDao
import com.tileshell.core.data.db.NoteEntity
import com.tileshell.core.data.db.TileShellDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A note, mapped from the persisted [NoteEntity] row. */
data class NoteItem(
    val id: Long,
    val text: String,
    val updatedAt: Long,
)

private fun NoteEntity.toItem() = NoteItem(id = id, text = text, updatedAt = updatedAt)

/** The first line (title) and a flattened rest-of-text snippet for one note. */
data class NotePreview(val title: String, val snippet: String)

/**
 * Pure — the "first line is the title" convention (matching Google Keep/
 * Apple Notes rather than a separate title field). Lives here (not in
 * `:feature:livetiles`, where the live tile face that also uses it lives)
 * so both the tile face and the personalize-module notes sheet can share it
 * without a new cross-feature-module dependency for one tiny function.
 */
fun notePreview(text: String): NotePreview {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return NotePreview(title = "empty note", snippet = "")
    val firstBreak = trimmed.indexOf('\n')
    return if (firstBreak == -1) {
        NotePreview(title = trimmed, snippet = "")
    } else {
        NotePreview(
            title = trimmed.substring(0, firstBreak).trim(),
            snippet = trimmed.substring(firstBreak + 1).trim().replace('\n', ' '),
        )
    }
}

/**
 * A filesystem-safe `.txt` filename for exporting a note (SAF "save a copy"),
 * derived from its own title so multiple exports are still distinguishable
 * from each other without the user having to type a name every time. Falls
 * back to a generic name when the note has no usable title (empty, or all
 * punctuation stripped away).
 */
fun suggestedNoteFileName(text: String): String {
    val title = notePreview(text).title
    val safe = title
        .replace(Regex("[^A-Za-z0-9 _-]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(40)
    return "${safe.ifEmpty { "note" }}.txt"
}

/**
 * Source of truth for the Notes live tile's notepad — one shared list of
 * notes (not one per pinned tile), most-recently-edited first.
 */
class NoteRepository(private val dao: NoteDao) {

    /** Live notes, most-recently-edited first. */
    val notes: Flow<List<NoteItem>> = dao.observeAll().map { rows -> rows.map { it.toItem() } }

    /** Creates a blank note and returns its new id, ready to open in the editor. */
    suspend fun createNote(): Long =
        dao.insert(NoteEntity(text = "", updatedAt = System.currentTimeMillis()))

    suspend fun get(id: Long): NoteItem? = dao.getById(id)?.toItem()

    suspend fun updateText(id: Long, text: String) =
        dao.updateText(id, text, System.currentTimeMillis())

    suspend fun delete(id: Long) = dao.delete(id)

    companion object {
        fun create(context: Context): NoteRepository =
            NoteRepository(TileShellDatabase.get(context).noteDao())
    }
}
