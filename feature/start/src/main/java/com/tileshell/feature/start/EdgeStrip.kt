package com.tileshell.feature.start

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.tileshell.core.design.Wallpapers
import com.tileshell.feature.livetiles.NotificationSnapshot
import com.tileshell.feature.livetiles.rememberAppIconBitmap

private val TILE_DP = 90.dp
private val STRIP_GAP = 3.dp
private val STRIP_PAD = 9.dp

// Total thickness (thin dimension): 20dp handle + 90dp tile + 9dp pad = 119dp.
// When collapsed only the 20dp handle peeks at the edge.
private val HANDLE_EXTENT = 20.dp
private val STRIP_THICK = HANDLE_EXTENT + TILE_DP + STRIP_PAD

/**
 * Optional edge-strip overlay: a horizontal row (bottom) or vertical column (left)
 * of small app-icon shortcuts with notification badges. Auto-hides to a 20dp
 * accent pull-tab at the screen edge; tap the tab to reveal or collapse.
 * Background is either one of the 6 bundled gradients or a translucent surface.
 * No live tile faces — static icon + badge only.
 */
@Composable
internal fun BoxScope.EdgeStrip(
    position: String,
    apps: List<String>,
    backgroundId: String,
    notifications: NotificationSnapshot,
    dark: Boolean,
    accent: Color,
    onLaunch: (pkg: String) -> Unit,
) {
    if (apps.isEmpty()) return

    val isBottom = position != "left"
    var expanded by rememberSaveable { mutableStateOf(true) }

    val slide by animateFloatAsState(
        targetValue = if (expanded) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "edgeStripSlide",
    )

    val wallpaper = if (backgroundId != Wallpapers.NONE_ID) Wallpapers.forId(backgroundId) else null
    val solidBg = if (dark) Color(0xF0111118) else Color(0xF0F0EDE8)

    if (isBottom) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    translationY = (STRIP_THICK - HANDLE_EXTENT).toPx() * slide
                }
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .then(if (wallpaper == null) Modifier.background(solidBg) else Modifier),
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
                StripHandle(isBottom = true, accent = accent) { expanded = !expanded }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(STRIP_GAP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(STRIP_PAD))
                    apps.forEach { pkg ->
                        EdgeIconSlot(pkg, notifications, dark, onLaunch)
                    }
                    Spacer(Modifier.width(STRIP_PAD))
                }
                Spacer(Modifier.height(STRIP_PAD))
            }
        }
    } else {
        // Left strip: icons column + pull-tab at right edge.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    translationX = -(STRIP_THICK - HANDLE_EXTENT).toPx() * slide
                }
                .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                .then(if (wallpaper == null) Modifier.background(solidBg) else Modifier),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(STRIP_GAP),
                ) {
                    Spacer(Modifier.height(STRIP_PAD))
                    apps.forEach { pkg ->
                        EdgeIconSlot(pkg, notifications, dark, onLaunch)
                    }
                    Spacer(Modifier.height(STRIP_PAD))
                }
                StripHandle(isBottom = false, accent = accent) { expanded = !expanded }
            }
        }
    }
}

/** Pull tab that reveals/collapses the strip. */
@Composable
private fun StripHandle(isBottom: Boolean, accent: Color, onToggle: () -> Unit) {
    Box(
        modifier = if (isBottom) {
            Modifier.fillMaxWidth().height(HANDLE_EXTENT)
        } else {
            Modifier.width(HANDLE_EXTENT).fillMaxHeight()
        }.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onToggle,
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (isBottom) {
            Box(
                Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .background(accent.copy(alpha = 0.7f), RoundedCornerShape(2.dp)),
            )
        } else {
            Box(
                Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .background(accent.copy(alpha = 0.7f), RoundedCornerShape(2.dp)),
            )
        }
    }
}

/** One 90×90dp app-icon slot with a notification badge pill. */
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
                modifier = Modifier.size(TILE_DP * 0.72f),
            )
        }
        if (badgeCount > 0) {
            val bg = if (dark) Color.White else Color(0xFF111111)
            val fg = if (dark) Color(0xFF111111) else Color.White
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 5.dp)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .height(18.dp)
                    .background(bg, CircleShape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = fg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}
