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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens

/**
 * The "permissions" sub-sheet (personalize → contacts, calendar, location &
 * physical activity) — the data-source permissions live tiles and the
 * home-screen widgets read from. "badges & live mail" moved out of here into
 * the "live tiles" group instead (it feeds live-tile content, not a
 * data-source permission in this sense), so this sheet is scoped to those
 * four, the same way [BackupRestoreSheet] and [HiddenAppsSheet] already stand
 * on their own.
 */
@Composable
fun PermissionsSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    contactsGranted: Boolean,
    calendarGranted: Boolean,
    locationGranted: Boolean,
    activityGranted: Boolean,
    onRequestContacts: () -> Unit,
    onRequestCalendar: () -> Unit,
    onRequestLocation: () -> Unit,
    onRequestActivity: () -> Unit,
    musicGranted: Boolean,
    onRequestMusic: () -> Unit,
    postNotificationsGranted: Boolean,
    onRequestPostNotifications: () -> Unit,
    notificationAccess: Boolean,
    onNotificationAccess: () -> Unit,
    usageAccess: Boolean,
    onUsageAccess: () -> Unit,
    batteryExempt: Boolean,
    onBatteryExemption: () -> Unit,
    writeSettings: Boolean,
    onWriteSettings: () -> Unit,
    accessibilityEnabled: Boolean,
    onAccessibility: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "permissionsSheetProgress",
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
                    .fillMaxHeight()
                    .graphicsLayer { translationY = size.height * (1f - progress) }
                    .background(tokens.sheet, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .statusBarsPadding()
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
                    text = "permissions",
                    color = tokens.fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )

                Text(
                    text = "what tileshell can use, and what for. only the weather location ever leaves your phone.",
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )

                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    GroupLabel("data", tokens)
                    PermissionRow("contacts", "people hub · quick search", contactsGranted, accent, tokens, onRequestContacts)
                    PermissionRow("calendar", "calendar tile · productivity", calendarGranted, accent, tokens, onRequestCalendar)
                    PermissionRow("location", "weather · sunrise and moonrise", locationGranted, accent, tokens, onRequestLocation)
                    // Asked contextually (the first time a steps face renders),
                    // so this row is the way back after a missed or declined ask.
                    PermissionRow("physical activity", "steps tile and widget", activityGranted, accent, tokens, onRequestActivity)
                    PermissionRow("music & audio", "music hub library", musicGranted, accent, tokens, onRequestMusic)
                    PermissionRow("notifications", "music player controls", postNotificationsGranted, accent, tokens, onRequestPostNotifications)

                    GroupLabel("special access · opens android settings", tokens)
                    PermissionRow("notification access", "badges, what's new, previews", notificationAccess, accent, tokens, onNotificationAccess, special = true)
                    PermissionRow("usage access", "most-used apps, screen time", usageAccess, accent, tokens, onUsageAccess, special = true)
                    PermissionRow("background battery", "live updates with the screen off", batteryExempt, accent, tokens, onBatteryExemption, special = true)
                    PermissionRow("modify system settings", "quick panel brightness, timeout", writeSettings, accent, tokens, onWriteSettings, special = true)
                    PermissionRow("accessibility", "lock screen, recents, edge swipes", accessibilityEnabled, accent, tokens, onAccessibility, special = true)
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String, tokens: ColorTokens) {
    Text(
        text = text,
        color = tokens.fgDim,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

/** A permission row: label + description on the left, status / "allow" on the right. */
@Composable
private fun PermissionRow(
    label: String,
    description: String,
    granted: Boolean,
    accent: Color,
    tokens: ColorTokens,
    onClick: () -> Unit,
    special: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = tokens.fg, fontSize = 14.sp)
            Text(text = description, color = tokens.fgDim, fontSize = 12.sp)
        }
        Text(
            text = when {
                granted && special -> "on ✓"
                granted -> "allowed ✓"
                special -> "turn on ›"
                else -> "allow ›"
            },
            color = if (granted) accent else tokens.fgDim,
            fontSize = 13.sp,
        )
    }
}
