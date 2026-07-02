package com.tileshell.feature.personalize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.settings.FontStyle
import com.tileshell.core.data.settings.TileColorSource
import com.tileshell.core.data.settings.TileFill
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.WallpaperGradient
import com.tileshell.core.design.Wallpapers
import com.tileshell.core.design.Wallpapers.NONE_ID
import com.tileshell.core.design.colorTokens
import com.tileshell.core.design.wallpaperBackground

/**
 * The personalize bottom sheet (FR-7): a slide-up panel over a fading scrim with
 * a grip, a lowercase "personalize" title and the full prototype `buildSettings`
 * groups — dark/light segmented toggle, 14 accent swatches, transparent-tiles +
 * blur-wallpaper toggles, a tile-transparency slider, the wallpaper row (custom
 * photo + 6 bundled gradients) and a reset-start-layout action. Stateless — it
 * renders the passed values and reports changes via the callbacks, so the host
 * persists them and feeds the new values straight back, re-skinning live.
 */
/** A subscribed news feed shown in the feeds-management list (framework-free). */
data class FeedSourceItem(val url: String, val name: String, val category: String, val enabled: Boolean)

@Composable
fun PersonalizeSheet(
    visible: Boolean,
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
    onAdjustWallpaper: () -> Unit,
    tiledWallpaper: Boolean,
    onTiledWallpaperChange: (Boolean) -> Unit,
    feedEnabled: Boolean,
    onAddLiveTile: (appId: String) -> Unit,
    onSystemSettings: () -> Unit,
    followSystemTheme: Boolean,
    onFollowSystemThemeChange: (Boolean) -> Unit,
    onThemeChange: (dark: Boolean) -> Unit,
    onAccentChange: (id: String) -> Unit,
    onGlassChange: (Boolean) -> Unit,
    onTransparencyChange: (Float) -> Unit,
    onBlurChange: (Boolean) -> Unit,
    onWallpaperChange: (id: String) -> Unit,
    onPickCustomWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onResetTileStyle: () -> Unit,
    photosSelected: Int,
    onPickPhotos: () -> Unit,
    notificationsEnabled: Boolean,
    onNotificationAccess: () -> Unit,
    batteryOptimizationExempt: Boolean,
    batteryGuidanceNote: String,
    onBatteryExemption: () -> Unit,
    cornerRadius: Float,
    onCornerRadiusChange: (Float) -> Unit,
    tileGap: Float,
    onTileGapChange: (Float) -> Unit,
    tileColorSource: TileColorSource,
    onTileColorSourceChange: (TileColorSource) -> Unit,
    tileFill: TileFill,
    onTileFillChange: (TileFill) -> Unit,
    fontStyle: FontStyle,
    onFontStyleChange: (FontStyle) -> Unit,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    onClearPhotos: () -> Unit,
    contactsGranted: Boolean,
    calendarGranted: Boolean,
    locationGranted: Boolean,
    onRequestContacts: () -> Unit,
    onRequestCalendar: () -> Unit,
    onRequestLocation: () -> Unit,
    onAbout: () -> Unit,
    onFolders: () -> Unit,
    onHiddenApps: () -> Unit,
    onBackupRestore: () -> Unit,
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
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)

    // Android back / back-gesture closes the sheet. When a sub-sheet (about,
    // folders, bing history) is open on top, its own handler — registered later —
    // takes the back press first, so this closes personalize only once they're gone.
    BackHandler(enabled = visible) { onDismiss() }

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

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.sheet)
                // Swallow taps so they don't fall through to the scrim.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            // Grip.
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tokens.fgDim.copy(alpha = 0.5f)),
            )

            Text(
                text = "personalize",
                color = tokens.fg,
                fontSize = 30.sp,
                fontWeight = FontWeight.W200,
                letterSpacing = (-1).sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 14.dp),
            )

            // ---- theme ----
            SettingGroup(label = "theme", tokens.fgDim) {
                Column {
                    ToggleRow(
                        "follow system",
                        on = followSystemTheme,
                        accent = accent,
                        tokens,
                        onFollowSystemThemeChange,
                    )
                    if (!followSystemTheme) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, tokens.tileLine),
                        ) {
                            SegCell("dark", selected = dark, accent = accent, fg = tokens.fg) {
                                onThemeChange(true)
                            }
                            SegCell("light", selected = !dark, accent = accent, fg = tokens.fg) {
                                onThemeChange(false)
                            }
                        }
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

            // ---- wallpaper ----
            SettingGroup(label = "wallpaper", tokens.fgDim) {
                ToggleRow("bing daily wallpaper", on = bingWallpaper, accent = accent, tokens, onBingWallpaperChange)
                WallpaperNavRow("recent bing wallpapers", "browse ›", accent, tokens, onBingHistory)
                if (customWallpaper) {
                    WallpaperNavRow("adjust position", "reframe ›", accent, tokens, onAdjustWallpaper)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PhotoButton(tokens = tokens, onClick = onPickCustomWallpaper, modifier = Modifier.weight(1f))
                    Wallpapers.all.take(3).forEach { wp ->
                        WallpaperCell(
                            wallpaper = wp,
                            selected = !customWallpaper && wp.id == wallpaperId,
                            ring = tokens.fg,
                            modifier = Modifier.weight(1f),
                            onClick = { onWallpaperChange(wp.id) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Wallpapers.all.drop(3).forEach { wp ->
                        WallpaperCell(
                            wallpaper = wp,
                            selected = !customWallpaper && wp.id == wallpaperId,
                            ring = tokens.fg,
                            modifier = Modifier.weight(1f),
                            onClick = { onWallpaperChange(wp.id) },
                        )
                    }
                    NoneWallpaperCell(
                        selected = !customWallpaper && wallpaperId == NONE_ID,
                        tokens = tokens,
                        modifier = Modifier.weight(1f),
                        onClick = onClearWallpaper,
                    )
                }
            }

            // ---- tile style ----
            SettingGroup(label = "tile style", tokens.fgDim) {
                // Glass / blur / tiled-wallpaper
                ToggleRow("transparent tiles", on = glass, accent = accent, tokens, onGlassChange)
                Spacer(Modifier.height(14.dp))
                Text("tile transparency", color = tokens.fgDim, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Slider(
                    value = transparency,
                    onValueChange = onTransparencyChange,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = tokens.tileLine,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                ToggleRow("blur wallpaper", on = blur, accent = accent, tokens, onBlurChange)
                Spacer(Modifier.height(14.dp))
                ToggleRow("wallpaper behind tiles", on = tiledWallpaper, accent = accent, tokens, onTiledWallpaperChange)
                Spacer(Modifier.height(20.dp))
                // Colour, fill & shape
                ToggleRow(
                    "tile colour from app icon",
                    on = tileColorSource == TileColorSource.APP_ICON,
                    accent = accent,
                    tokens,
                    onChange = { on ->
                        onTileColorSourceChange(
                            if (on) TileColorSource.APP_ICON else TileColorSource.GLOBAL_ACCENT,
                        )
                    },
                )
                Spacer(Modifier.height(14.dp))
                ToggleRow(
                    "gradient fill",
                    on = tileFill == TileFill.GRADIENT,
                    accent = accent,
                    tokens,
                    onChange = { on -> onTileFillChange(if (on) TileFill.GRADIENT else TileFill.FLAT) },
                )
                Spacer(Modifier.height(14.dp))
                Text("corner radius", color = tokens.fgDim, fontSize = 13.sp)
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
                    Slider(
                        value = cornerRadius,
                        onValueChange = onCornerRadiusChange,
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
                    Text("tile spacing", color = tokens.fgDim, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            Box(Modifier.size(10.dp, 22.dp).clip(RoundedCornerShape(3.dp)).background(accent))
                            Box(Modifier.size(10.dp, 22.dp).clip(RoundedCornerShape(3.dp)).background(accent))
                        }
                        Slider(
                            value = tileGap,
                            onValueChange = onTileGapChange,
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
                Spacer(Modifier.height(20.dp))
                // Reset
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onResetTileStyle)
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

            // ---- typography ----
            SettingGroup(label = "typography", tokens.fgDim) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        FontStyle.SYSTEM to "system",
                        FontStyle.OUTFIT to "outfit",
                        FontStyle.NUNITO to "nunito",
                    ).forEach { entry ->
                        val style = entry.first
                        val label = entry.second
                        val selected = fontStyle == style
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) accent else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (selected) accent else tokens.tileLine,
                                    RoundedCornerShape(20.dp),
                                )
                                .clickable { onFontStyleChange(style) }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = if (selected) Color.White else tokens.fg,
                                fontSize = 13.sp,
                            )
                        }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(4, 5, 6).forEach { count ->
                            val selected = columns == count
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) accent else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (selected) accent else tokens.tileLine,
                                        RoundedCornerShape(20.dp),
                                    )
                                    .clickable { onColumnsChange(count) }
                                    .padding(horizontal = 18.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = count.toString(),
                                    color = if (selected) Color.White else tokens.fg,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }

            // ---- live tiles (re-add a deleted clock/weather/calendar tile) ----
            SettingGroup(label = "live tiles", tokens.fgDim) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "re-add a deleted live tile to the start screen",
                        color = tokens.fgDim,
                        fontSize = 13.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("clock" to "clock", "weather" to "weather", "calendar" to "calendar")
                            .forEach { (appId, label) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, tokens.tileLine, RoundedCornerShape(10.dp))
                                        .clickable { onAddLiveTile(appId) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("+ $label", color = accent, fontSize = 13.sp)
                                }
                            }
                    }
                }
            }

            // ---- live photos (FR-2 photos tile) ----
            SettingGroup(label = "live photos", tokens.fgDim) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPickPhotos)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "choose photos", color = tokens.fg, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (photosSelected > 0) "$photosSelected selected ›" else "pick ›",
                        color = if (photosSelected > 0) accent else tokens.fgDim,
                        fontSize = 13.sp,
                    )
                }
                if (photosSelected > 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onClearPhotos)
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "clear selected photos", color = tokens.fgDim, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Text(text = "✕", color = tokens.fgDim, fontSize = 13.sp)
                    }
                }
            }

            // ---- folders (category folders) ----
            SettingGroup(label = "folders", tokens.fgDim) {
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

            // ---- permissions (live-tile data sources) ----
            SettingGroup(label = "permissions", tokens.fgDim) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PermissionRow(
                        label = "contacts",
                        description = "people tile · quick search",
                        granted = contactsGranted,
                        accent = accent,
                        tokens = tokens,
                        onClick = onRequestContacts,
                    )
                    PermissionRow(
                        label = "calendar",
                        description = "calendar tile",
                        granted = calendarGranted,
                        accent = accent,
                        tokens = tokens,
                        onClick = onRequestCalendar,
                    )
                    PermissionRow(
                        label = "location",
                        description = "weather tile",
                        granted = locationGranted,
                        accent = accent,
                        tokens = tokens,
                        onClick = onRequestLocation,
                    )
                }
            }

            // ---- notifications (FR-1.2 / FR-2 opt-in) ----
            SettingGroup(label = "notifications", tokens.fgDim) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNotificationAccess)
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
                    Spacer(Modifier.height(10.dp))
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

            // ---- system ----
            SettingGroup(label = "system", tokens.fgDim) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSystemSettings)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "android settings", color = tokens.fg, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text(text = "open ›", color = accent, fontSize = 13.sp)
                }
            }

            // ---- backup & restore ----
            SettingGroup(label = "backup & restore", tokens.fgDim) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onBackupRestore)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "manage backups", color = tokens.fg, fontSize = 14.sp)
                        Text(
                            text = "layout history, auto-save, export & restore",
                            color = tokens.fgDim,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(text = "›", color = accent, fontSize = 16.sp)
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
            .clickable { onChange(!on) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = tokens.fg, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (on) accent else tokens.tileLine),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/** The "pick a photo" cell that opens the system picker (prototype `.w.add`). */
@Composable
private fun PhotoButton(
    tokens: com.tileshell.core.design.ColorTokens,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, tokens.tileLine, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = TileIcons["image"],
            contentDescription = "pick a photo",
            tint = tokens.fg,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(text = "photo", color = tokens.fgDim, fontSize = 11.sp)
    }
}

/** "No wallpaper" cell — shows a slashed circle on the theme bg colour. */
@Composable
private fun NoneWallpaperCell(
    selected: Boolean,
    tokens: com.tileshell.core.design.ColorTokens,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .then(if (selected) Modifier.border(2.5.dp, tokens.fg).padding(4.dp) else Modifier)
            .clip(RoundedCornerShape(4.dp))
            .background(tokens.bg)
            .border(1.dp, tokens.tileLine, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TileIcons["close"],
            contentDescription = "remove wallpaper",
            tint = tokens.fgDim,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** One bundled-wallpaper preview cell (prototype `.wallrow .w`). */
@Composable
private fun WallpaperCell(
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
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        content()
    }
}

/** One cell of the segmented toggle (prototype .seg div / .seg div.on). */
@Composable
private fun RowScope.SegCell(
    label: String,
    selected: Boolean,
    accent: Color,
    fg: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .background(if (selected) accent else Color.Transparent)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else fg,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** A permission row: label + description on the left, status / "allow" on the right. */
@Composable
private fun PermissionRow(
    label: String,
    description: String,
    granted: Boolean,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = tokens.fg, fontSize = 14.sp)
            Text(text = description, color = tokens.fgDim, fontSize = 12.sp)
        }
        Text(
            text = if (granted) "allowed ✓" else "allow ›",
            color = if (granted) accent else tokens.fgDim,
            fontSize = 13.sp,
        )
    }
}

/** One accent swatch (prototype .swatches i / .swatches i.sel). */
@Composable
private fun Swatch(
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
