package com.tileshell.core.data

import android.content.Context
import com.tileshell.core.data.db.TaskDao
import com.tileshell.core.data.db.TaskEntity
import com.tileshell.core.data.db.TaskListDao
import com.tileshell.core.data.db.TaskListEntity
import com.tileshell.core.data.db.TileShellDatabase
import com.tileshell.core.data.reminders.TaskRepeat
import com.tileshell.core.data.reminders.TaskReminders
import com.tileshell.core.data.reminders.nextReminderAt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A checklist item, mapped from the persisted [TaskEntity] row. */
data class TaskItem(
    val id: Long,
    val text: String,
    val done: Boolean,
    /** Next reminder time (epoch millis), or null; always null when reminders are switched off. */
    val remindAt: Long? = null,
    val repeat: TaskRepeat = TaskRepeat.Once,
    val snoozeAt: Long? = null,
    val listId: String = "",
)

private fun TaskEntity.toItem() = TaskItem(
    id = id,
    text = text,
    done = done,
    remindAt = remindAt.takeIf { TaskReminders.ENABLED },
    repeat = TaskRepeat.decode(remindRepeat),
    snoozeAt = remindSnoozeAt,
    listId = listId,
)

/** A named task list with its count of unfinished tasks. */
data class TaskListSummary(val id: String, val name: String, val openCount: Int)

/** An unfinished task and the list it belongs to. */
data class OpenTask(
    val id: Long,
    val text: String,
    val listId: String,
    /** Reminder time, or null; null whenever reminders are switched off. */
    val remindAt: Long? = null,
    val repeat: TaskRepeat = TaskRepeat.Once,
)

/**
 * Source of truth for the Tasks live tile's checklist. Each pinned Tasks tile
 * (Start) or gadget (glance) keeps its own independent list, keyed by
 * [TaskItem]-caller-supplied `listId` — the tile/widget's own stable id (see
 * `TasksTileFace`/`TaskListSheet`) — rather than one list shared by every
 * instance, since a user pinning a second Tasks tile clearly wants a second,
 * separate checklist, not a duplicate view of the first one.
 */
class TaskRepository(
    private val dao: TaskDao,
    private val lists: TaskListDao,
    /** Runs after every write that can change a reminder — reschedules the alarms. */
    private val onRemindersChanged: suspend () -> Unit = {},
) {

    /** Live, ordered task list for one specific pinned instance. */
    fun tasks(listId: String): Flow<List<TaskItem>> =
        dao.observeAll(listId).map { rows -> rows.map { it.toItem() } }

    /**
     * Appends a new task at the end of [listId]'s list, optionally with a
     * reminder set in the same step. Blank text is ignored.
     */
    suspend fun addTask(listId: String, text: String, remindAt: Long? = null, repeat: TaskRepeat = TaskRepeat.Once) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        ensureList(listId)
        val withReminder = remindAt != null && TaskReminders.ENABLED
        dao.insert(
            TaskEntity(
                text = trimmed,
                listId = listId,
                position = dao.maxPosition(listId) + 1,
                createdAt = System.currentTimeMillis(),
                remindAt = remindAt.takeIf { withReminder },
                remindRepeat = if (withReminder) repeat.code else "",
            ),
        )
        if (withReminder) onRemindersChanged()
    }

    /**
     * Ticking a repeating task with a reminder moves it to its next date and
     * keeps it open (no copies); everything else just toggles.
     */
    suspend fun setDone(id: Long, done: Boolean) {
        val task = if (done && TaskReminders.ENABLED) dao.get(id) else null
        val repeat = TaskRepeat.decode(task?.remindRepeat)
        val next = task?.remindAt?.let { nextReminderAt(it, repeat, System.currentTimeMillis()) }
        if (next != null) dao.advanceReminder(id, next) else dao.setDone(id, done)
        onRemindersChanged()
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        onRemindersChanged()
    }

    /** Sets a reminder on a task, or clears it when [at] is null. */
    suspend fun setReminder(id: Long, at: Long?, repeat: TaskRepeat) {
        dao.setReminder(id, at, if (at == null) "" else repeat.code)
        onRemindersChanged()
    }

    /** Open tasks with a reminder across every list, soonest first. */
    fun reminderTasks(): Flow<List<TaskItem>> =
        dao.observeReminderTasks().map { rows -> if (TaskReminders.ENABLED) rows.map { it.toItem() } else emptyList() }

    /** Removes only [listId]'s checked-off tasks — the safe, non-destructive "tidy up" action. */
    suspend fun clearCompleted(listId: String) = dao.clearCompleted(listId)

    /** Wipes [listId]'s whole list, including unfinished tasks — a deliberate "start over." */
    suspend fun clearAll(listId: String) {
        dao.clearAll(listId)
        onRemindersChanged()
    }

    /** Daily auto-clear (see `TaskDailyResetWorker`) — completed tasks across every list, not just one. */
    suspend fun clearCompletedEverywhere() = dao.clearCompletedEverywhere()

    /** Every named list with its unfinished count, oldest first. */
    fun lists(): Flow<List<TaskListSummary>> =
        lists.observeSummaries().map { rows -> rows.map { TaskListSummary(it.id, it.name, it.openCount) } }

    /** [listId]'s name, or null until the list has a row. */
    fun listName(listId: String): Flow<String?> = lists.observeName(listId)

    /** Unfinished tasks across every list: reminders first (soonest first), then the rest newest first. */
    fun openTasks(limit: Int = 20): Flow<List<OpenTask>> =
        lists.observeOpenTasks(limit).map { rows -> rows.map {
            OpenTask(it.id, it.text, it.listId, it.remindAt.takeIf { TaskReminders.ENABLED }, TaskRepeat.decode(it.remindRepeat))
        } }

    fun openCount(): Flow<Int> = lists.observeOpenCount()

    /** Gives [listId] a row (default name) if it doesn't have one yet — called
     * whenever a list is shown or written to, so every list gets a name. */
    suspend fun ensureList(listId: String) {
        if (listId.isBlank()) return
        lists.insert(TaskListEntity(listId, defaultTaskListName(lists.count()), System.currentTimeMillis()))
    }

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
        onRemindersChanged()
    }

    companion object {
        fun create(context: Context): TaskRepository {
            val app = context.applicationContext
            val db = TileShellDatabase.get(app)
            return TaskRepository(db.taskDao(), db.taskListDao()) { TaskReminders.sync(app) }
        }
    }
}
