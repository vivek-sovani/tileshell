package com.tileshell.feature.start

import android.app.Application
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tileshell.core.data.LayoutRepository
import com.tileshell.core.data.FolderChild
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.settings.LauncherSettings
import com.tileshell.core.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
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
    private val settingsRepository = SettingsRepository.create(application)
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

    /** Switch theme (FR-7); persisted and applied live. */
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

    /** Select a bundled gradient wallpaper, clearing any custom photo (FR-7). */
    fun setWallpaper(wallpaperId: String) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setWallpaper(wallpaperId) }
    }

    /** Persist a user-picked custom wallpaper URI (FR-7). */
    fun setCustomWallpaper(uri: String) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepository.setCustomWallpaper(uri) }
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
     * Home pressed on Start: closes the folder overlay (FR-4), leaves edit mode
     * (FR-3.1) and asks the screen to collapse the pager and scroll to the top.
     */
    fun goHome() {
        closePersonalize()
        closeFolder()
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

    /** Cycle the tile's size small→medium→wide→large→small (FR-3.4 resize). */
    fun resize(id: String) {
        viewModelScope.launch(writeContext) { repository.cycleTileSize(id) }
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
