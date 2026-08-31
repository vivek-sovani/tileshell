package com.tileshell.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE listId = :listId ORDER BY position")
    fun observeAll(listId: String): Flow<List<TaskEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM tasks WHERE listId = :listId")
    suspend fun maxPosition(listId: String): Int

    @Insert
    suspend fun insert(task: TaskEntity)

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
}
