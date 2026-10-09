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
import androidx.compose.foundation.layout.wrapContentHeight
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
import com.tileshell.core.data.AutoExportNaming
import com.tileshell.core.data.AutoExportState
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens

/** The two backup screens, opened from their own rows in personalize (reset has no screen: its row starts the setup). */
enum class BackupSection(val title: String, val description: String) {
    SNAPSHOTS(
        "snapshots",
        "quick restore points of your start layout and settings, kept on this phone. they won't help if you lose or change the phone: use a backup file for that.",
    ),
    FILE(
        "backup file",
        "everything in one file you keep: your layout, settings, notes, tasks and music favourites. use it to move to a new phone or as a safety copy.",
    ),
}

/**
 * One backup screen ([section]): snapshots on this phone, or the backup file. Opened from its own row in
 * personalize → backups, snapshots & reset (personalize → backups, snapshots & reset): layout history,
 * auto-save + frequency, save-now, and file export/import. Pulled out of the main
 * [PersonalizeSheet] — which was growing too long — the same way [AboutSheet] and
 * [LayoutHistorySheet] already stand on their own.
 */
@Composable
fun BackupRestoreSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    section: BackupSection,
    onDismiss: () -> Unit,
    onOpenHistory: () -> Unit,
    onSaveSnapshot: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    autoBackupEnabled: Boolean,
    autoBackupIntervalHours: Int,
    onAutoBackupEnabled: (Boolean) -> Unit,
    onAutoBackupInterval: (Int) -> Unit,
    autoExport: AutoExportState,
    onChooseExportFolder: () -> Unit,
    onAutoExportEnabled: (Boolean) -> Unit,
    onAutoExportInterval: (Int) -> Unit,
    onAutoExportNow: () -> Unit,
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
                    .wrapContentHeight()
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
                    text = section.title,
                    color = tokens.fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Text(
                    text = section.description,
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                )

                Column(modifier = Modifier.padding(horizontal = 20.dp)) {

                    if (section == BackupSection.SNAPSHOTS) {
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
                            Text(text = "automatic snapshots", color = tokens.fg, fontSize = 14.sp)
                            Text(
                                text = if (autoBackupEnabled) "a snapshot is taken $intervalLabel" else "off",
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

                    WallpaperNavRow("save a snapshot now", "save ›", accent, tokens, onSaveSnapshot)
                    WallpaperNavRow("restore a previous layout", "view ›", accent, tokens, onOpenHistory)
                    }

                    if (section == BackupSection.FILE) {

                    WallpaperNavRow("export a backup file", "save ›", accent, tokens, onExportBackup)
                    WallpaperNavRow("restore from a backup file", "open ›", accent, tokens, onRestoreBackup)
                    AutoExportSection(
                        state = autoExport,
                        accent = accent,
                        tokens = tokens,
                        onChooseFolder = onChooseExportFolder,
                        onEnabled = onAutoExportEnabled,
                        onInterval = onAutoExportInterval,
                        onNow = onAutoExportNow,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "tip: save the exported file to google drive (or another cloud folder) " +
                            "so it's there to restore on your next device.",
                        color = tokens.fgDim,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    }
                }
            }
        }
    }
}

/**
 * "auto-export to a folder": pick a folder once (Drive and other cloud folders
 * work) and TileShell saves a dated full backup there on a schedule, keeping
 * the newest few. The same file as "export layout".
 */
@Composable
private fun AutoExportSection(
    state: AutoExportState,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onChooseFolder: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onInterval: (Int) -> Unit,
    onNow: () -> Unit,
) {
    val summary = when {
        state.folderUri == null -> "off · choose a folder to start"
        !state.enabled -> "off · folder: ${state.folderName ?: "chosen"}"
        else -> (if (state.intervalDays <= 1) "daily" else "weekly") +
            " · keeps the last ${AutoExportState.KEEP} · ${state.folderName ?: "chosen folder"}"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "auto-export to a folder", color = tokens.fg, fontSize = 14.sp)
            Text(text = summary, color = tokens.fgDim, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Switch(
            checked = state.enabled && state.folderUri != null,
            onCheckedChange = { on ->
                // Nothing to turn on until a folder is chosen.
                if (on && state.folderUri == null) onChooseFolder() else onEnabled(on)
            },
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accent),
        )
    }
    WallpaperNavRow(
        "folder",
        if (state.folderUri == null) "choose ›" else "change ›",
        accent, tokens, onChooseFolder,
    )
    if (state.ready) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = "how often", color = tokens.fgDim, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            listOf(1 to "daily", 7 to "weekly").forEach { (days, label) ->
                val selected = (state.intervalDays <= 1) == (days == 1)
                Text(
                    text = label,
                    color = if (selected) Color.White else tokens.fgDim,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(
                            if (selected) accent else tokens.fgDim.copy(alpha = 0.12f),
                            RoundedCornerShape(4.dp),
                        )
                        .clickable { onInterval(days) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        WallpaperNavRow("export now", "save ›", accent, tokens, onNow)
    }
    AutoExportNaming.statusLine(state, System.currentTimeMillis())?.let {
        Text(
            text = it,
            color = if (state.lastError != null) accent else tokens.fgDim,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
