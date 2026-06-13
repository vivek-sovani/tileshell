package com.tileshell.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TileEntity::class,
        FolderEntity::class,
        FolderChildEntity::class,
        AppCacheEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TileShellDatabase : RoomDatabase() {

    abstract fun layoutDao(): LayoutDao

    companion object {
        private const val NAME = "tileshell.db"

        /** v1→v2: add the monoline icon-glyph key to tiles and folder children. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tiles ADD COLUMN iconKey TEXT")
                db.execSQL("ALTER TABLE folder_children ADD COLUMN iconKey TEXT")
            }
        }

        /** Versioned migrations, added as the schema evolves. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

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
