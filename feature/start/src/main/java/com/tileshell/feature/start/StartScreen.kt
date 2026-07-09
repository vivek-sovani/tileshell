@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.tileshell.feature.start

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import java.net.URLEncoder
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tileshell.core.data.AppCategories
import com.tileshell.core.data.AppLauncher
import com.tileshell.core.data.CachedScreenshotPrefs
import com.tileshell.core.data.ContactTile
import com.tileshell.core.data.FolderChild
import com.tileshell.core.data.TileColors
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.TileSize
import com.tileshell.feature.applist.AppListScreen
import com.tileshell.feature.livetiles.CalendarSmallFace
import com.tileshell.feature.livetiles.CalendarTileFace
import com.tileshell.feature.livetiles.ClockSmallFace
import com.tileshell.feature.livetiles.ClockTileFace
import com.tileshell.feature.livetiles.ConversationTileFace
import com.tileshell.feature.livetiles.LiveFace
import com.tileshell.feature.livetiles.MediaSessionsEffect
import com.tileshell.feature.livetiles.MusicTileFace
import com.tileshell.feature.livetiles.NotificationAccess
import com.tileshell.feature.livetiles.NotificationCenter
import com.tileshell.feature.start.feed.FeedPage
import com.tileshell.feature.start.feed.googleSearchUrl
import com.tileshell.feature.start.feed.pagerCommitTarget
import com.tileshell.feature.livetiles.NotificationSnapshot
import com.tileshell.feature.livetiles.NotificationTileFace
import com.tileshell.feature.livetiles.PeopleTileFace
import com.tileshell.feature.livetiles.PhotosData
import com.tileshell.feature.livetiles.PhotosStore
import com.tileshell.feature.livetiles.PhotosTileFace
import com.tileshell.feature.livetiles.contactLookupUri
import com.tileshell.feature.livetiles.rememberContactPhotoUri
import com.tileshell.feature.livetiles.rememberTileBitmap
import com.tileshell.feature.livetiles.WeatherSmallFace
import com.tileshell.feature.livetiles.WeatherTileFace
import com.tileshell.feature.livetiles.rememberFlipState
import com.tileshell.feature.livetiles.rememberLiveTilesActive
import com.tileshell.feature.livetiles.OemBatteryGuard
import com.tileshell.feature.livetiles.rememberBatteryOptimizationExempt
import com.tileshell.feature.livetiles.rememberNotificationAccess
import com.tileshell.feature.livetiles.rememberPermissionGranted
import com.tileshell.feature.livetiles.WeatherRefreshWorker
import com.tileshell.feature.personalize.AboutSheet
import com.tileshell.feature.personalize.BackupRestoreSheet
import com.tileshell.feature.personalize.LayoutHistorySheet
import com.tileshell.feature.livetiles.LayoutAutoBackupWorker
import com.tileshell.feature.personalize.CategoryFolderSheet
import com.tileshell.feature.personalize.FeedSourceItem
import com.tileshell.feature.personalize.EdgeStripSheet
import com.tileshell.feature.personalize.HiddenAppsSheet
import com.tileshell.feature.personalize.PersonalizeGuidePrefs
import com.tileshell.feature.personalize.PersonalizeGuideSheet
import com.tileshell.feature.personalize.PersonalizeSheet
import com.tileshell.feature.system.AppUpdateState
import com.tileshell.feature.system.rememberAppUpdateState
import com.tileshell.core.data.settings.FontStyle
import com.tileshell.core.data.settings.TileColorSource
import com.tileshell.core.data.settings.TileFill
import com.tileshell.core.design.DarkColorTokens
import com.tileshell.core.design.Glass
import com.tileshell.core.design.LocalAccent
import com.tileshell.core.design.LocalColorTokens
import com.tileshell.core.design.LocalTileCornerRadius
import com.tileshell.core.design.LocalTileFont
import com.tileshell.core.design.LocalTileGradient
import com.tileshell.core.design.NunitoFamily
import com.tileshell.core.design.OutfitFamily
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.Wallpapers
import com.tileshell.core.design.Wallpapers.NONE_ID
import com.tileshell.core.design.colorTokens
import com.tileshell.core.design.tileGradientBrush
import com.tileshell.core.design.tiltOnPress
import com.tileshell.core.design.wallpaperWindow
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.abs
import kotlin.math.roundToInt

/** Hairline between show-through tiles so each reads as a distinct window. */
private val TiledTileBorder = Color(0x66000000)

/**
 * The real Start screen and its App-list page, joined by the finger-following
 * pager (FR-6). Start renders persisted tiles packed by the dense packer as
 * monoline glyphs on accent fills with lowercase lower-left labels (icon only
 * on small), over the aurora wallpaper. Swiping left brings in the App-list
 * page (placeholder until S9): Start parallaxes −22% and fades to 0.4, the
 * page commits past 50%, otherwise springs back. Tapping an app tile launches
 * it via LauncherApps. Insets follow FR-1.
 */
@Composable
fun StartScreen(
    modifier: Modifier = Modifier,
    viewModel: StartViewModel = viewModel(),
    onLockScreen: () -> Unit = {},
    onRecents: () -> Unit = {},
) {
    val tiles by viewModel.tiles.collectAsStateWithLifecycle()
    val swipeEnabled by viewModel.swipeEnabled.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val selectedTileId by viewModel.selectedTileId.collectAsStateWithLifecycle()
    val openFolderId by viewModel.openFolderId.collectAsStateWithLifecycle()
    val personalizeOpen by viewModel.personalizeOpen.collectAsStateWithLifecycle()
    val aboutOpen by viewModel.aboutOpen.collectAsStateWithLifecycle()
    val personalizeGuideOpen by viewModel.personalizeGuideOpen.collectAsStateWithLifecycle()
    val historyOpen by viewModel.historyOpen.collectAsStateWithLifecycle()
    val layoutHistory by viewModel.layoutHistory.collectAsStateWithLifecycle()
    val backupOpen by viewModel.backupOpen.collectAsStateWithLifecycle()
    val foldersOpen by viewModel.foldersOpen.collectAsStateWithLifecycle()
    val hiddenAppsOpen by viewModel.hiddenAppsOpen.collectAsStateWithLifecycle()
    val edgeStripOpen by viewModel.edgeStripOpen.collectAsStateWithLifecycle()
    val searchOpen by viewModel.searchOpen.collectAsStateWithLifecycle()
    // Hoisted above the EdgeStrip composable so its expanded/collapsed state survives
    // being unmounted while personalize/edit-mode/a folder is on top (it used to live
    // inside EdgeStrip itself and reset to expanded every time the strip remounted).
    var edgeStripExpanded by remember { mutableStateOf(true) }
    val hiddenPackages by viewModel.hiddenPackages.collectAsStateWithLifecycle()
    val isAppList by viewModel.isAppList.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val feedSources by viewModel.feedSources.collectAsStateWithLifecycle()
    // Live notification state (FR-1.2 badges, FR-2 mail/messages). Empty until the
    // user enables notification access, which keeps every tile static / un-badged.
    val notifications by NotificationCenter.snapshot.collectAsStateWithLifecycle()
    val notificationAccess = rememberNotificationAccess()
    val batteryExempt = rememberBatteryOptimizationExempt()
    val contactsGranted = rememberPermissionGranted(android.Manifest.permission.READ_CONTACTS)
    val calendarGranted = rememberPermissionGranted(android.Manifest.permission.READ_CALENDAR)
    val locationGranted = rememberPermissionGranted(android.Manifest.permission.ACCESS_COARSE_LOCATION)
    val (updateState, onUpdateAction) = rememberAppUpdateState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // First time Personalize is ever opened, auto-surface the how-to guide on top
    // of it (one-shot, mirrors FirstRunHintPrefs); a permanent row inside
    // Personalize reopens it manually afterwards.
    LaunchedEffect(personalizeOpen) {
        if (personalizeOpen && !PersonalizeGuidePrefs.shown(context)) {
            viewModel.openPersonalizeGuide()
            PersonalizeGuidePrefs.markShown(context)
        }
    }

    // Keep the daily Bing wallpaper refresh enqueued across process restarts while
    // the user has it on (the worker no-ops once they turn it off).
    LaunchedEffect(settings.bingWallpaper) {
        if (settings.bingWallpaper) {
            com.tileshell.feature.livetiles.BingWallpaperWorker.ensureScheduled(context)
        } else {
            com.tileshell.feature.livetiles.BingWallpaperWorker.cancel(context)
        }
    }

    // Same re-arm for the wallpaper slideshow rotation, also picking up interval changes.
    LaunchedEffect(settings.wallpaperSlideshowEnabled, settings.wallpaperSlideshowIntervalMin) {
        if (settings.wallpaperSlideshowEnabled) {
            com.tileshell.feature.livetiles.WallpaperSlideshowWorker.ensureScheduled(
                context, settings.wallpaperSlideshowIntervalMin,
            )
        } else {
            com.tileshell.feature.livetiles.WallpaperSlideshowWorker.cancel(context)
        }
    }

    val specs = remember(tiles) { tiles.map { TileSpec(it.id, it.size) } }
    val byId = remember(tiles) { tiles.associateBy { it.id } }

    // The open folder's model (null closes the overlay; also self-closes if the
    // folder is removed or emptied while open).
    val openFolder = remember(tiles, openFolderId) {
        tiles.firstOrNull { it.id == openFolderId } as? TileModel.Folder
    }
    // If the folder vanished while open (e.g. an uninstall dissolved it), fully
    // close so the swipe is re-enabled rather than left stuck off.
    LaunchedEffect(openFolderId, openFolder) {
        if (openFolderId != null && openFolder == null) viewModel.closeFolder()
    }

    // Effective theme: follow the device dark-mode setting unless the user opted
    // into a manual choice. Used everywhere the chrome is skinned so the whole
    // tree re-composes when either the system setting or the preference changes.
    val dark = if (settings.followSystemTheme) isSystemInDarkTheme() else settings.dark

    // Active theme tokens + global accent (FR-7), provided down the tree so the
    // chrome (sheet, edit bar, app list) re-skins live when personalization changes.
    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(settings.accentId)
    val noWallpaper = settings.wallpaperId == NONE_ID && settings.customWallpaperUri == null
    val wallpaper = Wallpapers.forId(settings.wallpaperId)
    // Tile style: corner radius + gradient fill + font family.
    val tileFont = when (settings.fontStyle) {
        FontStyle.OUTFIT -> OutfitFamily
        FontStyle.NUNITO -> NunitoFamily
        FontStyle.SYSTEM -> androidx.compose.ui.text.font.FontFamily.Default
    }
    val baseTextStyle = LocalTextStyle.current
    // "Wallpaper behind tiles" mode: the screen goes dark and the wallpaper shows
    // only through the tiles. Decode the custom photo here (when set) so the tiles
    // can window into it; a bundled gradient is drawn directly by the window modifier.
    val tiledWallpaper = settings.tiledWallpaper
    val tiledPhoto =
        if (tiledWallpaper && settings.customWallpaperUri != null) {
            rememberWallpaperBitmap(settings.customWallpaperUri!!)
        } else {
            null
        }

    // URI of a just-picked wallpaper photo waiting for the user to crop/position it.
    // Set by the picker callback; cleared when the crop overlay is confirmed or cancelled.
    var pendingWallpaperCropUri by remember { mutableStateOf<String?>(null) }
    // True while re-framing the active (custom/Bing) wallpaper — adjusts alignment only.
    var adjustingWallpaper by remember { mutableStateOf(false) }
    // True while the recent-Bing-wallpapers viewer is open.
    var bingHistoryOpen by remember { mutableStateOf(false) }

    // Gallery photo picker for a custom wallpaper. PickVisualMedia opens the phone's
    // gallery / system photo picker (nicer than the SAF document browser). Its grant
    // isn't persistable, so the picked image is copied into private storage and the
    // crop overlay works on that copy (MediaImport; supersedes DECISIONS S18/S23).
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val local = withContext(Dispatchers.IO) { MediaImport.importWallpaper(context, uri) }
                // Don't save yet — show the crop overlay first so the user can position
                // the photo before it becomes the live wallpaper.
                if (local != null) pendingWallpaperCropUri = local.toString()
            }
        }
    }

    // Live-photos selection (FR-2). PickMultipleVisualMedia opens the gallery; the
    // picked photos are copied into private storage so the slideshow survives a
    // reboot without a persistable grant (MediaImport).
    val photosStore = remember(context) { PhotosStore.create(context) }
    val photosCount = photosStore.data.collectAsStateWithLifecycle(initialValue = PhotosData())
        .value.uris.size
    val photosPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val local = withContext(Dispatchers.IO) { MediaImport.importPhotos(context, uris) }
                if (local.isNotEmpty()) photosStore.setUris(local)
            }
        }
    }

    // Wallpaper slideshow selection: multiple photos the background wallpaper
    // rotates through on a timer (`WallpaperSlideshowWorker`), mirroring the live-
    // photos picker above. Picking while the slideshow is already on shows the
    // first photo immediately, same instant feedback as picking a single wallpaper.
    val wallpaperSlideshowStore = remember(context) {
        com.tileshell.feature.livetiles.WallpaperSlideshowStore.create(context)
    }
    val wallpaperSlideshowCount = wallpaperSlideshowStore.data
        .collectAsStateWithLifecycle(initialValue = com.tileshell.feature.livetiles.WallpaperSlideshowData())
        .value.uris.size
    val wallpaperSlideshowPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val local = withContext(Dispatchers.IO) { MediaImport.importWallpaperSlideshow(context, uris) }
                if (local.isNotEmpty()) {
                    wallpaperSlideshowStore.setUris(local)
                    if (settings.wallpaperSlideshowEnabled) viewModel.setWallpaperSlide(local.first(), 0)
                }
            }
        }
    }

    // Per-permission launchers for the personalize "permissions" section.
    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted state is re-read on ON_RESUME via rememberPermissionGranted */ }
    val calendarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) WeatherRefreshWorker.refreshNow(context)
    }

    // SAF launchers for backup export/import (permission-free; supports Google Drive).
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }

    val backupRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importBackup(it) } }

    // Show a Toast for backup export/restore outcomes.
    LaunchedEffect(viewModel) {
        viewModel.backupMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Show a Toast for quick search's "pin to start" action.
    LaunchedEffect(viewModel) {
        viewModel.pinMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Keep the auto-backup WorkManager task in sync with settings.
    val autoBackupEnabled = settings.autoBackupEnabled
    val autoBackupInterval = settings.autoBackupIntervalHours
    LaunchedEffect(autoBackupEnabled, autoBackupInterval) {
        if (autoBackupEnabled) {
            LayoutAutoBackupWorker.schedule(context, autoBackupInterval)
        } else {
            LayoutAutoBackupWorker.cancel(context)
        }
    }

    // Pending screenshot capture: set true when the user taps "save snapshot" so the
    // PersonalizeSheet can dismiss first, then we PixelCopy the bare Start screen.
    var captureRequested by remember { mutableStateOf(false) }
    val activity = context as? android.app.Activity
    LaunchedEffect(captureRequested) {
        if (captureRequested && activity != null) {
            delay(380) // wait for sheet dismiss animation (~300ms) to finish
            val id = System.currentTimeMillis().toString()
            val path = captureSnapshotJpeg(activity, context, id)
            viewModel.saveLayoutSnapshot(id = id, screenshotPath = path)
            captureRequested = false
        }
    }

    // Opportunistically cache a screenshot whenever Start leaves the foreground, so the
    // headless auto-backup worker (no window to PixelCopy from) has something to reuse —
    // see cacheForegroundScreenshot. Hooked on ON_PAUSE rather than ON_STOP: the window is
    // still attached/visible at pause time, but may already be gone by the time stop fires.
    // Skipped mid-edit so we never capture the jiggle/drag UI.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activity, editMode) {
        val observer = LifecycleEventObserver { _, event ->
            val throttleMs = 10 * 60 * 1000L // at most once per 10 min — ON_PAUSE fires on every app switch
            if (event == Lifecycle.Event.ON_PAUSE && activity != null && !editMode &&
                CachedScreenshotPrefs.claimAttempt(context, throttleMs)
            ) {
                scope.launch {
                    val id = System.currentTimeMillis().toString()
                    captureSnapshotJpeg(activity, context, id)?.let { path ->
                        viewModel.cacheForegroundScreenshot(path)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Guard against accidental restore (destructive — replaces the current layout).
    var restoreConfirmPending by remember { mutableStateOf(false) }
    if (restoreConfirmPending) {
        AlertDialog(
            onDismissRequest = { restoreConfirmPending = false },
            title = { Text("restore backup?") },
            text = { Text("this will replace your current start screen layout and settings.") },
            confirmButton = {
                TextButton(onClick = {
                    restoreConfirmPending = false
                    backupRestoreLauncher.launch(arrayOf("application/json"))
                }) { Text("restore") }
            },
            dismissButton = {
                TextButton(onClick = { restoreConfirmPending = false }) { Text("cancel") }
            },
        )
    }

    val scrollState = rememberScrollState()
    // Pager position: -1 = feed (left), 0 = Start, +1 = app list (right). The feed
    // page is the swipe-right surface; it is only reachable when enabled (FR-7).
    val feedEnabled = settings.feedEnabled
    // In landscape we drop the feed↔Start swipe and show both as side-by-side
    // panels instead (so a 4-col grid never balloons to fill the wide screen).
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val progress = remember { Animatable(0f) }
    // Live tiles pause when Start is no longer the foreground surface: the app
    // list (>50% right) or the feed (>50% left) has taken over, or an overlay
    // sits above it (FR-2 gating).
    val appListShown by remember { derivedStateOf { progress.value >= 0.5f } }
    val feedShown by remember { derivedStateOf { progress.value <= -0.5f } }
    val liveSuspended = appListShown || feedShown || openFolder != null || personalizeOpen
    val settleSpec = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    fun settleTo(target: Float) {
        scope.launch {
            progress.animateTo(target, settleSpec)
            viewModel.setAppList(target >= 0.5f)
        }
    }

    // If the feed page is turned off in personalize while it is showing, slide
    // back to Start so the pager never rests on a now-absent page.
    LaunchedEffect(feedEnabled) {
        if (!feedEnabled && progress.value < 0f) progress.animateTo(0f, settleSpec)
    }

    // The feed page is its own left panel in landscape (no −1 swipe position), so
    // never rest on it once we rotate into landscape.
    LaunchedEffect(isLandscape) {
        if (isLandscape && progress.value < 0f) progress.animateTo(0f, settleSpec)
    }

    // Home press collapses to Start and scrolls the grid to the top.
    LaunchedEffect(Unit) {
        viewModel.homeRequests.collect {
            viewModel.setAppList(false)
            scope.launch { progress.animateTo(0f, settleSpec) }
            scrollState.animateScrollTo(0)
        }
    }

    // Two-finger swipe-down opens quick search — only while resting on Start with
    // nothing else already up (edit mode / a folder already disables swipeEnabled;
    // the sheet flags are checked directly since they don't touch it).
    val restingAtStart = abs(progress.value) < 0.05f
    val quickSearchEnabled = swipeEnabled && restingAtStart && !searchOpen &&
        !personalizeOpen && !aboutOpen && !historyOpen && !backupOpen &&
        !foldersOpen && !hiddenAppsOpen
    // Runs in the Initial pass like the pager, but keys off pointer *count* (2)
    // rather than direction, so it never competes with the single-finger pager /
    // tile-drag gestures below it — those simply never see a second pointer.
    val quickSearchGesture = Modifier.pointerInput(quickSearchEnabled) {
        if (!quickSearchEnabled) return@pointerInput
        val thresholdPx = 40.dp.toPx()
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val startA = first.position
            var secondId: PointerId? = null
            var startB = Offset.Zero
            var triggered = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (secondId == null) {
                    pressed.firstOrNull { it.id != first.id }?.let {
                        secondId = it.id
                        startB = it.position
                    }
                }
                val second = secondId
                if (!triggered && second != null) {
                    val a = pressed.firstOrNull { it.id == first.id } ?: break
                    val b = pressed.firstOrNull { it.id == second } ?: break
                    val dy = ((a.position.y - startA.y) + (b.position.y - startB.y)) / 2f
                    val dx = ((a.position.x - startA.x) + (b.position.x - startB.x)) / 2f
                    if (isQuickSearchSwipe(dy, dx, thresholdPx)) {
                        triggered = true
                        viewModel.openSearch()
                    }
                }
                if (triggered) event.changes.forEach { it.consume() }
            }
        }
    }

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalColorTokens provides tokens,
        LocalAccent provides accent,
        LocalTileCornerRadius provides settings.cornerRadius,
        LocalTileGradient provides (settings.tileFill == TileFill.GRADIENT),
        LocalTileFont provides tileFont,
        LocalTextStyle provides baseTextStyle.copy(fontFamily = tileFont),
    ) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().then(quickSearchGesture)) {
        val widthPx = constraints.maxWidth.toFloat()
        val viewportHeightPx = constraints.maxHeight.toFloat()
        val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()

        // Wallpaper layer (FR-7): selected gradient or custom photo, optionally
        // blurred. Drawn first so all content sits above it. In "wallpaper behind
        // tiles" mode the screen instead goes flat dark — the wallpaper shows only
        // through the tiles, keeping every gap/border dark. "none" skips the image
        // layer entirely and the theme bg colour shows through.
        if (tiledWallpaper) {
            Box(modifier = Modifier.fillMaxSize().background(tokens.bg))
        } else if (noWallpaper) {
            Box(modifier = Modifier.fillMaxSize().background(tokens.bg))
        } else {
            WallpaperBackground(
                gradient = wallpaper,
                customWallpaperUri = settings.customWallpaperUri,
                blur = settings.blur,
                alignX = settings.wallpaperAlignX,
                alignY = settings.wallpaperAlignY,
                zoom = settings.wallpaperZoom,
                dark = dark,
            )
        }

        // Horizontal pager gesture, parameterised by the width of the page being
        // swiped (`pageWidthPx`) and the lowest reachable position (`lower`: −1 to
        // reach the feed in portrait, 0 when there is no swipe-in feed page).
        // Detection runs in the Initial pass so a dominant horizontal drag is
        // claimed before the vertical grid scroll (a child) can consume it;
        // vertical drags pass straight through.
        fun pagerModifier(pageWidthPx: Float, lower: Float): Modifier =
            Modifier.pointerInput(swipeEnabled, pageWidthPx, lower) {
                if (!swipeEnabled) return@pointerInput
                val slop = 12.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val base = progress.value
                    var horizontal = false
                    var decided = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (!decided) {
                            if (abs(dx) > slop && abs(dx) > abs(dy) * 1.2f) {
                                decided = true
                                horizontal = true
                            } else if (abs(dy) > slop) {
                                decided = true // vertical → leave it to the grid scroll
                            }
                        }
                        if (horizontal) {
                            change.consume()
                            val target = (base - dx / pageWidthPx).coerceIn(lower, 1f)
                            scope.launch { progress.snapTo(target) }
                        }
                        if (!change.pressed) break
                    }
                    if (horizontal) {
                        settleTo(pagerCommitTarget(base, progress.value).coerceAtLeast(lower))
                    }
                }
            }

        // Blur the Start surface behind the folder overlay (prototype
        // backdrop-filter; real blur only takes effect API 31+, harmless below).
        val behindBlur = if (openFolder != null) 14.dp else 0.dp
        // Mirrors the EdgeStrip mount/suppress condition below — true exactly when the
        // strip is actually rendered and expanded (not just collapsed to its sliver),
        // so the app-list/gear affordance only rises to clear it when it's really there.
        val edgeStripVisible = settings.edgeStripEnabled && settings.edgeStripApps.isNotEmpty() &&
            !editMode && openFolderId == null && !personalizeOpen && !searchOpen && edgeStripExpanded

        // Page content reused by both layouts. `pageWidthPx` drives the Start grid
        // and its edit-drag hit-testing, so a half-width landscape panel keeps
        // tiles portrait-sized instead of stretching to fill the wide screen.
        val renderStartPage: @Composable (Float) -> Unit = { pageWidthPx ->
                StartPage(
                    specs = specs,
                    byId = byId,
                    apps = apps,
                    scrollState = scrollState,
                    chevronVisible = swipeEnabled,
                    edgeStripVisible = edgeStripVisible,
                    editMode = editMode,
                    liveSuspended = liveSuspended,
                    selectedTileId = selectedTileId,
                    accent = accent,
                    accentId = settings.accentId,
                    appIconColors = settings.tileColorSource == TileColorSource.APP_ICON,
                    // Tiled-wallpaper mode ignores the gap setting (stays tight) so
                    // wider spacing never fragments the show-through wallpaper.
                    tileGapPx = if (tiledWallpaper) {
                        null
                    } else {
                        with(density) { settings.tileGap.dp.toPx() }
                    },
                    glass = settings.glass,
                    transparency = settings.transparency,
                    glassLine = tokens.glassLine,
                    tiledWallpaper = tiledWallpaper,
                    wallpaper = wallpaper,
                    wallpaperPhoto = tiledPhoto,
                    wallpaperAlignX = settings.wallpaperAlignX,
                    wallpaperAlignY = settings.wallpaperAlignY,
                    wallpaperZoom = settings.wallpaperZoom,
                    darkTheme = dark,
                    notifications = notifications,
                    widthPx = pageWidthPx,
                    viewportHeightPx = viewportHeightPx,
                    statusBarTopPx = statusBarTopPx,
                    columns = settings.columns,
                    onLockScreen = onLockScreen,
                    onTile = { tile ->
                        when (tile) {
                            is TileModel.App -> onTileClick(context, tile)
                            is TileModel.Folder -> viewModel.openFolder(tile.id)
                        }
                    },
                    onLaunchFolderChild = { child -> launchFolderChild(context, child) },
                    onChevron = { settleTo(1f) },
                    onEnterEdit = { id ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.enterEdit(id)
                    },
                    // In-edit tap on another tile switches the selection (no
                    // long-press haptic — it's a light tap, not a fresh lift).
                    onSelectTile = viewModel::enterEdit,
                    onExitEdit = viewModel::exitEdit,
                    onReorder = viewModel::reorder,
                    onMerge = { dragId, targetId, survivingOrder ->
                        viewModel.merge(dragId, targetId, survivingOrder)
                        Toast.makeText(context, "grouped", Toast.LENGTH_SHORT).show()
                    },
                    onResize = viewModel::resize,
                    onUnpin = viewModel::unpin,
                    onSetTileColor = viewModel::setTileColor,
                    onAdd = {
                        viewModel.exitEdit()
                        settleTo(1f)
                        Toast.makeText(context, "long-press an app to pin", Toast.LENGTH_SHORT)
                            .show()
                    },
                    onPersonalize = viewModel::openPersonalize,
                )
        }

        val renderAppList: @Composable () -> Unit = {
            AppListScreen(
                modifier = Modifier.fillMaxSize(),
                visible = isAppList,
                onPinned = { settleTo(0f) },
            )
        }

        // Feed page: an independent, opaque screen. In portrait it slides in over
        // Start from the left edge as the user swipes right (mirrors the app list);
        // in landscape it is the always-visible left panel (`active` stays true).
        val renderFeed: @Composable (Boolean) -> Unit = { active ->
            FeedPage(
                accent = accent,
                statusBarTopPx = statusBarTopPx,
                feedEnabled = settings.feedEnabled,
                onFeedEnabledChange = viewModel::setFeedEnabled,
                feeds = feedSources.map { FeedSourceItem(it.url, it.name, it.category, it.enabled) },
                onToggleFeed = viewModel::setFeedSourceEnabled,
                onToggleCategory = viewModel::setFeedCategoryEnabled,
                onRemoveFeed = viewModel::removeFeedSource,
                onAddFeed = viewModel::addFeedSource,
                onOpenQuickSearch = viewModel::openSearch,
                onWeatherDetails = { query -> launchWebSearch(context, query) },
                onAddSchedule = { launchAddEvent(context) },
                onOpenArticle = { link -> launchUrl(context, link) },
                onRefresh = {
                    viewModel.refreshFeeds()
                    Toast.makeText(context, "refreshing news", Toast.LENGTH_SHORT).show()
                },
                active = active,
            )
        }

        Box(modifier = Modifier.fillMaxSize().blur(behindBlur)) {
            when {
                // Landscape with the feed on: two side-by-side panels. The feed is
                // the left panel (always live); Start is the right panel at half
                // width, so its grid stays portrait-sized. The app list slides in
                // over the Start panel only — the feed panel stays put.
                isLandscape && feedEnabled -> {
                    val panelWidthPx = widthPx / 2f
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            renderFeed(true)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                // Keep the parallaxing Start panel and the sliding app
                                // list inside the right half — without this the Start
                                // tiles bleed left over the feed panel as they shift.
                                .clipToBounds()
                                .then(pagerModifier(panelWidthPx, 0f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationX = -0.22f * panelWidthPx * progress.value
                                        alpha = 1f - 0.6f * abs(progress.value)
                                    },
                            ) { renderStartPage(panelWidthPx) }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationX = panelWidthPx * (1f - progress.value) }
                                    .background(LocalColorTokens.current.bg),
                            ) { renderAppList() }
                        }
                    }
                }
                // Landscape with the feed off: no left panel, so keep Start at a
                // portrait-like width centred on screen (tiles never balloon); the
                // app list still covers the full width.
                isLandscape -> {
                    val cappedWidthPx = minOf(widthPx, with(density) { 460.dp.toPx() })
                    Box(modifier = Modifier.fillMaxSize().then(pagerModifier(widthPx, 0f))) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .width(with(density) { cappedWidthPx.toDp() })
                                .fillMaxHeight()
                                .graphicsLayer {
                                    translationX = -0.22f * widthPx * progress.value
                                    alpha = 1f - 0.6f * abs(progress.value)
                                },
                        ) { renderStartPage(cappedWidthPx) }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationX = widthPx * (1f - progress.value) }
                                .background(LocalColorTokens.current.bg),
                        ) { renderAppList() }
                    }
                }
                // Portrait: the stacked, swipeable pager (feed −1, Start 0, list +1).
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(pagerModifier(widthPx, if (feedEnabled) -1f else 0f)),
                    ) {
                        // Start page: parallaxes (±22%) and fades as a side page comes in.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = -0.22f * widthPx * progress.value
                                    alpha = 1f - 0.6f * abs(progress.value)
                                },
                        ) { renderStartPage(widthPx) }

                        // App-list page: slides in from the right.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationX = widthPx * (1f - progress.value) }
                                .background(LocalColorTokens.current.bg),
                        ) { renderAppList() }

                        // Feed page (left): drawn on top with its own background so
                        // Start never shows through it. Only composed when enabled
                        // (FR-7); off-screen otherwise.
                        if (feedEnabled) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationX = widthPx * (-1f - progress.value) }
                                    .background(LocalColorTokens.current.bg),
                            ) { renderFeed(feedShown) }
                        }
                    }
                }
            }
        }

        // Edge-strip overlay: shown only when enabled and no overlay is on top. Quick
        // search stays out of the mount condition — the strip stays composed and just
        // slides fully away (suppressed) so its expanded/collapsed state isn't lost.
        if (settings.edgeStripEnabled && settings.edgeStripApps.isNotEmpty() &&
            !editMode && openFolderId == null && !personalizeOpen
        ) {
            EdgeStrip(
                apps = settings.edgeStripApps,
                backgroundId = settings.edgeStripBackgroundId,
                handleSize = settings.edgeStripHandleSize,
                notifications = notifications,
                dark = dark,
                accent = TileAccents.forId(settings.accentId),
                expanded = edgeStripExpanded,
                onExpandedChange = { edgeStripExpanded = it },
                suppressed = searchOpen,
                onLaunch = { pkg ->
                    val app = apps.firstOrNull { it.packageName == pkg }
                    if (app != null) AppLauncher.launch(context, app.packageName, app.activityName)
                },
                onSearch = viewModel::openSearch,
                onRecents = onRecents,
            )
        }

        // Each sub-sheet hides its parent while it's on top, so only one page is ever
        // visible at once (a proper back-stack: closing a child reveals its parent again,
        // driven by each sheet's own BackHandler(enabled = visible)).
        val personalizeVisible =
            personalizeOpen && !aboutOpen && !foldersOpen && !backupOpen && !hiddenAppsOpen &&
                !personalizeGuideOpen && !edgeStripOpen
        val backupVisible = backupOpen && !historyOpen
        // Update banner only over the plain Start/feed pages — never on top of edit
        // mode, an open folder, the app list, quick search, or a personalize sheet.
        val showUpdateBanner = !editMode && !isAppList && openFolderId == null &&
            !personalizeOpen && !searchOpen
        val hiddenAppEntries = remember(apps, hiddenPackages) {
            apps.filter { it.packageName in hiddenPackages }.sortedBy { it.label.lowercase() }
        }

        // Personalize sheet overlay (edit bar → personalize, FR-7).
        PersonalizeSheet(
            visible = personalizeVisible,
            rightHalf = isLandscape,
            dark = dark,
            followSystemTheme = settings.followSystemTheme,
            onFollowSystemThemeChange = viewModel::setFollowSystemTheme,
            accentId = settings.accentId,
            glass = settings.glass,
            transparency = settings.transparency,
            blur = settings.blur,
            wallpaperId = settings.wallpaperId,
            customWallpaper = settings.customWallpaperUri != null,
            bingWallpaper = settings.bingWallpaper,
            onBingWallpaperChange = viewModel::setBingWallpaper,
            onBingHistory = { bingHistoryOpen = true },
            onAdjustWallpaper = { if (settings.customWallpaperUri != null) adjustingWallpaper = true },
            wallpaperSlideshowEnabled = settings.wallpaperSlideshowEnabled,
            onWallpaperSlideshowChange = viewModel::setWallpaperSlideshowEnabled,
            wallpaperSlideshowIntervalMin = settings.wallpaperSlideshowIntervalMin,
            onWallpaperSlideshowIntervalChange = viewModel::setWallpaperSlideshowInterval,
            wallpaperSlideshowCount = wallpaperSlideshowCount,
            onPickWallpaperSlideshowPhotos = {
                wallpaperSlideshowPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onClearWallpaperSlideshowPhotos = {
                scope.launch {
                    wallpaperSlideshowStore.setUris(emptyList())
                    withContext(Dispatchers.IO) { MediaImport.clearWallpaperSlideshow(context) }
                }
            },
            tiledWallpaper = settings.tiledWallpaper,
            onTiledWallpaperChange = viewModel::setTiledWallpaper,
            feedEnabled = settings.feedEnabled,
            onAddLiveTile = { appId ->
                viewModel.addLiveTile(appId)
                Toast.makeText(context, "added $appId tile", Toast.LENGTH_SHORT).show()
            },
            onSystemSettings = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            onThemeChange = viewModel::setTheme,
            onAccentChange = viewModel::setAccent,
            onGlassChange = viewModel::setGlass,
            onTransparencyChange = viewModel::setTransparency,
            onBlurChange = viewModel::setBlur,
            onWallpaperChange = viewModel::setWallpaper,
            onPickCustomWallpaper = {
                wallpaperPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onClearWallpaper = viewModel::clearWallpaper,
            onResetTileStyle = {
                viewModel.resetTileStyle()
                Toast.makeText(context, "tile style reset", Toast.LENGTH_SHORT).show()
            },
            photosSelected = photosCount,
            onPickPhotos = {
                photosPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onClearPhotos = {
                scope.launch {
                    photosStore.setUris(emptyList())
                    withContext(Dispatchers.IO) { MediaImport.clearPhotos(context) }
                }
            },
            contactsGranted = contactsGranted,
            calendarGranted = calendarGranted,
            locationGranted = locationGranted,
            onRequestContacts = {
                contactsLauncher.launch(android.Manifest.permission.READ_CONTACTS)
            },
            onRequestCalendar = {
                calendarLauncher.launch(android.Manifest.permission.READ_CALENDAR)
            },
            onRequestLocation = {
                locationLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            notificationsEnabled = notificationAccess,
            onNotificationAccess = {
                runCatching { context.startActivity(NotificationAccess.settingsIntent()) }
                    .onFailure {
                        Toast.makeText(context, "open settings to allow access", Toast.LENGTH_SHORT)
                            .show()
                    }
            },
            batteryOptimizationExempt = batteryExempt,
            batteryGuidanceNote = OemBatteryGuard.guidanceNote(),
            onBatteryExemption = { OemBatteryGuard.requestExemption(context) },
            cornerRadius = settings.cornerRadius,
            onCornerRadiusChange = viewModel::setCornerRadius,
            tileGap = settings.tileGap,
            onTileGapChange = viewModel::setTileGap,
            tileColorSource = settings.tileColorSource,
            onTileColorSourceChange = viewModel::setTileColorSource,
            tileFill = settings.tileFill,
            onTileFillChange = viewModel::setTileFill,
            fontStyle = settings.fontStyle,
            onFontStyleChange = viewModel::setFontStyle,
            columns = settings.columns,
            onColumnsChange = viewModel::setColumns,
            onAbout = viewModel::openAbout,
            onPersonalizeGuide = viewModel::openPersonalizeGuide,
            onFolders = viewModel::openFolders,
            onHiddenApps = viewModel::openHiddenApps,
            edgeStripEnabled = settings.edgeStripEnabled,
            onEdgeStrip = viewModel::openEdgeStrip,
            onBackupRestore = viewModel::openBackup,
            onDismiss = viewModel::closePersonalize,
        )

        // About sheet (personalize → about).
        AboutSheet(
            visible = aboutOpen,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            onDismiss = viewModel::closeAbout,
        )

        // How-to-personalize guide (personalize → guide; auto-shown once, see the
        // LaunchedEffect(personalizeOpen) above).
        PersonalizeGuideSheet(
            visible = personalizeGuideOpen,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            onDismiss = viewModel::closePersonalizeGuide,
        )

        // Hidden-apps sheet (personalize → hidden apps).
        HiddenAppsSheet(
            visible = hiddenAppsOpen,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            apps = hiddenAppEntries,
            onUnhide = { viewModel.unhide(it.packageName) },
            onDismiss = viewModel::closeHiddenApps,
        )

        // Edge-strip settings sheet (personalize → edge strip).
        EdgeStripSheet(
            visible = edgeStripOpen,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            enabled = settings.edgeStripEnabled,
            position = settings.edgeStripPosition,
            selectedApps = settings.edgeStripApps,
            installedApps = apps,
            backgroundId = settings.edgeStripBackgroundId,
            handleSize = settings.edgeStripHandleSize,
            onEnabledChange = viewModel::setEdgeStripEnabled,
            onPositionChange = viewModel::setEdgeStripPosition,
            onAppsChange = viewModel::setEdgeStripApps,
            onBackgroundChange = viewModel::setEdgeStripBackground,
            onHandleSizeChange = viewModel::setEdgeStripHandleSize,
            onDismiss = viewModel::closeEdgeStrip,
        )

        // Quick search (two-finger swipe-down on Start): apps, contacts, web.
        QuickSearchOverlay(
            visible = searchOpen,
            dark = dark,
            accentId = settings.accentId,
            apps = apps.filterNot { it.packageName in hiddenPackages },
            contactsGranted = contactsGranted,
            onRequestContacts = {
                contactsLauncher.launch(android.Manifest.permission.READ_CONTACTS)
            },
            onPinContact = viewModel::pinContact,
            onDismiss = viewModel::closeSearch,
        )

        // Layout history sheet (personalize → history).
        LayoutHistorySheet(
            visible = historyOpen,
            snapshots = layoutHistory,
            dark = dark,
            accentId = settings.accentId,
            onDismiss = viewModel::closeHistory,
            onRestore = viewModel::restoreFromSnapshot,
            onDelete = viewModel::deleteSnapshot,
            rightHalf = isLandscape,
        )

        // Backup & restore sheet (personalize → manage backups).
        BackupRestoreSheet(
            visible = backupVisible,
            dark = dark,
            accentId = settings.accentId,
            onDismiss = viewModel::closeBackup,
            onOpenHistory = viewModel::openHistory,
            onSaveSnapshot = {
                viewModel.closeBackup()
                viewModel.closePersonalize()
                captureRequested = true
            },
            onExportBackup = { backupExportLauncher.launch("tileshell-backup.json") },
            onRestoreBackup = { restoreConfirmPending = true },
            autoBackupEnabled = settings.autoBackupEnabled,
            autoBackupIntervalHours = settings.autoBackupIntervalHours,
            onAutoBackupEnabled = viewModel::setAutoBackupEnabled,
            onAutoBackupInterval = viewModel::setAutoBackupInterval,
            rightHalf = isLandscape,
        )

        // Build a name→packageNames map from the current tile list so CategoryFolderSheet
        // can detect which categories already have a folder and pre-check their members.
        val existingFoldersByName = remember(tiles) {
            tiles.filterIsInstance<TileModel.Folder>()
                .associate { folder ->
                    folder.name.lowercase() to folder.children.mapTo(HashSet()) { it.packageName }
                }
        }

        // Category-folders sheet (personalize → folders).
        CategoryFolderSheet(
            visible = foldersOpen,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            apps = apps,
            onCreate = { name, picked ->
                viewModel.createFolder(name, picked)
                val verb = if (existingFoldersByName.containsKey(name.lowercase())) "updated" else "created"
                Toast.makeText(context, "$verb \"$name\" folder", Toast.LENGTH_SHORT).show()
            },
            onDismiss = viewModel::closeFolders,
            existingFolderPackages = { name -> existingFoldersByName[name.lowercase()] ?: emptySet() },
        )

        // Full-screen folder overlay (FR-4).
        FolderOverlay(
            folder = openFolder,
            accent = accent,
            appIconColors = settings.tileColorSource == TileColorSource.APP_ICON,
            tileGap = settings.tileGap,
            columns = settings.columns,
            onClose = viewModel::closeFolder,
            onLaunchChild = { child ->
                launchFolderChild(context, child)
                viewModel.closeFolder()
            },
            onRename = { name -> openFolder?.let { viewModel.renameFolder(it.id, name) } },
            onPullOut = { child ->
                openFolder?.let { viewModel.removeFolderChild(it.id, child) }
            },
            onResize = { child ->
                openFolder?.let { viewModel.resizeFolderChild(it.id, child) }
            },
            onReorder = viewModel::reorderFolderChildren,
            onSetColor = viewModel::setFolderChildAccent,
            onMakeStack = { size -> openFolder?.let { viewModel.convertFolderToStack(it.id, size) } },
        )

        // Play Store update prompt: a thin dismissible strip pinned to the top,
        // never a takeover — see UpdateAvailableBanner for why.
        if (showUpdateBanner) {
            UpdateAvailableBanner(
                state = updateState,
                accent = accent,
                onAction = onUpdateAction,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        // First-run hint (S19): one-time prototype hint card over Start. Sits
        // above all other layers so it reads on a fresh install; self-hides once
        // seen.
        FirstRunHint(accentId = settings.accentId)

        // Wallpaper crop overlay: shown immediately after the user picks a photo so
        // they can drag to position the image before it becomes the live wallpaper.
        val cropUri = pendingWallpaperCropUri
        if (cropUri != null) {
            WallpaperCropOverlay(
                uri = cropUri,
                onConfirm = { alignX, alignY, zoom ->
                    viewModel.setCustomWallpaper(cropUri, alignX, alignY, zoom)
                    pendingWallpaperCropUri = null
                },
                onCancel = { pendingWallpaperCropUri = null },
                rightHalf = isLandscape,
            )
        }

        // Re-frame the active wallpaper (own photo or Bing image): same drag UI, but
        // only the alignment is written — the image/daily-mode are left untouched.
        val adjustUri = settings.customWallpaperUri
        if (adjustingWallpaper && adjustUri != null) {
            WallpaperCropOverlay(
                uri = adjustUri,
                initialAlignX = settings.wallpaperAlignX,
                initialAlignY = settings.wallpaperAlignY,
                initialZoom = settings.wallpaperZoom,
                onConfirm = { alignX, alignY, zoom ->
                    viewModel.setWallpaperAlignment(alignX, alignY, zoom)
                    adjustingWallpaper = false
                },
                onCancel = { adjustingWallpaper = false },
                rightHalf = isLandscape,
            )
        }

        // Recent-Bing-wallpapers viewer (personalize → "recent bing wallpapers").
        BingHistorySheet(
            visible = bingHistoryOpen,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            onPick = { imageUrl ->
                viewModel.applyBingImage(imageUrl)
                bingHistoryOpen = false
                Toast.makeText(context, "setting bing wallpaper…", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { bingHistoryOpen = false },
        )
    }
    }
}

@Composable
private fun StartPage(
    specs: List<TileSpec>,
    byId: Map<String, TileModel>,
    apps: List<com.tileshell.core.data.AppEntry>,
    scrollState: androidx.compose.foundation.ScrollState,
    chevronVisible: Boolean,
    edgeStripVisible: Boolean,
    editMode: Boolean,
    liveSuspended: Boolean,
    selectedTileId: String?,
    accent: Color,
    accentId: String,
    appIconColors: Boolean,
    tileGapPx: Float?,
    glass: Boolean,
    transparency: Float,
    glassLine: Color,
    tiledWallpaper: Boolean,
    wallpaper: com.tileshell.core.design.WallpaperGradient,
    wallpaperPhoto: ImageBitmap?,
    wallpaperAlignX: Float,
    wallpaperAlignY: Float,
    wallpaperZoom: Float,
    darkTheme: Boolean,
    notifications: NotificationSnapshot,
    widthPx: Float,
    viewportHeightPx: Float,
    statusBarTopPx: Float,
    columns: Int,
    onLockScreen: () -> Unit,
    onTile: (TileModel) -> Unit,
    onLaunchFolderChild: (FolderChild) -> Unit,
    onChevron: () -> Unit,
    onEnterEdit: (String) -> Unit,
    onSelectTile: (String) -> Unit,
    onExitEdit: () -> Unit,
    onReorder: (List<String>) -> Unit,
    onMerge: (dragId: String, targetId: String, survivingOrder: List<String>) -> Unit,
    onResize: (String) -> Unit,
    onUnpin: (String) -> Unit,
    onSetTileColor: (id: String, colorId: String?) -> Unit,
    onAdd: () -> Unit,
    onPersonalize: () -> Unit,
) {
    // Single jiggle phase shared by every tile (only composed while editing, so
    // it costs nothing on a resting Start screen). Even/odd tiles use opposite
    // signs so the grid shimmers like WP edit mode.
    val jigglePhase = rememberJigglePhase(editMode)
    val density = LocalDensity.current

    // Working order driving the grid. Mirrors the persisted order except during
    // a drag, when reorder mutates it live (the drop persists the result).
    val order = remember { mutableStateListOf<String>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    val dragOffset = remember { mutableStateOf(IntOffset.Zero) }
    // Tile currently highlighted as a merge target (finger in its centre zone).
    var mergeTargetId by remember { mutableStateOf<String?>(null) }
    // Tile whose accent-colour picker is open (edit-mode colour dot tapped), or null.
    var colorPickerFor by remember { mutableStateOf<String?>(null) }
    // Reconcile the working order with the persisted layout: keep the existing
    // relative order of surviving ids, drop removed ones, append new ones in
    // persisted order. This preserves a just-dropped reorder (the async DB write
    // lands the same order, so no flicker) while still absorbing pins/uninstalls.
    LaunchedEffect(specs) {
        if (draggingId != null) return@LaunchedEffect
        val ids = specs.map { it.id }
        val merged = if (order.isEmpty()) {
            ids
        } else {
            val present = ids.toHashSet()
            val kept = order.filter { it in present }
            val keptSet = kept.toHashSet()
            kept + ids.filter { it !in keptSet }
        }
        if (merged != order.toList()) {
            order.clear()
            order.addAll(merged)
        }
    }
    val displaySpecs = order.mapNotNull { id -> byId[id]?.let { TileSpec(id, it.size) } }

    // Live tiles (FR-2). The flip scheduler turns one of the visible flippable
    // tiles every ~2.6 s, paused whenever live tiles are gated off (edit mode,
    // off-screen, screen off, battery saver, animations off).
    val liveActive = rememberLiveTilesActive(suspended = editMode || liveSuspended)
    // Publish active media sessions into MediaCenter so the music tile and any
    // music-app tile (Apple Music, YT Music, …) can show their now-playing track.
    MediaSessionsEffect(active = liveActive)
    val liveIds = remember(displaySpecs, byId) {
        displaySpecs.mapNotNull { spec ->
            val model = byId[spec.id] as? TileModel.App ?: return@mapNotNull null
            spec.id.takeIf { LiveFace.forIconKey(model.iconKey, model.size)?.flips == true }
        }
    }
    val flipState = rememberFlipState(liveIds, active = liveActive)

    // Auto-scroll while a drag hovers near the top/bottom viewport edge (FR-3.2).
    var autoScroll by remember { mutableStateOf(0) } // -1 up, 0 off, +1 down
    LaunchedEffect(autoScroll) {
        if (autoScroll == 0) return@LaunchedEffect
        val speed = with(density) { 8.dp.toPx() }
        while (true) {
            val delta = autoScroll * speed
            val consumed = scrollState.scrollBy(delta)
            if (consumed == 0f) break // hit an edge
            withFrameNanos { }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Tapping empty space in edit mode exits (prototype onDown).
            .emptySpaceExit(editMode, onExitEdit),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .navigationBarsPadding()
                // Keep tiles clear of a display cutout (e.g. a landscape notch).
                .displayCutoutPadding(),
        ) {
            val editDrag = Modifier.editDragGesture(
                editMode = editMode,
                widthPx = widthPx,
                columns = columns,
                gapPx = tileGapPx,
                order = order,
                byId = byId,
                draggingId = { draggingId },
                selectedId = { selectedTileId },
                onUnpin = { id -> order.remove(id); onUnpin(id) },
                onOpenFolder = { id -> byId[id]?.let(onTile) },
                onResize = onResize,
                onColor = { id -> colorPickerFor = id },
                onLift = { id, offset -> draggingId = id; dragOffset.value = offset },
                onDrag = { offset -> dragOffset.value = offset },
                onReorderTo = { dragId, targetId ->
                    val next = reorderTiles(order.toList(), dragId, targetId)
                    if (next != order.toList()) {
                        order.clear()
                        order.addAll(next)
                    }
                },
                onMoveToEnd = { dragId ->
                    if (order.lastOrNull() != dragId && order.remove(dragId)) {
                        order.add(dragId)
                    }
                },
                onMergeMode = { dragId ->
                    // Park the dragged tile at the end so the other tiles settle
                    // into their natural slots beneath the floating tile.
                    if (order.lastOrNull() != dragId && order.remove(dragId)) {
                        order.add(dragId)
                    }
                },
                onMergeTarget = { id -> mergeTargetId = id },
                onAutoScroll = { dir -> autoScroll = dir },
                onDrop = { merge ->
                    autoScroll = 0
                    val drag = draggingId
                    if (merge != null && drag != null) {
                        // Optimistically drop the dragged tile; the merge write
                        // rewrites the target into a folder once it lands. The
                        // surviving order (drag removed) is persisted with it.
                        order.remove(drag)
                        onMerge(drag, merge, order.toList())
                    } else {
                        onReorder(order.toList())
                    }
                    draggingId = null
                    mergeTargetId = null
                },
                onSelect = onSelectTile,
                onTapExit = onExitEdit,
                contentTopPx = statusBarTopPx,
                viewportHeightPx = viewportHeightPx,
                scrollOffsetPx = { scrollState.value.toFloat() },
                edgeZonePx = with(density) { 64.dp.toPx() },
            )

            DenseTileGrid(
                tiles = displaySpecs,
                columns = columns,
                gapPx = tileGapPx,
                modifier = Modifier.fillMaxWidth().then(editDrag),
            ) { spec, slot, sizePx ->
                val model = byId[spec.id] ?: return@DenseTileGrid
                val dragging = spec.id == draggingId
                val slotState = animateIntOffsetAsState(slot, label = "slot")
                val index = displaySpecs.indexOfFirst { it.id == spec.id }
                Box(
                    modifier = Modifier
                        .offset { if (dragging) dragOffset.value else slotState.value }
                        .zIndex(if (dragging) 10f else 0f)
                        .size(
                            with(density) { sizePx.width.toDp() },
                            with(density) { sizePx.height.toDp() },
                        ),
                ) {
                    // Per-tile accent (FR-7): a saved override (palette id or exact
                    // #hex) always wins; otherwise, in app-icon-colour mode an app
                    // tile takes its icon's dominant colour, else the global accent.
                    val tileOverride = when (model) {
                        is TileModel.App -> model.accentOverride
                        is TileModel.Folder -> model.accentOverride
                    }
                    val iconColor = (model as? TileModel.App)
                        ?.takeIf { appIconColors && tileOverride == null && it.packageName.isNotBlank() }
                        ?.let { rememberDominantIconColor(it.packageName, it.activityName) }
                    val tileAccent = when {
                        tileOverride != null -> TileAccents.colorForOverride(tileOverride, accentId)
                        iconColor != null -> iconColor
                        else -> accent
                    }
                    TileView(
                        tile = model,
                        index = index,
                        editMode = editMode,
                        selected = editMode && model.id == selectedTileId,
                        dragging = dragging,
                        mergeTarget = model.id == mergeTargetId,
                        accent = tileAccent,
                        glass = glass,
                        transparency = transparency,
                        glassLine = glassLine,
                        tiledWallpaper = tiledWallpaper,
                        wallpaper = wallpaper,
                        wallpaperPhoto = wallpaperPhoto,
                        wallpaperAlignX = wallpaperAlignX,
                        wallpaperAlignY = wallpaperAlignY,
                        wallpaperZoom = wallpaperZoom,
                        // This tile's window onto the screen-fixed wallpaper: its live
                        // on-screen top-left (grid slot minus the scroll offset, below
                        // the status bar). Read in the draw phase, so the wallpaper
                        // stays put while the tiles scroll over it.
                        wallpaperOrigin = {
                            Offset(
                                slot.x.toFloat(),
                                statusBarTopPx + slot.y.toFloat() - scrollState.value.toFloat(),
                            )
                        },
                        fullWidth = widthPx,
                        fullHeight = viewportHeightPx,
                        jigglePhase = jigglePhase,
                        flipped = flipState.isFlipped(model.id),
                        liveActive = liveActive,
                        badgeCount = when (model) {
                            is TileModel.App -> notifications.badgeFor(model.packageName)
                            // A folder aggregates the unread counts of its children,
                            // so a folder of mail/chat apps surfaces a single summed
                            // badge (de-duped by package — multiple activities of one
                            // app count once).
                            is TileModel.Folder -> model.children
                                .map { it.packageName }.distinct()
                                .sumOf { notifications.badgeFor(it) }
                        },
                        darkTheme = darkTheme,
                        canMoveBack = order.indexOf(model.id) > 0,
                        canMoveForward = order.indexOf(model.id) in 0 until order.size - 1,
                        showColorDot = true,
                        // Inline tap-to-launch: always for medium/wide folders
                        // (cells stay tappable); for a small folder only on the
                        // roomy 4-column grid (too tiny on 5/6 columns).
                        inlineFolderLaunch = model.size != TileSize.SMALL || columns == 4,
                        appIconColors = appIconColors,
                        nextSizeIsLarger = model.size.nextIsLarger(
                            largeAllowed = (model as? TileModel.App)?.let { appTile ->
                                AppCategories.allowsLargeTile(
                                    iconKey = appTile.iconKey,
                                    app = apps.firstOrNull { entry -> entry.packageName == appTile.packageName },
                                    columns = columns,
                                )
                            } ?: false,
                        ),
                        onTap = { if (!editMode) onTile(model) },
                        onLongPress = { if (!editMode) onEnterEdit(model.id) },
                        onLaunchFolderChild = onLaunchFolderChild,
                        onResize = { onResize(model.id) },
                        onUnpin = { order.remove(model.id); onUnpin(model.id) },
                        onSelect = { onSelectTile(model.id) },
                        onExitEdit = onExitEdit,
                        onMove = { dir ->
                            val i = order.indexOf(model.id)
                            val j = i + dir
                            if (i >= 0 && j in order.indices) {
                                val next = reorderTiles(order.toList(), model.id, order[j])
                                if (next != order.toList()) {
                                    order.clear()
                                    order.addAll(next)
                                    onReorder(next)
                                }
                            }
                        },
                    )
                }
            }
            // FR-1 bottom breathing room (prototype home-scroll padding-bottom:74px;
            // grows to clear the edit bar while editing, like .home-scroll padding).
            Spacer(Modifier.height(if (editMode) 130.dp else 74.dp))
        }

        // App-list affordance (prototype .allapps-btn) with a settings button just
        // below it; both hidden in edit mode (personalize is on the edit bar there).
        // Original order restored (chevron above gear); bottom offset animates up to
        // clear the edge strip's full expanded height only while it's actually visible,
        // and eases back down to the original resting position once it isn't.
        if (chevronVisible) {
            val iconsBottomOffset by animateDpAsState(
                targetValue = if (edgeStripVisible) STRIP_THICK + 8.dp else 26.dp,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
                label = "startIconsBottomOffset",
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 14.dp, bottom = iconsBottomOffset),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 48dp min touch targets (a11y) — icons stay smaller inside.
                Box(
                    modifier = Modifier.size(48.dp).clickable(onClick = onChevron),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TileIcons["chevron"],
                        contentDescription = "open app list",
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(28.dp),
                    )
                }
                Box(
                    modifier = Modifier.size(48.dp).combinedClickable(
                        onClick = onPersonalize,
                        onLongClick = onLockScreen,
                        onLongClickLabel = "lock screen",
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TileIcons["settings"],
                        contentDescription = "settings",
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }

        // Bottom edit bar (prototype .edit-bar): slides up while editing.
        EditBar(
            visible = editMode,
            onAdd = onAdd,
            onPersonalize = onPersonalize,
            onDone = onExitEdit,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // Per-tile accent picker (edit-mode colour dot → palette, FR-7).
        colorPickerFor?.let { pickId ->
            val model = byId[pickId]
            val app = model as? TileModel.App
            val current = when (model) {
                is TileModel.App -> model.accentOverride
                is TileModel.Folder -> model.accentOverride
                else -> null
            }
            // Icon suggestion only for an app tile (a folder has no single icon).
            val suggestion = if (app != null && app.packageName.isNotBlank()) {
                rememberIconSuggestion(app.packageName, app.activityName)
            } else {
                null
            }
            TileColorPicker(
                current = current,
                suggestedNearestId = suggestion?.nearestId,
                suggestedExact = suggestion?.exact,
                onPick = { colorId -> onSetTileColor(pickId, colorId); colorPickerFor = null },
                onDismiss = { colorPickerFor = null },
            )
        }
    }
}

/**
 * Bottom-sheet accent picker for a single tile (FR-7): a "follow global" chip,
 * two optional icon-derived suggestions — the exact dominant colour
 * ([suggestedExact], stored as a `#hex` override) and the nearest palette accent
 * ([suggestedNearestId]) — then the 14 palette swatches, over a tap-to-dismiss
 * scrim. [current] is the tile's saved override (null = following global), shown
 * ringed wherever it matches.
 */
@Composable
private fun BoxScope.TileColorPicker(
    current: String?,
    suggestedNearestId: String?,
    suggestedExact: Color?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val exactHex = suggestedExact?.let { "#%06X".format(it.toArgb() and 0xFFFFFF) }
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    )
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Color(0xFF1A1A1F))
            .navigationBarsPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(20.dp),
    ) {
        Text("tile colour", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .border(
                    1.dp,
                    if (current == null) Color.White else Color.White.copy(alpha = 0.3f),
                    RoundedCornerShape(20.dp),
                )
                .clickable { onPick(null) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("use default colour", color = Color.White, fontSize = 13.sp)
        }
        if (suggestedExact != null && exactHex != null) {
            Spacer(Modifier.height(10.dp))
            SuggestionRow(
                swatch = suggestedExact,
                label = "exact · from icon",
                ringed = current == exactHex,
                onClick = { onPick(exactHex) },
            )
        }
        if (suggestedNearestId != null) {
            Spacer(Modifier.height(10.dp))
            SuggestionRow(
                swatch = TileAccents.forId(suggestedNearestId),
                label = "nearest accent",
                ringed = current == suggestedNearestId,
                onClick = { onPick(suggestedNearestId) },
            )
        }
        Spacer(Modifier.height(14.dp))
        TileColors.IDS.chunked(7).forEach { rowIds ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                rowIds.forEach { id ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(TileAccents.forId(id))
                            .border(
                                width = if (current == id) 2.dp else 0.dp,
                                color = if (current == id) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .clickable { onPick(id) },
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        // A small white dot badges the nearest-accent suggestion
                        // so it's findable within the grid too.
                        if (id == suggestedNearestId) {
                            Box(
                                modifier = Modifier
                                    .padding(3.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A pill row in the colour picker: a colour [swatch] + [label], ringed when it
 *  is the tile's current choice. Used for the exact / nearest icon suggestions. */
@Composable
private fun SuggestionRow(
    swatch: Color,
    label: String,
    ringed: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(
                if (ringed) 2.dp else 1.dp,
                if (ringed) Color.White else Color.White.copy(alpha = 0.3f),
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(swatch),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

/**
 * The spoken label for a tile: the app/folder name, any unread count, and — while
 * editing — the current size and selection, so a TalkBack user knows the state
 * before invoking resize/move.
 */
internal fun tileAccessibilityLabel(
    tile: TileModel,
    badgeCount: Int,
    editMode: Boolean,
    selected: Boolean,
): String = buildString {
    when (tile) {
        is TileModel.App -> {
            append(tile.label ?: tile.iconKey ?: "app")
            if (badgeCount > 0) append(", $badgeCount new")
        }
        is TileModel.Folder -> {
            append(tile.name)
            append(" folder, ${tile.children.size} ")
            append(if (tile.children.size == 1) "app" else "apps")
        }
    }
    if (editMode) {
        val size = when (tile.size) {
            TileSize.SMALL -> "small"
            TileSize.MEDIUM -> "medium"
            TileSize.WIDE -> "wide"
            TileSize.LARGE -> "large"
        }
        append(", $size tile")
        if (selected) append(", selected")
    }
}

@Composable
private fun TileView(
    tile: TileModel,
    index: Int,
    editMode: Boolean,
    selected: Boolean,
    dragging: Boolean,
    mergeTarget: Boolean,
    accent: Color,
    glass: Boolean,
    transparency: Float,
    glassLine: Color,
    tiledWallpaper: Boolean,
    wallpaper: com.tileshell.core.design.WallpaperGradient,
    wallpaperPhoto: ImageBitmap?,
    wallpaperAlignX: Float,
    wallpaperAlignY: Float,
    wallpaperZoom: Float,
    wallpaperOrigin: () -> Offset,
    fullWidth: Float,
    fullHeight: Float,
    jigglePhase: Float,
    flipped: Boolean,
    liveActive: Boolean,
    badgeCount: Int,
    darkTheme: Boolean,
    canMoveBack: Boolean,
    canMoveForward: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onResize: () -> Unit,
    onUnpin: () -> Unit,
    onSelect: () -> Unit,
    onExitEdit: () -> Unit,
    onMove: (direction: Int) -> Unit,
    onLaunchFolderChild: (FolderChild) -> Unit = {},
    showColorDot: Boolean = false,
    inlineFolderLaunch: Boolean = false,
    appIconColors: Boolean = false,
    nextSizeIsLarger: Boolean = false,
) {
    // TalkBack reads the whole tile as one node: the app/folder name plus state,
    // with the launch/edit operations exposed as semantic actions (the visual
    // drag/corner-control flow is sighted-only, so screen-reader users drive the
    // exact same ViewModel calls through these custom actions instead).
    val a11yLabel = tileAccessibilityLabel(tile, badgeCount, editMode, selected)

    val tileCornerRadius = LocalTileCornerRadius.current
    val useTileGradient = LocalTileGradient.current
    // Glass fill tinted by this tile's own resolved accent (see Glass.kt) —
    // computed here, not passed in, so every tile (including each stack member
    // / folder cell below) tints by its own colour rather than one shared value.
    val glassFill = if (glass) Glass.fill(darkTheme, transparency, accent) else null
    // A widget stack owns its own tap/long-press (tap = launch current member,
    // long-press = enter edit mode), so the outer tile gesture is suppressed for it;
    // in edit mode the grid drag owns interaction for every tile.
    val isStackTile = tile is TileModel.Folder && tile.isStack

    // Edit chrome (prototype CSS): non-selected tiles dim to .45, the selected
    // tile scales to 1.04, and editing tiles jiggle (±.5°, alternating phase).
    // A dragged tile lifts: scales up with a shadow and ignores dim/jiggle. A
    // merge target stays full-opacity and gets a highlight outline.
    val alpha by animateFloatAsState(
        targetValue = if (editMode && !selected && !dragging && !mergeTarget) 0.45f else 1f,
        label = "tileAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (dragging) 1.08f else if (selected) 1.04f else 1f,
        label = "tileScale",
    )
    val elevation by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        label = "tileElevation",
    )
    val rotation = if (editMode && !dragging) (if (index % 2 == 0) jigglePhase else -jigglePhase) else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
                shadowElevation = elevation * 18.dp.toPx()
            }
            // The press-tilt effect (S7) is replaced by the jiggle while editing.
            .then(if (editMode) Modifier else Modifier.tiltOnPress())
            // Optional rounded corners (personalisation setting 0–20 dp).
            .then(
                if (tileCornerRadius > 0f)
                    Modifier.clip(RoundedCornerShape(tileCornerRadius.dp))
                else Modifier
            )
            // Tile fill, in priority order:
            //  • "wallpaper behind tiles" → a window onto the screen-anchored
            //    wallpaper (custom photo if set, else the bundled gradient), with a
            //    dark hairline so the tiles read as distinct windows.
            //  • glass (FR-7) → translucent fill + inset hairline.
            //  • otherwise the single global accent (one tile colour across Start,
            //    default blue — the per-tile colourId is ignored).
            .then(
                when {
                    tiledWallpaper && wallpaperPhoto != null -> Modifier.photoWindow(
                        image = wallpaperPhoto,
                        fullWidth = fullWidth,
                        fullHeight = fullHeight,
                        darkBase = colorTokens(darkTheme).bg,
                        origin = wallpaperOrigin,
                        alignX = wallpaperAlignX,
                        alignY = wallpaperAlignY,
                        zoom = wallpaperZoom,
                    )
                    tiledWallpaper -> Modifier.wallpaperWindow(
                        wallpaper = wallpaper,
                        fullWidth = fullWidth,
                        fullHeight = fullHeight,
                        origin = wallpaperOrigin,
                        dark = darkTheme,
                    )
                    else -> if (glassFill != null) {
                        Modifier.background(glassFill)
                    } else if (useTileGradient) {
                        Modifier.background(tileGradientBrush(accent))
                    } else {
                        Modifier.background(accent)
                    }
                },
            )
            .then(
                when {
                    tiledWallpaper -> Modifier.border(
                        1.dp, TiledTileBorder,
                        RoundedCornerShape(tileCornerRadius.dp),
                    )
                    glassFill != null -> Modifier.border(
                        1.dp, glassLine,
                        RoundedCornerShape(tileCornerRadius.dp),
                    )
                    else -> Modifier
                },
            )
            // Merge-target highlight (prototype .merge-target: 3px inset outline).
            .then(
                if (mergeTarget) Modifier.border(3.dp, DarkColorTokens.fg) else Modifier,
            )
            // Out of edit mode the tile owns tap-to-launch / long-press-to-edit;
            // in edit mode the grid-level drag gesture owns all interaction.
            // Stack tiles handle their own tap/long-press inside StackTileContent
            // (tap = launch current member, long-press = open folder overlay), so
            // the outer gesture is suppressed for them too.
            .then(
                if (editMode || isStackTile) Modifier
                else Modifier.tileGesture(onTap = onTap, onLongPress = onLongPress),
            )
            // Accessibility: collapse the tile to a single labelled button and
            // expose its operations as actions (TalkBack), replacing the inert
            // descendant icon/label/live-face semantics.
            .clearAndSetSemantics {
                contentDescription = a11yLabel
                role = Role.Button
                if (editMode) {
                    onClick(label = "select") { onSelect(); true }
                    customActions = buildList {
                        add(CustomAccessibilityAction("resize") { onResize(); true })
                        add(CustomAccessibilityAction("unpin") { onUnpin(); true })
                        if (canMoveBack) {
                            add(CustomAccessibilityAction("move back") { onMove(-1); true })
                        }
                        if (canMoveForward) {
                            add(CustomAccessibilityAction("move forward") { onMove(1); true })
                        }
                        add(CustomAccessibilityAction("done editing") { onExitEdit(); true })
                    }
                } else {
                    val verb = if (tile is TileModel.Folder) "open" else "launch"
                    onClick(label = verb) { onTap(); true }
                    customActions = listOf(
                        CustomAccessibilityAction("customize") { onLongPress(); true },
                    )
                }
            },
    ) {
        when (tile) {
            is TileModel.App -> AppTileContent(
                tile,
                flipped = flipped,
                liveActive = liveActive,
                interactive = !editMode,
            )
            is TileModel.Folder ->
                if (tile.isStack) {
                    StackTileContent(
                        tile = tile,
                        editMode = editMode,
                        selected = selected,
                        liveActive = liveActive,
                        accent = accent,
                        appIconColors = appIconColors,
                        glass = glass,
                        transparency = transparency,
                        tiledWallpaper = tiledWallpaper,
                        darkTheme = darkTheme,
                        wallpaper = wallpaper,
                        wallpaperPhoto = wallpaperPhoto,
                        wallpaperAlignX = wallpaperAlignX,
                        wallpaperAlignY = wallpaperAlignY,
                        wallpaperZoom = wallpaperZoom,
                        wallpaperOrigin = wallpaperOrigin,
                        fullWidth = fullWidth,
                        fullHeight = fullHeight,
                        onLaunchChild = onLaunchFolderChild,
                        onEnterEdit = onLongPress,
                    )
                } else {
                    FolderTileContent(
                        tile = tile,
                        editMode = editMode,
                        launchEnabled = inlineFolderLaunch,
                        appIconColors = appIconColors,
                        glass = glass,
                        transparency = transparency,
                        darkTheme = darkTheme,
                        tiledWallpaper = tiledWallpaper,
                        onLaunchChild = onLaunchFolderChild,
                        onOpenFolder = onTap,
                        onEnterEdit = onLongPress,
                    )
                }
        }
        // Per-app notification badge (FR-1.2). Top-right pill, count from the
        // notification listener; sized down on small tiles (prototype .badge).
        if (badgeCount > 0) {
            NotificationBadge(
                count = badgeCount,
                dark = darkTheme,
                small = tile.size == TileSize.SMALL,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        // Selected tiles show corner controls: folder/stack tiles get a folder icon
        // at top-left (tap opens the overlay to manage members one-by-one); app
        // tiles get the standard close icon. Stacks suppress resize and colour dot.
        if (selected) {
            when {
                isStackTile -> StackEditControls()
                tile is TileModel.Folder -> TileControls(showColor = showColorDot, dotColor = accent, nextSizeIsLarger = nextSizeIsLarger, isFolder = true)
                else -> TileControls(showColor = showColorDot, dotColor = accent, nextSizeIsLarger = nextSizeIsLarger)
            }
        }
    }
}

/**
 * The prototype `.badge`: a rounded count pill in the tile's top-right corner.
 * White on dark themes, inverted on light (`#screen.light .badge`). Shrinks on
 * small tiles. Counts over 99 read "99+" so the pill keeps its shape.
 */
@Composable
private fun NotificationBadge(
    count: Int,
    dark: Boolean,
    small: Boolean,
    modifier: Modifier = Modifier,
) {
    val bg = if (dark) Color.White else Color(0xFF111111)
    val fg = if (dark) Color(0xFF111111) else Color.White
    val diameter = if (small) 18.dp else 22.dp
    val inset = if (small) 5.dp else 8.dp
    Box(
        modifier = modifier
            .padding(top = inset, end = inset)
            .defaultMinSize(minWidth = diameter, minHeight = diameter)
            .height(diameter)
            .background(bg, CircleShape)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = fg,
            fontSize = if (small) 11.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Corner controls shown on the selected tile in edit mode (prototype
 * `.tile-controls`): top-left shows a close icon for app tiles or a folder icon
 * for folder tiles (opens the overlay to pull apps out one-by-one); resize is
 * bottom-right; and — for app tiles ([showColor]) — a colour dot bottom-left.
 * These are the visual affordance; the taps are handled by the grid's
 * [editDragGesture] corner hot-zones (FR-3.4/3.5/7).
 */
@Composable
private fun BoxScope.TileControls(showColor: Boolean, dotColor: Color, nextSizeIsLarger: Boolean, isFolder: Boolean = false) {
    TileControl(
        iconKey = if (isFolder) "folder" else "close",
        description = if (isFolder) "open folder" else "unpin",
        modifier = Modifier.align(Alignment.TopStart),
    )
    TileControl(
        iconKey = if (nextSizeIsLarger) "resize" else "resize-shrink",
        description = "resize",
        modifier = Modifier.align(Alignment.BottomEnd),
    )
    if (showColor) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White)
                .padding(3.dp)
                .clip(CircleShape)
                .background(dotColor),
            contentAlignment = Alignment.Center,
        ) {}
    }
}

/**
 * Corner controls for a **selected widget stack** in edit mode. Shows a folder
 * icon at TopStart — handled by [editDragGesture]'s top-left zone, which opens
 * the folder overlay so the user can pull members out one by one. No resize or
 * colour dot — stacks are fixed at 3×3 and member colours are set per-member in
 * the overlay.
 */
@Composable
private fun BoxScope.StackEditControls() {
    TileControl(
        iconKey = "folder",
        description = "open stack members",
        modifier = Modifier.align(Alignment.TopStart),
    )
}

@Composable
private fun TileControl(iconKey: String, description: String, modifier: Modifier) {
    // No background chip — the close/resize glyphs sit directly on the tile's own
    // fill, tinted white like the tile's icon/label.
    Box(
        modifier = modifier.size(26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TileIcons[iconKey],
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Bottom edit bar (prototype `.edit-bar`): add / personalize / done, sliding up
 * from below while editing. add → app list (with a hint toast), personalize →
 * stub sheet, done → exit edit.
 */
@Composable
private fun EditBar(
    visible: Boolean,
    onAdd: () -> Unit,
    onPersonalize: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offset by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(260),
        label = "editBarOffset",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = size.height * offset }
            .background(LocalColorTokens.current.sheet)
            .navigationBarsPadding()
            .height(60.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditBarButton("plus", "add", enabled = true, onClick = onAdd)
        Spacer(Modifier.size(34.dp))
        EditBarButton("settings", "personalize", enabled = true, onClick = onPersonalize)
        Spacer(Modifier.size(34.dp))
        EditBarButton("check", "done", enabled = true, onClick = onDone)
    }
}

/**
 * Full-screen folder overlay (FR-4): a translucent scrim over the blurred Start
 * screen, a close button, the lowercase folder title, and a grid of child tiles.
 *
 * Edit interaction mirrors the Start screen exactly (the same [TileView] +
 * [editDragGesture], minus folder-merge): tapping a child launches it; holding a
 * child enters edit (jiggle + corner controls); in edit mode dragging reorders,
 * the top-left × removes the app from the folder ([onPullOut], returning it to
 * Start), the bottom-right ⤢ toggles its size between small and medium (folders
 * allow only those two), and tapping empty space exits edit. The grid scrolls
 * (with edge auto-scroll while dragging) so a folder taller than the screen is
 * fully reachable. Long-pressing the title renames it. Renders nothing when
 * [folder] is null.
 */
@Composable
private fun FolderOverlay(
    folder: TileModel.Folder?,
    accent: Color,
    appIconColors: Boolean,
    tileGap: Float = 3f,
    columns: Int = GridPacker.COLUMNS,
    onClose: () -> Unit,
    onLaunchChild: (FolderChild) -> Unit,
    onRename: (String) -> Unit,
    onPullOut: (FolderChild) -> Unit,
    onResize: (FolderChild) -> Unit,
    onReorder: (List<FolderChild>) -> Unit,
    onSetColor: (FolderChild, String?) -> Unit = { _, _ -> },
    onMakeStack: (TileSize) -> Unit = {},
) {
    if (folder == null) return
    val density = LocalDensity.current
    val tileGapPx = with(density) { tileGap.dp.toPx() }
    var renaming by remember(folder.id) { mutableStateOf(false) }
    var editMode by remember(folder.id) { mutableStateOf(false) }
    var selectedId by remember(folder.id) { mutableStateOf<String?>(null) }
    // Child whose accent-colour picker is open (edit-mode colour dot tapped), or null.
    var colorPickerFor by remember(folder.id) { mutableStateOf<String?>(null) }

    // Keep media sessions live while the folder is open. The Start screen's
    // own MediaSessionsEffect poll is suspended whenever a folder/personalize
    // overlay is up, and per-controller MediaController callbacks are unreliable
    // on many players — so without this a music-app tile *inside* a folder would
    // freeze: album art wouldn't change on prev/next and the play/pause glyph
    // wouldn't track stop/resume. Polling here (gated like Start, paused only in
    // folder edit) keeps now-playing fresh and the transport buttons in sync.
    val liveActive = rememberLiveTilesActive(suspended = editMode)
    MediaSessionsEffect(active = liveActive)

    // Per-app notification counts for the child tiles. The folder *tile* shows an
    // aggregated badge on the Start screen; opened, each child app should show its
    // own count — same source (the process-wide notification snapshot).
    val notifications by NotificationCenter.snapshot.collectAsStateWithLifecycle()

    // Android back: edit → exit edit; otherwise close the folder.
    BackHandler(enabled = true) {
        if (editMode) { editMode = false; selectedId = null } else onClose()
    }

    // Child app models + lookup, keyed by the child's own DB rowId. Component
    // string (packageName/activityName) isn't unique enough: self-contained
    // liveOnly children (weather/calendar/clock with no resolvable app) can share
    // the exact same blank "" / "" identity, which used to collapse them onto one
    // slot here — one child effectively invisible, its neighbour rendered twice.
    val byId = remember(folder.children, folder.colorId) {
        folder.children.associate { c ->
            val key = c.rowId.toString()
            key to (TileModel.App(
                id = key,
                position = 0,
                size = c.size,
                colorId = folder.colorId,
                packageName = c.packageName,
                activityName = c.activityName,
                label = c.label,
                iconKey = c.iconKey,
                accentOverride = c.accentOverride,
            ) as TileModel)
        }
    }
    val childByKey = remember(folder.children) {
        folder.children.associateBy { it.rowId.toString() }
    }

    // Working order, reconciled with the persisted children (preserves a just-
    // dropped reorder while absorbing resizes / removals — same as StartPage).
    val order = remember(folder.id) { mutableStateListOf<String>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    val dragOffset = remember { mutableStateOf(IntOffset.Zero) }
    LaunchedEffect(folder.children) {
        if (draggingId != null) return@LaunchedEffect
        val ids = folder.children.map { it.rowId.toString() }
        val merged = if (order.isEmpty()) {
            ids
        } else {
            val present = ids.toHashSet()
            val kept = order.filter { it in present }
            val keptSet = kept.toHashSet()
            kept + ids.filter { it !in keptSet }
        }
        if (merged != order.toList()) { order.clear(); order.addAll(merged) }
    }
    val displaySpecs = order.mapNotNull { id -> byId[id]?.let { TileSpec(id, it.size) } }

    fun persistOrder() = onReorder(order.mapNotNull { childByKey[it] })

    val jigglePhase = rememberJigglePhase(editMode)
    val scrollState = rememberScrollState()
    // Grid top in root coords at scroll 0 — feeds the auto-scroll edge maths.
    var gridTopRoot by remember { mutableStateOf(0f) }
    // Auto-scroll while a drag hovers near the top/bottom viewport edge.
    var autoScroll by remember { mutableStateOf(0) }
    LaunchedEffect(autoScroll) {
        if (autoScroll == 0) return@LaunchedEffect
        val speed = with(density) { 8.dp.toPx() }
        while (true) {
            val consumed = scrollState.scrollBy(autoScroll * speed)
            if (consumed == 0f) break
            withFrameNanos { }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Scrim (prototype rgba(8,8,12,.55)). In edit mode an empty-space tap
            // leaves edit; otherwise it dismisses the folder.
            .background(Color(0x8C08080C))
            .emptySpaceExit(editMode) { editMode = false; selectedId = null },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val viewportHeightPx = constraints.maxHeight.toFloat()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Close button, top-left (hidden while renaming).
            Box(modifier = Modifier.fillMaxWidth()) {
                if (!renaming) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 6.dp, start = 14.dp)
                            .size(48.dp)
                            .clickable(onClick = onClose, role = Role.Button),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = TileIcons["close"],
                            contentDescription = "close folder",
                            tint = DarkColorTokens.fg,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // Title (long-press to rename; suppressed in edit mode) or inline editor.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (renaming) {
                    FolderTitleEditor(
                        initial = folder.name,
                        onCommit = { name -> onRename(name); renaming = false },
                    )
                } else {
                    Text(
                        text = folder.name.lowercase(),
                        color = DarkColorTokens.fg,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Thin,
                        modifier = Modifier.pointerInput(folder.id) {
                            detectTapGestures(onLongPress = { if (!editMode) renaming = true })
                        },
                    )
                }
            }

            // Contextual hint.
            Text(
                text = if (editMode) "tap × to remove  ·  drag to reorder  ·  tap ⤢ to resize"
                       else "tap to open  ·  hold to edit",
                color = DarkColorTokens.fg.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = TextAlign.Center,
            )

            // "Make stack" shortcut: resize every child to a uniform WIDE or
            // LARGE size in one shot, turning the folder into a widget stack
            // carousel — the same end state as resizing every child by hand,
            // or merging two large tiles (large is offered on any column count).
            // "keep as folder" is shown alongside as a plain, current-state chip
            // so the mini-grid isn't presented as if stacking were the only
            // option. Any of the three choices closes the overlay — it's a
            // one-time decision point, not something to keep browsing after.
            // Hidden once it's already a stack, or a lone-child folder (which
            // wouldn't read as a meaningful carousel).
            if (!editMode && !renaming && !folder.isStack && folder.children.size >= 2) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                ) {
                    StackModeChip(label = "keep as folder", selected = true, onClick = onClose)
                    Spacer(Modifier.width(10.dp))
                    StackModeChip(
                        label = "make wide stack",
                        onClick = { onMakeStack(TileSize.WIDE); onClose() },
                    )
                    Spacer(Modifier.width(10.dp))
                    StackModeChip(
                        label = "make large stack",
                        onClick = { onMakeStack(TileSize.LARGE); onClose() },
                    )
                }
            }

            // Grid-level edit-drag (same gesture as Start, merge disabled).
            val editDrag = Modifier.editDragGesture(
                editMode = editMode,
                widthPx = widthPx,
                columns = columns,
                gapPx = tileGapPx,
                order = order,
                byId = byId,
                draggingId = { draggingId },
                selectedId = { selectedId },
                onUnpin = { id -> order.remove(id); childByKey[id]?.let(onPullOut) },
                onResize = { id -> childByKey[id]?.let(onResize) },
                onColor = { id -> colorPickerFor = id },
                onLift = { id, offset -> draggingId = id; dragOffset.value = offset },
                onDrag = { offset -> dragOffset.value = offset },
                onReorderTo = { dragId, targetId ->
                    val next = reorderTiles(order.toList(), dragId, targetId)
                    if (next != order.toList()) { order.clear(); order.addAll(next) }
                },
                onMergeMode = {},
                onMergeTarget = {},
                onAutoScroll = { dir -> autoScroll = dir },
                onDrop = {
                    autoScroll = 0
                    if (draggingId != null) persistOrder()
                    draggingId = null
                },
                onSelect = { selectedId = it },
                onTapExit = { editMode = false; selectedId = null },
                contentTopPx = gridTopRoot + scrollState.value,
                viewportHeightPx = viewportHeightPx,
                scrollOffsetPx = { scrollState.value.toFloat() },
                edgeZonePx = with(density) { 64.dp.toPx() },
                allowMerge = false,
            )

            DenseTileGrid(
                tiles = displaySpecs,
                columns = columns,
                gapPx = tileGapPx,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { gridTopRoot = it.positionInRoot().y + scrollState.value }
                    .then(editDrag),
            ) { spec, slot, sizePx ->
                val model = byId[spec.id] ?: return@DenseTileGrid
                val dragging = spec.id == draggingId
                val slotState = animateIntOffsetAsState(slot, label = "folderSlot")
                val index = displaySpecs.indexOfFirst { it.id == spec.id }
                Box(
                    modifier = Modifier
                        .offset { if (dragging) dragOffset.value else slotState.value }
                        .zIndex(if (dragging) 10f else 0f)
                        .size(
                            with(density) { sizePx.width.toDp() },
                            with(density) { sizePx.height.toDp() },
                        ),
                ) {
                    // The child keeps its own colour inside the open folder: an
                    // explicit override wins, else its icon colour in app-icon mode,
                    // else the global accent.
                    val childApp = model as? TileModel.App
                    val childAccent = childApp?.accentOverride
                        ?.let { TileAccents.colorForOverride(it, "blue") }
                        ?: childApp?.takeIf { appIconColors && it.packageName.isNotBlank() }
                            ?.let { rememberDominantIconColor(it.packageName, it.activityName) }
                        ?: accent
                    TileView(
                        tile = model,
                        index = index,
                        editMode = editMode,
                        selected = editMode && spec.id == selectedId,
                        dragging = dragging,
                        mergeTarget = false,
                        accent = childAccent,
                        glass = false,
                        transparency = 0f,
                        glassLine = Color.Transparent,
                        tiledWallpaper = false,
                        wallpaper = Wallpapers.forId(NONE_ID),
                        wallpaperPhoto = null,
                        wallpaperAlignX = 0.5f,
                        wallpaperAlignY = 0.5f,
                        wallpaperZoom = 1f,
                        wallpaperOrigin = { Offset.Zero },
                        fullWidth = widthPx,
                        fullHeight = viewportHeightPx,
                        jigglePhase = jigglePhase,
                        flipped = false,
                        liveActive = liveActive,
                        badgeCount = childByKey[spec.id]?.let {
                            notifications.badgeFor(it.packageName)
                        } ?: 0,
                        darkTheme = true,
                        canMoveBack = order.indexOf(spec.id) > 0,
                        canMoveForward = order.indexOf(spec.id) in 0 until order.size - 1,
                        showColorDot = true,
                        nextSizeIsLarger = model.size.nextForFolderChild(
                            AppCategories.allowsLargeTile(
                                iconKey = childByKey[spec.id]?.iconKey,
                                app = null,
                                columns = columns,
                            ),
                        ).area > model.size.area,
                        onTap = { if (!editMode) childByKey[spec.id]?.let(onLaunchChild) },
                        onLongPress = { if (!editMode) { editMode = true; selectedId = spec.id } },
                        onResize = { childByKey[spec.id]?.let(onResize) },
                        onUnpin = { order.remove(spec.id); childByKey[spec.id]?.let(onPullOut) },
                        onSelect = { selectedId = spec.id },
                        onExitEdit = { editMode = false; selectedId = null },
                        onMove = { dir ->
                            val i = order.indexOf(spec.id)
                            val j = i + dir
                            if (i >= 0 && j in order.indices) {
                                val next = reorderTiles(order.toList(), spec.id, order[j])
                                if (next != order.toList()) {
                                    order.clear(); order.addAll(next); persistOrder()
                                }
                            }
                        },
                    )
                }
            }

            // Bottom breathing room so the last row clears the nav bar / lifts
            // above the edge while editing (matches Start's home-scroll padding).
            Spacer(Modifier.height(if (editMode) 130.dp else 74.dp))
        }

        // Per-child accent picker (edit-mode colour dot → palette, FR-7), same
        // sheet the Start screen uses for top-level tiles.
        colorPickerFor?.let { pickId ->
            val child = childByKey[pickId]
            val suggestion = child
                ?.takeIf { it.packageName.isNotBlank() }
                ?.let { rememberIconSuggestion(it.packageName, it.activityName) }
            TileColorPicker(
                current = child?.accentOverride,
                suggestedNearestId = suggestion?.nearestId,
                suggestedExact = suggestion?.exact,
                onPick = { colorId -> child?.let { onSetColor(it, colorId) }; colorPickerFor = null },
                onDismiss = { colorPickerFor = null },
            )
        }
    }
}

/** Pill button for the folder overlay's "make stack · wide/large" shortcut. */
@Composable
private fun StackModeChip(label: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (selected) {
                    Modifier.background(Color.White.copy(alpha = 0.16f))
                } else {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

/** Inline, auto-focused rename field for the folder title (FR-4). */
@Composable
private fun FolderTitleEditor(initial: String, onCommit: (String) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    BasicTextField(
        value = draft,
        onValueChange = { draft = it },
        singleLine = true,
        textStyle = TextStyle(
            color = DarkColorTokens.fg,
            fontSize = 30.sp,
            fontWeight = FontWeight.Thin,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(DarkColorTokens.fg),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(draft) }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp)
            .focusRequester(focus),
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun EditBarButton(
    iconKey: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            // 48dp min touch target (a11y) for the add/personalize/done controls.
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = TileIcons[iconKey],
            contentDescription = label,
            tint = LocalColorTokens.current.fg,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(text = label, color = LocalColorTokens.current.fg, fontSize = 13.sp)
    }
}

/**
 * Single shared jiggle phase (±.5°) for edit mode. Returns 0 — and composes no
 * animation — while not editing, so the resting Start screen never animates. Also
 * returns 0 when the system has animations turned off ("remove animations" a11y
 * setting / battery saver), so the grid sits still for motion-sensitive users.
 */
@Composable
private fun rememberJigglePhase(editMode: Boolean): Float {
    if (!editMode) return 0f
    val context = LocalContext.current
    val animationsOff = remember(editMode) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    if (animationsOff) return 0f
    val transition = rememberInfiniteTransition(label = "jiggle")
    val phase by transition.animateFloat(
        initialValue = -0.5f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "jigglePhase",
    )
    return phase
}

/**
 * Exits edit mode when [editMode] is on and the user taps empty space (a tap
 * that does not move past the 7 px slop). Non-consuming and inactive otherwise,
 * so it never interferes with launching or scrolling.
 */
private fun Modifier.emptySpaceExit(editMode: Boolean, onExit: () -> Unit): Modifier =
    pointerInput(editMode) {
        if (!editMode) return@pointerInput
        val slop = 7.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var moved = false
            var consumed = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) consumed = true // edit-bar / control owns it
                if ((change.position - down.position).getDistance() > slop) moved = true
                if (!change.pressed) {
                    if (!moved && !consumed) onExit()
                    break
                }
            }
        }
    }

/**
 * Per-tile tap / long-press gesture for the *non-edit* Start screen (FR-3.1):
 * a release within 7 px is a tap (launch); holding 600 ms fires the long-press
 * (enter edit). Never consumes the down, so vertical grid scrolling still wins
 * on drags. (Edit-mode interaction is handled by [editDragGesture].)
 */
private fun Modifier.tileGesture(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
): Modifier = pointerInput(onTap, onLongPress) {
    val slop = 7.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // true = released early (tap), false = moved past slop (let scroll win),
        // null = 600 ms elapsed still pressed (long-press → enter edit).
        val outcome = withTimeoutOrNull(600L) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                // A child handled it (e.g. a music tile's transport button): don't
                // also launch or enter edit.
                if (change != null && change.isConsumed) return@withTimeoutOrNull false
                if (change == null || !change.pressed) return@withTimeoutOrNull true
                if ((change.position - down.position).getDistance() > slop) {
                    return@withTimeoutOrNull false
                }
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        when (outcome) {
            null -> {
                onLongPress()
                waitForUpOrCancellation()
            }
            true -> onTap()
            false -> Unit
        }
    }
}

/**
 * Edit-mode drag-to-reorder gesture (FR-3.2), attached to the whole grid so it
 * can lift any tile. Pointer positions are grid-local, matching [GridGeometry].
 *
 * Down on a tile, then a >7 px move, lifts it ([onLift]); thereafter the tile
 * follows the finger ([onDrag]) and, when the finger hovers the edge zone of
 * another tile, that tile's slot is taken over ([onReorderTo]) — the centre
 * 22–78% is left alone (reserved for the S14 merge). Near a viewport edge the
 * grid auto-scrolls ([onAutoScroll]). A release after dragging persists the
 * order ([onDrop]); an in-place tap that lifted nothing exits edit ([onTapExit]).
 */
private fun Modifier.editDragGesture(
    editMode: Boolean,
    widthPx: Float,
    columns: Int = GridPacker.COLUMNS,
    gapPx: Float? = null,
    order: List<String>,
    byId: Map<String, TileModel>,
    draggingId: () -> String?,
    selectedId: () -> String?,
    onUnpin: (String) -> Unit,
    onOpenFolder: (String) -> Unit = {},
    onResize: (String) -> Unit,
    onColor: (String) -> Unit = {},
    onLift: (id: String, offset: IntOffset) -> Unit,
    onDrag: (offset: IntOffset) -> Unit,
    onReorderTo: (dragId: String, targetId: String) -> Unit,
    onMoveToEnd: (dragId: String) -> Unit = {},
    onMergeMode: (dragId: String) -> Unit,
    onMergeTarget: (targetId: String?) -> Unit,
    onAutoScroll: (dir: Int) -> Unit,
    onDrop: (mergeTargetId: String?) -> Unit,
    onSelect: (String) -> Unit,
    onTapExit: () -> Unit,
    contentTopPx: Float,
    viewportHeightPx: Float,
    scrollOffsetPx: () -> Float,
    edgeZonePx: Float,
    allowMerge: Boolean = true,
): Modifier = pointerInput(editMode, widthPx, columns, gapPx, byId, selectedId()) {
    // Re-keyed on byId so a resize/unpin mid-session refreshes the captured tile
    // sizes, and on the selected id so an in-edit selection switch refreshes the
    // corner-control target; neither changes mid-drag, so a live drag is safe.
    if (!editMode) return@pointerInput
    val geom = GridGeometry.of(widthPx, columns, gapPx)
    val slop = 7.dp.toPx()
    // Merge is intent-gated: the finger must dwell (pause within [dwellMoveTol])
    // in a target's centre for [mergeDwellMs] before a merge commits. A moving
    // finger reorders even straight across a centre, so repositioning a tile
    // never trips an accidental folder-merge (FR-3.3).
    val mergeDwellMs = 250L
    val dwellMoveTol = 14.dp.toPx()

    fun placementsNow(): List<TilePlacement> =
        GridPacker.pack(order.mapNotNull { id -> byId[id]?.let { TileSpec(id, it.size) } }, columns)

    // The other tiles packed *without* [exclude] (the dragged tile). Because a
    // drag only ever moves the dragged tile within the order, this layout is
    // invariant for the whole gesture — so a merge target never slips out from
    // under the finger the way it does in the dragged-included layout.
    fun othersPacked(exclude: String): List<TilePlacement> =
        GridPacker.pack(
            order.filter { it != exclude }
                .mapNotNull { id -> byId[id]?.let { TileSpec(id, it.size) } },
            columns,
        )

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)

        // Corner controls on the selected tile (FR-3.4/3.5/7): a tap in the
        // top-left zone unpins, bottom-right resizes, bottom-left recolours
        // (app tiles only). Handled here (not as child buttons) so the grid owns
        // all edit interaction; the events are consumed so empty-space-exit
        // never also fires.
        val sel = selectedId()
        val selPlacement = sel?.let { id -> placementsNow().firstOrNull { it.id == id } }
        if (selPlacement != null) {
            val r = geom.rect(selPlacement)
            val zone = 30.dp.toPx()
            val inUnpin = down.position.x <= r.left + zone && down.position.y <= r.top + zone
            val inResize = down.position.x >= r.right - zone && down.position.y >= r.bottom - zone
            val inColor =
                down.position.x <= r.left + zone && down.position.y >= r.bottom - zone
            if (inUnpin || inResize || inColor) {
                var movedCtl = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if ((change.position - down.position).getDistance() > slop) movedCtl = true
                    if (!change.pressed) {
                        if (!movedCtl) when {
                            inUnpin -> if (byId[selPlacement.id] is TileModel.Folder)
                                onOpenFolder(selPlacement.id)
                            else
                                onUnpin(selPlacement.id)
                            inColor -> onColor(selPlacement.id)
                            else -> onResize(selPlacement.id)
                        }
                        break
                    }
                }
                return@awaitEachGesture
            }
        }

        val startId = tileAt(placementsNow(), geom, down.position)
        var lifted = false
        var moved = false
        var grab = Offset.Zero
        var lastTarget: String? = null
        var mergeId: String? = null
        // Dwell tracking for intent-gated merge: which tile's centre the finger
        // currently rests in, where it entered, and when.
        var dwellId: String? = null
        var dwellAnchor = Offset.Zero
        var dwellStartMs = 0L
        // Half-extent of the dragged tile, captured at lift, so merge can be
        // judged from where the floating tile's CENTRE sits (not the finger).
        var dragHalf = Offset.Zero

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val pos = change.position
            if (!moved && (pos - down.position).getDistance() > slop) moved = true

            if (startId != null && !lifted && moved) {
                lifted = true
                val p0 = placementsNow().first { it.id == startId }
                val r = geom.rect(p0)
                grab = down.position - r.topLeft
                val sz = geom.sizePx(p0)
                dragHalf = Offset(sz.width / 2f, sz.height / 2f)
                onLift(startId, (pos - grab).round())
            }

            if (lifted) {
                change.consume()
                onDrag((pos - grab).round())

                // Merge (FR-3.3) vs reorder (FR-3.2). A *moving* finger always
                // reorders — even straight across a tile's centre — so
                // repositioning never trips an accidental merge. Merge commits
                // only on intent: a dwell (pause within [dwellMoveTol]) for
                // [mergeDwellMs] while the dragged tile sits over a target.
                //
                // The merge target is judged from where the floating tile's
                // CENTRE lands — not the finger — so aligning two tiles merges
                // them regardless of where the tile was grabbed or how big it
                // is. This is what makes small↔small and wide↔wide merges easy:
                // you just drop one tile squarely onto another. Targets come
                // from the OTHER tiles packed without the dragged tile (an
                // invariant layout) so they don't slip out from under it;
                // reorder still uses the live dragged-included layout so the gap
                // follows the finger. A committed merge stays sticky while the
                // centre remains over the target (a wobble won't drop it);
                // sliding off it breaks back into a reorder.
                val dragCentre = (pos - grab) + dragHalf
                val hovered = startId?.let { drag ->
                    othersPacked(drag).firstOrNull { geom.rect(it).contains(dragCentre) }
                }
                val inCentre = allowMerge && hovered != null &&
                    inMergeZone(geom.rect(hovered), dragCentre) &&
                    byId[startId] !is TileModel.Folder

                if (inCentre) {
                    if (dwellId != hovered!!.id ||
                        (pos - dwellAnchor).getDistance() > dwellMoveTol
                    ) {
                        // New tile, or the finger moved too far: restart the clock.
                        dwellId = hovered.id
                        dwellAnchor = pos
                        dwellStartMs = change.uptimeMillis
                    }
                } else {
                    dwellId = null
                }
                val dwelled = inCentre &&
                    change.uptimeMillis - dwellStartMs >= mergeDwellMs
                val mergeNow = inCentre && (dwelled || mergeId == hovered!!.id)

                if (mergeNow && startId != null) {
                    lastTarget = null
                    if (mergeId != hovered!!.id) {
                        mergeId = hovered.id
                        onMergeTarget(hovered.id)
                        onMergeMode(startId)
                    }
                } else {
                    if (mergeId != null) { mergeId = null; onMergeTarget(null) }
                    val placements = placementsNow()
                    val target = placements.firstOrNull {
                        it.id != startId && geom.rect(it).contains(pos)
                    }
                    if (target != null) {
                        if (target.id != lastTarget) {
                            lastTarget = target.id
                            startId?.let { onReorderTo(it, target.id) }
                        }
                    } else {
                        lastTarget = null
                        // Finger in the trailing empty region below every tile:
                        // send the dragged tile to the end of the order so it packs
                        // into the bottom rows. Dense-pack can't strand a gap, but a
                        // tile *can* be ordered last — this makes "drop it at the
                        // bottom" reachable (the empty area is otherwise no tile's
                        // hit-target, so a plain drop there would snap back).
                        val contentBottom = placements.maxOfOrNull { geom.rect(it).bottom } ?: 0f
                        if (pos.y > contentBottom) startId?.let { onMoveToEnd(it) }
                    }
                }

                // Auto-scroll near the viewport edges.
                val fingerViewportY = (contentTopPx + pos.y) - scrollOffsetPx()
                onAutoScroll(
                    when {
                        fingerViewportY < edgeZonePx -> -1
                        fingerViewportY > viewportHeightPx - edgeZonePx -> 1
                        else -> 0
                    },
                )
            }

            if (!change.pressed) {
                when {
                    lifted || draggingId() != null -> onDrop(mergeId)
                    moved -> Unit
                    // A tap on another tile switches which tile is being edited
                    // (its corner controls move to it); a tap on open space (no
                    // tile hit) exits edit mode. Tapping the already-selected tile
                    // keeps it selected — only open space leaves edit.
                    startId != null -> if (startId != selectedId()) onSelect(startId)
                    else -> onTapExit()
                }
                break
            }
        }
    }
}

@Composable
private fun AppTileContent(
    tile: TileModel.App,
    flipped: Boolean = false,
    liveActive: Boolean = false,
    // Whether the tile's interactive elements (music transport controls) should
    // respond to taps. Driven by edit mode — not the live-tile animation gate —
    // so the buttons stay operable even when animations are off / battery saver
    // is on (which holds liveActive low while now-playing still shows).
    interactive: Boolean = false,
    // When non-null, passed as forcedIndex to PhotosTileFace so the displayed photo
    // is controlled by StackTileContent (hoisted above AnimatedContent so it survives
    // composition recycling). Null = standalone tile, normal 3 s timer.
    photosStackIndex: Int? = null,
) {
    // A pinned contact (quick search → "pin to start") is a plain App tile whose
    // activityName encodes the contact's identity (ContactTile) rather than a
    // resolvable launch component — render its photo/avatar instead of falling
    // through to the app-icon/live-face machinery below.
    val contactId = remember(tile.activityName) { ContactTile.decode(tile.activityName)?.first }
    if (contactId != null) {
        ContactTileFace(contactId = contactId, name = tile.label.orEmpty(), size = tile.size, modifier = Modifier.fillMaxSize())
        return
    }

    // Live faces replace the static glyph at medium+ (FR-2). Small tiles and
    // apps with no live face fall through to the static glyph; weather/calendar
    // also fall back to it when their opt-in permission is denied or no data is
    // cached (the live composables call the slot).
    val staticGlyph = @Composable { StaticTileGlyph(tile) }

    // Small (1×1) clock / calendar tiles get a compact non-flipping live face —
    // the time, and today's day number — instead of the static glyph.
    if (tile.size == TileSize.SMALL) {
        when (tile.iconKey) {
            "clock" -> { ClockSmallFace(active = liveActive, modifier = Modifier.fillMaxSize()); return }
            "calendar" -> { CalendarSmallFace(active = liveActive, modifier = Modifier.fillMaxSize()); return }
            "weather" -> { WeatherSmallFace(fallback = staticGlyph, modifier = Modifier.fillMaxSize()); return }
        }
    }

    val face = LiveFace.forIconKey(tile.iconKey, tile.size)
    when (face) {
        LiveFace.CLOCK -> {
            ClockTileFace(
                size = tile.size,
                flipped = flipped,
                active = liveActive,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        LiveFace.WEATHER -> {
            WeatherTileFace(
                size = tile.size,
                flipped = flipped,
                fallback = staticGlyph,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        LiveFace.CALENDAR -> {
            CalendarTileFace(
                size = tile.size,
                flipped = flipped,
                active = liveActive,
                fallback = staticGlyph,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        LiveFace.MAIL, LiveFace.MESSAGES -> {
            ConversationTileFace(
                kind = face,
                packageName = tile.packageName,
                flipped = flipped,
                active = liveActive,
                fallback = staticGlyph,
                size = tile.size,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        LiveFace.PEOPLE -> {
            PeopleTileFace(
                size = tile.size,
                flipped = flipped,
                active = liveActive,
                fallback = staticGlyph,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        LiveFace.PHOTOS -> {
            PhotosTileFace(
                active = liveActive,
                fallback = staticGlyph,
                packageName = tile.packageName,
                size = tile.size,
                forcedIndex = photosStackIndex,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        LiveFace.MUSIC -> {
            MusicTileFace(
                flipped = flipped,
                active = liveActive,
                interactive = interactive,
                fallback = staticGlyph,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        null -> {
            // No dedicated live face: a medium+ app tile goes live when its own
            // package is playing media (a music app like Apple Music / YT Music
            // shows its now-playing track) — else when it has an active
            // notification (FR-2.3). Small tiles stay static (the badge carries the
            // count). Fall through: now-playing → notification → static glyph.
            if (tile.size != TileSize.SMALL) {
                MusicTileFace(
                    flipped = flipped,
                    active = liveActive,
                    interactive = interactive,
                    packageName = tile.packageName,
                    fallback = {
                        NotificationTileFace(
                            packageName = tile.packageName,
                            active = liveActive,
                            fallback = staticGlyph,
                            size = tile.size,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                return
            }
        }
    }
    staticGlyph()
}

/** The non-live tile face: the monoline glyph, with a label above small size. */
@Composable
private fun StaticTileGlyph(tile: TileModel.App) {
    val useAppIcon = !TileIcons.hasIcon(tile.iconKey)
    val appIcon = if (useAppIcon) rememberTileAppIcon(tile.packageName, tile.activityName) else null

    @Composable
    fun TileIconContent(monolineSize: Int) {
        if (useAppIcon && appIcon != null) {
            Image(
                bitmap = appIcon,
                contentDescription = tile.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(monolineSize.dp),
            )
        } else {
            Icon(
                imageVector = TileIcons[tile.iconKey],
                contentDescription = tile.label,
                tint = Color.White,
                modifier = Modifier.size(monolineSize.dp),
            )
        }
    }

    if (tile.size == TileSize.SMALL) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TileIconContent(30)
        }
    } else {
        // A 3×3 large tile (music/news only) gets a bigger glyph so it isn't lost in
        // the larger footprint; medium/wide keep the standard size.
        val glyphSize = if (tile.size == TileSize.LARGE) 46 else 34
        Column(modifier = Modifier.fillMaxSize().padding(11.dp)) {
            TileIconContent(glyphSize)
            Spacer(Modifier.weight(1f))
            TileLabel(tile.label.orEmpty())
        }
    }
}

/**
 * A pinned contact's tile face: the contact's own photo full-bleed (WP people
 * tile style) with the name legible over a bottom scrim, or — when they have no
 * photo, or it fails to load — the "people" glyph over the tile's normal
 * accent/gradient/wallpaper fill (drawn by the caller Box, same as
 * [StaticTileGlyph]) so the colour picker still means something for them.
 */
@Composable
private fun ContactTileFace(contactId: Long, name: String, size: TileSize, modifier: Modifier = Modifier) {
    val photoUri = rememberContactPhotoUri(contactId)
    val photo = photoUri?.let { rememberTileBitmap(it, targetPx = if (size == TileSize.LARGE) 300 else 150) }
    if (photo != null) {
        Box(modifier = modifier) {
            Image(
                bitmap = photo,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (size != TileSize.SMALL) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))),
                )
                Text(
                    text = name.lowercase(),
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomStart).padding(11.dp),
                )
            }
        }
    } else if (size == TileSize.SMALL) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(TileIcons["people"], null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
    } else {
        val glyphSize = if (size == TileSize.LARGE) 46 else 34
        Column(modifier = modifier.padding(11.dp)) {
            Icon(TileIcons["people"], null, tint = Color.White, modifier = Modifier.size(glyphSize.dp))
            Spacer(Modifier.weight(1f))
            TileLabel(name)
        }
    }
}

@Composable
private fun rememberTileAppIcon(packageName: String, activityName: String): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null, packageName, activityName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getActivityIcon(ComponentName(packageName, activityName))
                    .toBitmap(width = 96, height = 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }.value
}

/** An app icon's dominant colour ([exact]) and the nearest of the 14 accents. */
private data class IconSuggestion(val exact: Color, val nearestId: String)

/**
 * The per-tile colour suggestions from an app icon (FR-7): its dominant colour
 * (saturated, opaque pixels averaged weighted by saturation so the brand hue
 * beats white/grey chrome) and the nearest of the 14 accents. Null while the
 * icon is loading or it is effectively colourless.
 */
/** The dominant colour of an app's launcher icon, for app-icon-colour mode (FR-7). */
@Composable
private fun rememberDominantIconColor(packageName: String, activityName: String): Color? {
    val icon = rememberTileAppIcon(packageName, activityName)
    return remember(icon) { icon?.let { dominantIconColor(it) } }
}

@Composable
private fun rememberIconSuggestion(packageName: String, activityName: String): IconSuggestion? {
    val icon = rememberTileAppIcon(packageName, activityName)
    return remember(icon) {
        icon?.let { dominantIconColor(it) }?.let { IconSuggestion(it, TileAccents.nearestAccentId(it)) }
    }
}

private fun dominantIconColor(bitmap: ImageBitmap): Color? {
    val w = bitmap.width
    val h = bitmap.height
    if (w == 0 || h == 0) return null
    val px = IntArray(w * h)
    runCatching { bitmap.asAndroidBitmap().getPixels(px, 0, w, 0, 0, w, h) }
        .getOrElse { return null }
    var wr = 0.0; var wg = 0.0; var wb = 0.0; var wSum = 0.0 // saturation-weighted
    var ar = 0.0; var ag = 0.0; var ab = 0.0; var aN = 0      // plain opaque average
    for (p in px) {
        if ((p ushr 24 and 0xff) < 128) continue
        val r = p ushr 16 and 0xff
        val g = p ushr 8 and 0xff
        val b = p and 0xff
        ar += r; ag += g; ab += b; aN++
        val mx = maxOf(r, g, b)
        val sat = if (mx == 0) 0f else (mx - minOf(r, g, b)).toFloat() / mx
        if (sat > 0.25f && mx > 40) {
            wr += r * sat; wg += g * sat; wb += b * sat; wSum += sat
        }
    }
    return when {
        wSum > 0 -> Color((wr / wSum).toInt(), (wg / wSum).toInt(), (wb / wSum).toInt())
        aN > 0 -> Color((ar / aN).toInt(), (ag / aN).toInt(), (ab / aN).toInt())
        else -> null
    }
}

@Composable
private fun FolderChildIcon(child: FolderChild?) {
    // Always call rememberTileAppIcon so the composable call count is stable
    // regardless of whether child is null or has a WP icon.
    val pkg = child?.packageName.orEmpty()
    val act = child?.activityName.orEmpty()
    val appIcon = rememberTileAppIcon(pkg, act)
    val useAppIcon = child != null && !TileIcons.hasIcon(child.iconKey)

    if (child == null) return
    if (useAppIcon && appIcon != null) {
        Image(
            bitmap = appIcon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(18.dp),
        )
    } else {
        Icon(
            imageVector = TileIcons[child.iconKey],
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun FolderTileContent(
    tile: TileModel.Folder,
    editMode: Boolean,
    launchEnabled: Boolean,
    appIconColors: Boolean,
    glass: Boolean,
    transparency: Float,
    darkTheme: Boolean,
    tiledWallpaper: Boolean,
    onLaunchChild: (FolderChild) -> Unit,
    onOpenFolder: () -> Unit,
    onEnterEdit: () -> Unit,
) {
    // Inline iOS-style folder face: a mini-grid of the child app icons. A wide
    // folder shows a 4×2 grid (more apps); medium/small show 2×2. When
    // [launchEnabled] (only on the roomy 4-column grid) each icon is tappable to
    // launch out of edit mode, and an overflow cell becomes "+N" that opens the
    // overlay; on denser 5/6-column grids the cells are too small to tap, so they
    // are display-only and the whole tile opens the overlay on tap. In edit mode
    // the cells are always inert so the grid-level drag owns the tile.
    val children = tile.children
    val cols = if (tile.size == TileSize.WIDE) 4 else 2
    val rows = 2
    val maxCells = cols * rows
    val overflow = children.size > maxCells
    val lastIndex = maxCells - 1
    Column(modifier = Modifier.fillMaxSize().padding(9.dp)) {
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            for (rowIndex in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    for (colIndex in 0 until cols) {
                        val cellIndex = rowIndex * cols + colIndex
                        val isPlus = overflow && cellIndex == lastIndex
                        val child = if (isPlus) null else children.getOrNull(cellIndex)
                        val tap: (() -> Unit)? = when {
                            editMode || !launchEnabled -> null
                            isPlus -> onOpenFolder
                            child != null -> ({ onLaunchChild(child) })
                            else -> null
                        }
                        // The child keeps its own colour inside the folder: an
                        // explicit override wins; otherwise, in app-icon-colour
                        // mode the icon's dominant colour shows; else neutral dark.
                        // Under "wallpaper behind tiles" the parent tile is already
                        // a window onto the wallpaper, so a cell paints nothing and
                        // lets it show through (matching a plain tile, which also
                        // drops its accent under tiled mode); under glass the cell
                        // takes the same translucent glass tint as any other tile
                        // instead of a fully opaque colour that would otherwise mask
                        // the frosted background just for this one cell.
                        val cellBg = child?.accentOverride
                            ?.let { TileAccents.colorForOverride(it, "blue") }
                            ?: child?.takeIf { appIconColors }
                                ?.let { rememberDominantIconColor(it.packageName, it.activityName) }
                            ?: Color(0x2E000000)
                        val cellFill = when {
                            tiledWallpaper -> Modifier
                            glass -> Modifier.background(Glass.fill(darkTheme, transparency, cellBg))
                            else -> Modifier.background(cellBg)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .then(cellFill)
                                .then(
                                    if (tap != null) {
                                        Modifier.combinedClickable(
                                            onClick = tap,
                                            onLongClick = onEnterEdit,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isPlus) {
                                Text(
                                    text = "+${children.size - lastIndex}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            } else {
                                FolderChildIcon(child)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        TileLabel(tile.name)
    }
}

/** Auto-rotate interval for a widget stack — long enough to read a notification snippet. */
private const val STACK_ROTATE_MS = 10000L
private const val STACK_EDGE_DRAG_ZONE_DP = 40

/**
 * A **widget stack**: a folder whose members are all LARGE renders as a swipeable
 * 3×3 carousel of full-size live tiles instead of a mini-grid of icons. The current
 * member's live face fills the tile (reusing [AppTileContent] with interactive=true, so
 * music play controls and other live-face gestures work); the stack auto-rotates every
 * [STACK_ROTATE_MS] while live, and members slide up/down. **Tap** → launch the current
 * member's app. **Drag up/down starting in the right-edge strip** (where the position
 * indicator lives, [STACK_EDGE_DRAG_ZONE_DP] wide — wider than the indicator itself for a
 * comfortable hit target) → flip members immediately, at plain touch-slop. A touch that
 * starts anywhere else on the tile never captures vertical movement — it bails unconsumed
 * the moment it moves past slop, so the enclosing screen scroll/pager always wins there;
 * only that touch supports **long-press → select for edit**. This keeps the flip gesture
 * confined to a small corner instead of the whole tile fighting the screen for every
 * vertical swipe. A thin right-edge indicator shows position.
 */
@Composable
private fun StackTileContent(
    tile: TileModel.Folder,
    editMode: Boolean,
    selected: Boolean,
    liveActive: Boolean,
    accent: Color,
    appIconColors: Boolean,
    glass: Boolean,
    transparency: Float,
    tiledWallpaper: Boolean,
    darkTheme: Boolean,
    wallpaper: com.tileshell.core.design.WallpaperGradient,
    wallpaperPhoto: ImageBitmap?,
    wallpaperAlignX: Float,
    wallpaperAlignY: Float,
    wallpaperZoom: Float,
    wallpaperOrigin: () -> Offset,
    fullWidth: Float,
    fullHeight: Float,
    onLaunchChild: (FolderChild) -> Unit,
    onEnterEdit: () -> Unit,
) {
    val useTileGradient = LocalTileGradient.current
    val children = tile.children
    val count = children.size
    val pageIndex = remember(tile.id, count) { mutableStateOf(0) }
    val safeIndex = pageIndex.value.coerceIn(0, (count - 1).coerceAtLeast(0))
    // Direction of the last member change (+1 next / −1 previous) — drives the slide.
    val lastDir = remember { mutableStateOf(1) }

    // Per-member flip state: each child flips to its back face 2 600 ms after it
    // becomes the active member (matching the global flip scheduler interval), then
    // resets to front when the stack rotates to the next member.
    // liveActive is NOT a key — brief interruptions (app list) don't restart the
    // timer; it is checked after the delay, same pattern as the auto-rotate above.
    val flipStates = remember(tile.id, count) { mutableStateMapOf<String, Boolean>() }

    // Hoisted photos index: AnimatedContent recreates the composition for each member
    // on every visit, so remember() inside PhotosTileFace resets to 0 each time.
    // Keeping the index here (above AnimatedContent) makes it persist across rotations.
    // photosActivated skips the advance on the very first visit so photo 0 is shown first.
    val photosStackIndex = remember(tile.id) { mutableIntStateOf(0) }
    var photosActivated by remember(tile.id) { mutableStateOf(false) }

    LaunchedEffect(safeIndex) {
        val child = children.getOrNull(safeIndex) ?: return@LaunchedEffect
        val key = child.rowId.toString()
        flipStates[key] = false
        if (child.iconKey == "photos") {
            if (photosActivated) photosStackIndex.value++
            photosActivated = true
        }
        delay(2600L)
        if (liveActive) flipStates[key] = true
    }

    // Random phase offset so multiple stacks on screen don't all rotate in lockstep.
    // Stable per tile.id for the lifetime of the composition.
    val rotateOffset = remember(tile.id) { Random.nextLong(0L, STACK_ROTATE_MS) }

    // Auto-rotate: runs for the lifetime of the composition so the delay never
    // resets when liveActive or editMode toggle briefly (e.g. app list opens and
    // closes). The guard is checked after each full delay, not as a LaunchedEffect
    // key, so a short interruption doesn't shorten the next interval.
    LaunchedEffect(count) {
        if (count <= 1) return@LaunchedEffect
        delay(rotateOffset)
        while (true) {
            delay(STACK_ROTATE_MS)
            if (!liveActive || editMode) continue
            lastDir.value = 1
            pageIndex.value = (pageIndex.value + 1) % count
        }
    }

    // Stable ref to the current-member launch action (page index changes across
    // recompositions; rememberUpdatedState lets the gesture read it without restart).
    val launchCurrent = rememberUpdatedState {
        children.getOrNull(pageIndex.value.coerceIn(0, (count - 1).coerceAtLeast(0)))
            ?.let(onLaunchChild)
    }
    val enterEditRef = rememberUpdatedState(onEnterEdit)

    Box(
        modifier = Modifier
            .fillMaxSize()
            // In non-edit mode a touch starting in the right-edge strip flips members
            // on vertical drag (instant, at plain touch-slop); a touch starting
            // anywhere else on the tile only supports tap-to-launch and long-press to
            // select for edit — any movement there bails unconsumed so the enclosing
            // screen scroll/pager always wins. If a child (e.g. a music transport
            // button) consumes the event the gesture bails without firing launch or
            // edit-select. In edit mode the grid drag owns interaction, so this block
            // is removed.
            .then(
                if (editMode) Modifier
                else Modifier.pointerInput(count) {
                    val slop = viewConfiguration.touchSlop
                    val stepPx = 44.dp.toPx()
                    val edgeZonePx = STACK_EDGE_DRAG_ZONE_DP.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (count > 1 && down.position.x >= size.width - edgeZonePx) {
                            // Right-edge strip: confined enough that a drag starting
                            // here is never the user trying to scroll the screen, so
                            // it can flip immediately with no long-press wait.
                            var anchorY = down.position.y
                            var flipping = false
                            loop@ while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break@loop
                                if (change.isConsumed) break@loop
                                if (!flipping) {
                                    val dy = change.position.y - anchorY
                                    val dx = change.position.x - down.position.x
                                    if (abs(dy) > slop && abs(dy) > abs(dx)) {
                                        flipping = true
                                        anchorY = change.position.y
                                    } else if (!change.pressed) {
                                        launchCurrent.value()
                                        break@loop
                                    }
                                }
                                if (flipping) {
                                    change.consume()
                                    val dy = change.position.y - anchorY
                                    if (dy <= -stepPx) {
                                        lastDir.value = 1
                                        pageIndex.value = (pageIndex.value + 1) % count
                                        anchorY = change.position.y
                                    } else if (dy >= stepPx) {
                                        lastDir.value = -1
                                        pageIndex.value = (pageIndex.value - 1 + count) % count
                                        anchorY = change.position.y
                                    }
                                }
                                if (!change.pressed) break@loop
                            }
                        } else {
                            // Rest of the tile: any movement (either axis) before the
                            // long-press timeout bails unconsumed, so a plain swipe is
                            // always left for the enclosing scroll/pager.
                            val phase = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: return@withTimeoutOrNull 1
                                    if (change.isConsumed) return@withTimeoutOrNull 1
                                    if (!change.pressed) return@withTimeoutOrNull 0
                                    val dy = change.position.y - down.position.y
                                    val dx = change.position.x - down.position.x
                                    if (abs(dx) > slop || abs(dy) > slop) return@withTimeoutOrNull 1
                                }
                                @Suppress("UNREACHABLE_CODE") 1
                            }
                            when (phase) {
                                null -> {
                                    enterEditRef.value()
                                    waitForUpOrCancellation()
                                }
                                0 -> launchCurrent.value()
                                else -> Unit
                            }
                        }
                    }
                }
            ),
    ) {
        // Members slide vertically (in the travel direction) so each reads as a
        // distinct tile scrolling past — for both the swipe and the auto-rotate.
        AnimatedContent(
            targetState = safeIndex,
            transitionSpec = {
                val dir = lastDir.value
                (slideInVertically { h -> dir * h } + fadeIn()) togetherWith
                    (slideOutVertically { h -> -dir * h } + fadeOut())
            },
            label = "stackMember",
        ) { i ->
            children.getOrNull(i)?.let { child ->
                // Each member keeps its own colour while rotating: an explicit
                // per-child override wins; otherwise, in app-icon-colour mode the
                // icon's dominant colour shows; else the stack tile's own accent —
                // same three-way chain as FolderOverlay/FolderTileContent. This has
                // to be painted here (not on the outer TileView Box) since the
                // outer background is fixed once per composition and can't vary
                // per rotated member.
                val memberAccent = child.accentOverride
                    ?.let { TileAccents.colorForOverride(it, "blue") }
                    ?: child.takeIf { appIconColors }
                        ?.let { rememberDominantIconColor(it.packageName, it.activityName) }
                    ?: accent
                // Tinted by this member's own colour, not the stack tile's — each
                // rotated member should read as its own glass tile.
                val memberGlassFill = if (glass) Glass.fill(darkTheme, transparency, memberAccent) else null
                // Render the member's live face at the stack tile's footprint by
                // reusing the normal app-tile content. interactive=true so music
                // transport buttons and other live-face controls are tappable.
                // Fill mirrors the outer TileView priority (wallpaper window / glass
                // / gradient / flat) so a stack behaves like any other tile under
                // "wallpaper behind tiles" or glass mode instead of always painting
                // an opaque memberAccent square over it.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            when {
                                tiledWallpaper && wallpaperPhoto != null -> Modifier.photoWindow(
                                    image = wallpaperPhoto,
                                    fullWidth = fullWidth,
                                    fullHeight = fullHeight,
                                    darkBase = colorTokens(darkTheme).bg,
                                    origin = wallpaperOrigin,
                                    alignX = wallpaperAlignX,
                                    alignY = wallpaperAlignY,
                                    zoom = wallpaperZoom,
                                )
                                tiledWallpaper -> Modifier.wallpaperWindow(
                                    wallpaper = wallpaper,
                                    fullWidth = fullWidth,
                                    fullHeight = fullHeight,
                                    origin = wallpaperOrigin,
                                    dark = darkTheme,
                                )
                                memberGlassFill != null -> Modifier.background(memberGlassFill)
                                useTileGradient -> Modifier.background(tileGradientBrush(memberAccent))
                                else -> Modifier.background(memberAccent)
                            },
                        ),
                ) {
                    AppTileContent(
                        tile = TileModel.App(
                            id = tile.id + "#" + child.rowId,
                            position = 0,
                            size = tile.size,
                            colorId = tile.colorId,
                            packageName = child.packageName,
                            activityName = child.activityName,
                            label = child.label,
                            iconKey = child.iconKey,
                            accentOverride = child.accentOverride,
                        ),
                        flipped = flipStates[child.rowId.toString()] ?: false,
                        liveActive = liveActive && (i == safeIndex),
                        interactive = true,
                        photosStackIndex = if (child.iconKey == "photos") photosStackIndex.value else null,
                    )
                }
            }
        }
        // Vertical scroll indicator (right edge): a faint track with a thumb whose
        // position tracks the current member — signals the swipe affordance.
        if (count > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 5.dp)
                    .width(3.dp)
                    .fillMaxHeight(0.5f)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (safeIndex > 0) Spacer(Modifier.weight(safeIndex.toFloat()))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.92f)),
                    )
                    val below = count - 1 - safeIndex
                    if (below > 0) Spacer(Modifier.weight(below.toFloat()))
                }
            }
        }
    }
}

@Composable
private fun TileLabel(text: String) {
    Text(
        text = text.lowercase(),
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
    )
}

/**
 * Runs the feed search pill's query (FR-7): hands it to the Google app via
 * `ACTION_WEB_SEARCH` (the Quick Search Box / Google app picks it up), falling
 * back to a browser `google.com/search` view when nothing handles the search
 * action. Best-effort — both attempts are guarded so a missing handler is silent.
 */
internal fun launchWebSearch(context: Context, query: String) {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return
    val search = Intent(Intent.ACTION_WEB_SEARCH)
        .putExtra(SearchManager.QUERY, trimmed)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(search) }.isSuccess) return
    val browser = Intent(Intent.ACTION_VIEW, Uri.parse(googleSearchUrl(trimmed)))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(browser) }
}

/**
 * One AI assistant offered as a quick-search shortcut (post-S27, not in the WP
 * prototype/spec — see DECISIONS.md "AI assistants in quick search"). [domain] is
 * only used to fetch that service's real favicon as a pill icon when the app
 * itself isn't installed — see [faviconUrl].
 */
data class AiAssistant(val id: String, val label: String, val packageName: String, val domain: String)

/** The AI assistants offered in quick search, in display order. */
val AI_ASSISTANTS = listOf(
    AiAssistant("chatgpt", "chatgpt", "com.openai.chatgpt", "chatgpt.com"),
    AiAssistant("gemini", "gemini", "com.google.android.apps.bard", "gemini.google.com"),
    AiAssistant("claude", "claude", "com.anthropic.claude", "claude.ai"),
    AiAssistant("perplexity", "perplexity", "ai.perplexity.app.android", "perplexity.ai"),
    AiAssistant("copilot", "copilot", "com.microsoft.copilot", "copilot.microsoft.com"),
)

/**
 * Google's favicon service (`s2/favicons`) — widely used, undocumented-but-stable,
 * returns any domain's real favicon as a PNG at roughly [sizePx]. Used as the
 * second-tier icon source for [ServicePill] (after the real installed app icon,
 * before the plain accent-tinted initial) so a search engine or assistant that
 * isn't installed still shows its actual logo instead of a generic placeholder.
 */
internal fun faviconUrl(domain: String, sizePx: Int = 128): String =
    "https://www.google.com/s2/favicons?domain=$domain&sz=$sizePx"

/**
 * Hands [query] to the named AI assistant's app via a plain text share
 * (`ACTION_SEND`) — the same mechanism as sharing text from any other app, which
 * every major assistant app registers as a share target and opens as a new,
 * pre-filled prompt. Falls back to that app's Play Store listing when it isn't
 * installed (so the row is still useful — "go get the app" — rather than a
 * silent no-op), matching [launchWebSearch]'s best-effort two-tier pattern.
 */
internal fun launchAiAssistant(context: Context, packageName: String, query: String) {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return
    val share = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .setPackage(packageName)
        .putExtra(Intent.EXTRA_TEXT, trimmed)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(share) }.isSuccess) return
    val marketListing = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(marketListing) }.isSuccess) return
    val webListing = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(webListing) }
}

/**
 * One search engine offered as a quick-search shortcut (post-S27, not in the WP
 * prototype/spec). [packageName] is only used to show that engine's real app icon
 * when installed; [domain] is the fallback favicon source when it isn't (see
 * [faviconUrl]) — either way [urlTemplate] is what's actually opened, so a wrong
 * or missing icon source just falls back further, never breaks the search).
 */
data class SearchEngine(
    val id: String,
    val label: String,
    val packageName: String?,
    val domain: String,
    val urlTemplate: (String) -> String,
)

/** The search engines offered in quick search, in display order. */
val SEARCH_ENGINES = listOf(
    SearchEngine("google", "google", "com.google.android.googlequicksearchbox", "google.com", ::googleSearchUrl),
    SearchEngine("bing", "bing", "com.microsoft.bing", "bing.com") {
        "https://www.bing.com/search?q=" + URLEncoder.encode(it.trim(), "UTF-8")
    },
    SearchEngine("duckduckgo", "duckduckgo", "com.duckduckgo.mobile.android", "duckduckgo.com") {
        "https://duckduckgo.com/?q=" + URLEncoder.encode(it.trim(), "UTF-8")
    },
    SearchEngine("yahoo", "yahoo", "com.yahoo.mobile.client.android.search", "yahoo.com") {
        "https://search.yahoo.com/search?p=" + URLEncoder.encode(it.trim(), "UTF-8")
    },
    SearchEngine("yandex", "yandex", "ru.yandex.searchplugin", "yandex.com") {
        "https://yandex.com/search/?text=" + URLEncoder.encode(it.trim(), "UTF-8")
    },
)

/**
 * Opens [engine]'s search results for [query]. The "google" engine reuses
 * [launchWebSearch] (tries the system's default search handler first, e.g. the
 * Google app's Quick Search Box, falling back to a browser); every other engine
 * goes straight to its own search URL in a browser, since there's no equivalent
 * "default handler" concept for a specific non-default engine.
 */
internal fun launchSearchEngine(context: Context, engine: SearchEngine, query: String) {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return
    if (engine.id == "google") {
        launchWebSearch(context, trimmed)
        return
    }
    val browser = Intent(Intent.ACTION_VIEW, Uri.parse(engine.urlTemplate(trimmed)))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(browser) }
}

/**
 * Opens the calendar app's add-event screen (`ACTION_INSERT` on the events URI)
 * so the user can add a schedule straight from the feed. Best-effort: toasts when
 * no calendar app handles it.
 */
/** Opens an article [url] in the browser. Best-effort; toasts when no handler. */
private fun launchUrl(context: Context, url: String) {
    if (url.isBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(intent) }.isSuccess) return
    Toast.makeText(context, "couldn't open the article", Toast.LENGTH_SHORT).show()
}

private fun launchAddEvent(context: Context) {
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(intent) }.isSuccess) return
    Toast.makeText(context, "no calendar app to add an event", Toast.LENGTH_SHORT).show()
}

private fun onTileClick(context: Context, tile: TileModel) {
    when (tile) {
        is TileModel.App -> {
            // A pinned contact (quick search → "pin to start"): reopen its
            // contact card rather than falling into the liveOnly branch below
            // (its packageName is blank the same way weather/calendar's is).
            val contact = ContactTile.decode(tile.activityName)
            if (contact != null) {
                openContactCard(context, contact.first, contact.second)
                return
            }
            // Clock tile: open the system clock's alarms screen. Reliable across
            // devices (works even when the clock role didn't resolve to a launch
            // component, which otherwise left the tap inert) and matches the tile's
            // alarm-centric face. Falls through to a normal launch if no app handles it.
            if (tile.iconKey == "clock" && openClock(context)) return
            if (tile.packageName.isNotBlank()) {
                // If the tile is currently showing a notification (badge / live
                // face), tapping opens that notification inside the app and clears
                // the app's notifications. Falls through to a normal launch when the
                // app has nothing pending or the notification had no content intent.
                if (NotificationCenter.openAndClear(context, tile.packageName)) return
                if (!AppLauncher.launch(context, tile.packageName, tile.activityName)) {
                    Toast.makeText(
                        context,
                        "couldn't open ${tile.label ?: "app"}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } else {
                // Self-contained live tiles (weather/calendar) seeded with no
                // resolved app: fall back to a content intent for the live feature
                // (e.g. open the device calendar). Inert if there's no handler.
                launchLiveTileFallback(context, tile.iconKey)
            }
        }
        // Folder overlay arrives in S16; tapping is inert for now.
        is TileModel.Folder -> Unit
    }
}

/**
 * Opens the system app behind a self-contained live tile that seeded without a
 * resolved launch component. Calendar maps to the calendar provider's VIEW intent
 * (the default calendar app); weather has no standard launcher intent, so it opens
 * a weather web search (handled in-app by the Google app where present, else the
 * browser). Other live tiles have no target and stay inert. Best-effort — a missing
 * handler is swallowed rather than toasted.
 */
/**
 * Launch a folder/widget-stack child, mirroring [onTileClick]'s App branch: a
 * clock child opens the system alarms screen, a pending notification opens
 * in-app, and a self-contained liveOnly child seeded with no resolved app
 * (weather/calendar with a blank package) falls back to its live-tile intent
 * instead of failing with "couldn't open" — that fallback only applied to
 * top-level tiles before, so the same child launched from inside a folder or
 * stack (whose package is not part of its folder identity) errored out.
 */
private fun launchFolderChild(context: Context, child: FolderChild) {
    val contact = ContactTile.decode(child.activityName)
    if (contact != null) {
        openContactCard(context, contact.first, contact.second)
        return
    }
    if (child.iconKey == "clock" && openClock(context)) return
    if (child.packageName.isNotBlank()) {
        if (NotificationCenter.openAndClear(context, child.packageName)) return
        if (!AppLauncher.launch(context, child.packageName, child.activityName)) {
            Toast.makeText(context, "couldn't open ${child.label ?: "app"}", Toast.LENGTH_SHORT).show()
        }
    } else {
        launchLiveTileFallback(context, child.iconKey)
    }
}

/** Opens a pinned contact's card ([ContactTile]) via `ACTION_VIEW`. Best-effort. */
private fun openContactCard(context: Context, contactId: Long, lookupKey: String) {
    val intent = Intent(Intent.ACTION_VIEW, contactLookupUri(contactId, lookupKey))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun launchLiveTileFallback(context: Context, iconKey: String?) {
    if (iconKey == "clock") {
        openClock(context)
        return
    }
    val intent = when (iconKey) {
        "calendar" -> Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse("content://com.android.calendar/time"))
        "weather" -> Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse("https://www.google.com/search?q=weather"))
        else -> return
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * Opens the device's default clock app on its alarms screen via
 * [AlarmClock.ACTION_SHOW_ALARMS] — the standard, app-agnostic way to open the
 * clock. Returns true if an app handled it; false (no clock app) so the caller can
 * fall back to a normal package launch.
 */
private fun openClock(context: Context): Boolean = runCatching {
    context.startActivity(
        Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
}.getOrDefault(false)

/**
 * Captures the current window contents via [android.view.PixelCopy], scales to a
 * 360 px-wide JPEG thumbnail, and writes it to `filesDir/snapshots/snapshot_<id>.jpg`.
 * Returns the absolute file path on success, null if PixelCopy fails or any IO error
 * occurs. Must be called from a coroutine on the main dispatcher.
 */
internal suspend fun captureSnapshotJpeg(
    activity: android.app.Activity,
    context: Context,
    id: String,
): String? {
    val decorView = activity.window.decorView
    if (decorView.width == 0 || decorView.height == 0) return null
    val full = android.graphics.Bitmap.createBitmap(
        decorView.width, decorView.height, android.graphics.Bitmap.Config.ARGB_8888,
    )
    val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
    android.view.PixelCopy.request(
        activity.window, full,
        { result -> deferred.complete(result == android.view.PixelCopy.SUCCESS) },
        android.os.Handler(android.os.Looper.getMainLooper()),
    )
    if (!deferred.await()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val thumbW = 360
            val thumbH = (360f * full.height / full.width).toInt()
            val thumb = android.graphics.Bitmap.createScaledBitmap(full, thumbW, thumbH, true)
            val dir = java.io.File(context.filesDir, "snapshots").also { it.mkdirs() }
            val file = java.io.File(dir, "snapshot_$id.jpg")
            java.io.FileOutputStream(file).use { out ->
                thumb.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            }
            file.absolutePath
        }.getOrNull()
    }
}
