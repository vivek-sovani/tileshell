package com.tileshell.feature.livetiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.TileIcons

/**
 * One button in a [HubAppBar]: a round icon whose [label] shows when the bar
 * is expanded with "···"; [description] is what TalkBack reads.
 */
internal class HubAppBarAction(
    val iconKey: String,
    val description: String,
    val label: String,
    val onClick: () -> Unit,
) {
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
internal fun HubAppBar(
    tokens: ColorTokens,
    actions: List<HubAppBarAction>,
    modifier: Modifier = Modifier,
    menuItems: List<HubAppBarAction> = emptyList(),
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(tokens.sheet),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp, bottom = if (expanded) 6.dp else 8.dp),
            ) {
                actions.forEach { action ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(64.dp)
                            .clickable {
                                expanded = false
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
                            Icon(TileIcons[action.iconKey], contentDescription = null, tint = tokens.fg, modifier = Modifier.size(18.dp))
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
