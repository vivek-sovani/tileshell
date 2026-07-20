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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
 * full design rationale and the no-new-Play-Console-permission scoping.
 *
 * Styled as a miniature Start screen rather than a generic Android settings
 * sheet: each toggle is a small colour-filled tile (accent when on, a neutral
 * dark tile when off) with a monoline icon and a lowercase corner label, the
 * same visual language as every Start tile. Volume/brightness render as wide
 * accent tiles with a dark scrim covering the "unfilled" remainder — a live
 * WP-tile-style progress fill instead of a Material slider — draggable
 * anywhere across the tile to set the level. Every toggle is either a
 * genuine toggle (flashlight, DND once access is granted) or a read state +
 * tap-to-settings deep link (Wi-Fi, Bluetooth, airplane mode, location,
 * battery saver) — visually identical either way.
 */
/** Shared row height for every toggle tile and slider bar — one proportional grid, not mixed sizes. */
private val QuickPanelRowHeight = 44.dp

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

            val chips = quickPanelChips(
                context, wifiOn, airplaneOn, locationOn, batterySaverOn, torchOn, toggleTorch,
                dndGranted, dndOn, writeSettingsGranted, rotationLockOn,
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { chip ->
                            QuickPanelTile(
                                chip,
                                tokens = tokens,
                                accent = accent,
                                modifier = Modifier.weight(1f).height(QuickPanelRowHeight),
                            )
                        }
                        repeat(3 - row.size) { Box(modifier = Modifier.weight(1f)) }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VolumeTile("media", AudioManager.STREAM_MUSIC, "volume", "volume-mute", accent)
                VolumeTile("ring", AudioManager.STREAM_RING, "bell", "bell-mute", accent)

                if (writeSettingsGranted) {
                    BrightnessTile(brightness, setBrightness, accent)
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

/**
 * A small colour-filled Start-tile-style toggle: accent fill when on, a
 * neutral dark tile when off. Same height as [LiveTileSlider]'s bar
 * ([QuickPanelRowHeight]) so every row in the panel — toggles and
 * volume/brightness alike — reads as one proportional grid rather than
 * chunky squares up top and thin bars below.
 */
@Composable
private fun QuickPanelTile(
    chip: QuickPanelChipSpec,
    tokens: com.tileshell.core.design.ColorTokens,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val bg = if (chip.active) accent else tokens.chip
    val fg = if (chip.active) Color.White else tokens.fgDim
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = chip.onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(TileIcons[chip.icon], null, tint = fg, modifier = Modifier.size(18.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
        Text(chip.label, color = fg, fontSize = 11.sp, maxLines = 1)
    }
}

/**
 * Volume as a wide accent tile (a real toggle, not read-only): the icon swaps
 * between on/muted state; dragging anywhere across the tile all the way to
 * the left edge is the mute gesture — the fill reaching zero already reads as
 * "muted", so there's no separate mute button competing for touch area with
 * the drag-to-set gesture.
 */
@Composable
private fun VolumeTile(label: String, stream: Int, icon: String, mutedIcon: String, accent: Color) {
    val (level, setLevel) = rememberStreamVolume(stream)
    var draft by remember { mutableStateOf(level) }
    LaunchedEffect(level) { draft = level }
    LiveTileSlider(
        icon = if (draft <= 0f) mutedIcon else icon,
        label = label,
        accent = accent,
        value = draft,
        onValueChange = { draft = it },
        onValueChangeFinished = { setLevel(draft) },
    )
}

@Composable
private fun BrightnessTile(level: Float, setLevel: (Float) -> Unit, accent: Color) {
    var draft by remember { mutableStateOf(level) }
    LaunchedEffect(level) { draft = level }
    LiveTileSlider(
        icon = "brightness",
        label = "brightness",
        accent = accent,
        value = draft,
        onValueChange = { draft = it },
        onValueChangeFinished = { setLevel(draft) },
    )
}

/**
 * A wide accent tile whose fill doubles as the slider — the WP live-tile
 * "progress" look instead of a Material slider: a dark scrim covers the
 * unfilled remainder from the right edge, and the filled (accent-visible)
 * portion grows from the left as [value] increases. Draggable anywhere
 * across the tile; the value updates live during the drag and commits via
 * [onValueChangeFinished] on release (matches the volume/brightness APIs'
 * own "write on release, not per-frame" contract).
 */
@Composable
private fun LiveTileSlider(
    icon: String,
    label: String,
    accent: Color,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    var widthPx by remember { mutableStateOf(1f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(QuickPanelRowHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(accent)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onValueChange((down.position.x / widthPx).coerceIn(0f, 1f))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        onValueChange((change.position.x / widthPx).coerceIn(0f, 1f))
                        change.consume()
                    }
                    onValueChangeFinished()
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(1f - value)
                .align(Alignment.CenterEnd)
                .background(Color.Black.copy(alpha = 0.35f)),
        )
        Row(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(TileIcons[icon], contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            Text(label, color = Color.White, fontSize = 12.sp)
        }
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
        Icon(TileIcons["clock"], null, tint = tokens.fgDim, modifier = Modifier.size(22.dp))
        Text(
            "screen timeout",
            color = tokens.fgDim,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        Text(screenTimeoutLabel(currentMs), color = tokens.fg, fontSize = 14.sp)
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
