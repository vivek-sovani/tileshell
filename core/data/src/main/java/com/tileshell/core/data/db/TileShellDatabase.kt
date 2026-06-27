package com.tileshell.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteException
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
    version = 5,
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

        /** v2→v3: add per-child tile size to folder_children (default MEDIUM). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folder_children ADD COLUMN size TEXT NOT NULL DEFAULT 'MEDIUM'")
            }
        }

        /**
         * v3→v4: add the per-tile accent override column (FR-7). Nullable with no
         * default, so every existing tile decodes to null = follow the global
         * accent — preserving the prior uniform-accent look (no tile suddenly
         * recolours on upgrade); only explicit user overrides set a palette id.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tiles ADD COLUMN accentOverride TEXT")
            }
        }

        /** v4→v5: carry a per-tile accent override on folder children too, so an
         *  app's colour survives being merged into (and pulled out of) a folder. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folder_children ADD COLUMN accentOverride TEXT")
            }
        }

        /** Versioned migrations, added as the schema evolves. */
        val MIGRATIONS: Array<Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        @Volatile
        private var instance: TileShellDatabase? = null

        fun get(context: Context): TileShellDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        /**
         * Open the database, recovering to an empty file if it is corrupt
         * (S19 hardening). A schema-version mismatch with no migration path (a
         * downgrade, or a DB left by an incompatible build) recreates the file
         * via [fallbackToDestructiveMigration]; the file is force-opened here so
         * on-disk corruption surfaces at startup rather than on the first random
         * query, and a corruption the framework's handler cannot recover from is
         * wiped and rebuilt. Either way the DB comes up empty and the seeder
         * ([LayoutRepository.seedIfEmpty]) re-fills the WP default layout.
         */
        private fun build(context: Context): TileShellDatabase {
            fun open(): TileShellDatabase =
                Room.databaseBuilder(context, TileShellDatabase::class.java, NAME)
                    .addMigrations(*MIGRATIONS)
                    .fallbackToDestructiveMigration()
                    .build()

            val db = open()
            return try {
                db.openHelper.readableDatabase // force-open: corruption fails here, not later
                db
            } catch (e: SQLiteException) {
                db.close()
                context.deleteDatabase(NAME)
                open()
            }
        }
    }
}
