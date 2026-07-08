package com.tileshell.feature.start

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.Wallpapers
import com.tileshell.feature.livetiles.NotificationSnapshot
import com.tileshell.feature.livetiles.rememberAppIconBitmap

private val TILE_DP = 48.dp      // slot size — compact touch target
private val STRIP_ICON = 34.dp   // matches Start screen glyph size for non-LARGE tiles
private val STRIP_GAP = 3.dp
private val STRIP_PAD = 6.dp

// Total thickness: 28dp handle + 48dp slot + 6dp pad = 82dp.
// When collapsed only 14dp (half the handle) peeks at the edge.
private val HANDLE_EXTENT = 28.dp
private val STRIP_THICK = HANDLE_EXTENT + TILE_DP + STRIP_PAD

/**
 * Optional bottom edge-strip overlay: search shortcut on the left, a horizontal
 * row of pinned app-icon shortcuts in the middle, and a recents shortcut on the
 * right. Auto-hides to a 14dp sliver at the bottom edge; tap to reveal/collapse.
 * Background is either one of the 6 bundled gradients or a translucent surface.
 * No live tile faces — static icon + badge only.
 */
@Composable
internal fun BoxScope.EdgeStrip(
    apps: List<String>,
    backgroundId: String,
    notifications: NotificationSnapshot,
    dark: Boolean,
    accent: Color,
    onLaunch: (pkg: String) -> Unit,
    onSearch: () -> Unit,
    onRecents: () -> Unit,
) {
    if (apps.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(true) }

    val slide by animateFloatAsState(
        targetValue = if (expanded) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "edgeStripSlide",
    )

    val wallpaper = if (backgroundId != Wallpapers.NONE_ID) Wallpapers.forId(backgroundId) else null
    val solidBg = if (dark) Color(0xF0111118) else Color(0xF0F0EDE8)
    val iconTint = if (dark) Color.White.copy(alpha = 0.85f) else Color(0xFF111111).copy(alpha = 0.85f)

    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .graphicsLayer {
                translationY = (STRIP_THICK - HANDLE_EXTENT * 0.5f).toPx() * slide
            }
            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
            .then(if (wallpaper == null) Modifier.background(solidBg) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        if (wallpaper != null) {
            WallpaperBackground(
                gradient = wallpaper,
                customWallpaperUri = null,
                blur = false,
                dark = dark,
                modifier = Modifier.matchParentSize(),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StripHandle(accent = accent) { expanded = !expanded }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = STRIP_PAD),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Search button — left end
                StripActionButton(
                    iconKey = "search",
                    tint = iconTint,
                    onClick = onSearch,
                )

                // App shortcuts — scrollable middle section
                LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = STRIP_GAP),
                    horizontalArrangement = Arrangement.spacedBy(STRIP_GAP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(apps, key = { it }) { pkg ->
                        EdgeIconSlot(pkg, notifications, dark, onLaunch)
                    }
                }

                // Recents button — right end
                StripActionButton(
                    iconKey = "recents",
                    tint = iconTint,
                    onClick = onRecents,
                )
            }
        }
    }
}

/** Pull tab that reveals/collapses the strip. */
@Composable
private fun StripHandle(accent: Color, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HANDLE_EXTENT)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .background(accent.copy(alpha = 0.7f), RoundedCornerShape(2.dp)),
        )
    }
}

/** Search / recents action button flanking the app row. */
@Composable
private fun StripActionButton(iconKey: String, tint: Color, onClick: () -> Unit) {
    val icon = TileIcons[iconKey]
    Box(
        modifier = Modifier
            .size(TILE_DP)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = iconKey,
                tint = tint,
                modifier = Modifier.size(STRIP_ICON),
            )
        }
    }
}

/** One 48×48dp app-icon slot with a notification badge pill. */
@Composable
private fun EdgeIconSlot(
    packageName: String,
    notifications: NotificationSnapshot,
    dark: Boolean,
    onLaunch: (String) -> Unit,
) {
    val icon = rememberAppIconBitmap(packageName)
    val badgeCount = notifications.badges[packageName] ?: 0

    Box(
        modifier = Modifier
            .size(TILE_DP)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onLaunch(packageName) },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(STRIP_ICON),
            )
        }
        if (badgeCount > 0) {
            val bg = if (dark) Color.White else Color(0xFF111111)
            val fg = if (dark) Color(0xFF111111) else Color.White
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp)
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .height(16.dp)
                    .background(bg, CircleShape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = fg,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}
