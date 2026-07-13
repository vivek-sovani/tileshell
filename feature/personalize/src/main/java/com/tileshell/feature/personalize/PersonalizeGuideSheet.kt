package com.tileshell.feature.personalize

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.Wallpapers
import com.tileshell.core.design.colorTokens
import com.tileshell.core.design.tileGradientBrush

/**
 * A static how-to guide for personalization, opened from a permanent row in
 * [PersonalizeSheet] and auto-shown once the first time that sheet is opened
 * (see [PersonalizeGuidePrefs]) — user feedback was that the settings groups
 * are discoverable but the *how* behind less-obvious interactions (per-tile
 * colour, merging into folders/stacks, wallpaper reframing) wasn't. Reuses
 * [AboutSheet]'s sheet chrome and [FeatureGroup]/[SectionHeader] list style,
 * just with instructional wording instead of a feature inventory.
 */
@Composable
fun PersonalizeGuideSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "personalizeGuideSheetProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)

    BackHandler(enabled = visible) { onDismiss() }

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
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
                .fillMaxHeight(0.92f)
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.sheet)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        ) {
            // Grip
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tokens.fgDim.copy(alpha = 0.5f)),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
            ) {
                Text(
                    text = "how to personalize",
                    color = tokens.fg,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.W200,
                    letterSpacing = (-0.8).sp,
                )
                Text(
                    text = "colours, wallpaper, tiles, pinning apps, the feed, and permissions",
                    color = tokens.fgDim,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W300,
                )
            }

            HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(20.dp))

            FeatureGroup(
                title = "colours",
                accent = accent,
                tokens = tokens,
                visual = { ColoursVisual(accent, tokens) },
                items = listOf(
                    "personalize · accent colour sets the colour every tile uses by default",
                    "in edit mode, tap the colour dot on a selected tile to give just that tile its own colour",
                    "turn on \"tile colour from app icon\" (personalize · colour & fill) to auto-pick each app's dominant colour instead",
                    "turn on \"gradient fill\" for a subtle diagonal gradient instead of a flat colour",
                ),
            )

            FeatureGroup(
                title = "wallpaper",
                accent = accent,
                tokens = tokens,
                visual = { WallpaperVisual(tokens) },
                items = listOf(
                    "personalize · wallpaper: choose none, a photo, a slideshow, daily bing, or a stock gradient",
                    "with a photo or bing wallpaper set, use \"adjust position · reframe\" to pinch-zoom and drag it into place",
                    "slideshow rotates through photos you pick, every 15 minutes to 3 hours",
                    "turn on \"behind tiles\" (tile background) to let the wallpaper show through the grid",
                ),
            )

            FeatureGroup(
                title = "tile look",
                accent = accent,
                tokens = tokens,
                visual = { TileLookVisual(accent, tokens) },
                items = listOf(
                    "personalize · tile background: none, transparent (glass), or behind tiles",
                    "with transparent chosen, drag \"tile transparency\" to control how see-through tiles are",
                    "tile style · corner radius slider rounds every tile's corners",
                    "tile style · tile spacing slider sets the gap between tiles — drag it up to spread them out, down to pack them tighter",
                    "typography switches every tile's text between system, outfit, and nunito",
                    "\"reset tile style\" at the bottom of tile style puts corners, spacing, fill, colour & font back to default",
                ),
            )

            FeatureGroup(
                title = "organizing tiles",
                accent = accent,
                tokens = tokens,
                visual = { OrganizingVisual(accent, tokens) },
                items = listOf(
                    "long-press any tile to enter edit mode",
                    "drag one tile onto another, centre to centre, to merge them into a folder",
                    "merge two large tiles (or open a folder and use \"make stack · wide/large\") to turn them into a widget stack — \"keep as folder\" sits right alongside, so you won't convert by accident",
                    "use a selected tile's resize handle to cycle its size",
                    "tap the folder icon on a selected folder or stack to expand it in place and manage its members",
                    "an open stack offers switching to the other size (wide ↔ large) or \"make normal folder\" to revert it",
                    "tap × on a selected tile to unpin it — inside an open folder or stack, that sends the member back to start",
                ),
            )

            FeatureGroup(
                title = "pinning apps",
                accent = accent,
                tokens = tokens,
                visual = { PinningVisual(accent, tokens) },
                items = listOf(
                    "tap the chevron at the bottom of start (or swipe left) to open the app list",
                    "long-press any app for \"pin to start\", \"hide\", or \"uninstall\"",
                    "before the alphabetical list: a \"recent\" section shows your most-used and newly-installed apps, plus any with a pending notification even if it isn't pinned",
                    "tap a letter on the right for the jump grid, to skip straight to that part of the alphabet",
                    "hid an app by mistake? personalize · app visibility · hidden apps brings it back with \"show\"",
                ),
            )

            FeatureGroup(
                title = "feed: glance & news",
                accent = accent,
                tokens = tokens,
                visual = { FeedVisual(tokens) },
                items = listOf(
                    "swipe right from start (or swipe left from the app list) to open the feed",
                    "glance tab: live clock, weather, calendar events, and now-playing, plus any widgets you've added",
                    "tap the search pill on glance to jump straight into quick search",
                    "news tab: pick any number of regions — india plus ~20 other countries, defaulting to your device's own",
                    "tap the ⚙ next to the glance/news tabs to add your own rss/atom feeds and pick categories",
                ),
            )

            FeatureGroup(
                title = "permissions",
                accent = accent,
                tokens = tokens,
                visual = { PermissionsVisual(accent, tokens) },
                items = listOf(
                    "personalize · notifications · \"badges & live mail\" turns on unread badge counts on tiles, plus live mail/messages and notification-preview tile faces",
                    "long-press the settings gear on start to lock the screen",
                    "the first time, this opens android's accessibility settings so you can turn on tileshell's lock service once — it's a one-time manual step, the launcher can't enable it for you",
                    "turning it on preserves biometric unlock (android 9 and up); without it, locking falls back to a plain device-admin lock with no biometrics",
                    "personalize · permissions also lists contacts (people tile, quick search), calendar (calendar tile), and location (weather tile) — tap any of them to grant",
                ),
            )

            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * Shared card chrome for a section's illustration row — reused across every
 * `*Visual` below so they all sit in the same subtle rounded strip (mirrors
 * [DevInfoGroup]'s card look) rather than floating loose above the bullets.
 */
@Composable
private fun GuideVisualCard(
    tokens: com.tileshell.core.design.ColorTokens,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(tokens.tileLine.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** A few real accent [Swatch]es plus a mock tile with the per-tile colour-dot badge. */
@Composable
private fun ColoursVisual(accent: Color, tokens: com.tileshell.core.design.ColorTokens) {
    GuideVisualCard(tokens) {
        TileAccents.swatches.take(5).forEach { (_, color) ->
            Swatch(
                color = color,
                selected = false,
                ring = tokens.fg,
                modifier = Modifier.size(22.dp),
                onClick = {},
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(accent),
        ) {
            Icon(
                imageVector = TileIcons["palette"],
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.BottomEnd)
                    .padding(2.dp),
            )
        }
    }
}

/** The real bundled gradients, rendered exactly as [WallpaperCell] draws them on the wallpaper row. */
@Composable
private fun WallpaperVisual(tokens: com.tileshell.core.design.ColorTokens) {
    GuideVisualCard(tokens) {
        Wallpapers.all.take(3).forEach { wp ->
            WallpaperCell(
                wallpaper = wp,
                selected = false,
                ring = tokens.fg,
                modifier = Modifier.size(36.dp),
                onClick = {},
            )
        }
    }
}

/**
 * Flat vs. gradient fill (real [tileGradientBrush]) plus a blur glyph for the
 * background-style bullets, and a tight-vs-loose box pair for the tile-spacing
 * bullet — the same flanking-box idiom the real "tile spacing" slider draws in
 * [PersonalizeSheet], not a new metaphor.
 */
@Composable
private fun TileLookVisual(accent: Color, tokens: com.tileshell.core.design.ColorTokens) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GuideVisualCard(tokens) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(accent),
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(tileGradientBrush(accent)),
            )
            Icon(
                imageVector = TileIcons["blur"],
                contentDescription = null,
                tint = tokens.fgDim,
                modifier = Modifier.size(20.dp),
            )
        }
        GuideVisualCard(tokens) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                Box(Modifier.size(10.dp, 22.dp).clip(RoundedCornerShape(3.dp)).background(accent))
                Box(Modifier.size(10.dp, 22.dp).clip(RoundedCornerShape(3.dp)).background(accent))
            }
            Text(text = "→", color = tokens.fgDim, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp, 22.dp).clip(RoundedCornerShape(3.dp)).background(accent))
                Box(Modifier.size(10.dp, 22.dp).clip(RoundedCornerShape(3.dp)).background(accent))
            }
        }
    }
}

/** Two tiles merging into a folder, plus the × used to unpin a tile back out. */
@Composable
private fun OrganizingVisual(accent: Color, tokens: com.tileshell.core.design.ColorTokens) {
    GuideVisualCard(tokens) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accent),
        )
        Icon(
            imageVector = TileIcons["plus"],
            contentDescription = null,
            tint = tokens.fgDim,
            modifier = Modifier.size(16.dp),
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accent.copy(alpha = 0.55f)),
        )
        Text(text = "→", color = tokens.fgDim, fontSize = 14.sp)
        Icon(
            imageVector = TileIcons["folder"],
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = TileIcons["close"],
            contentDescription = null,
            tint = tokens.fgDim,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** A mock app row with the pin/recent affordances the app list actually shows. */
@Composable
private fun PinningVisual(accent: Color, tokens: com.tileshell.core.design.ColorTokens) {
    GuideVisualCard(tokens) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TileIcons["app"],
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tokens.fgDim.copy(alpha = 0.4f)),
        )
        Icon(
            imageVector = TileIcons["pin"],
            contentDescription = null,
            tint = tokens.fgDim,
            modifier = Modifier.size(18.dp),
        )
        Icon(
            imageVector = TileIcons["recents"],
            contentDescription = null,
            tint = tokens.fgDim,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** The glance widgets plus the news/search glyphs the feed page actually shows. */
@Composable
private fun FeedVisual(tokens: com.tileshell.core.design.ColorTokens) {
    GuideVisualCard(tokens) {
        listOf("clock", "weather", "calendar", "music").forEach { key ->
            Icon(
                imageVector = TileIcons[key],
                contentDescription = null,
                tint = tokens.fg,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = TileIcons["search"],
            contentDescription = null,
            tint = tokens.fgDim,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Notifications/badges + the settings-gear lock, alongside the other opt-in permissions. */
@Composable
private fun PermissionsVisual(accent: Color, tokens: com.tileshell.core.design.ColorTokens) {
    GuideVisualCard(tokens) {
        Box {
            Icon(
                imageVector = TileIcons["mail"],
                contentDescription = null,
                tint = tokens.fg,
                modifier = Modifier.size(20.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
        Icon(
            imageVector = TileIcons["settings"],
            contentDescription = null,
            tint = tokens.fg,
            modifier = Modifier.size(20.dp),
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(18.dp)
                .background(tokens.tileLine),
        )
        Icon(
            imageVector = TileIcons["contacts"],
            contentDescription = null,
            tint = tokens.fgDim,
            modifier = Modifier.size(18.dp),
        )
        Icon(
            imageVector = TileIcons["calendar"],
            contentDescription = null,
            tint = tokens.fgDim,
            modifier = Modifier.size(18.dp),
        )
        Icon(
            imageVector = TileIcons["weather"],
            contentDescription = null,
            tint = tokens.fgDim,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** One-shot "personalize guide seen" flag, kept in the shared launcher prefs. */
object PersonalizeGuidePrefs {
    private const val PREFS = "tileshell.prefs"
    private const val KEY = "personalize_guide_shown"

    fun shown(context: Context): Boolean =
        prefs(context).getBoolean(KEY, false)

    fun markShown(context: Context) {
        prefs(context).edit().putBoolean(KEY, true).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
