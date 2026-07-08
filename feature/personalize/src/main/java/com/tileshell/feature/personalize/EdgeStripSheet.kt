package com.tileshell.feature.personalize

import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import com.tileshell.core.design.Wallpapers
import com.tileshell.core.design.colorTokens
import com.tileshell.core.design.wallpaperBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Edge-strip settings sub-sheet (personalize → edge strip): enable/disable toggle,
 * position picker (bottom / left), app multi-picker from the installed app list,
 * and a background selector (none or one of the 6 bundled gradients).
 */
@Composable
fun EdgeStripSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    enabled: Boolean,
    position: String,
    selectedApps: List<String>,
    installedApps: List<AppEntry>,
    backgroundId: String,
    handleSize: String,
    onEnabledChange: (Boolean) -> Unit,
    onPositionChange: (String) -> Unit,
    onAppsChange: (List<String>) -> Unit,
    onBackgroundChange: (String) -> Unit,
    onHandleSizeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "edgeStripSheetProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)

    val sortedApps = remember(installedApps) {
        installedApps.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

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
                    .fillMaxHeight(0.88f)
                    .graphicsLayer { translationY = size.height * (1f - progress) }
                    .background(tokens.sheet, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                // Drag handle
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
                    text = "edge strip",
                    color = tokens.fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W300,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                ) {
                    // ---- enable toggle ----
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEnabledChange(!enabled) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("enable edge strip", color = tokens.fg, fontSize = 15.sp)
                                Text(
                                    "shortcuts at the screen edge, auto-hides when not in use",
                                    color = tokens.fgDim, fontSize = 12.sp,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (enabled) "on" else "off",
                                color = if (enabled) accent else tokens.fgDim,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.W500,
                            )
                        }
                        HorizontalDivider(
                            color = tokens.tileLine,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }

                    if (enabled) {
                        // ---- background ----
                        item {
                            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                                Text(
                                    "background",
                                    color = tokens.fgDim,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 10.dp),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // "none" swatch
                                    val noneSelected = backgroundId == Wallpapers.NONE_ID
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .then(
                                                if (noneSelected) Modifier.border(
                                                    2.5.dp, accent, RoundedCornerShape(6.dp),
                                                ).padding(4.dp) else Modifier
                                            )
                                            .background(
                                                if (dark) Color(0xFF111118) else Color(0xFFECE9E4),
                                                RoundedCornerShape(6.dp),
                                            )
                                            .border(1.dp, tokens.tileLine, RoundedCornerShape(6.dp))
                                            .clickable { onBackgroundChange(Wallpapers.NONE_ID) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("—", color = tokens.fgDim, fontSize = 12.sp)
                                    }
                                    // Gradient swatches
                                    Wallpapers.all.forEach { grad ->
                                        val sel = backgroundId == grad.id
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .then(
                                                    if (sel) Modifier.border(
                                                        2.5.dp, accent, RoundedCornerShape(6.dp),
                                                    ).padding(4.dp) else Modifier
                                                )
                                                .clip(RoundedCornerShape(6.dp))
                                                .wallpaperBackground(grad)
                                                .clickable { onBackgroundChange(grad.id) },
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = tokens.tileLine,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }

                        // ---- handle size ----
                        item {
                            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                                Text(
                                    "handle size",
                                    color = tokens.fgDim,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 10.dp),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("thin", "medium", "thick").forEach { size ->
                                        val sel = handleSize == size
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (sel) accent else Color.Transparent)
                                                .border(
                                                    1.5.dp,
                                                    if (sel) accent else tokens.fgDim.copy(alpha = 0.4f),
                                                    RoundedCornerShape(6.dp),
                                                )
                                                .clickable { onHandleSizeChange(size) }
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                        ) {
                                            Text(
                                                text = size,
                                                color = if (sel) Color.White else tokens.fg,
                                                fontSize = 13.sp,
                                                fontWeight = if (sel) FontWeight.W600 else FontWeight.W400,
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = tokens.tileLine,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }

                        // ---- strip order (reorder selected apps) ----
                        if (selectedApps.isNotEmpty()) {
                            item(key = "order_header") {
                                Text(
                                    "strip order",
                                    color = tokens.fgDim,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(
                                        start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp,
                                    ),
                                )
                            }
                            itemsIndexed(selectedApps, key = { _, pkg -> "order_$pkg" }) { index, pkg ->
                                val app = sortedApps.firstOrNull { it.packageName == pkg }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier.size(28.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (app != null) {
                                            val icon = rememberEdgeStripAppIcon(
                                                app.packageName, app.activityName,
                                            )
                                            if (icon != null) {
                                                Image(
                                                    bitmap = icon,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = app?.label ?: pkg,
                                        color = tokens.fg,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    val canUp = index > 0
                                    val canDown = index < selectedApps.lastIndex
                                    Text(
                                        text = "↑",
                                        color = if (canUp) accent else tokens.fgDim.copy(alpha = 0.25f),
                                        fontSize = 20.sp,
                                        modifier = Modifier
                                            .clickable(enabled = canUp) {
                                                val list = selectedApps.toMutableList()
                                                list.add(index - 1, list.removeAt(index))
                                                onAppsChange(list)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                    Text(
                                        text = "↓",
                                        color = if (canDown) accent else tokens.fgDim.copy(alpha = 0.25f),
                                        fontSize = 20.sp,
                                        modifier = Modifier
                                            .clickable(enabled = canDown) {
                                                val list = selectedApps.toMutableList()
                                                list.add(index + 1, list.removeAt(index))
                                                onAppsChange(list)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                    Text(
                                        text = "−",
                                        color = tokens.fgDim.copy(alpha = 0.6f),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.W300,
                                        modifier = Modifier
                                            .clickable {
                                                onAppsChange(selectedApps - pkg)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                            item(key = "order_divider") {
                                HorizontalDivider(
                                    color = tokens.tileLine,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                )
                            }
                        }

                        // ---- app selector header ----
                        item {
                            val count = selectedApps.size
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("apps", color = tokens.fgDim, fontSize = 13.sp)
                                    if (count > 0) {
                                        Text(
                                            "$count selected",
                                            color = accent,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                                if (count > 0) {
                                    Text(
                                        "clear all",
                                        color = tokens.fgDim,
                                        fontSize = 13.sp,
                                        modifier = Modifier
                                            .clickable { onAppsChange(emptyList()) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }

                        // ---- app rows ----
                        items(
                            sortedApps,
                            key = { it.key },
                        ) { app ->
                            val selected = app.packageName in selectedApps
                            EdgeStripAppRow(
                                app = app,
                                selected = selected,
                                accent = accent,
                                tokens = tokens,
                            ) {
                                val next = if (selected) {
                                    selectedApps - app.packageName
                                } else {
                                    selectedApps + app.packageName
                                }
                                onAppsChange(next)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EdgeStripAppRow(
    app: AppEntry,
    selected: Boolean,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            val icon = rememberEdgeStripAppIcon(app.packageName, app.activityName)
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(app.label, color = tokens.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) accent else Color.Transparent)
                .border(
                    1.5.dp,
                    if (selected) accent else tokens.fgDim.copy(alpha = 0.5f),
                    RoundedCornerShape(4.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun rememberEdgeStripAppIcon(packageName: String, activityName: String): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null, packageName, activityName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getActivityIcon(ComponentName(packageName, activityName))
                    .toBitmap(width = 96, height = 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }.value
}
