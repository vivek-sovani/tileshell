package com.tileshell.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Query("UPDATE notes SET text = :text, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateText(id: Long, text: String, updatedAt: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)
}
