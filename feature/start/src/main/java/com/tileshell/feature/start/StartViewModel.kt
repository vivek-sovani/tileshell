package com.tileshell.feature.start

import android.Manifest
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tileshell.core.data.AppCatalogRepository
import com.tileshell.core.data.AppCategories
import com.tileshell.core.data.AppEntry
import com.tileshell.core.data.BackupFeedSource
import com.tileshell.core.data.BackupManager
import com.tileshell.core.data.BackupWidget
import com.tileshell.core.data.CachedScreenshotPrefs
import com.tileshell.core.data.FolderChild
import com.tileshell.core.data.HiddenApps
import com.tileshell.core.data.LayoutHistoryRepository
import com.tileshell.core.data.LayoutRepository
import com.tileshell.core.data.LayoutSnapshot
import com.tileshell.core.data.PinResult
import com.tileshell.core.data.SettingsAppMigration
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.settings.LauncherSettings
import com.tileshell.core.data.settings.SettingsRepository
import com.tileshell.core.data.settings.HomeStyle
import com.tileshell.core.data.settings.IconShape
import com.tileshell.core.data.settings.TilePackMode
import com.tileshell.core.data.settings.isAnchored
import com.tileshell.feature.livetiles.DEFAULT_FEED_SOURCES
import com.tileshell.feature.livetiles.FeedRefreshWorker
import com.tileshell.feature.livetiles.FeedSource
import com.tileshell.feature.livetiles.FeedStore
import com.tileshell.feature.livetiles.PhotosStore
import com.tileshell.feature.livetiles.WallpaperSlideshowStore
import com.tileshell.feature.livetiles.queryProfileName
import com.tileshell.feature.start.feed.HostedWidget
import com.tileshell.feature.start.feed.WidgetData
import com.tileshell.feature.start.feed.WidgetStore
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

    /** The active news-region presets (multi-select), for feed settings' region chips. */
    val feedRegions: StateFlow<Set<String>> = feedStore.data
        .map { it.regions }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet(),
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

    /**
     * The id of the folder currently expanded inline on Start (FR-4, WP-style:
     * the folder tile becomes an up-arrow placeholder and its children appear
     * as extra rows right below it, pushing everything under it down), or
     * null. Only one at a time — expanding a different folder collapses this
     * one first.
     */
    private val _expandedFolderId = MutableStateFlow<String?>(null)
    val expandedFolderId: StateFlow<String?> = _expandedFolderId.asStateFlow()

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

    /**
     * An image [Uri] shared into TileShell from another app (e.g. "share" from Gallery/Photos),
     * awaiting import + the crop overlay so the user can position it before it becomes the
     * wallpaper — mirrors the existing wallpaper-picker flow in [StartScreen]. Set by
     * [receiveSharedImage] (called from `MainActivity` when it receives an `ACTION_SEND` intent);
     * cleared once `StartScreen` has copied it into private storage and handed off to its own
     * `pendingWallpaperCropUri` state.
     */
    private val _sharedWallpaperUri = MutableStateFlow<Uri?>(null)
    val sharedWallpaperUri: StateFlow<Uri?> = _sharedWallpaperUri.asStateFlow()

    fun receiveSharedImage(uri: Uri) {
        _sharedWallpaperUri.value = uri
    }

    fun consumeSharedWallpaperUri() {
        _sharedWallpaperUri.value = null
    }

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

    /** True while the permissions sheet is open (personalize → permissions). */
    private val _permissionsOpen = MutableStateFlow(false)
    val permissionsOpen: StateFlow<Boolean> = _permissionsOpen.asStateFlow()

    /** True while the news-region sheet is open (personalize → news region). */
    private val _newsRegionOpen = MutableStateFlow(false)
    val newsRegionOpen: StateFlow<Boolean> = _newsRegionOpen.asStateFlow()

    /** True while quick search is open (two-finger swipe-down on Start). */
    private val _searchOpen = MutableStateFlow(false)
    val searchOpen: StateFlow<Boolean> = _searchOpen.asStateFlow()

    /** True while the edge-strip settings sheet is open (personalize → edge strip). */
    private val _edgeStripOpen = MutableStateFlow(false)
    val edgeStripOpen: StateFlow<Boolean> = _edgeStripOpen.asStateFlow()

    /** True while the quick panel is open (two-finger swipe-up on Start, or its settings-gear-area tap affordance). */
    private val _quickPanelOpen = MutableStateFlow(false)
    val quickPanelOpen: StateFlow<Boolean> = _quickPanelOpen.asStateFlow()

    /**
     * True while the one-shot home-style (tiles vs icons) choice wizard is
     * open — shown once per device: on a genuinely fresh install, and once
     * for an existing install upgrading to the version that introduced ICONS
     * mode (both cases simply have [HomeStyleWizardPrefs.shown] unset, so no
     * version-number check is needed — see its own doc comment). Set in
     * [init], never re-opened once [chooseHomeStyle]/[skipHomeStyleWizard]
     * marks it shown.
     */
    private val _homeStyleWizardOpen = MutableStateFlow(false)
    val homeStyleWizardOpen: StateFlow<Boolean> = _homeStyleWizardOpen.asStateFlow()

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
     * A no-op while [LauncherSettings.lockLayout] is on — this is the single
     * choke point every long-press/edit-mode entry routes through, so gating
     * here blocks all of them at once without touching each call site.
     */
    fun enterEdit(tileId: String) {
        if (settings.value.lockLayout) return
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
            // an explicit user toggle (see seedStickySlots). FREE is anchored the
            // same way STICKY is (see TilePackMode.isAnchored) — without this,
            // a FREE-mode install would silently degenerate to append-only
            // auto-arrange, the exact bug already hit for STICKY.
            val initialSettings = settingsRepository.settings.first()
            if (initialSettings.tilePackMode.isAnchored) {
                seedStickySlots(initialSettings.columns)
            }
            migrateSettingsTile()
            if (!HomeStyleWizardPrefs.shown(getApplication())) {
                _homeStyleWizardOpen.value = true
            }
        }
        // Resolve the news-region preset from the device locale before reconciling
        // (order matters: reconcileDefaults reads FeedData.region, so it must run
        // after seedRegionDefaults has had a chance to set it) — then pull in any
        // news feeds/categories added in a newer app version (DataStore keeps the
        // first-seen list, so new defaults like state/entertainment need an explicit
        // reconcile to appear in existing installs).
        viewModelScope.launch(Dispatchers.IO) {
            feedStore.seedRegionDefaults(java.util.Locale.getDefault().country)
            feedStore.reconcileDefaults()
        }
        // Best-effort: seed the feed greeting's name from the device's own
        // contact profile, same shape as seedRegionDefaults above — only
        // while the setting is still blank, so it never overwrites a name the
        // user has since typed in or deliberately cleared from Personalize.
        // Also re-attempted from seedUserNameFromProfileIfBlank() whenever the
        // caller observes READ_CONTACTS transition to granted, since this
        // init-time attempt races the runtime permission dialog (denied here,
        // granted moments later) and the launcher's ViewModel/process is very
        // long-lived, so a one-shot init-only check may never get a second try.
        seedUserNameFromProfileIfBlank()
        viewModelScope.launch(writeContext) {
            debouncedReorders.collect { repository.reorderTiles(it) }
        }
        launcherApps.registerCallback(packageCallback, Handler(Looper.getMainLooper()))
    }

    /**
     * Best-effort seed of the feed greeting's name from the device's own contact
     * profile. Safe to call repeatedly (e.g. on every READ_CONTACTS grant, or from
     * ON_RESUME) — it only writes when [LauncherSettings.userName] is still blank,
     * so it never overwrites a name the user has since typed in or cleared.
     */
    fun seedUserNameFromProfileIfBlank() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val current = settingsRepository.settings.first()
            if (current.userName.isBlank() &&
                ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                queryProfileName(app)?.let { settingsRepository.setUserName(it) }
            }
        }
    }

    /**
     * Toggle a folder's inline expansion (FR-4, WP-style): tapping a collapsed
     * folder expands it (and collapses whatever else was expanded); tapping
     * the expanded placeholder again collapses it. Unlike the previous modal
     * overlay, this doesn't touch the pager swipe — there's no full-screen
     * surface to protect, so only edit mode gates that (as usual).
     */
    fun toggleFolder(id: String) {
        _expandedFolderId.value = if (_expandedFolderId.value == id) null else id
    }

    /** Collapse whichever folder is expanded. Safe when none is. */
    fun collapseFolder() {
        _expandedFolderId.value = null
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

    /** Open the permissions sheet (personalize → permissions). */
    fun openPermissions() {
        _permissionsOpen.value = true
    }

    /** Close the permissions sheet. */
    fun closePermissions() {
        _permissionsOpen.value = false
    }

    /** Open the news-region sheet (personalize → news region). */
    fun openNewsRegion() {
        _newsRegionOpen.value = true
    }

    /** Close the news-region sheet. */
    fun closeNewsRegion() {
        _newsRegionOpen.value = false
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

    /** Open the quick panel (two-finger swipe-up on Start). Doesn't touch the pager swipe — mirrors openBackup/openPersonalize, not openSearch. */
    fun openQuickPanel() { _quickPanelOpen.value = true }

    /** Close the quick panel. Safe when not open. */
    fun closeQuickPanel() { _quickPanelOpen.value = false }

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

    /** Set the name shown in the feed's "good morning, `<name>`" greeting. */
    fun setUserName(name: String) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setUserName(name) }
    }

    /** Master on/off switch for live-tile flipping/updates. */
    fun setLiveTilesEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setLiveTilesEnabled(enabled) }
    }

    /** Forces the feed/glance screen to a flat background, independent of Start's wallpaper. */
    fun setFeedNoBackground(noBackground: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setFeedNoBackground(noBackground) }
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
            if (mode.isAnchored) seedStickySlots(settings.value.columns)
            settingsRepository.setTilePackMode(mode)
        }
    }

    /**
     * Switch the Start grid's cell renderer (WP tiles ↔ Android-style icons).
     * Rewrites nothing in the layout itself — a tile's stored size is simply
     * ignored while the smaller (icon) renderer is active, so switching back
     * restores the tile layout exactly. The one side effect: entering ICONS
     * for the first time seeds [LauncherSettings.cornerRadius] to a subtle
     * 4dp chamfer *if the user has never touched that slider* (still at its
     * 0f default) — icons render with a proportionally rounded mask
     * regardless of this setting, and a dead-flat 0-radius tile grid sitting
     * next to rounded icons read as visibly unfinished when this was tried.
     * A user who already customized the radius keeps their own value.
     */
    fun setHomeStyle(style: HomeStyle) {
        viewModelScope.launch(writeContext) {
            if (style == HomeStyle.ICONS && settings.value.cornerRadius == LauncherSettings().cornerRadius) {
                settingsRepository.setCornerRadius(ICONS_MODE_DEFAULT_CORNER_RADIUS)
            }
            settingsRepository.setHomeStyle(style)
        }
    }

    /**
     * The first-run wizard's pick: sets [style] via [setHomeStyle] and marks
     * the one-shot wizard flag so it never shows again. Picking ICONS also
     * shrinks the just-seeded default apps down to SMALL (see
     * [shrinkDefaultAppsToIcons]) — user-reported: picking "icons" still
     * showed a Start screen dominated by big live tiles, confusingly unlike
     * what "icons" implied, since [setHomeStyle] deliberately never resizes
     * anything (it's a pure renderer flag) and [DefaultLayout]'s fixed
     * WP-appropriate seed sizes are written well before this choice is ever
     * made.
     */
    fun chooseHomeStyle(style: HomeStyle) {
        setHomeStyle(style)
        if (style == HomeStyle.ICONS) shrinkDefaultAppsToIcons()
        HomeStyleWizardPrefs.markShown(getApplication())
        _homeStyleWizardOpen.value = false
    }

    /**
     * Shrinks every current top-level app tile with a real, resolvable
     * package down to SMALL, and anchors every top-level tile at an explicit
     * `gridSlot` laying out a **two-lane grid**: clock/calendar/weather
     * occupy a reserved lane along the right edge, and every other tile
     * (icons + folders) dense-packs into the remaining lane on the left —
     * "icons on the left" per explicit user request. Only ever called once,
     * from the one-shot wizard's ICONS pick on a genuinely fresh
     * (never-customized) layout — never from a later Personalize toggle —
     * so it can't clobber anything the user has since arranged.
     *
     * Deliberately leaves blank-package `liveOnly` tiles' *size* untouched
     * (weather never resolves a role, so it stays its declared MEDIUM 2×2 —
     * "these should keep reading as live tiles") and folders (a folder stays
     * a folder-sized tile with its own mini-grid preview, not shrunk to a
     * compact icon); both still get packed into the left icon lane like any
     * other non-reserved tile.
     *
     * The right lane is exactly 2 columns wide, packed via the same dense
     * [GridPacker.pack] the left lane uses (with `columns = 2`) so
     * clock/calendar (each SMALL once their role resolves, as it does on
     * most devices) land side by side on row 0, and weather's 2×2 footprint
     * — the same width as the lane — settles directly below them on row 1:
     * "calendar and clock side by side on top right, and below weather tile
     * (right)". The left lane is the remaining `columns - 2` columns, offset
     * by 0; the right lane's placements are offset by `columns - 2` to sit
     * against the right edge.
     */
    private fun shrinkDefaultAppsToIcons() {
        viewModelScope.launch(writeContext) {
            val current = tiles.value
            val futureSize = HashMap<String, TileSize>()
            current.forEach { tile ->
                val shrunk = tile is TileModel.App && tile.packageName.isNotBlank() && tile.size != TileSize.SMALL
                val size = if (shrunk) TileSize.SMALL else tile.size
                futureSize[tile.id] = size
                if (shrunk) repository.setTileSize(tile.id, TileSize.SMALL)
            }

            val byIconKey = current.filterIsInstance<TileModel.App>().associateBy { it.iconKey }
            val rightLaneIds = listOf("calendar", "clock", "weather").mapNotNull { byIconKey[it]?.id }
            val leftLaneIds = current.map { it.id }.filterNot { it in rightLaneIds }

            val columns = settings.value.columns
            if (rightLaneIds.isNotEmpty() && columns >= 3) {
                val laneWidth = 2
                val laneOffset = columns - laneWidth
                val rightPlacements = GridPacker.pack(
                    rightLaneIds.map { TileSpec(it, futureSize.getValue(it)) },
                    laneWidth,
                )
                rightPlacements.forEach { p ->
                    repository.setTileGridSlot(p.id, GridPacker.encodeSlot(p.col + laneOffset, p.row))
                }
                val leftPlacements = GridPacker.pack(
                    leftLaneIds.map { TileSpec(it, futureSize.getValue(it)) },
                    laneOffset,
                )
                leftPlacements.forEach { p ->
                    repository.setTileGridSlot(p.id, GridPacker.encodeSlot(p.col, p.row))
                }
            } else {
                // Too narrow for a separate lane (shouldn't happen at the
                // supported 4/5/6 column counts) — fall back to one plain
                // dense-packed lane spanning the full grid width.
                current.forEach { if (it.gridSlot != null) repository.setTileGridSlot(it.id, null) }
                val placements = GridPacker.pack(current.map { TileSpec(it.id, futureSize.getValue(it.id)) }, columns)
                placements.forEach { p -> repository.setTileGridSlot(p.id, GridPacker.encodeSlot(p.col, p.row)) }
            }
        }
    }

    /** Dismissing the first-run wizard without an explicit pick — leaves
     *  [LauncherSettings.homeStyle] exactly as it already is (TILES on a
     *  fresh install), but still marks the flag so it isn't shown again. */
    fun skipHomeStyleWizard() {
        HomeStyleWizardPrefs.markShown(getApplication())
        _homeStyleWizardOpen.value = false
    }

    /** Set the icon mask ICONS home style applies (unused in TILES). */
    fun setIconShape(shape: IconShape) {
        viewModelScope.launch(writeContext) { settingsRepository.setIconShape(shape) }
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
     * One-time migration for installs that predate the "personalize" Start
     * tile (the corner gear's replacement). Fresh installs already get it
     * from [LayoutRepository.seedIfEmpty] / [DefaultLayout] directly; this
     * only needs to backfill existing layouts — plus two follow-up icon
     * corrections, since Room never retroactively re-applies a changed
     * `iconFor` mapping to an already-persisted tile:
     *
     * - personalize keeps the shared gear glyph (an earlier version of this
     *   migration gave it its own "palette" glyph instead, since reversed).
     * - the real Android Settings app's own existing Start pin (if the user
     *   has one — a real, ordinary app tile, not part of [DefaultLayout]'s
     *   own seed list) gets its `iconKey` cleared so it falls back to its
     *   *real* device icon instead of the shared gear, reading as visually
     *   distinct from the personalize tile right next to it.
     */
    private suspend fun migrateSettingsTile() {
        val current = repository.tiles.first()
        val personalizeTile = current.filterIsInstance<TileModel.App>()
            .firstOrNull { it.packageName.isBlank() && it.label == "personalize" }
        if (personalizeTile == null) {
            repository.addDefaultTile("personalize")
        } else if (personalizeTile.iconKey != "settings") {
            repository.updateTileIconKey(personalizeTile.id, "settings")
        }

        val context = getApplication<Application>()
        val settingsPkg = repository.resolvedPackageFor("settings")
        if (settingsPkg != null) {
            current.filterIsInstance<TileModel.App>()
                .filter { it.packageName == settingsPkg && it.iconKey == "settings" }
                .forEach { repository.updateTileIconKey(it.id, null) }
        }

        // An earlier version of this migration hid the real Settings app from
        // the App List (to avoid a duplicate pin once Quick Panel got its own
        // "android settings" tile) — reversed per later explicit request, so
        // it stays discoverable/pinnable there. See SettingsAppMigration's own
        // doc comment for why this needs its own one-shot guard.
        if (!SettingsAppMigration.hasUnhideRun(context)) {
            settingsPkg?.let { pkg -> HiddenApps.unhide(context, pkg) }
            SettingsAppMigration.markUnhideRun(context)
        }
    }

    /**
     * Anchor a tile at a grid cell after a sticky-mode drag-drop (FR-3.2 WP
     * variant). Dropping onto a cell that's already occupied pushes the
     * occupant(s) straight down to make room — the same push-down +
     * empty-row-collapse [stickySlotsForPlacement] already does for a resize
     * — rather than rejecting the drop or leaving two tiles overlapping.
     * Real auto-arrange (a full dense repack) never runs: only the tiles the
     * dropped footprint actually displaces move, cascading the minimum
     * amount needed.
     */
    fun setTileGridSlot(id: String, slot: Int?) {
        if (slot == null) return
        val model = tiles.value.firstOrNull { it.id == id } ?: return
        val targetCol = GridPacker.decodeSlotCol(slot)
        val targetRow = GridPacker.decodeSlotRow(slot)
        // FREE mode swaps with whatever occupies the drop cell instead of
        // pushing it down — nothing else on the grid moves. STICKY (and DENSE,
        // which never reaches this anchored write path) keep the existing
        // push-down behaviour.
        val finalSlots = if (settings.value.tilePackMode == TilePackMode.FREE) {
            val columns = settings.value.columns
            val anchored = tiles.value.mapNotNull { t ->
                if (t.id == id) return@mapNotNull null
                val s = t.gridSlot ?: return@mapNotNull null
                TilePlacement(t.id, t.size, GridPacker.decodeSlotCol(s), GridPacker.decodeSlotRow(s))
            }
            val fromCol = model.gridSlot?.let { GridPacker.decodeSlotCol(it) }
            val fromRow = model.gridSlot?.let { GridPacker.decodeSlotRow(it) }
            GridPacker.swapPlacement(anchored, id, fromCol, fromRow, model.size, targetCol, targetRow, columns)
        } else {
            stickySlotsForPlacement(movedId = id, size = model.size, targetCol = targetCol, targetRow = targetRow)
        }
        viewModelScope.launch(writeContext) {
            finalSlots.forEach { (tid, s) -> repository.setTileGridSlot(tid, s) }
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

    /**
     * Manual multi-select toggle of a news-region preset (feed settings): additively
     * merges/removes [region]'s feeds — several regions can be active at once, the
     * explicit-choice counterpart to the locale-based auto-seed in `StartViewModel.init`.
     */
    fun setFeedRegionEnabled(region: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            feedStore.toggleRegion(region, enabled)
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
     * Drag-pulled a folder child out onto the top-level grid (dense/free
     * arrangement): inserts it as a new top-level tile right where
     * [beforeId] currently sits (or appended at the end when [beforeId] is
     * null/no longer present — an empty-area drop), instead of always
     * appending to the bottom like the tap-× shortcut ([removeFolderChild]).
     * [beforeId]'s position is read fresh from [tiles] rather than trusting
     * the caller's own copy of the order, so it can't have drifted stale.
     */
    fun pullFolderChildToPosition(folderId: String, child: FolderChild, beforeId: String?) {
        val newTileId = "pin-${child.packageName}-${System.currentTimeMillis()}"
        val reorderedIds = insertBeforeTarget(tiles.value.map { it.id }, newTileId, beforeId)
        viewModelScope.launch(writeContext) {
            repository.placeFolderChildAtTopLevel(folderId, child, newTileId, reorderedIds = reorderedIds)
        }
    }

    /**
     * Drag-pulled a folder child out onto a sticky/free-arrangement grid cell
     * ([slot], see [GridPacker.encodeSlot]) — mirrors [setTileGridSlot]'s
     * push-down for a tile that doesn't exist yet: [stickySlotsForPlacement]
     * is computed as if a fresh tile were already anchored there, and every
     * tile it displaces is written alongside the new tile's own resolved
     * slot, all in one go.
     */
    fun pullFolderChildToSlot(folderId: String, child: FolderChild, slot: Int) {
        val newTileId = "pin-${child.packageName}-${System.currentTimeMillis()}"
        val finalSlots = stickySlotsForPlacement(
            movedId = newTileId,
            size = child.size,
            targetCol = GridPacker.decodeSlotCol(slot),
            targetRow = GridPacker.decodeSlotRow(slot),
        )
        viewModelScope.launch(writeContext) {
            repository.placeFolderChildAtTopLevel(folderId, child, newTileId, gridSlot = finalSlots[newTileId])
            finalSlots.filterKeys { it != newTileId }.forEach { (id, s) -> repository.setTileGridSlot(id, s) }
        }
    }

    /**
     * Drag-pulled a folder child out onto another top-level tile's merge
     * zone — the drag-out counterpart of [merge]: [child] (still inside
     * [folderId] until this commits) is folded into [targetId] exactly like
     * an ordinary top-level drag-merge would.
     */
    fun pullFolderChildIntoMerge(folderId: String, child: FolderChild, targetId: String) {
        val survivingOrder = tiles.value.map { it.id }
        viewModelScope.launch(writeContext) {
            repository.mergeFolderChildIntoTile(folderId, child, targetId, survivingOrder)
        }
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
     * Set a folder child's size directly to [size] — the write path for
     * gesture-based drag resize, mirroring [resizeTo] for top-level tiles.
     * Can land on any of the eleven [TileSize] presets, unlike
     * [resizeFolderChild]'s fixed tap cycle.
     */
    fun resizeFolderChildTo(folderId: String, child: FolderChild, size: TileSize) {
        viewModelScope.launch(writeContext) {
            repository.resizeFolderChildTo(folderId, child, size)
        }
    }

    /**
     * Turn a folder into a widget stack in one shot: every child resized to
     * [size] (any [TileSize.stackable] size, not just WIDE/LARGE), the folder
     * tile matching, and `TileModel.Folder.showAsStack` turned on. In sticky
     * mode this grows the folder tile's own footprint exactly like [resize]
     * does, so it needs the same anchored-slot handling (column shift +
     * push-down + empty-row collapse) — otherwise the folder's stale anchored
     * cell (sized for its old, smaller footprint) no longer fits the new size
     * and [GridPacker.packSticky] silently re-flows it to the bottom of the
     * grid, same "teleports away" bug [stickyResizeSlots] was written to
     * prevent.
     */
    fun convertFolderToStack(folderId: String, size: TileSize) {
        val model = tiles.value.firstOrNull { it.id == folderId }
        val finalSlots = if (model != null) stickyResizeSlots(model, size) else emptyMap()
        viewModelScope.launch(writeContext) {
            finalSlots.forEach { (movedId, slot) -> repository.setTileGridSlot(movedId, slot) }
            repository.convertFolderToStack(folderId, size)
        }
    }

    /**
     * The single "show as stack" / "show as folder" toggle offered alongside
     * an expanded folder's children (supersedes the old fixed "make stack ·
     * wide"/"make stack · large" shortcuts, now that any [TileSize.stackable]
     * size can be a stack — see docs/DECISIONS.md).
     *  - Currently a stack → [repository.collapseStack] just turns the toggle
     *    off; children and the tile's own footprint are untouched (see its
     *    doc comment), so no sticky-mode slot handling is needed here, unlike
     *    [convertFolderToStack].
     *  - Currently a plain folder → uniforms every child to a stackable
     *    target size (the tile's own current size if that's itself
     *    stackable, so the footprint doesn't have to change; otherwise
     *    MEDIUM) via [convertFolderToStack].
     */
    fun toggleFolderStack(folderId: String) {
        val model = tiles.value.firstOrNull { it.id == folderId } as? TileModel.Folder ?: return
        if (model.isStack) {
            viewModelScope.launch(writeContext) { repository.collapseStack(folderId) }
            collapseFolder()
        } else {
            val target = model.size.takeIf { it.stackable } ?: TileSize.MEDIUM
            convertFolderToStack(folderId, target)
        }
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
     * Home pressed on Start: collapses any expanded folder (FR-4), leaves edit
     * mode (FR-3.1) and asks the screen to collapse the pager and scroll to
     * the top.
     */
    fun goHome() {
        closePersonalize()
        closeAbout()
        closePersonalizeGuide()
        closeFolders()
        closeHiddenApps()
        closeBackup()
        closePermissions()
        closeNewsRegion()
        closeEdgeStrip()
        closeQuickPanel()
        collapseFolder()
        closeSearch()
        exitEdit()
        // Home/back out of the first-run wizard without picking counts as a
        // skip (marks it shown) — same "never nags twice" rule every other
        // one-shot flag in this app follows.
        if (_homeStyleWizardOpen.value) skipHomeStyleWizard()
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
     * Set a tile's size directly to [size] — the write path for gesture-based
     * drag resize, which can land on any of the eleven [TileSize] presets
     * rather than stepping through [resize]'s fixed tap cycle. Shares [resize]'s two
     * guards (a widget stack never resizes; sticky/free mode pushes a colliding
     * neighbor down via [stickyResizeSlots] exactly as a tap-resize would) but
     * writes [size] as given instead of computing `size.next(largeAllowed)`.
     */
    fun resizeTo(id: String, size: TileSize) {
        val model = tiles.value.firstOrNull { it.id == id } ?: return
        if (model is TileModel.Folder && model.isStack) return
        val finalSlots = stickyResizeSlots(model, size)
        viewModelScope.launch(writeContext) {
            finalSlots.forEach { (movedId, slot) -> repository.setTileGridSlot(movedId, slot) }
            repository.setTileSize(id, size)
        }
    }

    /**
     * All grid-cell writes sticky mode needs for [model] to resize to
     * [nextSize]: the resized tile's own cell (its column shifts left just
     * enough to keep the new, wider footprint inside the grid when it no
     * longer fits starting at its original column — e.g. growing to WIDE from
     * anywhere but column 0 always overflows otherwise; its row never moves
     * for its own sake), every tile the resulting footprint displaces (nudged
     * sideways within its own row if there's a free gap there, else pushed
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
        // FREE mode still needs push-down on resize (unlike drag-drop, which
        // swaps): a growing tile must not overlap, and there's no second tile
        // to swap anchors with. This is the one place FREE moves a tile the
        // user didn't touch — see TilePackMode.isAnchored's doc comment.
        if (!settings.value.tilePackMode.isAnchored) return emptyMap()
        val ownSlot = model.gridSlot ?: return emptyMap()
        return stickySlotsForPlacement(
            movedId = model.id,
            size = nextSize,
            targetCol = GridPacker.decodeSlotCol(ownSlot),
            targetRow = GridPacker.decodeSlotRow(ownSlot),
        )
    }

    /**
     * All grid-cell writes sticky mode needs to place [movedId] (already/about
     * to be sized [size]) at ([targetCol], [targetRow]): the tile's own cell
     * (column clamped so its footprint stays inside the grid), every other
     * anchored tile the resulting footprint displaces — nudged sideways
     * within its own row if there's a free gap there, else pushed straight
     * down, cascading until nothing overlaps — and any fully-empty row that
     * leaves behind, collapsed. Shared by [stickyResizeSlots] (grows a tile
     * in place, so [targetCol]/[targetRow] come from the tile's own current
     * cell) and [setTileGridSlot] (moves a tile to wherever a drag-drop
     * released it, including on top of an already-occupied cell — the
     * occupant gets displaced here exactly like a resize's neighbor would).
     * Delegates to [GridPacker.stickyPlacement], the same pure computation
     * StartScreen's drag gesture calls to render a live preview before the
     * drop actually commits.
     */
    private fun stickySlotsForPlacement(movedId: String, size: TileSize, targetCol: Int, targetRow: Int): Map<String, Int> {
        val columns = settings.value.columns
        val anchored = tiles.value.mapNotNull { t ->
            if (t.id == movedId) return@mapNotNull null
            val slot = t.gridSlot ?: return@mapNotNull null
            TilePlacement(t.id, t.size, GridPacker.decodeSlotCol(slot), GridPacker.decodeSlotRow(slot))
        }
        return GridPacker.stickyPlacement(anchored, movedId, size, targetCol, targetRow, columns)
    }

    /** Set or clear a tile's per-tile accent override (null = follow global, FR-7). */
    fun setTileColor(id: String, colorId: String?) {
        viewModelScope.launch(writeContext) { repository.setTileAccent(id, colorId) }
    }

    /** Set a single app tile's "show as icon"/"show as tile" toggle (ICONS home style only). */
    fun setTileDisplayAsIcon(id: String, displayAsIcon: Boolean) {
        viewModelScope.launch(writeContext) { repository.setTileDisplayAsIcon(id, displayAsIcon) }
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

    /**
     * Export the current layout + settings to the SAF URI chosen by the user.
     * Also captures hidden apps, feed subscriptions/regions, feed widget layout,
     * the photos-tile selection, and the wallpaper slideshow's photo list —
     * domains added well after the original tiles/folders/settings backup that a
     * completeness audit found were silently never included (user-reported:
     * "restore is not exactly the same as backup").
     */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val application = getApplication<Application>()
                val (tiles, folders, children) = repository.tilesForBackup()
                val currentSettings = settingsRepository.settings.first()
                val hiddenApps = HiddenApps.hidden(application).first()
                val feed = feedStore.read()
                val widgets = WidgetStore.create(application).read().widgets
                val photoUris = PhotosStore.create(application).read().uris
                val wallpaperUris = WallpaperSlideshowStore.create(application).read().uris
                val json = BackupManager.buildBackupJson(
                    tiles, folders, children, currentSettings,
                    hiddenApps = hiddenApps,
                    feedSources = feed.sources.map { BackupFeedSource(it.url, it.name, it.category, it.enabled) },
                    feedRegions = feed.regions,
                    widgets = widgets.map { BackupWidget(it.widgetId, it.heightDp, it.widthDp) },
                    photoUris = photoUris,
                    wallpaperSlideshowUris = wallpaperUris,
                )
                application.contentResolver
                    .openOutputStream(uri)?.use { it.write(json.encodeToByteArray()) }
                _backupMessage.tryEmit("backup saved")
            }.onFailure {
                _backupMessage.tryEmit("export failed")
            }
        }
    }

    /**
     * Import a layout + settings backup from the SAF URI chosen by the user, plus
     * the extra domains [exportBackup] now captures. Hosted feed widgets are the
     * one exception restored selectively: a `HostedWidget.widgetId` is bound to
     * this specific `AppWidgetHost` instance, not portable like the rest of a
     * backup, so any id that no longer resolves via `AppWidgetManager` (a
     * cross-device restore, or after a reinstall) is dropped rather than kept as
     * a broken slot.
     */
    fun importBackup(uri: Uri) {
        viewModelScope.launch(writeContext) {
            runCatching {
                val application = getApplication<Application>()
                val json = application.contentResolver
                    .openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    ?: error("could not read backup file")
                val backup = BackupManager.parseBackup(json)
                repository.restoreFromBackup(backup.tiles, backup.folders, backup.folderChildren)
                settingsRepository.restoreSettings(backup.settings)
                HiddenApps.replaceAll(application, backup.hiddenApps)
                feedStore.replaceSourcesAndRegions(
                    backup.feedSources.map { FeedSource(it.url, it.name, it.category, it.enabled) },
                    backup.feedRegions,
                )
                val widgetManager = AppWidgetManager.getInstance(application)
                val liveWidgets = backup.widgets.filter {
                    runCatching { widgetManager.getAppWidgetInfo(it.widgetId) != null }.getOrDefault(false)
                }
                WidgetStore.create(application).replaceAll(
                    WidgetData(liveWidgets.map { HostedWidget(it.widgetId, it.heightDp, it.widthDp) }),
                )
                PhotosStore.create(application).setUris(backup.photoUris)
                WallpaperSlideshowStore.create(application).setUris(backup.wallpaperSlideshowUris)
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

    fun setQuickPanelTileOrder(order: List<String>) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setQuickPanelTileOrder(order) }
    }

    fun setQuickPanelTileSizes(sizes: List<String>) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setQuickPanelTileSizes(sizes) }
    }

    fun setEdgeStripBackground(bgId: String) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setEdgeStripBackground(bgId) }
    }

    fun setEdgeStripHandleSize(size: String) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setEdgeStripHandleSize(size) }
    }

    /** Toggle "lock layout" (Personalize): while on, [enterEdit] is a no-op. */
    fun setLockLayout(locked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setLockLayout(locked) }
    }

    /** Toggle "hide status bar" (Personalize): hides the system status bar over TileShell. */
    fun setHideStatusBar(hidden: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setHideStatusBar(hidden) }
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

        /** See [setHomeStyle]'s doc comment: seeded once, only from the 0f default. */
        const val ICONS_MODE_DEFAULT_CORNER_RADIUS = 4f
    }
}
