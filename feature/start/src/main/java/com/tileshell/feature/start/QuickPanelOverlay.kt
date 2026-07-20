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
import com.tileshell.feature.livetiles.rememberAirplaneModeOn
import com.tileshell.feature.livetiles.rememberBatterySaverOn
import com.tileshell.feature.livetiles.rememberDndAccessGranted
import com.tileshell.feature.livetiles.rememberDndOn
import com.tileshell.feature.livetiles.rememberLocationEnabled
import com.tileshell.feature.livetiles.rememberRingerVibrate
import com.tileshell.feature.livetiles.rememberStreamVolume
import com.tileshell.feature.livetiles.rememberTorchOn
import com.tileshell.feature.livetiles.rememberWifiEnabled
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
                    .height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(quickPanelChips(context, wifiOn, airplaneOn, locationOn, batterySaverOn, torchOn, toggleTorch, dndGranted, dndOn)) { chip ->
                    QuickPanelChip(chip, tokens = tokens, accent = accent)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VolumeRow("media", AudioManager.STREAM_MUSIC, accent, tokens)
                VolumeRow("ring", AudioManager.STREAM_RING, accent, tokens)
                VolumeRow("alarm", AudioManager.STREAM_ALARM, accent, tokens)
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
): List<QuickPanelChipSpec> = listOf(
    QuickPanelChipSpec("wifi", "wifi", wifiOn) { openWifiSettings(context) },
    // Bluetooth: no live state (reading it needs BLUETOOTH_CONNECT on API 31+,
    // a new dangerous permission — see docs/QUICK-PANEL-SPEC.md), tap-only.
    QuickPanelChipSpec("bluetooth", "bluetooth", false) { deepLink(context, Settings.ACTION_BLUETOOTH_SETTINGS) },
    QuickPanelChipSpec("flashlight", "flashlight", torchOn, toggleTorch),
    QuickPanelChipSpec("dnd", "dnd", dndOn) {
        if (dndGranted) toggleDnd(context, !dndOn)
        else deepLink(context, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    },
    QuickPanelChipSpec("airplane", "airplane", airplaneOn) { deepLink(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS) },
    QuickPanelChipSpec("maps", "location", locationOn) { deepLink(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
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
            .background(bg, shape = RoundedCornerShape(10.dp))
            .clickable(onClick = chip.onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(TileIcons[chip.icon], null, tint = tint, modifier = Modifier.size(18.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        Text(chip.label, color = tint, fontSize = 12.sp)
    }
}

@Composable
private fun VolumeRow(label: String, stream: Int, accent: Color, tokens: com.tileshell.core.design.ColorTokens) {
    val (level, setLevel) = rememberStreamVolume(stream)
    var draft by remember { mutableStateOf(level) }
    LaunchedEffect(level) { draft = level }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(TileIcons["volume"], null, tint = tokens.fgDim, modifier = Modifier.size(16.dp))
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

private fun openWifiSettings(context: Context) {
    val panel = runCatching { context.startActivity(Intent("android.settings.panel.action.WIFI")) }
    if (panel.isFailure) deepLink(context, Settings.ACTION_WIFI_SETTINGS)
}

private fun deepLink(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action)) }
}
