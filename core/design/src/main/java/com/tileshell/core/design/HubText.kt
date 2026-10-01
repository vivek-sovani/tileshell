package com.tileshell.core.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A filter or choice in a hub, the way Lumia showed one: plain light text,
 * the selected one in the accent colour and the rest grey. Replaces the
 * rounded pill chips (user-requested, "match lumia"). [leading] can add a
 * small icon before the label, such as the app's own icon.
 */
@Composable
fun HubFilter(
    label: String,
    selected: Boolean,
    tokens: ColorTokens,
    accent: Color,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics {
                this.selected = selected
                role = Role.Tab
            }
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = if (selected) accent else tokens.fgDim,
            fontSize = 17.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
        )
    }
}
