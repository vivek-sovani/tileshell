package com.tileshell.feature.start

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
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
import com.tileshell.feature.livetiles.CalendarTileFace
import com.tileshell.feature.livetiles.ClockTileFace
import com.tileshell.feature.livetiles.ConversationTileFace
import com.tileshell.feature.livetiles.LiveFace
import com.tileshell.feature.livetiles.MusicTileFace
import com.tileshell.feature.livetiles.NotificationAccess
import com.tileshell.feature.livetiles.NotificationCenter
import com.tileshell.feature.livetiles.NotificationSnapshot
import com.tileshell.feature.livetiles.NotificationTileFace
import com.tileshell.feature.livetiles.PeopleTileFace
import com.tileshell.feature.livetiles.PhotosData
import com.tileshell.feature.livetiles.PhotosStore
import com.tileshell.feature.livetiles.PhotosTileFace
import com.tileshell.feature.livetiles.WeatherTileFace
import com.tileshell.feature.livetiles.rememberFlipState
import com.tileshell.feature.livetiles.rememberLiveTilesActive
import com.tileshell.feature.livetiles.rememberNotificationAccess
import com.tileshell.feature.personalize.PersonalizeSheet
import com.tileshell.core.design.DarkColorTokens
import com.tileshell.core.design.Glass
import com.tileshell.core.design.LocalAccent
import com.tileshell.core.design.LocalColorTokens
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.Wallpapers
import com.tileshell.core.design.colorTokens
import com.tileshell.core.design.tiltOnPress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.abs

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
) {
    val tiles by viewModel.tiles.collectAsStateWithLifecycle()
    val swipeEnabled by viewModel.swipeEnabled.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val selectedTileId by viewModel.selectedTileId.collectAsStateWithLifecycle()
    val openFolderId by viewModel.openFolderId.collectAsStateWithLifecycle()
    val personalizeOpen by viewModel.personalizeOpen.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // Live notification state (FR-1.2 badges, FR-2 mail/messages). Empty until the
    // user enables notification access, which keeps every tile static / un-badged.
    val notifications by NotificationCenter.snapshot.collectAsStateWithLifecycle()
    val notificationAccess = rememberNotificationAccess()
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

    // Active theme tokens + global accent (FR-7), provided down the tree so the
    // chrome (sheet, edit bar, app list) re-skins live when personalization changes.
    val tokens = colorTokens(settings.dark)
    val accent = TileAccents.forId(settings.accentId)
    val wallpaper = Wallpapers.forId(settings.wallpaperId)
    // Transparent-tile fill at the current slider (FR-7); null when glass is off.
    val glassFill = if (settings.glass) Glass.fill(settings.dark, settings.transparency) else null

    // System photo picker for a custom wallpaper. OpenDocument (not the photo
    // picker) so we can take a persistable read grant — required to reload the
    // photo after a reboot (see docs/DECISIONS.md S18).
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setCustomWallpaper(uri.toString())
        }
    }

    // Photos tile selection (FR-2). OpenMultipleDocuments (not the photo picker)
    // for the same reason as the wallpaper: a persistable read grant so the
    // slideshow survives a reboot (DECISIONS S18/S23).
    val photosStore = remember(context) { PhotosStore.create(context) }
    val photosCount = photosStore.data.collectAsStateWithLifecycle(initialValue = PhotosData())
        .value.uris.size
    val photosPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            scope.launch { photosStore.setUris(uris.map { it.toString() }) }
        }
    }

    val scrollState = rememberScrollState()
    // 0 = Start, 1 = App list.
    val progress = remember { Animatable(0f) }
    // Live tiles pause when Start is no longer the foreground surface: the app
    // list has taken over (>50% across), or an overlay sits above it (FR-2 gating).
    val appListShown by remember { derivedStateOf { progress.value >= 0.5f } }
    val liveSuspended = appListShown || openFolder != null || personalizeOpen
    val settleSpec = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    fun settleTo(target: Float) {
        scope.launch {
            progress.animateTo(target, settleSpec)
            viewModel.setAppList(target >= 0.5f)
        }
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
    ) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val viewportHeightPx = constraints.maxHeight.toFloat()
        val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()

        // Wallpaper layer (FR-7): selected gradient or custom photo, optionally
        // blurred. Drawn first so all content sits above it.
        WallpaperBackground(
            gradient = wallpaper,
            customWallpaperUri = settings.customWallpaperUri,
            blur = settings.blur,
        )

        // Horizontal pager gesture. Detection runs in the Initial pass so a
        // dominant horizontal drag is claimed before the vertical grid scroll
        // (a child) can consume it; vertical drags pass straight through.
        val pager = Modifier.pointerInput(swipeEnabled, widthPx) {
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
                        val target = (base - dx / widthPx).coerceIn(0f, 1f)
                        scope.launch { progress.snapTo(target) }
                    }
                    if (!change.pressed) break
                }
                if (horizontal) settleTo(if (progress.value >= 0.5f) 1f else 0f)
            }
        }

        // Blur the Start surface behind the folder overlay (prototype
        // backdrop-filter; real blur only takes effect API 31+, harmless below).
        val behindBlur = if (openFolder != null) 14.dp else 0.dp
        Box(modifier = Modifier.fillMaxSize().blur(behindBlur).then(pager)) {
            // Start page: parallaxes left and fades as the app list comes in.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -0.22f * widthPx * progress.value
                        alpha = 1f - 0.6f * progress.value
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
                    glassFill = glassFill,
                    glassLine = tokens.glassLine,
                    darkTheme = settings.dark,
                    notifications = notifications,
                    widthPx = widthPx,
                    viewportHeightPx = viewportHeightPx,
                    statusBarTopPx = statusBarTopPx,
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
        }

        // Personalize sheet overlay (edit bar → personalize, FR-7).
        PersonalizeSheet(
            visible = personalizeOpen,
            dark = settings.dark,
            accentId = settings.accentId,
            glass = settings.glass,
            transparency = settings.transparency,
            blur = settings.blur,
            wallpaperId = settings.wallpaperId,
            customWallpaper = settings.customWallpaperUri != null,
            onThemeChange = viewModel::setTheme,
            onAccentChange = viewModel::setAccent,
            onGlassChange = viewModel::setGlass,
            onTransparencyChange = viewModel::setTransparency,
            onBlurChange = viewModel::setBlur,
            onWallpaperChange = viewModel::setWallpaper,
            onPickCustomWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
            onResetLayout = {
                viewModel.resetLayout()
                Toast.makeText(context, "layout reset", Toast.LENGTH_SHORT).show()
            },
            photosSelected = photosCount,
            onPickPhotos = { photosPicker.launch(arrayOf("image/*")) },
            notificationsEnabled = notificationAccess,
            onNotificationAccess = {
                runCatching { context.startActivity(NotificationAccess.settingsIntent()) }
                    .onFailure {
                        Toast.makeText(context, "open settings to allow access", Toast.LENGTH_SHORT)
                            .show()
                    }
            },
            onDismiss = viewModel::closePersonalize,
        )

        // Full-screen folder overlay (FR-4).
        FolderOverlay(
            folder = openFolder,
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
            onRemoveChild = { child ->
                openFolder?.let { viewModel.removeFolderChild(it.id, child) }
            },
        )

        // First-run hint (S19): one-time prototype hint card over Start. Sits
        // above all other layers so it reads on a fresh install; self-hides once
        // seen.
        FirstRunHint()
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
    glassFill: Color?,
    glassLine: Color,
    darkTheme: Boolean,
    notifications: NotificationSnapshot,
    widthPx: Float,
    viewportHeightPx: Float,
    statusBarTopPx: Float,
    onTile: (TileModel) -> Unit,
    onChevron: () -> Unit,
    onEnterEdit: (String) -> Unit,
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
                .navigationBarsPadding(),
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
                        glassFill = glassFill,
                        glassLine = glassLine,
                        jigglePhase = jigglePhase,
                        flipped = flipState.isFlipped(model.id),
                        liveActive = liveActive,
                        badgeCount = (model as? TileModel.App)
                            ?.let { notifications.badgeFor(it.packageName) } ?: 0,
                        darkTheme = darkTheme,
                        onTap = { if (!editMode) onTile(model) },
                        onLongPress = { if (!editMode) onEnterEdit(model.id) },
                    )
                }
            }
            // FR-1 bottom breathing room (prototype home-scroll padding-bottom:74px;
            // grows to clear the edit bar while editing, like .home-scroll padding).
            Spacer(Modifier.height(if (editMode) 130.dp else 74.dp))
        }

        // App-list affordance (prototype .allapps-btn): hidden in edit mode.
        if (chevronVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 14.dp, bottom = 26.dp)
                    .size(40.dp)
                    .clickable(onClick = onChevron),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TileIcons["chevron"],
                    contentDescription = "open app list",
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(28.dp),
                )
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

@Composable
private fun TileView(
    tile: TileModel,
    index: Int,
    editMode: Boolean,
    selected: Boolean,
    dragging: Boolean,
    mergeTarget: Boolean,
    glassFill: Color?,
    glassLine: Color,
    jigglePhase: Float,
    flipped: Boolean,
    liveActive: Boolean,
    badgeCount: Int,
    darkTheme: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
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
            // Glass mode (FR-7): translucent fill + inset hairline replaces the
            // accent background; otherwise the per-tile accent colour.
            .background(glassFill ?: TileAccents.forId(tile.colorId))
            .then(if (glassFill != null) Modifier.border(1.dp, glassLine) else Modifier)
            // Merge-target highlight (prototype .merge-target: 3px inset outline).
            .then(
                if (mergeTarget) Modifier.border(3.dp, DarkColorTokens.fg) else Modifier,
            )
            // Out of edit mode the tile owns tap-to-launch / long-press-to-edit;
            // in edit mode the grid-level drag gesture owns all interaction.
            .then(
                if (editMode) Modifier
                else Modifier.tileGesture(onTap = onTap, onLongPress = onLongPress),
            ),
    ) {
        when (tile) {
            is TileModel.App -> AppTileContent(tile, flipped = flipped, liveActive = liveActive)
            is TileModel.Folder -> FolderTileContent(tile)
        }
        // Glass small tiles lose their colour fill, so a top-right accent dot
        // keeps the tile's colour identity (prototype `#screen.glass .tile.small
        // .accentdot`). Larger glass tiles show the icon/label instead.
        if (glassFill != null && tile.size == TileSize.SMALL) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(8.dp)
                    .background(TileAccents.forId(tile.colorId), CircleShape),
            )
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
    Box(
        modifier = modifier
            .size(26.dp)
            .background(DarkColorTokens.fg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TileIcons[iconKey],
            contentDescription = description,
            tint = DarkColorTokens.bg,
            modifier = Modifier.size(15.dp),
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
 * long-pressing the title renames it (persisted). Long-pressing a child enters
 * edit mode (children jiggle, each shows an unpin × control); tapping × removes
 * that app from the folder ([onRemoveChild]) — the folder dissolves to a plain
 * tile when one app is left and the overlay then self-closes. Renders nothing
 * when [folder] is null (closed, or the folder was removed/emptied while open).
 */
@Composable
private fun FolderOverlay(
    folder: TileModel.Folder?,
    onClose: () -> Unit,
    onLaunchChild: (FolderChild) -> Unit,
    onRename: (String) -> Unit,
    onRemoveChild: (FolderChild) -> Unit,
) {
    if (folder == null) return
    val density = LocalDensity.current
    var renaming by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    // Leaving edit mode when the folder changes identity keeps the × controls from
    // lingering after a dissolve/close.
    LaunchedEffect(folder.id) { editing = false }
    val jigglePhase = rememberJigglePhase(editing)
    // The close button and scrim first exit edit mode, then dismiss the overlay.
    val dismiss = { if (editing) editing = false else onClose() }

    val childSpecs = remember(folder.children) {
        folder.children.map { TileSpec(it.packageName + "/" + it.activityName, TileSize.MEDIUM) }
    }
    val childById = remember(folder.children) {
        folder.children.associateBy { it.packageName + "/" + it.activityName }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Scrim (prototype rgba(8,8,12,.55)); tap to dismiss (or exit edit).
            .background(Color(0x8C08080C))
            .pointerInput(editing) { detectTapGestures { dismiss() } },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Close button, top-right.
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 14.dp)
                        .size(34.dp)
                        .pointerInput(editing) { detectTapGestures { dismiss() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TileIcons["close"],
                        contentDescription = if (editing) "done editing folder" else "close folder",
                        tint = DarkColorTokens.fg,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            // Title (long-press to rename) or the inline rename editor.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 30.dp, bottom = 18.dp),
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
                            detectTapGestures(onLongPress = { renaming = true })
                        },
                    )
                }
            }

            // Grid of medium child tiles (2 per row on the 4-column grid).
            DenseTileGrid(tiles = childSpecs, modifier = Modifier.fillMaxWidth()) { spec, slot, sizePx ->
                val child = childById[spec.id] ?: return@DenseTileGrid
                val childIndex = childSpecs.indexOfFirst { it.id == spec.id }
                val appModel = TileModel.App(
                    id = spec.id,
                    position = 0,
                    size = TileSize.MEDIUM,
                    colorId = folder.colorId,
                    packageName = child.packageName,
                    activityName = child.activityName,
                    label = child.label,
                    iconKey = child.iconKey,
                )
                val rotation = if (editing) {
                    if (childIndex % 2 == 0) jigglePhase else -jigglePhase
                } else {
                    0f
                }
                Box(
                    modifier = Modifier
                        .offset { slot }
                        .graphicsLayer { rotationZ = rotation }
                        .size(
                            with(density) { sizePx.width.toDp() },
                            with(density) { sizePx.height.toDp() },
                        )
                        .background(TileAccents.forId(folder.colorId))
                        // Tap launches when not editing; long-press enters edit mode.
                        .pointerInput(spec.id, editing) {
                            detectTapGestures(
                                onTap = { if (!editing) onLaunchChild(child) },
                                onLongPress = { editing = true },
                            )
                        },
                ) {
                    AppTileContent(appModel)
                    // Unpin control (prototype .tc-pin) — only while editing.
                    if (editing) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .size(22.dp)
                                .background(Color(0xCC1A1A22), CircleShape)
                                .pointerInput(spec.id) {
                                    detectTapGestures { onRemoveChild(child) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = TileIcons["close"],
                                contentDescription = "remove ${child.label ?: "app"} from folder",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
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
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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
 * animation — while not editing, so the resting Start screen never animates.
 */
@Composable
private fun rememberJigglePhase(editMode: Boolean): Float {
    if (!editMode) return 0f
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
 * a release within 7 px is a tap (launch); holding 430 ms fires the long-press
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
        // null = 430 ms elapsed still pressed (long-press → enter edit).
        val outcome = withTimeoutOrNull(430L) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
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
    onTapExit: () -> Unit,
    contentTopPx: Float,
    viewportHeightPx: Float,
    scrollOffsetPx: () -> Float,
    edgeZonePx: Float,
): Modifier = pointerInput(editMode, widthPx, byId) {
    // Re-keyed on byId so a resize/unpin mid-session refreshes the captured tile
    // sizes; byId never changes during a drag, so an in-progress drag is safe.
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
                val mergeHovered = startId?.let { drag ->
                    othersPacked(drag)
                        .firstOrNull { geom.rect(it).contains(pos) }
                        ?.takeIf { inMergeZone(geom.rect(it), pos) }
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
                if (lifted || draggingId() != null) onDrop(mergeId) else if (!moved) onTapExit()
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
            // No dedicated live face: any app tile still goes live when its own
            // package has an active notification (FR-2.3 — notification tiles for
            // all other apps). Small tiles stay static (the badge carries the
            // count); medium+ show the latest notification, else the static glyph.
            if (tile.size != TileSize.SMALL) {
                NotificationTileFace(
                    packageName = tile.packageName,
                    fallback = staticGlyph,
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

private fun onTileClick(context: Context, tile: TileModel) {
    when (tile) {
        is TileModel.App ->
            // Self-contained live tiles (weather/calendar with no resolved app)
            // carry a blank launch target — tapping is inert, not an error toast.
            if (tile.packageName.isNotBlank() &&
                !AppLauncher.launch(context, tile.packageName, tile.activityName)
            ) {
                Toast.makeText(
                    context,
                    "couldn't open ${tile.label ?: "app"}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        // Folder overlay arrives in S16; tapping is inert for now.
        is TileModel.Folder -> Unit
    }
}
