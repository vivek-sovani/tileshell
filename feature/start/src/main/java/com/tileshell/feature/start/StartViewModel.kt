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
import com.tileshell.core.data.settings.TilePackMode
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

    /** True while the how-to-personalize guide sheet is open (personalize → guide). */
    private val _personalizeGuideOpen = MutableStateFlow(false)
    val personalizeGuideOpen: StateFlow<Boolean> = _personalizeGuideOpen.asStateFlow()

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

    /** True while the edge-strip settings sheet is open (personalize → edge strip). */
    private val _edgeStripOpen = MutableStateFlow(false)
    val edgeStripOpen: StateFlow<Boolean> = _edgeStripOpen.asStateFlow()

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
        viewModelScope.launch(writeContext) {
            repository.seedIfEmpty()
            // Sticky mode is the fresh-install default (LauncherSettings), so the
            // very first layout needs its anchors seeded here too — not only on
            // an explicit user toggle (see seedStickySlots).
            val initialSettings = settingsRepository.settings.first()
            if (initialSettings.tilePackMode == TilePackMode.STICKY) {
                seedStickySlots(initialSettings.columns)
            }
        }
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

    /** Open the how-to-personalize guide sheet (personalize → guide). */
    fun openPersonalizeGuide() {
        _personalizeGuideOpen.value = true
    }

    /** Close the how-to-personalize guide sheet. */
    fun closePersonalizeGuide() {
        _personalizeGuideOpen.value = false
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
    fun openEdgeStrip() { _edgeStripOpen.value = true }
    fun closeEdgeStrip() { _edgeStripOpen.value = false }

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

    /** Persist a user-picked custom wallpaper URI and its crop alignment/zoom (FR-7). */
    fun setCustomWallpaper(uri: String, alignX: Float = 0.5f, alignY: Float = 0.5f, zoom: Float = 1f) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setCustomWallpaper(uri, alignX, alignY, zoom)
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

    /** Update only the focal-point alignment/zoom of the active custom/Bing wallpaper. */
    fun setWallpaperAlignment(alignX: Float, alignY: Float, zoom: Float = 1f) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setWallpaperAlignment(alignX, alignY, zoom)
        }
    }

    /**
     * Turn the wallpaper slideshow on or off (rotates through `WallpaperSlideshowStore`'s
     * photos on a timer instead of one fixed photo; mutually exclusive with Bing).
     * Enabling schedules the periodic rotation and — if photos are already picked —
     * applies the first one immediately, same "instant feedback" as picking a single
     * custom wallpaper; disabling cancels the rotation and leaves the current photo
     * showing (mirrors [setBingWallpaper]'s own on/off split).
     */
    fun setWallpaperSlideshowEnabled(on: Boolean) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setWallpaperSlideshowEnabled(on)
            if (on) {
                val uris = com.tileshell.feature.livetiles.WallpaperSlideshowStore.create(context).read().uris
                if (uris.isNotEmpty()) settingsRepository.setWallpaperSlide(uris.first(), 0)
            }
        }
        if (on) {
            com.tileshell.feature.livetiles.WallpaperSlideshowWorker.ensureScheduled(
                context, settings.value.wallpaperSlideshowIntervalMin,
            )
        } else {
            com.tileshell.feature.livetiles.WallpaperSlideshowWorker.cancel(context)
        }
    }

    /** Set the slideshow rotation interval in minutes; reschedules immediately if already on. */
    fun setWallpaperSlideshowInterval(minutes: Int) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setWallpaperSlideshowInterval(minutes) }
        if (settings.value.wallpaperSlideshowEnabled) {
            com.tileshell.feature.livetiles.WallpaperSlideshowWorker.ensureScheduled(getApplication(), minutes)
        }
    }

    /** Apply [uri] at [index] as the current slideshow photo (used when photos are (re)picked). */
    fun setWallpaperSlide(uri: String, index: Int) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setWallpaperSlide(uri, index) }
    }

    /**
     * Pin a specific Bing image (chosen from the history viewer) as the wallpaper.
     * Downloads it off-thread via the worker; stays in Bing mode (daily auto-refresh
     * keeps running and will replace it on the next scheduled run).
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
    }

    /**
     * Switch the Start grid's gap-closing behaviour. Turning STICKY on seeds a
     * grid cell only for tiles that have never been anchored (gridSlot == null),
     * placed via [GridPacker.packSticky] around whatever is already anchored —
     * so re-enabling sticky mode after a round-trip through dense mode doesn't
     * discard an arrangement the user already built, and on first-ever use (every
     * tile unanchored) this reduces to the current dense-packed layout, so the
     * switch is visually seamless.
     */
    fun setTilePackMode(mode: TilePackMode) {
        viewModelScope.launch(writeContext) {
            if (mode == TilePackMode.STICKY) seedStickySlots(settings.value.columns)
            settingsRepository.setTilePackMode(mode)
        }
    }

    /**
     * Anchor every currently-unslotted tile at its present (dense-packed)
     * cell — called both when the user explicitly switches sticky mode on
     * and once at startup if it's *already* the active mode (the fresh-install
     * default). Without this, a tile that's never been dragged has no gridSlot
     * at all, and an all-unanchored layout has nothing to hold anyone's
     * position in place — every tile "floats," so `packSticky` re-derives
     * everyone's cell fresh via the same append-only fallback dense packing
     * uses, and the grid reads as plain auto-arrange (reported as "first time
     * it behaves like auto-arrange") until something finally gets anchored —
     * which an explicit off-then-on toggle happened to trigger as a side
     * effect, masking the gap in the fresh-install case.
     */
    private suspend fun seedStickySlots(columns: Int) {
        val current = repository.tiles.first()
        val unslotted = current.filter { it.gridSlot == null }.mapTo(HashSet()) { it.id }
        if (unslotted.isEmpty()) return
        val specs = current.map { TileSpec(it.id, it.size) }
        val slotOf: (String) -> Int? = { id -> current.firstOrNull { it.id == id }?.gridSlot }
        val placements = GridPacker.packSticky(specs, slotOf, columns)
        placements.filter { it.id in unslotted }.forEach { p ->
            repository.setTileGridSlot(p.id, GridPacker.encodeSlot(p.col, p.row))
        }
    }

    /**
     * Anchor a tile at a grid cell after a sticky-mode drag-drop (FR-3.2 WP
     * variant). A full row gap is never allowed: if leaving the tile's old
     * cell empties out a whole row, [collapseEmptyRowsAfterMove] shifts
     * everything below it up to close the row.
     */
    fun setTileGridSlot(id: String, slot: Int?) {
        if (slot == null) return
        val finalSlots = collapseEmptyRowsAfterMove(id, slot)
        viewModelScope.launch(writeContext) {
            finalSlots.forEach { (tid, s) -> repository.setTileGridSlot(tid, s) }
        }
    }

    /**
     * Every anchored tile's cell after [movedId] takes on [movedSlot] (all
     * others keep their current cell), with any resulting fully-empty row
     * collapsed ([GridPacker.collapseEmptyRows]) — the tiles whose row must
     * actually change, including [movedId] itself. Empty (not a map entry) for
     * a tile that doesn't move. Dense mode never calls this.
     */
    private fun collapseEmptyRowsAfterMove(movedId: String, movedSlot: Int): Map<String, Int> {
        val current = tiles.value
        val projected = current.mapNotNull { t ->
            val slot = if (t.id == movedId) movedSlot else t.gridSlot
            slot?.let {
                TilePlacement(t.id, t.size, GridPacker.decodeSlotCol(it), GridPacker.decodeSlotRow(it))
            }
        }
        val collapse = GridPacker.collapseEmptyRows(projected)
        return if (movedId in collapse) collapse else collapse + (movedId to movedSlot)
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
     * Resize a folder child. Cycles the full small→medium→wide→large steps (same
     * as a top-level tile — see [AppCategories.allowsLargeTile]). A LARGE child is
     * a widget-stack member, so resizing it collapses the stack back to a normal
     * folder (handled in the repository).
     */
    fun resizeFolderChild(folderId: String, child: FolderChild) {
        val largeAllowed = AppCategories.allowsLargeTile(
            iconKey = child.iconKey,
            app = apps.value.firstOrNull { it.packageName == child.packageName },
            columns = settings.value.columns,
        )
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
        closePersonalizeGuide()
        closeFolders()
        closeHiddenApps()
        closeBackup()
        closeEdgeStrip()
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
     * Cycle the tile's size (FR-3.4 resize): medium → small → wide → medium. Any
     * app tile, on any grid density, also gets the 3×3 large step
     * ([AppCategories.allowsLargeTile]).
     */
    fun resize(id: String) {
        val model = tiles.value.firstOrNull { it.id == id }
        // A widget stack stays a fixed 3×3 — don't run the folder tile through the
        // resize cycle (that would shrink the stack's footprint). Members are
        // resized individually inside the folder overlay instead.
        if (model is TileModel.Folder && model.isStack) return
        // A plain (non-stack) folder gets the same small→medium→wide→large cycle
        // as an app tile — a bigger mini-grid is useful for a folder holding many
        // apps. This is independent of the widget-stack mechanism: `isStack` only
        // turns true when every *child* is uniformly WIDE/LARGE, so a large
        // folder whose children aren't all large stays a normal (bigger) folder.
        val largeAllowed = when (model) {
            is TileModel.App -> AppCategories.allowsLargeTile(
                iconKey = model.iconKey,
                app = apps.value.firstOrNull { it.packageName == model.packageName },
                columns = settings.value.columns,
            )
            is TileModel.Folder -> true
            null -> false
        }
        // Sticky mode (FR-3.4 WP variant): an anchored tile stays put, so growing
        // its footprint can collide with a neighbor that dense mode would've just
        // reflowed around. First cut blocked the resize outright on any overlap
        // (failed almost everywhere in a normally tightly-packed layout), then
        // un-anchored the colliding tile entirely (flung it away to the bottom
        // instead of staying nearby). Both reported as wrong — a directly
        // adjacent tile should stay adjacent. Now: any tile(s) the new footprint
        // would overlap are pushed straight down (same column, just below the
        // resized tile's new bottom edge) rather than un-anchored, cascading to
        // whatever they in turn now overlap — the resized tile always succeeds,
        // and neighbors move the minimum needed to stay out of the way while
        // staying right where they were otherwise.
        val nextSize = model?.size?.next(largeAllowed)
        val finalSlots = if (model != null && nextSize != null) {
            stickyResizeSlots(model, nextSize)
        } else {
            emptyMap()
        }
        viewModelScope.launch(writeContext) {
            finalSlots.forEach { (movedId, slot) -> repository.setTileGridSlot(movedId, slot) }
            repository.cycleTileSize(id, largeAllowed)
        }
    }

    /**
     * All grid-cell writes sticky mode needs for [model] to resize to
     * [nextSize]: the resized tile's own cell (its column shifts left just
     * enough to keep the new, wider footprint inside the grid when it no
     * longer fits starting at its original column — e.g. growing to WIDE from
     * anywhere but column 0 always overflows otherwise; its row never moves
     * for its own sake), every tile the resulting footprint displaces (pushed
     * straight down, cascading until nothing overlaps), and any fully-empty
     * row that leaves behind, collapsed. Always empty in dense mode or for a
     * never-anchored tile.
     *
     * Before the column shift: any tile not already at column 0 that grew
     * wider than the room to its right (most commonly resizing up to WIDE)
     * hit an "impossible at this column" bail-out with no fallback other than
     * leaving the DB's position/size alone — [GridPacker.packSticky] then
     * couldn't place it at its stored, now-too-narrow cell and silently
     * re-flowed it to the first free cell after the bottom row instead, which
     * read as "resize teleports the tile away." It only showed up when there
     * was a tile to the left holding this one off column 0 — one already at
     * column 0 never needed the shift, so never hit the bug.
     */
    private fun stickyResizeSlots(model: TileModel, nextSize: TileSize): Map<String, Int> {
        if (settings.value.tilePackMode != TilePackMode.STICKY) return emptyMap()
        val ownSlot = model.gridSlot ?: return emptyMap()
        val columns = settings.value.columns
        val row = GridPacker.decodeSlotRow(ownSlot)
        val w = nextSize.cols.coerceAtMost(columns)
        val h = nextSize.rows
        val effectiveCol = GridPacker.decodeSlotCol(ownSlot).coerceAtMost((columns - w).coerceAtLeast(0))
        val effectiveSlot = GridPacker.encodeSlot(effectiveCol, row)

        val pushDown = stickyPushDown(model.id, effectiveCol, row, w, h, columns)
        // A full row gap is never allowed: pushing neighbors down (or shifting
        // this tile's own column) can leave a row fully empty, so close it,
        // shifting everything below up (may further adjust the just-pushed
        // tiles, and even the resized tile itself if there's headroom to
        // reclaim above it).
        val projected = tiles.value.mapNotNull { t ->
            val (size, slot) = when {
                t.id == model.id -> nextSize to effectiveSlot
                pushDown.containsKey(t.id) -> t.size to pushDown.getValue(t.id)
                else -> t.size to (t.gridSlot ?: return@mapNotNull null)
            }
            TilePlacement(t.id, size, GridPacker.decodeSlotCol(slot), GridPacker.decodeSlotRow(slot))
        }
        val collapse = GridPacker.collapseEmptyRows(projected)
        // The resized tile's own cell always needs writing — its column may
        // have shifted even when collapse found no row to close.
        val ownFinal = collapse[model.id] ?: effectiveSlot
        return pushDown + collapse + (model.id to ownFinal)
    }

    /**
     * New grid cells for every anchored tile that a resized footprint at
     * ([col], [row], [w] x [h]) would displace, in sticky mode: each is pushed
     * straight down (same column) to sit just below whatever it now overlaps,
     * cascading until nothing overlaps. [excludeId] (the resizing tile itself)
     * is never in the returned map — the caller already knows its own cell.
     */
    private fun stickyPushDown(excludeId: String, col: Int, row: Int, w: Int, h: Int, columns: Int): Map<String, Int> {
        data class Box(val id: String, val col: Int, var row: Int, val w: Int, val h: Int)
        fun overlaps(a: Box, b: Box) =
            a.col < b.col + b.w && b.col < a.col + a.w && a.row < b.row + b.h && b.row < a.row + a.h

        val resized = Box(excludeId, col, row, w, h)
        // Every other *anchored* tile at its current cell — a never-anchored
        // (floating) tile isn't part of the fixed layout, so it's left alone.
        val boxes = tiles.value.filter { it.id != excludeId }.mapNotNull { t ->
            val s = t.gridSlot ?: return@mapNotNull null
            Box(t.id, GridPacker.decodeSlotCol(s), GridPacker.decodeSlotRow(s), t.size.cols.coerceAtMost(columns), t.size.rows)
        }
        val fixed = listOf(resized) + boxes
        val moved = mutableMapOf<String, Int>()
        var settled = false
        var guard = 0
        while (!settled && guard++ <= boxes.size) {
            settled = true
            for (box in boxes) {
                val blockers = fixed.filter { it !== box && overlaps(it, box) }
                if (blockers.isEmpty()) continue
                val newRow = blockers.maxOf { it.row + it.h }
                if (newRow > box.row) {
                    box.row = newRow
                    moved[box.id] = GridPacker.encodeSlot(box.col, box.row)
                    settled = false
                }
            }
        }
        return moved
    }

    /** Set or clear a tile's per-tile accent override (null = follow global, FR-7). */
    fun setTileColor(id: String, colorId: String?) {
        viewModelScope.launch(writeContext) { repository.setTileAccent(id, colorId) }
    }

    /** Unpin (remove) a tile from the Start grid (FR-3.5). */
    fun unpin(id: String) {
        // A full row gap is never allowed: removing a tile can leave its row
        // fully empty, so close it before the removal lands.
        val collapse = collapseEmptyRowsAfterRemoval(id)
        viewModelScope.launch(writeContext) {
            collapse.forEach { (movedId, slot) -> repository.setTileGridSlot(movedId, slot) }
            repository.removeTile(id)
        }
    }

    /**
     * The sticky layout with [removedId] gone, any fully-empty row it leaves
     * behind collapsed. Empty in dense mode.
     */
    private fun collapseEmptyRowsAfterRemoval(removedId: String): Map<String, Int> {
        if (settings.value.tilePackMode != TilePackMode.STICKY) return emptyMap()
        val projected = tiles.value.mapNotNull { t ->
            if (t.id == removedId) return@mapNotNull null
            val slot = t.gridSlot ?: return@mapNotNull null
            TilePlacement(t.id, t.size, GridPacker.decodeSlotCol(slot), GridPacker.decodeSlotRow(slot))
        }
        return GridPacker.collapseEmptyRows(projected)
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
                val hash = BackupManager.layoutHash(tiles, folders, children, currentSettings)
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
                val currentSettings = settingsRepository.settings.first()
                val hash = BackupManager.layoutHash(tiles, folders, children, currentSettings)
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

    fun setEdgeStripEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setEdgeStripEnabled(enabled) }
    }

    fun setEdgeStripPosition(position: String) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setEdgeStripPosition(position) }
    }

    fun setEdgeStripApps(apps: List<String>) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setEdgeStripApps(apps) }
    }

    fun setEdgeStripBackground(bgId: String) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setEdgeStripBackground(bgId) }
    }

    fun setEdgeStripHandleSize(size: String) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setEdgeStripHandleSize(size) }
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
