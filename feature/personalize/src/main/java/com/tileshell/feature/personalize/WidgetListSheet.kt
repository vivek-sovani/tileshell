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
)

/**
 * Every widget pinnable from this sheet. The first three (weather/calendar/
 * clock) moved here from `CategoryFolderSheet`'s old "+ weather/+ calendar/
 * + clock" row now that there's a dedicated catalog for this — one place to
 * add a widget, not two. The rest are new, opt-in-only widgets backed by
 * [com.tileshell.core.data.seed.DefaultLayout.OPT_IN_WIDGET_TILES].
 */
private val WIDGET_CATALOG = listOf(
    WidgetCatalogEntry("weather", "weather", "live forecast for your location", "weather", "cyan"),
    WidgetCatalogEntry("calendar", "calendar", "today's date, flips to your next event", "calendar", "magenta"),
    WidgetCatalogEntry("clock", "clock", "time, weekday and date", "clock", "cobalt"),
    WidgetCatalogEntry("battery", "battery", "charge level and time remaining", "battery", "green"),
    WidgetCatalogEntry("alarm", "alarm", "next alarm time and active days", "alarm", "purple"),
    WidgetCatalogEntry("moonphase", "moon phase", "tonight's phase and illumination", "moonphase", "slate"),
    WidgetCatalogEntry("tasks", "tasks", "a checklist you keep, right on start", "tasks", "blue"),
    WidgetCatalogEntry("notepad", "notes", "your last note, always one glance away", "notepad", "amber"),
    WidgetCatalogEntry("stickynote", "sticky note", "one note, pinned to its own tile", "stickynote", "amber"),
    WidgetCatalogEntry("flashlight", "flashlight", "tap the tile to turn it on or off", "flashlight", "steel"),
    WidgetCatalogEntry("countdown", "countdown", "days until a date you set — pin as many as you like", "countdown", "magenta"),
    WidgetCatalogEntry("steps", "steps", "today's step count, from your phone's own sensor", "steps", "lime"),
    WidgetCatalogEntry("sports", "sports", "follow a team's score — pick one after pinning", "sports", "red"),
    WidgetCatalogEntry("stock", "stock market", "follow a stock or a whole sector — pick one after pinning", "stock", "teal"),
    WidgetCatalogEntry("commodity", "commodities", "gold, silver, oil, or a currency pair — pick one after pinning", "commodity", "mauve"),
    WidgetCatalogEntry("calsys", "calendar systems", "today's date in a calendar system of your choice, and the roman date", "calsys", "cobalt"),
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
                    text = "add widgets",
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
                    items(WIDGET_CATALOG, key = { it.appId }) { entry ->
                        val disabled = entry.appId == "notepad" && notesAlreadyPinned
                        WidgetCatalogRow(
                            entry = entry,
                            tokens = tokens,
                            enabled = !disabled,
                            onClick = {
                                onAddWidget(entry.appId)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
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
