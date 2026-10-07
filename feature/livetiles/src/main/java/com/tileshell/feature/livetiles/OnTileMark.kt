package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.ColorTokens

/**
 * The hubs' "on tile" mark: ▣ in the accent when the row is on its tile, ▢
 * dim when it isn't. One look for every hub that lets you choose what a tile
 * shows (markets' watchlist and indices, people's favourites). The 48dp touch
 * target is the whole box; [description] is what TalkBack reads.
 */
@Composable
internal fun OnTileMark(
    on: Boolean,
    tokens: ColorTokens,
    accent: Color,
    description: String,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = description },
    ) {
        Text(if (on) "▣" else "▢", color = if (on) accent else tokens.fgDim, fontSize = 20.sp)
    }
}
