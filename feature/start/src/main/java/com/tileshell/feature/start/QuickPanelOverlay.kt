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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens
import com.tileshell.feature.livetiles.BRIGHTNESS_VOLUME_LEVELS
import com.tileshell.feature.livetiles.nextPercentLevel
import com.tileshell.feature.livetiles.nextScreenTimeoutPreset
import com.tileshell.feature.livetiles.openWriteSettingsAccess
import com.tileshell.feature.livetiles.rememberAirplaneModeOn
import com.tileshell.feature.livetiles.rememberBatterySaverOn
import com.tileshell.feature.livetiles.rememberBluetoothOn
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
import java.util.Calendar
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Quick panel's square-tile grid is this many tiles wide (matches the real WP Action Center photo). */
private const val QUICK_PANEL_COLUMNS = 5

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
    // Cycling decisions use our own remembered level, not a fresh readback of the
    // hardware fraction — media/ring streams have a tiny native range (often 15
    // or 7 steps), so round-tripping a percent through it and reading back
    // rounds to a different percent than intended, making the "next level"
    // check see itself as still below the just-set target and re-target the
    // same level forever (reported: "volume settings tap not working"). Seeding
    // once from the real level and then only ever advancing locally keeps every
    // tap deterministic regardless of how coarse the underlying hardware step is.
    val brightnessLevel = rememberSteppedPercent(brightness)
    val mediaLevel = rememberSteppedPercent(mediaVolume)
    val ringLevel = rememberSteppedPercent(ringVolume)
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .graphicsLayer { translationY = -size.height * (1f - progress) }
                .background(tokens.sheet, shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
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
                visible = visible,
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
                brightnessLevel = brightnessLevel,
                setBrightness = setBrightness,
                mediaLevel = mediaLevel,
                setMediaVolume = setMediaVolume,
                ringLevel = ringLevel,
                setRingVolume = setRingVolume,
                screenTimeoutMs = screenTimeoutMs,
                setScreenTimeoutMs = setScreenTimeoutMs,
                dark = dark,
                followSystemTheme = followSystemTheme,
                onThemeChange = onThemeChange,
                onFollowSystemThemeChange = onFollowSystemThemeChange,
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tiles.chunked(QUICK_PANEL_COLUMNS).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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

            // Pull-tab handle sits at the bottom edge of the panel now — the edge
            // closest to open space, where it slides down from the top and this
            // reads as "drag/swipe here to close" (mirrors every other sheet's
            // handle sitting at its own open-space edge, just flipped top<->bottom
            // since this panel docks to the top instead of the bottom).
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp, bottom = 4.dp)
                    .width(32.dp)
                    .height(3.dp)
                    .background(tokens.fgDim, shape = RoundedCornerShape(2.dp)),
            )
        }
    }
}

/**
 * Panel header: live clock + date on the left, and compact circular icon
 * buttons for personalize / android settings / lock screen on the right —
 * mirroring a real device's quick settings panel header (clock/date left,
 * edit/power/settings icons right), per explicit user request. These three
 * shortcuts used to be square tiles in the grid below; they moved up here
 * instead so the grid holds only genuine device controls.
 */
@Composable
private fun QuickPanelHeader(
    tokens: ColorTokens,
    visible: Boolean,
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

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = feedClock12(now), color = tokens.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = quickPanelHeaderDate(now),
                color = tokens.fgDim,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 1.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            QuickPanelHeaderIcon(icon = "settings", description = "personalize", tokens = tokens, onClick = onOpenPersonalize)
            QuickPanelHeaderIcon(
                icon = "settings",
                description = "android settings",
                tokens = tokens,
                iconBitmap = androidSettingsIcon,
                onClick = onOpenAndroidSettings,
            )
            QuickPanelHeaderIcon(icon = "lock", description = "lock screen", tokens = tokens, onClick = onLockScreen)
        }
    }
}

@Composable
private fun QuickPanelHeaderIcon(
    icon: String,
    description: String,
    tokens: ColorTokens,
    onClick: () -> Unit,
    iconBitmap: ImageBitmap? = null,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = onClick),
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
                tint = tokens.fg,
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
 * Seeds once from [hardwareFraction] (as the nearest [BRIGHTNESS_VOLUME_LEVELS]
 * step) and never re-syncs from it afterwards — see the call site's comment for
 * why a fresh readback breaks tap-to-step cycling on a coarse-grained stream.
 */
@Composable
private fun rememberSteppedPercent(hardwareFraction: Float): MutableState<Int> = remember {
    val initialPercent = (hardwareFraction * 100).roundToInt()
    mutableStateOf(BRIGHTNESS_VOLUME_LEVELS.minByOrNull { kotlin.math.abs(it - initialPercent) } ?: 0)
}

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
 * first (wifi, bluetooth, location, airplane), then flashlight, then the
 * adjustable-level tiles interleaved with rotation lock (brightness, rotation
 * lock, screen timeout, ring volume, media volume — or a single "allow
 * access" fallback tile in place of brightness/timeout until `WRITE_SETTINGS`
 * is granted), then dnd, then theme. The personalize/android-settings/
 * lock-screen shortcuts are **not** in this grid — they're compact icon
 * buttons in the panel's own header row instead (top-right, alongside the
 * clock/date on the left), matching a real device's quick settings panel
 * header. Grouping this way (rather than the reference photo's literal
 * order) reads more predictably once every real toggle carries live on/off
 * accent state — a user scanning for "is airplane mode on" shouldn't have to
 * skip over an unrelated flashlight tile in between. Location sits third
 * (ahead of airplane), dnd sits well down the list, rotation lock sits right
 * after brightness (not screen timeout), and media volume sits last in its
 * row (the extreme right of row two) rather than beside rotation lock — all
 * per explicit, iterative user preference over the initial ordering.
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
    brightnessLevel: MutableState<Int>,
    setBrightness: (Float) -> Unit,
    mediaLevel: MutableState<Int>,
    setMediaVolume: (Float) -> Unit,
    ringLevel: MutableState<Int>,
    setRingVolume: (Float) -> Unit,
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

    // Adjustable-level tiles, with rotation lock between brightness and the volumes, and screen
    // timeout after media volume — both swapped from their initial straightforward grouped order,
    // per explicit user request.
    if (writeSettingsGranted) {
        add(
            QuickPanelTileSpec(
                icon = "brightness", label = "${brightnessLevel.value}%", active = false,
                onClick = {
                    brightnessLevel.value = nextPercentLevel(brightnessLevel.value)
                    setBrightness(brightnessLevel.value / 100f)
                },
            ),
        )
    } else {
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
            icon = if (ringLevel.value <= 0) "bell-mute" else "bell",
            label = "${ringLevel.value}%",
            active = false,
            onClick = {
                ringLevel.value = nextPercentLevel(ringLevel.value)
                setRingVolume(ringLevel.value / 100f)
            },
        ),
    )
    // Media volume sits last (extreme right of row two), per explicit user request.
    add(
        QuickPanelTileSpec(
            icon = if (mediaLevel.value <= 0) "volume-mute" else "volume",
            label = "${mediaLevel.value}%",
            active = false,
            onClick = {
                mediaLevel.value = nextPercentLevel(mediaLevel.value)
                setMediaVolume(mediaLevel.value / 100f)
            },
        ),
    )

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
    val fg = if (tile.active) Color.White else tokens.fgDim
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = tile.onClick)
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
