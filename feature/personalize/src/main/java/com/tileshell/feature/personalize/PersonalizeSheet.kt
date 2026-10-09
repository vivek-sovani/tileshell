package com.tileshell.feature.personalize

import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.tileshell.core.design.HubFilter
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.tileshell.core.design.HubAppBarAction
import com.tileshell.core.design.HubAppBar
import com.tileshell.core.design.HubPanorama
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.tileshell.core.design.Glass
import com.tileshell.core.design.SquircleShape
import com.tileshell.core.design.isLightBackground
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.tileshell.core.data.settings.FontStyle
import com.tileshell.core.data.settings.LiveRefreshRate
import com.tileshell.core.data.settings.resolveMs
import com.tileshell.core.data.settings.TileColorSource
import com.tileshell.core.data.settings.TileFill
import com.tileshell.core.data.settings.HomeStyle
import com.tileshell.core.data.settings.IconShape
import com.tileshell.core.data.settings.MonochromeIconTint
import com.tileshell.core.data.settings.TilePackMode
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.WallpaperGradient
import com.tileshell.core.design.Wallpapers
import com.tileshell.core.design.Wallpapers.NONE_ID
import com.tileshell.core.design.colorTokens
import com.tileshell.core.design.wallpaperBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The personalize bottom sheet (FR-7): a slide-up panel over a fading scrim with
 * a grip, a lowercase "personalize" title and the full prototype `buildSettings`
 * groups — dark/light/auto segmented toggle, 14 accent swatches, transparent-tiles +
 * blur-wallpaper toggles, a tile-transparency slider, the wallpaper row (custom
 * photo + 6 bundled gradients) and a reset-start-layout action. Stateless — it
 * renders the passed values and reports changes via the callbacks, so the host
 * persists them and feeds the new values straight back, re-skinning live.
 */
/** A subscribed news feed shown in the feeds-management list (framework-free). */
data class FeedSourceItem(val url: String, val name: String, val category: String, val enabled: Boolean)

/** The five mutually-exclusive kinds of Start wallpaper, for the type selector below. */
private enum class WallpaperType { NONE, PHOTO, SLIDESHOW, BING, STOCK }

/**
 * Which [WallpaperType] is currently active, derived from the existing persisted
 * flags (there's no separate stored "type" — it's implied by which of these is
 * set, same priority order the data layer already enforces as mutually exclusive).
 */
private fun currentWallpaperType(
    wallpaperId: String,
    customWallpaper: Boolean,
    bingWallpaper: Boolean,
    wallpaperSlideshowEnabled: Boolean,
): WallpaperType = when {
    bingWallpaper -> WallpaperType.BING
    wallpaperSlideshowEnabled -> WallpaperType.SLIDESHOW
    customWallpaper -> WallpaperType.PHOTO
    wallpaperId != NONE_ID -> WallpaperType.STOCK
    else -> WallpaperType.NONE
}

/**
 * The three mutually-exclusive tile-background styles, selected the same way as
 * [WallpaperType] above — a segmented row, picking one applies it immediately.
 */
/** Swatches per row in the stock-wallpaper picker grid. */
private const val WALLPAPER_GRID_COLUMNS = 3

private enum class TileBackgroundStyle { NONE, TRANSPARENT, BEHIND_TILES, BORDERLESS }

/** Which [TileBackgroundStyle] is active, derived from the same three mutually exclusive flags. */
private fun currentTileBackgroundStyle(
    glass: Boolean,
    tiledWallpaper: Boolean,
    borderlessTiles: Boolean,
): TileBackgroundStyle = when {
    tiledWallpaper -> TileBackgroundStyle.BEHIND_TILES
    glass -> TileBackgroundStyle.TRANSPARENT
    borderlessTiles -> TileBackgroundStyle.BORDERLESS
    else -> TileBackgroundStyle.NONE
}

@Composable
fun PersonalizeSheet(
    visible: Boolean,
    // True from opening personalize until it is closed, including while one
    // of its sub-screens (backup, about…) hides it; while true the panorama
    // comes back on the section it was left on.
    sessionOpen: Boolean = visible,
    dark: Boolean,
    accentId: String,
    glass: Boolean,
    transparency: Float,
    blur: Boolean,
    wallpaperId: String,
    customWallpaper: Boolean,
    bingWallpaper: Boolean,
    onBingWallpaperChange: (Boolean) -> Unit,
    onBingHistory: () -> Unit,
    // The custom wallpaper is a Bing image someone picked (not daily mode).
    bingPicked: Boolean = false,
    // The recent Bing images, inline, for bing's "select" choice.
    bingRecentImages: @Composable () -> Unit = {},
    // Fetches today's Bing image again (bing "daily").
    onRefreshBing: () -> Unit = {},
    // The wallpaper is a photo picked as "photo" (not a slide or Bing image).
    photoWallpaper: Boolean? = null,
    // Choosing "photo": brings back the last photo set, else opens the picker.
    onSelectPhotoType: (() -> Unit)? = null,
    /** "bing" tapped: brings back the day picked before, else turns daily on. */
    onSelectBingType: (() -> Unit)? = null,
    // The current photo wallpaper, previewed under "photo", and its framing.
    customWallpaperUri: String? = null,
    wallpaperAlignX: Float = 0.5f,
    wallpaperAlignY: Float = 0.5f,
    onAdjustWallpaper: () -> Unit,
    wallpaperSlideshowEnabled: Boolean,
    onWallpaperSlideshowChange: (Boolean) -> Unit,
    wallpaperSlideshowIntervalMin: Int,
    onWallpaperSlideshowIntervalChange: (Int) -> Unit,
    wallpaperSlideshowUris: List<String>,
    onPickWallpaperSlideshowPhotos: () -> Unit,
    onClearWallpaperSlideshowPhotos: () -> Unit,
    onRemoveWallpaperSlideshowPhoto: (String) -> Unit,
    tiledWallpaper: Boolean,
    onTiledWallpaperChange: (Boolean) -> Unit,
    borderlessTiles: Boolean,
    onBorderlessTilesChange: (Boolean) -> Unit,
    tileOutline: Boolean,
    onTileOutlineChange: (Boolean) -> Unit,
    feedEnabled: Boolean,
    onFeedEnabledChange: (Boolean) -> Unit,
    feedNoBackground: Boolean,
    onFeedNoBackgroundChange: (Boolean) -> Unit,
    userName: String,
    onUserNameChange: (String) -> Unit,
    onSystemSettings: () -> Unit,
    followSystemTheme: Boolean,
    onFollowSystemThemeChange: (Boolean) -> Unit,
    onThemeChange: (dark: Boolean) -> Unit,
    onAccentChange: (id: String) -> Unit,
    onGlassChange: (Boolean) -> Unit,
    onTransparencyChange: (Float) -> Unit,
    onBlurChange: (Boolean) -> Unit,
    onWallpaperChange: (id: String) -> Unit,
    // Switching to the "stock" type tab defaults to the first gradient just
    // so the section isn't blank — a plain apply, never the "which real
    // screen(s) should this go to" chooser [onWallpaperChange] triggers,
    // since the user hasn't deliberately picked a specific wallpaper yet.
    onSelectStockWallpaperType: () -> Unit,
    onPickCustomWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onResetTileStyle: () -> Unit,
    photoUris: List<String>,
    onPickPhotos: () -> Unit,
    isDefaultLauncher: Boolean,
    onSetDefaultLauncher: () -> Unit,
    cornerRadius: Float,
    onCornerRadiusChange: (Float) -> Unit,
    tileGap: Float,
    onTileGapChange: (Float) -> Unit,
    tileColorSource: TileColorSource,
    onTileColorSourceChange: (TileColorSource) -> Unit,
    // Live preview swatch for the "wallpaper" tile-colour-source pill — the same
    // wallpaper-derived accent the feed/glance page and Quick Panel already show,
    // so the user can see the actual colour before switching to it.
    wallpaperAccentPreview: Color,
    tileFill: TileFill,
    onTileFillChange: (TileFill) -> Unit,
    fontStyle: FontStyle,
    onFontStyleChange: (FontStyle) -> Unit,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    tilePackMode: TilePackMode,
    onTilePackModeChange: (TilePackMode) -> Unit,
    homeStyle: HomeStyle,
    onHomeStyleChange: (HomeStyle) -> Unit,
    iconShape: IconShape,
    onIconShapeChange: (IconShape) -> Unit,
    themedIcons: Boolean,
    onThemedIconsChange: (Boolean) -> Unit,
    monochromeIconTint: MonochromeIconTint,
    onMonochromeIconTintChange: (MonochromeIconTint) -> Unit,
    lockLayout: Boolean,
    onLockLayoutChange: (Boolean) -> Unit,
    hideStatusBar: Boolean,
    onHideStatusBarChange: (Boolean) -> Unit,
    doubleTapLock: Boolean = false,
    onDoubleTapLockChange: (Boolean) -> Unit = {},
    onClearPhotos: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    /** Master on/off switch for live-tile flipping/updates. */
    liveTilesEnabled: Boolean,
    onLiveTilesEnabledChange: (Boolean) -> Unit,
    /** How often stock/commodity/sports tiles re-poll — a battery/data tradeoff, default matches the original hardcoded cadence. */
    weatherRefreshRate: LiveRefreshRate,
    onWeatherRefreshRateChange: (LiveRefreshRate) -> Unit,
    newsRefreshRate: LiveRefreshRate,
    onNewsRefreshRateChange: (LiveRefreshRate) -> Unit,
    stockRefreshRate: LiveRefreshRate,
    onStockRefreshRateChange: (LiveRefreshRate) -> Unit,
    commodityRefreshRate: LiveRefreshRate,
    onCommodityRefreshRateChange: (LiveRefreshRate) -> Unit,
    sportsRefreshRate: LiveRefreshRate,
    onSportsRefreshRateChange: (LiveRefreshRate) -> Unit,
    /** Badges & live mail — grouped with live tiles, since it feeds their content. */
    notificationsEnabled: Boolean,
    onNotificationAccess: () -> Unit,
    batteryOptimizationExempt: Boolean,
    batteryGuidanceNote: String,
    onBatteryExemption: () -> Unit,
    onAbout: () -> Unit,
    onPersonalizeGuide: () -> Unit,
    onFolders: () -> Unit,
    onHiddenApps: () -> Unit,
    edgeStripEnabled: Boolean,
    onEdgeStrip: () -> Unit,
    onOpenBackup: (BackupSection) -> Unit,
    /** Opens the contacts/calendar/location/physical-activity permissions sub-sheet. */
    onPermissions: () -> Unit,
    /** Opens the news-region picker sub-sheet. */
    onNewsRegion: () -> Unit,
    /** Total selectable news regions (static descriptive count, not a live selection count). */
    newsRegionCount: Int,
    onDismiss: () -> Unit,
    // In landscape the launcher splits into a feed (left) + Start (right) panel;
    // the sheet then docks to the right half over Start instead of full width.
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Slide/fade progress (prototype .sheet transition: .3s cubic-bezier).
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "sheetProgress",
    )
    if (!sessionOpen) PersonalizeSectionMemory.page = 0
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    // The sheet's own chrome (selected pills, sliders, highlights) must match
    // whichever accent tiles themselves are actually using — when the user has
    // picked "wallpaper" as the tile colour source, that's the wallpaper-
    // derived colour (wallpaperAccentPreview, already resolved with the same
    // no-wallpaper fallback Start/feed/Quick Panel use), not the plain global
    // accentId. Real bug, user-reported: "wallpaper based accent color not
    // used in personalisation screen."
    val accent = if (tileColorSource == TileColorSource.WALLPAPER_ACCENT) {
        wallpaperAccentPreview
    } else {
        TileAccents.forId(accentId)
    }
    var showResetTileStyleConfirm by remember { mutableStateOf(false) }
    var showLiveTilesPermissionPrompt by remember { mutableStateOf(false) }
    // "live data refresh" opens as its own screen on top of the sheet.
    var liveRefreshOpen by remember { mutableStateOf(false) }

    // Android back / back-gesture closes the sheet. When a sub-sheet (about,
    // folders, bing history) is open on top, its own handler — registered later —
    // takes the back press first, so this closes personalize only once they're gone.
    BackHandler(enabled = visible) { onDismiss() }
    // Registered after the sheet's own handler, so it takes back first.
    BackHandler(enabled = visible && liveRefreshOpen) { liveRefreshOpen = false }

    if (showResetTileStyleConfirm) {
        AlertDialog(
            onDismissRequest = { showResetTileStyleConfirm = false },
            title = { Text("reset tile style?") },
            text = { Text("this resets corners, spacing, columns, fill, colour & font to their defaults.") },
            confirmButton = {
                TextButton(onClick = {
                    onResetTileStyle()
                    showResetTileStyleConfirm = false
                }) { Text("reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetTileStyleConfirm = false }) { Text("cancel") }
            },
        )
    }

    if (showLiveTilesPermissionPrompt) {
        AlertDialog(
            onDismissRequest = { showLiveTilesPermissionPrompt = false },
            title = { Text("allow live tile updates?") },
            text = {
                Text(
                    "live tiles show badges and mail/message previews using two permissions:\n\n" +
                        "• notification access — reads which apps have pending notifications\n" +
                        "• background activity — keeps updates flowing when the screen is off\n\n" +
                        "you can grant these now, or skip and enable them later from personalize.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLiveTilesPermissionPrompt = false
                    onNotificationAccess()
                }) { Text("continue") }
            },
            dismissButton = {
                TextButton(onClick = { showLiveTilesPermissionPrompt = false }) { Text("not now") }
            },
        )
    }

    val sectionPager = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = PersonalizeSectionMemory.page,
        pageCount = { PERSONALIZE_SECTIONS.size },
    )
    LaunchedEffect(sectionPager) {
        snapshotFlow { sectionPager.currentPage }.collect { PersonalizeSectionMemory.page = it }
    }

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        // Scrim (prototype rgba(0,0,0,.5)); tap to dismiss.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        // A Windows Phone panorama (user-requested): colours → wallpaper → tiles →
        // start → live → system, each section scrolling on its own.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxSize()
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.bg)
                // Swallow taps so they don't fall through to the scrim.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            HubPanorama(
                title = "personalize",
                sections = PERSONALIZE_SECTIONS,
                pagerState = sectionPager,
                tokens = tokens,
                modifier = Modifier.weight(1f),
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                ) {
                    when (page) {
                        0 -> { // colours
                        // ---- theme: dark/light/auto as small square tiles, matching the
                        // Quick Panel's WP-style tile grid (accent fill selected, neutral
                        // chip fill otherwise) instead of a plain segmented row ----
                        SettingGroup(label = "theme", tokens.fgDim) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ThemeTile(
                                    "moon", "dark", selected = !followSystemTheme && dark,
                                    accent = accent, tokens = tokens,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    onFollowSystemThemeChange(false)
                                    onThemeChange(true)
                                }
                                ThemeTile(
                                    "brightness", "light", selected = !followSystemTheme && !dark,
                                    accent = accent, tokens = tokens,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    onFollowSystemThemeChange(false)
                                    onThemeChange(false)
                                }
                                ThemeTile(
                                    "auto", "auto", selected = followSystemTheme,
                                    accent = accent, tokens = tokens,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    onFollowSystemThemeChange(true)
                                }
                            }
                        }
                        // ---- accent colour ----
                        SettingGroup(label = "accent colour", tokens.fgDim) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                TileAccents.swatches.chunked(7).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        row.forEach { (id, color) ->
                                            Swatch(
                                                color = color,
                                                selected = id == accentId,
                                                ring = tokens.fg,
                                                modifier = Modifier.weight(1f),
                                                onClick = { onAccentChange(id) },
                                            )
                                        }
                                        repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                        // ---- tile color source: same label-above / bordered-segmented-row
                        // convention every other selector on this sheet uses (home style,
                        // arrangement, wallpaper type) — a bespoke same-line label+pills
                        // row squeezed "wallpaper" down to near-zero width once it grew a
                        // swatch dot, wrapping its text one letter per line instead of
                        // just overflowing ----
                        SettingGroup(label = "tile color source", tokens.fgDim) {
                            // Two rows of two (like tile style) — four labels squeezed on
                            // one line.
                            Column(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                SegCell(
                                    "accent",
                                    selected = tileColorSource == TileColorSource.GLOBAL_ACCENT,
                                    accent = accent,
                                    fg = tokens.fg,
                                ) {
                                    onTileColorSourceChange(TileColorSource.GLOBAL_ACCENT)
                                }
                                SegCell(
                                    "multicolour",
                                    selected = tileColorSource == TileColorSource.MULTICOLOR,
                                    accent = accent,
                                    fg = tokens.fg,
                                ) {
                                    onTileColorSourceChange(TileColorSource.MULTICOLOR)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                SegCell(
                                    "app icon",
                                    selected = tileColorSource == TileColorSource.APP_ICON,
                                    accent = accent,
                                    fg = tokens.fg,
                                ) {
                                    onTileColorSourceChange(TileColorSource.APP_ICON)
                                }
                                // Carries an actual swatch dot sampled from the current
                                // wallpaper (the same colour the feed/glance page and Quick
                                // Panel already use) so the user sees the real colour before
                                // switching to it — accent/app-icon need no such preview
                                // (accent already fills the whole cell when selected; app
                                // icon has no single colour to show ahead of time).
                                SegCell(
                                    "wallpaper",
                                    selected = tileColorSource == TileColorSource.WALLPAPER_ACCENT,
                                    accent = accent,
                                    fg = tokens.fg,
                                    swatch = wallpaperAccentPreview,
                                ) {
                                    onTileColorSourceChange(TileColorSource.WALLPAPER_ACCENT)
                                }
                            }
                            }
                        }
                        // ---- typography ----
                        SettingGroup(label = "typography", tokens.fgDim) {
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                listOf(
                                    FontStyle.SYSTEM to "system",
                                    FontStyle.OUTFIT to "outfit",
                                    FontStyle.NUNITO to "nunito",
                                ).forEach { entry ->
                                    val style = entry.first
                                    val label = entry.second
                                    HubFilter(label, fontStyle == style, tokens, accent) { onFontStyleChange(style) }
                                }
                            }
                        }
                        }
                        2 -> { // wallpaper
                        // ---- wallpaper ----
                        SettingGroup(label = "wallpaper", tokens.fgDim) {
                            val currentWallpaper =
                                if (bingPicked && !wallpaperSlideshowEnabled) WallpaperType.BING
                                else currentWallpaperType(wallpaperId, photoWallpaper ?: customWallpaper, bingWallpaper, wallpaperSlideshowEnabled)

                            // Picking a type applies a sensible default immediately (opens the photo
                            // picker, turns slideshow/Bing on, picks the first stock gradient) — every
                            // transition reuses the same setters the old flat toggles called, which
                            // already clear the other, now-inactive types. The section below then asks
                            // for whatever more that type needs (which photo, which interval, …).
                            fun selectWallpaperType(type: WallpaperType) {
                                if (type == currentWallpaper) return
                                when (type) {
                                    WallpaperType.NONE -> onClearWallpaper()
                                    WallpaperType.PHOTO -> (onSelectPhotoType ?: onPickCustomWallpaper)()
                                    WallpaperType.SLIDESHOW -> onWallpaperSlideshowChange(true)
                                    WallpaperType.BING -> onSelectBingType?.invoke() ?: onBingWallpaperChange(true)
                                    WallpaperType.STOCK -> onSelectStockWallpaperType()
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                            ) {
                                val labels = listOf(
                                    WallpaperType.NONE to "none",
                                    WallpaperType.PHOTO to "photo",
                                    WallpaperType.SLIDESHOW to "slides",
                                    WallpaperType.BING to "bing",
                                    WallpaperType.STOCK to "stock",
                                )
                                labels.forEach { (type, label) ->
                                    SegCell(label, selected = type == currentWallpaper, accent = accent, fg = tokens.fg) {
                                        selectWallpaperType(type)
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))

                            when (currentWallpaper) {
                                WallpaperType.NONE -> Text(
                                    text = "flat theme background, no photo or pattern",
                                    color = tokens.fgDim,
                                    fontSize = 13.sp,
                                )
                                WallpaperType.PHOTO -> {
                                    WallpaperNavRow(
                                        "photo",
                                        if (photoWallpaper ?: customWallpaper) "change ›" else "choose ›",
                                        accent, tokens, onPickCustomWallpaper,
                                    )
                                    val isPhoto = photoWallpaper ?: customWallpaper
                                    if (isPhoto && customWallpaperUri != null) {
                                        WallpaperPreview(customWallpaperUri, wallpaperAlignX, wallpaperAlignY, tokens, onAdjustWallpaper)
                                    }
                                    if (isPhoto) {
                                        WallpaperNavRow("adjust position", "reframe ›", accent, tokens, onAdjustWallpaper)
                                    }
                                }
                                WallpaperType.SLIDESHOW -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(text = "every", color = tokens.fgDim, fontSize = 14.sp)
                                        Spacer(Modifier.weight(1f))
                                        listOf(15 to "15m", 30 to "30m", 60 to "1h", 180 to "3h").forEach { (min, label) ->
                                            HubFilter(label, wallpaperSlideshowIntervalMin == min, tokens, accent) {
                                                onWallpaperSlideshowIntervalChange(min)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "slideshow photos",
                                        color = tokens.fgDim,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(bottom = 6.dp),
                                    )
                                    PhotoGrid(
                                        uris = wallpaperSlideshowUris,
                                        accent = accent,
                                        tokens = tokens,
                                        onAdd = onPickWallpaperSlideshowPhotos,
                                        onRemove = onRemoveWallpaperSlideshowPhoto,
                                        onRemoveAll = onClearWallpaperSlideshowPhotos,
                                    )
                                }
                                WallpaperType.BING -> {
                                    // "daily" follows Bing's image of the day; "select"
                                    // shows the recent ones to pick from, right here.
                                    var selecting by remember { mutableStateOf(!bingWallpaper) }
                                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                        HubFilter("daily", bingWallpaper && !selecting, tokens, accent) {
                                            selecting = false
                                            if (!bingWallpaper) onBingWallpaperChange(true)
                                        }
                                        HubFilter("select", selecting || !bingWallpaper, tokens, accent) { selecting = true }
                                    }
                                    // The Bing image in use, as it sits on Start, with reframe.
                                    val showCurrent = customWallpaper && customWallpaperUri != null
                                    if (selecting || !bingWallpaper) {
                                        // Selected wallpaper first, then the recent ones to pick from.
                                        if (showCurrent) {
                                            WallpaperPreview(customWallpaperUri!!, wallpaperAlignX, wallpaperAlignY, tokens, onAdjustWallpaper)
                                            WallpaperNavRow("adjust position", "reframe ›", accent, tokens, onAdjustWallpaper)
                                        }
                                        Text(
                                            "tap one to set it as your wallpaper",
                                            color = tokens.fgDim,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
                                        )
                                        bingRecentImages()
                                    } else {
                                        Text(
                                            "a new image of the day every morning",
                                            color = tokens.fgDim,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                                        )
                                        WallpaperNavRow("refresh daily wallpaper", "refresh ›", accent, tokens, onRefreshBing)
                                        if (showCurrent) {
                                            WallpaperPreview(customWallpaperUri!!, wallpaperAlignX, wallpaperAlignY, tokens, onAdjustWallpaper)
                                            WallpaperNavRow("adjust position", "reframe ›", accent, tokens, onAdjustWallpaper)
                                        }
                                    }
                                }
                                WallpaperType.STOCK -> {
                                    // Fixed 3 per row. This used to be a hardcoded
                                    // take(3)/drop(3) pair, which silently meant "3, then
                                    // everything else" — adding a 7th wallpaper made the
                                    // second row four narrower cells than the first. Rows
                                    // are chunked instead, and a short final row is padded
                                    // with weighted spacers, so every swatch is the same
                                    // size whatever the list length.
                                    Wallpapers.all.chunked(WALLPAPER_GRID_COLUMNS)
                                        .forEachIndexed { index, row ->
                                            if (index > 0) Spacer(Modifier.height(10.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                row.forEach { wp ->
                                                    WallpaperCell(
                                                        wallpaper = wp,
                                                        selected = wp.id == wallpaperId,
                                                        ring = tokens.fg,
                                                        modifier = Modifier.weight(1f),
                                                        onClick = { onWallpaperChange(wp.id) },
                                                    )
                                                }
                                                repeat(WALLPAPER_GRID_COLUMNS - row.size) {
                                                    Spacer(Modifier.weight(1f))
                                                }
                                            }
                                        }
                                }
                            }

                        }
                        }
                        1 -> { // tiles
                        // ---- tile style (merged: background style + transparency/blur +
                        // shape/spacing + gradient fill + reset) ----
                        SettingGroup(label = "tile style", tokens.fgDim) {
                            val currentBackground =
                                currentTileBackgroundStyle(glass, tiledWallpaper, borderlessTiles)

                            // Same pattern as the wallpaper type selector above: picking an option
                            // applies it immediately (the two are already mutually exclusive at the
                            // data layer — SettingsRepository.setGlass/setTiledWallpaper), and the
                            // section below asks for whatever more that option needs.
                            fun selectBackground(style: TileBackgroundStyle) {
                                if (style == currentBackground) return
                                when (style) {
                                    TileBackgroundStyle.NONE -> {
                                        onGlassChange(false)
                                        onTiledWallpaperChange(false)
                                        onBorderlessTilesChange(false)
                                    }
                                    TileBackgroundStyle.TRANSPARENT -> onGlassChange(true)
                                    TileBackgroundStyle.BEHIND_TILES -> onTiledWallpaperChange(true)
                                    TileBackgroundStyle.BORDERLESS -> onBorderlessTilesChange(true)
                                }
                            }

                            // 2×2 rather than one 4-wide row: two of these four labels
                            // are now two words ("behind tiles", "widget cards"), which
                            // squeezed badly at quarter-width in a single row (user-
                            // reported "too crowded in horizontal space"). Each cell gets
                            // roughly double the room this way, at the cost of one extra
                            // row of height — the same trade the wallpaper swatch grid
                            // above already makes.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(),
                            ) {
                                val labels = listOf(
                                    TileBackgroundStyle.NONE to "none",
                                    TileBackgroundStyle.TRANSPARENT to "transparent",
                                    TileBackgroundStyle.BEHIND_TILES to "behind tiles",
                                    TileBackgroundStyle.BORDERLESS to "widget cards",
                                )
                                labels.chunked(2).forEachIndexed { index, row ->
                                    if (index > 0) HorizontalDivider(color = tokens.tileLine)
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        row.forEach { (style, label) ->
                                            SegCell(
                                                label,
                                                selected = style == currentBackground,
                                                accent = accent,
                                                fg = tokens.fg,
                                            ) {
                                                selectBackground(style)
                                            }
                                        }
                                    }
                                }
                            }

                            // The hairline only exists in the two styles that paint a tile
                            // surface the wallpaper shows through — a solid accent tile and a
                            // borderless one never draw one, so the toggle would be inert
                            // there. Off keeps the fill and drops just the edge line, so
                            // adjacent tiles read as one continuous surface (most noticeable
                            // in "behind tiles" at a small tile gap).
                            if (currentBackground == TileBackgroundStyle.TRANSPARENT ||
                                currentBackground == TileBackgroundStyle.BEHIND_TILES
                            ) {
                                Spacer(Modifier.height(14.dp))
                                ToggleRow("tile outline", on = tileOutline, accent = accent, tokens, onTileOutlineChange)
                            }

                            // Tile transparency controls the see-through amount for either
                            // style that paints a translucent surface: "transparent" (the
                            // accent-tinted glass fill) and "borderless" (its neutral
                            // widget-card fill) — the same slider, since both are "how
                            // much of the tile's own surface alpha shows through," just
                            // applied to a different base colour per style. Blur applies
                            // to "none"/"transparent"/"borderless" — all three render
                            // through the same non-tiled WallpaperBackground — but not
                            // "behind tiles": that mode has no single composable to blur
                            // (each tile draws its own window onto the wallpaper), and
                            // blurring every tile's window individually is prohibitively
                            // expensive (one RenderEffect layer per visible tile — tried
                            // it, caused an ANR).
                            if (currentBackground == TileBackgroundStyle.TRANSPARENT ||
                                currentBackground == TileBackgroundStyle.BORDERLESS
                            ) {
                                // Local draft so the thumb (and the % readout) track the
                                // finger at frame rate. The persisted value is debounced
                                // (StartViewModel's pendingSettingWrites) to avoid rewriting
                                // the whole settings blob on every frame of the drag, so
                                // binding straight to the persisted value would leave the
                                // slider visibly stuck mid-gesture. Re-keyed on the persisted
                                // value so an external change (reset tile style) still moves
                                // the thumb; no write lands mid-drag, so this can't fight the
                                // gesture.
                                var transparencyDraft by remember(transparency) { mutableFloatStateOf(transparency) }
                                Spacer(Modifier.height(14.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("tile transparency", color = tokens.fgDim, fontSize = 13.sp)
                                    Text("${(transparencyDraft * 100).roundToInt()}%", color = tokens.fgDim, fontSize = 13.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                Slider(
                                    value = transparencyDraft,
                                    onValueChange = { transparencyDraft = it; onTransparencyChange(it) },
                                    colors = SliderDefaults.colors(
                                        thumbColor = accent,
                                        activeTrackColor = accent,
                                        inactiveTrackColor = tokens.tileLine,
                                    ),
                                )
                            }
                            if (currentBackground != TileBackgroundStyle.BEHIND_TILES) {
                                Spacer(Modifier.height(14.dp))
                                ToggleRow("blur wallpaper", on = blur, accent = accent, tokens, onBlurChange)
                            }

                            // Corner radius, tile spacing, and gradient fill all
                            // have zero visible effect on a borderless tile — it
                            // uses its own fixed corner radius/gap (see
                            // BORDERLESS_CORNER_RADIUS_DP/_TILE_GAP_DP in
                            // StartScreen.kt) and never reads useTileGradient. Hiding
                            // all three while this style is active avoids showing
                            // controls that visibly do nothing.
                            if (currentBackground != TileBackgroundStyle.BORDERLESS) {
                                Spacer(Modifier.height(18.dp))
                                HorizontalDivider(color = tokens.tileLine)
                                Spacer(Modifier.height(18.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("corner radius", color = tokens.fgDim, fontSize = 13.sp)
                                    Text("${cornerRadius.roundToInt()}", color = tokens.fgDim, fontSize = 13.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .background(accent),
                                    )
                                    // Local draft so the thumb tracks the finger at frame rate.
                                    // The persisted value is debounced (StartViewModel's
                                    // pendingSettingWrites) to avoid rewriting the whole settings
                                    // blob on every frame of the drag, so binding the Slider
                                    // straight to the persisted value would leave it visibly
                                    // stuck mid-gesture. Re-keyed on the persisted value so an
                                    // external change (reset tile style) still moves the thumb;
                                    // no write lands mid-drag, so this can't fight the gesture.
                                    var cornerDraft by remember(cornerRadius) { mutableFloatStateOf(cornerRadius) }
                                    Slider(
                                        value = cornerDraft,
                                        onValueChange = { cornerDraft = it; onCornerRadiusChange(it) },
                                        valueRange = 0f..20f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = accent,
                                            activeTrackColor = accent,
                                            inactiveTrackColor = tokens.tileLine,
                                        ),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(accent),
                                    )
                                }
                                // Tile spacing — hidden when wallpaper-behind-tiles is on so wider
                                // gaps never fragment the show-through wallpaper.
                                if (!tiledWallpaper) {
                                    Spacer(Modifier.height(14.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("tile spacing", color = tokens.fgDim, fontSize = 13.sp)
                                        Text("${tileGap.roundToInt()}", color = tokens.fgDim, fontSize = 13.sp)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Box(Modifier.size(10.dp, 22.dp).clip(RoundedCornerShape(3.dp)).background(accent))
                                            Box(Modifier.size(10.dp, 22.dp).clip(RoundedCornerShape(3.dp)).background(accent))
                                        }
                                    // Local draft so the thumb tracks the finger at frame rate.
                                    // The persisted value is debounced (StartViewModel's
                                    // pendingSettingWrites) to avoid rewriting the whole settings
                                    // blob on every frame of the drag, so binding the Slider
                                    // straight to the persisted value would leave it visibly
                                    // stuck mid-gesture. Re-keyed on the persisted value so an
                                    // external change (reset tile style) still moves the thumb;
                                    // no write lands mid-drag, so this can't fight the gesture.
                                        var gapDraft by remember(tileGap) { mutableFloatStateOf(tileGap) }
                                        Slider(
                                            value = gapDraft,
                                            onValueChange = { gapDraft = it; onTileGapChange(it) },
                                            valueRange = 0f..16f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = accent,
                                                activeTrackColor = accent,
                                                inactiveTrackColor = tokens.tileLine,
                                            ),
                                            modifier = Modifier.weight(1f),
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Box(Modifier.size(10.dp, 22.dp).clip(RoundedCornerShape(3.dp)).background(accent))
                                            Box(Modifier.size(10.dp, 22.dp).clip(RoundedCornerShape(3.dp)).background(accent))
                                        }
                                    }
                                }

                                // Only where tiles have a colour to shade (solid or
                                // transparent); behind tiles shows the wallpaper itself.
                                if (!tiledWallpaper) {
                                    Spacer(Modifier.height(14.dp))
                                    ToggleRow(
                                        "gradient fill",
                                        on = tileFill == TileFill.GRADIENT,
                                        accent = accent,
                                        tokens,
                                        onChange = { on -> onTileFillChange(if (on) TileFill.GRADIENT else TileFill.FLAT) },
                                    )
                                }

                            }
                        }
                        // ---- grid columns ----
                        SettingGroup(label = "grid columns", tokens.fgDim) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "how many small tiles fit across a row",
                                    color = tokens.fgDim,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    "a medium tile spans 2 columns, a wide tile spans 4",
                                    color = tokens.fgDim,
                                    fontSize = 12.sp,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    listOf(4, 5, 6).forEach { count ->
                                        HubFilter(count.toString(), columns == count, tokens, accent) { onColumnsChange(count) }
                                    }
                                }
                            }
                        }
                        // ---- arrangement: compact sticky | free | dense segmented pill ----
                        SettingGroup(label = "arrangement", tokens.fgDim) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "how the grid closes gaps when a tile is removed or resized",
                                    color = tokens.fgDim,
                                    fontSize = 13.sp,
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                ) {
                                    SegCell("sticky", selected = tilePackMode == TilePackMode.STICKY, accent = accent, fg = tokens.fg) {
                                        onTilePackModeChange(TilePackMode.STICKY)
                                    }
                                    SegCell("free", selected = tilePackMode == TilePackMode.FREE, accent = accent, fg = tokens.fg) {
                                        onTilePackModeChange(TilePackMode.FREE)
                                    }
                                    SegCell("dense", selected = tilePackMode == TilePackMode.DENSE, accent = accent, fg = tokens.fg) {
                                        onTilePackModeChange(TilePackMode.DENSE)
                                    }
                                }
                                // Only shown for FREE, which is the one mode where dropping a
                                // tile onto another swaps them instead of pushing anything
                                // down or reflowing the grid.
                                if (tilePackMode == TilePackMode.FREE) {
                                    Text(
                                        "nothing moves unless you move it — dropping a tile onto another swaps the two",
                                        color = tokens.fgDim,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }

                        // ---- reset tile style: last in tiles (user-requested) ----
                        SettingGroup(label = "reset", tokens.fgDim) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showResetTileStyleConfirm = true }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(text = "reset tile style", color = tokens.fg, fontSize = 14.sp)
                                    Text(
                                        text = "corners, spacing, columns, fill, colour & font",
                                        color = tokens.fgDim,
                                        fontSize = 12.sp,
                                    )
                                }
                                Text(text = "↺", color = tokens.fgDim, fontSize = 16.sp)
                            }
                        }
                        }
                        3 -> { // start
                        // ---- home style: windows-phone tiles vs. android-style icons ----
                        SettingGroup(label = "home style", tokens.fgDim) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "how apps render on the small (1×1) size — bigger tiles, live tiles and folders look the same either way",
                                    color = tokens.fgDim,
                                    fontSize = 13.sp,
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                ) {
                                    SegCell("tiles", selected = homeStyle == HomeStyle.TILES, accent = accent, fg = tokens.fg) {
                                        onHomeStyleChange(HomeStyle.TILES)
                                    }
                                    SegCell("icons", selected = homeStyle == HomeStyle.ICONS, accent = accent, fg = tokens.fg) {
                                        onHomeStyleChange(HomeStyle.ICONS)
                                    }
                                }
                                // Icon shape only matters once there's an icon to mask —
                                // hidden entirely in TILES, where this setting is unused.
                                if (homeStyle == HomeStyle.ICONS) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("icon shape", color = tokens.fgDim, fontSize = 13.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        IconShape.entries.forEach { candidate ->
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(candidate.previewShape())
                                                        .then(
                                                            // ORIGINAL means "unmasked, no colour fill" — an
                                                            // outline-only swatch, so it reads as visually
                                                            // distinct from SQUARE's solid filled rectangle
                                                            // even though both preview the same plain shape.
                                                            if (candidate == IconShape.ORIGINAL) {
                                                                Modifier.border(1.dp, tokens.tileLine, candidate.previewShape())
                                                            } else {
                                                                Modifier.background(accent)
                                                            },
                                                        )
                                                        .then(
                                                            if (iconShape == candidate) {
                                                                Modifier.border(2.dp, tokens.fg, candidate.previewShape())
                                                            } else {
                                                                Modifier
                                                            },
                                                        )
                                                        .clickable { onIconShapeChange(candidate) },
                                                )
                                                Text(
                                                    candidate.name.lowercase(),
                                                    color = if (iconShape == candidate) tokens.fg else tokens.fgDim,
                                                    fontSize = 10.sp,
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                ToggleRow("monochrome icons", on = themedIcons, accent = accent, tokens, onThemedIconsChange)
                                if (themedIcons) {
                                    Text(
                                        "every app icon renders as a flat glyph — nothing-phone style",
                                        color = tokens.fgDim,
                                        fontSize = 12.sp,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                    ) {
                                        SegCell(
                                            "accent",
                                            selected = monochromeIconTint == MonochromeIconTint.ACCENT,
                                            accent = accent,
                                            fg = tokens.fg,
                                        ) { onMonochromeIconTintChange(MonochromeIconTint.ACCENT) }
                                        SegCell(
                                            "monochrome",
                                            selected = monochromeIconTint == MonochromeIconTint.NEUTRAL,
                                            accent = accent,
                                            fg = tokens.fg,
                                        ) { onMonochromeIconTintChange(MonochromeIconTint.NEUTRAL) }
                                    }
                                    Text(
                                        if (monochromeIconTint == MonochromeIconTint.ACCENT) {
                                            "glyphs tint to your accent colour"
                                        } else {
                                            "glyphs are a fixed black/white, independent of your accent colour"
                                        },
                                        color = tokens.fgDim,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                        // ---- pages: start is organized into always-available, swipeable
                        // pages (named sections + the trailing "main" page) — nothing to
                        // toggle here any more, "+ add page" just always works, like
                        // folders. See the guide sheet for how to add/rename/reorder. ----
                        // ---- folders & categories ----
                        SettingGroup(label = "folders & categories", tokens.fgDim) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onFolders)
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "create category folders", color = tokens.fg, fontSize = 14.sp)
                                    Text(
                                        text = "group apps by what they do — communication, social, shopping…",
                                        color = tokens.fgDim,
                                        fontSize = 12.sp,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(text = "›", color = accent, fontSize = 16.sp)
                            }
                        }
                        // ---- edge strip ----
                        SettingGroup(label = "edge strip", tokens.fgDim) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onEdgeStrip)
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "edge strip", color = tokens.fg, fontSize = 14.sp)
                                    Text(
                                        text = if (edgeStripEnabled) "enabled · tap to configure" else "optional shortcut strip at a screen edge",
                                        color = tokens.fgDim,
                                        fontSize = 12.sp,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (edgeStripEnabled) "on ›" else "›",
                                    color = accent,
                                    fontSize = 16.sp,
                                )
                            }
                        }
                        // ---- lock layout ----
                        SettingGroup(label = "layout", tokens.fgDim) {
                            ToggleRow("lock layout", on = lockLayout, accent = accent, tokens, onLockLayoutChange)
                            Text(
                                "when on, long-pressing a tile never opens edit mode — nothing can be moved, resized, or removed by accident",
                                color = tokens.fgDim,
                                fontSize = 12.sp,
                            )
                        }
                        }
                        4 -> { // live
                        // ---- live tiles: master on/off switch (flip animation only) +
                        // badges & live mail (its own row, kept independent — it's a
                        // notification-access grant, not something the master switch
                        // should imply is already asked for just by being on) ----
                        SettingGroup(label = "live tiles", tokens.fgDim) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ToggleRow(
                                    "live tile updates",
                                    on = liveTilesEnabled,
                                    accent = accent,
                                    tokens = tokens,
                                    onChange = onLiveTilesEnabledChange,
                                )
                                Text(
                                    text = "pauses clock/weather/notification flipping when off — badges and counts keep updating",
                                    color = tokens.fgDim,
                                    fontSize = 12.sp,
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = {
                                            if (notificationsEnabled) onNotificationAccess() else showLiveTilesPermissionPrompt = true
                                        })
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(text = "badges & live mail", color = tokens.fg, fontSize = 14.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = if (notificationsEnabled) "on ›" else "allow access ›",
                                        color = if (notificationsEnabled) accent else tokens.fgDim,
                                        fontSize = 13.sp,
                                    )
                                }
                                if (notificationsEnabled && !batteryOptimizationExempt) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = onBatteryExemption)
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "background activity",
                                                color = tokens.fg,
                                                fontSize = 14.sp,
                                            )
                                            Text(
                                                text = if (batteryGuidanceNote.isNotEmpty()) {
                                                    batteryGuidanceNote
                                                } else {
                                                    "exempt from battery optimisation for reliable badges"
                                                },
                                                color = tokens.fgDim,
                                                fontSize = 12.sp,
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(text = "fix ›", color = accent, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        // ---- live data refresh: per-category poll interval, a battery/data
                        // tradeoff — "default" keeps each category's original cadence
                        // (stock/commodity 60s, sports 90s); the tile itself also slows
                        // stock/commodity polling further outside 9am-4pm weekday market
                        // hours regardless of which rate is picked here. ----
                        SettingGroup(label = "live data refresh", tokens.fgDim) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { liveRefreshOpen = true }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "refresh rates", color = tokens.fg, fontSize = 14.sp)
                                    Text(
                                        text = "how often weather, news, stocks, commodities and sports update",
                                        color = tokens.fgDim,
                                        fontSize = 12.sp,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(text = "›", color = accent, fontSize = 16.sp)
                            }
                        }
                        // ---- live photos (FR-2 photos tile) ----
                        SettingGroup(label = "live photos", tokens.fgDim) {
                            PhotoGrid(
                                uris = photoUris,
                                accent = accent,
                                tokens = tokens,
                                onAdd = onPickPhotos,
                                onRemove = onRemovePhoto,
                                onRemoveAll = onClearPhotos,
                            )
                        }
                        }
                        5 -> { // glance
                        // ---- feed & glance ---- (always reachable here, unlike the feed
                        // page's own gear-icon settings sheet, which becomes unreachable
                        // the moment "show feed page" is turned off from inside it)
                        SettingGroup(label = "feed & glance", tokens.fgDim) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ToggleRow("show feed page", on = feedEnabled, accent = accent, tokens, onFeedEnabledChange)
                                ToggleRow(
                                    "no background",
                                    on = feedNoBackground,
                                    accent = accent,
                                    tokens,
                                    onFeedNoBackgroundChange,
                                )
                                Text(
                                    "keeps the glance screen flat even when start has a wallpaper set",
                                    color = tokens.fgDim,
                                    fontSize = 12.sp,
                                )
                                Column {
                                    Text("your name", color = tokens.fgDim, fontSize = 13.sp)
                                    Spacer(Modifier.height(6.dp))
                                    BasicTextField(
                                        value = userName,
                                        onValueChange = onUserNameChange,
                                        singleLine = true,
                                        textStyle = TextStyle(color = tokens.fg, fontSize = 14.sp),
                                        cursorBrush = SolidColor(accent),
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                                            imeAction = ImeAction.Done,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            // Lumia's text box: square, with a plain border.
                                            .border(2.dp, tokens.fgDim)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        decorationBox = { inner ->
                                            if (userName.isEmpty()) {
                                                Text("shown in the feed's greeting", color = tokens.fgDim, fontSize = 14.sp)
                                            }
                                            inner()
                                        },
                                    )
                                }
                            }
                        }
                        // ---- news region (sub-sheet) ----
                        SettingGroup(label = "news region", tokens.fgDim) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onNewsRegion)
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "choose news regions", color = tokens.fg, fontSize = 14.sp)
                                    Text(
                                        text = "$newsRegionCount countries available",
                                        color = tokens.fgDim,
                                        fontSize = 12.sp,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(text = "›", color = accent, fontSize = 16.sp)
                            }
                        }
                        }
                        else -> { // system
                        // ---- permissions (contacts/calendar/location/physical activity — sub-sheet) ----
                        SettingGroup(label = "permissions", tokens.fgDim) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onPermissions)
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "contacts, calendar, location & steps", color = tokens.fg, fontSize = 14.sp)
                                    Text(
                                        text = "manage what live tiles can access",
                                        color = tokens.fgDim,
                                        fontSize = 12.sp,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(text = "›", color = accent, fontSize = 16.sp)
                            }
                        }
                        // ---- hidden apps ----
                        SettingGroup(label = "app visibility", tokens.fgDim) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onHiddenApps)
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "hidden apps", color = tokens.fg, fontSize = 14.sp)
                                    Text(
                                        text = "show apps you've hidden from the app list",
                                        color = tokens.fgDim,
                                        fontSize = 12.sp,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(text = "›", color = accent, fontSize = 16.sp)
                            }
                        }
                        // ---- system ----
                        // The default-launcher row is hidden once TileShell already is one —
                        // there's nothing left there ("android settings" moved to the top of
                        // the sheet, see above). Re-checked live on every ON_RESUME
                        // ([rememberIsDefaultLauncher]) so backing out to Settings, changing it
                        // there, and returning updates this without reopening the sheet.
                        SettingGroup(label = "system", tokens.fgDim) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!isDefaultLauncher) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = onSetDefaultLauncher)
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "default launcher", color = tokens.fg, fontSize = 14.sp)
                                            Text(
                                                text = "make tileshell your home screen",
                                                color = tokens.fgDim,
                                                fontSize = 12.sp,
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(text = "set ›", color = accent, fontSize = 13.sp)
                                    }
                                }
                                KeyboardRow(accent, tokens)
                                ToggleRow("hide status bar", on = hideStatusBar, accent = accent, tokens, onHideStatusBarChange)
                                Text(
                                    "hides the clock/battery/signal strip at the top of the screen — swipe down " +
                                    "from the top edge to reveal it temporarily",
                                    color = tokens.fgDim,
                                    fontSize = 12.sp,
                                )
                                ToggleRow("double tap to lock", on = doubleTapLock, accent = accent, tokens, onDoubleTapLockChange)
                                Text(
                                    "a double tap on empty space on start turns the screen off. it uses the same " +
                                    "accessibility lock as the quick panel's lock tile, so it asks for that once.",
                                    color = tokens.fgDim,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        // ---- android settings: quick jump to the real device Settings app,
                        // moved to the top of the sheet per explicit user request (was buried
                        // in the "system" group near the bottom) and given the device's own
                        // real Settings icon instead of the generic gear glyph ----
                        SettingGroup(label = "android settings", tokens.fgDim) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onSystemSettings)
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val androidSettingsIcon = rememberAndroidSettingsIcon()
                                if (androidSettingsIcon != null) {
                                    Image(
                                        bitmap = androidSettingsIcon,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(22.dp),
                                    )
                                } else {
                                    Icon(TileIcons["settings"], null, tint = tokens.fg, modifier = Modifier.size(22.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "android settings", color = tokens.fg, fontSize = 14.sp)
                                    Text(
                                        text = "the device's own settings app",
                                        color = tokens.fgDim,
                                        fontSize = 12.sp,
                                    )
                                }
                                Text(text = "open ›", color = accent, fontSize = 13.sp)
                            }
                        }
                        // ---- backup & restore ----
                        SettingGroup(label = "backups, snapshots & reset", tokens.fgDim) {
                            // Each row says what it is and opens its own screen.
                            listOf(
                                Triple(BackupSection.SNAPSHOTS, "snapshots", "restore points of your layout, kept on this phone"),
                                Triple(BackupSection.FILE, "backup file", "everything in one file, for a new phone or a safety copy"),
                                Triple(BackupSection.RESET, "reset start layout", "set start up again from the first step"),
                            ).forEach { (backupSection, title, description) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenBackup(backupSection) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = title, color = tokens.fg, fontSize = 14.sp)
                                        Text(text = description, color = tokens.fgDim, fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(text = "›", color = accent, fontSize = 16.sp)
                                }
                            }
                        }
                        // ---- help ----
                        SettingGroup(label = "help", tokens.fgDim) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onPersonalizeGuide)
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "how to personalize", color = tokens.fg, fontSize = 14.sp)
                                    Text(
                                        text = "colours, wallpaper, tiles, home style, pages, pinning apps, the feed, and permissions",
                                        color = tokens.fgDim,
                                        fontSize = 12.sp,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(text = "guide ›", color = accent, fontSize = 13.sp)
                            }
                        }
                        // ---- about ----
                        SettingGroup(label = "about", tokens.fgDim) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onAbout)
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(text = "tileshell", color = tokens.fg, fontSize = 14.sp)
                                Spacer(Modifier.weight(1f))
                                Text(text = "features & info ›", color = accent, fontSize = 13.sp)
                            }
                        }
                        }
                    }
                }
            }
            HubAppBar(
                tokens = tokens,
                actions = listOf(
                    HubAppBarAction("back", "close personalize", "back", onDismiss),
                    HubAppBarAction("help", "how to personalize", "guide", onPersonalizeGuide),
                    HubAppBarAction("settings", "android settings", "android settings", onSystemSettings),
                ),
            )
        }

        if (liveRefreshOpen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = size.height * (1f - progress) }
                    .background(tokens.sheet)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "‹ personalize",
                    color = accent,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { liveRefreshOpen = false }.padding(vertical = 6.dp),
                )
                Text("live data refresh", color = tokens.fg, fontSize = 26.sp, fontWeight = FontWeight.Light)
                Text(
                    "slower saves battery and data. stock and commodity tiles also slow down outside market hours on their own.",
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                )
                RefreshRateRow("weather", weatherRefreshRate, WEATHER_RATE_OPTIONS, 30 * 60_000L, accent, tokens, onWeatherRefreshRateChange)
                RefreshRateRow("news", newsRefreshRate, WEATHER_RATE_OPTIONS, 30 * 60_000L, accent, tokens, onNewsRefreshRateChange)
                RefreshRateRow("stocks", stockRefreshRate, MARKET_RATE_OPTIONS, 60_000L, accent, tokens, onStockRefreshRateChange)
                RefreshRateRow("commodities", commodityRefreshRate, MARKET_RATE_OPTIONS, 60_000L, accent, tokens, onCommodityRefreshRateChange)
                RefreshRateRow("sports", sportsRefreshRate, SPORTS_RATE_OPTIONS, 90_000L, accent, tokens, onSportsRefreshRateChange)
                Text(
                    "home-screen widgets follow these too, but android refreshes them at most every 15 minutes " +
                        "(every few minutes while a market is open or a match is live).",
                    color = tokens.fgDim,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/** The section personalize was last on, so returning from a sub-screen (backup,
 * about, folders…) lands back there instead of on "colours". Reset when
 * personalize is closed. */
private object PersonalizeSectionMemory {
    var page = 0
}

/**
 * Chosen photos as square thumbnails (user-requested), each with a × to remove
 * it, then a "+" square that picks more and adds them. "remove all" below when
 * there are any.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PhotoGrid(
    uris: List<String>,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onRemoveAll: () -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        uris.forEach { uri ->
            androidx.compose.runtime.key(uri) {
                Box(modifier = Modifier.size(PHOTO_THUMB)) {
                    val bitmap = rememberPhotoThumbnail(uri)
                    Box(Modifier.fillMaxSize().background(tokens.chip)) {
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(26.dp)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable(onClickLabel = "remove photo") { onRemove(uri) },
                    ) {
                        Icon(TileIcons["close"], contentDescription = "remove photo", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(PHOTO_THUMB)
                .border(2.dp, tokens.fgDim)
                .clickable(onClickLabel = "add photos", onClick = onAdd),
        ) {
            Icon(TileIcons["plus"], contentDescription = "add photos", tint = accent, modifier = Modifier.size(28.dp))
        }
    }
    if (uris.isNotEmpty()) {
        Text(
            "${uris.size} photo${if (uris.size == 1) "" else "s"} · remove all",
            color = tokens.fgDim,
            fontSize = 13.sp,
            modifier = Modifier
                .clickable(onClick = onRemoveAll)
                .padding(top = 8.dp, bottom = 4.dp),
        )
    }
}

private val PHOTO_THUMB = 72.dp

/** The wallpaper in use, phone-shaped and framed as on Start; tap to reframe.
 * Keyed on [uri], whose `?v=` changes with each new Bing download. */
@Composable
private fun WallpaperPreview(
    uri: String,
    alignX: Float,
    alignY: Float,
    tokens: com.tileshell.core.design.ColorTokens,
    onReframe: () -> Unit,
) {
    val preview = rememberPhotoThumbnail(uri, minSidePx = 480)
    Box(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .width(130.dp)
            .aspectRatio(9f / 19.5f)
            .background(tokens.chip)
            .border(1.dp, tokens.fgDim)
            .clickable(onClickLabel = "reframe", onClick = onReframe),
    ) {
        if (preview != null) {
            androidx.compose.foundation.Image(
                bitmap = preview,
                contentDescription = "your wallpaper",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alignment = androidx.compose.ui.BiasAlignment(alignX * 2f - 1f, alignY * 2f - 1f),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A small thumbnail of an imported photo, decoded off the main thread. */
@Composable
private fun rememberPhotoThumbnail(uri: String, minSidePx: Int = 200): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val parsed = android.net.Uri.parse(uri)
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(parsed)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= minSidePx && bounds.outHeight / (sample * 2) >= minSidePx) sample *= 2
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(parsed)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }.value
}

/** The personalize panorama's sections, in swipe order. */
private val PERSONALIZE_SECTIONS = listOf("colours", "tiles", "wallpaper", "start", "live", "glance", "system")

/** A compact tappable navigation row: dim label on the left, accent action on the right. */
@Composable
internal fun WallpaperNavRow(
    label: String,
    action: String,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = tokens.fgDim, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(text = action, color = accent, fontSize = 13.sp)
    }
}

/** A pill toggle row (prototype `.toggle-row` + `.tg`). */
@Composable
private fun ToggleRow(
    label: String,
    on: Boolean,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!on) }
            .semantics { stateDescription = if (on) "on" else "off" }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = tokens.fg, fontSize = 17.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        com.tileshell.core.design.LumiaSwitch(on, accent, tokens, onChange)
    }
}

/** One bundled-wallpaper preview cell (prototype `.wallrow .w`). */
@Composable
internal fun WallpaperCell(
    wallpaper: WallpaperGradient,
    selected: Boolean,
    ring: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .then(if (selected) Modifier.border(2.5.dp, ring).padding(4.dp) else Modifier)
            .clip(RoundedCornerShape(4.dp))
            .wallpaperBackground(wallpaper)
            .clickable(onClick = onClick),
    )
}

/** A labelled settings group (prototype .set-group / .set-label). */
@Composable
private fun SettingGroup(label: String, labelColor: Color, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 18.dp)) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        content()
    }
}

/**
 * A small square theme-choice tile — icon + label, accent fill when selected
 * else a neutral chip fill — same on/off contract as the Quick Panel's own
 * tile grid (`QuickPanelTile`), reused here per user request instead of the
 * plain segmented row every other selector on this sheet still uses.
 */
@Composable
private fun rememberAndroidSettingsIcon(): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val resolved = context.packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)
                    ?: return@runCatching null
                val info = resolved.activityInfo
                context.packageManager
                    .getActivityIcon(ComponentName(info.packageName, info.name))
                    .toBitmap(width = 96, height = 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }.value
}

@Composable
private fun ThemeTile(
    icon: String,
    label: String,
    selected: Boolean,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // Lumia-style text choice ("dark  light  auto"), selected in the accent.
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            color = if (selected) accent else tokens.fgDim,
            fontSize = 17.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

/** Weather and news: the default (30m) sits second, between 15m and 1h. */
private val WEATHER_RATE_OPTIONS = listOf(
    LiveRefreshRate.EVERY_15_MIN to "15m",
    LiveRefreshRate.DEFAULT to "30m",
    LiveRefreshRate.EVERY_1_HOUR to "1h",
    LiveRefreshRate.EVERY_3_HOURS to "3h",
)

/** Stocks and commodities: the default is 1 minute, so it's the first pill. */
private val MARKET_RATE_OPTIONS = listOf(
    LiveRefreshRate.DEFAULT to "1m",
    LiveRefreshRate.EVERY_5_MIN to "5m",
    LiveRefreshRate.EVERY_15_MIN to "15m",
    LiveRefreshRate.EVERY_30_MIN to "30m",
    LiveRefreshRate.EVERY_1_HOUR to "1h",
)

/** Sports: the default is 90 seconds. */
private val SPORTS_RATE_OPTIONS = listOf(
    LiveRefreshRate.DEFAULT to "90s",
    LiveRefreshRate.EVERY_5_MIN to "5m",
    LiveRefreshRate.EVERY_15_MIN to "15m",
    LiveRefreshRate.EVERY_30_MIN to "30m",
    LiveRefreshRate.EVERY_1_HOUR to "1h",
)

/**
 * One category's refresh selector: label and its default on top, a segmented
 * row of [options] below. A pill is selected when it resolves to the same
 * interval as [rate] (so an old stored "1m" still lights the 1m default pill).
 */
@Composable
private fun RefreshRateRow(
    label: String,
    rate: LiveRefreshRate,
    options: List<Pair<LiveRefreshRate, String>>,
    defaultMs: Long,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onChange: (LiveRefreshRate) -> Unit,
) {
    val defaultLabel = options.first { it.first == LiveRefreshRate.DEFAULT }.second
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(label, color = tokens.fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("default $defaultLabel", color = tokens.fgDim, fontSize = 12.sp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            options.forEach { (value, text) ->
                val selected = rate.resolveMs(defaultMs) == value.resolveMs(defaultMs)
                SegCell(text, selected = selected, accent = accent, fg = tokens.fg) { onChange(value) }
            }
        }
    }
}

/**
 * Text/icon colour for content sitting directly on an [accent] fill whose
 * own brightness isn't known ahead of time — e.g. every selected segmented-
 * toggle cell below, which is filled with the sheet's own `accent` local,
 * itself the wallpaper-derived colour whenever "tile colour source" is set
 * to wallpaper (see [PersonalizeSheet]'s own `accent` resolution). A plain
 * hardcoded white read fine against the app's own accent swatches, which are
 * always mid-to-dark, but not against an arbitrary light wallpaper accent
 * (user-reported: "accent bar... text color not adjusted based on light and
 * dark wallpaper") — picks dark or light text by the fill's actual luminance
 * instead, matching how the app list/Quick Panel already do this for an
 * arbitrary accent-filled surface.
 */
private fun accentOnColor(accent: Color): Color = Glass.faceTextColor(isLightBackground(accent))

/**
 * One cell of the segmented toggle (prototype .seg div / .seg div.on).
 * [swatch], when non-null, draws a small colour dot before the label — e.g.
 * the "tile color source" row's "wallpaper" cell, previewing the actual
 * wallpaper-derived colour it would apply.
 */
@Composable
private fun RowScope.SegCell(
    label: String,
    selected: Boolean,
    accent: Color,
    fg: Color,
    swatch: Color? = null,
    onClick: () -> Unit,
) {
    // Lumia-style: plain light text, the selected one in the accent colour
    // (was an accent-filled cell in a bordered row).
    Box(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (swatch != null) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                )
            }
            Text(
                text = label,
                color = if (selected) accent else fg.copy(alpha = 0.55f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
            )
        }
    }
}

/**
 * The [Shape] each [IconShape] previews as in the icon-shape swatch row.
 * [IconShape.SQUARE] and [IconShape.ORIGINAL] both preview as a plain
 * rectangle (real icon-cell rendering skips masking entirely for ORIGINAL,
 * which reads the same for a small square preview swatch as an actual square
 * mask) — the call site distinguishes them by fill instead: SQUARE renders
 * solid (a real colour-filled mask), ORIGINAL renders outline-only (no
 * masking/fill at all). A small local duplicate of `:feature:start`'s
 * `IconCellView.kt#toComposeShape` rather than a shared one: `IconShape`
 * lives in `:core:data` and `SquircleShape` in `:core:design`, and neither
 * core module depends on the other, so each consuming feature module (this
 * one, and `:feature:start`) maps the enum to a real `Shape` locally — the
 * same split already used for `TileFill`/`FontStyle`.
 */
private fun IconShape.previewShape(): Shape = when (this) {
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUIRCLE -> SquircleShape()
    IconShape.ROUNDED -> RoundedCornerShape(percent = 30)
    IconShape.SQUARE -> RectangleShape
    IconShape.ORIGINAL -> RectangleShape
}

/** One accent swatch (prototype .swatches i / .swatches i.sel). */
@Composable
internal fun Swatch(
    color: Color,
    selected: Boolean,
    ring: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .then(if (selected) Modifier.border(2.5.dp, ring).padding(4.dp) else Modifier)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
    )
}
