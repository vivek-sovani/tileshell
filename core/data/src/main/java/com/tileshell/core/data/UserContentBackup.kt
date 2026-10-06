package com.tileshell.core.data

import android.content.Context
import androidx.room.withTransaction
import com.tileshell.core.data.db.NoteEntity
import com.tileshell.core.data.db.TaskEntity
import com.tileshell.core.data.db.TaskListEntity
import com.tileshell.core.data.db.TileShellDatabase
import com.tileshell.core.data.reminders.TaskReminders

/** Notes, task lists and tasks as the manual backup carries them. */
data class UserContent(
    val notes: List<NoteEntity>,
    val taskLists: List<TaskListEntity>,
    val tasks: List<TaskEntity>,
)

/**
 * Reads and replaces the user-written database content (notes, tasks) for the
 * manual backup. Ids are kept as they were: a sticky-note tile points at its
 * note by id, and a task's reminder alarm is keyed by task id.
 */
object UserContentBackup {

    suspend fun read(context: Context): UserContent {
        val db = TileShellDatabase.get(context.applicationContext)
        return UserContent(db.noteDao().allOnce(), db.taskListDao().allOnce(), db.taskDao().allOnce())
    }

    /** Replaces only the parts the backup actually held (null = leave as is). */
    suspend fun replace(
        context: Context,
        notes: List<NoteEntity>?,
        taskLists: List<TaskListEntity>?,
        tasks: List<TaskEntity>?,
    ) {
        val app = context.applicationContext
        val db = TileShellDatabase.get(app)
        db.withTransaction {
            if (notes != null) {
                db.noteDao().clearAll()
                db.noteDao().insertAll(notes)
            }
            if (taskLists != null) {
                db.taskListDao().clearAll()
                db.taskListDao().insertAll(taskLists)
            }
            if (tasks != null) {
                db.taskDao().clearAll()
                db.taskDao().insertAll(tasks)
            }
        }
        // Reminder alarms follow the tasks table.
        if (tasks != null) TaskReminders.sync(app)
    }
}
