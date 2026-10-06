package com.tileshell.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE listId = :listId ORDER BY position")
    fun observeAll(listId: String): Flow<List<TaskEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM tasks WHERE listId = :listId")
    suspend fun maxPosition(listId: String): Int

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Query("UPDATE tasks SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    /** Removes only checked-off tasks from one list — never touches an active one. */
    @Query("DELETE FROM tasks WHERE done = 1 AND listId = :listId")
    suspend fun clearCompleted(listId: String)

    /** Wipes one whole list, active tasks included — the "clean slate" action. */
    @Query("DELETE FROM tasks WHERE listId = :listId")
    suspend fun clearAll(listId: String)

    /** Daily auto-clear: checked-off tasks across every list, not just one. */
    @Query("DELETE FROM tasks WHERE done = 1")
    suspend fun clearCompletedEverywhere()

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    /** Open tasks whose reminder still has to alert (due, snoozed, or overdue). */
    @Query("SELECT * FROM tasks WHERE done = 0 AND remindAt IS NOT NULL AND remindFired = 0")
    suspend fun pendingReminders(): List<TaskEntity>

    /** Open tasks with a reminder, any list — the tiles pick out the due ones. */
    @Query("SELECT * FROM tasks WHERE done = 0 AND remindAt IS NOT NULL ORDER BY remindAt")
    fun observeReminderTasks(): Flow<List<TaskEntity>>

    /** Sets or clears ([at] null) a reminder; always re-arms it. */
    @Query("UPDATE tasks SET remindAt = :at, remindRepeat = :repeat, remindSnoozeAt = NULL, remindFired = 0 WHERE id = :id")
    suspend fun setReminder(id: Long, at: Long?, repeat: String)

    /** A repeating task moves to its next date and stays open. */
    @Query("UPDATE tasks SET remindAt = :nextAt, remindSnoozeAt = NULL, remindFired = 0, done = 0 WHERE id = :id")
    suspend fun advanceReminder(id: Long, nextAt: Long)

    @Query("UPDATE tasks SET remindSnoozeAt = NULL, remindFired = 1 WHERE id = :id")
    suspend fun markReminderFired(id: Long)

    @Query("UPDATE tasks SET remindSnoozeAt = :at, remindFired = 0 WHERE id = :id")
    suspend fun snoozeReminder(id: Long, at: Long)

    // Backup and restore.
    @Query("SELECT * FROM tasks ORDER BY id")
    suspend fun allOnce(): List<TaskEntity>

    @Query("DELETE FROM tasks")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)
}
