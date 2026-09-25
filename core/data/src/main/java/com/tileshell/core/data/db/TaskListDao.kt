package com.tileshell.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskListDao {
    /** Every list, oldest first, with its unfinished-task count. */
    @Query(
        """
        SELECT l.id AS id, l.name AS name,
            (SELECT COUNT(*) FROM tasks t WHERE t.listId = l.id AND t.done = 0) AS openCount
        FROM task_lists l ORDER BY l.createdAt
        """,
    )
    fun observeSummaries(): Flow<List<TaskListSummaryRow>>

    @Query("SELECT name FROM task_lists WHERE id = :id")
    fun observeName(id: String): Flow<String?>

    @Query("SELECT COUNT(*) FROM task_lists")
    suspend fun count(): Int

    /** No-op when the list already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(list: TaskListEntity)

    @Query("UPDATE task_lists SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM task_lists WHERE id = :id")
    suspend fun delete(id: String)

    /** Unfinished tasks across every list, newest first — the hub's "today". */
    @Query("SELECT * FROM tasks WHERE done = 0 ORDER BY createdAt DESC LIMIT :limit")
    fun observeOpenTasks(limit: Int): Flow<List<TaskEntity>>

    @Query("SELECT COUNT(*) FROM tasks WHERE done = 0")
    fun observeOpenCount(): Flow<Int>
}
