package com.tileshell.core.data

/**
 * A sticky note tile is a note pinned to Start: its tile's `activityName`
 * holds `note:<id>`, linking it to that row in the notes table (v13→v14).
 * A tile with no link yet (a freshly added sticky note) creates its note on
 * first edit.
 */
object StickyNoteTile {
    private const val PREFIX = "note:"

    fun encode(noteId: Long): String = "$PREFIX$noteId"

    /** The linked note id, or null for an unlinked tile. */
    fun decode(activityName: String?): Long? =
        activityName?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.toLongOrNull()
}

/**
 * Which task list a Tasks tile shows. Older tiles (and any added from "add
 * live tiles") show the list keyed by their own tile id. A list pinned from
 * the productivity hub instead carries `tasklist:<listId>` in the tile's
 * `activityName`, since that list existed before the tile did.
 */
object TaskListTile {
    private const val PREFIX = "tasklist:"

    fun encode(listId: String): String = "$PREFIX$listId"

    fun listIdFor(tileId: String, activityName: String?): String =
        activityName?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.ifBlank { null } ?: tileId
}

/** The next default list name: "tasks" for the first list, then "tasks 2", … */
fun defaultTaskListName(existingCount: Int): String =
    if (existingCount <= 0) "tasks" else "tasks ${existingCount + 1}"
