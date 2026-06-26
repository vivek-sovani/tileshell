@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.tileshell.feature.start

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tileshell.core.data.AppLauncher
import com.tileshell.core.data.FolderChild
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
import com.tileshell.feature.livetiles.WeatherTileFace
import com.tileshell.feature.livetiles.rememberFlipState
import com.tileshell.feature.livetiles.rememberLiveTilesActive
import com.tileshell.feature.livetiles.OemBatteryGuard
import com.tileshell.feature.livetiles.rememberBatteryOptimizationExempt
import com.tileshell.feature.livetiles.rememberNotificationAccess
import com.tileshell.feature.livetiles.rememberPermissionGranted
import com.tileshell.feature.livetiles.WeatherRefreshWorker
import com.tileshell.feature.personalize.AboutSheet
import com.tileshell.feature.personalize.CategoryFolderSheet
import com.tileshell.feature.personalize.FeedSourceItem
import com.tileshell.feature.personalize.PersonalizeSheet
import com.tileshell.core.data.settings.FontStyle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.abs
import kotlin.math.roundToInt

/** Flat dark screen behind "wallpaper behind tiles" mode (keeps gaps/borders dark). */
private val TiledScreenDark = Color(0xFF0A0A0D)

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
) {
    val tiles by viewModel.tiles.collectAsStateWithLifecycle()
    val swipeEnabled by viewModel.swipeEnabled.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val selectedTileId by viewModel.selectedTileId.collectAsStateWithLifecycle()
    val openFolderId by viewModel.openFolderId.collectAsStateWithLifecycle()
    val personalizeOpen by viewModel.personalizeOpen.collectAsStateWithLifecycle()
    val aboutOpen by viewModel.aboutOpen.collectAsStateWithLifecycle()
    val foldersOpen by viewModel.foldersOpen.collectAsStateWithLifecycle()
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

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
    // Transparent-tile fill at the current slider (FR-7); null when glass is off.
    val glassFill = if (settings.glass) Glass.fill(dark, settings.transparency) else null
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

    val scrollState = rememberScrollState()
    // Pager position: -1 = feed (left), 0 = Start, +1 = app list (right). The feed
    // page is the swipe-right surface; it is only reachable when enabled (FR-7).
    val feedEnabled = settings.feedEnabled
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

    // Home press collapses to Start and scrolls the grid to the top.
    LaunchedEffect(Unit) {
        viewModel.homeRequests.collect {
            viewModel.setAppList(false)
            scope.launch { progress.animateTo(0f, settleSpec) }
            scrollState.animateScrollTo(0)
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
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val viewportHeightPx = constraints.maxHeight.toFloat()
        val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()

        // Wallpaper layer (FR-7): selected gradient or custom photo, optionally
        // blurred. Drawn first so all content sits above it. In "wallpaper behind
        // tiles" mode the screen instead goes flat dark — the wallpaper shows only
        // through the tiles, keeping every gap/border dark. "none" skips the image
        // layer entirely and the theme bg colour shows through.
        if (tiledWallpaper) {
            Box(modifier = Modifier.fillMaxSize().background(TiledScreenDark))
        } else if (noWallpaper) {
            Box(modifier = Modifier.fillMaxSize().background(tokens.bg))
        } else {
            WallpaperBackground(
                gradient = wallpaper,
                customWallpaperUri = settings.customWallpaperUri,
                blur = settings.blur,
                alignX = settings.wallpaperAlignX,
                alignY = settings.wallpaperAlignY,
            )
        }

        // Horizontal pager gesture. Detection runs in the Initial pass so a
        // dominant horizontal drag is claimed before the vertical grid scroll
        // (a child) can consume it; vertical drags pass straight through.
        val pager = Modifier.pointerInput(swipeEnabled, widthPx, feedEnabled) {
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
                        val lower = if (feedEnabled) -1f else 0f
                        val target = (base - dx / widthPx).coerceIn(lower, 1f)
                        scope.launch { progress.snapTo(target) }
                    }
                    if (!change.pressed) break
                }
                if (horizontal) {
                    val lower = if (feedEnabled) -1f else 0f
                    settleTo(pagerCommitTarget(base, progress.value).coerceAtLeast(lower))
                }
            }
        }

        // Blur the Start surface behind the folder overlay (prototype
        // backdrop-filter; real blur only takes effect API 31+, harmless below).
        val behindBlur = if (openFolder != null) 14.dp else 0.dp
        Box(modifier = Modifier.fillMaxSize().blur(behindBlur).then(pager)) {
            // Start page: parallaxes (±22%) and fades as a side page comes in.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -0.22f * widthPx * progress.value
                        alpha = 1f - 0.6f * abs(progress.value)
                    },
            ) {
                StartPage(
                    specs = specs,
                    byId = byId,
                    scrollState = scrollState,
                    chevronVisible = swipeEnabled,
                    editMode = editMode,
                    liveSuspended = liveSuspended,
                    selectedTileId = selectedTileId,
                    accent = accent,
                    glassFill = glassFill,
                    glassLine = tokens.glassLine,
                    tiledWallpaper = tiledWallpaper,
                    wallpaper = wallpaper,
                    wallpaperPhoto = tiledPhoto,
                    wallpaperAlignX = settings.wallpaperAlignX,
                    wallpaperAlignY = settings.wallpaperAlignY,
                    darkTheme = dark,
                    notifications = notifications,
                    widthPx = widthPx,
                    viewportHeightPx = viewportHeightPx,
                    statusBarTopPx = statusBarTopPx,
                    onLockScreen = onLockScreen,
                    onTile = { tile ->
                        when (tile) {
                            is TileModel.App -> onTileClick(context, tile)
                            is TileModel.Folder -> viewModel.openFolder(tile.id)
                        }
                    },
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
                    onAdd = {
                        viewModel.exitEdit()
                        settleTo(1f)
                        Toast.makeText(context, "long-press an app to pin", Toast.LENGTH_SHORT)
                            .show()
                    },
                    onPersonalize = viewModel::openPersonalize,
                )
            }

            // App-list page: slides in from the right.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = widthPx * (1f - progress.value) }
                    .background(LocalColorTokens.current.bg),
            ) {
                AppListScreen(
                    modifier = Modifier.fillMaxSize(),
                    onPinned = { settleTo(0f) },
                )
            }

            // Feed page (left): an independent, opaque screen that slides in over
            // Start from the left edge as the user swipes right (mirrors the app
            // list). Drawn on top with its own background so Start never shows
            // through it. Only composed when enabled (FR-7); off-screen otherwise.
            if (feedEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = widthPx * (-1f - progress.value) }
                        .background(LocalColorTokens.current.bg),
                ) {
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
                        onSearch = { query -> launchWebSearch(context, query) },
                        onWeatherDetails = { query -> launchWebSearch(context, query) },
                        onAddSchedule = { launchAddEvent(context) },
                        onOpenArticle = { link -> launchUrl(context, link) },
                        onRefresh = {
                            viewModel.refreshFeeds()
                            Toast.makeText(context, "refreshing news", Toast.LENGTH_SHORT).show()
                        },
                        active = feedShown,
                    )
                }
            }
        }

        // Personalize sheet overlay (edit bar → personalize, FR-7).
        PersonalizeSheet(
            visible = personalizeOpen,
            dark = dark,
            followSystemTheme = settings.followSystemTheme,
            onFollowSystemThemeChange = viewModel::setFollowSystemTheme,
            accentId = settings.accentId,
            glass = settings.glass,
            transparency = settings.transparency,
            blur = settings.blur,
            wallpaperId = settings.wallpaperId,
            customWallpaper = settings.customWallpaperUri != null,
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
            onResetLayout = {
                viewModel.resetLayout()
                Toast.makeText(context, "layout reset", Toast.LENGTH_SHORT).show()
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
            tileFill = settings.tileFill,
            onTileFillChange = viewModel::setTileFill,
            fontStyle = settings.fontStyle,
            onFontStyleChange = viewModel::setFontStyle,
            onAbout = viewModel::openAbout,
            onFolders = viewModel::openFolders,
            onDismiss = viewModel::closePersonalize,
        )

        // About sheet (personalize → about).
        AboutSheet(
            visible = aboutOpen,
            dark = dark,
            accentId = settings.accentId,
            onDismiss = viewModel::closeAbout,
        )

        // Category-folders sheet (personalize → folders).
        CategoryFolderSheet(
            visible = foldersOpen,
            dark = dark,
            accentId = settings.accentId,
            apps = apps,
            onCreate = { name, picked ->
                viewModel.createFolder(name, picked)
                Toast.makeText(context, "created \"$name\" folder", Toast.LENGTH_SHORT).show()
            },
            onDismiss = viewModel::closeFolders,
        )

        // Full-screen folder overlay (FR-4).
        FolderOverlay(
            folder = openFolder,
            accent = accent,
            onClose = viewModel::closeFolder,
            onLaunchChild = { child ->
                if (!AppLauncher.launch(context, child.packageName, child.activityName)) {
                    Toast.makeText(
                        context,
                        "couldn't open ${child.label ?: "app"}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                viewModel.closeFolder()
            },
            onRename = { name -> openFolder?.let { viewModel.renameFolder(it.id, name) } },
            onPullOut = { child ->
                openFolder?.let { viewModel.removeFolderChild(it.id, child) }
            },
            onResize = viewModel::resizeFolderChild,
            onReorder = viewModel::reorderFolderChildren,
        )

        // First-run hint (S19): one-time prototype hint card over Start. Sits
        // above all other layers so it reads on a fresh install; self-hides once
        // seen.
        FirstRunHint()

        // Wallpaper crop overlay: shown immediately after the user picks a photo so
        // they can drag to position the image before it becomes the live wallpaper.
        val cropUri = pendingWallpaperCropUri
        if (cropUri != null) {
            WallpaperCropOverlay(
                uri = cropUri,
                onConfirm = { alignX, alignY ->
                    viewModel.setCustomWallpaper(cropUri, alignX, alignY)
                    pendingWallpaperCropUri = null
                },
                onCancel = { pendingWallpaperCropUri = null },
            )
        }
    }
    }
}

@Composable
private fun StartPage(
    specs: List<TileSpec>,
    byId: Map<String, TileModel>,
    scrollState: androidx.compose.foundation.ScrollState,
    chevronVisible: Boolean,
    editMode: Boolean,
    liveSuspended: Boolean,
    selectedTileId: String?,
    accent: Color,
    glassFill: Color?,
    glassLine: Color,
    tiledWallpaper: Boolean,
    wallpaper: com.tileshell.core.design.WallpaperGradient,
    wallpaperPhoto: ImageBitmap?,
    wallpaperAlignX: Float,
    wallpaperAlignY: Float,
    darkTheme: Boolean,
    notifications: NotificationSnapshot,
    widthPx: Float,
    viewportHeightPx: Float,
    statusBarTopPx: Float,
    onLockScreen: () -> Unit,
    onTile: (TileModel) -> Unit,
    onChevron: () -> Unit,
    onEnterEdit: (String) -> Unit,
    onSelectTile: (String) -> Unit,
    onExitEdit: () -> Unit,
    onReorder: (List<String>) -> Unit,
    onMerge: (dragId: String, targetId: String, survivingOrder: List<String>) -> Unit,
    onResize: (String) -> Unit,
    onUnpin: (String) -> Unit,
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
                order = order,
                byId = byId,
                draggingId = { draggingId },
                selectedId = { selectedTileId },
                onUnpin = { id -> order.remove(id); onUnpin(id) },
                onResize = onResize,
                onLift = { id, offset -> draggingId = id; dragOffset.value = offset },
                onDrag = { offset -> dragOffset.value = offset },
                onReorderTo = { dragId, targetId ->
                    val next = reorderTiles(order.toList(), dragId, targetId)
                    if (next != order.toList()) {
                        order.clear()
                        order.addAll(next)
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
                    TileView(
                        tile = model,
                        index = index,
                        editMode = editMode,
                        selected = editMode && model.id == selectedTileId,
                        dragging = dragging,
                        mergeTarget = model.id == mergeTargetId,
                        accent = accent,
                        glassFill = glassFill,
                        glassLine = glassLine,
                        tiledWallpaper = tiledWallpaper,
                        wallpaper = wallpaper,
                        wallpaperPhoto = wallpaperPhoto,
                        wallpaperAlignX = wallpaperAlignX,
                        wallpaperAlignY = wallpaperAlignY,
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
                        badgeCount = (model as? TileModel.App)
                            ?.let { notifications.badgeFor(it.packageName) } ?: 0,
                        darkTheme = darkTheme,
                        canMoveBack = order.indexOf(model.id) > 0,
                        canMoveForward = order.indexOf(model.id) in 0 until order.size - 1,
                        onTap = { if (!editMode) onTile(model) },
                        onLongPress = { if (!editMode) onEnterEdit(model.id) },
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
        if (chevronVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 14.dp, bottom = 26.dp),
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
    glassFill: Color?,
    glassLine: Color,
    tiledWallpaper: Boolean,
    wallpaper: com.tileshell.core.design.WallpaperGradient,
    wallpaperPhoto: ImageBitmap?,
    wallpaperAlignX: Float,
    wallpaperAlignY: Float,
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
) {
    // TalkBack reads the whole tile as one node: the app/folder name plus state,
    // with the launch/edit operations exposed as semantic actions (the visual
    // drag/corner-control flow is sighted-only, so screen-reader users drive the
    // exact same ViewModel calls through these custom actions instead).
    val a11yLabel = tileAccessibilityLabel(tile, badgeCount, editMode, selected)

    val tileCornerRadius = LocalTileCornerRadius.current
    val useTileGradient = LocalTileGradient.current

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
            // Optional rounded corners (personalisation setting 0–12 dp).
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
                        darkBase = TiledScreenDark,
                        origin = wallpaperOrigin,
                        alignX = wallpaperAlignX,
                        alignY = wallpaperAlignY,
                    )
                    tiledWallpaper -> Modifier.wallpaperWindow(
                        wallpaper = wallpaper,
                        fullWidth = fullWidth,
                        fullHeight = fullHeight,
                        origin = wallpaperOrigin,
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
            .then(
                if (editMode) Modifier
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
            is TileModel.App -> AppTileContent(tile, flipped = flipped, liveActive = liveActive)
            is TileModel.Folder -> FolderTileContent(tile)
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
        if (selected) TileControls()
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
 * `.tile-controls`): unpin (close) top-left, resize bottom-right. These are the
 * visual affordance; the taps are handled by the grid's [editDragGesture] via
 * the matching corner hot-zones (FR-3.4/3.5).
 */
@Composable
private fun BoxScope.TileControls() {
    TileControl(
        iconKey = "close",
        description = "unpin",
        modifier = Modifier.align(Alignment.TopStart),
    )
    TileControl(
        iconKey = "resize",
        description = "resize",
        modifier = Modifier.align(Alignment.BottomEnd),
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
 * screen, a close button, the lowercase folder title, and a grid of medium child
 * tiles. Tapping a child launches it and dismisses; tapping the scrim dismisses;
 * long-pressing the title renames it (persisted). Long-press-dragging a child and
 * releasing it away from its slot pulls that app out of the folder back onto Start
 * ([onPullOut]) — the folder dissolves to a plain tile when one app is left and the
 * overlay then self-closes. Renders nothing when [folder] is null (closed, or the
 * folder was removed/emptied while open).
 */
@Composable
private fun FolderOverlay(
    folder: TileModel.Folder?,
    accent: Color,
    onClose: () -> Unit,
    onLaunchChild: (FolderChild) -> Unit,
    onRename: (String) -> Unit,
    onPullOut: (FolderChild) -> Unit,
    onResize: (FolderChild) -> Unit,
    onReorder: (List<FolderChild>) -> Unit,
) {
    if (folder == null) return
    val density = LocalDensity.current
    var renaming by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }

    // Local reorder state: ordered list of child keys (pkg/activity).
    val localOrder = remember(folder.id) {
        mutableStateListOf<String>().also { list ->
            list.addAll(folder.children.map { it.packageName + "/" + it.activityName })
        }
    }
    // Sync when DB pushes a new children list (resize changes size, not order).
    LaunchedEffect(folder.children) {
        val incoming = folder.children.map { it.packageName + "/" + it.activityName }
        if (incoming != localOrder.toList()) {
            localOrder.clear()
            localOrder.addAll(incoming)
        }
    }

    val childById = remember(folder.children) {
        folder.children.associateBy { it.packageName + "/" + it.activityName }
    }

    // Drag state for edit-mode reorder.
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    // Slot centres for hit-testing reorder swaps: key → centre in grid-local px.
    val slotCentres = remember { mutableStateMapOf<String, Offset>() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x8C08080C))
            .pointerInput(Unit) { detectTapGestures { if (!editMode) onClose() } },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Header row: close (left) + edit/done (right) or rename (centre).
            Box(modifier = Modifier.fillMaxWidth()) {
                // Close button — hidden while renaming.
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
                // Edit / Done button.
                Text(
                    text = if (editMode) "done" else "edit",
                    color = accent,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 14.dp, end = 20.dp)
                        .clickable {
                            if (editMode) {
                                // Commit reorder on exit.
                                val ordered = localOrder.mapNotNull { childById[it] }
                                onReorder(ordered)
                            }
                            editMode = !editMode
                            renaming = false
                        },
                )
            }

            // Title (long-press to rename, suppressed in edit mode) or inline editor.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 12.dp),
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
                text = if (editMode) "tap tile to resize  ·  drag to reorder  ·  drag out to remove"
                       else "tap to open  ·  long-press drag out to remove",
                color = DarkColorTokens.fg.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                textAlign = TextAlign.Center,
            )

            // Tile grid — uses child.size (not hardcoded MEDIUM).
            val childSpecs = localOrder.mapNotNull { key ->
                childById[key]?.let { TileSpec(key, it.size) }
            }

            DenseTileGrid(tiles = childSpecs, modifier = Modifier.fillMaxWidth()) { spec, slot, sizePx ->
                val child = childById[spec.id] ?: return@DenseTileGrid
                val appModel = TileModel.App(
                    id = spec.id,
                    position = 0,
                    size = child.size,
                    colorId = folder.colorId,
                    packageName = child.packageName,
                    activityName = child.activityName,
                    label = child.label,
                    iconKey = child.iconKey,
                )

                // Record the slot centre for reorder hit-testing.
                val centre = Offset(
                    slot.x + sizePx.width / 2f,
                    slot.y + sizePx.height / 2f,
                )
                slotCentres[spec.id] = centre

                // Pull-out threshold: 2.5× tile unit so reorder moves don't trigger it.
                val pullThreshold = sizePx.width * 2.5f

                val isDragging = dragKey == spec.id
                val thisDragOffset = if (isDragging) dragOffset else Offset.Zero
                val pulledOut = isDragging && dragOffset.getDistance() > pullThreshold

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                slot.x + thisDragOffset.x.roundToInt(),
                                slot.y + thisDragOffset.y.roundToInt(),
                            )
                        }
                        .zIndex(if (isDragging) 5f else 0f)
                        .graphicsLayer {
                            val s = if (isDragging) 1.06f else 1f
                            scaleX = s; scaleY = s
                            alpha = if (pulledOut) 0.45f else 1f
                            shadowElevation = if (isDragging) 16.dp.toPx() else 0f
                        }
                        .size(
                            with(density) { sizePx.width.toDp() },
                            with(density) { sizePx.height.toDp() },
                        )
                        .background(accent)
                        .then(
                            if (editMode) {
                                Modifier
                                    // Tap in edit mode = cycle size.
                                    .pointerInput(spec.id) {
                                        detectTapGestures { onResize(child) }
                                    }
                                    // Drag in edit mode = reorder (or pull-out if very far).
                                    .pointerInput(spec.id, pullThreshold) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                dragKey = spec.id
                                                dragOffset = Offset.Zero
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount
                                                // Compute dragged tile's current centre.
                                                val draggedCentre = Offset(
                                                    slot.x + dragOffset.x + sizePx.width / 2f,
                                                    slot.y + dragOffset.y + sizePx.height / 2f,
                                                )
                                                // Swap with whichever tile's resting centre
                                                // the dragged centre is nearest to.
                                                val hoveredKey = slotCentres
                                                    .filter { (k, _) -> k != spec.id }
                                                    .minByOrNull { (_, c) ->
                                                        (c - draggedCentre).getDistance()
                                                    }
                                                    ?.takeIf { (_, c) ->
                                                        (c - draggedCentre).getDistance() <
                                                            sizePx.width * 0.6f
                                                    }
                                                    ?.key
                                                if (hoveredKey != null) {
                                                    val newOrder = reorderTiles(
                                                        localOrder.toList(),
                                                        spec.id,
                                                        hoveredKey,
                                                    )
                                                    if (newOrder != localOrder.toList()) {
                                                        localOrder.clear()
                                                        localOrder.addAll(newOrder)
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                if (dragOffset.getDistance() > pullThreshold) {
                                                    onPullOut(child)
                                                } else {
                                                    val ordered = localOrder.mapNotNull { childById[it] }
                                                    onReorder(ordered)
                                                }
                                                dragKey = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                dragKey = null
                                                dragOffset = Offset.Zero
                                            },
                                        )
                                    }
                            } else {
                                Modifier
                                    // Normal mode: tap = launch.
                                    .pointerInput(spec.id) {
                                        detectTapGestures { onLaunchChild(child) }
                                    }
                                    // Normal mode: long-press-drag = pull out.
                                    .pointerInput(spec.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                dragKey = spec.id
                                                dragOffset = Offset.Zero
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount
                                            },
                                            onDragEnd = {
                                                val dist = dragOffset.getDistance()
                                                if (dist > sizePx.height * 0.7f) onPullOut(child)
                                                dragKey = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                dragKey = null
                                                dragOffset = Offset.Zero
                                            },
                                        )
                                    }
                            }
                        ),
                ) {
                    AppTileContent(appModel)
                    // Edit-mode overlay: show size label.
                    if (editMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.55f),
                                    RoundedCornerShape(3.dp),
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = child.size.name.lowercase(),
                                color = Color.White,
                                fontSize = 9.sp,
                            )
                        }
                    }
                }
            }
        }
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
    order: List<String>,
    byId: Map<String, TileModel>,
    draggingId: () -> String?,
    selectedId: () -> String?,
    onUnpin: (String) -> Unit,
    onResize: (String) -> Unit,
    onLift: (id: String, offset: IntOffset) -> Unit,
    onDrag: (offset: IntOffset) -> Unit,
    onReorderTo: (dragId: String, targetId: String) -> Unit,
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
): Modifier = pointerInput(editMode, widthPx, byId, selectedId()) {
    // Re-keyed on byId so a resize/unpin mid-session refreshes the captured tile
    // sizes, and on the selected id so an in-edit selection switch refreshes the
    // corner-control target; neither changes mid-drag, so a live drag is safe.
    if (!editMode) return@pointerInput
    val geom = GridGeometry.of(widthPx)
    val slop = 7.dp.toPx()

    fun placementsNow(): List<TilePlacement> =
        GridPacker.pack(order.mapNotNull { id -> byId[id]?.let { TileSpec(id, it.size) } })

    // The other tiles packed *without* [exclude] (the dragged tile). Because a
    // drag only ever moves the dragged tile within the order, this layout is
    // invariant for the whole gesture — so a merge target never slips out from
    // under the finger the way it does in the dragged-included layout.
    fun othersPacked(exclude: String): List<TilePlacement> =
        GridPacker.pack(
            order.filter { it != exclude }
                .mapNotNull { id -> byId[id]?.let { TileSpec(id, it.size) } },
        )

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)

        // Corner controls on the selected tile (FR-3.4/3.5): a tap in the
        // top-left zone unpins, bottom-right resizes. Handled here (not as
        // child buttons) so the grid owns all edit interaction; the events are
        // consumed so the empty-space-exit never also fires.
        val sel = selectedId()
        val selPlacement = sel?.let { id -> placementsNow().firstOrNull { it.id == id } }
        if (selPlacement != null) {
            val r = geom.rect(selPlacement)
            val zone = 30.dp.toPx()
            val inUnpin = down.position.x <= r.left + zone && down.position.y <= r.top + zone
            val inResize = down.position.x >= r.right - zone && down.position.y >= r.bottom - zone
            if (inUnpin || inResize) {
                var movedCtl = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if ((change.position - down.position).getDistance() > slop) movedCtl = true
                    if (!change.pressed) {
                        if (!movedCtl) if (inUnpin) onUnpin(selPlacement.id) else onResize(selPlacement.id)
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

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val pos = change.position
            if (!moved && (pos - down.position).getDistance() > slop) moved = true

            if (startId != null && !lifted && moved) {
                lifted = true
                val r = geom.rect(placementsNow().first { it.id == startId })
                grab = down.position - r.topLeft
                onLift(startId, (pos - grab).round())
            }

            if (lifted) {
                change.consume()
                onDrag((pos - grab).round())

                // Merge (FR-3.3) vs reorder (FR-3.2). Merge is detected against
                // the OTHER tiles packed without the dragged tile — a layout that
                // stays put — so hovering a tile's centre 22–78% reliably catches
                // it. Entering a merge target settles the others under the finger
                // (the dragged tile is parked at the end) and highlights it.
                // Otherwise the live, dragged-included layout drives the reorder,
                // so the gap keeps following the finger.
                // The other tile (if any) directly under the finger, then whether
                // it should be the merge target. Entering needs the 22–78% centre;
                // staying only needs the finger to remain on the same tile (sticky)
                // so a near-centre wobble doesn't drop the merge into a reorder.
                val hovered = startId?.let { drag ->
                    othersPacked(drag).firstOrNull { geom.rect(it).contains(pos) }
                }
                val mergeHovered = hovered?.takeIf {
                    heldAsMergeTarget(geom.rect(it), pos, alreadyTarget = it.id == mergeId)
                }

                if (mergeHovered != null && startId != null) {
                    lastTarget = null
                    if (mergeId != mergeHovered.id) {
                        mergeId = mergeHovered.id
                        onMergeTarget(mergeHovered.id)
                        onMergeMode(startId)
                    }
                } else {
                    if (mergeId != null) { mergeId = null; onMergeTarget(null) }
                    val target = placementsNow().firstOrNull {
                        it.id != startId && geom.rect(it).contains(pos)
                    }
                    if (target == null) {
                        lastTarget = null
                    } else if (target.id != lastTarget) {
                        lastTarget = target.id
                        startId?.let { onReorderTo(it, target.id) }
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
) {
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
                fallback = staticGlyph,
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
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        LiveFace.MUSIC -> {
            MusicTileFace(
                flipped = flipped,
                active = liveActive,
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
                    packageName = tile.packageName,
                    fallback = {
                        NotificationTileFace(
                            packageName = tile.packageName,
                            fallback = staticGlyph,
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
        Column(modifier = Modifier.fillMaxSize().padding(11.dp)) {
            TileIconContent(34)
            Spacer(Modifier.weight(1f))
            TileLabel(tile.label.orEmpty())
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
private fun FolderTileContent(tile: TileModel.Folder) {
    Column(modifier = Modifier.fillMaxSize().padding(9.dp)) {
        // 2×2 mini-grid of the first four child glyphs (folder face — refined in S16).
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            for (rowIndex in 0 until 2) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    for (colIndex in 0 until 2) {
                        val child = tile.children.getOrNull(rowIndex * 2 + colIndex)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(2.dp)
                                .background(Color(0x2E000000)),
                            contentAlignment = Alignment.Center,
                        ) {
                            FolderChildIcon(child)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        TileLabel(tile.name)
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
private fun launchWebSearch(context: Context, query: String) {
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
