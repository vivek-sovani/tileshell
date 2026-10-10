package com.tileshell.feature.livetiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Windows Phone count badge — a filled circle with a ring and number in
 * [ring] — shared by the hub tiles' app icons and the hub apps pages. Matches
 * Start's `NotificationBadge` (`:feature:start`, which can't be reached from
 * here), so every badge in the launcher reads the same.
 */
@Composable
internal fun OutlinedCountBadge(
    count: Int,
    ring: Color,
    fill: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 18.dp,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = diameter, minHeight = diameter)
            .height(diameter)
            .background(fill, CircleShape)
            .border(1.5.dp, ring, CircleShape)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = ring,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
