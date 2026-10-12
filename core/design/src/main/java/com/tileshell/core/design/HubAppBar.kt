package com.tileshell.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One button in a [HubAppBar]: a round icon whose [label] shows when the bar
 * is expanded with "···"; [description] is what TalkBack reads.
 */
class HubAppBarAction(
    val iconKey: String,
    val description: String,
    val label: String,
    val onClick: () -> Unit,
) {
    /** Drawn in place of the [iconKey] glyph, e.g. another app's own launcher icon (set with [withIcon]). */
    var icon: ImageBitmap? = null
        private set

    /** This button with [icon] drawn in place of its glyph; unchanged while [icon] is null. */
    fun withIcon(icon: ImageBitmap?): HubAppBarAction = apply { this.icon = icon }

    /** The label is the description itself. */
    constructor(iconKey: String, description: String, onClick: () -> Unit) :
        this(iconKey, description, description, onClick)
}

/**
 * The hubs' Windows Phone app bar (user-requested, following the Lumia
 * hubs): a full-width strip along the bottom with round outlined icon
 * buttons in the middle and "···" at the right. "···" raises the bar to
 * show each button's label and any [menuItems] as plain text rows, as
 * Lumia's did; tapping anything lowers it again.
 */
@Composable
fun HubAppBar(
    tokens: ColorTokens,
    actions: List<HubAppBarAction>,
    modifier: Modifier = Modifier,
    menuItems: List<HubAppBarAction> = emptyList(),
    // For the section on the second screen of a split window ([rememberRightScreenPage], [rightScreenActions]).
    rightActions: List<HubAppBarAction> = emptyList(),
) {
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val split = LocalHinge.current
        ?.splitOf(vertical = true, extent = LocalWindowInfo.current.containerSize.width.toFloat())
    val firstScreen = split?.let { with(density) { it.first.toDp() } }
    val hingeGap = split?.let { with(density) { it.gap.toDp() } } ?: 0.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(tokens.sheet),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val usable = firstScreen ?: maxWidth
            val onSecond = rightActions.isNotEmpty() && firstScreen != null
            ActionRow(actions, usable, tokens, expanded, Modifier.align(Alignment.TopStart)) { expanded = false }
            if (onSecond) {
                // The second screen's own buttons, clear of the hinge and of the "···" corner.
                val start = firstScreen!! + hingeGap
                ActionRow(
                    rightActions, maxWidth - start, tokens, expanded,
                    Modifier.align(Alignment.TopStart).padding(start = start),
                ) { expanded = false }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(width = 52.dp, height = 40.dp)
                    .clickable { expanded = !expanded }
                    .semantics { contentDescription = if (expanded) "hide labels" else "more" },
            ) {
                Text("···", color = tokens.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (expanded) {
            menuItems.forEach { item ->
                Text(
                    item.label,
                    color = tokens.fg,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expanded = false
                            item.onClick()
                        }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                )
            }
            if (menuItems.isNotEmpty()) Spacer(Modifier.height(6.dp))
        }
    }
}

/**
 * A row of round buttons centred in [width], shrunk to leave the "···" corner and a matching space on the
 * other side clear. [onClicked] runs before an action does (it lowers an expanded bar).
 */
@Composable
private fun ActionRow(
    actions: List<HubAppBarAction>,
    width: Dp,
    tokens: ColorTokens,
    expanded: Boolean,
    modifier: Modifier,
    onClicked: () -> Unit,
) {
    val buttonWidth = minOf(64.dp, (width - 104.dp - 12.dp * (actions.size - 1).coerceAtLeast(0)) / actions.size.coerceAtLeast(1))
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .width(width)
            .wrapContentWidth(Alignment.CenterHorizontally)
            .padding(top = 8.dp, bottom = if (expanded) 6.dp else 8.dp),
    ) {
        actions.forEach { action ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(buttonWidth)
                    .clickable {
                        onClicked()
                        action.onClick()
                    }
                    .semantics { contentDescription = action.description },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(1.5.dp, tokens.fg), CircleShape),
                ) {
                    val realIcon = action.icon
                    if (realIcon != null) {
                        Image(bitmap = realIcon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(26.dp))
                    } else {
                        Icon(TileIcons[action.iconKey], contentDescription = null, tint = tokens.fg, modifier = Modifier.size(18.dp))
                    }
                }
                if (expanded) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        action.label,
                        color = tokens.fg,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The buttons for the second screen of a split window: what the section there offers, minus "back" and anything
 * the first screen's bar already has (the same description).
 */
fun rightScreenActions(left: List<HubAppBarAction>, right: List<HubAppBarAction>?): List<HubAppBarAction> =
    right.orEmpty().filter { it.iconKey != "back" && left.none { l -> l.description == it.description } }
