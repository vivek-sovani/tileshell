package com.tileshell.core.data

import android.content.Context
import com.tileshell.core.data.db.TaskDao
import com.tileshell.core.data.db.TaskEntity
import com.tileshell.core.data.db.TaskListDao
import com.tileshell.core.data.db.TaskListEntity
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

/** A named task list with its count of unfinished tasks. */
data class TaskListSummary(val id: String, val name: String, val openCount: Int)

/** An unfinished task and the list it belongs to. */
data class OpenTask(val id: Long, val text: String, val listId: String)

/**
 * Source of truth for the Tasks live tile's checklist. Each pinned Tasks tile
 * (Start) or gadget (glance) keeps its own independent list, keyed by
 * [TaskItem]-caller-supplied `listId` — the tile/widget's own stable id (see
 * `TasksTileFace`/`TaskListSheet`) — rather than one list shared by every
 * instance, since a user pinning a second Tasks tile clearly wants a second,
 * separate checklist, not a duplicate view of the first one.
 */
class TaskRepository(private val dao: TaskDao, private val lists: TaskListDao) {

    /** Live, ordered task list for one specific pinned instance. */
    fun tasks(listId: String): Flow<List<TaskItem>> =
        dao.observeAll(listId).map { rows -> rows.map { it.toItem() } }

    /** Appends a new task at the end of [listId]'s list. Blank text is ignored. */
    suspend fun addTask(listId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        ensureList(listId)
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

    /** Every named list with its unfinished count, oldest first. */
    fun lists(): Flow<List<TaskListSummary>> =
        lists.observeSummaries().map { rows -> rows.map { TaskListSummary(it.id, it.name, it.openCount) } }

    /** [listId]'s name, or null until the list has a row. */
    fun listName(listId: String): Flow<String?> = lists.observeName(listId)

    /** Unfinished tasks across every list, newest first. */
    fun openTasks(limit: Int = 20): Flow<List<OpenTask>> =
        lists.observeOpenTasks(limit).map { rows -> rows.map { OpenTask(it.id, it.text, it.listId) } }

    fun openCount(): Flow<Int> = lists.observeOpenCount()

    /** Gives [listId] a row (default name) if it doesn't have one yet — called
     * whenever a list is shown or written to, so every list gets a name. */
    suspend fun ensureList(listId: String) {
        if (listId.isBlank()) return
        lists.insert(TaskListEntity(listId, defaultTaskListName(lists.count()), System.currentTimeMillis()))
    }

    /** The oldest list's id, or null when there are none. */
    suspend fun firstListIdOrNull(): String? = lists.firstId()

    /** A new empty list for the productivity hub; returns its id. */
    suspend fun createList(name: String): String {
        val id = "list-${System.currentTimeMillis()}"
        val trimmed = name.trim().ifEmpty { defaultTaskListName(lists.count()) }
        lists.insert(TaskListEntity(id, trimmed, System.currentTimeMillis()))
        return id
    }

    suspend fun renameList(listId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        ensureList(listId)
        lists.rename(listId, trimmed)
    }

    /** Deletes a list and all its tasks. */
    suspend fun deleteList(listId: String) {
        dao.clearAll(listId)
        lists.delete(listId)
    }

    companion object {
        fun create(context: Context): TaskRepository {
            val db = TileShellDatabase.get(context)
            return TaskRepository(db.taskDao(), db.taskListDao())
        }
    }
}
