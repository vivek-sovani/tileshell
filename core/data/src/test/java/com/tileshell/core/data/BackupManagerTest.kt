package com.tileshell.core.data

import com.tileshell.core.data.db.FolderChildEntity
import com.tileshell.core.data.db.FolderEntity
import com.tileshell.core.data.db.TileEntity
import com.tileshell.core.data.settings.FontStyle
import com.tileshell.core.data.settings.LauncherSettings
import com.tileshell.core.data.settings.TileColorSource
import com.tileshell.core.data.settings.TileFill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupManagerTest {

    private val sampleSettings = LauncherSettings(
        dark = false,
        accentId = "magenta",
        glass = false,
        transparency = 0.3f,
        cornerRadius = 8f,
        tileGap = 6f,
        columns = 5,
        tileFill = TileFill.GRADIENT,
        fontStyle = FontStyle.NUNITO,
        tileColorSource = TileColorSource.APP_ICON,
    )

    private val sampleTiles = listOf(
        TileEntity(
            id = "t1", position = 0, size = TileSize.MEDIUM, colorId = "blue",
            type = TileEntity.TYPE_APP, packageName = "com.example", activityName = "com.example.Main",
            label = "Example", iconKey = "phone", accentOverride = null, folderId = null,
        ),
        TileEntity(
            id = "t2", position = 1, size = TileSize.WIDE, colorId = "green",
            type = TileEntity.TYPE_FOLDER, packageName = null, activityName = null,
            label = null, iconKey = null, accentOverride = "#ff0000", folderId = "f1",
        ),
    )

    private val sampleFolders = listOf(FolderEntity(id = "f1", name = "social"))

    private val sampleChildren = listOf(
        FolderChildEntity(
            rowId = 99, folderId = "f1", position = 0,
            packageName = "com.instagram", activityName = "com.instagram.A",
            label = null, iconKey = null, size = TileSize.MEDIUM, accentOverride = null,
        ),
        FolderChildEntity(
            rowId = 100, folderId = "f1", position = 1,
            packageName = "com.twitter", activityName = "com.twitter.B",
            label = "X", iconKey = "messages", size = TileSize.SMALL, accentOverride = "blue",
        ),
    )

    @Test
    fun `round-trip preserves tiles`() {
        val json = BackupManager.buildBackupJson(sampleTiles, sampleFolders, sampleChildren, sampleSettings)
        val data = BackupManager.parseBackup(json)

        assertEquals(2, data.tiles.size)
        val t1 = data.tiles[0]
        assertEquals("t1", t1.id)
        assertEquals(0, t1.position)
        assertEquals(TileSize.MEDIUM, t1.size)
        assertEquals("blue", t1.colorId)
        assertEquals(TileEntity.TYPE_APP, t1.type)
        assertEquals("com.example", t1.packageName)
        assertEquals("com.example.Main", t1.activityName)
        assertEquals("Example", t1.label)
        assertEquals("phone", t1.iconKey)
        assertNull(t1.accentOverride)
        assertNull(t1.folderId)

        val t2 = data.tiles[1]
        assertEquals("#ff0000", t2.accentOverride)
        assertEquals("f1", t2.folderId)
        assertNull(t2.packageName)
    }

    @Test
    fun `round-trip preserves folders`() {
        val json = BackupManager.buildBackupJson(sampleTiles, sampleFolders, sampleChildren, sampleSettings)
        val data = BackupManager.parseBackup(json)

        assertEquals(1, data.folders.size)
        assertEquals("f1", data.folders[0].id)
        assertEquals("social", data.folders[0].name)
    }

    @Test
    fun `round-trip preserves folder children and resets rowId to 0`() {
        val json = BackupManager.buildBackupJson(sampleTiles, sampleFolders, sampleChildren, sampleSettings)
        val data = BackupManager.parseBackup(json)

        assertEquals(2, data.folderChildren.size)
        val c1 = data.folderChildren[0]
        assertEquals(0L, c1.rowId)   // rowId always 0 on restore
        assertEquals("f1", c1.folderId)
        assertEquals("com.instagram", c1.packageName)
        assertNull(c1.label)
        assertEquals(TileSize.MEDIUM, c1.size)

        val c2 = data.folderChildren[1]
        assertEquals(0L, c2.rowId)
        assertEquals("X", c2.label)
        assertEquals("messages", c2.iconKey)
        assertEquals(TileSize.SMALL, c2.size)
        assertEquals("blue", c2.accentOverride)
    }

    @Test
    fun `round-trip preserves settings`() {
        val json = BackupManager.buildBackupJson(sampleTiles, sampleFolders, sampleChildren, sampleSettings)
        val data = BackupManager.parseBackup(json)

        assertEquals(sampleSettings.dark, data.settings.dark)
        assertEquals(sampleSettings.accentId, data.settings.accentId)
        assertEquals(sampleSettings.glass, data.settings.glass)
        assertEquals(sampleSettings.transparency, data.settings.transparency, 0.001f)
        assertEquals(sampleSettings.cornerRadius, data.settings.cornerRadius, 0.001f)
        assertEquals(sampleSettings.tileGap, data.settings.tileGap, 0.001f)
        assertEquals(sampleSettings.columns, data.settings.columns)
        assertEquals(sampleSettings.tileFill, data.settings.tileFill)
        assertEquals(sampleSettings.fontStyle, data.settings.fontStyle)
        assertEquals(sampleSettings.tileColorSource, data.settings.tileColorSource)
    }

    @Test
    fun `round-trip with empty layout`() {
        val json = BackupManager.buildBackupJson(emptyList(), emptyList(), emptyList(), LauncherSettings())
        val data = BackupManager.parseBackup(json)

        assertEquals(0, data.tiles.size)
        assertEquals(0, data.folders.size)
        assertEquals(0, data.folderChildren.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `wrong version throws`() {
        val json = """{"version":99,"settings":"","tiles":[],"folders":[],"folderChildren":[]}"""
        BackupManager.parseBackup(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed json throws`() {
        BackupManager.parseBackup("not json at all")
    }

    @Test
    fun `layoutHash changes when settings change with identical tiles`() {
        val hash1 = BackupManager.layoutHash(sampleTiles, sampleFolders, sampleChildren, sampleSettings)
        val hash2 = BackupManager.layoutHash(
            sampleTiles, sampleFolders, sampleChildren,
            sampleSettings.copy(accentId = "blue"),
        )

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `layoutHash is stable for identical tiles and settings`() {
        val hash1 = BackupManager.layoutHash(sampleTiles, sampleFolders, sampleChildren, sampleSettings)
        val hash2 = BackupManager.layoutHash(sampleTiles, sampleFolders, sampleChildren, sampleSettings)

        assertEquals(hash1, hash2)
    }

    @Test
    fun `nullable fields survive round-trip as null`() {
        val tile = TileEntity(
            id = "x", position = 0, size = TileSize.SMALL, colorId = "blue",
            type = TileEntity.TYPE_APP, packageName = null, activityName = null,
            label = null, iconKey = null, accentOverride = null, folderId = null,
        )
        val json = BackupManager.buildBackupJson(listOf(tile), emptyList(), emptyList(), LauncherSettings())
        val data = BackupManager.parseBackup(json)

        assertNull(data.tiles[0].packageName)
        assertNull(data.tiles[0].activityName)
        assertNull(data.tiles[0].label)
        assertNull(data.tiles[0].iconKey)
        assertNull(data.tiles[0].accentOverride)
        assertNull(data.tiles[0].folderId)
        assertNull(data.tiles[0].gridSlot)
    }

    @Test
    fun `round-trip preserves gridSlot for sticky-mode anchored tiles`() {
        val tile = TileEntity(
            id = "anchored", position = 0, size = TileSize.MEDIUM, colorId = "blue",
            type = TileEntity.TYPE_APP, packageName = "com.example", activityName = null,
            label = null, iconKey = null, accentOverride = null, folderId = null,
            gridSlot = 2003,
        )
        val json = BackupManager.buildBackupJson(listOf(tile), emptyList(), emptyList(), sampleSettings)
        val data = BackupManager.parseBackup(json)

        assertEquals(2003, data.tiles[0].gridSlot)
    }

    @Test
    fun `layoutHash changes when only gridSlot changes`() {
        val anchored = sampleTiles[0].copy(gridSlot = 1002)
        val hash1 = BackupManager.layoutHash(sampleTiles, sampleFolders, sampleChildren, sampleSettings)
        val hash2 = BackupManager.layoutHash(
            listOf(anchored, sampleTiles[1]), sampleFolders, sampleChildren, sampleSettings,
        )

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `round-trip preserves hidden apps, feed sources and regions, widgets, and photo uris`() {
        val json = BackupManager.buildBackupJson(
            sampleTiles, sampleFolders, sampleChildren, sampleSettings,
            hiddenApps = setOf("com.a", "com.b"),
            feedSources = listOf(
                BackupFeedSource(url = "https://a.example/feed", name = "A", category = "nation", enabled = true),
                BackupFeedSource(url = "https://b.example/feed", name = "B", category = "custom", enabled = false),
            ),
            feedRegions = setOf("IN", "US"),
            widgets = listOf(BackupWidget(widgetId = 7, heightDp = 200, widthDp = 0)),
            photoUris = listOf("content://a", "content://b"),
            wallpaperSlideshowUris = listOf("content://w1"),
        )
        val data = BackupManager.parseBackup(json)

        assertEquals(setOf("com.a", "com.b"), data.hiddenApps)
        assertEquals(2, data.feedSources.size)
        assertEquals("https://a.example/feed", data.feedSources[0].url)
        assertEquals("nation", data.feedSources[0].category)
        assertEquals(true, data.feedSources[0].enabled)
        assertEquals(false, data.feedSources[1].enabled)
        assertEquals(setOf("IN", "US"), data.feedRegions)
        assertEquals(1, data.widgets.size)
        assertEquals(7, data.widgets[0].widgetId)
        assertEquals(200, data.widgets[0].heightDp)
        assertEquals(listOf("content://a", "content://b"), data.photoUris)
        assertEquals(listOf("content://w1"), data.wallpaperSlideshowUris)
    }

    @Test
    fun `backup without the new domains decodes them as empty`() {
        // Simulates parsing an old (pre-extension) backup file missing these keys.
        val json = BackupManager.buildBackupJson(sampleTiles, sampleFolders, sampleChildren, sampleSettings)
        val data = BackupManager.parseBackup(json)

        assertEquals(emptySet<String>(), data.hiddenApps)
        assertEquals(emptyList<BackupFeedSource>(), data.feedSources)
        assertEquals(emptySet<String>(), data.feedRegions)
        assertEquals(emptyList<BackupWidget>(), data.widgets)
        assertEquals(emptyList<String>(), data.photoUris)
        assertEquals(emptyList<String>(), data.wallpaperSlideshowUris)
    }
}
