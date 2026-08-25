package com.tileshell.feature.start

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.Glass
import com.tileshell.core.design.isLightBackground
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.WallpaperGradient
import com.tileshell.core.design.colorTokens
import com.tileshell.core.design.wallpaperBackground
import com.tileshell.feature.livetiles.Connectivity
import com.tileshell.feature.livetiles.nextScreenTimeoutPreset
import com.tileshell.feature.livetiles.openWriteSettingsAccess
import com.tileshell.feature.livetiles.rememberAirplaneModeOn
import com.tileshell.feature.livetiles.rememberBatterySaverOn
import com.tileshell.feature.livetiles.rememberBluetoothOn
import com.tileshell.feature.livetiles.rememberDeviceStatus
import com.tileshell.feature.livetiles.rememberDndAccessGranted
import com.tileshell.feature.livetiles.rememberDndOn
import com.tileshell.feature.livetiles.rememberLocationEnabled
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
import com.tileshell.feature.start.feed.feedClock12
import com.tileshell.feature.start.feed.quickPanelHeaderDate
import com.tileshell.feature.start.feed.rememberFeedPalette
import java.util.Calendar
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Quick panel's square-tile grid is this many tiles wide. Was 5 (matching the
 * real WP Action Center photo exactly) — dropped to 4 per explicit on-device
 * feedback: fewer, bigger tiles with more breathing room between them read
 * better than a tighter 5-across grid.
 */
private const val QUICK_PANEL_COLUMNS = 4

/**
 * Quick panel: a two-finger swipe-up on Start opens this. The gesture itself is
 * unchanged (still swipe-**up**, so it can never collide with quick search's
 * two-finger swipe-**down**), but the panel now docks to and slides down from
 * the **top** edge — matching the real Android quick settings panel — rather
 * than sliding up from the bottom the way every other sheet in this app does.
 * See docs/QUICK-PANEL-SPEC.md for the full design rationale and the
 * no-new-Play-Console-permission scoping.
 *
 * Styled as a miniature Start screen rather than a generic Android settings
 * sheet: every control — toggles, brightness, volume, screen timeout, and the
 * settings/lock shortcuts — is a **perfect square** tile in one dense grid,
 * matching the real Windows Phone Action Center rather than the wide chip/
 * slider rows this panel originally shipped with. Binary toggles (wifi,
 * bluetooth, location, airplane, flashlight, rotation lock, dnd — grouped
 * and ordered per [quickPanelTiles]) fill with the personalization
 * accent when on, a neutral dark tile when off — the same on/off contract
 * every Start tile already uses. Brightness and volume
 * are **not** drag sliders (a real WP tile has no slider at all): tapping
 * steps through fixed levels (0/10/20/40/60/80/100%), with the current level
 * shown as the tile's own bold label, exactly like the real device's
 * "25%"-style brightness tile.
 */
@Composable
fun QuickPanelOverlay(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    followSystemTheme: Boolean,
    onDismiss: () -> Unit,
    onOpenPersonalize: () -> Unit,
    onLockScreen: () -> Unit,
    onThemeChange: (Boolean) -> Unit,
    onFollowSystemThemeChange: (Boolean) -> Unit,
    /** Start's own resolved wallpaper gradient — synthesized into the panel's own backdrop, same as the glance page. */
    wallpaper: WallpaperGradient,
    /** Start's custom photo wallpaper, if any (palette-extracted, never drawn directly — see [rememberFeedPalette]). */
    customWallpaperPhoto: ImageBitmap? = null,
    /** True when Start has no wallpaper at all — the panel then stays a flat surface, matching the glance page's own fallback. */
    noWallpaper: Boolean = false,
    /** The glance page's own "no background" opt-out (personalize · feed & glance) — the panel honours the same choice rather than having a separate toggle. */
    feedNoBackground: Boolean = false,
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
    val globalAccent = TileAccents.forId(accentId)
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // Same synthesized-palette backdrop as the glance page — a colour gradient
    // derived from Start's wallpaper (never the raw photo), per explicit
    // request to give the panel "a background just like glance", including
    // honouring glance's own "no background" opt-out rather than adding a
    // second, separate toggle for the panel. The panel's own accent (tile
    // fills, slider colours, header status tints) switches to match it too,
    // exactly mirroring how the glance page's own cards use `feedAccent`
    // instead of the plain global accent once a background is showing —
    // falls back to the flat surface + plain accent when Start has no
    // wallpaper at all, or the user opted the flat look in.
    val flatBackground = noWallpaper || feedNoBackground
    val (panelGradient, accent) = if (flatBackground) {
        wallpaper to globalAccent
    } else {
        rememberFeedPalette(customWallpaperPhoto, wallpaper, globalAccent)
    }
    val panelBackgroundIsLight = rememberChosenWallpaperIsLight(
        customPhoto = null,
        noWallpaper = flatBackground,
        wallpaper = panelGradient,
        dark = dark,
        screenBg = tokens.bg,
    )
    val panelFg = Glass.faceTextColor(panelBackgroundIsLight)
    val panelFgDim = panelFg.copy(alpha = 0.62f)

    BackHandler(enabled = visible) { onDismiss() }

    val wifiOn = rememberWifiEnabled()
    val bluetoothOn = rememberBluetoothOn()
    val airplaneOn = rememberAirplaneModeOn()
    val locationOn = rememberLocationEnabled()
    // Computed for parity with other read-only toggles, but battery saver has no
    // chip yet (see quickPanelTiles) — kept out of scope for this redesign.
    rememberBatterySaverOn()
    val (torchOn, toggleTorch) = rememberTorchOn()
    val dndGranted = rememberDndAccessGranted()
    val dndOn = rememberDndOn()
    val writeSettingsGranted = rememberWriteSettingsGranted()
    val rotationLockOn = rememberRotationLockOn()
    val (brightness, setBrightness) = rememberScreenBrightness()
    val (screenTimeoutMs, setScreenTimeoutMs) = rememberScreenTimeoutMs()
    val (mediaVolume, setMediaVolume) = rememberStreamVolume(AudioManager.STREAM_MUSIC)
    val (ringVolume, setRingVolume) = rememberStreamVolume(AudioManager.STREAM_RING)
    val androidSettingsIcon = rememberAndroidSettingsIcon()

    SheetStage(rightHalf = rightHalf, dockTop = true, modifier = modifier) {
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

        val panelShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .graphicsLayer { translationY = -size.height * (1f - progress) }
                .clip(panelShape)
                .then(
                    if (flatBackground) Modifier.background(tokens.sheet)
                    else Modifier.wallpaperBackground(panelGradient, dark),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .statusBarsPadding()
                .padding(top = 4.dp, bottom = 10.dp),
        ) {
            QuickPanelHeader(
                tokens = tokens,
                fg = panelFg,
                fgDim = panelFgDim,
                accent = accent,
                visible = visible,
                wifiOn = wifiOn,
                airplaneOn = airplaneOn,
                androidSettingsIcon = androidSettingsIcon,
                onOpenPersonalize = { onDismiss(); onOpenPersonalize() },
                onOpenAndroidSettings = { deepLink(context, Settings.ACTION_SETTINGS) },
                onLockScreen = { onDismiss(); onLockScreen() },
            )

            val tiles = quickPanelTiles(
                context = context,
                wifiOn = wifiOn,
                bluetoothOn = bluetoothOn,
                airplaneOn = airplaneOn,
                locationOn = locationOn,
                torchOn = torchOn,
                toggleTorch = toggleTorch,
                dndGranted = dndGranted,
                dndOn = dndOn,
                rotationLockOn = rotationLockOn,
                writeSettingsGranted = writeSettingsGranted,
                screenTimeoutMs = screenTimeoutMs,
                setScreenTimeoutMs = setScreenTimeoutMs,
                dark = dark,
                followSystemTheme = followSystemTheme,
                onThemeChange = onThemeChange,
                onFollowSystemThemeChange = onFollowSystemThemeChange,
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                tiles.chunked(QUICK_PANEL_COLUMNS).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { tile ->
                            QuickPanelTile(
                                tile,
                                tokens = tokens,
                                accent = accent,
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                            )
                        }
                        repeat(QUICK_PANEL_COLUMNS - row.size) { Box(modifier = Modifier.weight(1f)) }
                    }
                }
            }

            QuickPanelSliders(
                tokens = tokens,
                fg = panelFg,
                fgDim = panelFgDim,
                accent = accent,
                writeSettingsGranted = writeSettingsGranted,
                brightness = brightness,
                setBrightness = setBrightness,
                mediaVolume = mediaVolume,
                setMediaVolume = setMediaVolume,
                ringVolume = ringVolume,
                setRingVolume = setRingVolume,
            )

            // Pull-tab handle sits at the bottom edge of the panel now — the edge
            // closest to open space, where it slides down from the top and this
            // reads as "drag/swipe here to close" (mirrors every other sheet's
            // handle sitting at its own open-space edge, just flipped top<->bottom
            // since this panel docks to the top instead of the bottom). Also
            // directly draggable now, per explicit request: dragging it upward
            // past a small threshold dismisses the panel — the same direction
            // you'd naturally pull it back toward, since the panel itself slides
            // down from above.
            var handleDragAccumPx by remember { mutableStateOf(0f) }
            var handleDismissed by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp, bottom = 4.dp)
                    .size(width = 56.dp, height = 20.dp)
                    .pointerInput(Unit) {
                        val thresholdPx = 24.dp.toPx()
                        detectVerticalDragGestures(
                            onDragStart = {
                                handleDragAccumPx = 0f
                                handleDismissed = false
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                handleDragAccumPx += dragAmount
                                if (!handleDismissed && handleDragAccumPx < -thresholdPx) {
                                    handleDismissed = true
                                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    onDismiss()
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .background(panelFgDim, shape = RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

/**
 * Panel header: live clock + date on the left with a small read-only status
 * row (wifi/bluetooth/cellular/battery) on the right of that same top line —
 * standing in for the real status bar this app can hide — and a second row
 * below it for the personalize / android settings / lock screen icon buttons.
 * Mirrors a real device's quick settings panel header (clock/date + status
 * icons on top, action icons below), per explicit user request. The status
 * icons and the three shortcuts both used to live elsewhere (the status
 * row on the feed page's device-status card, the shortcuts as square tiles
 * in the grid below) — moved up here instead, freeing the grid for genuine
 * device controls and giving the hidden status bar a replacement.
 */
@Composable
private fun QuickPanelHeader(
    tokens: ColorTokens,
    fg: Color,
    fgDim: Color,
    accent: Color,
    visible: Boolean,
    wifiOn: Boolean,
    airplaneOn: Boolean,
    androidSettingsIcon: ImageBitmap?,
    onOpenPersonalize: () -> Unit,
    onOpenAndroidSettings: () -> Unit,
    onLockScreen: () -> Unit,
) {
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        while (true) {
            now = Calendar.getInstance()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    val status = rememberDeviceStatus()
    val bluetoothOn = rememberBluetoothOn()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = feedClock12(now), color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = quickPanelHeaderDate(now),
                    color = fgDim,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 1.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Reuses the same live radio state as the wifi toggle tile below
                // (rememberWifiEnabled) rather than "is wifi the active data
                // transport" — a device can be wifi-on-but-not-the-active-route
                // (e.g. a captive/no-internet network) and this should still
                // read as on, matching the toggle tile it sits above. Plain
                // fg/fgDim (not accent) — matches the cellular icon right next
                // to it, per explicit user request; accent tint was tried first
                // but read as inconsistent with the plain-coloured network icon.
                Icon(
                    TileIcons["wifi"], contentDescription = "wi-fi",
                    tint = if (wifiOn) fg else fgDim,
                    modifier = Modifier.size(16.dp),
                )
                Icon(
                    TileIcons["bluetooth"], contentDescription = "bluetooth",
                    tint = if (bluetoothOn) fg else fgDim,
                    modifier = Modifier.size(16.dp),
                )
                // Cellular signal is meaningless in airplane mode — swap in the
                // airplane glyph instead, matching a real device's status bar.
                if (airplaneOn) {
                    Icon(
                        TileIcons["airplane"], contentDescription = "airplane mode",
                        tint = fg,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Icon(
                        TileIcons["cellular"], contentDescription = "cellular",
                        tint = if (status.connectivity == Connectivity.CELLULAR) fg else fgDim,
                        modifier = Modifier.size(16.dp),
                    )
                }
                BatteryIndicator(percent = status.batteryPercent, fg = fg, fgDim = fgDim)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        ) {
            QuickPanelHeaderIcon(icon = "settings", description = "personalize", fg = fg, onClick = onOpenPersonalize)
            QuickPanelHeaderIcon(
                icon = "settings",
                description = "android settings",
                fg = fg,
                iconBitmap = androidSettingsIcon,
                onClick = onOpenAndroidSettings,
            )
            QuickPanelHeaderIcon(icon = "lock", description = "lock screen", fg = fg, onClick = onLockScreen)
        }
    }
}

/**
 * Battery glyph drawn by hand (not from [TileIcons], which is stroke-only with
 * no fill support) so the level can render as a proportionate fill, colour-
 * coded green/amber/red — per explicit request, richer than a fixed outline
 * glyph + separate percentage. Percentage text still sits alongside it (more
 * informative than the icon alone), just no longer the only way to read the
 * level.
 */
@Composable
private fun BatteryIndicator(percent: Int?, fg: Color, fgDim: Color) {
    val fraction = ((percent ?: 100) / 100f).coerceIn(0f, 1f)
    val fillColor = when {
        percent == null -> fgDim
        percent <= 20 -> Color(0xFFE5484D)
        percent <= 50 -> Color(0xFFE5A02E)
        else -> Color(0xFF2FA84F)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier.size(width = 20.dp, height = 11.dp)) {
            val strokeWidth = 1.2.dp.toPx()
            val nubWidth = 1.6.dp.toPx()
            val bodyWidth = size.width - nubWidth
            val bodyRect = RoundRect(
                left = 0f, top = 0f, right = bodyWidth, bottom = size.height,
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            drawPath(Path().apply { addRoundRect(bodyRect) }, color = fg, style = Stroke(strokeWidth))
            drawRoundRect(
                color = fg,
                topLeft = Offset(bodyWidth, size.height * 0.28f),
                size = Size(nubWidth, size.height * 0.44f),
                cornerRadius = CornerRadius(0.6.dp.toPx()),
            )
            val inset = strokeWidth * 1.6f
            val fillWidth = ((bodyWidth - inset * 2) * fraction).coerceAtLeast(0f)
            if (fillWidth > 0f) {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(inset, inset),
                    size = Size(fillWidth, size.height - inset * 2),
                    cornerRadius = CornerRadius(1.dp.toPx()),
                )
            }
        }
        Text(text = percent?.let { "$it%" } ?: "—", color = fg, fontSize = 12.sp)
    }
}

@Composable
private fun QuickPanelHeaderIcon(
    icon: String,
    description: String,
    fg: Color,
    onClick: () -> Unit,
    iconBitmap: ImageBitmap? = null,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onClick()
            }),
        contentAlignment = Alignment.Center,
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = description,
                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)),
            )
        } else {
            Icon(
                imageVector = TileIcons[icon],
                contentDescription = description,
                tint = fg,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private data class QuickPanelTileSpec(
    val icon: String,
    val label: String,
    /** Binary on/off tint (accent vs neutral). Value tiles (brightness, settings, …) pass false — they're always neutral, matching the real device's non-toggle tiles. */
    val active: Boolean,
    val onClick: () -> Unit,
    /** Overrides [icon] with a real app icon bitmap when set (only "android settings" uses this). */
    val iconBitmap: ImageBitmap? = null,
)

/**
 * Brightness/media-volume/ring-volume as three full-width drag sliders below
 * the toggle-tile grid, per explicit user request (sliders read better than
 * tap-to-step tiles for these three, unlike the earlier square-tile redesign's
 * choice). Brightness only renders once `WRITE_SETTINGS` is granted (the
 * toggle-tile grid already carries an "allow access" tile for that case);
 * media/ring volume need no special permission and always show.
 */
@Composable
private fun QuickPanelSliders(
    tokens: ColorTokens,
    fg: Color,
    fgDim: Color,
    accent: Color,
    writeSettingsGranted: Boolean,
    brightness: Float,
    setBrightness: (Float) -> Unit,
    mediaVolume: Float,
    setMediaVolume: (Float) -> Unit,
    ringVolume: Float,
    setRingVolume: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (writeSettingsGranted) {
            val brightnessFraction = rememberSliderFraction(brightness)
            QuickPanelSliderRow(
                icon = "brightness",
                value = brightnessFraction.value,
                onValueChange = { brightnessFraction.value = it; setBrightness(it) },
                tokens = tokens,
                fg = fg,
                fgDim = fgDim,
                accent = accent,
            )
        }
        val ringFraction = rememberSliderFraction(ringVolume)
        var ringPreMute by remember { mutableStateOf(ringFraction.value.takeIf { it > 0f } ?: 0.5f) }
        QuickPanelSliderRow(
            icon = if (ringFraction.value <= 0f) "bell-mute" else "bell",
            value = ringFraction.value,
            onValueChange = { ringFraction.value = it; setRingVolume(it) },
            onIconClick = {
                if (ringFraction.value > 0f) {
                    ringPreMute = ringFraction.value
                    ringFraction.value = 0f
                    setRingVolume(0f)
                } else {
                    ringFraction.value = ringPreMute
                    setRingVolume(ringPreMute)
                }
            },
            tokens = tokens,
            fg = fg,
            fgDim = fgDim,
            accent = accent,
        )
        val mediaFraction = rememberSliderFraction(mediaVolume)
        var mediaPreMute by remember { mutableStateOf(mediaFraction.value.takeIf { it > 0f } ?: 0.5f) }
        QuickPanelSliderRow(
            icon = if (mediaFraction.value <= 0f) "volume-mute" else "volume",
            value = mediaFraction.value,
            onValueChange = { mediaFraction.value = it; setMediaVolume(it) },
            onIconClick = {
                if (mediaFraction.value > 0f) {
                    mediaPreMute = mediaFraction.value
                    mediaFraction.value = 0f
                    setMediaVolume(0f)
                } else {
                    mediaFraction.value = mediaPreMute
                    setMediaVolume(mediaPreMute)
                }
            },
            tokens = tokens,
            fg = fg,
            fgDim = fgDim,
            accent = accent,
        )
    }
}

/**
 * [onIconClick], when set, makes the leading icon a mute/unmute toggle — tapping
 * it while the level is above zero mutes to 0%, remembering the level to
 * restore on the next tap; tapping while muted restores it. Brightness has no
 * mute concept, so its row passes null and the icon stays a plain glyph.
 */
@Composable
private fun QuickPanelSliderRow(
    icon: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    tokens: ColorTokens,
    fg: Color,
    fgDim: Color,
    accent: Color,
    onIconClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val haptics = LocalHapticFeedback.current
        Icon(
            imageVector = TileIcons[icon],
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(20.dp).let { base ->
                if (onIconClick == null) base else base.clickable {
                    haptics.performHapticFeedback(
                        if (value > 0f) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                    )
                    onIconClick()
                }
            },
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd) },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = tokens.tileLine,
            ),
        )
        Text(
            text = "${(value * 100).roundToInt()}%",
            color = fgDim,
            fontSize = 13.sp,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End,
        )
    }
}

/**
 * Seeds once from [hardwareFraction] and never re-syncs from it afterwards — a
 * fresh readback of a stream's coarse hardware step count (often 15 or 7
 * levels) can round-trip to a value slightly different from what was just
 * set, which would make the slider thumb visibly jitter/snap back mid-drag if
 * it were bound straight to the hardware value.
 */
@Composable
private fun rememberSliderFraction(hardwareFraction: Float): MutableState<Float> =
    remember { mutableStateOf(hardwareFraction) }

/**
 * The real Android Settings app's own launcher icon, resolved at runtime (varies
 * by OEM) rather than the generic gear glyph — mirrors `StartScreen.kt`'s
 * `rememberTileAppIcon` decode-with-fallback shape, kept local since that one is
 * file-private.
 */
@Composable
private fun rememberAndroidSettingsIcon(): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val resolved = context.packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)
                    ?: return@runCatching null
                val info = resolved.activityInfo
                context.packageManager
                    .getActivityIcon(ComponentName(info.packageName, info.name))
                    .toBitmap(width = 96, height = 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }.value
}

/**
 * The full ordered tile list for the panel's grid, grouped by kind rather than
 * strictly mirroring the reference WP photo's order — connectivity toggles
 * first (wifi, bluetooth, location, airplane), then flashlight, then rotation
 * lock and screen timeout (or a single "allow access" fallback tile in their
 * place until `WRITE_SETTINGS` is granted), then dnd, then theme. Brightness
 * and volume are **not** in this grid — they're slider rows below it instead
 * ([QuickPanelSliders]), per explicit user request (a real device's quick
 * settings panel favors sliders for those two over discrete tap-to-step
 * tiles). The personalize/android-settings/lock-screen shortcuts are also
 * not in this grid — they're compact icon buttons in the panel's own header
 * row instead (top-right, alongside the clock/date on the left), matching a
 * real device's quick settings panel header. Grouping this way (rather than
 * the reference photo's literal order) reads more predictably once every
 * real toggle carries live on/off accent state — a user scanning for "is
 * airplane mode on" shouldn't have to skip over an unrelated flashlight tile
 * in between. Location sits third (ahead of airplane) and dnd sits well down
 * the list, both per explicit, iterative user preference over the initial
 * ordering.
 */
private fun quickPanelTiles(
    context: Context,
    wifiOn: Boolean,
    bluetoothOn: Boolean,
    airplaneOn: Boolean,
    locationOn: Boolean,
    torchOn: Boolean,
    toggleTorch: () -> Unit,
    dndGranted: Boolean,
    dndOn: Boolean,
    rotationLockOn: Boolean,
    writeSettingsGranted: Boolean,
    screenTimeoutMs: Long,
    setScreenTimeoutMs: (Long) -> Unit,
    dark: Boolean,
    followSystemTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onFollowSystemThemeChange: (Boolean) -> Unit,
): List<QuickPanelTileSpec> = buildList {
    // Connectivity toggles.
    add(QuickPanelTileSpec(icon = "wifi", label = "wifi", active = wifiOn, onClick = { openWifiSettings(context) }))
    add(
        QuickPanelTileSpec(
            icon = "bluetooth", label = "bluetooth", active = bluetoothOn,
            onClick = { deepLink(context, Settings.ACTION_BLUETOOTH_SETTINGS) },
        ),
    )
    add(
        QuickPanelTileSpec(
            icon = "maps", label = "location", active = locationOn,
            onClick = { deepLink(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
        ),
    )
    add(
        QuickPanelTileSpec(
            icon = "airplane", label = "airplane", active = airplaneOn,
            onClick = { deepLink(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS) },
        ),
    )

    // Device-mode toggles.
    add(QuickPanelTileSpec(icon = "flashlight", label = "flashlight", active = torchOn, onClick = toggleTorch))

    if (!writeSettingsGranted) {
        add(
            QuickPanelTileSpec(
                icon = "settings", label = "allow access", active = false,
                onClick = { openWriteSettingsAccess(context) },
            ),
        )
    }
    add(
        QuickPanelTileSpec(
            icon = "rotate", label = "rotation lock", active = rotationLockOn,
            onClick = {
                // A genuine toggle once WRITE_SETTINGS is granted; until then, tapping
                // deep-links to the grant screen instead of silently no-op'ing.
                if (writeSettingsGranted) setRotationLock(context, !rotationLockOn) else openWriteSettingsAccess(context)
            },
        ),
    )
    if (writeSettingsGranted) {
        add(
            QuickPanelTileSpec(
                icon = "clock", label = screenTimeoutLabel(screenTimeoutMs), active = false,
                onClick = { setScreenTimeoutMs(nextScreenTimeoutPreset(screenTimeoutMs)) },
            ),
        )
    }

    add(
        QuickPanelTileSpec(
            icon = "dnd", label = "dnd", active = dndOn,
            onClick = {
                // Once access is granted this is a genuine toggle; until then, deep-link
                // to the general "Do Not Disturb" settings screen (which also surfaces
                // the access-grant prompt itself) rather than straight to
                // ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS — that screen is an
                // app-by-app access list, not the DND settings a user tapping this tile
                // actually expects to land on.
                if (dndGranted) toggleDnd(context, !dndOn) else openDndSettings(context)
            },
        ),
    )

    // Theme + app shortcuts.
    val themeChoice = themeChoiceFor(dark, followSystemTheme)
    add(
        QuickPanelTileSpec(
            // Accent-filled like a real toggle (not neutral like the brightness/
            // volume value tiles) — it always represents the current selection,
            // the same way Personalize's own theme tiles accent-highlight whichever
            // of dark/light/auto is currently chosen.
            icon = themeChoice.icon, label = themeChoice.label, active = true,
            onClick = {
                when (nextThemeChoice(themeChoice)) {
                    ThemeChoice.DARK -> { onFollowSystemThemeChange(false); onThemeChange(true) }
                    ThemeChoice.LIGHT -> { onFollowSystemThemeChange(false); onThemeChange(false) }
                    ThemeChoice.AUTO -> onFollowSystemThemeChange(true)
                }
            },
        ),
    )
}

/** One tap-to-cycle theme tile (dark → light → auto → dark), instead of three separate ones. */
internal enum class ThemeChoice(val icon: String, val label: String) {
    DARK("moon", "dark"),
    LIGHT("brightness", "light"),
    AUTO("auto", "auto"),
}

internal fun themeChoiceFor(dark: Boolean, followSystemTheme: Boolean): ThemeChoice = when {
    followSystemTheme -> ThemeChoice.AUTO
    dark -> ThemeChoice.DARK
    else -> ThemeChoice.LIGHT
}

internal fun nextThemeChoice(current: ThemeChoice): ThemeChoice = when (current) {
    ThemeChoice.DARK -> ThemeChoice.LIGHT
    ThemeChoice.LIGHT -> ThemeChoice.AUTO
    ThemeChoice.AUTO -> ThemeChoice.DARK
}

/**
 * A small square Start-tile-style control: monoline icon top-center, short
 * state label bottom-center — accent fill for an "on" binary toggle, a
 * neutral dark tile otherwise (value tiles like brightness/settings are
 * always neutral, since they're not on/off states).
 */
@Composable
private fun QuickPanelTile(
    tile: QuickPanelTileSpec,
    tokens: ColorTokens,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val bg = if (tile.active) accent else tokens.chip
    // An active tile's text must adapt to its own accent fill (not just the
    // panel's overall background lightness, which panelFg tracks) — a
    // wallpaper-derived accent can be light even on a dark panel.
    val fg = if (tile.active) Glass.faceTextColor(useDarkText = isLightBackground(accent)) else tokens.fgDim
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                tile.onClick()
            })
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (tile.iconBitmap != null) {
            Image(
                bitmap = tile.iconBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Icon(TileIcons[tile.icon], null, tint = fg, modifier = Modifier.size(18.dp))
        }
        Text(
            tile.label,
            color = fg,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
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
