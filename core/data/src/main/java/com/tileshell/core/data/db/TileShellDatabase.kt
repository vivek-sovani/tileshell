package com.tileshell.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

@Database(
    entities = [
        TileEntity::class,
        FolderEntity::class,
        FolderChildEntity::class,
        AppCacheEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TileShellDatabase : RoomDatabase() {

    abstract fun layoutDao(): LayoutDao

    companion object {
        private const val NAME = "tileshell.db"

        /** Versioned migrations, added as the schema evolves past v1. */
        val MIGRATIONS: Array<Migration> = emptyArray()

        @Volatile
        private var instance: TileShellDatabase? = null

        fun get(context: Context): TileShellDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): TileShellDatabase =
            Room.databaseBuilder(context, TileShellDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
