package com.tileshell.feature.personalize

import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.tileshell.core.data.AppEntry
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The hidden-apps sub-sheet (personalize → hidden apps): lists every app hidden
 * from the app list (via the "hide" long-press action there) with a "show"
 * action to bring it back. Follows the same slide-up shape as
 * [AboutSheet]/[BackupRestoreSheet].
 */
@Composable
fun HiddenAppsSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    apps: List<AppEntry>,
    onUnhide: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "hiddenAppsSheetProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)

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
                    .fillMaxHeight(0.62f)
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
                    text = "hidden apps",
                    color = tokens.fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W300,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                )
                Text(
                    text = "apps you've hidden from the app list",
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                )
                HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp))

                if (apps.isEmpty()) {
                    Text(
                        text = "no hidden apps — long-press an app in the app list to hide it",
                        color = tokens.fgDim,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
                    ) {
                        items(apps, key = { it.key }) { app ->
                            HiddenAppRow(app = app, accent = accent, tokens = tokens, onUnhide = { onUnhide(app) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenAppRow(
    app: AppEntry,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onUnhide: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            val icon = rememberAppIcon(app.packageName, app.activityName)
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                Icon(TileIcons["app"], null, tint = tokens.fg, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(app.label, color = tokens.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(
            text = "show",
            color = accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.W500,
            modifier = Modifier.clickable(onClick = onUnhide).padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

/** Loads an app's launcher icon off the main thread, as an [ImageBitmap]. */
@Composable
private fun rememberAppIcon(packageName: String, activityName: String): ImageBitmap? {
    val context = LocalContext.current
    val state = produceState<ImageBitmap?>(null, packageName, activityName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getActivityIcon(ComponentName(packageName, activityName))
                    .toBitmap(width = 96, height = 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }
    return state.value
}
