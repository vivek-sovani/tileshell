package com.tileshell.feature.personalize

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens

@Composable
fun AboutSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "aboutSheetProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            .getOrDefault("0.9")
    }

    Box(modifier = modifier.fillMaxSize()) {
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

            // Header — app name + version
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            ) {
                TileLogoMark(accent = accent)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "tileshell",
                    color = tokens.fg,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.W200,
                    letterSpacing = (-1.2).sp,
                )
                Text(
                    text = "windows mobile-style launcher",
                    color = tokens.fgDim,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W300,
                    letterSpacing = 0.sp,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "v$version",
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W500,
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(20.dp))

            // ── USER FEATURES ──────────────────────────────────────────────
            SectionHeader("what you can do", tokens.fgDim)

            FeatureGroup(
                title = "start screen",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "live tile grid in three sizes — small, medium, wide",
                    "choose grid density — 4, 5, or 6 tiles across a row",
                    "long-press any tile to enter edit mode",
                    "drag to reorder, resize, or unpin tiles",
                    "drag a tile into the empty space below to send it to the bottom",
                    "a moving tile reorders cleanly; pause it over another to merge",
                    "merge two same-size tiles by lining them up centre to centre",
                    "unread badge counts on app tiles",
                ),
            )

            FeatureGroup(
                title = "folders",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "merge two tiles together to create a folder",
                    "auto-create category folders from your installed apps",
                    "resize, reorder, and remove tiles inside an open folder",
                    "pull an app out of a folder to unpin it back to start",
                    "folder shows a combined badge; each app shows its own inside",
                    "music keeps playing controls and album art live inside a folder",
                ),
            )

            FeatureGroup(
                title = "feed & news",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "swipe right from start to open the feed",
                    "glance tab: live clock, weather, calendar events, now-playing",
                    "tap the calendar card to open your calendar app",
                    "tap the now-playing card to open the music app",
                    "news tab: articles from 10+ india news sources",
                    "add and manage your own custom RSS / Atom feeds",
                    "8 categories: nation, tech, sports, cricket, business and more",
                ),
            )

            FeatureGroup(
                title = "widgets",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "add any android app widget to the feed page",
                    "resize widgets by dragging the edge",
                    "long-press a widget to edit or remove it",
                ),
            )

            FeatureGroup(
                title = "live tiles",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "clock — live time, weekday, and date; flips to alarm",
                    "weather — real forecasts via open-meteo, no account needed",
                    "weather as a small tile shows the current temperature",
                    "calendar — today's date always visible; flips to upcoming events",
                    "music — now-playing, album art, and transport controls",
                    "people — rotating mosaic of your contacts' photos",
                    "photos — cross-fading slideshow of photos you pick",
                    "mail & messages — latest sender and message snippet",
                    "any app tile shows its latest notification as a preview",
                ),
            )

            FeatureGroup(
                title = "personalization",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "14 accent colours to choose from",
                    "per-tile colour — give any tile its own colour in edit mode",
                    "tile colour from app icon — auto-picks the dominant colour",
                    "dark, light, or follow-system theme",
                    "glass (transparent) tiles with adjustable transparency",
                    "adjustable tile spacing — drag a slider to pack or spread tiles",
                    "6 built-in gradient wallpapers + custom photo",
                    "daily bing wallpaper, with a viewer for recent days",
                    "wallpaper visible through tiles (show-through mode)",
                    "tile corner radius slider and gradient fill option",
                    "font style: outfit (default), nunito, or system",
                    "grid columns — pack 4, 5, or 6 tiles into a row",
                ),
            )

            FeatureGroup(
                title = "screen lock",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "long-press the settings gear to lock the screen",
                    "preserves biometric unlock on android 9 and above",
                ),
            )

            FeatureGroup(
                title = "accessibility",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "full talkback support with labels and custom actions",
                    "48dp minimum touch targets throughout",
                    "respects system font scale, display cutouts, and rtl layouts",
                    "animations pause when system animations are disabled",
                    "live tiles pause in battery saver mode",
                ),
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(16.dp))

            Text(
                text = "© 2026 vivek sovani",
                color = tokens.fgDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

/** Compact 2×2 tile grid mark representing the launcher. */
@Composable
private fun TileLogoMark(accent: Color) {
    val gap = 3.dp
    val cell = 18.dp
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Box(Modifier.size(cell).background(accent))
            Box(Modifier.size(cell).background(accent.copy(alpha = 0.5f)))
        }
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Box(Modifier.size(cell).background(accent.copy(alpha = 0.7f)))
            Box(Modifier.size(cell).background(accent.copy(alpha = 0.35f)))
        }
    }
}

@Composable
private fun SectionHeader(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
    )
}

@Composable
private fun FeatureGroup(
    title: String,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    items: List<String>,
) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp)) {
        Text(
            text = title,
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.W500,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        items.forEach { item ->
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "·",
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(end = 8.dp, top = 1.dp),
                )
                Text(
                    text = item,
                    color = tokens.fg,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun DevInfoGroup(
    tokens: com.tileshell.core.design.ColorTokens,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tokens.tileLine.copy(alpha = 0.3f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        content()
    }
}

@Composable
private fun DevRow(label: String, value: String, tokens: com.tileshell.core.design.ColorTokens) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = tokens.fgDim,
            fontSize = 12.sp,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            color = tokens.fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.W400,
        )
    }
}
