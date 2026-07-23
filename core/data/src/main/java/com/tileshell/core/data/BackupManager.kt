package com.tileshell.core.data

import com.tileshell.core.data.db.FolderChildEntity
import com.tileshell.core.data.db.FolderEntity
import com.tileshell.core.data.db.TileEntity
import com.tileshell.core.data.settings.LauncherSettings
import com.tileshell.core.data.settings.SettingsCodec
import org.json.JSONArray
import org.json.JSONObject

/**
 * A subscribed feed source, mirroring `feature.livetiles.FeedSource` — kept as a
 * plain local copy rather than an import so :core:data never depends on a feature
 * module; callers on the feature side map to/from their own type.
 */
data class BackupFeedSource(val url: String, val name: String, val category: String, val enabled: Boolean)

/** A hosted feed widget, mirroring `feature.start.feed.HostedWidget` — see [BackupFeedSource]. */
data class BackupWidget(val widgetId: Int, val heightDp: Int, val widthDp: Int)

/**
 * Serialized snapshot of the Start layout + settings for export/import. The
 * trailing fields (default empty) cover domains added well after the original
 * tiles/folders/settings backup and are populated only by the manual
 * export/import path (`StartViewModel.exportBackup`/`importBackup`), not by the
 * automatic rolling layout-history snapshots (`saveLayoutSnapshot`/
 * `restoreFromSnapshot`) — those stay scoped to layout + settings, since
 * feed subscriptions/hidden apps/etc. aren't really part of "the layout."
 */
data class BackupData(
    val tiles: List<TileEntity>,
    val folders: List<FolderEntity>,
    val folderChildren: List<FolderChildEntity>,
    val settings: LauncherSettings,
    val hiddenApps: Set<String> = emptySet(),
    val feedSources: List<BackupFeedSource> = emptyList(),
    val feedRegions: Set<String> = emptySet(),
    val widgets: List<BackupWidget> = emptyList(),
    val photoUris: List<String> = emptyList(),
    val wallpaperSlideshowUris: List<String> = emptyList(),
)

/**
 * Pure JSON serializer/deserializer for layout backups. No Android Context
 * needed — fully JVM-unit-testable. Uses org.json (already on classpath via
 * the weather provider).
 *
 * Version 1 schema: { version, settings, tiles, folders, folderChildren }
 * FolderChildEntity.rowId is always stored as 0 so Room auto-generates new
 * IDs on restore (avoids any collision with existing rows in a clean DB).
 *
 * `tile.gridSlot` (added without a version bump — purely additive/optional,
 * so old backups still parse fine and decode it as null) was previously
 * omitted here entirely: since STICKY is the default tile-arrangement mode
 * and `gridSlot` is exactly what anchors a tile to its own cell in that mode,
 * every restore silently dropped it and let tiles re-flow to different
 * positions than what was actually backed up — the direct cause of a
 * user-reported "restore isn't the same as backup" bug. `hiddenApps`,
 * `feedSources`/`feedRegions`, `widgets`, `photoUris`, and
 * `wallpaperSlideshowUris` were added the same way (additive, no version
 * bump) after a fuller audit found those entire domains — added in later
 * sessions — were never wired into backup/restore at all.
 */
object BackupManager {

    private const val CURRENT_VERSION = 1

    fun buildBackupJson(
        tiles: List<TileEntity>,
        folders: List<FolderEntity>,
        children: List<FolderChildEntity>,
        settings: LauncherSettings,
        hiddenApps: Set<String> = emptySet(),
        feedSources: List<BackupFeedSource> = emptyList(),
        feedRegions: Set<String> = emptySet(),
        widgets: List<BackupWidget> = emptyList(),
        photoUris: List<String> = emptyList(),
        wallpaperSlideshowUris: List<String> = emptyList(),
    ): String = JSONObject().apply {
        put("version", CURRENT_VERSION)
        put("settings", SettingsCodec.encode(settings))
        put("tiles", JSONArray().also { arr ->
            tiles.forEach { t ->
                arr.put(JSONObject().apply {
                    put("id", t.id)
                    put("position", t.position)
                    put("size", t.size.name)
                    put("colorId", t.colorId)
                    putOpt("accentOverride", t.accentOverride)
                    putOpt("gridSlot", t.gridSlot)
                    put("type", t.type)
                    putOpt("packageName", t.packageName)
                    putOpt("activityName", t.activityName)
                    putOpt("label", t.label)
                    putOpt("iconKey", t.iconKey)
                    putOpt("folderId", t.folderId)
                })
            }
        })
        put("folders", JSONArray().also { arr ->
            folders.forEach { f ->
                arr.put(JSONObject().apply {
                    put("id", f.id)
                    put("name", f.name)
                })
            }
        })
        put("folderChildren", JSONArray().also { arr ->
            children.forEach { c ->
                arr.put(JSONObject().apply {
                    put("rowId", 0)
                    put("folderId", c.folderId)
                    put("position", c.position)
                    put("packageName", c.packageName)
                    put("activityName", c.activityName)
                    putOpt("label", c.label)
                    putOpt("iconKey", c.iconKey)
                    put("size", c.size.name)
                    putOpt("accentOverride", c.accentOverride)
                })
            }
        })
        put("hiddenApps", JSONArray(hiddenApps.toList()))
        put("feedSources", JSONArray().also { arr ->
            feedSources.forEach { s ->
                arr.put(JSONObject().apply {
                    put("url", s.url)
                    put("name", s.name)
                    put("category", s.category)
                    put("enabled", s.enabled)
                })
            }
        })
        put("feedRegions", JSONArray(feedRegions.toList()))
        put("widgets", JSONArray().also { arr ->
            widgets.forEach { w ->
                arr.put(JSONObject().apply {
                    put("widgetId", w.widgetId)
                    put("heightDp", w.heightDp)
                    put("widthDp", w.widthDp)
                })
            }
        })
        put("photoUris", JSONArray(photoUris))
        put("wallpaperSlideshowUris", JSONArray(wallpaperSlideshowUris))
    }.toString()

    /**
     * Stable deduplication hash of the tile/folder structure AND settings — a
     * settings-only change (wallpaper, accent, tile style, columns, etc.) must also
     * produce a new history entry, otherwise a stale head snapshot silently keeps
     * old settings and "restore" appears to revert personalization on restore.
     */
    fun layoutHash(
        tiles: List<TileEntity>,
        folders: List<FolderEntity>,
        children: List<FolderChildEntity>,
        settings: LauncherSettings,
    ): String = buildString {
        tiles.sortedBy { it.id }.forEach { t ->
            append(t.id).append(':').append(t.position).append(':')
                .append(t.size.name).append(':').append(t.type).append(':')
                .append(t.packageName).append(':').append(t.folderId).append(':')
                .append(t.gridSlot).append('|')
        }
        folders.sortedBy { it.id }.forEach { f ->
            append(f.id).append(':').append(f.name).append('|')
        }
        children.sortedWith(compareBy({ it.folderId }, { it.position })).forEach { c ->
            append(c.folderId).append(':').append(c.position).append(':')
                .append(c.packageName).append(':').append(c.size.name).append('|')
        }
        append("settings:").append(SettingsCodec.encode(settings))
    }.hashCode().toString()

    /**
     * Parse and validate a backup JSON string. Throws [IllegalArgumentException]
     * on a version mismatch or any structural problem — callers should wrap in
     * runCatching and surface the failure as a user-visible error.
     */
    fun parseBackup(json: String): BackupData {
        val root = runCatching { JSONObject(json) }
            .getOrElse { throw IllegalArgumentException("not valid JSON", it) }

        val version = root.optInt("version", -1)
        require(version == CURRENT_VERSION) {
            "unsupported backup version $version (expected $CURRENT_VERSION)"
        }

        val settings = SettingsCodec.decode(root.optString("settings", ""))

        val tiles = root.getJSONArray("tiles").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TileEntity(
                    id = o.getString("id"),
                    position = o.getInt("position"),
                    size = TileSize.entries.find { it.name == o.getString("size") }
                        ?: TileSize.MEDIUM,
                    colorId = o.getString("colorId"),
                    accentOverride = o.optString("accentOverride", "").ifEmpty { null },
                    gridSlot = if (o.has("gridSlot") && !o.isNull("gridSlot")) o.getInt("gridSlot") else null,
                    type = o.getString("type"),
                    packageName = o.optString("packageName", "").ifEmpty { null },
                    activityName = o.optString("activityName", "").ifEmpty { null },
                    label = o.optString("label", "").ifEmpty { null },
                    iconKey = o.optString("iconKey", "").ifEmpty { null },
                    folderId = o.optString("folderId", "").ifEmpty { null },
                )
            }
        }

        val folders = root.getJSONArray("folders").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FolderEntity(id = o.getString("id"), name = o.getString("name"))
            }
        }

        val folderChildren = root.getJSONArray("folderChildren").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FolderChildEntity(
                    rowId = 0,
                    folderId = o.getString("folderId"),
                    position = o.getInt("position"),
                    packageName = o.getString("packageName"),
                    activityName = o.getString("activityName"),
                    label = o.optString("label", "").ifEmpty { null },
                    iconKey = o.optString("iconKey", "").ifEmpty { null },
                    size = TileSize.entries.find { it.name == o.optString("size", "") }
                        ?: TileSize.MEDIUM,
                    accentOverride = o.optString("accentOverride", "").ifEmpty { null },
                )
            }
        }

        val hiddenApps = root.optJSONArray("hiddenApps")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } ?: emptySet()

        val feedSources = root.optJSONArray("feedSources")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BackupFeedSource(
                    url = o.getString("url"),
                    name = o.getString("name"),
                    category = o.getString("category"),
                    enabled = o.optBoolean("enabled", true),
                )
            }
        } ?: emptyList()

        val feedRegions = root.optJSONArray("feedRegions")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } ?: emptySet()

        val widgets = root.optJSONArray("widgets")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BackupWidget(
                    widgetId = o.getInt("widgetId"),
                    heightDp = o.getInt("heightDp"),
                    widthDp = o.optInt("widthDp", 0),
                )
            }
        } ?: emptyList()

        val photoUris = root.optJSONArray("photoUris")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()

        val wallpaperSlideshowUris = root.optJSONArray("wallpaperSlideshowUris")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()

        return BackupData(
            tiles, folders, folderChildren, settings,
            hiddenApps, feedSources, feedRegions, widgets, photoUris, wallpaperSlideshowUris,
        )
    }
}
