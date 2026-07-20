package com.tileshell.feature.start

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens
import com.tileshell.feature.livetiles.nextScreenTimeoutPreset
import com.tileshell.feature.livetiles.openWriteSettingsAccess
import com.tileshell.feature.livetiles.rememberAirplaneModeOn
import com.tileshell.feature.livetiles.rememberBatterySaverOn
import com.tileshell.feature.livetiles.rememberDndAccessGranted
import com.tileshell.feature.livetiles.rememberDndOn
import com.tileshell.feature.livetiles.rememberLocationEnabled
import com.tileshell.feature.livetiles.rememberRingerVibrate
import com.tileshell.feature.livetiles.rememberRotationLockOn
import com.tileshell.feature.livetiles.rememberScreenBrightness
import com.tileshell.feature.livetiles.rememberScreenTimeoutMs
import com.tileshell.feature.livetiles.rememberStreamVolume
import com.tileshell.feature.livetiles.rememberTorchOn
import com.tileshell.feature.livetiles.rememberWifiEnabled
import com.tileshell.feature.livetiles.rememberWriteSettingsGranted
import com.tileshell.feature.livetiles.screenTimeoutLabel
import com.tileshell.feature.livetiles.setRotationLock
import com.tileshell.feature.livetiles.toggleDnd

/**
 * Quick panel: a two-finger swipe-up on Start opens this, sliding up from the
 * bottom edge (motion follows the finger, same physical logic as the classic
 * iOS Control Center gesture — chosen so it can never collide with quick
 * search's two-finger swipe-**down**). See docs/QUICK-PANEL-SPEC.md for the
 * full design rationale and the no-new-Play-Console-permission scoping. Every
 * chip is either a genuine toggle (flashlight, DND once access is granted,
 * ringer normal/vibrate) or a read state + tap-to-settings deep link (Wi-Fi,
 * Bluetooth, airplane mode, location, battery saver) — visually identical
 * either way, since the user never needs to know which is which.
 */
@Composable
fun QuickPanelOverlay(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "quickPanelProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current

    BackHandler(enabled = visible) { onDismiss() }

    val wifiOn = rememberWifiEnabled()
    val airplaneOn = rememberAirplaneModeOn()
    val locationOn = rememberLocationEnabled()
    val batterySaverOn = rememberBatterySaverOn()
    val (torchOn, toggleTorch) = rememberTorchOn()
    val dndGranted = rememberDndAccessGranted()
    val dndOn = rememberDndOn()
    val writeSettingsGranted = rememberWriteSettingsGranted()
    val rotationLockOn = rememberRotationLockOn()
    val (brightness, setBrightness) = rememberScreenBrightness()
    val (screenTimeoutMs, setScreenTimeoutMs) = rememberScreenTimeoutMs()

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.sheet, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(top = 10.dp, bottom = 4.dp)
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(32.dp)
                    .height(3.dp)
                    .background(tokens.fgDim, shape = RoundedCornerShape(2.dp)),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .height(260.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    quickPanelChips(
                        context, wifiOn, airplaneOn, locationOn, batterySaverOn, torchOn, toggleTorch,
                        dndGranted, dndOn, writeSettingsGranted, rotationLockOn,
                    ),
                ) { chip ->
                    QuickPanelChip(chip, tokens = tokens, accent = accent)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VolumeRow("media", AudioManager.STREAM_MUSIC, accent, tokens, muteable = true)
                VolumeRow("ring", AudioManager.STREAM_RING, accent, tokens, muteable = true)
                // Alarm deliberately gets no mute action — a muted alarm is a
                // genuine footgun (see docs/QUICK-PANEL-SPEC.md §3a).
                VolumeRow("alarm", AudioManager.STREAM_ALARM, accent, tokens, muteable = false)

                if (writeSettingsGranted) {
                    BrightnessRow(brightness, setBrightness, accent, tokens)
                    ScreenTimeoutRow(
                        screenTimeoutMs,
                        onTap = { setScreenTimeoutMs(nextScreenTimeoutPreset(screenTimeoutMs)) },
                        tokens = tokens,
                    )
                } else {
                    SystemSettingsAccessRow(tokens) { openWriteSettingsAccess(context) }
                }
            }

            Box(modifier = Modifier.height(8.dp))
        }
    }
}

private data class QuickPanelChipSpec(
    val icon: String,
    val label: String,
    val active: Boolean,
    val onClick: () -> Unit,
)

private fun quickPanelChips(
    context: Context,
    wifiOn: Boolean,
    airplaneOn: Boolean,
    locationOn: Boolean,
    batterySaverOn: Boolean,
    torchOn: Boolean,
    toggleTorch: () -> Unit,
    dndGranted: Boolean,
    dndOn: Boolean,
    writeSettingsGranted: Boolean,
    rotationLockOn: Boolean,
): List<QuickPanelChipSpec> = listOf(
    QuickPanelChipSpec("wifi", "wifi", wifiOn) { openWifiSettings(context) },
    // Bluetooth: no live state (reading it needs BLUETOOTH_CONNECT on API 31+,
    // a new dangerous permission — see docs/QUICK-PANEL-SPEC.md), tap-only.
    QuickPanelChipSpec("bluetooth", "bluetooth", false) { deepLink(context, Settings.ACTION_BLUETOOTH_SETTINGS) },
    QuickPanelChipSpec("flashlight", "flashlight", torchOn, toggleTorch),
    QuickPanelChipSpec("dnd", "dnd", dndOn) {
        // Once access is granted this is a genuine toggle; until then, deep-link
        // to the general "Do Not Disturb" settings screen (which also surfaces
        // the access-grant prompt itself) rather than straight to
        // ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS — that screen is an
        // app-by-app access list, not the DND settings a user tapping this chip
        // actually expects to land on.
        if (dndGranted) toggleDnd(context, !dndOn) else openDndSettings(context)
    },
    QuickPanelChipSpec("airplane", "airplane", airplaneOn) { deepLink(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS) },
    QuickPanelChipSpec("maps", "location", locationOn) { deepLink(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
    QuickPanelChipSpec("rotate", "rotation lock", rotationLockOn) {
        // A genuine toggle once WRITE_SETTINGS is granted; until then, tapping
        // deep-links to the grant screen instead of silently no-op'ing.
        if (writeSettingsGranted) setRotationLock(context, !rotationLockOn) else openWriteSettingsAccess(context)
    },
)

@Composable
private fun QuickPanelChip(
    chip: QuickPanelChipSpec,
    tokens: com.tileshell.core.design.ColorTokens,
    accent: Color,
) {
    val bg = if (chip.active) accent.copy(alpha = 0.18f) else tokens.chip
    val tint = if (chip.active) accent else tokens.fgDim
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(bg, shape = RoundedCornerShape(12.dp))
            .clickable(onClick = chip.onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(TileIcons[chip.icon], null, tint = tint, modifier = Modifier.size(20.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
        Text(chip.label, color = tint, fontSize = 13.sp)
    }
}

@Composable
private fun VolumeRow(
    label: String,
    stream: Int,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    muteable: Boolean,
) {
    val (level, setLevel) = rememberStreamVolume(stream)
    var draft by remember { mutableStateOf(level) }
    // Remembers the level to restore on unmute — updated whenever the stream is
    // audibly above zero, so muting-then-unmuting always comes back to the last
    // level the user actually chose, not a fixed default.
    var preMuteLevel by remember { mutableStateOf(if (level > 0f) level else 0.5f) }
    LaunchedEffect(level) {
        draft = level
        if (level > 0f) preMuteLevel = level
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (muteable) {
            val muted = draft <= 0f
            Icon(
                TileIcons[if (muted) "volume-mute" else "volume"],
                contentDescription = if (muted) "unmute $label" else "mute $label",
                tint = tokens.fgDim,
                modifier = Modifier.size(18.dp).clickable {
                    val next = if (muted) preMuteLevel else 0f
                    draft = next
                    setLevel(next)
                },
            )
        } else {
            Icon(TileIcons["volume"], null, tint = tokens.fgDim, modifier = Modifier.size(18.dp))
        }
        Text(
            label,
            color = tokens.fgDim,
            fontSize = 12.sp,
            modifier = Modifier.width(48.dp).padding(start = 8.dp),
        )
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { setLevel(draft) },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = tokens.tileLine,
            ),
        )
    }
}

@Composable
private fun BrightnessRow(
    level: Float,
    setLevel: (Float) -> Unit,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
) {
    var draft by remember { mutableStateOf(level) }
    LaunchedEffect(level) { draft = level }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(TileIcons["brightness"], null, tint = tokens.fgDim, modifier = Modifier.size(18.dp))
        Text(
            "brightness",
            color = tokens.fgDim,
            fontSize = 12.sp,
            modifier = Modifier.width(64.dp).padding(start = 8.dp),
        )
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { setLevel(draft) },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = tokens.tileLine,
            ),
        )
    }
}

/** Tap cycles through [SCREEN_TIMEOUT_PRESETS_MS] — simpler than a picker dialog for a small preset list. */
@Composable
private fun ScreenTimeoutRow(currentMs: Long, onTap: () -> Unit, tokens: com.tileshell.core.design.ColorTokens) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(TileIcons["clock"], null, tint = tokens.fgDim, modifier = Modifier.size(18.dp))
        Text(
            "screen timeout",
            color = tokens.fgDim,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        Text(screenTimeoutLabel(currentMs), color = tokens.fg, fontSize = 13.sp)
    }
}

/** Shown instead of brightness/screen-timeout until WRITE_SETTINGS is granted (rotation lock's chip has its own inline fallback). */
@Composable
private fun SystemSettingsAccessRow(tokens: com.tileshell.core.design.ColorTokens, onRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRequest)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "allow modify system settings for brightness & screen timeout",
            color = tokens.fgDim,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Text("allow", color = tokens.fg, fontSize = 12.sp)
    }
}

private fun openWifiSettings(context: Context) {
    val panel = runCatching { context.startActivity(Intent("android.settings.panel.action.WIFI")) }
    if (panel.isFailure) deepLink(context, Settings.ACTION_WIFI_SETTINGS)
}

/**
 * The general "Do Not Disturb" settings screen — not part of the public SDK
 * (there's no `Settings.ACTION_ZEN_MODE_SETTINGS` constant), but the action
 * string itself is a stable AOSP intent-filter present since Marshmallow.
 * Falls back to the access-grant screen (which also lets the user turn DND on
 * from there) if a device's Settings app doesn't expose it.
 */
private fun openDndSettings(context: Context) {
    val general = runCatching { context.startActivity(Intent("android.settings.ZEN_MODE_SETTINGS")) }
    if (general.isFailure) deepLink(context, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
}

private fun deepLink(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action)) }
}
