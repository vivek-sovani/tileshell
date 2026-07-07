package com.tileshell.feature.personalize

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens

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
                    text = "colours, wallpaper, tiles, pinning apps, and the feed",
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
                items = listOf(
                    "personalize · tile background: none, transparent (glass), or behind tiles",
                    "with transparent chosen, drag \"tile transparency\" to control how see-through tiles are",
                    "tile style · corner radius and tile spacing sliders shape how tiles sit together",
                    "typography switches every tile's text between system, outfit, and nunito",
                    "\"reset tile style\" at the bottom of tile style puts corners, spacing, fill, colour & font back to default",
                ),
            )

            FeatureGroup(
                title = "organizing tiles",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "long-press any tile to enter edit mode",
                    "drag one tile onto another, centre to centre, to merge them into a folder",
                    "merge two large tiles (or open a folder and use \"make stack · wide/large\") to turn them into a widget stack",
                    "use a selected tile's resize handle to cycle its size",
                    "drag a tile out of a folder or stack to unpin it back to start",
                ),
            )

            FeatureGroup(
                title = "pinning apps",
                accent = accent,
                tokens = tokens,
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
                items = listOf(
                    "swipe right from start (or swipe left from the app list) to open the feed",
                    "glance tab: live clock, weather, calendar events, and now-playing, plus any widgets you've added",
                    "tap the search pill on glance to jump straight into quick search",
                    "news tab: articles from 10+ sources",
                    "tap the ⚙ next to the glance/news tabs to add your own rss/atom feeds and pick categories",
                ),
            )

            Spacer(Modifier.height(4.dp))
        }
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
