package com.tileshell.feature.personalize

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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.WallpaperGradient
import com.tileshell.core.design.Wallpapers
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
    tiledWallpaper: Boolean,
    onTiledWallpaperChange: (Boolean) -> Unit,
    feedEnabled: Boolean,
    onFeedEnabledChange: (Boolean) -> Unit,
    feeds: List<FeedSourceItem>,
    onToggleFeed: (url: String, enabled: Boolean) -> Unit,
    onToggleCategory: (category: String, enabled: Boolean) -> Unit,
    onRemoveFeed: (url: String) -> Unit,
    onAddFeed: (url: String, name: String) -> Unit,
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
    onResetLayout: () -> Unit,
    photosSelected: Int,
    onPickPhotos: () -> Unit,
    notificationsEnabled: Boolean,
    onNotificationAccess: () -> Unit,
    onDismiss: () -> Unit,
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

    Box(modifier = modifier.fillMaxSize()) {
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
                    // The manual dark/light control only applies when not following
                    // the device setting; hidden while "follow system" is on.
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
                            // Pad a short final row so swatches keep their column width.
                            repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            // ---- transparent tiles / blur wallpaper ----
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 18.dp)) {
                ToggleRow("transparent tiles", on = glass, accent = accent, tokens, onGlassChange)
                Spacer(Modifier.height(14.dp))
                ToggleRow("blur wallpaper", on = blur, accent = accent, tokens, onBlurChange)
                Spacer(Modifier.height(14.dp))
                // Show-through mode: dark screen, wallpaper visible only behind tiles.
                ToggleRow("wallpaper behind tiles", on = tiledWallpaper, accent = accent, tokens, onTiledWallpaperChange)
                Spacer(Modifier.height(14.dp))
                // The left "feed" page (swipe right from Start).
                ToggleRow("left feed page", on = feedEnabled, accent = accent, tokens, onFeedEnabledChange)
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

            // ---- tile transparency ----
            SettingGroup(label = "tile transparency", tokens.fgDim) {
                Slider(
                    value = transparency,
                    onValueChange = onTransparencyChange,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = tokens.tileLine,
                    ),
                )
            }

            // ---- wallpaper ----
            SettingGroup(label = "wallpaper", tokens.fgDim) {
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
                    // Keep the bottom row's cells the same width as the top row's.
                    repeat(1) { Spacer(Modifier.weight(1f)) }
                }
            }

            // ---- layout ----
            SettingGroup(label = "layout", tokens.fgDim) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onResetLayout)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "reset start layout", color = tokens.fg, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text(text = "↺", color = tokens.fgDim, fontSize = 16.sp)
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
            }

            // ---- news feeds (left feed discover section) — kept last; only
            // relevant when the feed page itself is on ----
            if (feedEnabled) {
                SettingGroup(label = "news feeds", tokens.fgDim) {
                    FeedsManager(
                        feeds = feeds,
                        accent = accent,
                        tokens = tokens,
                        onToggleFeed = onToggleFeed,
                        onToggleCategory = onToggleCategory,
                        onRemove = onRemoveFeed,
                        onAdd = onAddFeed,
                    )
                }
            }
        }
    }
}

// Display order + labels for the known categories; feeds in other categories are
// treated as user-added "custom" feeds.
private val FEED_CATEGORY_LABELS = linkedMapOf(
    "nation" to "national news",
    "state" to "local news",
    "entertainment" to "entertainment",
    "cricket" to "cricket",
    "sports" to "sports",
    "tech" to "technology",
    "business" to "business",
    "food" to "food",
)

/**
 * News management: feeds grouped under each category. The category header toggles
 * all its feeds at once; each feed below has its own toggle so individual sources
 * can be picked. Custom (user-added) feeds get a remove action, plus a field to add
 * a custom RSS/Atom URL. Wired to the host's FeedStore.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FeedsManager(
    feeds: List<FeedSourceItem>,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onToggleFeed: (String, Boolean) -> Unit,
    onToggleCategory: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FEED_CATEGORY_LABELS.forEach { (category, label) ->
            val inCategory = feeds.filter { it.category == category }
            if (inCategory.isEmpty()) return@forEach
            val anyOn = inCategory.any { it.enabled }
            ToggleRow(label = label, on = anyOn, accent = accent, tokens = tokens) {
                onToggleCategory(category, it)
            }
            // Individual feeds expand for selection only while the category is on,
            // shown as tappable chips (filled = selected) to keep it compact.
            if (anyOn) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    inCategory.forEach { feed ->
                        FeedChip(feed.name, feed.enabled, accent, tokens) {
                            onToggleFeed(feed.url, !feed.enabled)
                        }
                    }
                }
            }
        }

        val customFeeds = feeds.filter { it.category !in FEED_CATEGORY_LABELS }
        if (customFeeds.isNotEmpty()) {
            Text("custom feeds", color = tokens.fgDim, fontSize = 12.sp)
            customFeeds.forEach { feed ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        feed.name,
                        color = if (feed.enabled) tokens.fg else tokens.fgDim,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "remove",
                        color = tokens.fgDim,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onRemove(feed.url) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    TogglePill(on = feed.enabled, accent = accent, tokens = tokens) {
                        onToggleFeed(feed.url, !feed.enabled)
                    }
                }
            }
        }

        var url by remember { mutableStateOf("") }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, tokens.tileLine, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    textStyle = TextStyle(color = tokens.fg, fontSize = 14.sp),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (url.isNotBlank()) { onAdd(url, ""); url = "" }
                    }),
                    decorationBox = { inner ->
                        if (url.isEmpty()) Text("add feed url", color = tokens.fgDim, fontSize = 14.sp)
                        inner()
                    },
                )
            }
            Text(
                "add",
                color = accent,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { if (url.isNotBlank()) { onAdd(url, ""); url = "" } }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

/** A compact selectable chip (filled = on) used for per-feed selection. */
@Composable
private fun FeedChip(
    label: String,
    on: Boolean,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (on) Modifier.background(accent)
                else Modifier.border(1.dp, tokens.tileLine, RoundedCornerShape(16.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (on) Color.White else tokens.fgDim,
            fontSize = 13.sp,
        )
    }
}

/** The standalone pill toggle (the switch part of [ToggleRow]). */
@Composable
private fun TogglePill(
    on: Boolean,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (on) accent else tokens.tileLine)
            .clickable(onClick = onClick),
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
