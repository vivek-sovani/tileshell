package com.tileshell.core.data

import android.content.Context
import com.tileshell.core.data.db.TaskDao
import com.tileshell.core.data.db.TaskEntity
import com.tileshell.core.data.db.TileShellDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A checklist item, mapped from the persisted [TaskEntity] row. */
data class TaskItem(
    val id: Long,
    val text: String,
    val done: Boolean,
)

private fun TaskEntity.toItem() = TaskItem(id = id, text = text, done = done)

/**
 * Source of truth for the Tasks live tile's checklist. Each pinned Tasks tile
 * (Start) or gadget (glance) keeps its own independent list, keyed by
 * [TaskItem]-caller-supplied `listId` — the tile/widget's own stable id (see
 * `TasksTileFace`/`TaskListSheet`) — rather than one list shared by every
 * instance, since a user pinning a second Tasks tile clearly wants a second,
 * separate checklist, not a duplicate view of the first one.
 */
class TaskRepository(private val dao: TaskDao) {

    /** Live, ordered task list for one specific pinned instance. */
    fun tasks(listId: String): Flow<List<TaskItem>> =
        dao.observeAll(listId).map { rows -> rows.map { it.toItem() } }

    /** Appends a new task at the end of [listId]'s list. Blank text is ignored. */
    suspend fun addTask(listId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        dao.insert(
            TaskEntity(
                text = trimmed,
                listId = listId,
                position = dao.maxPosition(listId) + 1,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun setDone(id: Long, done: Boolean) = dao.setDone(id, done)

    suspend fun delete(id: Long) = dao.delete(id)

    /** Removes only [listId]'s checked-off tasks — the safe, non-destructive "tidy up" action. */
    suspend fun clearCompleted(listId: String) = dao.clearCompleted(listId)

    /** Wipes [listId]'s whole list, including unfinished tasks — a deliberate "start over." */
    suspend fun clearAll(listId: String) = dao.clearAll(listId)

    /** Daily auto-clear (see `TaskDailyResetWorker`) — completed tasks across every list, not just one. */
    suspend fun clearCompletedEverywhere() = dao.clearCompletedEverywhere()

    companion object {
        fun create(context: Context): TaskRepository =
            TaskRepository(TileShellDatabase.get(context).taskDao())
    }
}
