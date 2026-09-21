package com.tileshell.feature.livetiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.TileIcons

/** One circular button in a [HubAppBar]. */
internal data class HubAppBarAction(val iconKey: String, val description: String, val onClick: () -> Unit)

/**
 * The hub screens' bottom app bar — a translucent pill of outlined circular
 * icon buttons, matching the approved mockup exactly (the mockup's `.ts-appbar`/
 * `.ts-btn`: a rounded dark bar, each icon in its own bordered circle). Carries
 * "back" (dismiss) as one of its icons rather than a standalone top-corner
 * button, per the mockup's own bottom app-bar convention — user-confirmed
 * after the top-corner version was found to not match what was approved.
 */
@Composable
internal fun HubAppBar(tokens: ColorTokens, actions: List<HubAppBarAction>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(tokens.sheet)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            actions.forEach { action ->
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(1.2.dp, tokens.fgDim), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = action.onClick,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TileIcons[action.iconKey],
                        contentDescription = action.description,
                        tint = tokens.fg,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}
