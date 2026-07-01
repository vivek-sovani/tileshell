package com.tileshell.feature.personalize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens

/**
 * The backup & restore sub-sheet (personalize → manage backups): layout history,
 * auto-save + frequency, save-now, and file export/import. Pulled out of the main
 * [PersonalizeSheet] — which was growing too long — the same way [AboutSheet] and
 * [LayoutHistorySheet] already stand on their own.
 */
@Composable
fun BackupRestoreSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    onOpenHistory: () -> Unit,
    onSaveSnapshot: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    autoBackupEnabled: Boolean,
    autoBackupIntervalHours: Int,
    onAutoBackupEnabled: (Boolean) -> Unit,
    onAutoBackupInterval: (Int) -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "backupSheetProgress",
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
                    )
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                // drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tokens.fgDim.copy(alpha = 0.4f)),
                )

                Text(
                    text = "backup & restore",
                    color = tokens.fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )

                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    WallpaperNavRow("layout history", "view ›", accent, tokens, onOpenHistory)

                    // Auto-save: description reflects current state so the toggle is self-explanatory
                    val intervalLabel = when {
                        autoBackupIntervalHours <= 6 -> "every 6h"
                        autoBackupIntervalHours <= 12 -> "every 12h"
                        else -> "daily"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "auto-save", color = tokens.fg, fontSize = 14.sp)
                            Text(
                                text = if (autoBackupEnabled) "saves automatically · $intervalLabel" else "off",
                                color = tokens.fgDim,
                                fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked = autoBackupEnabled,
                            onCheckedChange = onAutoBackupEnabled,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accent),
                        )
                    }
                    // Compact 3-option frequency picker — only shown when auto-save is on
                    if (autoBackupEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(text = "frequency", color = tokens.fgDim, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            listOf(6 to "6h", 12 to "12h", 24 to "daily").forEach { (h, label) ->
                                val selected = when (h) {
                                    6 -> autoBackupIntervalHours <= 6
                                    12 -> autoBackupIntervalHours in 7..23
                                    else -> autoBackupIntervalHours >= 24
                                }
                                Text(
                                    text = label,
                                    color = if (selected) Color.White else tokens.fgDim,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .background(
                                            if (selected) accent else tokens.fgDim.copy(alpha = 0.12f),
                                            RoundedCornerShape(4.dp),
                                        )
                                        .clickable { onAutoBackupInterval(h) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    WallpaperNavRow("save now", "save ›", accent, tokens, onSaveSnapshot)

                    // — file transfer divider —
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = tokens.fgDim.copy(alpha = 0.15f),
                        )
                        Text(
                            text = "  file transfer  ",
                            color = tokens.fgDim.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = tokens.fgDim.copy(alpha = 0.15f),
                        )
                    }

                    WallpaperNavRow("export layout", "save ›", accent, tokens, onExportBackup)
                    WallpaperNavRow("restore from file", "open ›", accent, tokens, onRestoreBackup)
                }
            }
        }
    }
}
