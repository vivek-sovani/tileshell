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
    version = 7,
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

        /**
         * v5→v6: add the gap-preserving ("windows phone style") tile arrangement's
         * persisted absolute grid cell. Nullable with no default, so every existing
         * tile decodes to null (= never anchored) — a no-op until the user opts into
         * sticky mode, at which point unanchored tiles get seeded from their current
         * dense-packed position (StartViewModel.setTilePackMode).
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tiles ADD COLUMN gridSlot INTEGER")
            }
        }

        /**
         * v6→v7: add the explicit "show as stack" toggle (see
         * `TileModel.Folder.showAsStack`'s doc comment) — a widget stack is no
         * longer purely derived from uniform WIDE/LARGE children, since the
         * stackable size set grew to cover most sizes including the default
         * MEDIUM (see `TileSize.stackable`), and deriving from uniformity alone
         * would make an ordinary same-sized-children folder auto-render as a
         * stack. Backfills `true` for any folder that is *currently* a uniform
         * WIDE or LARGE stack (the only two stack sizes that existed before this
         * migration), so an existing stack keeps rendering as one on upgrade
         * instead of silently flipping to a plain folder the moment the flag
         * defaults to false.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN showAsStack INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE folders SET showAsStack = 1 WHERE id IN (
                        SELECT folderId FROM folder_children
                        GROUP BY folderId
                        HAVING COUNT(*) = SUM(CASE WHEN size = 'WIDE' THEN 1 ELSE 0 END)
                            OR COUNT(*) = SUM(CASE WHEN size = 'LARGE' THEN 1 ELSE 0 END)
                    )
                    """.trimIndent(),
                )
            }
        }

        /** Versioned migrations, added as the schema evolves. */
        val MIGRATIONS: Array<Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

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
