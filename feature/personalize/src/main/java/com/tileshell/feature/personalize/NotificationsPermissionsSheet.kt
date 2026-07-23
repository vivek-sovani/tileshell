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
 * The "notifications & permissions" sub-sheet (personalize → contacts, calendar,
 * location & badges): moved verbatim out of the main [PersonalizeSheet]'s old
 * "permissions" (contacts/calendar/location) and "notifications" (badges & live
 * mail + battery exemption) groups, the same way [BackupRestoreSheet] and
 * [HiddenAppsSheet] already stand on their own.
 */
@Composable
fun NotificationsPermissionsSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    contactsGranted: Boolean,
    calendarGranted: Boolean,
    locationGranted: Boolean,
    onRequestContacts: () -> Unit,
    onRequestCalendar: () -> Unit,
    onRequestLocation: () -> Unit,
    notificationsEnabled: Boolean,
    onNotificationAccess: () -> Unit,
    batteryOptimizationExempt: Boolean,
    batteryGuidanceNote: String,
    onBatteryExemption: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "notificationsPermissionsSheetProgress",
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
                    text = "notifications & permissions",
                    color = tokens.fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )

                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "permissions",
                        color = tokens.fgDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 10.dp, top = 6.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PermissionRow(
                            label = "contacts",
                            description = "people tile · quick search",
                            granted = contactsGranted,
                            accent = accent,
                            tokens = tokens,
                            onClick = onRequestContacts,
                        )
                        PermissionRow(
                            label = "calendar",
                            description = "calendar tile",
                            granted = calendarGranted,
                            accent = accent,
                            tokens = tokens,
                            onClick = onRequestCalendar,
                        )
                        PermissionRow(
                            label = "location",
                            description = "weather tile",
                            granted = locationGranted,
                            accent = accent,
                            tokens = tokens,
                            onClick = onRequestLocation,
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "notifications",
                        color = tokens.fgDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNotificationAccess)
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "badges & live mail", color = tokens.fg, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (notificationsEnabled) "on ›" else "allow access ›",
                            color = if (notificationsEnabled) accent else tokens.fgDim,
                            fontSize = 13.sp,
                        )
                    }
                    if (notificationsEnabled && !batteryOptimizationExempt) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onBatteryExemption)
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "background activity",
                                    color = tokens.fg,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    text = if (batteryGuidanceNote.isNotEmpty()) {
                                        batteryGuidanceNote
                                    } else {
                                        "exempt from battery optimisation for reliable badges"
                                    },
                                    color = tokens.fgDim,
                                    fontSize = 12.sp,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(text = "fix ›", color = accent, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
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
            text = if (granted) "allowed ✓" else "allow ›",
            color = if (granted) accent else tokens.fgDim,
            fontSize = 13.sp,
        )
    }
}
