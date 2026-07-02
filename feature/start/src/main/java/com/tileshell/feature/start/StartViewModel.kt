package com.tileshell.feature.start

import android.app.Application
import android.content.Context
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tileshell.core.data.AppCatalogRepository
import com.tileshell.core.data.AppCategories
import com.tileshell.core.data.AppEntry
import com.tileshell.core.data.BackupManager
import com.tileshell.core.data.CachedScreenshotPrefs
import com.tileshell.core.data.FolderChild
import com.tileshell.core.data.HiddenApps
import com.tileshell.core.data.LayoutHistoryRepository
import com.tileshell.core.data.LayoutRepository
import com.tileshell.core.data.LayoutSnapshot
import com.tileshell.core.data.PinResult
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.settings.LauncherSettings
import com.tileshell.core.data.settings.SettingsRepository
import com.tileshell.feature.livetiles.DEFAULT_FEED_SOURCES
import com.tileshell.feature.livetiles.FeedRefreshWorker
import com.tileshell.feature.livetiles.FeedSource
import com.tileshell.feature.livetiles.FeedStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the Start screen: exposes the persisted layout as a [StateFlow], seeds
 * the WP default layout on first run, emits "go home" requests, and prunes
 * tiles for uninstalled packages (FR-5).
 */
class StartViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LayoutRepository.create(application)
    private val catalogRepository = AppCatalogRepository(application)
    private val settingsRepository = SettingsRepository.create(application)
    private val historyRepository = LayoutHistoryRepository(application)
    private val feedStore = FeedStore.create(application)
    private val launcherApps =
        application.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    // All layout mutations run on a single-thread context so committed edits are
    // serialized — they apply in call order and never interleave with one
    // another's transaction (S19 persistence hardening). The DAO ops are already
    // each a @Transaction; this guarantees ordering across them.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val writeContext = Dispatchers.IO.limitedParallelism(1)

    val tiles: StateFlow<List<TileModel>> = repository.tiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** Persisted personalization (theme + accent), applied live (FR-7). */
    val settings: StateFlow<LauncherSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LauncherSettings(),
    )

    /** Live catalogue of installed launchable apps (drives category folders). */
    val apps: StateFlow<List<AppEntry>> = catalogRepository.apps.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** Packages hidden from the app list (personalize → hidden apps). */
    val hiddenPackages: StateFlow<Set<String>> = HiddenApps.hidden(application).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptySet(),
    )

    /** The user's subscribed news feeds (left feed discover section). */
    val feedSources: StateFlow<List<FeedSource>> = feedStore.data
        .map { it.sources }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DEFAULT_FEED_SOURCES,
        )

    /** Emitted when the user presses Home while already on Start. */
    private val _homeRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homeRequests: SharedFlow<Unit> = _homeRequests

    // Reorder commits are debounced (S19): a flurry of drops coalesces into one
    // transactional write of the latest order. Buffered + DROP_OLDEST so a burst
    // never suspends the caller, and the freshest order always wins.
    private val reorderRequests = MutableSharedFlow<List<String>>(
        extraBufferCapacity = 8,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )

    /** True once the App-list page is the committed page. */
    private val _isAppList = MutableStateFlow(false)
    val isAppList: StateFlow<Boolean> = _isAppList.asStateFlow()

    /**
     * Whether the Start⇄App-list swipe is allowed. Flipped off by edit mode
     * and by open overlays (S16) once those land.
     */
    private val _swipeEnabled = MutableStateFlow(true)
    val swipeEnabled: StateFlow<Boolean> = _swipeEnabled.asStateFlow()

    /** True while the Start grid is in tile-edit mode (FR-3.1). */
    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    /** The tile currently selected for editing (shows corner controls), if any. */
    private val _selectedTileId = MutableStateFlow<String?>(null)
    val selectedTileId: StateFlow<String?> = _selectedTileId.asStateFlow()

    /** The id of the folder whose full-screen overlay is open (FR-4), or null. */
    private val _openFolderId = MutableStateFlow<String?>(null)
    val openFolderId: StateFlow<String?> = _openFolderId.asStateFlow()

    /** True while the personalize sheet is open (edit bar → personalize, FR-7). */
    private val _personalizeOpen = MutableStateFlow(false)
    val personalizeOpen: StateFlow<Boolean> = _personalizeOpen.asStateFlow()

    /** True while the about sheet is open (personalize → about). */
    private val _aboutOpen = MutableStateFlow(false)
    val aboutOpen: StateFlow<Boolean> = _aboutOpen.asStateFlow()

    /** True while the layout history sheet is open (personalize → layout history). */
    private val _historyOpen = MutableStateFlow(false)
    val historyOpen: StateFlow<Boolean> = _historyOpen.asStateFlow()

    /** True while the backup & restore sheet is open (personalize → manage backups). */
    private val _backupOpen = MutableStateFlow(false)
    val backupOpen: StateFlow<Boolean> = _backupOpen.asStateFlow()

    /** Rolling history of the last 10 layout snapshots (newest first). */
    val layoutHistory: StateFlow<List<LayoutSnapshot>> = historyRepository.snapshots.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** True while the category-folders sheet is open (personalize → folders). */
    private val _foldersOpen = MutableStateFlow(false)
    val foldersOpen: StateFlow<Boolean> = _foldersOpen.asStateFlow()

    /** True while the hidden-apps sheet is open (personalize → hidden apps). */
    private val _hiddenAppsOpen = MutableStateFlow(false)
    val hiddenAppsOpen: StateFlow<Boolean> = _hiddenAppsOpen.asStateFlow()

    /** True while quick search is open (two-finger swipe-down on Start). */
    private val _searchOpen = MutableStateFlow(false)
    val searchOpen: StateFlow<Boolean> = _searchOpen.asStateFlow()

    fun setAppList(value: Boolean) {
        _isAppList.value = value
    }

    fun setSwipeEnabled(value: Boolean) {
        _swipeEnabled.value = value
    }

    /**
     * Enter edit mode with [tileId] selected (FR-3.1, fired by the 430 ms tile
     * long-press). Disables the pager swipe and pauses live-tile animations
     * (the latter is a no-op until `:feature:livetiles` is wired into Start).
     */
    fun enterEdit(tileId: String) {
        _selectedTileId.value = tileId
        _editMode.value = true
        _swipeEnabled.value = false
    }

    /**
     * Leave edit mode via any exit path (done, empty-space tap, plain tile tap,
     * Home or Back). Re-enables the swipe and resumes live-tile animations
     * (no-op for now). Safe to call when not editing.
     */
    fun exitEdit() {
        if (!_editMode.value) return
        _editMode.value = false
        _selectedTileId.value = null
        _swipeEnabled.value = true
    }

    /** Removes tiles whose app was uninstalled while we were running. */
    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
            packageName?.let(::prunePackage)
        }

        override fun onPackagesUnavailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean,
        ) {
            if (!replacing) packageNames?.forEach(::prunePackage)
        }

        override fun onPackageAdded(packageName: String?, user: UserHandle?) = Unit
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = Unit
        override fun onPackagesAvailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean,
        ) = Unit
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val debouncedReorders = reorderRequests.debounce(REORDER_DEBOUNCE_MS)

    init {
        viewModelScope.launch(writeContext) { repository.seedIfEmpty() }
        // Pull in any news feeds/categories added in a newer app version (DataStore
        // keeps the first-seen list, so new defaults like state/entertainment need
        // an explicit reconcile to appear in existing installs).
        viewModelScope.launch(Dispatchers.IO) { feedStore.reconcileDefaults() }
        viewModelScope.launch(writeContext) {
            debouncedReorders.collect { repository.reorderTiles(it) }
        }
        launcherApps.registerCallback(packageCallback, Handler(Looper.getMainLooper()))
    }

    /**
     * Open the full-screen overlay for a folder tile (FR-4). Disables the pager
     * swipe while it is up.
     */
    fun openFolder(id: String) {
        _openFolderId.value = id
        _swipeEnabled.value = false
    }

    /** Close the folder overlay and re-enable the swipe. Safe when none is open. */
    fun closeFolder() {
        if (_openFolderId.value == null) return
        _openFolderId.value = null
        _swipeEnabled.value = true
    }

    /** Open the personalize sheet (FR-7). Reachable from the edit bar. */
    fun openPersonalize() {
        _personalizeOpen.value = true
    }

    /** Close the personalize sheet. Safe when none is open. */
    fun closePersonalize() {
        _personalizeOpen.value = false
    }

    /** Open the about sheet (personalize → about). */
    fun openAbout() {
        _aboutOpen.value = true
    }

    /** Close the about sheet. */
    fun closeAbout() {
        _aboutOpen.value = false
    }

    /** Open the layout history sheet (personalize → history). */
    fun openHistory() { _historyOpen.value = true }

    /** Close the layout history sheet. */
    fun closeHistory() { _historyOpen.value = false }

    /** Open the backup & restore sheet (personalize → manage backups). */
    fun openBackup() { _backupOpen.value = true }

    /** Close the backup & restore sheet. */
    fun closeBackup() { _backupOpen.value = false }

    /** Open the category-folders sheet (personalize → folders). */
    fun openFolders() {
        _foldersOpen.value = true
    }

    /** Close the category-folders sheet. */
    fun closeFolders() {
        _foldersOpen.value = false
    }

    /** Open the hidden-apps sheet (personalize → hidden apps). */
    fun openHiddenApps() {
        _hiddenAppsOpen.value = true
    }

    /** Close the hidden-apps sheet. */
    fun closeHiddenApps() {
        _hiddenAppsOpen.value = false
    }

    /** Open quick search (two-finger swipe-down on Start). Disables the pager swipe. */
    fun openSearch() {
        _searchOpen.value = true
        _swipeEnabled.value = false
    }

    /** Close quick search and re-enable the swipe. Safe when not open. */
    fun closeSearch() {
        if (!_searchOpen.value) return
        _searchOpen.value = false
        _swipeEnabled.value = true
    }

    /** Unhide [packageName], returning it to the app list. */
    fun unhide(packageName: String) {
        viewModelScope.launch { HiddenApps.unhide(getApplication(), packageName) }
    }

    /**
     * Create a folder named [name] holding [apps] on the Start grid (category
     * folders). No-op when [apps] is empty. The sheet stays open so the user
     * can create additional category folders without reopening it.
     */
    fun createFolder(name: String, apps: List<AppEntry>) {
        if (apps.isEmpty()) return
        val folderName = name.trim().ifEmpty { "folder" }
        viewModelScope.launch(writeContext) {
            repository.createFolder(folderName, apps)
        }
    }


    /** Switch theme (FR-7); persisted and applied live. */
    fun setFollowSystemTheme(follow: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setFollowSystemTheme(follow) }
    }

    fun setTheme(dark: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setDark(dark) }
    }

    /** Set the global accent colour (FR-7); persisted and applied live. */
    fun setAccent(accentId: String) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setAccent(accentId) }
    }

    /** Toggle transparent-tile ("glass") mode (FR-7). */
    fun setGlass(glass: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setGlass(glass) }
    }

    /** Set the tile-transparency slider value 0..1 (FR-7). */
    fun setTransparency(transparency: Float) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setTransparency(transparency) }
    }

    /** Toggle the blur-wallpaper effect (FR-7). */
    fun setBlur(blur: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setBlur(blur) }
    }

    /** Remove all wallpaper (shows the theme bg colour). */
    fun clearWallpaper() {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.clearWallpaper() }
    }

    /** Select a bundled gradient wallpaper, clearing any custom photo (FR-7). */
    fun setWallpaper(wallpaperId: String) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setWallpaper(wallpaperId) }
    }

    /** Persist a user-picked custom wallpaper URI and its crop alignment (FR-7). */
    fun setCustomWallpaper(uri: String, alignX: Float = 0.5f, alignY: Float = 0.5f) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setCustomWallpaper(uri, alignX, alignY)
        }
    }

    /**
     * Turn the Microsoft Bing daily wallpaper on or off. Enabling flips the setting,
     * schedules the daily refresh and kicks an immediate download; disabling clears the
     * image (reverting to the gradient) and cancels the work.
     */
    fun setBingWallpaper(on: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setBingWallpaper(on) }
        val context = getApplication<Application>()
        if (on) {
            com.tileshell.feature.livetiles.BingWallpaperWorker.ensureScheduled(context)
            com.tileshell.feature.livetiles.BingWallpaperWorker.refreshNow(context)
        } else {
            com.tileshell.feature.livetiles.BingWallpaperWorker.cancel(context)
        }
    }

    /** Update only the focal-point alignment of the active custom/Bing wallpaper. */
    fun setWallpaperAlignment(alignX: Float, alignY: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setWallpaperAlignment(alignX, alignY)
        }
    }

    /**
     * Pin a specific Bing image (chosen from the history viewer) as the wallpaper.
     * Downloads it off-thread via the worker and sets it as a fixed custom wallpaper,
     * which turns daily auto-refresh off.
     */
    fun applyBingImage(imageUrl: String) {
        com.tileshell.feature.livetiles.BingWallpaperWorker.applyImage(getApplication(), imageUrl)
    }

    /** Toggle "wallpaper behind tiles" mode (dark screen, show-through tiles). */
    fun setTiledWallpaper(on: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setTiledWallpaper(on) }
    }

    /** Toggle the left "feed" page (the 3rd pager page reached by swiping right). */
    fun setFeedEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setFeedEnabled(enabled) }
    }

    /** Set the tile corner radius 0–12 dp. */
    fun setCornerRadius(radius: Float) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setCornerRadius(radius) }
    }

    /** Set the inter-tile gap (0–16 dp). */
    fun setTileGap(gap: Float) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setTileGap(gap) }
    }

    /** Switch the default tile colour source (global accent vs app-icon colour). */
    fun setTileColorSource(source: com.tileshell.core.data.settings.TileColorSource) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setTileColorSource(source) }
    }

    /** Reset the tile-style controls (corners, spacing, columns, fill, colour, font). */
    fun resetTileStyle() {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.resetTileStyle() }
    }

    /** Switch tile fill between flat and gradient. */
    fun setTileFill(fill: com.tileshell.core.data.settings.TileFill) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setTileFill(fill) }
    }

    /** Switch the UI font style. */
    fun setFontStyle(style: com.tileshell.core.data.settings.FontStyle) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setFontStyle(style) }
    }

    /** Set the Start grid column count (4, 5, or 6 small-tile columns). */
    fun setColumns(columns: Int) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setColumns(columns) }
        // The 3×3 large size is only allowed on 5/6-column grids; dropping to 4
        // auto-shrinks any large tile back to medium.
        if (columns < 5) {
            viewModelScope.launch(writeContext) { repository.demoteLargeTiles() }
        }
    }

    /** Subscribe a custom RSS/Atom feed and refresh so its articles appear soon. */
    fun addFeedSource(url: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            feedStore.addSource(url, name)
            FeedRefreshWorker.refreshNow(getApplication())
        }
    }

    /** Remove a subscribed feed. */
    fun removeFeedSource(url: String) {
        viewModelScope.launch(Dispatchers.IO) { feedStore.removeSource(url) }
    }

    /** Enable/disable a subscribed feed; refreshes so the discover list rebuilds. */
    fun setFeedSourceEnabled(url: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            feedStore.setEnabled(url, enabled)
            FeedRefreshWorker.refreshNow(getApplication())
        }
    }

    /** Enable/disable a whole news category; refreshes so the discover list rebuilds. */
    fun setFeedCategoryEnabled(category: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            feedStore.setCategoryEnabled(category, enabled)
            FeedRefreshWorker.refreshNow(getApplication())
        }
    }

    /** Re-add a deleted default live tile (clock/weather/calendar) to the grid. */
    fun addLiveTile(appId: String) {
        viewModelScope.launch(writeContext) { repository.addDefaultTile(appId) }
    }

    /** Force a manual news refresh (the feed's refresh action). */
    fun refreshFeeds() {
        FeedRefreshWorker.refreshNow(getApplication())
    }

    /** Reset the Start grid to the WP default layout (FR-7). */
    fun resetLayout() {
        viewModelScope.launch(writeContext) { repository.resetLayout() }
    }

    /** Rename the open folder (FR-4). Blank/whitespace names are ignored. */
    fun renameFolder(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(writeContext) { repository.renameFolder(id, trimmed) }
    }

    /**
     * Remove one app from a folder (FR-4). The folder dissolves to a plain tile
     * when a single app remains (the overlay then self-closes as the folder model
     * disappears) and vanishes when empty.
     */
    fun removeFolderChild(folderId: String, child: FolderChild) {
        viewModelScope.launch(writeContext) { repository.removeFolderChild(folderId, child) }
    }

    /**
     * Resize a folder child. On a 5/6-column grid it cycles the full small→
     * medium→wide→large steps (same as a top-level tile); on 4 columns it keeps
     * the tighter small↔medium toggle. A LARGE child is a widget-stack member, so
     * resizing it collapses the stack back to a normal folder (handled in the
     * repository) regardless of column count.
     */
    fun resizeFolderChild(folderId: String, child: FolderChild) {
        val largeAllowed = settings.value.columns >= 5
        viewModelScope.launch(writeContext) {
            repository.resizeFolderChild(folderId, child, largeAllowed)
        }
    }

    /**
     * Turn a folder into a widget stack in one shot: every child resized to
     * [size] (WIDE or LARGE), the folder tile matching.
     */
    fun convertFolderToStack(folderId: String, size: TileSize) {
        viewModelScope.launch(writeContext) { repository.convertFolderToStack(folderId, size) }
    }

    /** Set or clear a folder child's own accent override (null = follow global, FR-7). */
    fun setFolderChildAccent(child: FolderChild, colorId: String?) {
        viewModelScope.launch(writeContext) { repository.setFolderChildAccent(child.rowId, colorId) }
    }

    /** Persist a new display order for folder children after an in-folder drag. */
    fun reorderFolderChildren(orderedChildren: List<FolderChild>) {
        viewModelScope.launch(writeContext) {
            repository.reorderFolderChildren(orderedChildren.map { it.rowId })
        }
    }

    /**
     * Home pressed on Start: closes the folder overlay (FR-4), leaves edit mode
     * (FR-3.1) and asks the screen to collapse the pager and scroll to the top.
     */
    fun goHome() {
        closePersonalize()
        closeAbout()
        closeFolders()
        closeHiddenApps()
        closeBackup()
        closeFolder()
        closeSearch()
        exitEdit()
        _homeRequests.tryEmit(Unit)
    }

    /**
     * Persist a new tile order after an edit-mode drag-to-reorder (FR-3.2). The
     * write is debounced ([debouncedReorders]) so rapid commits coalesce.
     */
    fun reorder(orderedIds: List<String>) {
        reorderRequests.tryEmit(orderedIds)
    }

    /**
     * Cycle the tile's size (FR-3.4 resize): medium → small → wide → medium. Music
     * and news app tiles on a 5/6-column grid also get the 3×3 large step
     * ([AppCategories.allowsLargeTile]).
     */
    fun resize(id: String) {
        val model = tiles.value.firstOrNull { it.id == id }
        // A widget stack stays a fixed 3×3 — don't run the folder tile through the
        // resize cycle (that would shrink the stack's footprint). Members are
        // resized individually inside the folder overlay instead.
        if (model is TileModel.Folder && model.isStack) return
        val tile = model as? TileModel.App
        val largeAllowed = tile != null && AppCategories.allowsLargeTile(
            iconKey = tile.iconKey,
            app = apps.value.firstOrNull { it.packageName == tile.packageName },
            columns = settings.value.columns,
        )
        viewModelScope.launch(writeContext) { repository.cycleTileSize(id, largeAllowed) }
    }

    /** Set or clear a tile's per-tile accent override (null = follow global, FR-7). */
    fun setTileColor(id: String, colorId: String?) {
        viewModelScope.launch(writeContext) { repository.setTileAccent(id, colorId) }
    }

    /** Unpin (remove) a tile from the Start grid (FR-3.5). */
    fun unpin(id: String) {
        viewModelScope.launch(writeContext) { repository.removeTile(id) }
    }

    /**
     * Merge the dragged tile onto the target, forming/growing a folder (FR-3.3).
     * [survivingOrder] is the working order after the dragged tile is removed, so
     * any reorder incurred during the drag is persisted with the merge.
     */
    fun merge(dragId: String, targetId: String, survivingOrder: List<String>) {
        viewModelScope.launch(writeContext) {
            repository.mergeTiles(dragId, targetId, survivingOrder)
        }
    }

    // One-shot toast messages emitted after an export/import completes (or fails).
    private val _backupMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val backupMessage: SharedFlow<String> = _backupMessage.asSharedFlow()

    // One-shot toast messages emitted after a "pin to start" action from quick search.
    private val _pinMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val pinMessage: SharedFlow<String> = _pinMessage.asSharedFlow()

    /** Pin a contact to Start from quick search's "pin to start" action. */
    fun pinContact(contactId: Long, lookupKey: String, name: String) {
        viewModelScope.launch(writeContext) {
            val result = repository.pinContact(contactId, lookupKey, name)
            _pinMessage.tryEmit(
                when (result) {
                    PinResult.PINNED -> "pinned $name to start"
                    PinResult.ALREADY_ON_START -> "already on start"
                },
            )
        }
    }

    /** Export the current layout + settings to the SAF URI chosen by the user. */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val (tiles, folders, children) = repository.tilesForBackup()
                val currentSettings = settingsRepository.settings.first()
                val json = BackupManager.buildBackupJson(tiles, folders, children, currentSettings)
                getApplication<Application>().contentResolver
                    .openOutputStream(uri)?.use { it.write(json.encodeToByteArray()) }
                _backupMessage.tryEmit("backup saved")
            }.onFailure {
                _backupMessage.tryEmit("export failed")
            }
        }
    }

    /** Import a layout + settings backup from the SAF URI chosen by the user. */
    fun importBackup(uri: Uri) {
        viewModelScope.launch(writeContext) {
            runCatching {
                val json = getApplication<Application>().contentResolver
                    .openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    ?: error("could not read backup file")
                val backup = BackupManager.parseBackup(json)
                repository.restoreFromBackup(backup.tiles, backup.folders, backup.folderChildren)
                settingsRepository.restoreSettings(backup.settings)
                _backupMessage.tryEmit("layout restored")
            }.onFailure {
                _backupMessage.tryEmit("restore failed")
            }
        }
    }

    /** Manually save the current layout to the rolling history. */
    fun saveLayoutSnapshot(id: String = System.currentTimeMillis().toString(), screenshotPath: String? = null) {
        viewModelScope.launch(writeContext) {
            runCatching {
                val (tiles, folders, children) = repository.tilesForBackup()
                val currentSettings = settingsRepository.settings.first()
                val json = BackupManager.buildBackupJson(tiles, folders, children, currentSettings)
                val hash = BackupManager.layoutHash(tiles, folders, children)
                val ts = id.toLongOrNull() ?: System.currentTimeMillis()
                historyRepository.addSnapshot(
                    LayoutSnapshot(
                        id = id,
                        timestamp = ts,
                        label = "manual",
                        tileCount = tiles.size,
                        folderCount = folders.size,
                        contentHash = hash,
                        json = json,
                        screenshotPath = screenshotPath,
                    )
                )
                _backupMessage.tryEmit("snapshot saved")
            }.onFailure { _backupMessage.tryEmit("save failed") }
        }
    }

    /**
     * Cache a screenshot taken opportunistically while Start was on-screen (e.g. on
     * ON_STOP), keyed to the current layout's content hash, so the headless auto-backup
     * worker — which has no window to PixelCopy from — can reuse it later.
     */
    fun cacheForegroundScreenshot(path: String) {
        viewModelScope.launch(writeContext) {
            runCatching {
                val app = getApplication<Application>()
                val (tiles, folders, children) = repository.tilesForBackup()
                val hash = BackupManager.layoutHash(tiles, folders, children)
                val previous = CachedScreenshotPrefs.currentPath(app)
                CachedScreenshotPrefs.save(app, path, hash)
                // Clean up the file we're superseding, unless a saved history entry still
                // points at it (a manual/auto snapshot may have captured it permanently).
                if (previous != null && previous != path) {
                    val stillReferenced = historyRepository.snapshots.first().any { it.screenshotPath == previous }
                    if (!stillReferenced) java.io.File(previous).delete()
                }
            }
        }
    }

    /** Restore a layout snapshot from the history. */
    fun restoreFromSnapshot(snapshot: LayoutSnapshot) {
        viewModelScope.launch(writeContext) {
            runCatching {
                val backup = BackupManager.parseBackup(snapshot.json)
                repository.restoreFromBackup(backup.tiles, backup.folders, backup.folderChildren)
                settingsRepository.restoreSettings(backup.settings)
                _backupMessage.tryEmit("layout restored")
            }.onFailure { _backupMessage.tryEmit("restore failed") }
        }
    }

    /** Delete a snapshot from the history by its id; also removes its screenshot file. */
    fun deleteSnapshot(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                java.io.File(getApplication<android.app.Application>().filesDir, "snapshots/snapshot_$id.jpg").delete()
            }
            historyRepository.deleteSnapshot(id)
        }
    }

    /** Persist the auto-backup enabled state and re-schedule (or cancel) the worker. */
    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setAutoBackupEnabled(enabled)
        }
    }

    fun setAutoBackupInterval(hours: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setAutoBackupIntervalHours(hours)
        }
    }

    private fun prunePackage(packageName: String) {
        viewModelScope.launch(writeContext) { repository.removeApp(packageName) }
    }

    override fun onCleared() {
        launcherApps.unregisterCallback(packageCallback)
    }

    private companion object {
        /** Coalesce window for reorder commits (small enough to be invisible). */
        const val REORDER_DEBOUNCE_MS = 120L
    }
}
