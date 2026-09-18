@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.tileshell.feature.start

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tileshell.core.data.AppCategories
import com.tileshell.core.data.AppIconCache
import com.tileshell.core.data.AppLauncher
import com.tileshell.core.data.CachedScreenshotPrefs
import com.tileshell.core.data.CalendarSystemTile
import com.tileshell.core.data.calendarSystemFor
import com.tileshell.core.data.CommodityTile
import com.tileshell.core.data.ContactTile
import com.tileshell.core.data.CountdownTile
import com.tileshell.core.data.FolderChild
import com.tileshell.core.data.SportsTile
import com.tileshell.core.data.StepsPrefs
import com.tileshell.core.data.StockTile
import com.tileshell.core.data.TileColors
import com.tileshell.core.data.Section
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.UNSECTIONED_LABEL
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.WeatherTile
import com.tileshell.core.data.hasNotesTile
import com.tileshell.core.data.settings.FontStyle
import com.tileshell.core.data.settings.HomeStyle
import com.tileshell.core.data.settings.IconShape
import com.tileshell.core.data.settings.LiveRefreshRate
import com.tileshell.core.data.settings.TileColorSource
import com.tileshell.core.data.settings.TileFill
import com.tileshell.core.data.settings.TilePackMode
import com.tileshell.core.data.settings.WallpaperSyncTarget
import com.tileshell.core.data.settings.isAnchored
import com.tileshell.core.data.shortcutIconDrawable
import com.tileshell.core.design.CornerArcGlyph
import com.tileshell.core.design.DarkColorTokens
import com.tileshell.core.design.Glass
import com.tileshell.core.design.LIGHT_BACKGROUND_LUMINANCE_THRESHOLD
import com.tileshell.core.design.LocalAccent
import com.tileshell.core.design.LocalColorTokens
import com.tileshell.core.design.LocalTileCornerRadius
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.LocalTileFont
import com.tileshell.core.design.LocalTileGradient
import com.tileshell.core.design.NunitoFamily
import com.tileshell.core.design.OutfitFamily
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.WallpaperGradient
import com.tileshell.core.design.Wallpapers
import com.tileshell.core.design.Wallpapers.NONE_ID
import com.tileshell.core.design.colorTokens
import com.tileshell.core.design.isLightBackground
import com.tileshell.core.design.themedBase
import com.tileshell.core.design.tileGradientBrush
import com.tileshell.core.design.tiltOnPress
import com.tileshell.core.design.wallpaperWindow
import com.tileshell.feature.applist.AppListScreen
import com.tileshell.feature.livetiles.AlarmTileFace
import com.tileshell.feature.livetiles.BatterySmallFace
import com.tileshell.feature.livetiles.BatteryTileFace
import com.tileshell.feature.livetiles.CalendarSmallFace
import com.tileshell.feature.livetiles.CalendarSystemSmallFace
import com.tileshell.feature.livetiles.CalendarSystemTileFace
import com.tileshell.feature.livetiles.CalendarTileFace
import com.tileshell.feature.livetiles.ClockSmallFace
import com.tileshell.feature.livetiles.ClockTileFace
import com.tileshell.feature.livetiles.CommoditySmallFace
import com.tileshell.feature.livetiles.CommodityTileFace
import com.tileshell.feature.livetiles.ConversationTileFace
import com.tileshell.feature.livetiles.CountdownSmallFace
import com.tileshell.feature.livetiles.CountdownTileFace
import com.tileshell.feature.livetiles.FlashlightSmallFace
import com.tileshell.feature.livetiles.FlashlightTileFace
import com.tileshell.feature.livetiles.INDIA_COUNTRY_CODE
import com.tileshell.feature.livetiles.INTERNATIONAL_REGION_CODE
import com.tileshell.feature.livetiles.LayoutAutoBackupWorker
import com.tileshell.feature.livetiles.LiveFace
import com.tileshell.feature.livetiles.MediaSessionsEffect
import com.tileshell.feature.livetiles.MoonPhaseTileFace
import com.tileshell.feature.livetiles.MusicTileFace
import com.tileshell.feature.livetiles.NotesTileFace
import com.tileshell.feature.livetiles.NotificationAccess
import com.tileshell.feature.livetiles.NotificationCenter
import com.tileshell.feature.livetiles.NotificationSnapshot
import com.tileshell.feature.livetiles.NotificationTileFace
import com.tileshell.feature.livetiles.OemBatteryGuard
import com.tileshell.feature.livetiles.PeopleTileFace
import com.tileshell.feature.livetiles.PhotosData
import com.tileshell.feature.livetiles.PhotosStore
import com.tileshell.feature.livetiles.PhotosTileFace
import com.tileshell.feature.livetiles.SELECTABLE_COUNTRIES
import com.tileshell.feature.livetiles.SportsLinks
import com.tileshell.feature.livetiles.SportsTileFace
import com.tileshell.feature.livetiles.StepsSmallFace
import com.tileshell.feature.livetiles.StepsTileFace
import com.tileshell.feature.livetiles.StickyNoteTileFace
import com.tileshell.feature.livetiles.StockSmallFace
import com.tileshell.feature.livetiles.StockTileFace
import com.tileshell.feature.livetiles.TasksTileFace
import com.tileshell.feature.livetiles.WeatherRefreshWorker
import com.tileshell.feature.livetiles.WeatherSmallFace
import com.tileshell.feature.livetiles.WeatherTileFace
import com.tileshell.feature.livetiles.canShowSystemPermissionDialog
import com.tileshell.feature.livetiles.contactLookupUri
import com.tileshell.feature.livetiles.openAppPermissionSettings
import com.tileshell.feature.livetiles.regionDisplayName
import com.tileshell.feature.livetiles.rememberBatteryOptimizationExempt
import com.tileshell.feature.livetiles.rememberContactPhotoUri
import com.tileshell.feature.livetiles.rememberFlipState
import com.tileshell.feature.livetiles.rememberLiveTilesActive
import com.tileshell.feature.livetiles.rememberNotificationAccess
import com.tileshell.feature.livetiles.rememberPermissionGranted
import com.tileshell.feature.livetiles.rememberTileBitmap
import com.tileshell.feature.livetiles.widget.StepsWidgetRefreshWorker
import com.tileshell.feature.personalize.AboutSheet
import com.tileshell.feature.personalize.BackupRestoreSheet
import com.tileshell.feature.personalize.CalendarSystemPickerSheet
import com.tileshell.feature.personalize.CategoryFolderSheet
import com.tileshell.feature.personalize.CommodityPickerSheet
import com.tileshell.feature.personalize.CountdownEditorSheet
import com.tileshell.feature.personalize.EdgeStripSheet
import com.tileshell.feature.personalize.FeedSourceItem
import com.tileshell.feature.personalize.HiddenAppsSheet
import com.tileshell.feature.personalize.LayoutHistorySheet
import com.tileshell.feature.personalize.NewsRegionSheet
import com.tileshell.feature.personalize.NotesSheet
import com.tileshell.feature.personalize.PermissionsSheet
import com.tileshell.feature.personalize.PersonalizeGuidePrefs
import com.tileshell.feature.personalize.PersonalizeGuideSheet
import com.tileshell.feature.personalize.PersonalizeSheet
import com.tileshell.feature.personalize.RegionOption
import com.tileshell.feature.personalize.SportsPickerSheet
import com.tileshell.feature.personalize.StickyNoteEditorSheet
import com.tileshell.feature.personalize.StockPickerSheet
import com.tileshell.feature.personalize.TaskListSheet
import com.tileshell.feature.personalize.WeatherLocationSheet
import com.tileshell.feature.personalize.WidgetListSheet
import com.tileshell.feature.start.feed.FeedPage
import com.tileshell.feature.start.feed.googleSearchUrl
import com.tileshell.feature.start.feed.pagerCommitTarget
import com.tileshell.feature.start.feed.rememberFeedPalette
import com.tileshell.feature.system.AppUpdateState
import com.tileshell.feature.system.rememberAppUpdateState
import com.tileshell.feature.system.rememberDefaultLauncherState
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    onOpenNotifications: () -> Unit = {},
) {
    val tiles by viewModel.tiles.collectAsStateWithLifecycle()
    // The "main" (unsectioned) page only — see SectionBlocks.kt's TileBlock doc:
    // sections are swipeable pages, and the unsectioned block is always the
    // first one, "the anchor/home page you land on before swiping into named
    // ones". WallpaperStartPreview packs whatever list it's given as one flat
    // grid with no notion of blocks/pages at all, so feeding it every tile
    // (main page + every named section mixed together) produced a jumbled
    // arrangement unrelated to any real page — user-reported ("wallpaper
    // preview should always show main page... now it shows some unidentified
    // page"). Filtering to just the unsectioned tiles here is enough to make
    // the preview always show that one anchor page, matching what "main page"
    // means in this app.
    val mainPageTiles = remember(tiles) { tiles.filter { it.sectionId == null } }
    val swipeEnabled by viewModel.swipeEnabled.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val selectedTileId by viewModel.selectedTileId.collectAsStateWithLifecycle()
    val expandedFolderId by viewModel.expandedFolderId.collectAsStateWithLifecycle()
    val personalizeOpen by viewModel.personalizeOpen.collectAsStateWithLifecycle()
    val aboutOpen by viewModel.aboutOpen.collectAsStateWithLifecycle()
    val personalizeGuideOpen by viewModel.personalizeGuideOpen.collectAsStateWithLifecycle()
    val historyOpen by viewModel.historyOpen.collectAsStateWithLifecycle()
    val layoutHistory by viewModel.layoutHistory.collectAsStateWithLifecycle()
    val backupOpen by viewModel.backupOpen.collectAsStateWithLifecycle()
    val foldersOpen by viewModel.foldersOpen.collectAsStateWithLifecycle()
    val hiddenAppsOpen by viewModel.hiddenAppsOpen.collectAsStateWithLifecycle()
    val addWidgetsOpen by viewModel.addWidgetsOpen.collectAsStateWithLifecycle()
    val tasksOpen by viewModel.tasksOpen.collectAsStateWithLifecycle()
    val notesOpen by viewModel.notesOpen.collectAsStateWithLifecycle()
    val stickyNoteEditTileId by viewModel.stickyNoteEditTileId.collectAsStateWithLifecycle()
    val countdownEditTileId by viewModel.countdownEditTileId.collectAsStateWithLifecycle()
    val sportsEditTileId by viewModel.sportsEditTileId.collectAsStateWithLifecycle()
    val stockEditTileId by viewModel.stockEditTileId.collectAsStateWithLifecycle()
    val commodityEditTileId by viewModel.commodityEditTileId.collectAsStateWithLifecycle()
    val calendarSystemEditTileId by viewModel.calendarSystemEditTileId.collectAsStateWithLifecycle()
    val weatherLocationTarget by viewModel.weatherLocationTarget.collectAsStateWithLifecycle()
    val permissionsOpen by viewModel.permissionsOpen.collectAsStateWithLifecycle()
    val newsRegionOpen by viewModel.newsRegionOpen.collectAsStateWithLifecycle()
    val edgeStripOpen by viewModel.edgeStripOpen.collectAsStateWithLifecycle()
    val quickPanelOpen by viewModel.quickPanelOpen.collectAsStateWithLifecycle()
    val homeStyleWizardOpen by viewModel.homeStyleWizardOpen.collectAsStateWithLifecycle()
    val whatsNewOpen by viewModel.whatsNewOpen.collectAsStateWithLifecycle()
    val searchOpen by viewModel.searchOpen.collectAsStateWithLifecycle()
    // Hoisted above the EdgeStrip composable so its expanded/collapsed state survives
    // being unmounted while personalize/edit-mode/a folder is on top (it used to live
    // inside EdgeStrip itself and reset to expanded every time the strip remounted).
    var edgeStripExpanded by remember { mutableStateOf(true) }
    val hiddenPackages by viewModel.hiddenPackages.collectAsStateWithLifecycle()
    val isAppList by viewModel.isAppList.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    // One pager page per section plus the trailing implicit unsectioned block
    // (mirrors `blocksFor`'s own "sections + exactly one trailing unsectioned
    // block" invariant, without needing `order`/`byId`/`blocksFor` here — see
    // SectionBlocks.kt). Always ≥ 1, so a fresh install with zero named
    // sections still has exactly one Start block page, same as before this
    // feature existed.
    val blockCount = sections.size + 1
    val upper = blockCount.toFloat()
    val sortedSections = remember(sections) { sections.sortedBy { it.order } }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val feedSources by viewModel.feedSources.collectAsStateWithLifecycle()
    val feedRegions by viewModel.feedRegions.collectAsStateWithLifecycle()
    // Live notification state (FR-1.2 badges, FR-2 mail/messages). Empty until the
    // user enables notification access, which keeps every tile static / un-badged.
    val notifications by NotificationCenter.snapshot.collectAsStateWithLifecycle()
    val notificationAccess = rememberNotificationAccess()
    val batteryExempt = rememberBatteryOptimizationExempt()
    val contactsGranted = rememberPermissionGranted(android.Manifest.permission.READ_CONTACTS)
    val calendarGranted = rememberPermissionGranted(android.Manifest.permission.READ_CALENDAR)
    val locationGranted = rememberPermissionGranted(android.Manifest.permission.ACCESS_COARSE_LOCATION)
    val activityGranted = rememberPermissionGranted(android.Manifest.permission.ACTIVITY_RECOGNITION)
    // The ViewModel's own init-time attempt to seed the feed greeting's name from
    // the device contact profile races the runtime permission dialog (it always
    // sees "denied" then, since the dialog hasn't resolved yet) — retry here
    // whenever contactsGranted flips to true, whether that's from the in-app
    // launcher or the user granting it via system Settings and resuming.
    LaunchedEffect(contactsGranted) {
        if (contactsGranted) viewModel.seedUserNameFromProfileIfBlank()
    }
    val (isDefaultLauncher, onSetDefaultLauncher) = rememberDefaultLauncherState()
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

    // The expanded folder's model (null collapses it; also self-collapses if
    // the folder is removed or dissolved while expanded).
    val expandedFolder = remember(tiles, expandedFolderId) {
        tiles.firstOrNull { it.id == expandedFolderId } as? TileModel.Folder
    }
    LaunchedEffect(expandedFolderId, expandedFolder) {
        if (expandedFolderId != null && expandedFolder == null) viewModel.collapseFolder()
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
    // only through the tiles. Decode the custom photo here (when set, regardless of
    // tiled mode — also used by backgroundIsLight below) so tiled mode can window
    // into it; a bundled gradient is drawn directly by the window modifier.
    val tiledWallpaper = settings.tiledWallpaper
    val customWallpaperBitmap = settings.customWallpaperUri?.let { rememberWallpaperBitmap(it) }
    val tiledPhoto = if (tiledWallpaper) customWallpaperBitmap else null

    // TileColorSource.WALLPAPER_ACCENT (FR-7 follow-up): the same wallpaper-derived
    // accent colour the feed/glance page and Quick Panel already use (see
    // rememberFeedPalette), so tiles read as one coordinated palette with them.
    // Computed unconditionally (cheap — memoized internally, same as the feed/
    // Quick Panel's own always-on calls) so Personalize can preview the actual
    // colour on its "wallpaper" swatch before the user switches to it; only fed
    // into the tile colour-resolution chain below when that mode is active. When
    // there's no real wallpaper (`noWallpaper`), `wallpaper` itself still resolves
    // to a bundled gradient (Wallpapers.forId's fallback), so falling through to
    // rememberFeedPalette here would show that gradient's colour as if it were
    // "the wallpaper's" — same guard the feed page/Quick Panel already use.
    val wallpaperAccentColor = if (noWallpaper) {
        accent
    } else {
        rememberFeedPalette(customWallpaperBitmap, wallpaper, accent).second
    }
    val wallpaperAccent = if (settings.tileColorSource == TileColorSource.WALLPAPER_ACCENT) {
        wallpaperAccentColor
    } else {
        null
    }

    // Whether the user's actual chosen background — the plain screen bg (no
    // wallpaper), a bundled gradient's themed base, or a custom/Bing photo's
    // sampled brightness — reads as light. A solid tile's fill never shows this
    // (always the saturated accent colour), but glass (transparent) tiles and
    // "wallpaper behind tiles" mode both let it show through, where white
    // text/icons would lose contrast — user-requested, see docs/DECISIONS.md
    // "Live tile text: black when the wallpaper behind it is light".
    val chosenWallpaperIsLight = rememberChosenWallpaperIsLight(
        customPhoto = customWallpaperBitmap,
        noWallpaper = noWallpaper,
        wallpaper = wallpaper,
        dark = dark,
        screenBg = tokens.bg,
    )
    // The general screen area outside any tile (behind the chevron/gear) always
    // shows tokens.bg in tiled mode (the real photo/gradient is only windowed
    // *into* each tile there), so it needs its own, slightly different check.
    val screenBackgroundIsLight = if (tiledWallpaper) isLightBackground(tokens.bg) else chosenWallpaperIsLight

    // URI of a just-picked wallpaper photo waiting for the user to crop/position it.
    // Set by the picker callback; cleared when the crop overlay is confirmed or cancelled.
    var pendingWallpaperCropUri by remember { mutableStateOf<String?>(null) }
    // True while re-framing the active (custom/Bing) wallpaper — adjusts alignment only.
    var adjustingWallpaper by remember { mutableStateOf(false) }
    // True while the recent-Bing-wallpapers viewer is open.
    var bingHistoryOpen by remember { mutableStateOf(false) }
    // A brand-new wallpaper pick (gradient/photo/Bing) waiting on the
    // "where should this apply" chooser below — nothing is actually set as
    // TileShell's own wallpaper until one of its options is tapped;
    // dismissing it discards the pick entirely (per explicit request, "not
    // now" means not even setting it for TileShell). Not used for a plain
    // re-crop/re-frame of the *already-active* image — that changes no
    // content, so there's nothing new to choose a target for.
    var pendingWallpaperPick by remember { mutableStateOf<PendingWallpaperPick?>(null) }

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

    // A photo shared into TileShell from another app (e.g. Gallery/Photos' own "share"
    // sheet) — MainActivity forwards it via viewModel.receiveSharedImage(uri) when it
    // receives an ACTION_SEND intent. Same copy-then-crop flow as the picker above: the
    // share grant is only valid for the life of this intent, so it's imported into
    // private storage immediately, then the existing crop overlay takes over exactly as
    // if the photo had been picked from within the app.
    val sharedWallpaperUri by viewModel.sharedWallpaperUri.collectAsStateWithLifecycle()
    LaunchedEffect(sharedWallpaperUri) {
        val incoming = sharedWallpaperUri ?: return@LaunchedEffect
        val local = withContext(Dispatchers.IO) { MediaImport.importWallpaper(context, incoming) }
        if (local != null) pendingWallpaperCropUri = local.toString()
        viewModel.consumeSharedWallpaperUri()
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
    // Physical activity (steps). Unlike the three above, this one is *not* in
    // MainActivity's upfront batch — it's asked contextually the first time a
    // steps face renders (see StepsTile.kt's gate), which is why it needs its
    // own asked-flag bookkeeping here too: granting it from this row is the
    // permanent way back in after that one-shot ask, and the placed steps
    // widget is pushed straight away rather than waiting out its 15-min
    // refresh interval.
    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        StepsPrefs.markPermissionAsked(context)
        if (granted) StepsWidgetRefreshWorker.refreshNow(context)
    }
    // A permanently denied runtime permission makes `launch` a silent no-op —
    // the system dialog never appears — so a row wired straight to it reads as
    // a dead button. Route to this app's own Settings entry instead in that
    // state, the only place it can still be turned on.
    fun requestPermissionOrOpenSettings(
        permission: String,
        asked: Boolean,
        launch: () -> Unit,
    ) {
        if (canShowSystemPermissionDialog(context, permission, asked)) launch() else openAppPermissionSettings(context)
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
    // Pager position: -1 = feed (left), 0 .. blockCount-1 = Start's own block
    // pages (one per section, plus the trailing unsectioned page), blockCount
    // = app list (right). The feed page is the swipe-right surface; it is
    // only reachable when enabled (FR-7).
    val feedEnabled = settings.feedEnabled
    // In landscape we drop the feed↔Start swipe and show both as side-by-side
    // panels instead (so a 4-col grid never balloons to fill the wide screen).
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val progress = remember { Animatable(0f) }
    // Live tiles pause when Start is no longer the foreground surface: the app
    // list (>50% right) or the feed (>50% left) has taken over, or an overlay
    // sits above it (FR-2 gating). `upper` is however many block pages Start
    // currently has (see `blockCount` above) — the app list always sits at
    // that numeric slot, not a hardcoded `1f`, since Start itself may now
    // occupy several pager positions (one per section, plus the trailing
    // unsectioned page).
    // Keyed on `upper` — without this, the derivedStateOf's calculation block
    // is created once (on the very first composition, when `sections` may
    // still be the collectAsStateWithLifecycle default empty list, i.e.
    // `upper == 1f`) and never recreated, so it would keep comparing against
    // that stale bound forever even once real section data raises `upper` —
    // every non-main block page's resting position (progress 1, 2, 3, ...)
    // is `>= 1f - 0.5f`, so every one of them would then misreport as "the
    // app list is showing" (found via: page-dot indicator, and live tiles,
    // both only working on the main page — `liveSuspended` below reads this
    // same value).
    val appListShown by remember(upper) { derivedStateOf { progress.value >= upper - 0.5f } }
    val feedShown by remember { derivedStateOf { progress.value <= -0.5f } }
    // An expanded folder no longer suspends live tiles — it's inline on Start,
    // not a separate full-screen surface, so there's nothing to pause behind.
    val liveSuspended = appListShown || feedShown || personalizeOpen
    val settleSpec = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    fun settleTo(target: Float) {
        scope.launch {
            progress.animateTo(target, settleSpec)
            viewModel.setAppList(target >= upper - 0.5f)
        }
    }

    // Which block page is "active" — the single source of truth for "pin a
    // new tile/live tile into whichever section is currently showing."
    // Naively reading this straight off `progress.value` breaks the instant
    // you rest on the app list (`progress.value == upper`), which is exactly
    // when a tile actually needs to be pinned (the add-live-tile sheet, the
    // weather-location picker, both opened from the app list) — that would
    // silently regress to "always pins to the last block," the exact bug
    // this feature's own history says was already fixed once. Instead, only
    // update while resting on a real block page, and always read it back
    // re-clamped so a delete/reorder can never leave it dangling.
    //
    // Real bug found on-device: `progress` is animated smoothly (spring/
    // animateTo) whenever the chevron or any other `settleTo(upper)` call
    // jumps straight to the app list — that animation sweeps `progress.value`
    // continuously through every intermediate block index on its way there,
    // so this effect kept firing and overwriting `lastActiveBlockIndex` with
    // each block it passed, landing on `blockCount - 1` (the very last page)
    // right before the animation reaches `upper` — regardless of which page
    // the user actually started from. A plain drag settle (via `snapTo`, not
    // an animation) never sets `isRunning`, so gating on it here only
    // suppresses updates during an animated fly-through, not real dragging.
    val currentIntPosition by remember { derivedStateOf { progress.value.roundToInt() } }
    var lastActiveBlockIndex by remember { mutableStateOf(0) }
    LaunchedEffect(currentIntPosition, blockCount, progress.isRunning) {
        if (!progress.isRunning && currentIntPosition in 0 until blockCount) lastActiveBlockIndex = currentIntPosition
    }
    val activeBlockIndex = lastActiveBlockIndex.coerceIn(0, blockCount - 1)
    // Block 0 is always the unsectioned "main" page (see SectionBlocks.blocksFor,
    // which prepends it ahead of every real section) — sortedSections only holds
    // named sections, so a real section at block index k lives at
    // sortedSections[k - 1], not sortedSections[k]. Mirrors the same -1 correction
    // at the cross-page-drag target-section lookup below.
    val activeSectionId: String? = sortedSections.getOrNull(activeBlockIndex - 1)?.id

    // Keep the pager in a valid range whenever the number of block pages
    // changes (a section created/deleted/merged) — e.g. stay on the app
    // list at its new numeric slot rather than sliding into where a deleted
    // section used to sit.
    //
    // Deliberately branches on `isAppList` (the ViewModel's own committed
    // open/close flag, set only by `settleTo`'s post-animation call) rather
    // than the live-derived `appListShown` above. `appListShown` recomputes
    // from the CURRENT `upper`, which shrinks the instant a page is removed
    // — so deleting whichever page you're currently resting on, when it
    // happens to be the *last* one, makes `upper` drop to equal your
    // unchanged `progress.value`, and `progress.value >= upper - 0.5f`
    // trivially becomes true even though you were never on the app list.
    // That misfire took this exact branch and snapped straight to the app
    // list instead of reclamping to the newly-last page — reproduced
    // on-device: removing the last named page via "remove page & tiles"
    // landed on the app list instead of the adjacent page. `isAppList` only
    // reflects a real, deliberate `settleTo(upper)`, so it can't be fooled
    // by `upper` moving out from under an unrelated resting position.
    LaunchedEffect(blockCount) {
        when {
            isAppList -> progress.snapTo(upper)
            feedShown -> {} // feed's position (-1) is independent of blockCount
            else -> progress.animateTo(progress.value.coerceIn(0f, (blockCount - 1).toFloat()), settleSpec)
        }
    }

    // The app list's "widgets" row (long-press an app → widgets → pick one)
    // hands the chosen provider up here; jumping to the feed page (settleTo(-1f))
    // and feeding it into FeedPage/WidgetSection's own bind pipeline is all this
    // needs to do — WidgetSection already owns the real add-widget machinery.
    var pendingFeedWidget by remember { mutableStateOf<android.appwidget.AppWidgetProviderInfo?>(null) }

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
    // derivedStateOf, like appListShown/feedShown above — and for the same
    // reason. Read plainly, this is a composition-time read of a value that
    // changes every frame of a swipe, so it invalidated the whole StartScreen
    // scope 60-120 times a second mid-gesture. It only feeds three booleans
    // (quickSearch/quickPanel/edgeSwipe enablement) that flip once, when the
    // pager crosses the threshold; every other progress.value read in this file
    // is already deferred into a graphicsLayer block, which reads at draw time
    // and never recomposes. This one line was the exception.
    //
    // "Resting on Start" now means resting on ANY of its block pages
    // (0 until blockCount), not just position 0 — real bug, found the same
    // way as the appListShown one above: this used to just check `≈0`, back
    // when Start was a single page at position 0, so quick search/quick
    // panel/edge-swipe silently only worked on the main page and did nothing
    // on any named section page. Keyed on `blockCount` for the same reason
    // appListShown needed `upper` as a key.
    val restingAtStart by remember(blockCount) {
        derivedStateOf {
            val rounded = progress.value.roundToInt()
            abs(progress.value - rounded) < 0.05f && rounded in 0 until blockCount
        }
    }
    val anySheetOpen = personalizeOpen || aboutOpen || historyOpen || backupOpen ||
        foldersOpen || hiddenAppsOpen || addWidgetsOpen || (tasksOpen != null) || notesOpen ||
        (stickyNoteEditTileId != null) || (countdownEditTileId != null) || (sportsEditTileId != null) || (stockEditTileId != null) ||
        (commodityEditTileId != null) || (calendarSystemEditTileId != null)
    val quickSearchEnabled = swipeEnabled && restingAtStart && !searchOpen && !quickPanelOpen && !anySheetOpen
    val quickPanelEnabled = swipeEnabled && restingAtStart && !searchOpen && !quickPanelOpen && !anySheetOpen
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
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        viewModel.openSearch()
                    }
                }
                if (triggered) event.changes.forEach { it.consume() }
            }
        }
    }

    // Two-finger swipe-down opens the quick panel (Wi-Fi/Bluetooth/flashlight/
    // DND/airplane/location chips + volume sliders — see
    // docs/QUICK-PANEL-SPEC.md). Identical shape to quickSearchGesture with the
    // vertical sign flipped — "down" vs. quick search's "up" means the two
    // gestures can never both fire for the same swipe. (Swapped directions
    // from the original down=search/up=panel mapping per explicit user
    // request — see isQuickPanelSwipe's doc for why.)
    val quickPanelGesture = Modifier.pointerInput(quickPanelEnabled) {
        if (!quickPanelEnabled) return@pointerInput
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
                    if (isQuickPanelSwipe(dy, dx, thresholdPx)) {
                        triggered = true
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        viewModel.openQuickPanel()
                    }
                }
                if (triggered) event.changes.forEach { it.consume() }
            }
        }
    }

    // Single-finger swipe from a screen edge: down-left opens the system
    // notification shade — not in the WP prototype/spec, see DECISIONS "Edge
    // swipe-down for notifications/quick settings". Down-right opens this
    // app's own Quick Panel instead of the system's quick settings (changed
    // per explicit user request — the system quick-settings action read as
    // confusing next to this app's own Quick Panel) — matching the two-finger
    // swipe-down gesture below, which also opens the Quick Panel. Up from
    // *either* edge opens quick search instead — an additional, easier-to-
    // discover path alongside the two-finger swipe-up gesture (quickSearchGesture
    // above), kept in sync with it after that gesture's direction swapped from
    // down to up per explicit user request (it used to mirror the Quick Panel
    // up-gesture; now it mirrors quick search's).
    // Same enable-gating as the two-finger gestures, and the same "don't
    // consume until triggered" shape, but keyed on which physical edge the
    // touch started in rather than pointer count — so it never steals an
    // ordinary tap/scroll/tile-drag starting away from either edge.
    val edgeSwipeEnabled = swipeEnabled && restingAtStart && !searchOpen && !quickPanelOpen && !anySheetOpen
    val edgeSwipeGesture = Modifier.pointerInput(edgeSwipeEnabled) {
        if (!edgeSwipeEnabled) return@pointerInput
        val thresholdPx = 40.dp.toPx()
        val zonePx = EDGE_SWIPE_ZONE_DP.dp.toPx()
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val zone = edgeZoneFor(first.position.x, size.width.toFloat(), zonePx)
            if (zone == EdgeZone.NONE) return@awaitEachGesture
            val start = first.position
            var triggered = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                val change = pressed.firstOrNull { it.id == first.id } ?: break
                if (!triggered) {
                    val dy = change.position.y - start.y
                    val dx = change.position.x - start.x
                    if (isEdgeSwipeDown(dy, dx, thresholdPx)) {
                        triggered = true
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        when (zone) {
                            EdgeZone.LEFT -> onOpenNotifications()
                            EdgeZone.RIGHT -> viewModel.openQuickPanel()
                            EdgeZone.NONE -> {}
                        }
                    } else if (isEdgeSwipeUp(dy, dx, thresholdPx)) {
                        triggered = true
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
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
        LocalAccent provides (wallpaperAccent ?: accent),
        LocalTileCornerRadius provides settings.cornerRadius,
        LocalTileGradient provides (settings.tileFill == TileFill.GRADIENT),
        LocalTileFont provides tileFont,
        LocalTextStyle provides baseTextStyle.copy(fontFamily = tileFont),
        LocalTileFaceColor provides Glass.faceTextColor(
            (settings.glass || tiledWallpaper || settings.borderlessTiles) && chosenWallpaperIsLight,
        ),
    ) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
            .then(quickSearchGesture)
            .then(quickPanelGesture)
            .then(edgeSwipeGesture),
    ) {
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
            // Cross-fades whenever the wallpaper's actual identity changes (a new
            // custom photo confirmed from the crop overlay, a Bing image applied, a
            // different bundled gradient picked) — keyed on that identity alone, not
            // alignment/zoom, so re-framing the *same* photo updates in place with no
            // fade flash. Without this the swap was an instant, jarring cut.
            val wallpaperIdentity = settings.customWallpaperUri ?: settings.wallpaperId
            Crossfade(
                targetState = wallpaperIdentity,
                animationSpec = tween(420),
                label = "wallpaperCrossfade",
            ) {
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
        }

        // Horizontal pager gesture, parameterised by the width of the page being
        // swiped (`pageWidthPx`) and the lowest reachable position (`lower`: −1 to
        // reach the feed in portrait, 0 when there is no swipe-in feed page).
        // Detection runs in the Initial pass so a dominant horizontal drag is
        // claimed before the vertical grid scroll (a child) can consume it;
        // vertical drags pass straight through.
        fun pagerModifier(pageWidthPx: Float, lower: Float): Modifier =
            Modifier.pointerInput(swipeEnabled, pageWidthPx, lower, upper) {
                if (!swipeEnabled) return@pointerInput
                val slop = 12.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val base = progress.value
                    var horizontal = false
                    var decided = false
                    var lastTarget = base
                    // Animatable.snapTo can't be called directly here —
                    // AwaitPointerEventScope is a restricted-suspension scope, so
                    // only its own member functions may suspend. The previous fix
                    // (dropping scope.launch) didn't compile for exactly that
                    // reason. Instead: one conflated channel + one background
                    // consumer coroutine *per gesture* (created lazily, only once
                    // the drag is recognised as horizontal), rather than the
                    // original scope.launch { progress.snapTo(target) } fired once
                    // per pointer-move event (up to ~120/s) — that per-event
                    // coroutine launch overhead is what read as the pager lagging/
                    // stuttering behind the finger. trySend is non-suspending, so
                    // it's fine to call from this restricted scope.
                    var updates: Channel<Float>? = null
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (!decided) {
                            if (abs(dx) > slop && abs(dx) > abs(dy) * 1.2f) {
                                decided = true
                                horizontal = true
                                val channel = Channel<Float>(Channel.CONFLATED)
                                updates = channel
                                scope.launch { for (t in channel) progress.snapTo(t) }
                            } else if (abs(dy) > slop) {
                                decided = true // vertical → leave it to the grid scroll
                            }
                        }
                        if (horizontal) {
                            change.consume()
                            lastTarget = (base - dx / pageWidthPx).coerceIn(lower, upper)
                            updates?.trySend(lastTarget)
                        }
                        if (!change.pressed) break
                    }
                    updates?.close()
                    if (horizontal) {
                        // Uses the synchronously-tracked lastTarget, not
                        // progress.value — the background consumer above applies
                        // updates asynchronously, so progress.value could still
                        // be a stale, earlier position at this exact instant.
                        settleTo(pagerCommitTarget(base, lastTarget, lower, upper).coerceAtLeast(lower))
                    }
                }
            }

        // Mirrors the EdgeStrip mount/suppress condition below — true exactly when the
        // strip is actually rendered and expanded (not just collapsed to its sliver),
        // so the app-list/gear affordance only rises to clear it when it's really there.
        val edgeStripVisible = settings.edgeStripEnabled && settings.edgeStripApps.isNotEmpty() &&
            !editMode && expandedFolderId == null && !personalizeOpen && !searchOpen && edgeStripExpanded

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
                    liveTilesEnabled = settings.liveTilesEnabled,
                    selectedTileId = selectedTileId,
                    accent = accent,
                    accentId = settings.accentId,
                    appIconColors = settings.tileColorSource == TileColorSource.APP_ICON,
                    stockRefreshRate = settings.stockRefreshRate,
                    commodityRefreshRate = settings.commodityRefreshRate,
                    sportsRefreshRate = settings.sportsRefreshRate,
                    wallpaperAccent = wallpaperAccent,
                    // Tiled-wallpaper mode ignores the gap setting (stays tight) so
                    // wider spacing never fragments the show-through wallpaper.
                    tileGapPx = if (tiledWallpaper) {
                        null
                    } else if (settings.borderlessTiles) {
                        // Fixed, not Personalize's own "tile spacing" slider
                        // (which is hidden for this style) — see
                        // BORDERLESS_TILE_GAP_DP's doc comment.
                        with(density) { BORDERLESS_TILE_GAP_DP.dp.toPx() }
                    } else {
                        with(density) { settings.tileGap.dp.toPx() }
                    },
                    glass = settings.glass,
                    transparency = settings.transparency,
                    glassLine = tokens.glassLine,
                    tiledWallpaper = tiledWallpaper,
                    borderless = settings.borderlessTiles,
                    tileOutline = settings.tileOutline,
                    wallpaper = wallpaper,
                    wallpaperPhoto = tiledPhoto,
                    screenBackgroundIsLight = screenBackgroundIsLight,
                    wallpaperAlignX = settings.wallpaperAlignX,
                    wallpaperAlignY = settings.wallpaperAlignY,
                    wallpaperZoom = settings.wallpaperZoom,
                    darkTheme = dark,
                    notifications = notifications,
                    widthPx = pageWidthPx,
                    viewportHeightPx = viewportHeightPx,
                    statusBarTopPx = statusBarTopPx,
                    hideStatusBar = settings.hideStatusBar,
                    columns = settings.columns,
                    sticky = settings.tilePackMode.isAnchored,
                    freeMode = settings.tilePackMode == TilePackMode.FREE,
                    homeStyle = settings.homeStyle,
                    iconShape = settings.iconShape,
                    // themedIcons intentionally not threaded here — parked (see
                    // DECISIONS.md "Themed icons: parked"); StartPage's own
                    // themedIcons param stays at its default false regardless
                    // of any already-persisted LauncherSettings.themedIcons value.
                    onSetTileSlot = viewModel::setTileGridSlot,
                    expandedFolderId = expandedFolderId,
                    onCollapseFolder = viewModel::collapseFolder,
                    onTile = { tile ->
                        when (tile) {
                            is TileModel.App -> {
                                // Launching any app tile (top-level, or one of the
                                // expanded folder's own children) implicitly
                                // dismisses whatever folder is currently expanded —
                                // "tap outside to close" for the top-level case,
                                // and simply tidy for the in-folder case.
                                if (expandedFolderId != null) viewModel.collapseFolder()
                                // The "personalize" tile is this app's own Personalize
                                // sheet, not a launchable app (blank package, like the
                                // weather/calendar liveOnly tiles) — the corner gear's
                                // replacement, see DefaultLayout's "personalize" role.
                                // Identified by label (its icon is just a visual choice,
                                // "palette" — see iconFor) rather than iconKey, so the two
                                // stay decoupled.
                                if (tile.packageName.isBlank() && tile.label == "personalize") {
                                    viewModel.openPersonalize()
                                } else if (tile.packageName.isBlank() && tile.iconKey == "tasks") {
                                    // The Tasks tile is a small in-app checklist, not a
                                    // launchable app — tapping it opens its own sheet
                                    // instead of falling into the liveOnly-fallback-intent
                                    // path every other blank-package tile uses. Each
                                    // pinned Tasks tile keeps its own independent list,
                                    // keyed by the tile's own stable id (user-reported:
                                    // every Tasks tile used to show the same one list).
                                    viewModel.openTasks(tile.id)
                                } else if (tile.packageName.isBlank() && tile.iconKey == "notepad") {
                                    viewModel.openNotes()
                                } else if (tile.packageName.isBlank() && tile.iconKey == "stickynote") {
                                    viewModel.openStickyNoteEditor(tile.id)
                                } else if (tile.packageName.isBlank() && tile.iconKey == "countdown") {
                                    viewModel.openCountdownEditor(tile.id)
                                } else if (tile.packageName.isBlank() && tile.iconKey == "sports") {
                                    // A configured tile opens the match's own
                                    // ESPN page (cached from the tile's own poll
                                    // loop — see SportsLinks); no team picked
                                    // yet, or no page cached, falls back to the
                                    // team picker.
                                    val configured = SportsTile.decode(tile.activityName) != null
                                    val webUrl = if (configured) SportsLinks.get(tile.id) else null
                                    when {
                                        webUrl != null -> runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                                        }
                                        configured -> Toast.makeText(context, "match details still loading", Toast.LENGTH_SHORT).show()
                                        else -> viewModel.openSportsEditor(tile.id)
                                    }
                                } else if (tile.packageName.isBlank() && tile.iconKey == "stock") {
                                    // A single-symbol tile opens that stock's real Yahoo
                                    // Finance page (a static URL from the symbol alone —
                                    // no cached-link machinery like SportsLinks needed);
                                    // a category tile, or no selection yet, reopens the
                                    // picker instead — there's no one page for a sector.
                                    val selection = StockTile.decode(tile.activityName)
                                    when (selection) {
                                        is StockTile.Selection.Single -> runCatching {
                                            val url = "https://finance.yahoo.com/quote/${Uri.encode(selection.symbol)}"
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                        else -> viewModel.openStockEditor(tile.id)
                                    }
                                } else if (tile.packageName.isBlank() && tile.iconKey == "commodity") {
                                    // Same "open the real Yahoo Finance page, or the
                                    // picker if nothing's picked yet" pattern as the
                                    // stock tile — a commodity/currency tile is always
                                    // a single symbol, so there's no category-vs-single
                                    // branch needed here.
                                    val decoded = CommodityTile.decode(tile.activityName)
                                    if (decoded != null) {
                                        runCatching {
                                            val url = "https://finance.yahoo.com/quote/${Uri.encode(decoded.first)}"
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    } else {
                                        viewModel.openCommodityEditor(tile.id)
                                    }
                                } else if (tile.packageName.isBlank() && tile.iconKey == "calsys") {
                                    // Same "open the real page, or the picker if
                                    // nothing's picked yet" pattern as stock/
                                    // commodity/sports above (user-requested: this
                                    // tile used to always reopen the picker even
                                    // once a system was already chosen) — a web
                                    // search for that system's calendar, matching
                                    // the home-screen widget's own tap behaviour
                                    // (see calendarSystemAppPendingIntent).
                                    val systemId = CalendarSystemTile.decode(tile.activityName)
                                    val displayName = systemId?.let { calendarSystemFor(it)?.displayName }
                                    if (displayName != null) {
                                        runCatching {
                                            val url = "https://www.google.com/search?q=" +
                                                Uri.encode("$displayName calendar today")
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    } else {
                                        viewModel.openCalendarSystemEditor(tile.id)
                                    }
                                } else if (tile.packageName.isBlank() && tile.iconKey == "weather") {
                                    // Same "open the real page" pattern as calendar
                                    // systems/stock/commodity/sports above (user-
                                    // requested: this tile used to always reopen
                                    // the location editor instead) — a weather
                                    // web search, matching the home-screen
                                    // widget's own tap behaviour (see
                                    // weatherAppPendingIntent).
                                    val query = when (val location = WeatherTile.decode(tile.activityName)) {
                                        is WeatherTile.Location.Fixed -> "weather in ${location.name}".trim()
                                        // A blank/un-encoded activityName (a tile
                                        // seeded before the location-picker flow
                                        // existed) still means "follow the
                                        // device's location" via the same
                                        // fallback resolveWeatherQuery already
                                        // uses — not "still needs setup" — so
                                        // there's no case left where tapping
                                        // should reopen the picker instead.
                                        WeatherTile.Location.Current, null -> "weather"
                                    }
                                    runCatching {
                                        val url = "https://www.google.com/search?q=" + Uri.encode(query)
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                } else {
                                    onTileClick(context, tile)
                                }
                            }
                            is TileModel.Folder -> viewModel.toggleFolder(tile.id)
                        }
                    },
                    onLaunchFolderChild = { child ->
                        if (child.packageName.isBlank() && child.label == "personalize") {
                            viewModel.openPersonalize()
                        } else if (child.packageName.isBlank() && child.iconKey == "tasks") {
                            // Matches the listId a folder child's own inline-expanded
                            // rendering uses (its synthetic AppTileContent `tile.id`),
                            // so the sheet shows exactly what that tile is displaying.
                            viewModel.openTasks(folderChildTileId(expandedFolderId.orEmpty(), child.rowId))
                        } else if (child.packageName.isBlank() && child.iconKey == "notepad") {
                            viewModel.openNotes()
                        } else {
                            launchFolderChild(context, child)
                        }
                    },
                    onPullOutFolderChild = { folderId, child -> viewModel.removeFolderChild(folderId, child) },
                    onPullOutFolderChildToMerge = { folderId, child, targetId ->
                        viewModel.pullFolderChildIntoMerge(folderId, child, targetId)
                        Toast.makeText(context, "grouped", Toast.LENGTH_SHORT).show()
                    },
                    onPullOutFolderChildToSlot = { folderId, child, slot ->
                        viewModel.pullFolderChildToSlot(folderId, child, slot)
                    },
                    onPullOutFolderChildToPosition = { folderId, child, beforeId ->
                        viewModel.pullFolderChildToPosition(folderId, child, beforeId)
                    },
                    onResizeFolderChild = { folderId, child -> viewModel.resizeFolderChild(folderId, child) },
                    onResizeFolderChildTo = { folderId, child, size ->
                        viewModel.resizeFolderChildTo(folderId, child, size)
                    },
                    onSetFolderChildColor = { folderId, rowId, colorId ->
                        val child = (tiles.firstOrNull { it.id == folderId } as? TileModel.Folder)
                            ?.children?.firstOrNull { it.rowId == rowId }
                        if (child != null) viewModel.setFolderChildAccent(child, colorId)
                    },
                    onRenameFolder = { folderId, name -> viewModel.renameFolder(folderId, name) },
                    onToggleFolderStack = { folderId -> viewModel.toggleFolderStack(folderId) },
                    onUnfoldFolder = viewModel::unfoldFolder,
                    onRemoveFolderAndTiles = viewModel::removeFolderAndTiles,
                    onReorderFolderChildren = viewModel::reorderFolderChildren,
                    onChevron = { settleTo(upper) },
                    onEnterEdit = { id ->
                        if (settings.lockLayout) {
                            Toast.makeText(
                                context,
                                "layout is locked — unlock it in personalize to edit",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.enterEdit(id)
                        }
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
                    onResizeTo = viewModel::resizeTo,
                    onResizeStack = viewModel::convertFolderToStack,
                    onUnpin = viewModel::unpin,
                    onSetTileColor = viewModel::setTileColor,
                    onSetTileDisplayAsIcon = viewModel::setTileDisplayAsIcon,
                    sections = sections,
                    onCreateSection = viewModel::createSection,
                    onRenameSection = viewModel::renameSection,
                    onDeleteSection = viewModel::deleteSection,
                    onRemovePageAndTiles = viewModel::removeSectionAndTiles,
                    onMoveSection = { id, direction ->
                        viewModel.moveSection(id, direction)
                        // Follow the section being reordered so it doesn't visually
                        // swap out from under the tap — safe because a section's own
                        // reorder buttons are only reachable on the block currently
                        // centered on-screen, so currentIntPosition is guaranteed to
                        // be that section's own index at the moment of the tap.
                        settleTo((currentIntPosition + direction).coerceIn(0, blockCount - 1).toFloat())
                    },
                    onAssignTileSection = viewModel::setTileSection,
                    pagerProgress = progress.value,
                    livePagerProgress = { progress.value },
                    activeBlockIndex = activeBlockIndex,
                    // A drag can only ever originate on the page currently on
                    // screen, so activeBlockIndex IS the source index for
                    // both callbacks below — no separate "which page did
                    // this start on" plumbing needed.
                    onCrossPageShift = { direction ->
                        val targetIndex = (activeBlockIndex + direction).coerceIn(0, blockCount - 1)
                        settleTo(targetIndex.toFloat())
                    },
                    onCrossPageDrop = { tileId, direction, targetSlot ->
                        // Block 0 is always the unsectioned "main" page (see
                        // SectionBlocks.kt); every index after it maps to
                        // sortedSections in order. The page itself already
                        // shifted at onCrossPageShift — this only commits the
                        // actual move, at wherever the touch ended up.
                        val targetIndex = (activeBlockIndex + direction).coerceIn(0, blockCount - 1)
                        val targetSectionId = sortedSections.getOrNull(targetIndex - 1)?.id
                        // Section + slot in one atomic call — calling
                        // setTileSection then setTileGridSlot separately
                        // raced every time (real bug, user-reported: "still
                        // placed at bottom" even after the auto-scroll fix —
                        // setTileGridSlot computes its placement synchronously
                        // off the tile's own still-stale section, scoping
                        // collision-resolution to the wrong page's tiles).
                        viewModel.moveTileToSectionAtSlot(tileId, targetSectionId, targetSlot)
                    },
                    onAdd = {
                        viewModel.exitEdit()
                        settleTo(upper)
                        Toast.makeText(context, "long-press an app to pin", Toast.LENGTH_SHORT)
                            .show()
                    },
                    onPersonalize = viewModel::openPersonalize,
                    onAddWidgets = viewModel::openAddWidgets,
                    onQuickPanel = viewModel::openQuickPanel,
                )
        }

        val renderAppList: @Composable () -> Unit = {
            AppListScreen(
                modifier = Modifier.fillMaxSize(),
                visible = isAppList,
                activeSectionId = activeSectionId,
                onPinned = { settleTo(lastActiveBlockIndex.toFloat()) },
                onOpenPersonalize = viewModel::openPersonalize,
                onAddWidget = { provider ->
                    if (feedEnabled) {
                        pendingFeedWidget = provider
                        settleTo(-1f)
                    } else {
                        Toast.makeText(context, "enable the glance page in personalize to pin widgets", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        // Feed page: an independent, opaque screen. In portrait it slides in over
        // Start from the left edge as the user swipes right (mirrors the app list);
        // in landscape it is the always-visible left panel (`active` stays true).
        val renderFeed: @Composable (Boolean) -> Unit = { active ->
            FeedPage(
                accent = accent,
                statusBarTopPx = statusBarTopPx,
                userName = settings.userName,
                wallpaper = wallpaper,
                customWallpaperUri = settings.customWallpaperUri,
                dark = dark,
                // Not OR-ed with tiledWallpaper: that mode only changes how Start's
                // *tiles* window onto the wallpaper (a per-tile effect with no
                // equivalent here) — the feed has no tiles, so it always shows the
                // plain full-screen wallpaper regardless of Start's tiled setting.
                noWallpaper = noWallpaper,
                feedNoBackground = settings.feedNoBackground,
                feeds = feedSources.map { FeedSourceItem(it.url, it.name, it.category, it.enabled) },
                onToggleFeed = viewModel::setFeedSourceEnabled,
                onToggleCategory = viewModel::setFeedCategoryEnabled,
                onRemoveFeed = viewModel::removeFeedSource,
                onAddFeed = viewModel::addFeedSource,
                feedRegions = feedRegions,
                onFeedRegionToggle = viewModel::setFeedRegionEnabled,
                onOpenQuickSearch = viewModel::openSearch,
                onWeatherDetails = { query -> launchWebSearch(context, query) },
                onAddSchedule = { launchAddEvent(context) },
                onOpenArticle = { link -> launchUrl(context, link) },
                onRefresh = {
                    viewModel.refreshFeeds()
                    Toast.makeText(context, "refreshing news", Toast.LENGTH_SHORT).show()
                },
                active = active,
                accentId = settings.accentId,
                borderlessTiles = settings.borderlessTiles,
                transparency = settings.transparency,
                pinWidgetRequest = pendingFeedWidget,
                onPinWidgetRequestConsumed = { pendingFeedWidget = null },
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                // Landscape with the feed on: two side-by-side panels. The feed is
                // the left panel (always live); Start is the right panel at half
                // width, so its grid stays portrait-sized. The app list slides in
                // over the Start panel only — the feed panel stays put.
                isLandscape && feedEnabled -> {
                    val panelWidthPx = widthPx / 2f
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
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
                            // Start's own block pages each position/slide themselves
                            // (see StartPage's per-block rendering) — this box just
                            // hosts them and clips the ones translated off-screen.
                            Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                                renderStartPage(panelWidthPx)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationX = panelWidthPx * (upper - progress.value) }
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
                                .clipToBounds(),
                        ) { renderStartPage(cappedWidthPx) }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationX = widthPx * (upper - progress.value) }
                                .background(LocalColorTokens.current.bg),
                        ) { renderAppList() }
                    }
                }
                // Portrait: the stacked, swipeable pager (feed −1, Start's own
                // block pages 0..blockCount-1, app list blockCount).
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(pagerModifier(widthPx, if (feedEnabled) -1f else 0f)),
                    ) {
                        // Start's own block pages each position/slide themselves
                        // (see StartPage's per-block rendering).
                        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                            renderStartPage(widthPx)
                        }

                        // App-list page: slides in from the right.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationX = widthPx * (upper - progress.value) }
                                .background(LocalColorTokens.current.bg),
                        ) { renderAppList() }

                        // Feed page (left): drawn on top with its own background so
                        // Start never shows through it. Only composed when enabled
                        // (FR-7); off-screen otherwise. clipToBounds() keeps the
                        // feed's own blurred wallpaper (scaled 1.12x by the blur
                        // effect) from bleeding past this panel's edge into Start —
                        // graphicsLayer doesn't clip by default.
                        if (feedEnabled) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationX = widthPx * (-1f - progress.value) }
                                    .clipToBounds()
                                    .background(LocalColorTokens.current.bg),
                            ) { renderFeed(feedShown) }
                        }
                    }
                }
            }
        }

        // Small dot row at the bottom of the screen showing how many pages Start
        // has and which one is active — page names are hidden outside edit
        // mode (user-requested "just do it by scroll"), so this is the only
        // always-visible cue that there's more than one page to swipe to.
        // Hidden with a single page (nothing to indicate), while editing
        // (every page's own header already shows its name there), and while
        // the feed/app-list is what's actually showing. User-requested move
        // from the top to the bottom; rises to clear the edge strip exactly
        // like the app-list/quick-panel icon column does, via the same
        // already-computed edgeStripVisible.
        if (blockCount > 1 && !editMode && !appListShown && !feedShown) {
            val dotsBottomOffset by animateDpAsState(
                targetValue = if (edgeStripVisible) STRIP_THICK + 8.dp else 14.dp,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
                label = "pageDotsBottomOffset",
            )
            PageDotsIndicator(
                count = blockCount,
                activeIndex = activeBlockIndex,
                tint = Glass.faceTextColor(screenBackgroundIsLight),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = dotsBottomOffset),
            )
        }

        // Edge-strip overlay: shown only when enabled and no overlay is on top. Quick
        // search stays out of the mount condition — the strip stays composed and just
        // slides fully away (suppressed) so its expanded/collapsed state isn't lost.
        if (settings.edgeStripEnabled && settings.edgeStripApps.isNotEmpty() &&
            !editMode && expandedFolderId == null && !personalizeOpen
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
        val showUpdateBanner = !editMode && !isAppList && expandedFolderId == null &&
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
            borderlessTiles = settings.borderlessTiles,
            onBorderlessTilesChange = viewModel::setBorderlessTiles,
            tileOutline = settings.tileOutline,
            onTileOutlineChange = viewModel::setTileOutline,
            feedEnabled = settings.feedEnabled,
            onFeedEnabledChange = viewModel::setFeedEnabled,
            feedNoBackground = settings.feedNoBackground,
            onFeedNoBackgroundChange = viewModel::setFeedNoBackground,
            userName = settings.userName,
            onUserNameChange = viewModel::setUserName,
            liveTilesEnabled = settings.liveTilesEnabled,
            onLiveTilesEnabledChange = viewModel::setLiveTilesEnabled,
            stockRefreshRate = settings.stockRefreshRate,
            onStockRefreshRateChange = viewModel::setStockRefreshRate,
            commodityRefreshRate = settings.commodityRefreshRate,
            onCommodityRefreshRateChange = viewModel::setCommodityRefreshRate,
            sportsRefreshRate = settings.sportsRefreshRate,
            onSportsRefreshRateChange = viewModel::setSportsRefreshRate,
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
            onWallpaperChange = { id -> pendingWallpaperPick = PendingWallpaperPick.Gradient(id) },
            onSelectStockWallpaperType = { viewModel.setWallpaper(Wallpapers.all.first().id) },
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
            isDefaultLauncher = isDefaultLauncher,
            onSetDefaultLauncher = onSetDefaultLauncher,
            cornerRadius = settings.cornerRadius,
            onCornerRadiusChange = viewModel::setCornerRadius,
            tileGap = settings.tileGap,
            onTileGapChange = viewModel::setTileGap,
            tileColorSource = settings.tileColorSource,
            onTileColorSourceChange = viewModel::setTileColorSource,
            wallpaperAccentPreview = wallpaperAccentColor,
            tileFill = settings.tileFill,
            onTileFillChange = viewModel::setTileFill,
            fontStyle = settings.fontStyle,
            onFontStyleChange = viewModel::setFontStyle,
            columns = settings.columns,
            onColumnsChange = viewModel::setColumns,
            tilePackMode = settings.tilePackMode,
            onTilePackModeChange = viewModel::setTilePackMode,
            homeStyle = settings.homeStyle,
            onHomeStyleChange = viewModel::setHomeStyle,
            iconShape = settings.iconShape,
            onIconShapeChange = viewModel::setIconShape,
            lockLayout = settings.lockLayout,
            onLockLayoutChange = viewModel::setLockLayout,
            hideStatusBar = settings.hideStatusBar,
            onHideStatusBarChange = viewModel::setHideStatusBar,
            onAbout = viewModel::openAbout,
            onPersonalizeGuide = viewModel::openPersonalizeGuide,
            onFolders = viewModel::openFolders,
            onHiddenApps = viewModel::openHiddenApps,
            edgeStripEnabled = settings.edgeStripEnabled,
            onEdgeStrip = viewModel::openEdgeStrip,
            onBackupRestore = viewModel::openBackup,
            onPermissions = viewModel::openPermissions,
            onNewsRegion = viewModel::openNewsRegion,
            newsRegionCount = 1 + SELECTABLE_COUNTRIES.size,
            onDismiss = viewModel::closePersonalize,
        )

        // About sheet (personalize → about).
        AboutSheet(
            visible = aboutOpen,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            onDismiss = viewModel::closeAbout,
            onVersionTap = viewModel::reopenWhatsNew,
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

        // Quick panel (two-finger swipe-up on Start, or its tap affordance):
        // Wi-Fi/Bluetooth/flashlight/DND/airplane/location/rotation-lock tiles,
        // brightness/volume/screen-timeout, theme, and settings/android-settings/lock-screen.
        QuickPanelOverlay(
            visible = quickPanelOpen,
            dark = dark,
            accentId = settings.accentId,
            followSystemTheme = settings.followSystemTheme,
            onDismiss = viewModel::closeQuickPanel,
            onOpenPersonalize = viewModel::openPersonalize,
            onLockScreen = onLockScreen,
            onThemeChange = viewModel::setTheme,
            onFollowSystemThemeChange = viewModel::setFollowSystemTheme,
            wallpaper = wallpaper,
            customWallpaperPhoto = customWallpaperBitmap,
            noWallpaper = noWallpaper,
            feedNoBackground = settings.feedNoBackground,
            tileOrder = settings.quickPanelTileOrder,
            tileSizes = settings.quickPanelTileSizes,
            onTileOrderChange = viewModel::setQuickPanelTileOrder,
            onTileSizesChange = viewModel::setQuickPanelTileSizes,
            borderlessTiles = settings.borderlessTiles,
            transparency = settings.transparency,
            rightHalf = isLandscape,
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

        // Category-folders sheet (personalize → folders & categories).
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

        // Add-widgets sheet (edit bar → add widgets).
        WidgetListSheet(
            visible = addWidgetsOpen,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            notesAlreadyPinned = tiles.hasNotesTile(),
            onAddWidget = { appId ->
                if (appId == "weather") {
                    // Weather needs one more answer before there's a tile to add
                    // at all (user-requested: "ask for current location or
                    // select location") — the location sheet below creates the
                    // tile itself once the user answers, so this closes the
                    // catalog and waits rather than calling addLiveTile now.
                    viewModel.closeAddWidgets()
                    viewModel.openWeatherLocationForNewTile(activeSectionId)
                } else {
                    viewModel.addLiveTile(appId, activeSectionId)
                    // Land back on a normal, settled Start screen showing the new
                    // tile in place, instead of leaving edit mode's jiggle/edit-bar
                    // up — matches the existing "add" (app list) entry point,
                    // which already exits edit mode the moment it's used.
                    viewModel.exitEdit()
                    Toast.makeText(context, "added $appId tile", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = viewModel::closeAddWidgets,
        )

        // Weather tile's own location choice — new tile (from the sheet above)
        // or reconfiguring an already-pinned one (tapping it, see the "weather"
        // tap branch below) — see StartViewModel.weatherLocationTarget.
        WeatherLocationSheet(
            visible = weatherLocationTarget != null,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            onUseCurrentLocation = {
                viewModel.setWeatherLocationCurrent()
                viewModel.exitEdit()
                Toast.makeText(context, "weather set to current location", Toast.LENGTH_SHORT).show()
            },
            onPickPlace = { lat, lon, name ->
                viewModel.setWeatherLocationPlace(lat, lon, name)
                viewModel.exitEdit()
                Toast.makeText(context, "weather set to $name", Toast.LENGTH_SHORT).show()
            },
            onDismiss = viewModel::closeWeatherLocationSheet,
        )

        // Tasks sheet (tapping a Tasks tile) — scoped to that tile's own list.
        TaskListSheet(
            visible = tasksOpen != null,
            listId = tasksOpen.orEmpty(),
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            autoClearDaily = settings.taskAutoClearDaily,
            onAutoClearDailyChange = viewModel::setTaskAutoClearDaily,
            onDismiss = viewModel::closeTasks,
        )

        // Notes sheet (tapping the Notes tile).
        NotesSheet(
            visible = notesOpen,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            onDismiss = viewModel::closeNotes,
        )

        // Sticky Note tile's own editor (tapping that tile).
        StickyNoteEditorSheet(
            visible = stickyNoteEditTileId != null,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            tileId = stickyNoteEditTileId,
            initialText = (tiles.firstOrNull { it.id == stickyNoteEditTileId } as? TileModel.App)
                ?.activityName.orEmpty(),
            onTextChange = viewModel::setStickyNoteText,
            onDismiss = viewModel::closeStickyNoteEditor,
        )

        // Countdown tile's own editor (tapping that tile).
        CountdownEditorSheet(
            visible = countdownEditTileId != null,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            tileId = countdownEditTileId,
            initialTargetIsoDate = (tiles.firstOrNull { it.id == countdownEditTileId } as? TileModel.App)
                ?.activityName?.let { CountdownTile.decode(it)?.first }.orEmpty(),
            initialLabel = (tiles.firstOrNull { it.id == countdownEditTileId } as? TileModel.App)
                ?.activityName?.let { CountdownTile.decode(it)?.second }.orEmpty(),
            onDataChange = viewModel::setCountdownData,
            onDismiss = viewModel::closeCountdownEditor,
        )

        // Sports tile's own team picker (tapping that tile).
        SportsPickerSheet(
            visible = sportsEditTileId != null,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            tileId = sportsEditTileId,
            onTeamPicked = viewModel::setSportsTeam,
            onDismiss = viewModel::closeSportsEditor,
        )

        // Stock tile's own picker (tapping that tile).
        StockPickerSheet(
            visible = stockEditTileId != null,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            tileId = stockEditTileId,
            onSinglePicked = viewModel::setStockSingle,
            onCategoryPicked = viewModel::setStockCategory,
            onMultiPicked = viewModel::setStockMulti,
            onDismiss = viewModel::closeStockEditor,
        )

        // Commodity/currency tile's own picker (tapping that tile).
        CommodityPickerSheet(
            visible = commodityEditTileId != null,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            tileId = commodityEditTileId,
            onPicked = viewModel::setCommodity,
            onDismiss = viewModel::closeCommodityEditor,
        )

        // "Calendar systems" tile's own picker (tapping that tile).
        CalendarSystemPickerSheet(
            visible = calendarSystemEditTileId != null,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            onPick = { systemId -> calendarSystemEditTileId?.let { viewModel.setCalendarSystem(it, systemId) } },
            onDismiss = viewModel::closeCalendarSystemEditor,
        )

        // Permissions sheet (personalize → permissions).
        PermissionsSheet(
            visible = permissionsOpen,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            onDismiss = viewModel::closePermissions,
            contactsGranted = contactsGranted,
            calendarGranted = calendarGranted,
            locationGranted = locationGranted,
            activityGranted = activityGranted,
            // asked = true for these three: MainActivity requests them
            // upfront on every fresh process, so by the time Personalize is
            // reachable they have certainly been asked at least once.
            onRequestContacts = {
                requestPermissionOrOpenSettings(android.Manifest.permission.READ_CONTACTS, asked = true) {
                    contactsLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                }
            },
            onRequestCalendar = {
                requestPermissionOrOpenSettings(android.Manifest.permission.READ_CALENDAR, asked = true) {
                    calendarLauncher.launch(android.Manifest.permission.READ_CALENDAR)
                }
            },
            onRequestLocation = {
                requestPermissionOrOpenSettings(android.Manifest.permission.ACCESS_COARSE_LOCATION, asked = true) {
                    locationLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
            onRequestActivity = {
                requestPermissionOrOpenSettings(
                    android.Manifest.permission.ACTIVITY_RECOGNITION,
                    asked = StepsPrefs.permissionAsked(context),
                ) {
                    activityLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                }
            },
        )

        // News-region sheet (personalize → news region) — same subscribed-region
        // set the feed page's own gear-icon sheet already edits.
        NewsRegionSheet(
            visible = newsRegionOpen,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            regions = (listOf(INDIA_COUNTRY_CODE, INTERNATIONAL_REGION_CODE) + SELECTABLE_COUNTRIES.map { it.code })
                .map { code -> RegionOption(code, regionDisplayName(code), code in feedRegions) },
            onToggleRegion = viewModel::setFeedRegionEnabled,
            onDismiss = viewModel::closeNewsRegion,
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
        // seen. Suppressed while the home-style wizard is up (below), which
        // takes priority as the very first thing a new install sees.
        if (!homeStyleWizardOpen) {
            FirstRunHint(accentId = settings.accentId)
        }

        // First-run home-style (tiles vs icons) choice wizard — see
        // HomeStyleWizardPrefs's doc comment for exactly when this shows.
        // Drawn last so it fully covers everything else, including the hint
        // above.
        if (homeStyleWizardOpen) {
            HomeStyleWizardScreen(
                onChoose = viewModel::chooseHomeStyle,
                onSkip = viewModel::skipHomeStyleWizard,
            )
        }

        // "What's new" card — one-time, shown only to a device that has
        // already run TileShell before (never alongside the wizard above,
        // which owns a genuinely fresh install's first impression instead;
        // whatsNewOpen and homeStyleWizardOpen are mutually exclusive by
        // construction in StartViewModel.init). Suppressed while any other
        // overlay chrome is up, same condition set showUpdateBanner already
        // uses, so it never appears mid-edit, over the app list, or stacked
        // behind/in front of another sheet.
        if (whatsNewOpen && !editMode && !isAppList && expandedFolderId == null &&
            !personalizeOpen && !searchOpen
        ) {
            WhatsNewSheet(
                visible = true,
                accentId = settings.accentId,
                onDismiss = viewModel::dismissWhatsNew,
            )
        }

        // Wallpaper crop overlay: shown immediately after the user picks a photo so
        // they can drag to position the image before it becomes the live wallpaper.
        // Reads pendingWallpaperCropUri directly (not a captured local) inside
        // onConfirm, so it still resolves to the right uri even though the host
        // below keeps this composed for a moment after the state goes null (its
        // fade-out) — read at call time, not the moment now null.
        WallpaperCropOverlayHost(
            uri = pendingWallpaperCropUri,
            tiles = mainPageTiles,
            settings = settings,
            accent = accent,
            wallpaperGradient = wallpaper,
            notifications = notifications,
            darkTheme = dark,
            glassLine = tokens.glassLine,
            onConfirm = { alignX, alignY, zoom ->
                pendingWallpaperCropUri?.let {
                    pendingWallpaperPick = PendingWallpaperPick.Photo(it, alignX, alignY, zoom)
                }
                pendingWallpaperCropUri = null
                // Reached via personalize → wallpaper → photo (or the share/
                // "apply via" entry points, where this is a harmless no-op since
                // personalize was never open) — the target chooser lands on
                // Start instead of leaving the personalize sheet showing
                // underneath, same as before.
                viewModel.closePersonalize()
            },
            onCancel = { pendingWallpaperCropUri = null },
            rightHalf = isLandscape,
        )

        // Re-frame the active wallpaper (own photo or Bing image): same drag UI, but
        // only the alignment is written — the image/daily-mode are left untouched.
        WallpaperCropOverlayHost(
            uri = if (adjustingWallpaper) settings.customWallpaperUri else null,
            tiles = mainPageTiles,
            settings = settings,
            accent = accent,
            wallpaperGradient = wallpaper,
            notifications = notifications,
            darkTheme = dark,
            glassLine = tokens.glassLine,
            initialAlignX = settings.wallpaperAlignX,
            initialAlignY = settings.wallpaperAlignY,
            initialZoom = settings.wallpaperZoom,
            onConfirm = { alignX, alignY, zoom ->
                viewModel.setWallpaperAlignment(alignX, alignY, zoom)
                adjustingWallpaper = false
                // Same as the initial set-wallpaper flow above — reframing is only
                // ever reached from personalize, so applying it should land back
                // on Start rather than leaving personalize open underneath.
                viewModel.closePersonalize()
            },
            onCancel = { adjustingWallpaper = false },
            rightHalf = isLandscape,
        )

        // Recent-Bing-wallpapers viewer (personalize → "recent bing wallpapers").
        BingHistorySheet(
            visible = bingHistoryOpen,
            rightHalf = isLandscape,
            dark = dark,
            accentId = settings.accentId,
            onPick = { imageUrl ->
                pendingWallpaperPick = PendingWallpaperPick.Bing(imageUrl)
                bingHistoryOpen = false
            },
            onDismiss = { bingHistoryOpen = false },
        )

        // Where to apply a freshly picked wallpaper — bare options, no
        // explanatory copy, shown *before* anything is set (mirrors the
        // OEM/AOSP wallpaper-set flow's own bottom prompt exactly: pick a
        // target or cancel, nothing applied either way until you do).
        // Dismissing discards [pendingWallpaperPick] outright — it's never
        // set as TileShell's own wallpaper, let alone pushed to the OS.
        pendingWallpaperPick?.let { pick ->
            fun applyTo(target: WallpaperSyncTarget) {
                when (pick) {
                    is PendingWallpaperPick.Gradient -> viewModel.setWallpaperWithSync(pick.id, target)
                    is PendingWallpaperPick.Photo ->
                        viewModel.setCustomWallpaperWithSync(pick.uri, pick.alignX, pick.alignY, pick.zoom, target)
                    is PendingWallpaperPick.Bing -> viewModel.applyBingImageWithSync(pick.imageUrl, target)
                }
                pendingWallpaperPick = null
            }
            // A plain custom Dialog, not Material3's AlertDialog — full
            // control over sizing instead of fighting its title/button-row
            // default chrome. Two separate cards (iOS/Android's classic
            // action-sheet shape): the 3 targets grouped together, "cancel"
            // on its own below with a visible gap — never grouped with the
            // real choices, per explicit request.
            Dialog(onDismissRequest = { pendingWallpaperPick = null }) {
                Column(modifier = Modifier.fillMaxWidth(0.84f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(tokens.sheet),
                    ) {
                        listOf(
                            "home screen" to WallpaperSyncTarget.HOME,
                            "lock screen" to WallpaperSyncTarget.LOCK,
                            "home + lock screen" to WallpaperSyncTarget.HOME_AND_LOCK,
                        ).forEachIndexed { index, (label, target) ->
                            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.tileLine))
                            Text(
                                text = label,
                                color = tokens.fg,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { applyTo(target) }
                                    .padding(vertical = 14.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "cancel",
                        color = tokens.fg,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(tokens.sheet)
                            .clickable { pendingWallpaperPick = null }
                            .padding(vertical = 14.dp),
                    )
                }
            }
        }
    }
    }
}

/**
 * Synthetic tile id for a folder child while its folder is inline-expanded
 * (FR-4 WP-style) — lets a [FolderChild] flow through the *same* grid/drag
 * machinery as a real top-level tile (packing, hit-testing, corner controls)
 * without a separate overlay grid. `:` is safe as a delimiter: every real tile
 * id this app generates (`pin-<pkg>-<millis>`, `folder-<millis>`, `live-...`,
 * seeded ids) is colon-free, and [rowId] is numeric, so splitting on the last
 * colon unambiguously recovers both parts.
 */
private const val FOLDER_CHILD_ID_PREFIX = "folderchild:"

/**
 * A wallpaper picked but not yet applied — waiting on the "home screen /
 * lock screen / home + lock screen" chooser (mirrors the real Android
 * wallpaper-setting flow's own bare 3-option + cancel prompt). Resolved
 * into the matching `StartViewModel.*WithSync` call once a target is
 * picked; discarded with no effect at all if the chooser is dismissed.
 */
private sealed interface PendingWallpaperPick {
    data class Gradient(val id: String) : PendingWallpaperPick
    data class Photo(val uri: String, val alignX: Float, val alignY: Float, val zoom: Float) : PendingWallpaperPick
    data class Bing(val imageUrl: String) : PendingWallpaperPick
}

/**
 * Fixed geometry for a "borderless" tile — deliberately not derived from
 * Personalize's own corner-radius/tile-spacing sliders (an earlier pass had
 * these as a floor over the sliders via `maxOf`; dropped in favour of a fixed
 * value once it was clear the sliders then did nothing below that floor,
 * which is confusing to have a visible, adjustable control for — Personalize
 * hides both rows entirely while this style is active). The point of this
 * style is to look like a real Android home-screen widget card: generously
 * rounded corners and a wide gap between tiles, neither of which the other
 * three styles' own defaults (square corners, a 3dp gap) provide.
 */
private const val BORDERLESS_CORNER_RADIUS_DP = 20f
private const val BORDERLESS_TILE_GAP_DP = 12f

/** Fraction of the available travel a reachability pull settles at once held. */
private const val REACHABILITY_HELD_FRACTION = 0.7f

// Drag-a-tile-to-a-neighboring-page (see editDragGesture's onCrossPageDrop):
// how close to the left/right screen edge the dragged tile's own centre must
// sit before the page shifts and the move commits, immediately, right there
// — no dwell/hold requirement (a deliberate simplification per direct user
// request, see docs/DECISIONS.md). A fresh, explicit choice for this gesture
// specifically, not reused from any other zone width elsewhere in this file.
private const val CROSS_PAGE_DRAG_EDGE_ZONE_DP = 32f

private fun folderChildTileId(folderId: String, rowId: Long): String =
    "$FOLDER_CHILD_ID_PREFIX$folderId:$rowId"

/** Reverses [folderChildTileId], or null if [id] isn't a synthetic child id. */
private fun parseFolderChildId(id: String): Pair<String, Long>? {
    if (!id.startsWith(FOLDER_CHILD_ID_PREFIX)) return null
    val rest = id.removePrefix(FOLDER_CHILD_ID_PREFIX)
    val sep = rest.lastIndexOf(':')
    if (sep <= 0) return null
    val rowId = rest.substring(sep + 1).toLongOrNull() ?: return null
    return rest.substring(0, sep) to rowId
}

/**
 * Where a folder child dragged out past its folder's own expanded block
 * ([isInsideFolderBlock]) landed at release, resolved by [editDragGesture]'s
 * `onFolderChildPulledOut` callback into one of [StartViewModel]'s
 * `pullFolderChildTo*` write paths — contrast [onReorderFolderChildTo] /
 * [onFolderChildDrop], which stay intra-folder and never reach this type.
 */
private sealed interface FolderChildDropTarget {
    /** Dropped in [targetId]'s merge zone — commits the same drag-merge FR-3.3 uses. */
    data class Merge(val targetId: String) : FolderChildDropTarget

    /** Dropped on a sticky/free-mode grid cell (see [GridPacker.encodeSlot]). */
    data class Cell(val slot: Int) : FolderChildDropTarget

    /** Dropped in dense/free mode: lands right before [beforeId] (null = append at the end). */
    data class Position(val beforeId: String?) : FolderChildDropTarget
}

/**
 * A [FolderChild] as a stand-in [TileModel.App] so it renders through the
 * exact same [TileView]/[AppTileContent] path as any pinned app (icon, label,
 * badges, corner controls) — this is a *rendering* convenience only; taps and
 * edit actions on a synthetic id are detected via [parseFolderChildId] and
 * routed to the real folder-child ViewModel calls, never through this fake
 * model's own (mostly-unused) `position`/`colorId`.
 */
private fun FolderChild.asTileModel(id: String): TileModel.App = TileModel.App(
    id = id,
    position = 0,
    size = size,
    colorId = "blue",
    packageName = packageName,
    activityName = activityName,
    label = label,
    iconKey = iconKey,
    accentOverride = accentOverride,
    // The "show as icon"/"show as tile" toggle is single-app-tile only (no
    // persisted FolderChild field for it) — a folder child keeps its
    // original SMALL-only-icon / MEDIUM+-live-tile behaviour regardless of
    // TileModel.App's own icon-favouring default.
    displayAsIcon = false,
)

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
    liveTilesEnabled: Boolean,
    selectedTileId: String?,
    accent: Color,
    accentId: String,
    appIconColors: Boolean,
    // Personalize's "live data refresh" — threaded down to TileView for the
    // stock/commodity/sports live faces.
    stockRefreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    commodityRefreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    sportsRefreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    // Resolved wallpaper-accent colour when TileColorSource.WALLPAPER_ACCENT is
    // active (null otherwise) — same value the feed/glance page and Quick Panel
    // derive via rememberFeedPalette, computed once at the top and threaded down
    // rather than recomputed per tile.
    wallpaperAccent: Color?,
    tileGapPx: Float?,
    glass: Boolean,
    transparency: Float,
    glassLine: Color,
    tiledWallpaper: Boolean,
    // "borderless" tile style: paint neither a fill nor an outline, so only the
    // tile's own content shows over the wallpaper. Mutually exclusive with
    // [glass]/[tiledWallpaper] at the data layer, so it only ever wins the fill
    // `when` below when both of those are already false.
    borderless: Boolean = false,
    // Whether a glass / "behind tiles" tile draws its 1dp hairline. A solid or
    // borderless tile has none to begin with, so this is inert for those two.
    tileOutline: Boolean = true,
    wallpaper: com.tileshell.core.design.WallpaperGradient,
    wallpaperPhoto: ImageBitmap?,
    screenBackgroundIsLight: Boolean,
    wallpaperAlignX: Float,
    wallpaperAlignY: Float,
    wallpaperZoom: Float,
    darkTheme: Boolean,
    notifications: NotificationSnapshot,
    widthPx: Float,
    viewportHeightPx: Float,
    statusBarTopPx: Float,
    /**
     * Mirrors the "hide status bar" Personalize toggle: skips [statusBarsPadding]
     * *and* [displayCutoutPadding] on the tile grid so it fills all the way to
     * the top of the screen instead of leaving either inset's height as blank
     * space. Reserving the status-bar inset via the system's [WindowInsets]
     * doesn't reliably shrink to zero once the bar is hidden on every device/API
     * level, and a device with a punch-hole/notch cutout reserves its own
     * separate top inset regardless of the status bar — both are skipped
     * unconditionally from the setting rather than left to the system to figure
     * out, confirmed against a real device with a punch-hole camera cutout.
     */
    hideStatusBar: Boolean = false,
    columns: Int,
    sticky: Boolean,
    // TilePackMode.FREE specifically (a subset of [sticky], which is also true
    // for STICKY) — [editDragGesture]'s live drag preview needs to tell the two
    // apart, since only STICKY pushes other tiles out of the way while
    // dragging; FREE never previews any other tile moving.
    freeMode: Boolean = false,
    // Which cell renderer a SMALL (1×1) tile uses — the icons-mode arc. 2×2+
    // always renders via TileView regardless, so this only ever changes
    // behaviour for SMALL app tiles (including inline-expanded folder
    // children, which flow through the exact same call site).
    homeStyle: HomeStyle = HomeStyle.TILES,
    // The icon mask ICONS home style applies (unused in TILES).
    iconShape: IconShape = IconShape.ORIGINAL,
    // Themed/monochrome app icons (Personalize) — threaded to TileView for the
    // live-tile "posted by" corner badge (app list has its own separate path).
    themedIcons: Boolean = false,
    onSetTileSlot: (id: String, slot: Int?) -> Unit,
    // FR-4 WP-style inline folder expand/collapse: the currently-expanded
    // folder's id (null = none), and the child-scoped actions that used to
    // live inside the separate FolderOverlay.
    expandedFolderId: String?,
    onCollapseFolder: () -> Unit,
    onTile: (TileModel) -> Unit,
    onLaunchFolderChild: (FolderChild) -> Unit,
    onPullOutFolderChild: (folderId: String, child: FolderChild) -> Unit,
    // Dragging a folder child out past its own folder's expanded block, onto
    // the top-level grid — the drag counterpart of [onPullOutFolderChild]
    // (which is tap-× only and always appends to the bottom). Exactly one of
    // these three fires per such drop, depending on where it landed; see
    // [FolderChildDropTarget].
    onPullOutFolderChildToMerge: (folderId: String, child: FolderChild, targetId: String) -> Unit,
    onPullOutFolderChildToSlot: (folderId: String, child: FolderChild, slot: Int) -> Unit,
    onPullOutFolderChildToPosition: (folderId: String, child: FolderChild, beforeId: String?) -> Unit,
    onResizeFolderChild: (folderId: String, child: FolderChild) -> Unit,
    onSetFolderChildColor: (folderId: String, rowId: Long, colorId: String?) -> Unit,
    onRenameFolder: (folderId: String, name: String) -> Unit,
    onToggleFolderStack: (folderId: String) -> Unit,
    // "unfold folder" (dissolve, keeping every child as its own pinned tile)
    // and "remove folder & tiles" (unpin the folder and every child at
    // once) — both from the folder's own colour-picker sheet.
    onUnfoldFolder: (folderId: String) -> Unit = {},
    onRemoveFolderAndTiles: (folderId: String) -> Unit = {},
    onReorderFolderChildren: (List<FolderChild>) -> Unit,
    onChevron: () -> Unit,
    // Empty-space long-press passes null (enter edit with nothing selected).
    onEnterEdit: (String?) -> Unit,
    onSelectTile: (String) -> Unit,
    onExitEdit: () -> Unit,
    onReorder: (List<String>) -> Unit,
    onMerge: (dragId: String, targetId: String, survivingOrder: List<String>) -> Unit,
    onResize: (String) -> Unit,
    // Gesture-based drag resize (Stage 2 of the icons-mode arc): lands directly
    // on the size the drag settled on, unlike [onResize]'s fixed tap cycle.
    // Top-level tiles only — a folder child's drag routes to
    // [onResizeFolderChildTo] instead (same gesture, different write path).
    onResizeTo: (id: String, size: TileSize) -> Unit,
    // A folder child's counterpart to [onResizeTo] — reaches the same
    // drag-only presets (e.g. BANNER/COLUMN) a folder child's tap cycle
    // ([onResizeFolderChild]) never visits.
    onResizeFolderChildTo: (folderId: String, child: FolderChild, size: TileSize) -> Unit,
    // A widget stack's own drag-resize counterpart to [onResizeTo]: homogenizes
    // every member to the new size along with the tile (see convertFolderToStack).
    onResizeStack: (folderId: String, size: TileSize) -> Unit,
    onUnpin: (String) -> Unit,
    onSetTileColor: (id: String, colorId: String?) -> Unit,
    onSetTileDisplayAsIcon: (id: String, displayAsIcon: Boolean) -> Unit = { _, _ -> },
    // Start-screen "sections": named, collapsible groups of top-level tiles.
    // A tile moves between sections only via [onAssignTileSection] (the
    // "move to section" corner-control picker) — drag-to-reorder always
    // stays within one block (real section, or the trailing unsectioned
    // group), never across one, by construction (see the per-block render
    // loop below).
    sections: List<Section> = emptyList(),
    onCreateSection: (String) -> Unit = {},
    onRenameSection: (id: String, label: String) -> Unit = { _, _ -> },
    onDeleteSection: (String) -> Unit = {},
    // "remove page & tiles" — unpins the page's section AND every tile
    // currently on it, at once (apps stay installed) — the bulk counterpart
    // to [onDeleteSection] ("merge with main"), which only ungroups.
    onRemovePageAndTiles: (String) -> Unit = {},
    onMoveSection: (id: String, direction: Int) -> Unit = { _, _ -> },
    onAssignTileSection: (tileId: String, sectionId: String?) -> Unit = { _, _ -> },
    // The pager's own live position (see StartScreen's `progress`) and the
    // already-clamped block index it currently resolves to — the single
    // source of truth for "which page is active," computed once by the
    // caller (StartScreen) so this composable never needs its own separate
    // active-block state to keep in sync.
    pagerProgress: Float = 0f,
    // A *live*-reading variant of [pagerProgress] for the cross-page drag
    // ghost below — that runs inside a suspend gesture loop (not
    // recomposition), so it needs to re-read the animating value fresh
    // every tick rather than see whatever [pagerProgress] snapshot this
    // composable happened to be recomposed with. Defaults to that snapshot
    // for any caller that doesn't provide a live one.
    livePagerProgress: () -> Float = { pagerProgress },
    activeBlockIndex: Int = 0,
    // Drag a top-level tile to the screen edge to carry it onto the
    // neighboring page — [direction] is -1 (earlier/left) or 1 (later/
    // right). [onCrossPageShift] fires once, immediately on crossing, to
    // shift the pager there and then; [onCrossPageDrop] fires once, at
    // release, with [targetSlot] (where on that page to land it, already
    // computed from the drop position). Both resolved to real
    // navigation/page/section/slot writes by the caller (StartScreen),
    // which is the one that knows blockCount/sortedSections/settleTo.
    onCrossPageShift: (direction: Int) -> Unit = {},
    onCrossPageDrop: (tileId: String, direction: Int, targetSlot: Int) -> Unit = { _, _, _ -> },
    onAdd: () -> Unit,
    onPersonalize: () -> Unit,
    onAddWidgets: () -> Unit = {},
    onQuickPanel: () -> Unit = {},
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
    // A cross-page drag's own floating visual (see CrossPageDragGhost below):
    // the dragged tile's id and its live offset relative to this composable's
    // own outer Box — set once the drag crosses a screen edge (its source
    // page's own in-grid rendering is invisible from then on, since that
    // page has slid off-screen), cleared the moment it's released.
    var crossPageDragId by remember { mutableStateOf<String?>(null) }
    var crossPageDragOffset by remember { mutableStateOf(Offset.Zero) }

    // Gesture-based drag resize (Stage 2 of the icons-mode arc). resizingId is
    // the tile currently under a resize handle; resizePreviewSize is the
    // TileSize the drag has snapped to *so far* — re-derived on every drag
    // tick from the accumulated delta since the drag started (never
    // incrementally), so it can't drift. The write (StartViewModel.resizeTo)
    // only happens once, on release — see onResizeDragEnd at the TileView call
    // site below. resizeGeom mirrors the same GridGeometry.of(...) call
    // DenseTileGrid makes internally from these same widthPx/columns/tileGapPx
    // inputs, so the live preview's pixel size always matches the real grid's.
    var resizingId by remember { mutableStateOf<String?>(null) }
    var resizePreviewSize by remember { mutableStateOf<TileSize?>(null) }
    var resizeAccumDx by remember { mutableStateOf(0f) }
    var resizeAccumDy by remember { mutableStateOf(0f) }
    val resizeGeom = remember(widthPx, columns, tileGapPx) { GridGeometry.of(widthPx, columns, tileGapPx) }

    // Tile currently highlighted as a merge target (finger in its centre zone).
    var mergeTargetId by remember { mutableStateOf<String?>(null) }
    // Tile whose accent-colour picker is open (edit-mode colour dot tapped), or null.
    var colorPickerFor by remember { mutableStateOf<String?>(null) }
    // Folder id pending confirmation for "remove folder & tiles" (a bulk,
    // multi-tile unpin — worth a confirm, unlike "unfold folder" which loses
    // nothing) — set once the picker's own button is tapped, shown after the
    // picker sheet itself has closed.
    var confirmRemoveFolderId by remember { mutableStateOf<String?>(null) }
    // Whether the fixed top-right "remove [page]" menu (see below) is open,
    // and the section id pending confirmation for "remove page & tiles" —
    // same two-state shape as the folder ones above, for the same reason.
    var pageRemoveMenuExpanded by remember { mutableStateOf(false) }
    var confirmRemovePageId by remember { mutableStateOf<String?>(null) }
    // Sticky-mode drag preview: id -> live push-down cell, recomputed on every
    // pointer move so the tiles a drop would displace visibly slide out of the
    // way *during* the drag (dense mode already got this for free via `order`
    // mutation; sticky mode's real placement only ever lived in the DB, so
    // nothing reflowed until release). Cleared once the underlying tile data
    // changes — i.e. right after a drop's DB write lands — since by then the
    // real gridSlot values already match what the preview showed, so clearing
    // it causes no visible jump.
    var stickyPreview by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(byId) { stickyPreview = emptyMap() }
    // Reconcile the working order with the persisted layout: keep the existing
    // relative order of surviving ids, drop removed ones, insert new ones at
    // their own position from the fresh (DB-ordered) list — see
    // GridGeometry.mergeOrder. This preserves a just-dropped reorder (the async
    // DB write lands the same order, so no flicker) while still absorbing
    // pins/uninstalls, and — unlike a blind append-at-the-end — respects a new
    // tile's own written position (e.g. a folder child dragged out to a chosen
    // spot) instead of always landing it at the bottom.
    LaunchedEffect(specs) {
        if (draggingId != null) return@LaunchedEffect
        val merged = mergeOrder(order.toList(), specs.map { it.id })
        if (merged != order.toList()) {
            order.clear()
            order.addAll(merged)
        }
    }
    // FR-4 WP-style inline folder expand/collapse: the expanded folder's
    // children are spliced into the *same* grid via synthetic ids
    // (folderChildTileId) so they flow through the exact packing/drag/hit-test
    // machinery as any top-level tile, instead of a separate overlay grid.
    // [augmentedById] adds lookup entries for those synthetic ids (rendering +
    // editDragGesture's byId reads); [expandTransform] is applied after the
    // normal pack/packSticky computation to actually shift the rows (see
    // GridPacker.expandFolderInline) — a pure render-time transform, nothing
    // here is persisted, so collapsing just stops applying it.
    // Memoized (not recomputed every recomposition): this feeds packSticky /
    // expandFolderInline below, both of which run synchronously during
    // composition. Live tiles recompose StartPage periodically (flip
    // scheduler, media/notification polling) even when nothing about the
    // layout changed — without `remember`, every one of those ticks would
    // redo the (non-trivial) grid-packing work while a folder is expanded,
    // which was cheap enough to go unnoticed while collapsed but, expanded,
    // was slow enough to occasionally starve the touch-handling coroutine
    // mid-tap and make a plain tap read as a 600 ms long-press (entering edit
    // mode instead of launching).
    val expandedFolder = remember(byId, expandedFolderId) {
        expandedFolderId?.let { byId[it] as? TileModel.Folder }
    }
    // Additive over [byId] — every genuine top-level id resolves identically
    // through either map (only synthetic child ids gain a new entry), so
    // switching a `byId[id]` lookup to `augmentedById[id]` never changes the
    // result for a real top-level tile; it only *adds* the ability to resolve
    // a synthetic child id that would otherwise miss.
    val augmentedById: Map<String, TileModel> = remember(byId, expandedFolder) {
        if (expandedFolder == null) {
            byId
        } else {
            byId + expandedFolder.children.associate { child ->
                val childId = folderChildTileId(expandedFolder.id, child.rowId)
                childId to child.asTileModel(childId)
            }
        }
    }
    // Resolved via [augmentedById] (not the plain [byId]) so a folder child
    // temporarily spliced into [order] — while it's being dragged out onto the
    // dense-mode top-level grid, see the pulled-out-preview machinery below —
    // resolves to a real spec instead of vanishing from the pack entirely.
    // Behavior-preserving for every other case: [augmentedById] only adds
    // entries beyond [byId] (see its own comment above), and a synthetic
    // child id is never a member of the *persisted* `order`/`specs` outside
    // of that one live-preview splice.
    val displaySpecs = order.mapNotNull { id -> augmentedById[id]?.let { TileSpec(id, it.size) } }
    // Working order of the currently-expanded folder's children, as synthetic
    // child ids — mirrors the persisted order except during a drag on one of
    // them, when reorder mutates it live (same pattern, and the same
    // GridGeometry.reorderTiles helper, as the top-level `order` list). Reset
    // to the persisted order whenever the expanded folder or its children
    // change, unless a drag is in progress (matches `order`'s own
    // reconciliation effect above).
    val folderChildOrder = remember { mutableStateListOf<String>() }
    LaunchedEffect(expandedFolder?.id, expandedFolder?.children) {
        if (draggingId != null) return@LaunchedEffect
        val ids = expandedFolder?.let { folder ->
            folder.children.map { folderChildTileId(folder.id, it.rowId) }
        } ?: emptyList()
        if (ids != folderChildOrder.toList()) {
            folderChildOrder.clear()
            folderChildOrder.addAll(ids)
        }
    }
    val expandTransform: ((List<TilePlacement>) -> List<TilePlacement>)? =
        remember(expandedFolder, columns) {
            expandedFolder?.let { folder ->
                { placements: List<TilePlacement> ->
                    val childById = folder.children.associateBy { folderChildTileId(folder.id, it.rowId) }
                    // Read live so a mid-drag reorder shows up on every call —
                    // folderChildOrder's identity never changes, only its
                    // contents, so this always reflects the current order
                    // even though this outer closure isn't recreated for it.
                    val orderedChildren = folderChildOrder.mapNotNull { childById[it] }
                    GridPacker.expandFolderInline(
                        placements = placements,
                        expandedId = folder.id,
                        children = orderedChildren.map { child ->
                            TileSpec(folderChildTileId(folder.id, child.rowId), child.size)
                        },
                        columns = columns,
                    )
                }
            }
        }
    // Memoized for the same reason as expandTransform above — a fresh lambda
    // every recomposition would defeat DenseTileGrid's own `remember` around
    // the (non-trivial) sticky-pack computation. A live drag's push-down
    // preview (stickyPreview) takes priority over the persisted cell so
    // other tiles visibly slide out of the way while the finger is still
    // down, not just after the drop commits.
    val slotOf: ((String) -> Int?)? = remember(byId, sticky) {
        if (sticky) { id: String -> stickyPreview[id] ?: byId[id]?.gridSlot } else null
    }

    // Start-screen "sections": groups the real top-level ids in [order] into
    // blocks — one per real [sections] entry (in their own order), plus a
    // trailing catch-all for unsectioned tiles — each rendered as its own
    // independently-packed DenseTileGrid + editDragGesture instance below,
    // so a drag can never reorder a tile past one in a different block.
    // Cheap (a handful of tiles), left unmemoized like [displaySpecs].
    val blocks = blocksFor(order, byId, sections)

    // A block's own aggregate pending-notification count — a folder's own
    // count is already the sum of its children, so this doesn't double-count
    // a folder's members separately. Shared by the section header (its own
    // badge, visible whenever collapsed hides the individual tiles carrying
    // it) and the jump-pill bar (every pill, so a notification hiding in a
    // *different* tab/section is never invisible — user-requested).
    fun blockBadgeCount(sectionId: String?): Int {
        val block = blocks.firstOrNull { it.sectionId == sectionId } ?: return 0
        return block.ids.sumOf { id ->
            when (val m = byId[id]) {
                is TileModel.App -> notifications.badgeFor(m.packageName)
                is TileModel.Folder -> m.children.map { it.packageName }.distinct()
                    .sumOf { notifications.badgeFor(it) }
                null -> 0
            }
        }
    }

    // For every block: its own tile specs, its own packed placements (the
    // same pack/packSticky + [expandTransform] pipeline DenseTileGrid runs
    // internally — redone here too, once, purely to learn how tall the
    // block is), and where its own grid starts within the scrolling content
    // (the running sum of every earlier block's own header + grid height +
    // inter-block gap — zero for the very first block, matching today's
    // single-grid behaviour exactly whenever there are no sections at all).
    // Both the two empty-space gestures below (which need one flat,
    // correctly-offset rect list spanning every block) and each block's own
    // `editDragGesture` (its `blockTopOffsetPx`, for its own auto-scroll edge
    // check) read from this. Memoized like DenseTileGrid's own internal
    // pack: this can redo real work (expandFolderInline) for whichever block
    // holds the currently-expanded folder, which was previously slow enough
    // mid-drag to starve the touch-handling coroutine when left unmemoized
    // (see the comment on [expandedFolder] above).
    val headerHeightPx = with(density) { SECTION_HEADER_HEIGHT_DP.dp.toPx() }
    // Each block is now its own full page (see the per-block render loop
    // below) rather than stacked vertically, so there's no cross-block
    // running offset to accumulate any more — every block's own content
    // starts at the top of its own page. A block's header only takes space
    // in edit mode (the name is hidden otherwise, reclaiming that height);
    // collapse/expand is gone — every block always renders its own tiles.
    val blockRenders = remember(blocks, columns, slotOf, stickyPreview, expandTransform, folderChildOrder.toList(), resizeGeom, editMode) {
        val gridTopOffsetPx = if (editMode) headerHeightPx else 0f
        blocks.map { block ->
            val specs = block.ids.mapNotNull { id -> byId[id]?.let { TileSpec(id, it.size) } }
            val base = slotOf?.let { GridPacker.packSticky(specs, it, columns) } ?: GridPacker.pack(specs, columns)
            val placements = expandTransform?.invoke(base) ?: base
            BlockRender(block, specs, placements, topOffsetPx = 0f, gridTopOffsetPx = gridTopOffsetPx)
        }
    }
    // One flat list of the ACTIVE block's own tiles' absolute rects, for the
    // two empty-space gestures — the only block a real touch can ever reach,
    // since every other block is translated off-screen (see the per-block
    // render loop below).
    val absoluteTileRects = remember(blockRenders, activeBlockIndex) {
        val render = blockRenders.getOrNull(activeBlockIndex) ?: return@remember emptyList()
        render.placements.map { p ->
            val r = resizeGeom.rect(p)
            p.id to Rect(r.left, r.top + render.gridTopOffsetPx, r.right, r.bottom + render.gridTopOffsetPx)
        }
    }
    // Each block page gets its own independent vertical scroll position (they're
    // genuinely separate pages now, not one shared scrolling column) — `key`
    // keeps a given section's own scroll position attached to its own identity
    // across a reorder, instead of following "whichever block is now at this
    // index." `activeScrollState` is only ever touched by the empty-space
    // gestures/auto-scroll below, which only ever fire on the one block a real
    // touch can reach (every other block is translated off-screen).
    val blockScrollStates: List<androidx.compose.foundation.ScrollState> = blocks.map { block ->
        key(block.sectionId ?: "__unsectioned__") { rememberScrollState() }
    }
    val activeScrollState = blockScrollStates[activeBlockIndex.coerceIn(0, blockScrollStates.size - 1)]

    // "Reachability": dragging down from empty space on this page slides its
    // own already-rendered tiles down toward the thumb — a pure paint
    // transform (graphicsLayer on the active block's own Column below), not
    // a reflow or a persisted change.
    var reachabilityOffsetPx by remember { mutableStateOf(0f) }
    var reachabilityHeld by remember { mutableStateOf(false) }
    val reachabilityAnim = remember { Animatable(0f) }
    val reachabilityScope = rememberCoroutineScope()
    val reachabilityActive = !editMode && expandedFolderId == null && blocks.size >= 2
    fun settleReachability(target: Float) {
        reachabilityScope.launch {
            reachabilityAnim.snapTo(reachabilityOffsetPx)
            reachabilityAnim.animateTo(target, tween(220)) { reachabilityOffsetPx = value }
        }
    }
    LaunchedEffect(activeBlockIndex, editMode) {
        reachabilityHeld = false
        reachabilityAnim.stop()
        reachabilityOffsetPx = 0f
    }
    // How far the tiles may travel: exactly the real empty space below the
    // active page's own rendered content, capped at a sane max — this both
    // keeps a tile from ever being dragged past the bottom of the screen and
    // makes the gesture a natural no-op on an already-full page, with no
    // extra feature-gating needed for that case.
    val maxReachabilityOffsetPx = remember(blockRenders, activeBlockIndex, viewportHeightPx) {
        val active = blockRenders.getOrNull(activeBlockIndex)
        val contentBottomPx = active?.let {
            it.gridTopOffsetPx + resizeGeom.totalHeight(GridPacker.rowCount(it.placements))
        } ?: 0f
        (viewportHeightPx - contentBottomPx).coerceIn(0f, with(density) { 260.dp.toPx() })
    }

    // Resolves a synthetic child id back to its folder id + real FolderChild,
    // for routing unpin/resize/colour to the folder-child ViewModel calls
    // instead of the top-level ones. Null for a real top-level tile id.
    fun folderChildRef(id: String): Pair<String, FolderChild>? {
        val (folderId, rowId) = parseFolderChildId(id) ?: return null
        val folder = byId[folderId] as? TileModel.Folder ?: return null
        val child = folder.children.firstOrNull { it.rowId == rowId } ?: return null
        return folderId to child
    }

    // Persists a live in-folder drag reorder (folderChildOrder) once the drop
    // completes, provided it actually changed something.
    fun commitFolderChildReorder() {
        val folder = expandedFolder ?: return
        val childById = folder.children.associateBy { folderChildTileId(folder.id, it.rowId) }
        val newOrder = folderChildOrder.mapNotNull { childById[it] }
        if (newOrder.size == folder.children.size && newOrder != folder.children) {
            onReorderFolderChildren(newOrder)
        }
    }

    // Live tiles (FR-2). The flip scheduler turns one of the flippable tiles
    // every ~2.6 s, paused whenever live tiles are gated off (edit mode,
    // off-screen, screen off, battery saver, animations off) — a per-tile
    // `liveActive` additionally gates each block's own tiles to only the
    // currently active page (see the per-block render loop below), so an
    // off-screen section's tiles don't keep animating/polling once every
    // block is simultaneously composed for the swipe.
    val liveActive = rememberLiveTilesActive(suspended = editMode || liveSuspended || !liveTilesEnabled)
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
    // Always targets the active page's own scroll state — the only one a
    // real drag can ever be happening on.
    var autoScroll by remember { mutableStateOf(0) } // -1 up, 0 off, +1 down
    LaunchedEffect(autoScroll) {
        if (autoScroll == 0) return@LaunchedEffect
        val speed = with(density) { 8.dp.toPx() }
        while (true) {
            val delta = autoScroll * speed
            val consumed = activeScrollState.scrollBy(delta)
            if (consumed == 0f) break // hit an edge
            withFrameNanos { }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Tapping empty space in edit mode exits (prototype onDown).
            .emptySpaceExit(editMode, onExitEdit)
            // Tapping empty space while a folder is inline-expanded (but not
            // editing) collapses it — the same "tap outside to dismiss"
            // convention as the old modal FolderOverlay's scrim, without the
            // whole-grid edit chrome that a real edit-mode exit would carry.
            // Must hit-test against the real tile placements rather than
            // reuse [emptySpaceExit]'s "was this touch consumed" check: a
            // plain tile's own [tileGesture] deliberately never consumes (so
            // grid scroll still wins on a drag), so that check can't tell "a
            // tile is here" from "this is empty space" — an earlier version
            // of this used [emptySpaceExit] directly and raced every tile's
            // own tap handler, occasionally consuming the tap normally meant
            // to launch a folder child or an outside tile instead.
            .folderCollapseOnEmptyTap(
                active = !editMode && expandedFolderId != null,
                absoluteTileRects = absoluteTileRects,
                onExit = onCollapseFolder,
            )
            // Long-press on empty grid space also enters edit mode (with
            // nothing selected), matching a tile's own long-press — only
            // when there's no folder inline-expanded (that's the tap-to-
            // collapse gesture above's territory instead).
            .emptySpaceEnterEdit(
                active = !editMode && expandedFolderId == null,
                absoluteTileRects = absoluteTileRects,
                contentTopPx = if (hideStatusBar) 0f else statusBarTopPx,
                scrollOffsetPx = { activeScrollState.value.toFloat() },
                onEnterEdit = { onEnterEdit(null) },
                reachabilityActive = reachabilityActive,
                reachabilityHeld = reachabilityHeld,
                reachabilityOffsetPx = reachabilityOffsetPx,
                maxReachabilityOffsetPx = maxReachabilityOffsetPx,
                onReachabilityDrag = { px -> reachabilityOffsetPx = px },
                onReachabilitySettle = { hold ->
                    reachabilityHeld = hold
                    settleReachability(if (hold) maxReachabilityOffsetPx * REACHABILITY_HELD_FRACTION else 0f)
                },
                onReachabilityCollapse = {
                    reachabilityHeld = false
                    settleReachability(0f)
                },
            ),
    ) {
        // Every block renders as its own full page, translated horizontally by
        // its own index relative to the pager's live position — the same
        // plain full-slide treatment the feed/app-list pages already use, so
        // an in-progress swipe shows real neighboring pages sliding in rather
        // than narrowing to just the active one and swapping content on
        // settle. `key` keeps a section's own scroll/drag-adjacent state
        // (see [blockScrollStates] above) attached to its own identity across
        // a reorder, not to "whichever block is now at this index."
        blockRenders.forEachIndexed { blockIndex, render ->
        val block = render.block
        key(block.sectionId ?: "__unsectioned__") {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = widthPx * (blockIndex - pagerProgress) }
                .clipToBounds(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(blockScrollStates[blockIndex])
                .then(
                    if (hideStatusBar) {
                        // Reclaim the top of the screen fully — including the
                        // display-cutout inset (e.g. a punch-hole camera), which
                        // on some devices reserves just as much top space as the
                        // status bar itself and would otherwise leave the exact
                        // same "wasted gap" this setting is meant to remove.
                        Modifier
                    } else {
                        Modifier.statusBarsPadding().displayCutoutPadding()
                    },
                )
                .navigationBarsPadding(),
        ) {
            // "+ add page" (edit mode only): a prominent affordance at the
            // very top of the grid — always available now, like folders
            // (no separate on/off setting to gate it any more). Naming a new
            // page is a real modal dialog (not the inline swap-the-button-
            // for-a-text-field this used to be) — user-requested, so it
            // reads as a deliberate "create" step rather than something that
            // could be dismissed by an accidental tap elsewhere; committing
            // also exits edit mode, since there's nothing on the fresh empty
            // page yet worth staying in edit mode to arrange.
            if (editMode) {
                var addingSectionDialog by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 10.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { addingSectionDialog = true },
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Glass.faceTextColor(screenBackgroundIsLight).copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "+",
                            color = Glass.faceTextColor(screenBackgroundIsLight),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "add page",
                        color = Glass.faceTextColor(screenBackgroundIsLight).copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (addingSectionDialog) {
                    AddPageDialog(
                        onCommit = { label ->
                            addingSectionDialog = false
                            onCreateSection(label)
                            onExitEdit()
                        },
                        onCancel = { addingSectionDialog = false },
                    )
                }
            }
            // A real section gets a tinted panel wrapping its header + tiles,
            // so it reads as its own boxed region (user-requested "show
            // visually various ways sections can be shown"); the trailing
            // unsectioned block never does, staying exactly as it rendered
            // Only the active tab's block is ever in [visibleBlockRenders],
            // so there's never a second section on screen to visually set
            // this one apart from — no tinted panel wrapper needed here (an
            // earlier continuous-scroll display mode used one; dropped along
            // with that mode).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Reachability shift (see [reachabilityOffset] above) —
                    // a paint-only translation, so this block's own layout
                    // size/position in the outer scrolling Column is
                    // unaffected; only where it's drawn (and, since Compose
                    // hit-testing accounts for graphicsLayer, where its
                    // tiles' own taps land) moves.
                    .graphicsLayer { translationY = reachabilityOffsetPx },
            ) {
            // The page's own name/rename/reorder/remove controls only show
            // while editing — hidden otherwise, reclaiming that height (the
            // page is identified purely by swiping to it, not by a
            // permanently-visible label).
            if (editMode) {
                if (block.sectionId != null) {
                    SectionHeader(
                        label = block.label ?: "",
                        editMode = editMode,
                        textColor = Glass.faceTextColor(screenBackgroundIsLight).copy(alpha = 0.85f),
                        badgeCount = blockBadgeCount(block.sectionId),
                        darkTheme = darkTheme,
                        onMoveEarlier = { onMoveSection(block.sectionId, -1) },
                        onMoveLater = { onMoveSection(block.sectionId, 1) },
                        onRename = { newLabel -> onRenameSection(block.sectionId, newLabel) },
                    )
                } else {
                    // The unsectioned ("main") page reads as a plain label,
                    // not the full interactive SectionHeader: there's no real
                    // Section row behind it to rename/reorder/delete.
                    UnsectionedHeader(textColor = Glass.faceTextColor(screenBackgroundIsLight).copy(alpha = 0.85f))
                }
            }
            // Every block's tiles always show — collapse/expand is gone (a
            // "collapsed" page has no purpose once every section is already
            // its own page you swipe past).
            run {
            key(block.sectionId ?: "__unsectioned__") {
            val blockIds = block.ids
            val blockIdSet = blockIds.toHashSet()
            fun moveWithinBlockToEnd(dragId: String) {
                if (blockIds.lastOrNull() != dragId && dragId in blockIdSet) {
                    val within = (blockIds - dragId) + dragId
                    val spliced = spliceBlockOrder(order.toList(), blockIdSet, within)
                    order.clear()
                    order.addAll(spliced)
                }
            }
            val editDrag = Modifier.editDragGesture(
                editMode = editMode,
                widthPx = widthPx,
                columns = columns,
                gapPx = tileGapPx,
                order = blockIds,
                byId = augmentedById,
                draggingId = { draggingId },
                selectedId = { selectedTileId },
                // A folder-child corner tap routes to the folder-scoped action;
                // a top-level tile keeps the original behaviour.
                onUnpin = { id ->
                    val ref = folderChildRef(id)
                    if (ref != null) {
                        onPullOutFolderChild(ref.first, ref.second)
                    } else {
                        order.remove(id)
                        onUnpin(id)
                    }
                },
                // Opening a folder from its edit-mode corner icon (long-press a
                // folder → tap the folder glyph) hands off to the same inline
                // expand-in-place browsing view a plain tap uses — which is a
                // non-edit interaction, so edit mode must end here too.
                // Otherwise the folder ends up expanded *while still in edit
                // mode*: every other tile still routes through editDragGesture
                // (select-for-editing) instead of tileGesture (launch), and
                // nothing else can tap-to-collapse it either, since that path
                // only exists on tileGesture's onTap.
                onOpenFolder = { id ->
                    augmentedById[id]?.let(onTile)
                    onExitEdit()
                },
                onResize = { id ->
                    val ref = folderChildRef(id)
                    if (ref != null) onResizeFolderChild(ref.first, ref.second) else onResize(id)
                },
                onColor = { id -> colorPickerFor = id },
                // Merging is disabled while a folder is expanded: its children
                // are never valid merge participants, and without this a drag
                // hovering over one would show a confusing "merge target"
                // highlight for a merge that would silently no-op.
                allowMerge = expandedFolderId == null,
                postProcess = expandTransform,
                onLift = { id, offset -> draggingId = id; dragOffset.value = offset },
                onDrag = { offset -> dragOffset.value = offset },
                onReorderTo = { dragId, targetId ->
                    // Scoped to this block's own ids only (both dragId and
                    // targetId always are, since [editDragGesture]'s own
                    // hit-testing here only ever sees the ids we passed it as
                    // `order` above) — a drag can never reorder past a tile
                    // in a different section/the unsectioned group.
                    val within = reorderTiles(blockIds, dragId, targetId)
                    if (within != blockIds) {
                        val spliced = spliceBlockOrder(order.toList(), blockIdSet, within)
                        order.clear()
                        order.addAll(spliced)
                    }
                },
                onMoveToEnd = { dragId -> moveWithinBlockToEnd(dragId) },
                onMergeMode = { dragId ->
                    // Park the dragged tile at the end of its own block so the
                    // other tiles in it settle into their natural slots
                    // beneath the floating tile.
                    moveWithinBlockToEnd(dragId)
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
                // The real per-page scroll position — not the outer
                // `scrollState` param, which is orphaned now that each block
                // page owns its own ScrollState (see blockScrollStates
                // above) and nothing scrolls the outer one any more. Real
                // bug, user-reported ("moving the tile it starts scrolling
                // down"): reading the dead, permanently-0 outer scrollState
                // here inflated the drag's on-screen finger position by the
                // page's real scroll offset once it had actually been
                // scrolled, spuriously tripping the near-bottom-edge
                // auto-scroll check well before the finger was anywhere
                // near the true bottom edge.
                scrollOffsetPx = { blockScrollStates[blockIndex].value.toFloat() },
                edgeZonePx = with(density) { 64.dp.toPx() },
                blockTopOffsetPx = render.gridTopOffsetPx,
                slotOf = slotOf,
                freeMode = freeMode,
                onStickyDrop = { id, slot -> if (slot != null) onSetTileSlot(id, slot) },
                onStickyPreview = { stickyPreview = it },
                onReorderFolderChildTo = { dragId, targetId ->
                    val next = reorderTiles(folderChildOrder.toList(), dragId, targetId)
                    if (next != folderChildOrder.toList()) {
                        folderChildOrder.clear()
                        folderChildOrder.addAll(next)
                    }
                },
                onFolderChildDrop = ::commitFolderChildReorder,
                onFolderChildPulledOut = { childId, target ->
                    val ref = folderChildRef(childId)
                    if (ref != null) {
                        val (folderId, child) = ref
                        when (target) {
                            is FolderChildDropTarget.Merge ->
                                onPullOutFolderChildToMerge(folderId, child, target.targetId)
                            is FolderChildDropTarget.Cell ->
                                onPullOutFolderChildToSlot(folderId, child, target.slot)
                            is FolderChildDropTarget.Position ->
                                onPullOutFolderChildToPosition(folderId, child, target.beforeId)
                        }
                    }
                },
                // Dense-mode live reflow (see editDragGesture's own doc on
                // these two params): splice the synthetic child id into the
                // real top-level `order` at the hovered position, mirroring
                // onReorderTo's own live-mutation pattern above, and drop it
                // out of folderChildOrder at the same time so it isn't ALSO
                // rendered inline inside the folder — expandTransform reads
                // folderChildOrder live (see its own comment), so this takes
                // effect immediately, no memo invalidation needed.
                onFolderChildDensePreview = { childId, beforeId ->
                    folderChildOrder.remove(childId)
                    val next = insertBeforeTarget(order.toList(), childId, beforeId)
                    if (next != order.toList()) {
                        order.clear()
                        order.addAll(next)
                    }
                },
                // Reverts the splice above: drop the synthetic id back out of
                // `order`, and — if the same folder is still expanded —
                // reinsert it into folderChildOrder at its real persisted
                // position among the folder's children (not just appended),
                // so the existing intra-folder sibling-reorder logic resumes
                // from the right spot rather than the child jumping to the
                // end of its siblings.
                onFolderChildDensePreviewClear = { childId ->
                    order.remove(childId)
                    val folder = expandedFolder
                    if (folder != null && childId !in folderChildOrder && parseFolderChildId(childId)?.first == folder.id) {
                        val ids = folder.children.map { folderChildTileId(folder.id, it.rowId) }
                        val idx = ids.indexOf(childId).let { if (it >= 0) it else folderChildOrder.size }
                        folderChildOrder.add(idx.coerceIn(0, folderChildOrder.size), childId)
                    }
                },
                crossPageEdgeZonePx = with(density) { CROSS_PAGE_DRAG_EDGE_ZONE_DP.dp.toPx() },
                onCrossPageShift = onCrossPageShift,
                onCrossPageDrop = { tileId, direction, targetSlot ->
                    // This gesture's own onDrop (below) never fires for this
                    // branch — the tile is leaving this page entirely, so its
                    // position within this page's own grid is moot — reset
                    // the shared drag state it would otherwise have cleaned up.
                    autoScroll = 0
                    draggingId = null
                    mergeTargetId = null
                    onCrossPageDrop(tileId, direction, targetSlot)
                },
                blockIndex = blockIndex,
                livePagerProgress = livePagerProgress,
                onCrossPageDragPosition = { tileId, offset ->
                    crossPageDragId = tileId
                    if (offset != null) crossPageDragOffset = offset
                },
            )

            DenseTileGrid(
                tiles = render.specs,
                columns = columns,
                gapPx = tileGapPx,
                slotOf = slotOf,
                slotOfKey = stickyPreview,
                postProcess = expandTransform,
                postProcessKey = folderChildOrder.toList(),
                modifier = Modifier.fillMaxWidth().then(editDrag),
            ) { spec, slot, sizePx ->
                val model = augmentedById[spec.id] ?: return@DenseTileGrid
                val dragging = spec.id == draggingId
                val resizing = spec.id == resizingId
                val slotState = animateIntOffsetAsState(slot, label = "slot")
                val index = displaySpecs.indexOfFirst { it.id == spec.id }
                // A live resize grows/shrinks this exact wrapper Box to the
                // drag's current snapped TileSize — a real preview of the
                // final tile rather than a separate outline, since TileView's
                // own content doesn't switch renderer by size (only Stage 3's
                // DenseTileGrid call site branches on `spec.size`, which stays
                // the *persisted* size throughout the drag either way).
                val livePreviewSizePx = if (resizing) {
                    resizePreviewSize?.let { resizeGeom.sizePx(TilePlacement("_resize_preview", it, 0, 0)) }
                } else {
                    null
                }
                Box(
                    modifier = Modifier
                        .offset { if (dragging) dragOffset.value else slotState.value }
                        .zIndex(if (dragging || resizing) 10f else 0f)
                        .size(
                            with(density) { (livePreviewSizePx?.width ?: sizePx.width).toDp() },
                            with(density) { (livePreviewSizePx?.height ?: sizePx.height).toDp() },
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
                        wallpaperAccent != null -> wallpaperAccent
                        else -> accent
                    }
                    // Shared across both renderers below (IconCellView / TileView) —
                    // extracted once so the icons-mode branch can't drift from
                    // tile mode's own wiring for the same gestures.
                    val badgeCount = when (model) {
                        is TileModel.App -> notifications.badgeFor(model.packageName)
                        // A folder aggregates the unread counts of its children,
                        // so a folder of mail/chat apps surfaces a single summed
                        // badge (de-duped by package — multiple activities of one
                        // app count once).
                        is TileModel.Folder -> model.children
                            .map { it.packageName }.distinct()
                            .sumOf { notifications.badgeFor(it) }
                    }
                    // "Move back/forward" (TalkBack custom actions) reorder the
                    // list-backed order — meaningless once a sticky-mode tile sits
                    // at its own anchored cell instead of a sequence position, so
                    // they're hidden there (drag-drop to any free cell replaces them).
                    val canMoveBack = !sticky && blockIds.indexOf(model.id) > 0
                    val canMoveForward = !sticky && blockIds.indexOf(model.id) in 0 until blockIds.size - 1
                    val onTapAction = {
                        if (!editMode) {
                            // Tapping any tile while reachability is holding
                            // performs the tap normally *and* springs the
                            // shift back — the tap itself is what dismisses it.
                            if (reachabilityHeld) {
                                reachabilityHeld = false
                                settleReachability(0f)
                            }
                            onTile(model)
                        }
                    }
                    val onLongPressAction = { if (!editMode) onEnterEdit(model.id) }
                    val onSelectAction = { onSelectTile(model.id) }
                    val onUnpinAction = {
                        val ref = folderChildRef(model.id)
                        if (ref != null) {
                            onPullOutFolderChild(ref.first, ref.second)
                        } else {
                            order.remove(model.id)
                            onUnpin(model.id)
                        }
                    }
                    val onMoveAction = { dir: Int ->
                        val i = blockIds.indexOf(model.id)
                        val j = i + dir
                        if (i >= 0 && j in blockIds.indices) {
                            val within = reorderTiles(blockIds, model.id, blockIds[j])
                            if (within != blockIds) {
                                val spliced = spliceBlockOrder(order.toList(), blockIdSet, within)
                                order.clear()
                                order.addAll(spliced)
                                onReorder(spliced)
                            }
                        }
                    }
                    // Gesture-based drag resize: available to a folder child
                    // too (inline expansion already renders it in the same
                    // absolute grid a top-level tile uses), routed to its own
                    // write path below since a child's resize has to run the
                    // stack-collapse/-promote bookkeeping a top-level tile
                    // doesn't need.
                    val resizeHandlesEnabled = true
                    val onResizeDragStartAction = {
                        resizingId = model.id
                        resizeAccumDx = 0f
                        resizeAccumDy = 0f
                        resizePreviewSize = model.size
                    }
                    val requireTallCycle = AppCategories.requiresTallTile((model as? TileModel.App)?.iconKey)
                    val onResizeDragByAction = { dx: Float, dy: Float ->
                        resizeAccumDx += dx
                        resizeAccumDy += dy
                        resizePreviewSize = snapResizeTarget(
                            geom = resizeGeom,
                            currentCols = model.size.cols,
                            currentRows = model.size.rows,
                            dxPx = resizeAccumDx,
                            dyPx = resizeAccumDy,
                            columns = columns,
                            minRows = if (requireTallCycle) 2 else 1,
                        )
                    }
                    val onResizeDragEndAction = {
                        resizePreviewSize?.let { newSize ->
                            val ref = folderChildRef(model.id)
                            when {
                                ref != null -> onResizeFolderChildTo(ref.first, ref.second, newSize)
                                // A stack's own drag resizes the whole thing: every
                                // member homogenizes to the new size along with the
                                // tile, staying a valid uniform stack (or, if the
                                // drag lands on a non-stackable size, falling back to
                                // the plain mini-grid — see TileModel.Folder.isStack's
                                // doc comment — with showAsStack left on so it
                                // resumes as a stack once resized back).
                                model is TileModel.Folder && model.isStack ->
                                    onResizeStack(model.id, newSize)
                                else -> onResizeTo(model.id, newSize)
                            }
                        }
                        resizingId = null
                        resizePreviewSize = null
                    }

                    // ICONS home style renders a SMALL app tile as a plain shaped
                    // icon, and a SMALL folder as the same shaped icon holding a
                    // 2x2 mini-grid of its children. A single app tile can also
                    // stretch that same shaped-icon rendering up to MEDIUM/WIDE/
                    // LARGE/XLARGE via its own displayAsIcon toggle (user-requested
                    // "show as icon"/"show as tile" — OneUI/Nothing-OS-style resizable
                    // icons; since a stretched icon can't also show live content,
                    // this is per-tile, defaulting to icon) — gated on a real
                    // resolvable package so the blank-package weather/calendar/
                    // clock/personalize liveOnly tiles (and pinned contacts, whose
                    // packageName is also blank) always keep showing live content
                    // regardless of the flag's default. A widget stack (whose own
                    // size is always WIDE/LARGE, never SMALL) always falls through
                    // to TileView unchanged (see HomeStyle's doc comment).
                    // Inline-expanded folder children are covered by the first
                    // branch too: they're synthetic TileModel.App instances at
                    // whatever size they were resized to (see FolderChild.asTileModel,
                    // which pins displayAsIcon = false so a child keeps its original
                    // SMALL-only-icon behaviour), so a SMALL one already renders as a
                    // plain icon with zero extra code here.
                    if (homeStyle == HomeStyle.ICONS && model is TileModel.App &&
                        (model.size == TileSize.SMALL || (model.displayAsIcon && model.packageName.isNotBlank()))
                    ) {
                        IconCellView(
                            tile = model,
                            editMode = editMode,
                            selected = editMode && model.id == selectedTileId,
                            dragging = dragging,
                            index = index,
                            jigglePhase = jigglePhase,
                            darkTheme = darkTheme,
                            columns = columns,
                            badgeCount = badgeCount,
                            notifications = notifications,
                            onTap = onTapAction,
                            onLongPress = onLongPressAction,
                            onSelect = onSelectAction,
                            onExitEdit = onExitEdit,
                            onUnpin = onUnpinAction,
                            onMove = onMoveAction,
                            canMoveBack = canMoveBack,
                            canMoveForward = canMoveForward,
                            iconShape = iconShape,
                            themedIcons = themedIcons,
                            stockRefreshRate = stockRefreshRate,
                            commodityRefreshRate = commodityRefreshRate,
                            accent = tileAccent,
                            liveActive = liveActive && blockIndex == activeBlockIndex,
                            resizeHandlesEnabled = resizeHandlesEnabled,
                            onResizeDragStart = onResizeDragStartAction,
                            onResizeDragBy = onResizeDragByAction,
                            onResizeDragEnd = onResizeDragEndAction,
                            // The colour dot (→ tile colour picker → the "show
                            // as icon"/"show as tile" toggle) only for a real
                            // top-level app tile — never a folder child, which
                            // has no such toggle (see TileColorPicker's own
                            // childRef gating).
                            showColorDot = model.packageName.isNotBlank() && folderChildRef(model.id) == null,
                        )
                    } else if (homeStyle == HomeStyle.ICONS && model is TileModel.Folder && model.size == TileSize.SMALL) {
                        IconFolderCell(
                            tile = model,
                            editMode = editMode,
                            selected = editMode && model.id == selectedTileId,
                            dragging = dragging,
                            index = index,
                            jigglePhase = jigglePhase,
                            darkTheme = darkTheme,
                            columns = columns,
                            badgeCount = badgeCount,
                            notifications = notifications,
                            onTap = onTapAction,
                            onLongPress = onLongPressAction,
                            onSelect = onSelectAction,
                            onExitEdit = onExitEdit,
                            onUnpin = onUnpinAction,
                            onMove = onMoveAction,
                            canMoveBack = canMoveBack,
                            canMoveForward = canMoveForward,
                            iconShape = iconShape,
                            resizeHandlesEnabled = resizeHandlesEnabled,
                            onResizeDragStart = onResizeDragStartAction,
                            onResizeDragBy = onResizeDragByAction,
                            onResizeDragEnd = onResizeDragEndAction,
                        )
                    } else {
                        TileView(
                            tile = model,
                            index = index,
                            editMode = editMode,
                            selected = editMode && model.id == selectedTileId,
                            dragging = dragging,
                            mergeTarget = model.id == mergeTargetId,
                            isExpanded = spec.id == expandedFolderId,
                            homeStyle = homeStyle,
                            iconShape = iconShape,
                            themedIcons = themedIcons,
                            stockRefreshRate = stockRefreshRate,
                            commodityRefreshRate = commodityRefreshRate,
                            sportsRefreshRate = sportsRefreshRate,
                            accent = tileAccent,
                            glass = glass,
                            transparency = transparency,
                            glassLine = glassLine,
                            tiledWallpaper = tiledWallpaper,
                            borderless = borderless,
                            tileOutline = tileOutline,
                            wallpaper = wallpaper,
                            wallpaperPhoto = wallpaperPhoto,
                            wallpaperAlignX = wallpaperAlignX,
                            wallpaperAlignY = wallpaperAlignY,
                            wallpaperZoom = wallpaperZoom,
                            // This tile's window onto the screen-fixed wallpaper: its live
                            // on-screen top-left (grid slot minus the scroll offset, below
                            // the status bar). [slot] is local to this block's own grid —
                            // real bug, user-reported ("behind the tiles option not working
                            // well for section view"): this dropped [render.gridTopOffsetPx],
                            // the block's own header height, so every section's tiles (any
                            // block with a header above its grid) painted the wallpaper
                            // window shifted up by exactly that height, visibly seaming at
                            // the header. Read in the draw phase, so the wallpaper stays put
                            // while the tiles scroll over it.
                            wallpaperOrigin = {
                                Offset(
                                    slot.x.toFloat(),
                                    statusBarTopPx + render.gridTopOffsetPx + slot.y.toFloat() - blockScrollStates[blockIndex].value.toFloat(),
                                )
                            },
                            fullWidth = widthPx,
                            fullHeight = viewportHeightPx,
                            jigglePhase = jigglePhase,
                            flipped = flipState.isFlipped(model.id),
                            liveActive = liveActive && blockIndex == activeBlockIndex,
                            notifications = notifications,
                            badgeCount = badgeCount,
                            darkTheme = darkTheme,
                            canMoveBack = canMoveBack,
                            canMoveForward = canMoveForward,
                            showColorDot = true,
                            // Inline tap-to-launch: always for medium/wide folders
                            // (cells stay tappable); for a small folder only on the
                            // roomy 4-column grid (too tiny on 5/6 columns).
                            inlineFolderLaunch = model.size != TileSize.SMALL || columns == 4,
                            appIconColors = appIconColors,
                            wallpaperAccent = wallpaperAccent,
                            onTap = onTapAction,
                            onLongPress = onLongPressAction,
                            onLaunchFolderChild = onLaunchFolderChild,
                            onRenameFolder = if (model is TileModel.Folder) {
                                { newName -> onRenameFolder(model.id, newName) }
                            } else {
                                {}
                            },
                            // TalkBack-only path (sighted corner taps go through
                            // editDragGesture, already folder-child-aware above).
                            onResize = {
                                val ref = folderChildRef(model.id)
                                if (ref != null) onResizeFolderChild(ref.first, ref.second) else onResize(model.id)
                            },
                            resizeHandlesEnabled = resizeHandlesEnabled,
                            onResizeDragStart = onResizeDragStartAction,
                            onResizeDragBy = onResizeDragByAction,
                            onResizeDragEnd = onResizeDragEndAction,
                            onUnpin = onUnpinAction,
                            onSelect = onSelectAction,
                            onExitEdit = onExitEdit,
                            onMove = onMoveAction,
                        )
                    }
                }
            }
            } // end key(block.sectionId) [inner]
            } // end run
            } // end Column(sectionPanelModifier)
            // FR-1 bottom breathing room (prototype home-scroll padding-bottom:74px;
            // grows to clear the edit bar while editing, like .home-scroll padding).
            Spacer(Modifier.height(if (editMode) 130.dp else 74.dp))
        } // end per-page Column(verticalScroll)
        } // end per-page Box(translationX)
        } // end key(block.sectionId ?: "__unsectioned__") [outer]
        } // end blockRenders.forEachIndexed

        // Cross-page drag's own floating visual: the dragged tile's real
        // in-grid rendering lives inside its source page's own DenseTileGrid,
        // invisible once that page has slid off-screen mid-carry — this is
        // what lets the user actually see (a simplified stand-in for) the
        // tile while continuing to aim it on the now-visible destination
        // page, addressing the real gap the release-time-only design left
        // (see docs/DECISIONS.md). Drawn last, so it's always on top.
        crossPageDragId?.let { dragId ->
            byId[dragId]?.let { model ->
                CrossPageDragGhost(
                    model = model,
                    offsetPx = crossPageDragOffset,
                    resizeGeom = resizeGeom,
                    accent = accent,
                    accentId = accentId,
                    wallpaperAccent = wallpaperAccent,
                )
            }
        }

        // Whole-page removal — moved out of the scrolling page header into a
        // fixed top-right corner control (user-requested: "this option top
        // right corner of page") so it's always reachable regardless of
        // scroll position, rather than scrolling away with the header. Only
        // the currently active page's own id is ever in scope here — a real
        // section only (never "main", which has nothing to merge into and
        // isn't itself removable this way).
        val activeSectionId = if (editMode) blocks.getOrNull(activeBlockIndex)?.sectionId else null
        if (activeSectionId != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(Glass.faceTextColor(screenBackgroundIsLight).copy(alpha = 0.12f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { pageRemoveMenuExpanded = true },
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = TileIcons["close"],
                        contentDescription = null,
                        tint = Glass.faceTextColor(screenBackgroundIsLight),
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "remove",
                        color = Glass.faceTextColor(screenBackgroundIsLight),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                DropdownMenu(expanded = pageRemoveMenuExpanded, onDismissRequest = { pageRemoveMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("merge with main") },
                        onClick = {
                            pageRemoveMenuExpanded = false
                            onDeleteSection(activeSectionId)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("remove page & tiles") },
                        onClick = {
                            pageRemoveMenuExpanded = false
                            confirmRemovePageId = activeSectionId
                        },
                    )
                }
            }
        }

        // "remove page & tiles" confirmation — a bulk, multi-tile unpin in one
        // tap is worth guarding against an accidental press, unlike "merge
        // with main" (nothing is lost there, just ungrouped).
        confirmRemovePageId?.let { sectionId ->
            AlertDialog(
                onDismissRequest = { confirmRemovePageId = null },
                title = { Text("remove page & tiles?") },
                text = {
                    Text(
                        "this page and everything on it will be removed from start. " +
                            "apps stay installed — you can pin them again any time from the app list.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmRemovePageId = null
                        onRemovePageAndTiles(sectionId)
                        // The page you were editing is gone — the pager lands you on
                        // whichever page is now adjacent, but there's nothing left to
                        // edit *there* on your behalf, so edit mode exits too rather
                        // than leaving you editing a page you didn't ask to edit.
                        onExitEdit()
                    }) { Text("remove") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRemovePageId = null }) { Text("cancel") }
                },
            )
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
                        imageVector = TileIcons["more"],
                        contentDescription = "open app list",
                        tint = Glass.faceTextColor(screenBackgroundIsLight).copy(alpha = 0.72f),
                        modifier = Modifier.size(28.dp),
                    )
                }
                // Tap affordance for the quick panel (two-finger swipe-up is the
                // primary gesture; this is the discoverable fallback for users who
                // don't find it — see docs/QUICK-PANEL-SPEC.md §4).
                Box(
                    modifier = Modifier.size(48.dp).clickable(onClick = onQuickPanel),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TileIcons["panel"],
                        contentDescription = "quick panel",
                        tint = Glass.faceTextColor(screenBackgroundIsLight).copy(alpha = 0.72f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // Bottom edit bar (prototype .edit-bar): slides up while editing.
        EditBar(
            visible = editMode,
            onAdd = onAdd,
            onAddWidgets = onAddWidgets,
            onPersonalize = onPersonalize,
            onDone = onExitEdit,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // Per-tile accent picker (edit-mode colour dot → palette, FR-7).
        colorPickerFor?.let { pickId ->
            val childRef = folderChildRef(pickId)
            val model = augmentedById[pickId]
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
            // The "show as stack"/"show as folder" toggle (moved here from a
            // standalone action tile next to the expanded children — see
            // docs/DECISIONS.md): shown only for a real top-level folder
            // (never a folder child, which is a synthetic App), and only when
            // it's either already a stack (always safe to un-toggle) or has
            // ≥2 children at a `TileSize.stackable` footprint (more than one
            // column) to uniform into a stack.
            val stackToggle = (model as? TileModel.Folder)?.let { folder ->
                when {
                    childRef != null -> null
                    folder.isStack -> "show as folder" to "folder"
                    folder.children.size >= 2 && folder.size.stackable -> "show as stack" to "stack"
                    else -> null
                }
            }
            // "unfold folder" / "remove folder & tiles": a real (not a
            // folder-child pseudo-tile) folder only — same gating as the
            // stack toggle above, minus its stack-eligibility condition
            // (these two apply to any folder regardless of size/uniformity).
            val isRealFolder = model is TileModel.Folder && childRef == null
            // The "show as icon"/"show as tile" toggle: single top-level app
            // tiles only (never a folder child — mirrors childRef != null
            // exclusion above, since FolderChild has no persisted field for
            // this), only in ICONS home style (meaningless in TILES mode),
            // and only with a real resolvable package (a blank-package
            // liveOnly tile — weather/calendar/clock/personalize — has no
            // icon to show and always stays a live tile regardless).
            val iconToggle = if (
                homeStyle == HomeStyle.ICONS && childRef == null && app != null && app.packageName.isNotBlank()
            ) {
                if (app.displayAsIcon) "show as tile" to "recents" else "show as icon" to "app"
            } else {
                null
            }
            // "move to section": top-level tiles only (a folder child has no
            // section of its own — it moves with its folder), and only once
            // at least one real section exists to offer.
            val sectionOptions = if (childRef == null && sections.isNotEmpty()) {
                listOf<Pair<String?, String>>(null to UNSECTIONED_LABEL) +
                    sections.sortedBy { it.order }.map { it.id to it.label }
            } else {
                null
            }
            TileColorPicker(
                current = current,
                suggestedNearestId = suggestion?.nearestId,
                suggestedExact = suggestion?.exact,
                stackToggleLabel = stackToggle?.first,
                stackToggleIconKey = stackToggle?.second,
                onToggleStack = {
                    onToggleFolderStack(pickId)
                    colorPickerFor = null
                },
                unfoldFolderLabel = if (isRealFolder) "unfold folder" else null,
                onUnfoldFolder = {
                    onUnfoldFolder(pickId)
                    colorPickerFor = null
                },
                removeFolderLabel = if (isRealFolder) "remove folder & tiles" else null,
                onRemoveFolderAndTiles = {
                    colorPickerFor = null
                    confirmRemoveFolderId = pickId
                },
                iconToggleLabel = iconToggle?.first,
                iconToggleIconKey = iconToggle?.second,
                onToggleIconDisplay = {
                    onSetTileDisplayAsIcon(pickId, !app!!.displayAsIcon)
                    colorPickerFor = null
                },
                // A masked real icon ignores accentOverride entirely, so the
                // swatch grid has no visible effect while showing as icon —
                // hide it rather than offer a choice that silently does
                // nothing (user-reported). Only applies to a tile actually
                // rendering as a masked icon, though: a blank-package liveOnly
                // tile (weather/calendar/clock/battery/alarm/moonphase/tasks/
                // notepad/stickynote) has no icon to mask to and always stays
                // a live tile regardless of `displayAsIcon`'s stored value
                // (mirrors `iconToggle`'s own `packageName.isNotBlank()` guard
                // just above) — omitting this check left the whole picker
                // rendering empty (no swatches, no toggle) for those tiles
                // whenever ICONS home style was active, reading as "the
                // colour picker isn't shown at all" (user-reported).
                showColorOptions = !(
                    homeStyle == HomeStyle.ICONS &&
                        app?.displayAsIcon == true &&
                        app.packageName.isNotBlank()
                ),
                sectionOptions = sectionOptions,
                currentSectionId = model?.sectionId,
                onAssignSection = { sectionId ->
                    onAssignTileSection(pickId, sectionId)
                    colorPickerFor = null
                },
                onPick = { colorId ->
                    if (childRef != null) {
                        onSetFolderChildColor(childRef.first, childRef.second.rowId, colorId)
                    } else {
                        onSetTileColor(pickId, colorId)
                    }
                    colorPickerFor = null
                },
                onDismiss = { colorPickerFor = null },
            )
        }

        // "remove folder & tiles" confirmation — a bulk, multi-tile unpin in
        // one tap is worth guarding against an accidental press, unlike
        // "unfold folder" (nothing is lost there, just ungrouped).
        confirmRemoveFolderId?.let { folderId ->
            AlertDialog(
                onDismissRequest = { confirmRemoveFolderId = null },
                title = { Text("remove folder & tiles?") },
                text = {
                    Text(
                        "this folder and everything in it will be removed from start. " +
                            "apps stay installed — you can pin them again any time from the app list.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmRemoveFolderId = null
                        onRemoveFolderAndTiles(folderId)
                    }) { Text("remove") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRemoveFolderId = null }) { Text("cancel") }
                },
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
 *
 * A folder additionally gets the "show as stack"/"show as folder" toggle here
 * ([stackToggleLabel] non-null, tinted with [stackToggleIconKey]'s glyph,
 * [onToggleStack]) — moved from a standalone action tile next to the
 * expanded folder's children into this sheet, per user request (see
 * docs/DECISIONS.md), since it's really another per-tile setting alongside
 * colour rather than something that needs its own grid cell. Set off below
 * the colour swatches with a thin divider, since it's a distinct setting,
 * not another colour choice.
 */
@Composable
private fun BoxScope.TileColorPicker(
    current: String?,
    suggestedNearestId: String?,
    suggestedExact: Color?,
    stackToggleLabel: String? = null,
    stackToggleIconKey: String? = null,
    onToggleStack: () -> Unit = {},
    // "unfold folder" (dissolve, every child stays pinned as its own tile)
    // and "remove folder & tiles" (unpin the folder and every child at
    // once) — a real folder only (null hides each row), same as the stack
    // toggle above.
    unfoldFolderLabel: String? = null,
    onUnfoldFolder: () -> Unit = {},
    removeFolderLabel: String? = null,
    onRemoveFolderAndTiles: () -> Unit = {},
    iconToggleLabel: String? = null,
    iconToggleIconKey: String? = null,
    onToggleIconDisplay: () -> Unit = {},
    // A tile "showing as icon" (ICONS home style) renders its real masked app
    // icon, ignoring any accent override entirely — so the colour swatches
    // below have no visible effect there and are just noise (user-reported).
    // Hidden in exactly that one case; a folder, a TILES-mode app, or an
    // ICONS-mode app currently "showing as tile" all still use their accent
    // for a real fill colour, so they keep the full picker.
    showColorOptions: Boolean = true,
    // Start-screen "sections": the ordered list of (sectionId, label) choices
    // to offer — always includes a leading (null, "unsectioned") entry — or
    // null to hide this row entirely (a folder child, which has no section
    // of its own). Non-null only when at least one real section exists.
    sectionOptions: List<Pair<String?, String>>? = null,
    currentSectionId: String? = null,
    onAssignSection: (String?) -> Unit = {},
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
        if (showColorOptions) {
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
        if (stackToggleLabel != null) {
            if (showColorOptions) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.15f)))
            }
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onToggleStack)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = TileIcons[stackToggleIconKey],
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(stackToggleLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (unfoldFolderLabel != null) {
            if (showColorOptions || stackToggleLabel != null) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.15f)))
            }
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onUnfoldFolder)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = TileIcons["folder"],
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(unfoldFolderLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (removeFolderLabel != null) {
            if (showColorOptions || stackToggleLabel != null || unfoldFolderLabel != null) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.15f)))
            }
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onRemoveFolderAndTiles)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = TileIcons["close"],
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(removeFolderLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (iconToggleLabel != null) {
            if (showColorOptions) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.15f)))
            }
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onToggleIconDisplay)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = TileIcons[iconToggleIconKey],
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(iconToggleLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (sectionOptions != null) {
            if (showColorOptions || stackToggleLabel != null || unfoldFolderLabel != null ||
                removeFolderLabel != null || iconToggleLabel != null
            ) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.15f)))
            }
            Spacer(Modifier.height(16.dp))
            Text("move to page", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sectionOptions.forEach { (sectionId, label) ->
                    val selected = sectionId == currentSectionId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                if (selected) Color.White else Color.White.copy(alpha = 0.3f),
                                RoundedCornerShape(16.dp),
                            )
                            .clickable { onAssignSection(sectionId) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(label.lowercase(), color = Color.White, fontSize = 13.sp)
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
            TileSize.WIDE_SMALL -> "wide small"
            TileSize.TALL -> "tall"
            TileSize.WIDE_MEDIUM -> "wide medium"
            TileSize.TALL_MEDIUM -> "tall medium"
            TileSize.XLARGE -> "extra large"
            TileSize.BANNER -> "banner"
            TileSize.COLUMN -> "column"
        }
        append(", $size tile")
        if (selected) append(", selected")
    }
}

@Composable
internal fun TileView(
    tile: TileModel,
    index: Int,
    editMode: Boolean,
    selected: Boolean,
    dragging: Boolean,
    mergeTarget: Boolean,
    // FR-4 WP-style: true for the folder tile currently expanded inline —
    // renders an up-arrow collapse affordance instead of its usual face.
    isExpanded: Boolean = false,
    // Only consulted by a closed folder's mini-grid (FolderTileContent) — see
    // FolderChildIcon's doc comment: ICONS mode shows each child's real app
    // icon there too, matching the top-level icon-vs-glyph rule; TILES mode
    // keeps the WP-authentic monoline glyph unchanged.
    homeStyle: HomeStyle = HomeStyle.TILES,
    // Threaded down to AppTileContent's live faces, whose AppIconCorner badge
    // masks to this shape in ICONS home style — see AppIconCorner's doc comment.
    iconShape: IconShape = IconShape.ORIGINAL,
    // Threaded down to AppTileContent's live faces' AppIconCorner badge —
    // themed/monochrome icon tinted to the tile's own face colour instead of
    // the app's full-colour icon (Personalize's "themed icons").
    themedIcons: Boolean = false,
    // Personalize's "live data refresh" — threaded down to the stock/commodity/
    // sports live faces, which fall back to their own hardcoded interval when left DEFAULT.
    stockRefreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    commodityRefreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    sportsRefreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    accent: Color,
    glass: Boolean,
    transparency: Float,
    glassLine: Color,
    tiledWallpaper: Boolean,
    // "borderless" tile style: paint neither a fill nor an outline, so only the
    // tile's own content shows over the wallpaper. Mutually exclusive with
    // [glass]/[tiledWallpaper] at the data layer, so it only ever wins the fill
    // `when` below when both of those are already false.
    borderless: Boolean = false,
    // Whether a glass / "behind tiles" tile draws its 1dp hairline. A solid or
    // borderless tile has none to begin with, so this is inert for those two.
    tileOutline: Boolean = true,
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
    notifications: NotificationSnapshot,
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
    onRenameFolder: (String) -> Unit = {},
    showColorDot: Boolean = false,
    inlineFolderLaunch: Boolean = false,
    appIconColors: Boolean = false,
    // See StartPage's own doc comment — resolved once, threaded down to a
    // folder's children / a stack's members so they resolve their own colour
    // the same way the top-level tile already did via its `accent` param.
    wallpaperAccent: Color? = null,
    // True only for the non-interactive wallpaper-crop live preview
    // (WallpaperStartPreview): renders the exact same tile content (icons,
    // live faces, colours, wallpaper windowing) but attaches none of the
    // tile's own touch-consuming gesture recognisers (tiltOnPress,
    // tap/long-press, music transport controls, a widget stack's
    // swipe-to-flip), so the crop overlay's own pinch/drag-to-position
    // gesture underneath is never intercepted. Every real Start-screen call
    // site leaves this at its default (false) and is completely unaffected.
    readOnly: Boolean = false,
    // Gesture-based drag resize (Stage 2 of the icons-mode arc). Only shown
    // when true — a widget stack gets the same corner-drag gesture as any
    // other tile (the call site's onResizeDragEnd routes a stack's drag
    // through onResizeStack instead of onResizeTo, homogenizing every
    // member); a folder child gets the same drag gesture a top-level tile
    // does too, routed to its own write path at the call site.
    // onResizeDragBy reports the *raw* pixel delta since drag start; the
    // caller (hoisted resize-preview state one level up) does the geometry
    // → TileSize snapping so TileView itself stays free of grid-geometry
    // knowledge, matching how every other gesture here is wired.
    resizeHandlesEnabled: Boolean = false,
    onResizeDragStart: () -> Unit = {},
    onResizeDragBy: (dxPx: Float, dyPx: Float) -> Unit = { _, _ -> },
    onResizeDragEnd: () -> Unit = {},
) {
    // TalkBack reads the whole tile as one node: the app/folder name plus state,
    // with the launch/edit operations exposed as semantic actions (the visual
    // drag/corner-control flow is sighted-only, so screen-reader users drive the
    // exact same ViewModel calls through these custom actions instead).
    val a11yLabel = tileAccessibilityLabel(tile, badgeCount, editMode, selected)

    // A borderless tile always uses its own fixed corner radius, not
    // Personalize's "corner radius" slider — see BORDERLESS_CORNER_RADIUS_DP's
    // doc comment. Personalize hides that row entirely for this style, since
    // it would otherwise have no visible effect.
    val tileCornerRadius = if (borderless) BORDERLESS_CORNER_RADIUS_DP else LocalTileCornerRadius.current
    val useTileGradient = LocalTileGradient.current
    // Glass fill tinted by this tile's own resolved accent (see Glass.kt) —
    // computed here, not passed in, so every tile (including each stack member
    // / folder cell below) tints by its own colour rather than one shared value.
    val glassFill = if (glass) Glass.fill(darkTheme, transparency, accent) else null
    // A widget stack owns its own tap/long-press (tap = launch current member,
    // long-press = enter edit mode), so the outer tile gesture is suppressed for it;
    // in edit mode the grid drag owns interaction for every tile. That only holds
    // while the stack is collapsed, though — expanded, it renders the shared
    // FolderExpandedPlaceholder (the arrow tile) instead of StackTileContent, and
    // that placeholder relies on the very same outer onTap to collapse back, just
    // like a plain (non-stack) folder does.
    val isStackTile = tile is TileModel.Folder && tile.isStack && !isExpanded

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
            .then(if (editMode || readOnly) Modifier else Modifier.tiltOnPress())
            // No drop shadow here — tried and removed (see DECISIONS.md
            // "Widget-card tiles: the drop shadow was removed a second
            // time"). Modifier.shadow rendered as a hard second rounded
            // rectangle rather than a soft blur on the emulator's software
            // renderer, reading as a border of its own — the exact thing
            // this whole style exists to avoid. The card fill + forced gap
            // + rounded corners already read as "raised" without it.
            // Rounded corners (personalisation setting 0–20 dp) — clipped
            // unconditionally (0dp is just a plain rectangular clip) so a
            // live face with a lot of text (e.g. a long sticky/notes preview)
            // can never bleed past this tile's own bounds into whatever sits
            // below it, instead of only clipping once a corner radius happens
            // to be set.
            .clip(RoundedCornerShape(tileCornerRadius.dp))
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
                    // Borderless paints no *tile colour* — a genuine
                    // widget-style translucent card instead (Glass
                    // .raisedCardFill), with the drop shadow above providing
                    // the actual sense of elevation this time.
                    borderless -> Modifier.background(Glass.raisedCardFill(darkTheme, transparency))
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
                    // "tile outline" off (Personalize) drops the hairline while
                    // keeping the fill, so adjacent tiles read as one continuous
                    // surface instead of a grid of outlined windows.
                    !tileOutline -> Modifier
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
                if (editMode || isStackTile || readOnly) Modifier
                else Modifier.tileGesture(onTap = onTap, onLongPress = onLongPress),
            )
            // Gesture-based resize (drag from the tile's bottom-right corner)
            // — for the selected tile in edit mode, including a widget stack
            // (dragging it resizes the whole stack, homogenizing every member
            // to the new size — see the call site's onResizeDragEnd, which
            // routes a stack's drag through onResizeStack instead of
            // onResizeTo), when the caller opted it in
            // (resizeHandlesEnabled).
            .then(
                if (selected && editMode && !isExpanded && resizeHandlesEnabled) {
                    Modifier.tileStretchGesture(
                        onDragStart = onResizeDragStart,
                        onDragBy = onResizeDragBy,
                        onDragEnd = onResizeDragEnd,
                    )
                } else {
                    Modifier
                },
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
                    val verb = when {
                        isExpanded -> "collapse"
                        tile is TileModel.Folder -> "expand"
                        else -> "launch"
                    }
                    onClick(label = verb) { onTap(); true }
                    customActions = listOf(
                        CustomAccessibilityAction("customize") { onLongPress(); true },
                    )
                }
            },
    ) {
        when {
            tile is TileModel.Folder && isExpanded -> FolderExpandedPlaceholder(
                name = tile.name,
                onRename = onRenameFolder,
            )
            tile is TileModel.App -> AppTileContent(
                tile,
                flipped = flipped,
                liveActive = liveActive,
                interactive = !editMode && !readOnly,
                homeStyle = homeStyle,
                iconShape = iconShape,
                themedIcons = themedIcons,
                stockRefreshRate = stockRefreshRate,
                commodityRefreshRate = commodityRefreshRate,
                sportsRefreshRate = sportsRefreshRate,
            )
            tile is TileModel.Folder ->
                // A widget stack's own carousel face owns a swipe-to-flip gesture
                // (right-edge zone) — skipped in the read-only preview in favour of
                // the plain closed mini-grid, so nothing there can intercept the
                // crop overlay's own drag/pinch.
                if (tile.isStack && !readOnly) {
                    StackTileContent(
                        tile = tile,
                        editMode = editMode,
                        selected = selected,
                        liveActive = liveActive,
                        accent = accent,
                        homeStyle = homeStyle,
                        iconShape = iconShape,
                        themedIcons = themedIcons,
                        stockRefreshRate = stockRefreshRate,
                        commodityRefreshRate = commodityRefreshRate,
                        sportsRefreshRate = sportsRefreshRate,
                        appIconColors = appIconColors,
                        glass = glass,
                        transparency = transparency,
                        tiledWallpaper = tiledWallpaper,
                        borderless = borderless,
                        darkTheme = darkTheme,
                        wallpaper = wallpaper,
                        wallpaperPhoto = wallpaperPhoto,
                        wallpaperAlignX = wallpaperAlignX,
                        wallpaperAlignY = wallpaperAlignY,
                        wallpaperZoom = wallpaperZoom,
                        wallpaperOrigin = wallpaperOrigin,
                        fullWidth = fullWidth,
                        fullHeight = fullHeight,
                        notifications = notifications,
                        onLaunchChild = onLaunchFolderChild,
                        onEnterEdit = onLongPress,
                    )
                } else {
                    FolderTileContent(
                        tile = tile,
                        editMode = editMode,
                        launchEnabled = inlineFolderLaunch,
                        appIconColors = appIconColors,
                        wallpaperAccent = wallpaperAccent,
                        glass = glass,
                        transparency = transparency,
                        darkTheme = darkTheme,
                        tiledWallpaper = tiledWallpaper,
                        borderless = borderless,
                        notifications = notifications,
                        homeStyle = homeStyle,
                        iconShape = iconShape,
                        onLaunchChild = onLaunchFolderChild,
                        onOpenFolder = onTap,
                        onEnterEdit = onLongPress,
                    )
                }
            else -> Unit
        }
        // Per-app notification badge (FR-1.2). Top-right pill, count from the
        // notification listener; sized down on small tiles (prototype .badge).
        // App tiles only: a closed folder shows its consolidated total beside its
        // name label instead (so it never collides with the per-app cell badges),
        // and a widget stack shows only per-member badges (no consolidated total).
        if (badgeCount > 0 && tile is TileModel.App) {
            NotificationBadge(
                count = badgeCount,
                dark = darkTheme,
                small = tile.size == TileSize.SMALL,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        // Selected tiles show corner controls: folder/stack tiles get a folder icon
        // at top-left (tap expands it inline to manage members one-by-one), a
        // resize icon (drag it to resize the whole stack, homogenizing every
        // member — see onResizeStack), and a colour dot (opens the same picker
        // sheet that now also hosts the "show as stack"/"show as folder"
        // toggle); app tiles get the standard close icon instead of the folder
        // icon. A widget stack used to suppress the resize icon and colour dot
        // here — both are shown now, identically to a plain folder. None of
        // this applies to the expanded placeholder — there's nothing to
        // resize/recolour/unpin while it's just a collapse affordance.
        if (selected && !isExpanded) {
            when {
                tile is TileModel.Folder -> TileControls(showColor = showColorDot, dotColor = accent, isFolder = true)
                else -> TileControls(showColor = showColorDot, dotColor = accent)
            }
        }
    }
}

/** Corner hit-zone for [tileStretchGesture] — matches the feed widget stack's own
 *  edge-drag zone constant, a comfortable size for a drag (not a tap). */
private const val RESIZE_CORNER_ZONE_DP = 40

/**
 * Gesture-based tile resize: press and drag from the tile's own bottom-right
 * corner (a [RESIZE_CORNER_ZONE_DP] zone, past touch slop) to stretch from
 * that corner with the opposite corner anchored — the classic widget-resize
 * feel, with no drawn handle at all. (An earlier version of this also
 * supported a two-finger stretch gesture anywhere on the tile; dropped after
 * on-device testing found the corner drag alone was the one that actually
 * felt right, and having both added a mode-switching state machine for a
 * gesture nobody used.)
 *
 * Reports only the *raw* pixel delta since the drag started; the caller
 * (hoisted resize-preview state at the grid level, which has the geometry)
 * turns that into a snapped [TileSize] and only writes it on drag end — this
 * modifier knows nothing about grid cells at all.
 *
 * Deliberately never consumes a touch outside the corner zone, or a touch
 * inside it that never moves past slop — so a press anywhere else on the tile
 * passes straight through to the grid's own tap-to-select/reorder-drag
 * machinery (attached on an ancestor, see `editDragGesture`) exactly as
 * before, and a plain *tap* in the corner zone still reaches the grid's
 * existing tap-cycle resize rather than being swallowed here.
 */
internal fun Modifier.tileStretchGesture(
    onDragStart: () -> Unit,
    onDragBy: (dxPx: Float, dyPx: Float) -> Unit,
    onDragEnd: () -> Unit,
) = pointerInput(Unit) {
    val cornerZonePx = RESIZE_CORNER_ZONE_DP.dp.toPx()
    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)
        val inCornerZone = firstDown.position.x >= size.width - cornerZonePx &&
            firstDown.position.y >= size.height - cornerZonePx
        if (!inCornerZone) return@awaitEachGesture

        var dragging = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == firstDown.id } ?: break
            if (!change.pressed) {
                if (dragging) onDragEnd()
                break
            }
            if (!dragging) {
                val movedFromStart = (change.position - firstDown.position).getDistance()
                if (movedFromStart > viewConfiguration.touchSlop) {
                    dragging = true
                    onDragStart()
                }
            }
            if (dragging) {
                val dx = change.position.x - change.previousPosition.x
                val dy = change.position.y - change.previousPosition.y
                onDragBy(dx, dy)
                change.consume()
            }
        }
    }
}

/**
 * The face a folder tile shows while inline-expanded (FR-4 WP-style): an
 * up-arrow — tapping it, or the surrounding background, collapses the section
 * back (same [TileView.onTap] every tile already has) — plus the folder's
 * name. Renaming is reached by tapping the name text specifically, and is
 * deliberately independent of edit mode: it's a small, local toggle (not the
 * whole-grid jiggle/drag chrome), so tapping the name never dims/jiggles every
 * other tile on Start the way entering edit mode would. The name label
 * consumes its own tap (see [TileLabel]'s `clickable`) so it doesn't also
 * collapse the tile via the outer tap-to-collapse.
 */
@Composable
private fun FolderExpandedPlaceholder(
    name: String,
    onRename: (String) -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = TileIcons["chevron"],
                contentDescription = null,
                tint = LocalTileFaceColor.current,
                modifier = Modifier.size(28.dp).rotate(-90f),
            )
        }
        if (renaming) {
            FolderNameEditor(
                initial = name,
                onCommit = { newName -> renaming = false; onRename(newName) },
            )
        } else if (name.isNotBlank()) {
            TileLabel(
                name,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { renaming = true },
            )
        }
    }
}

/**
 * A small row of dots at the top of the screen — one per Start block page,
 * the active one drawn larger/more opaque — so there's some always-visible
 * cue that Start has more than one page, now that a page's own name is
 * hidden outside edit mode. Purely a static "you are here" marker (doesn't
 * track a live drag mid-swipe); [activeIndex] is expected already clamped
 * into `0 until count`.
 */
@Composable
private fun PageDotsIndicator(count: Int, activeIndex: Int, tint: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val active = i == activeIndex
            Box(
                modifier = Modifier
                    .size(if (active) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = if (active) 0.9f else 0.35f)),
            )
        }
    }
}

/**
 * The dragged tile's own floating stand-in during a cross-page carry (see
 * `onCrossPageDragPosition`) — its real rendering lives inside its source
 * page's `DenseTileGrid`, invisible once that page slides off-screen mid-
 * carry, so without this the tile would appear to vanish the instant it
 * crosses the edge even though the user is still actively holding it.
 * Deliberately a simplified stand-in, not full parity with [TileView] (no
 * live faces, badges, or folder mini-grid) — good enough to see and track
 * what's being placed without re-deriving that whole rendering pipeline for
 * a tile that's already off its own page's grid.
 */
@Composable
private fun CrossPageDragGhost(
    model: TileModel,
    offsetPx: Offset,
    resizeGeom: GridGeometry,
    accent: Color,
    accentId: String,
    wallpaperAccent: Color?,
) {
    val tileOverride = when (model) {
        is TileModel.App -> model.accentOverride
        is TileModel.Folder -> model.accentOverride
    }
    val tileAccent = when {
        tileOverride != null -> TileAccents.colorForOverride(tileOverride, accentId)
        wallpaperAccent != null -> wallpaperAccent
        else -> accent
    }
    val sizePx = resizeGeom.sizePx(TilePlacement(model.id, model.size, 0, 0))
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { IntOffset(offsetPx.x.roundToInt(), offsetPx.y.roundToInt()) }
            .size(with(density) { sizePx.width.toDp() }, with(density) { sizePx.height.toDp() })
            .zIndex(20f)
            .alpha(0.92f)
            .clip(RoundedCornerShape(10.dp))
            .background(tileAccent),
        contentAlignment = Alignment.Center,
    ) {
        val iconKey = when (model) {
            is TileModel.App -> model.iconKey
            is TileModel.Folder -> "folder"
        }
        Icon(
            imageVector = TileIcons[iconKey],
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * The unsectioned ("main") page's own plain label header — shown only in
 * edit mode, alongside every named page's own [SectionHeader] (a page's name
 * is otherwise hidden, identified purely by swiping to it). Deliberately not
 * the full interactive [SectionHeader]: there's no real `Section` row behind
 * "main" to rename/reorder/delete. Matches [SectionHeader]'s own label
 * styling/divider exactly, just without any of its edit-mode controls.
 */
@Composable
private fun UnsectionedHeader(textColor: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SECTION_HEADER_HEIGHT_DP.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = UNSECTIONED_LABEL.lowercase(),
                color = textColor.copy(alpha = 1f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .height(1.dp)
                .background(textColor.copy(alpha = 0.12f)),
        )
    }
}

/**
 * A named page's header row: the label (tap-to-rename via
 * [SectionNameEditor], edit mode only), and — edit mode only — ←/→ reorder
 * and a remove ("close", ungroups the page's tiles rather than deleting
 * them) action, each a small ghost icon button rather than a bare glyph
 * (user-requested: "can [this] be more better design wise"). Only ever shown
 * while editing — the page's name is otherwise hidden, identified purely by
 * swiping to it. A thin low-opacity divider under the row separates it from
 * the tiles below without a heavy box.
 */
@Composable
private fun SectionHeader(
    label: String,
    editMode: Boolean,
    textColor: Color,
    // Summed notification badge across every tile currently in this section.
    badgeCount: Int,
    darkTheme: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onRename: (String) -> Unit,
) {
    var renaming by remember(label) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SECTION_HEADER_HEIGHT_DP.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (renaming) {
                SectionNameEditor(
                    initial = label,
                    textColor = textColor,
                    onCommit = { newLabel ->
                        renaming = false
                        if (newLabel.isNotBlank() && newLabel != label) onRename(newLabel)
                    },
                    onCancel = { renaming = false },
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label.lowercase(),
                        // Full opacity + bold + noticeably larger than the
                        // reorder/remove controls (which keep the passed,
                        // more muted textColor) — the section's own name is
                        // the one thing in this row that should read as a
                        // real heading, not another small icon (user-
                        // requested: "can section names be made more
                        // prominent").
                        color = textColor.copy(alpha = 1f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp,
                        modifier = if (editMode) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { renaming = true },
                            )
                        } else {
                            Modifier
                        },
                    )
                    if (badgeCount > 0) {
                        NotificationBadge(
                            count = badgeCount,
                            dark = darkTheme,
                            small = true,
                            cornerInset = false,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
        if (editMode && !renaming) {
            SectionHeaderIconButton(
                iconKey = "chevron",
                rotationDegrees = 180f,
                tint = textColor,
                contentDescription = "move page earlier",
                onClick = onMoveEarlier,
            )
            SectionHeaderIconButton(
                iconKey = "chevron",
                rotationDegrees = 0f,
                tint = textColor,
                contentDescription = "move page later",
                onClick = onMoveLater,
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .height(1.dp)
            .background(textColor.copy(alpha = 0.12f)),
    )
    }
}

/** A small ghost-circle icon button for a [SectionHeader]'s edit-mode
 *  reorder/remove actions — reuses the [TileIcons] chevron glyph rotated for
 *  up/down, and "close" for remove, rather than bare text glyphs. */
@Composable
private fun SectionHeaderIconButton(
    iconKey: String,
    rotationDegrees: Float,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .size(26.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TileIcons[iconKey],
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(13.dp).rotate(rotationDegrees),
        )
    }
}

/**
 * Section name entry (the header's tap-to-rename):
 * a text field with an explicit "✓" confirm and "✕" cancel, rather than
 * relying only on the keyboard's own Done action or losing focus to commit —
 * on a real device, dismissing the keyboard (e.g. the system back button)
 * only hides the IME, it does not reliably move Compose focus away from the
 * field, so a focus-loss-only commit could leave a typed name stuck with no
 * way to confirm it (confirmed: the field showed the typed text, but no
 * section was ever actually written to the database).
 *
 * [finished] guards against a real double-commit bug: tapping "✓" both fires
 * its own `onClick` *and* moves focus away from the text field, and losing
 * focus was *also* wired to commit — so a single tap on "✓" fired `onCommit`
 * twice, creating two sections with the same name (user-reported). Once
 * either `commit()` or `cancel()` runs, every other trigger this composable
 * still has pending (a focus-change callback that hasn't fired yet, a second
 * tap) becomes a no-op.
 */
@Composable
private fun SectionNameEditor(
    initial: String,
    textColor: Color,
    onCommit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var draft by remember {
        mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length)))
    }
    var finished by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    fun commit() {
        if (finished) return
        finished = true
        onCommit(draft.text)
    }
    fun cancel() {
        if (finished) return
        finished = true
        onCancel()
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = TextStyle(color = textColor, fontSize = 13.sp),
            cursorBrush = SolidColor(textColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus),
        )
        Text(
            text = "✓",
            color = textColor,
            fontSize = 16.sp,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::commit,
                ),
        )
        Text(
            text = "✕",
            color = textColor,
            fontSize = 14.sp,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = ::cancel,
            ),
        )
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/**
 * "add page" as a real modal dialog (user-requested, replacing an earlier
 * inline-swap-the-button-for-a-text-field design) — a deliberate "create"
 * step that a stray tap elsewhere can't silently dismiss, matching the
 * "remove page & tiles?" confirmation dialog's own weight for the opposite
 * action. "add" is disabled on a blank name rather than silently no-oping,
 * so there's no way to create a nameless page from here.
 */
@Composable
private fun AddPageDialog(onCommit: (String) -> Unit, onCancel: () -> Unit) {
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }
    val textColor = LocalTextStyle.current.color
    fun commit() {
        val trimmed = draft.text.trim()
        if (trimmed.isNotEmpty()) onCommit(trimmed)
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("add page") },
        text = {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = TextStyle(color = textColor, fontSize = 15.sp),
                cursorBrush = SolidColor(textColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { innerField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        if (draft.text.isEmpty()) {
                            Text("page name", color = textColor.copy(alpha = 0.5f), fontSize = 15.sp)
                        }
                        innerField()
                    }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = ::commit, enabled = draft.text.isNotBlank()) { Text("add") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("cancel") }
        },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Inline rename field for [FolderExpandedPlaceholder] — small-tile-sized
 *  variant of the old overlay's title editor (same commit-on-done behaviour). */
@Composable
private fun FolderNameEditor(initial: String, onCommit: (String) -> Unit) {
    // TextFieldValue (not a plain String) so the initial selection can be
    // placed at the end of the text — a bare String defaults the cursor to
    // the start, which made backspace delete nothing until the user first
    // tapped/dragged the caret into place.
    var draft by remember {
        mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length)))
    }
    val focus = remember { FocusRequester() }
    BasicTextField(
        value = draft,
        onValueChange = { draft = it },
        singleLine = true,
        textStyle = TextStyle(
            color = LocalTileFaceColor.current,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(LocalTileFaceColor.current),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(draft.text) }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .onFocusChanged { if (!it.isFocused && draft.text != initial) onCommit(draft.text) },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/**
 * The prototype `.badge`: a rounded count pill in the tile's top-right corner.
 * White on dark themes, inverted on light (`#screen.light .badge`). Shrinks on
 * small tiles. Counts over 99 read "99+" so the pill keeps its shape.
 */
@Composable
internal fun NotificationBadge(
    count: Int,
    dark: Boolean,
    small: Boolean,
    modifier: Modifier = Modifier,
    cornerInset: Boolean = true,
    // Scales the badge continuously to a specific diameter instead of the
    // plain small/large choice [small] gives — for a "show as icon" tile
    // whose icon can be anywhere from 40dp to 120dp+, a fixed-size badge
    // reads as too small on the bigger end (user-reported: "should be
    // proportionate"). Every existing caller leaves this null and keeps the
    // original fixed 18dp/22dp look exactly as before.
    sizeOverride: Dp? = null,
) {
    val bg = if (dark) Color.White else Color(0xFF111111)
    val fg = if (dark) Color(0xFF111111) else Color.White
    val diameter = sizeOverride ?: if (small) 18.dp else 22.dp
    val inset = sizeOverride?.let { it * 0.25f } ?: if (small) 5.dp else 8.dp
    val fontSize = sizeOverride?.let { (it.value * 0.5f).sp } ?: if (small) 11.sp else 13.sp
    Box(
        modifier = modifier
            .then(if (cornerInset) Modifier.padding(top = inset, end = inset) else Modifier)
            .defaultMinSize(minWidth = diameter, minHeight = diameter)
            .height(diameter)
            .background(bg, CircleShape)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = fg,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * A tiny per-app count dot for one cell of a closed folder's mini-grid
 * (`FolderTileContent`) — same white/dark-inverted pill as [NotificationBadge],
 * scaled down further to fit inside an icon-sized cell.
 */
@Composable
internal fun FolderChildBadge(count: Int, dark: Boolean, modifier: Modifier = Modifier) {
    val bg = if (dark) Color.White else Color(0xFF111111)
    val fg = if (dark) Color(0xFF111111) else Color.White
    Box(
        modifier = modifier
            .padding(top = 1.dp, end = 1.dp)
            .defaultMinSize(minWidth = 12.dp, minHeight = 12.dp)
            .background(bg, CircleShape)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 9) "9+" else count.toString(),
            color = fg,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Corner controls shown on the selected tile in edit mode (prototype
 * `.tile-controls`): top-left shows a close icon for app tiles or a folder icon
 * for folder tiles (opens the overlay to pull apps out one-by-one); a
 * corner-arc drag-resize handle is bottom-right (the same glyph used for
 * drag-resize on Quick Panel tiles and glance cards); and — for app tiles
 * ([showColor]) — a colour dot bottom-left. These are the visual affordance;
 * the taps are handled by the grid's [editDragGesture] corner hot-zones
 * (FR-3.4/3.5/7), and the resize drag itself by [tileStretchGesture].
 */
@Composable
internal fun BoxScope.TileControls(
    showColor: Boolean,
    dotColor: Color,
    isFolder: Boolean = false,
) {
    TileControl(
        iconKey = if (isFolder) "folder" else "close",
        description = if (isFolder) "open folder" else "unpin",
        modifier = Modifier.align(Alignment.TopStart),
    )
    CornerArcGlyph(
        tint = LocalTileFaceColor.current,
        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(20.dp),
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


@Composable
private fun TileControl(iconKey: String, description: String, modifier: Modifier) {
    // No background chip — the close/resize glyphs sit directly on the tile's own
    // fill, tinted to match the tile's icon/label.
    Box(
        modifier = modifier.size(26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TileIcons[iconKey],
            contentDescription = description,
            tint = LocalTileFaceColor.current,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Bottom edit bar (prototype `.edit-bar`): add / add widgets / personalize /
 * done, sliding up from below while editing. add → app list (with a hint
 * toast), add widgets → the widget catalog sheet, personalize → the
 * personalize sheet, done → exit edit.
 */
@Composable
private fun EditBar(
    visible: Boolean,
    onAdd: () -> Unit,
    onAddWidgets: () -> Unit,
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
        EditBarButton("widgets", "add live tiles", enabled = true, onClick = onAddWidgets)
        Spacer(Modifier.size(34.dp))
        EditBarButton("settings", "personalize", enabled = true, onClick = onPersonalize)
        Spacer(Modifier.size(34.dp))
        EditBarButton("check", "done", enabled = true, onClick = onDone)
    }
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
 * Exits edit mode when [active] and the user taps empty space (a tap that
 * does not move past the 7 px slop) — relies on corner-control taps
 * ([editDragGesture]) consuming their own touch so this can tell "a control
 * was tapped" from "this is empty space." Non-consuming and inactive
 * otherwise, so it never interferes with launching or scrolling.
 */
private fun Modifier.emptySpaceExit(active: Boolean, onExit: () -> Unit): Modifier =
    pointerInput(active) {
        if (!active) return@pointerInput
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
 * Fires [onExit] when [active] (a folder is inline-expanded and not editing)
 * and the user taps genuinely empty grid space — collapses the folder the
 * same "tap outside to dismiss" way [emptySpaceExit] exits edit mode, but
 * can't reuse it: a plain tile's own [tileGesture] deliberately never
 * consumes its touch (so grid scroll still wins on a drag), so there's no
 * "consumed" signal to tell "a tile is here" from "this is empty space" the
 * way [editDragGesture]'s corner controls provide one. Instead this hit-tests
 * the tap's position against the actual tile placements (same
 * pack/packSticky + postProcess pipeline [DenseTileGrid] renders, recomputed
 * here only when a tap actually happens — not on every recomposition) and
 * only fires when nothing is there. Firing unconditionally (the earlier,
 * buggy version) raced every tile's own tap handler and could consume the
 * very tap meant to launch a folder child or an outside tile.
 */
private fun Modifier.folderCollapseOnEmptyTap(
    active: Boolean,
    // Start-screen "sections": every currently-rendered tile's absolute
    // content-space rect (already offset per its own block — see
    // [StartPage]'s `absoluteTileRects`), rather than re-deriving one flat
    // pack here — a plain pack over every block's tiles as if they were one
    // grid would put tiles below the first block at the wrong row once
    // section headers/gaps are involved.
    absoluteTileRects: List<Pair<String, Rect>>,
    onExit: () -> Unit,
): Modifier = pointerInput(active, absoluteTileRects) {
    if (!active) return@pointerInput
    val slop = 7.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var moved = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.isConsumed) break // a tile (or its drag) owns this touch
            if ((change.position - down.position).getDistance() > slop) moved = true
            if (!change.pressed) {
                if (!moved) {
                    if (absoluteTileRects.none { it.second.contains(down.position) }) onExit()
                }
                break
            }
        }
    }
}

/**
 * Long-press on genuinely empty Start-grid space enters edit mode with
 * nothing selected — the add/add-widgets/personalize/done toolbar until now
 * only ever appeared from a per-tile long-press ([tileGesture]), which never
 * covers the gaps between tiles. Same 600 ms/7 px timing as [tileGesture]'s
 * own long-press. Hit-tests the *down* position against the real tile
 * placements (same pack/packSticky + postProcess pipeline as
 * [folderCollapseOnEmptyTap]) and bails out immediately when it lands on a
 * tile — [tileGesture] owns that touch, and starting our own timer there too
 * would race it (a plain tile never consumes its down, so both gestures
 * would otherwise see the same stream).
 *
 * This modifier sits on the outer, unscrolled Box (so it also covers the
 * area below the last row), while [GridGeometry]/[tileAt] work in grid
 * *content* space — the same content space [contentTopPx]/[scrollOffsetPx]
 * already convert to/from at the call site's other scroll-aware gestures
 * (e.g. the active page's own `blockScrollStates[blockIndex].value` a few
 * lines up). A touch's raw position must get the same conversion here, or hit-testing
 * silently drifts by the current scroll offset the moment the grid has been
 * scrolled at all — confirmed live on an emulator (an on-screen gap hit an
 * unrelated tile below it once scrolled).
 */
private fun Modifier.emptySpaceEnterEdit(
    active: Boolean,
    // Same precomputed, already block-offset rects as [folderCollapseOnEmptyTap].
    absoluteTileRects: List<Pair<String, Rect>>,
    contentTopPx: Float,
    scrollOffsetPx: () -> Float,
    onEnterEdit: () -> Unit,
    // "Reachability" (see [StartPage]'s `reachabilityOffset`): a downward
    // drag from empty space, on the same touch this function already owns,
    // slides the active tab's own tiles down toward the thumb. Kept as one
    // recognizer rather than a second sibling pointerInput — two independent
    // `awaitFirstDown`s on the same region would race each other for the
    // same raw stream with no consumption to arbitrate between them. All
    // default to inert values, so with the gate off this function's
    // behaviour is unchanged from before reachability existed.
    reachabilityActive: Boolean = false,
    reachabilityHeld: Boolean = false,
    reachabilityOffsetPx: Float = 0f,
    maxReachabilityOffsetPx: Float = 0f,
    // Plain (non-suspend) callbacks: `awaitPointerEventScope` is a
    // restricted suspension scope that can't call arbitrary suspend
    // functions (e.g. `Animatable.animateTo`) directly — the call site
    // does any animating itself, off this restricted scope.
    onReachabilityDrag: ((Float) -> Unit)? = null,
    // true = settle held at the target offset; false = snap back to 0.
    onReachabilitySettle: ((Boolean) -> Unit)? = null,
    // A plain tap on empty space while already held.
    onReachabilityCollapse: (() -> Unit)? = null,
): Modifier = pointerInput(active, absoluteTileRects, reachabilityActive, reachabilityHeld) {
    if (!active) return@pointerInput
    val slop = 7.dp.toPx()
    val commitPx = 40.dp.toPx()
    awaitEachGesture {
        // Initial pass throughout: Compose dispatches the default Main pass
        // child-first (bottom-up), so the scrolling Column *inside* this Box
        // (see the call site) gets first look at a Main-pass drag and — even
        // clamped to zero scroll at its bounds — consumes it before this
        // outer recognizer would ever see an unconsumed event. Watching in
        // Initial pass (parent-first) instead means we decide first; we only
        // ever actually [consume] once a downward drag is confirmed, so a
        // genuine long-press-still or a disqualified drag still reaches the
        // scrollable exactly as before.
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val contentPos = down.position.copy(
            y = down.position.y - contentTopPx + scrollOffsetPx() - reachabilityOffsetPx,
        )
        val onTile = absoluteTileRects.any { it.second.contains(contentPos) }

        if (reachabilityHeld) {
            // Only a plain tap on empty space (not a tile — its own
            // [tileGesture] already handles that, including its own
            // spring-back) collapses the shift; anything else is left
            // alone rather than fought over.
            if (onTile) return@awaitEachGesture
            var moved = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) return@awaitEachGesture
                if ((change.position - down.position).getDistance() > slop) moved = true
                if (!change.pressed) {
                    if (!moved) onReachabilityCollapse?.invoke()
                    break
                }
            }
            return@awaitEachGesture
        }

        if (onTile) return@awaitEachGesture

        // Phase 1: race "held still 600ms" -> enter edit, against "moved
        // past slop" -> fall through to phase 2 instead of doing nothing.
        // The moment slop breaks on a genuinely DOWNWARD move, claim the
        // touch for reachability right there (before returning) if that's
        // active — this is the one event where the scrollable child would
        // otherwise get first crack at an unconsumed drag on its own next
        // (Main-pass) turn. Direction-gated (`movingDown`) — real bug, user-
        // reported: without this check, ANY slop-break (including an upward
        // "scroll down the page to see more" drag) was consumed here
        // whenever reachabilityActive was on, which is the common case now
        // that Start's blocks.size is almost always >= 2 (every install with
        // even one named section). Phase 2's own "bailed once reversed"
        // check happens one event too late to undo that already-dropped
        // event — losing just that one move is enough to stop the child
        // Column's own `verticalScroll` from ever recognizing the drag,
        // freezing scroll for the rest of that touch. Scrolling via a tile
        // was unaffected (a different gesture path, [tileGesture] below,
        // never consumes on drag).
        var enteredReachability = false
        val movedPastSlop = withTimeoutOrNull(600L) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null && change.isConsumed) return@withTimeoutOrNull false
                if (change == null || !change.pressed) return@withTimeoutOrNull false
                if ((change.position - down.position).getDistance() > slop) {
                    val movingDown = change.position.y > down.position.y
                    if (reachabilityActive && onReachabilityDrag != null && movingDown) {
                        change.consume()
                        enteredReachability = true
                    }
                    return@withTimeoutOrNull true
                }
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        when (movedPastSlop) {
            null -> {
                onEnterEdit()
                waitForUpOrCancellation()
                return@awaitEachGesture
            }
            false -> return@awaitEachGesture // released early, or consumed elsewhere
            true -> Unit // fall through to phase 2
        }
        if (!enteredReachability) return@awaitEachGesture

        // Phase 2: live-track the confirmed reachability drag, no time cap
        // (a slow swipe is still a swipe). Every event we accept is consumed
        // for the same reason as above — otherwise the scrollable child
        // keeps eating each subsequent move too.
        val onDrag = onReachabilityDrag ?: return@awaitEachGesture
        var offsetSoFar = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                onReachabilitySettle?.invoke(offsetSoFar > commitPx)
                return@awaitEachGesture
            }
            val delta = change.position - down.position
            // Deliberately lenient: only bail once the finger has moved back
            // above the start point (a real "not downward" signal). A single
            // early frame with some sideways wobble is normal for a real
            // swipe and must not permanently kill the gesture.
            if (delta.y <= 0f) {
                onReachabilitySettle?.invoke(offsetSoFar > commitPx)
                return@awaitEachGesture
            }
            change.consume()
            offsetSoFar = delta.y.coerceIn(0f, maxReachabilityOffsetPx)
            onDrag(offsetSoFar)
            if (!change.pressed) {
                onReachabilitySettle?.invoke(offsetSoFar > commitPx)
                return@awaitEachGesture
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
internal fun Modifier.tileGesture(
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
    // Start-screen "sections": how far down the scrollable content this
    // particular block's own grid starts (the sum of every earlier block's
    // header + grid height) — zero for the first block, matching today's
    // single-grid behaviour exactly whenever there are no sections at all.
    // [pos] below is local to *this* block's own Box, so the auto-scroll
    // edge check needs this to translate it back into real page-content
    // space before comparing against the viewport bounds.
    blockTopOffsetPx: Float = 0f,
    allowMerge: Boolean = true,
    // Windows-phone-style sticky (gap-preserving) arrangement: non-null switches
    // placement + drop mechanics (see below); null (the default) is the original
    // dense-repack behaviour, unchanged for every existing caller.
    slotOf: ((String) -> Int?)? = null,
    // TilePackMode.FREE specifically (only meaningful when [slotOf] is
    // non-null — STICKY and FREE are both "anchored" modes for placement, but
    // only STICKY previews other tiles pushing out of the way while
    // dragging). FREE's own live preview never displaces anyone; the dropped
    // tile's *actual* redirect-to-nearest-free-cell resolution happens once,
    // at release, in the ViewModel write path (GridPacker.freePlacement).
    freeMode: Boolean = false,
    onStickyDrop: (dragId: String, slot: Int?) -> Unit = { _, _ -> },
    // Live push-down preview while a sticky-mode drag is in progress — called
    // with every tile that would be displaced (plus the dragged tile's own
    // resolved cell) each time the hovered cell changes, and with an empty
    // map when a merge is entered (no push-down applies while merging).
    onStickyPreview: (Map<String, Int>) -> Unit = {},
    // Inline folder expansion (GridPacker.expandFolderInline) applied after the
    // normal pack/packSticky computation — null for every caller that doesn't
    // support expansion (unchanged behaviour).
    postProcess: ((List<TilePlacement>) -> List<TilePlacement>)? = null,
    // Dragging a folder child (a synthetic id, see parseFolderChildId) reorders
    // within the expanded folder's own block instead of the top-level
    // order/sticky-slot machinery above — children have their own persisted
    // position, independent of whichever top-level arrangement mode is active.
    // Both no-op by default for every caller that doesn't support expansion.
    onReorderFolderChildTo: (dragId: String, targetId: String) -> Unit = { _, _ -> },
    onFolderChildDrop: () -> Unit = {},
    // Fires once, at release, when a folder-child drag ended outside its own
    // folder's expanded block ([isInsideFolderBlock]) — i.e. the child was
    // pulled out onto the top-level grid rather than just reordered among its
    // siblings. No-op by default for every caller that doesn't support
    // expansion, same as the two callbacks above.
    onFolderChildPulledOut: (childId: String, target: FolderChildDropTarget) -> Unit = { _, _ -> },
    // Dense mode only (slotOf == null): live reflow while a pulled-out folder
    // child hovers a non-merge spot, splicing the synthetic child id into the
    // real top-level `order` at the hovered position so GridPacker.pack
    // repacks the surrounding real tiles around it — exactly what an ordinary
    // top-level reorder already gets for free from mutating `order` directly.
    // [beforeId] is the same "insert before this id, or append at the end
    // when null" convention [onReorderTo]/[insertBeforeTarget] already use.
    // No-op by default for every caller that doesn't support expansion, same
    // as the folder-child callbacks above.
    onFolderChildDensePreview: (childId: String, beforeId: String?) -> Unit = { _, _ -> },
    // Reverts the splice above — called the moment the drag crosses back into
    // the folder block, enters a merge, or the gesture ends for any reason, so
    // the synthetic id never lingers in `order` past the gesture.
    onFolderChildDensePreviewClear: (childId: String) -> Unit = {},
    // Carry a top-level tile onto a neighboring page by dragging it to the
    // screen edge — the drag-based counterpart to the per-tile colour
    // picker's "move to page" chip. The page shift ([onCrossPageShift])
    // fires immediately on first crossing into the zone, no dwell/hold
    // required; the actual move ([onCrossPageDrop]) only commits at
    // release, so where it lands can still be adjusted after the shift. 0
    // (the default) disables the whole feature for any caller that doesn't
    // opt in, matching every other inert-by-default param above. A folder
    // child is out of scope (checked at the call site via
    // [parseFolderChildId]) — it moves with its folder.
    crossPageEdgeZonePx: Float = 0f,
    onCrossPageShift: (direction: Int) -> Unit = {},
    // targetSlot is where on the destination page to land — computed from
    // wherever the touch was at release, converted into that page's own
    // coordinate space (every page shares identical grid geometry, so a
    // fixed page-width offset is all the conversion needs — see the call
    // site), not left to whatever the destination page's placement engine
    // would auto-pick.
    onCrossPageDrop: (dragId: String, direction: Int, targetSlot: Int) -> Unit = { _, _, _ -> },
    // This block's own fixed page index and a *live* reader of the pager's
    // position (not a snapshot — this runs inside a suspend gesture loop,
    // recomposition doesn't re-supply it) — together these let the floating
    // drag ghost track the true, continuously-correct on-screen position
    // throughout the page-shift animation and the aim-after-shift hold that
    // follows it, expressed relative to the shared outer Box every block
    // page's own translationX is already relative to (see the call site).
    blockIndex: Int = 0,
    livePagerProgress: () -> Float = { 0f },
    // Reports the dragged tile's own live offset (relative to that same
    // shared outer Box) once a cross-page carry is under way, so a floating
    // visual can track it after its source page slides off-screen — null
    // both before crossing and again once released.
    onCrossPageDragPosition: (tileId: String?, offset: Offset?) -> Unit = { _, _ -> },
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

    fun placementsNow(): List<TilePlacement> {
        val specs = order.mapNotNull { id -> byId[id]?.let { TileSpec(id, it.size) } }
        val base = slotOf?.let { GridPacker.packSticky(specs, it, columns) } ?: GridPacker.pack(specs, columns)
        return postProcess?.invoke(base) ?: base
    }

    // The other tiles packed *without* [exclude] (the dragged tile). Because a
    // drag only ever moves the dragged tile within the order, this layout is
    // invariant for the whole gesture — so a merge target never slips out from
    // under the finger the way it does in the dragged-included layout.
    fun othersPacked(exclude: String): List<TilePlacement> {
        val specs = order.filter { it != exclude }
            .mapNotNull { id -> byId[id]?.let { TileSpec(id, it.size) } }
        val base = slotOf?.let { GridPacker.packSticky(specs, it, columns) } ?: GridPacker.pack(specs, columns)
        return postProcess?.invoke(base) ?: base
    }

    // Sticky-mode drop bound (real-bug fix): a drop can anchor no further
    // down than immediately after this block's own current last row —
    // never further, however far past this block's own (often much smaller
    // than the screen, once sections split one grid into several) rendered
    // area the finger travels. Without this, dragging past a small
    // section's tiny content computed an enormous row straight from the raw
    // pointer position, anchoring the tile dozens of rows down and leaving
    // a large empty gap above it (user-reported: "it created a big space").
    fun stickyDropRowBound(exclude: String): Int =
        othersPacked(exclude).maxOfOrNull { it.row + it.rows } ?: 0

    // Same as [othersPacked], but in sticky mode packed from each tile's real
    // persisted `gridSlot` only — never the live [slotOf] (which reads the
    // in-progress push-down preview). Merge-target hit-testing must use this:
    // othersPacked() is *not* actually invariant in sticky mode the way the
    // comment above claims — the live preview it packs with is being rewritten
    // by this very gesture's own push-down computation (see the `else` branch
    // below), so a tile the drag brushed past earlier can still be sitting
    // displaced when the finger later lines up over its true position, and the
    // merge zone check would miss it entirely. Packing from persisted slots
    // only guarantees every target's hitbox stays exactly where it visually
    // belongs, regardless of what the preview is doing elsewhere.
    fun othersPackedStable(exclude: String): List<TilePlacement> {
        val specs = order.filter { it != exclude }
            .mapNotNull { id -> byId[id]?.let { TileSpec(id, it.size) } }
        val stableSlotOf: ((String) -> Int?)? = if (slotOf != null) ({ id -> byId[id]?.gridSlot }) else null
        val base = stableSlotOf?.let { GridPacker.packSticky(specs, it, columns) } ?: GridPacker.pack(specs, columns)
        return postProcess?.invoke(base) ?: base
    }

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
            // Each corner check must also confirm the tap actually landed
            // *inside* the selected tile's own rect — otherwise a one-sided
            // threshold like "x <= r.left + zone" is satisfied by any point up
            // and to the left of that corner, however far away, including taps
            // on a completely different tile. That let tapping another tile
            // (especially one above/left of the selected one) misfire unpin,
            // resize, or the colour picker on the *previously* selected tile
            // instead of switching the selection to the tapped one.
            val inTile = r.contains(down.position)
            val inUnpin = inTile && down.position.x <= r.left + zone && down.position.y <= r.top + zone
            val inResize = inTile && down.position.x >= r.right - zone && down.position.y >= r.bottom - zone
            val inColor =
                inTile && down.position.x <= r.left + zone && down.position.y >= r.bottom - zone
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
        // Sticky mode only: the free cell currently under the dragged tile's
        // top-left corner, or null when that cell is occupied (invalid drop).
        var pendingSlot: Int? = null
        // Folder-child drag only: whether the *current* tick's point has moved
        // outside the folder's own expanded block (see isInsideFolderBlock) —
        // recomputed fresh every tick, so whatever it reads at release is
        // simply "where the finger was when it let go." The three fields below
        // it record which kind of top-level target (if any) is currently
        // hovered in that pulled-out state, mirroring dwellId/mergeId/pendingSlot's
        // role for an ordinary top-level drag but kept separate from them since
        // a folder child was never part of `order`/the sticky anchors to begin
        // with.
        var pulledOut = false
        var pulledOutMergeId: String? = null
        var pulledOutTargetId: String? = null
        var pulledOutSlot: Int? = null
        // Dense mode only: the top-level id the pulled-out child is currently
        // spliced-in before (or null when spliced at the very end, or when
        // nothing has been spliced yet) — mirrors [lastTarget]'s
        // change-detection role so the splice is only recomputed when the
        // hovered target actually changes, not every tick.
        var pulledOutDenseTarget: String? = null
        var pulledOutDenseSpliced = false
        // Dwell tracking for the pulled-out merge zone, mirroring dwellId/
        // dwellAnchor/dwellStartMs above (kept separate since the two systems
        // are mutually exclusive within one gesture — allowMerge is false
        // whenever a folder is expanded, which is required to drag a folder
        // child at all — but sharing state across two conceptually different
        // checks would be fragile).
        var pulledOutDwellId: String? = null
        var pulledOutDwellAnchor = Offset.Zero
        var pulledOutDwellStartMs = 0L
        // Cross-page carry: the page SHIFT fires once, immediately the first
        // tick the dragged tile's own centre crosses into
        // [crossPageEdgeZonePx] of a screen edge — no dwell/hold requirement.
        // The actual COMMIT (section + slot) still waits for release, so the
        // drop position can keep being adjusted after the shift instead of
        // freezing the instant it crosses (see docs/DECISIONS.md).
        // [crossPageDirection] is which edge triggered it (-1 left, 1 right),
        // fixed for the rest of the gesture once set.
        var crossPageTriggered = false
        var crossPageDirection = 0

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                // The pointer vanished without ever delivering a normal
                // "finger up" (change.pressed == false) — e.g. the system's
                // own edge-back gesture claiming the touch, which a
                // cross-page drag's edge-hold makes far more likely to hit
                // than an ordinary same-page drag. The cross-page ghost
                // (CrossPageDragGhost, driven by onCrossPageDragPosition) is
                // only ever cleared from the `!change.pressed` release branch
                // below, so losing the pointer any other way left it floating
                // on screen forever — user-reported: "drag ... two or more
                // pages at a time then it doesn't move and the visual keeps
                // hanging".
                if (crossPageTriggered) onCrossPageDragPosition(null, null)
                break
            }
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
                // Cross-page carry: fires once, the instant the dragged
                // tile's own centre first crosses into [crossPageEdgeZonePx]
                // of a screen edge — no dwell/hold required, the page shifts
                // but the page shift itself fires only once (see
                // docs/DECISIONS.md). Checked before consuming/dragging
                // further so every branch below (merge/reorder/sticky/
                // auto-scroll) is skipped from that point on — they're all
                // scoped to this page's own tiles, meaningless once this
                // tile is on its way to a different one.
                if (!crossPageTriggered && crossPageEdgeZonePx > 0f &&
                    startId != null && parseFolderChildId(startId) == null
                ) {
                    val dragCentreX = (pos - grab).x + dragHalf.x
                    val dir = when {
                        dragCentreX < crossPageEdgeZonePx -> -1
                        dragCentreX > widthPx - crossPageEdgeZonePx -> 1
                        else -> 0
                    }
                    if (dir != 0) {
                        crossPageTriggered = true
                        crossPageDirection = dir
                        // The vertical auto-scroll check below never runs
                        // again once triggered (the branch right after this
                        // one `continue`s past it) — stop it explicitly here
                        // too, or it keeps scrolling whatever page is active
                        // for the rest of the hold if it happened to already
                        // be running the instant this fires (real bug, user-
                        // reported: "tile ... still placed at bottom" — the
                        // destination page had auto-scrolled itself to the
                        // bottom during the aim-after-shift hold before the
                        // eventual release).
                        onAutoScroll(0)
                        onCrossPageShift(dir)
                    }
                }

                if (crossPageTriggered) {
                    // Still the same touch, still tracked by this page's own
                    // gesture (Compose ties an in-progress touch to whichever
                    // node first claimed it — there's no handing it to the
                    // destination page's own grid instance) — but every
                    // other page shares this page's exact column/gap/width,
                    // so a fixed offset of one page-width per page crossed
                    // converts this touch's own local x into "as if it were
                    // already on the destination page," with no dependency
                    // on the shift animation's live progress at all (that
                    // term cancels out algebraically — see DECISIONS.md).
                    // Real placement is computed once, at release, from
                    // wherever the touch is then — this is what lets it keep
                    // being aimed after the page has already shifted, instead
                    // of freezing the moment it crosses the edge.
                    change.consume()

                    // The floating ghost's own live position, expressed
                    // relative to the shared outer Box every block page's
                    // translationX is already relative to — this DOES need
                    // the live pager position (unlike the release-time slot
                    // math above), since the ghost must visually track the
                    // page-shift animation itself, not just its end state.
                    val liveTranslationX = widthPx * (blockIndex - livePagerProgress())
                    onCrossPageDragPosition(startId, Offset(liveTranslationX + (pos - grab).x, (pos - grab).y))

                    if (!change.pressed) {
                        val startIdSnapshot = startId
                        if (startIdSnapshot != null) {
                            val correctedTopLeft = (pos - grab).copy(
                                x = (pos - grab).x - widthPx * crossPageDirection,
                            )
                            val widthCols = byId[startIdSnapshot]?.size?.cols ?: 1
                            val cell = geom.cellAt(correctedTopLeft, columns, widthCols)
                            onCrossPageDrop(startIdSnapshot, crossPageDirection, GridPacker.encodeSlot(cell.x, cell.y))
                        }
                        onCrossPageDragPosition(null, null)
                        break
                    }
                    continue
                }

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
                    othersPackedStable(drag).firstOrNull { geom.rect(it).contains(dragCentre) }
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
                        // Sticky mode: clear any live push-down preview the instant
                        // dwelling starts — leaving it applied (or letting the
                        // sticky-preview branch below keep recomputing it while
                        // *not yet* dwelled long enough) displaces the very tile
                        // being hovered. That moves its packed rect out from under
                        // the drag centre on the next tick, `hovered` stops
                        // matching, and the dwell timer resets before it can ever
                        // reach [mergeDwellMs] — merge-to-folder was silently
                        // unreachable in sticky mode because of this feedback loop.
                        if (slotOf != null) onStickyPreview(emptyMap())
                    }
                } else {
                    dwellId = null
                }
                val dwelled = inCentre &&
                    change.uptimeMillis - dwellStartMs >= mergeDwellMs
                val mergeNow = inCentre && (dwelled || mergeId == hovered!!.id)

                if (inCentre && startId != null) {
                    // Dwelling toward a merge (or already merging): never fall
                    // through to the sticky-preview/reorder branch below while the
                    // centre sits in a target's merge zone, for the same reason
                    // the preview is cleared above — recomputing it here would
                    // reintroduce the feedback loop tick-by-tick instead of just
                    // once at dwell start.
                    lastTarget = null
                    if (mergeNow && mergeId != hovered!!.id) {
                        mergeId = hovered.id
                        onMergeTarget(hovered.id)
                        onMergeMode(startId)
                    }
                } else {
                    if (mergeId != null) { mergeId = null; onMergeTarget(null) }
                    val folderChildInfo = startId?.let(::parseFolderChildId)
                    if (folderChildInfo != null && startId != null) {
                        val dragFolderId = folderChildInfo.first
                        val placements = placementsNow()
                        val insideBlock = isInsideFolderBlock(placements, geom, dragFolderId, pos) { id ->
                            // Excludes the dragged child's own id: once the
                            // dense-mode live-splice below is active, [startId]
                            // gains a real placement of its own (spliced into
                            // `order`) that naturally tracks close to wherever
                            // it's currently hovering — without this exclusion
                            // that self-placement would trip "inside the
                            // block" the instant it's spliced in, immediately
                            // reverting the very splice that made it true, in
                            // an endless splice/unsplice flicker.
                            id != startId && parseFolderChildId(id)?.first == dragFolderId
                        }
                        if (insideBlock) {
                            if (pulledOut) {
                                // Just crossed back into the block: tear down
                                // whichever pulled-out preview was showing so it
                                // doesn't linger once we're back to a plain
                                // sibling reorder.
                                pulledOut = false
                                if (pulledOutMergeId != null) { pulledOutMergeId = null; onMergeTarget(null) }
                                if (slotOf != null) onStickyPreview(emptyMap())
                                if (pulledOutDenseSpliced) {
                                    onFolderChildDensePreviewClear(startId)
                                    pulledOutDenseSpliced = false
                                }
                                pulledOutTargetId = null
                                pulledOutSlot = null
                                pulledOutDwellId = null
                                pulledOutDenseTarget = null
                            }
                            // In-folder reorder: children have their own persisted
                            // order, independent of whichever top-level arrangement
                            // mode is active — swap within the expanded folder's own
                            // block instead of touching the top-level order or
                            // computing a sticky push-down preview.
                            val target = placements.firstOrNull {
                                it.id != startId && parseFolderChildId(it.id) != null && geom.rect(it).contains(pos)
                            }
                            if (target != null && target.id != lastTarget) {
                                lastTarget = target.id
                                onReorderFolderChildTo(startId, target.id)
                            }
                        } else {
                            // Pulled out onto the top-level grid: the exact same
                            // merge-zone check (inner 22-78% band) an ordinary
                            // top-level drag uses, judged from the same floating
                            // tile's centre computed above — then, when not
                            // merging, the same dense-hit-test / sticky push-down
                            // preview a top-level drop into that spot would show,
                            // restricted to real top-level tiles (a folder child
                            // is never a valid target for another folder child).
                            pulledOut = true
                            lastTarget = null
                            // Excludes the folder's own tile too — the floating
                            // tile's centre (dragCentre, used for the merge-zone
                            // check below) can briefly sit over it even while
                            // [pos] itself is already outside the block (e.g. the
                            // child was grabbed off-centre), and merging/placing a
                            // child back onto its own source folder makes no sense.
                            val topLevel = placements.filter { parseFolderChildId(it.id) == null && it.id != dragFolderId }
                            val hoveredMerge = topLevel.firstOrNull { geom.rect(it).contains(dragCentre) }
                            val inZone = hoveredMerge != null && inMergeZone(geom.rect(hoveredMerge), dragCentre)
                            // Same intent-gating as the ordinary top-level merge
                            // above (FR-3.3): a moving finger passing through a
                            // neighbour's merge zone on its way somewhere else
                            // must not commit to a merge — only a dwell of
                            // [mergeDwellMs] while the centre stays put does. Without
                            // this, since the zone is 56%x56% of every tile and
                            // tiles sit only a few dp apart, almost any release
                            // point on the way to a specific spot lands inside
                            // *some* tile's zone, leaving no real way to just
                            // place the tile next to one without merging into it.
                            if (inZone) {
                                if (pulledOutDwellId != hoveredMerge!!.id ||
                                    (pos - pulledOutDwellAnchor).getDistance() > dwellMoveTol
                                ) {
                                    pulledOutDwellId = hoveredMerge.id
                                    pulledOutDwellAnchor = pos
                                    pulledOutDwellStartMs = change.uptimeMillis
                                }
                            } else {
                                pulledOutDwellId = null
                            }
                            val pulledOutDwelled = inZone &&
                                change.uptimeMillis - pulledOutDwellStartMs >= mergeDwellMs
                            val pulledOutMergeNow = inZone && (pulledOutDwelled || pulledOutMergeId == hoveredMerge?.id)
                            if (pulledOutMergeNow) {
                                if (pulledOutMergeId != hoveredMerge!!.id) {
                                    pulledOutMergeId = hoveredMerge.id
                                    onMergeTarget(hoveredMerge.id)
                                }
                                pulledOutTargetId = null
                                if (pulledOutSlot != null) {
                                    pulledOutSlot = null
                                    if (slotOf != null) onStickyPreview(emptyMap())
                                }
                                // Entering a merge: the dense-mode order-splice
                                // preview (if any) must revert too — the same
                                // reason the sticky push-down preview above is
                                // cleared, no push-down/reflow preview applies
                                // while merging.
                                if (pulledOutDenseSpliced) {
                                    onFolderChildDensePreviewClear(startId)
                                    pulledOutDenseSpliced = false
                                    pulledOutDenseTarget = null
                                }
                            } else {
                                if (pulledOutMergeId != null) { pulledOutMergeId = null; onMergeTarget(null) }
                                if (slotOf != null) {
                                    val childSize = byId[startId]?.size ?: TileSize.SMALL
                                    val w = childSize.cols.coerceAtMost(columns)
                                    val rawCell = geom.cellAt(pos - grab, columns, w)
                                    val cell = IntOffset(rawCell.x, rawCell.y.coerceAtMost(stickyDropRowBound(startId)))
                                    pulledOutSlot = GridPacker.encodeSlot(cell.x, cell.y)
                                    pulledOutTargetId = null
                                    if (freeMode) {
                                        onStickyPreview(emptyMap())
                                    } else {
                                        val anchored = order.mapNotNull { id ->
                                            val t = byId[id] ?: return@mapNotNull null
                                            val slot = t.gridSlot ?: return@mapNotNull null
                                            TilePlacement(id, t.size, GridPacker.decodeSlotCol(slot), GridPacker.decodeSlotRow(slot))
                                        }
                                        onStickyPreview(
                                            GridPacker.stickyPlacement(anchored, startId, childSize, cell.x, cell.y, columns),
                                        )
                                    }
                                } else {
                                    // Dense mode: live-splice the child into the
                                    // real top-level `order` at the hovered
                                    // position — the drop counterpart of
                                    // pulledOutTargetId (still tracked below for
                                    // the final release-time
                                    // FolderChildDropTarget), but *also* fed
                                    // back into `order` so GridPacker.pack
                                    // repacks the surrounding real tiles around
                                    // it right now, mirroring the live reflow an
                                    // ordinary top-level reorder gets for free.
                                    // Only re-spliced when the hovered target id
                                    // actually changes (same debounce as
                                    // lastTarget/onReorderTo), not every tick.
                                    val target = topLevel.firstOrNull { geom.rect(it).contains(pos) }?.id
                                    if (target != null) {
                                        pulledOutTargetId = target
                                        if (!pulledOutDenseSpliced || target != pulledOutDenseTarget) {
                                            pulledOutDenseTarget = target
                                            pulledOutDenseSpliced = true
                                            onFolderChildDensePreview(startId, target)
                                        }
                                    } else {
                                        // No tile under the finger this tick —
                                        // mirrors the ordinary top-level reorder
                                        // below: only treat this as "the trailing
                                        // empty region, append at the end" once
                                        // the finger is genuinely past the bottom
                                        // of all content. Otherwise (a miss in an
                                        // interior gap — e.g. the few dp seam
                                        // between two tiles, very easy to clip
                                        // right as the finger lifts to release)
                                        // leave the current target/splice exactly
                                        // as it was; without this, a momentary
                                        // release-time jitter into a seam would
                                        // silently downgrade a perfectly good
                                        // drop into "always lands at the bottom."
                                        val contentBottom = topLevel.maxOfOrNull { geom.rect(it).bottom } ?: 0f
                                        if (pos.y > contentBottom) {
                                            pulledOutTargetId = null
                                            if (!pulledOutDenseSpliced || pulledOutDenseTarget != null) {
                                                pulledOutDenseTarget = null
                                                pulledOutDenseSpliced = true
                                                onFolderChildDensePreview(startId, null)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (slotOf != null && startId != null) {
                        // Sticky mode: the tile floats to wherever the finger drops
                        // it. Landing on an already-occupied cell is fine — the
                        // occupant(s) get nudged sideways within their own row if
                        // there's a free gap there, else pushed straight down to
                        // make room (the same GridPacker.stickyPlacement
                        // computation onStickyDrop → StartViewModel's write path
                        // uses), instead of the drop being rejected or a full
                        // auto-arrange repack running. Recomputed live on every
                        // move (not just at drop) so the displaced tile(s)
                        // visibly slide out of the way while the finger is
                        // still down — matching dense mode's live reflow hint.
                        // FREE mode never displaces anyone (not even in preview) —
                        // the dragged tile's own redirect-to-nearest-free-cell
                        // resolution happens once, at release, in the write path,
                        // so nothing needs recomputing on every move here.
                        val tileSize = byId[startId]?.size ?: TileSize.SMALL
                        val w = tileSize.cols.coerceAtMost(columns)
                        val rawCell = geom.cellAt(pos - grab, columns, w)
                        val cell = IntOffset(rawCell.x, rawCell.y.coerceAtMost(stickyDropRowBound(startId)))
                        pendingSlot = GridPacker.encodeSlot(cell.x, cell.y)
                        if (freeMode) {
                            onStickyPreview(emptyMap())
                        } else {
                            val anchored = order.mapNotNull { id ->
                                if (id == startId) return@mapNotNull null
                                val t = byId[id] ?: return@mapNotNull null
                                val slot = t.gridSlot ?: return@mapNotNull null
                                TilePlacement(id, t.size, GridPacker.decodeSlotCol(slot), GridPacker.decodeSlotRow(slot))
                            }
                            onStickyPreview(
                                GridPacker.stickyPlacement(anchored, startId, tileSize, cell.x, cell.y, columns),
                            )
                        }
                    } else {
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
                }

                // Auto-scroll near the viewport edges.
                val fingerViewportY = (contentTopPx + blockTopOffsetPx + pos.y) - scrollOffsetPx()
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
                    lifted || draggingId() != null -> {
                        if (startId != null && parseFolderChildId(startId) != null) {
                            if (pulledOut) {
                                val target = when {
                                    pulledOutMergeId != null -> FolderChildDropTarget.Merge(pulledOutMergeId!!)
                                    pulledOutSlot != null -> FolderChildDropTarget.Cell(pulledOutSlot!!)
                                    else -> FolderChildDropTarget.Position(pulledOutTargetId)
                                }
                                onFolderChildPulledOut(startId, target)
                                // Tear down the dense-mode live-splice preview
                                // now that the real write has been dispatched —
                                // the eventual real top-level tile gets a brand
                                // new DB id, never this synthetic one, so it
                                // must not linger in `order` past this gesture.
                                if (pulledOutDenseSpliced) onFolderChildDensePreviewClear(startId)
                            } else {
                                onFolderChildDrop()
                            }
                        } else if (slotOf != null && mergeId == null) {
                            startId?.let { onStickyDrop(it, pendingSlot) }
                        }
                        onDrop(mergeId)
                    }
                    moved -> Unit
                    // A tap on another tile switches which tile is being edited
                    // (its corner controls move to it); a tap on the
                    // already-selected tile, or on open space (no tile hit),
                    // exits edit mode. Consumed so the sibling emptySpaceExit
                    // gesture (attached to the whole screen) doesn't *also* see
                    // this same unconsumed release and fire its own exit right
                    // behind onSelect — which previously undid every tile-switch
                    // the instant it happened.
                    startId != null -> {
                        change.consume()
                        if (startId != selectedId()) onSelect(startId) else onTapExit()
                    }
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
    // Threaded down to every live face's own AppIconCorner so a MEDIUM+ tile's
    // app-icon badge picks up the same shape masking a SMALL ICONS-mode icon
    // cell already gets — see AppIconCorner's own doc comment.
    homeStyle: HomeStyle = HomeStyle.TILES,
    iconShape: IconShape = IconShape.ORIGINAL,
    themedIcons: Boolean = false,
    stockRefreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    commodityRefreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    sportsRefreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
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
    val staticGlyph = @Composable { StaticTileGlyph(tile, homeStyle, iconShape, themedIcons) }

    // Small (1×1) clock / calendar / battery tiles get a compact non-flipping
    // live face — the time, today's day number, or charge percent — instead of
    // the static glyph.
    if (tile.size == TileSize.SMALL) {
        when (tile.iconKey) {
            "clock" -> { ClockSmallFace(active = liveActive, modifier = Modifier.fillMaxSize()); return }
            "calendar" -> { CalendarSmallFace(active = liveActive, modifier = Modifier.fillMaxSize()); return }
            "weather" -> {
                WeatherSmallFace(
                    location = WeatherTile.decode(tile.activityName),
                    fallback = staticGlyph,
                    modifier = Modifier.fillMaxSize(),
                )
                return
            }
            "battery" -> { BatterySmallFace(modifier = Modifier.fillMaxSize()); return }
            "flashlight" -> { FlashlightSmallFace(interactive = interactive, modifier = Modifier.fillMaxSize()); return }
            "countdown" -> {
                val (isoDate, _) = CountdownTile.decode(tile.activityName) ?: ("" to "")
                CountdownSmallFace(targetIsoDate = isoDate, modifier = Modifier.fillMaxSize())
                return
            }
            "steps" -> { StepsSmallFace(fallback = staticGlyph, active = liveActive, modifier = Modifier.fillMaxSize()); return }
            "stock" -> {
                StockSmallFace(
                    selection = StockTile.decode(tile.activityName),
                    fallback = staticGlyph,
                    active = liveActive,
                    refreshRate = stockRefreshRate,
                    modifier = Modifier.fillMaxSize(),
                )
                return
            }
            "commodity" -> {
                CommoditySmallFace(
                    symbol = CommodityTile.decode(tile.activityName)?.first,
                    fallback = staticGlyph,
                    active = liveActive,
                    refreshRate = commodityRefreshRate,
                    modifier = Modifier.fillMaxSize(),
                )
                return
            }
            "calsys" -> { CalendarSystemSmallFace(modifier = Modifier.fillMaxSize()); return }
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
                location = WeatherTile.decode(tile.activityName),
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
                homeStyle = homeStyle,
                iconShape = iconShape,
                themedIcons = themedIcons,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        LiveFace.PEOPLE -> {
            PeopleTileFace(
                size = tile.size,
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
                homeStyle = homeStyle,
                iconShape = iconShape,
                themedIcons = themedIcons,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        LiveFace.BATTERY -> {
            BatteryTileFace(
                size = tile.size,
                flipped = flipped,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        LiveFace.ALARM -> {
            AlarmTileFace(
                size = tile.size,
                flipped = flipped,
                active = liveActive,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        LiveFace.MOONPHASE -> {
            MoonPhaseTileFace(
                size = tile.size,
                flipped = flipped,
                active = liveActive,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        LiveFace.TASKS -> {
            TasksTileFace(
                size = tile.size,
                interactive = interactive,
                listId = tile.id,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        LiveFace.NOTES -> {
            NotesTileFace(size = tile.size, flipped = flipped, modifier = Modifier.fillMaxSize())
            return
        }
        LiveFace.STICKYNOTE -> {
            StickyNoteTileFace(size = tile.size, text = tile.activityName, modifier = Modifier.fillMaxSize())
            return
        }
        LiveFace.FLASHLIGHT -> {
            FlashlightTileFace(size = tile.size, interactive = interactive, modifier = Modifier.fillMaxSize())
            return
        }
        LiveFace.COUNTDOWN -> {
            val (isoDate, label) = CountdownTile.decode(tile.activityName) ?: ("" to "")
            CountdownTileFace(
                size = tile.size,
                flipped = flipped,
                targetIsoDate = isoDate,
                label = label,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        LiveFace.STEPS -> {
            StepsTileFace(size = tile.size, fallback = staticGlyph, active = liveActive, modifier = Modifier.fillMaxSize())
            return
        }
        LiveFace.SPORTS -> {
            val selection = SportsTile.decode(tile.activityName)
            SportsTileFace(
                size = tile.size,
                flipped = flipped,
                active = liveActive,
                tileId = tile.id,
                leagueSlug = selection?.leagueSlug.orEmpty(),
                teamId = selection?.teamId.orEmpty(),
                teamLabel = selection?.teamLabel.orEmpty(),
                refreshRate = sportsRefreshRate,
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
                size = tile.size,
                homeStyle = homeStyle,
                iconShape = iconShape,
                themedIcons = themedIcons,
            )
            return
        }
        LiveFace.STOCK -> {
            StockTileFace(
                size = tile.size,
                flipped = flipped,
                active = liveActive,
                selection = StockTile.decode(tile.activityName),
                refreshRate = stockRefreshRate,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        LiveFace.COMMODITY -> {
            val decoded = CommodityTile.decode(tile.activityName)
            CommodityTileFace(
                size = tile.size,
                flipped = flipped,
                active = liveActive,
                symbol = decoded?.first,
                displayName = decoded?.second,
                refreshRate = commodityRefreshRate,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        LiveFace.CALENDAR_SYSTEM -> {
            CalendarSystemTileFace(
                size = tile.size,
                flipped = flipped,
                active = liveActive,
                systemId = CalendarSystemTile.decode(tile.activityName),
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
                            homeStyle = homeStyle,
                            iconShape = iconShape,
                            themedIcons = themedIcons,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    size = tile.size,
                    homeStyle = homeStyle,
                    iconShape = iconShape,
                    themedIcons = themedIcons,
                )
                return
            }
        }
    }
    staticGlyph()
}

/**
 * The non-live tile face: the monoline glyph, with a label above small size —
 * or, whenever a real app icon is shown instead ([useAppIcon]: any app with no
 * WP-recognized category glyph, the common case for an ordinary third-party
 * pinned app), masked to [iconShape] in ICONS home style, matching the
 * top-level SMALL icon cell and every other icon surface (user-reported
 * inconsistency: this is the *default* MEDIUM+ face for most pinned apps, so
 * it was the most visible unmasked spot of all).
 */
@Composable
private fun StaticTileGlyph(
    tile: TileModel.App,
    homeStyle: HomeStyle = HomeStyle.TILES,
    iconShape: IconShape = IconShape.ORIGINAL,
    // Themed/monochrome icon (Personalize) — independent of homeStyle/iconShape,
    // same as everywhere else this setting applies. The tile is already sitting
    // on its own accent-filled face (drawn by the caller), so unlike the app
    // list/live-tile-badge renderers there's no separate plate here — the
    // themed glyph is just tinted to LocalTileFaceColor, matching how the
    // generic category-glyph fallback below already renders on this exact face.
    themedIcons: Boolean = false,
) {
    val useAppIcon = !TileIcons.hasIcon(tile.iconKey)
    val composeShape = if (homeStyle == HomeStyle.ICONS) iconShape.toComposeShape() else null
    // Decode at (roughly) the actual dp this glyph will render at — mirrors
    // the size TileIconContent below picks by tile.size — rather than a
    // fixed 96px regardless of size, so LARGE's 46dp glyph doesn't visibly
    // blur on a high-density device (user-reported blur at bigger sizes).
    val monolineDp = when {
        tile.size == TileSize.SMALL -> 30
        tile.size.rows == 1 && tile.size.cols > 1 -> 26
        tile.size == TileSize.LARGE -> 46
        else -> 34
    }
    // A themed icon renders far bigger than the tiny WP monoline glyph (see
    // TileIconContent below) — decode at that larger size too, or the
    // upscaled result reads visibly blurry (user-reported at the small size).
    val themedDp = when {
        tile.size == TileSize.SMALL -> 60
        tile.size.rows == 1 && tile.size.cols > 1 -> 48
        tile.size == TileSize.LARGE -> 120
        else -> 78
    }
    val sizePx = with(LocalDensity.current) {
        (if (themedIcons) themedDp else monolineDp).dp.roundToPx()
    }.coerceAtLeast(96)
    val maskable = if (useAppIcon && (composeShape != null || themedIcons)) {
        rememberMaskableIcon(tile.packageName, tile.activityName, sizePx)
    } else {
        null
    }
    val appIcon = if (useAppIcon && maskable == null) {
        rememberTileAppIcon(tile.packageName, tile.activityName, sizePx)
    } else {
        null
    }
    val mono = maskable?.monochromeBitmap

    @Composable
    fun TileIconContent(monolineSize: Int) {
        if (useAppIcon && themedIcons && mono != null) {
            // A real app icon reads as noticeably bigger than the tiny WP
            // monoline glyph every other branch here uses ([monolineSize] is a
            // deliberate stylized-glyph convention, not an icon size) — uses
            // its own [themedDp] table instead, so the themed icon matches how
            // a normal launcher would show the app's own icon, rather than
            // looking like a shrunken glyph (user-reported still too small at
            // an earlier 1.8x-of-glyph-size attempt).
            Image(
                bitmap = mono,
                contentDescription = tile.label,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(LocalTileFaceColor.current),
                modifier = Modifier.size(themedDp.dp),
            )
        } else if (useAppIcon && maskable != null && composeShape != null) {
            Image(
                bitmap = if (maskable.isAdaptive) maskable.unmaskedBitmap else maskable.bitmap,
                contentDescription = tile.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(monolineSize.dp).clip(composeShape),
            )
        } else if (useAppIcon && maskable != null) {
            // maskable loaded (themedIcons requested it) but there's no themed
            // layer to show and no shape to mask to — same plain, unmasked
            // icon TILES mode has always shown, just sourced from the already-
            // loaded MaskableIcon instead of a second rememberTileAppIcon call.
            Image(
                bitmap = maskable.bitmap,
                contentDescription = tile.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(monolineSize.dp),
            )
        } else if (useAppIcon && appIcon != null) {
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
                tint = LocalTileFaceColor.current,
                modifier = Modifier.size(monolineSize.dp),
            )
        }
    }

    if (tile.size == TileSize.SMALL) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TileIconContent(30)
        }
    } else if (tile.size.rows == 1 && tile.size.cols > 1) {
        // One row tall but wider than a single column (e.g. WIDE_SMALL 2×1) —
        // there's no room for the icon-above-label stack below, so icon and
        // label sit side by side instead. Gesture-resize-only footprint; the
        // tap cycle never lands here (TileSize.next()).
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TileIconContent(26)
            Spacer(Modifier.width(8.dp))
            TileLabel(tile.label.orEmpty(), modifier = Modifier.weight(1f))
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
            Icon(TileIcons["people"], null, tint = LocalTileFaceColor.current, modifier = Modifier.size(30.dp))
        }
    } else {
        val glyphSize = if (size == TileSize.LARGE) 46 else 34
        Column(modifier = modifier.padding(11.dp)) {
            Icon(TileIcons["people"], null, tint = LocalTileFaceColor.current, modifier = Modifier.size(glyphSize.dp))
            Spacer(Modifier.weight(1f))
            TileLabel(name)
        }
    }
}

@Composable
internal fun rememberTileAppIcon(packageName: String, activityName: String, sizePx: Int = 96): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null, packageName, activityName, sizePx) {
        value = withContext(Dispatchers.IO) {
            // A pinned app shortcut publishes its own icon and has no resolvable
            // ComponentName (see shortcutIconDrawable) — without this the tile
            // showed its parent app's icon instead.
            // Process-wide cache: a scroll-recycle or a page swipe otherwise
            // re-decodes the same icon from scratch every time. See AppIconCache.
            val cacheKey = AppIconCache.iconCacheKey(packageName, activityName, sizePx)
            AppIconCache[cacheKey]?.let { return@withContext it.asImageBitmap() }
            shortcutIconDrawable(context, packageName, activityName)?.let {
                val bmp = it.toBitmap(width = sizePx, height = sizePx)
                AppIconCache.put(cacheKey, bmp)
                return@withContext bmp.asImageBitmap()
            }
            runCatching {
                context.packageManager
                    .getActivityIcon(ComponentName(packageName, activityName))
                    .toBitmap(width = sizePx, height = sizePx)
                    .also { AppIconCache.put(cacheKey, it) }
                    .asImageBitmap()
            }.recoverCatching {
                // Some apps (Flipkart, Myntra, etc.) launch via a seasonal
                // activity-alias that shows a special sale icon while enabled,
                // then disable that exact component once the sale ends —
                // getActivityIcon on the now-dead alias throws even though the
                // app itself is perfectly installed. Fall back to the app's
                // current (real) launcher icon rather than leaving a pinned
                // tile permanently blank.
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(width = sizePx, height = sizePx)
                    .also { AppIconCache.put(cacheKey, it) }
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
/**
 * The dominant colour of an app's launcher icon, for app-icon-colour mode (FR-7).
 *
 * The scan runs on [Dispatchers.IO], not in a `remember` block. [dominantIconColor]
 * walks every pixel, and `remember`'s calculation executes during composition on
 * the main thread — so with app-icon colours on, every tile in the grid, every
 * folder mini-grid cell and every rotated stack member paid for a full 96×96
 * scan on the UI thread on first composition, and again on each scroll-recycle
 * since nothing caches decoded icons. Keyed on the decoded bitmap, so it still
 * runs once per decode; it just no longer runs where it can drop a frame.
 */
@Composable
private fun rememberDominantIconColor(packageName: String, activityName: String): Color? {
    val icon = rememberTileAppIcon(packageName, activityName)
    return produceState<Color?>(null, icon) {
        value = icon?.let { withContext(Dispatchers.IO) { dominantIconColor(it) } }
    }.value
}

@Composable
private fun rememberIconSuggestion(packageName: String, activityName: String): IconSuggestion? {
    val icon = rememberTileAppIcon(packageName, activityName)
    return produceState<IconSuggestion?>(null, icon) {
        value = icon
            ?.let { withContext(Dispatchers.IO) { dominantIconColor(it) } }
            ?.let { IconSuggestion(it, TileAccents.nearestAccentId(it)) }
    }.value
}

internal fun dominantIconColor(bitmap: ImageBitmap): Color? {
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

/**
 * Whether the user's actual chosen background reads as light: a custom/Bing
 * photo's sampled average brightness when one is set, else the plain screen
 * background (no wallpaper), else a bundled gradient's own themed base colour.
 * Drives [LocalTileFaceColor] and the Start screen's chevron/gear tint — see
 * docs/DECISIONS.md "Live tile text: black when the wallpaper behind it is light".
 */
@Composable
internal fun rememberChosenWallpaperIsLight(
    customPhoto: ImageBitmap?,
    noWallpaper: Boolean,
    wallpaper: WallpaperGradient,
    dark: Boolean,
    screenBg: Color,
): Boolean {
    val photoLuminance = remember(customPhoto) { customPhoto?.let(::averageLuminance) }
    return when {
        photoLuminance != null -> photoLuminance > LIGHT_BACKGROUND_LUMINANCE_THRESHOLD
        noWallpaper -> isLightBackground(screenBg)
        else -> isLightBackground(themedBase(wallpaper.base, dark))
    }
}

/**
 * Cheap average perceived luminance (0..1) sampled across a coarse ~48×48 grid
 * of [bitmap] — fast enough to run once per wallpaper change even on a large
 * decoded photo, and plenty precise for a light/dark backdrop classification.
 */
private fun averageLuminance(bitmap: ImageBitmap): Float {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return 0f
    val android = bitmap.asAndroidBitmap()
    val strideX = (w / 48).coerceAtLeast(1)
    val strideY = (h / 48).coerceAtLeast(1)
    var sum = 0.0
    var n = 0
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            val p = android.getPixel(x, y)
            val r = (p ushr 16) and 0xff
            val g = (p ushr 8) and 0xff
            val b = p and 0xff
            sum += 0.299 * r + 0.587 * g + 0.114 * b
            n++
            x += strideX
        }
        y += strideY
    }
    return if (n > 0) (sum / n / 255.0).toFloat() else 0f
}

/**
 * A closed folder's mini-grid cell icon (`FolderTileContent`). In ICONS home
 * style this now prefers the child app's own real icon whenever a real
 * package is resolvable — same rule `IconCellView`'s `maskedOrGlyphIcon`
 * already applies to top-level icons (user-reported: a folder's default apps
 * — contacts/mail/messages — showed the generic WP category glyph instead
 * of their real icons, inconsistent with the rest of ICONS mode). TILES mode
 * keeps the original WP-authentic behaviour (glyph whenever the iconKey
 * matches a known category) unchanged.
 */
@Composable
private fun FolderChildIcon(
    child: FolderChild?,
    homeStyle: HomeStyle = HomeStyle.TILES,
    iconShape: IconShape = IconShape.ORIGINAL,
) {
    // Always call rememberTileAppIcon so the composable call count is stable
    // regardless of whether child is null or has a WP icon.
    val pkg = child?.packageName.orEmpty()
    val act = child?.activityName.orEmpty()
    val appIcon = rememberTileAppIcon(pkg, act)
    val useAppIcon = child != null && if (homeStyle == HomeStyle.ICONS) {
        child.packageName.isNotBlank()
    } else {
        !TileIcons.hasIcon(child.iconKey)
    }

    if (child == null) return
    // ICONS mode dropped this cell's background plate (see FolderTileContent's
    // cellFill), so the icon itself needs to be bigger to still fill the cell
    // — user-reported after that fix: "icon size should be bigger... as there
    // is no square around." TILES mode keeps the original 18dp, tuned for
    // sitting on its own tinted-square backdrop.
    val iconSize = if (homeStyle == HomeStyle.ICONS) 26.dp else 18.dp
    // ICONS mode masks this child's icon to the chosen shape too — matching the
    // top-level SMALL icon cell (IconCellView) — instead of always drawing the
    // OS's own native-shaped bitmap (user-reported inconsistency).
    val composeShape = if (homeStyle == HomeStyle.ICONS) iconShape.toComposeShape() else null
    val maskable = if (useAppIcon && composeShape != null) rememberMaskableIcon(pkg, act) else null
    when {
        useAppIcon && maskable != null -> {
            Image(
                bitmap = if (maskable.isAdaptive) maskable.unmaskedBitmap else maskable.bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(iconSize).clip(composeShape!!),
            )
        }
        useAppIcon && appIcon != null -> {
            Image(
                bitmap = appIcon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(iconSize),
            )
        }
        else -> {
            Icon(
                imageVector = TileIcons[child.iconKey],
                contentDescription = null,
                tint = LocalTileFaceColor.current,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun FolderTileContent(
    tile: TileModel.Folder,
    editMode: Boolean,
    launchEnabled: Boolean,
    appIconColors: Boolean,
    wallpaperAccent: Color? = null,
    glass: Boolean,
    transparency: Float,
    darkTheme: Boolean,
    tiledWallpaper: Boolean,
    borderless: Boolean = false,
    notifications: NotificationSnapshot,
    homeStyle: HomeStyle = HomeStyle.TILES,
    iconShape: IconShape = IconShape.ORIGINAL,
    onLaunchChild: (FolderChild) -> Unit,
    onOpenFolder: () -> Unit,
    onEnterEdit: () -> Unit,
) {
    // Inline iOS-style folder face: a mini-grid of the child app icons, sized to
    // match the folder tile's own footprint — a wide folder shows a 4×2 grid
    // (more apps), a large folder 3×3, medium/small 2×2, and the drag-only
    // one-dimensional presets (BANNER 4×1, COLUMN 1×4) show a single row or
    // single column of up to 4 apps rather than a 2×2 grid squeezed into a
    // strip (user-reported: a 4×1 folder was only showing 2 of its apps,
    // since the old hardcoded 2×2 fallback wasted half its cells cramming a
    // second row into a tile with no vertical room for one). SMALL is the one
    // deliberate exception, kept at the original 2×2 rather than shrinking to
    // a single icon — an established look predating this whole arc, not
    // something this fix should change. When [launchEnabled] (only on the
    // roomy 4-column grid) each icon is tappable to launch out of edit mode,
    // and an overflow cell becomes "+N" that opens the overlay; on denser
    // 5/6-column grids the cells are too small to tap, so they are
    // display-only and the whole tile opens the overlay on tap. In edit mode
    // the cells are always inert so the grid-level drag owns the tile.
    val children = tile.children
    val cols = if (tile.size == TileSize.SMALL) 2 else tile.size.cols
    val rows = if (tile.size == TileSize.SMALL) 2 else tile.size.rows
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
                        // A grid slot with no child at all (folder has fewer apps
                        // than the mini-grid's capacity) gets no backdrop whatsoever
                        // — painting the neutral tint there too left unused slots as
                        // ugly dark squares; the prototype's own markup never
                        // generates a `.gm` cell for a non-existent child either, so
                        // an empty slot should just show the folder tile's own fill.
                        val isEmptySlot = !isPlus && child == null
                        val cellBg = child?.accentOverride
                            ?.let { TileAccents.colorForOverride(it, "blue") }
                            ?: child?.takeIf { appIconColors }
                                ?.let { rememberDominantIconColor(it.packageName, it.activityName) }
                            ?: wallpaperAccent
                            ?: Color(0x2E000000)
                        val cellFill = when {
                            isEmptySlot -> Modifier
                            // ICONS mode: bare icon only, no per-cell background
                            // plate — matches a normal Android launcher's folder
                            // preview (user-reported: "only icon should be shown -
                            // dont show inside square"). The tinted-square look
                            // stays for TILES mode, which is unaffected.
                            homeStyle == HomeStyle.ICONS -> Modifier
                            tiledWallpaper -> Modifier
                            // Borderless: the folder tile itself paints nothing, so
                            // its mini-grid cells must not paint plates either — a
                            // grid of tinted squares floating over the wallpaper is
                            // exactly the "tile surface" this style removes.
                            borderless -> Modifier
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
                                    color = LocalTileFaceColor.current,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            } else {
                                FolderChildIcon(child, homeStyle, iconShape)
                            }
                            // Per-app count — lets a closed folder be scanned for
                            // *which* app has unread items, not just how many in
                            // total (the folder's own combined count sits beside its
                            // name label instead, so it never collides with this).
                            val childBadge = child?.let { notifications.badgeFor(it.packageName) } ?: 0
                            if (!isPlus && childBadge > 0) {
                                FolderChildBadge(
                                    count = childBadge,
                                    dark = darkTheme,
                                    modifier = Modifier.align(Alignment.TopEnd),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // The folder's consolidated total sits beside its name (not in the tile's
        // top-right corner, where it used to collide with the top-right cell's
        // per-app badge). De-duped by package, matching TileView's old aggregate.
        val folderTotal = tile.children.map { it.packageName }.distinct()
            .sumOf { notifications.badgeFor(it) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TileLabel(tile.name, modifier = Modifier.weight(1f, fill = false))
            if (folderTotal > 0) {
                Spacer(Modifier.width(4.dp))
                NotificationBadge(
                    count = folderTotal,
                    dark = darkTheme,
                    small = tile.size == TileSize.SMALL,
                    cornerInset = false,
                )
            }
        }
    }
}

/** Auto-rotate interval for a widget stack — long enough to read a notification snippet. */
private const val STACK_ROTATE_MS = 10000L
private const val STACK_EDGE_DRAG_ZONE_DP = 40

/** Width of the left/right screen-edge strip that starts the notification-shade /
 * quick-settings swipe-down gesture (see [StartScreen]'s `edgeSwipeGesture`). */
private const val EDGE_SWIPE_ZONE_DP = 32

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
    homeStyle: HomeStyle = HomeStyle.TILES,
    iconShape: IconShape = IconShape.ORIGINAL,
    themedIcons: Boolean = false,
    stockRefreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    commodityRefreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    sportsRefreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    appIconColors: Boolean,
    glass: Boolean,
    transparency: Float,
    tiledWallpaper: Boolean,
    borderless: Boolean = false,
    darkTheme: Boolean,
    wallpaper: com.tileshell.core.design.WallpaperGradient,
    wallpaperPhoto: ImageBitmap?,
    wallpaperAlignX: Float,
    wallpaperAlignY: Float,
    wallpaperZoom: Float,
    wallpaperOrigin: () -> Offset,
    fullWidth: Float,
    fullHeight: Float,
    notifications: NotificationSnapshot,
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
    //
    // Both flags must be read through rememberUpdatedState. Because the effect
    // deliberately never restarts, reading them directly captured whatever they
    // were at first composition and never saw another value — so a stack that
    // started life with liveActive true kept rotating with the screen off and
    // battery saver on, and one that started false never rotated at all. Same
    // stale-closure trap as the drag handles (see DECISIONS, "resize/reorder
    // follow-up #5"); the feed's own stack rotation already guards this way.
    val liveActiveRef = rememberUpdatedState(liveActive)
    val editModeRef = rememberUpdatedState(editMode)
    LaunchedEffect(count) {
        if (count <= 1) return@LaunchedEffect
        delay(rotateOffset)
        while (true) {
            delay(STACK_ROTATE_MS)
            if (!liveActiveRef.value || editModeRef.value) continue
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
                // icon's dominant colour shows; else the stack tile's own `accent`
                // (already resolved to the wallpaper accent upstream when that
                // mode is active, so no separate branch is needed here) — same
                // three-way chain as FolderOverlay/FolderTileContent. This has
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
                                borderless -> Modifier
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
                        homeStyle = homeStyle,
                        iconShape = iconShape,
                        themedIcons = themedIcons,
                        stockRefreshRate = stockRefreshRate,
                        commodityRefreshRate = commodityRefreshRate,
                        sportsRefreshRate = sportsRefreshRate,
                    )
                    // Per-member notification count — top-right, same corner as a
                    // plain app tile's badge (AppIconCorner, when a live face draws
                    // one, sits top-left, so there's no collision). Lets you tell
                    // *which* member the count belongs to as the stack rotates — no
                    // separate consolidated total exists for a stack. Rendered at the
                    // same full size as a regular (non-folder) app tile's badge, since
                    // a stack member fills the whole tile just like a pinned app —
                    // not the shrunk FolderChildBadge used for closed folders' tiny
                    // mini-grid cells.
                    val memberBadge = notifications.badgeFor(child.packageName)
                    if (memberBadge > 0) {
                        NotificationBadge(
                            count = memberBadge,
                            dark = darkTheme,
                            small = tile.size == TileSize.SMALL,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
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
private fun TileLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.lowercase(),
        color = LocalTileFaceColor.current,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
        modifier = modifier,
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
            // Alarm tile: same alarms screen the clock tile opens — there's no
            // separate "alarm app" to launch, and this is exactly what the tile's
            // own face is showing.
            if (tile.iconKey == "alarm" && openClock(context)) return
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
    if (child.iconKey == "alarm" && openClock(context)) return
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
        // Battery usage screen — standard, app-agnostic (works even on OEM
        // skins whose Settings app has no fixed package name to launch by).
        "battery" -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        // No standard "moon phase" app/intent exists; same fallback pattern as
        // weather — a web search, handled in-app by the Google app where
        // present, else the browser.
        "moonphase" -> Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse("https://www.google.com/search?q=moon+phase+today"))
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
    // PixelCopy.request can throw synchronously — IllegalArgumentException("Window
    // doesn't have a backing surface!") — when the window's Surface has already
    // been torn down by the time this runs, e.g. a screen-off ON_PAUSE capture
    // racing the display actually powering off. This was previously uncaught,
    // crashing the entire Home process (confirmed via repeated real-device crash
    // traces landing here, several times a day); every call site already treats a
    // null return as "capture skipped," so failing safe here costs nothing.
    val requested = runCatching {
        android.view.PixelCopy.request(
            activity.window, full,
            { result -> deferred.complete(result == android.view.PixelCopy.SUCCESS) },
            android.os.Handler(android.os.Looper.getMainLooper()),
        )
    }.isSuccess
    if (!requested) return null
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
