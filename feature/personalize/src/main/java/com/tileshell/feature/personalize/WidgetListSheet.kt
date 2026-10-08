package com.tileshell.feature.personalize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.tileshell.core.design.colorTokens

/** One row in the add-widgets catalog. [colorId] is a [TileAccents] id, not the 14 global accents. */
private data class WidgetCatalogEntry(
    val appId: String,
    val label: String,
    val description: String,
    val iconKey: String,
    val colorId: String,
    /** True for a hub: a tile whose tap opens its own hub screen. */
    val hub: Boolean = false,
)

/**
 * Everything pinnable from this sheet, in two groups. **Hubs** are a tile plus the hub it opens (every hub is named
 * "<x> hub"). **Widgets** are single-purpose tiles with no hub behind them. Pinning an app from the app list gives
 * a plain real-icon tile instead. Tiles that a hub already pins on its own (a team, a stock or commodity, a note or
 * task list, an alarm) are not listed here again: pin them from their hub, and any already on Start keep working.
 * Backed by [com.tileshell.core.data.seed.DefaultLayout.ALL_TILE_TEMPLATES].
 */
private val WIDGET_CATALOG = listOf(
    WidgetCatalogEntry("weather", "weather hub", "live forecast for your place, with hourly and daily detail", "weather", "cyan", hub = true),
    WidgetCatalogEntry("calendar", "calendar hub", "today's date, flipping to your next event, with the week and month", "calendar", "magenta", hub = true),
    WidgetCatalogEntry("clock", "clock hub", "the time on the front and your next alarm on the back; opens alarms, world clocks, timers, a stopwatch and timer sets", "clock", "cobalt", hub = true),
    WidgetCatalogEntry("people", "people hub", "contact photos, with what's new, favourites and your messaging and mail apps", "people", "teal", hub = true),
    WidgetCatalogEntry("music", "music hub", "now playing, with your library, podcasts, radio and history", "music", "orange", hub = true),
    WidgetCatalogEntry("productivity", "productivity hub", "next meeting and open tasks, with notes, task lists and your office apps", "productivity", "cobalt", hub = true),
    WidgetCatalogEntry("battery", "battery hub", "charge level and time remaining, with the week and what used it", "battery", "green", hub = true),
    WidgetCatalogEntry("money", "money hub", "bank and payment transactions and bills, with your payment and banking apps", "money", "green", hub = true),
    WidgetCatalogEntry("markets", "markets hub", "your watchlist, indices and movers; pin a tile each for stocks, commodities, currencies and crypto", "markets", "cobalt", hub = true),
    WidgetCatalogEntry("sportshub", "sports hub", "live scores, fixtures and results for the sports and teams you follow", "sportshub", "orange", hub = true),
    WidgetCatalogEntry("newshub", "news hub", "the newest headlines, with topics, saved stories and live news channels", "newshub", "red", hub = true),
    WidgetCatalogEntry("shophub", "shopping hub", "orders on their way, with your shopping, food and courier apps", "shophub", "magenta", hub = true),
    WidgetCatalogEntry("healthhub", "health hub", "your steps toward a daily goal, with a week view and your health and fitness apps", "healthhub", "lime", hub = true),
    WidgetCatalogEntry("panchang", "panchang hub", "today's tithi and the Hindu calendar, with festivals and moon times", "calsys", "cobalt", hub = true),

    WidgetCatalogEntry("mail", "mail", "your newest email, from your mail app", "mail", "purple"),
    WidgetCatalogEntry("messages", "messages", "your newest message, from your messaging app", "messages", "amber"),
    WidgetCatalogEntry("photos", "photos", "a slideshow of photos you pick", "photos", "cyan"),
    WidgetCatalogEntry("stickynote", "sticky note", "one note, pinned to its own tile", "stickynote", "amber"),
    WidgetCatalogEntry("countdown", "countdown", "days until a date you set — pin as many as you like", "countdown", "magenta"),
    WidgetCatalogEntry("calsys", "calendar systems", "today's date in a calendar system of your choice (Hindu, Islamic, Hebrew, Persian and more), and the roman date; tap the tile to change it", "calsys", "cobalt"),
    WidgetCatalogEntry("moonphase", "moon phase", "tonight's phase and illumination; opens the panchang hub", "moonphase", "slate"),
    WidgetCatalogEntry("flashlight", "flashlight", "tap the tile to turn it on or off", "flashlight", "steel"),
)

/**
 * The add-widgets sub-sheet, opened from the Start edit-mode bar's "add
 * widgets" button (alongside "add," which still opens the app list). Tapping
 * a row pins that widget to the end of the grid via [onAddWidget] and closes
 * the sheet. Follows the same slide-up shape as [HiddenAppsSheet].
 */
@Composable
fun WidgetListSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onAddWidget: (appId: String) -> Unit,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
    // Notes has one shared, repository-backed note list behind every pinned
    // tile — a second pin would just show the same content twice, so that
    // row greys out once one exists. Sticky note has no such sharing (each
    // tile's text lives on its own row) so it's always pinnable.
    notesAlreadyPinned: Boolean = false,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "widgetListSheetProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)

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
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.72f)
                    .graphicsLayer { translationY = size.height * (1f - progress) }
                    .background(tokens.sheet, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                // drag handle
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
                    text = "add live tiles",
                    color = tokens.fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W300,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                )
                Text(
                    text = "tap to pin it to the end of your start screen",
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                )
                HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                ) {
                    item(key = "hubs-header") { CatalogHeader("hubs", "a tile with its own hub: tap the tile to open it", tokens) }
                    items(WIDGET_CATALOG.filter { it.hub }, key = { it.appId }) { entry ->
                        WidgetCatalogRow(entry = entry, tokens = tokens, enabled = true, onClick = { onAddWidget(entry.appId); onDismiss() })
                    }
                    item(key = "widgets-header") { CatalogHeader("widgets", "single-purpose tiles", tokens) }
                    items(WIDGET_CATALOG.filter { !it.hub }, key = { it.appId }) { entry ->
                        WidgetCatalogRow(entry = entry, tokens = tokens, enabled = true, onClick = { onAddWidget(entry.appId); onDismiss() })
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogHeader(title: String, subtitle: String, tokens: com.tileshell.core.design.ColorTokens) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)) {
        Text(title, color = tokens.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(subtitle, color = tokens.fgDim, fontSize = 12.sp)
    }
}

@Composable
private fun WidgetCatalogRow(
    entry: WidgetCatalogEntry,
    tokens: com.tileshell.core.design.ColorTokens,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(TileAccents.forId(entry.colorId).copy(alpha = alpha)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(TileIcons[entry.iconKey], null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f).graphicsLayer { this.alpha = alpha }) {
            Text(entry.label, color = tokens.fg, fontSize = 16.sp)
            Text(
                text = if (enabled) entry.description else "already pinned to start",
                color = tokens.fgDim,
                fontSize = 12.5.sp,
            )
        }
    }
}
