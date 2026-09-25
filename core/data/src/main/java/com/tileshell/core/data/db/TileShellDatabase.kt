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
        TaskEntity::class,
        NoteEntity::class,
        SectionEntity::class,
        TaskListEntity::class,
    ],
    version = 14,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TileShellDatabase : RoomDatabase() {

    abstract fun layoutDao(): LayoutDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun taskListDao(): TaskListDao

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

        /**
         * v7→v8: add the ICONS-home-style "show as icon"/"show as tile" toggle
         * for a single app tile (user-requested — icons should stretch to any
         * of 4 square sizes like OneUI/Nothing OS, and since a stretched icon
         * can't also show live content, the user picks per-app whether it's an
         * icon or a live tile at MEDIUM+). Defaults to `1` (icon) even for
         * existing rows — the user's explicit ask was "by default it should be
         * icon in icon mode," not just for newly pinned apps — the render gate
         * additionally requires a non-blank `packageName` (see StartScreen.kt),
         * so this default is inert for the blank-package weather/calendar/
         * clock/personalize tiles, which keep showing live content exactly as
         * before regardless of this column's value.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tiles ADD COLUMN displayAsIcon INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * v8→v9: add the `tasks` table backing the new Tasks live tile (a single
         * global checklist, not per-pinned-tile). A fresh, empty table — nothing
         * to backfill.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tasks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `text` TEXT NOT NULL,
                        `done` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * v9→v10: add the `notes` table backing the new Notes live tile (one
         * shared notepad, not per-pinned-tile). A fresh, empty table — nothing
         * to backfill.
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `text` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * v10→v11: give the Tasks live tile a real per-instance list, keyed by
         * `listId` (the owning tile/gadget's own stable id) — user-reported:
         * pinning a second Tasks tile just showed the same single global list
         * again, instead of a fresh independent one. Existing rows default to
         * the literal string `"default"` via the column default; the second
         * statement then reassigns them to whichever Tasks tile already exists
         * on Start (oldest by grid position, if more than one somehow does),
         * so an upgrading install's real existing tasks stay visible on that
         * tile instead of silently becoming orphaned under an id nothing reads
         * — a no-op when no Tasks tile is pinned yet.
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN listId TEXT NOT NULL DEFAULT 'default'")
                db.execSQL(
                    """
                    UPDATE tasks SET listId = (
                        SELECT id FROM tiles WHERE iconKey = 'tasks' ORDER BY position LIMIT 1
                    )
                    WHERE EXISTS (SELECT 1 FROM tiles WHERE iconKey = 'tasks')
                    """.trimIndent(),
                )
            }
        }

        /**
         * Adds the index backing every `WHERE listId = ?` task query. Room
         * tracks indices as part of the schema, so this needs a real version
         * bump even though it changes no data. `IF NOT EXISTS` keeps it safe to
         * re-run, and the name must match what Room generates for
         * `@Index("listId")` or its schema validation will reject the database.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_listId ON tasks (listId)")
            }
        }

        /**
         * v12→v13: add named/collapsible Start-screen sections ("work",
         * "games", ...). A fresh `sections` table (nothing to backfill — no
         * section existed before this) plus a nullable `sectionId` column on
         * `tiles`, defaulting to null = unsectioned, so every existing tile
         * keeps rendering in the same default unsectioned area it always
         * has — an upgrading install's layout is visually unchanged until the
         * user explicitly creates a section.
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sections` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `label` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `collapsed` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                db.execSQL("ALTER TABLE tiles ADD COLUMN sectionId TEXT")
            }
        }

        /**
         * v13→v14, for the productivity hub:
         * 1. **Named task lists.** A fresh `task_lists` table, backfilled with
         *    one row per list that already exists — every distinct
         *    `tasks.listId`, plus every top-level Tasks tile (its list is its
         *    own tile id) even if still empty — oldest first, named "tasks",
         *    "tasks 2", … so nothing is unnamed.
         * 2. **Sticky notes become notes.** A sticky note's text used to live
         *    on its own tile row (`activityName`). Each one with text is moved
         *    into `notes`, and the tile's `activityName` becomes a link to that
         *    note (`note:<id>`, matching `StickyNoteTile.encode`), so the note
         *    appears in the notepad and the hub, and unpinning the tile no
         *    longer deletes it. Covers sticky notes inside folders too.
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `task_lists` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                val now = System.currentTimeMillis()
                val listIds = mutableListOf<Pair<String, Long>>()
                db.query("SELECT listId, MIN(createdAt) FROM tasks GROUP BY listId ORDER BY MIN(createdAt)").use { c ->
                    while (c.moveToNext()) listIds += c.getString(0) to c.getLong(1)
                }
                db.query("SELECT id FROM tiles WHERE iconKey = 'tasks' ORDER BY position").use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0)
                        if (listIds.none { it.first == id }) listIds += id to now
                    }
                }
                listIds.forEachIndexed { index, (id, createdAt) ->
                    val name = if (index == 0) "tasks" else "tasks ${index + 1}"
                    db.execSQL(
                        "INSERT OR IGNORE INTO task_lists (id, name, createdAt) VALUES (?, ?, ?)",
                        arrayOf<Any>(id, name, createdAt),
                    )
                }

                fun moveStickyText(table: String, keyColumn: String) {
                    val rows = mutableListOf<Pair<Any, String>>()
                    db.query("SELECT $keyColumn, activityName FROM $table WHERE iconKey = 'stickynote'").use { c ->
                        while (c.moveToNext()) {
                            val key: Any = if (c.getType(0) == android.database.Cursor.FIELD_TYPE_INTEGER) c.getLong(0) else c.getString(0)
                            val text = if (c.isNull(1)) "" else c.getString(1)
                            if (text.isNotBlank() && !text.startsWith("note:")) rows += key to text
                        }
                    }
                    rows.forEach { (key, text) ->
                        db.execSQL("INSERT INTO notes (text, updatedAt) VALUES (?, ?)", arrayOf<Any>(text, now))
                        val noteId = db.query("SELECT last_insert_rowid()").use { c -> c.moveToFirst(); c.getLong(0) }
                        db.execSQL(
                            "UPDATE $table SET activityName = ? WHERE $keyColumn = ?",
                            arrayOf<Any>("note:$noteId", key),
                        )
                    }
                }
                moveStickyText("tiles", "id")
                moveStickyText("folder_children", "rowId")
            }
        }

        /** Versioned migrations, added as the schema evolves. */
        val MIGRATIONS: Array<Migration> =
            arrayOf(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
            )

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
