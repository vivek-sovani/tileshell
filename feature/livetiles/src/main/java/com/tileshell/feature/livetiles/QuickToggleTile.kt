package com.tileshell.feature.livetiles

import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileIcons

/**
 * A quick panel toggle brought onto Start as a tile (pinned from the panel's edit mode, like Nothing OS's
 * toggles on its home screen). The tile's `activityName` is `qt:<id>`; the whole tile is the control, and a tap
 * does what the same tile does in the panel. The flashlight is its own older tile ([FlashlightTileFace]).
 */
object QuickToggleTile {
    /** The tile's icon key (and default-layout app id). */
    const val ICON_KEY = "qtoggle"
    private const val PREFIX = "qt:"

    data class Def(val id: String, val icon: String, val label: String)

    /** The panel toggles that can be pinned (the flashlight is separate). Ids match the panel's own tile ids. */
    val DEFS: List<Def> = listOf(
        Def("wifi", "wifi", "wifi"),
        Def("bluetooth", "bluetooth", "bluetooth"),
        Def("location", "maps", "location"),
        Def("airplane", "airplane", "airplane"),
        Def("rotation_lock", "rotate", "rotation lock"),
        Def("dnd", "dnd", "dnd"),
        Def("screen_timeout", "clock", "screen timeout"),
    )

    fun encode(id: String): String = PREFIX + id

    /** The toggle id of a tile's `activityName`, or null. */
    fun decode(activityName: String?): String? =
        activityName?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.takeIf { id -> DEFS.any { it.id == id } }

    fun defFor(id: String?): Def? = DEFS.firstOrNull { it.id == id }
}

/** What a pinned toggle shows and does right now. [on] is null for a value tile (screen timeout). */
private class QuickToggleState(val on: Boolean?, val value: String, val onClick: () -> Unit)

@Composable
private fun rememberQuickToggleState(id: String): QuickToggleState {
    val context = LocalContext.current
    fun onOff(on: Boolean) = if (on) "on" else "off"
    return when (id) {
        "wifi" -> {
            val on = rememberWifiEnabled()
            QuickToggleState(on, onOff(on)) { openWifiSettings(context) }
        }
        "bluetooth" -> {
            val on = rememberBluetoothOn()
            QuickToggleState(on, onOff(on)) { deepLink(context, Settings.ACTION_BLUETOOTH_SETTINGS) }
        }
        "location" -> {
            val on = rememberLocationEnabled()
            QuickToggleState(on, onOff(on)) { deepLink(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS) }
        }
        "airplane" -> {
            val on = rememberAirplaneModeOn()
            QuickToggleState(on, onOff(on)) { deepLink(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS) }
        }
        "rotation_lock" -> {
            val granted = rememberWriteSettingsGranted()
            val on = rememberRotationLockOn()
            QuickToggleState(on, onOff(on)) {
                // A real toggle once WRITE_SETTINGS is granted; until then the tap opens the grant screen.
                if (granted) setRotationLock(context, !on) else openWriteSettingsAccess(context)
            }
        }
        "dnd" -> {
            val granted = rememberDndAccessGranted()
            val on = rememberDndOn()
            QuickToggleState(on, onOff(on)) { if (granted) toggleDnd(context, !on) else openDndSettings(context) }
        }
        else -> {
            val granted = rememberWriteSettingsGranted()
            val (ms, set) = rememberScreenTimeoutMs()
            QuickToggleState(null, screenTimeoutLabel(ms)) {
                if (granted) set(nextScreenTimeoutPreset(ms)) else openWriteSettingsAccess(context)
            }
        }
    }
}

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

/** A pinned quick toggle at MEDIUM and larger: its glyph, its state and its name, like [FlashlightTileFace]. */
@Composable
fun QuickToggleTileFace(id: String?, size: TileSize, interactive: Boolean, modifier: Modifier = Modifier) {
    val def = QuickToggleTile.defFor(id) ?: return
    val state = rememberQuickToggleState(def.id)
    val narrow = size.narrowLive
    val lit = state.on != false
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(enabled = interactive, onClick = state.onClick)
            .padding(if (narrow) 4.dp else 11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Icon(
            imageVector = TileIcons[def.icon],
            contentDescription = null,
            tint = FaceText.copy(alpha = if (lit) 1f else 0.6f),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = state.value,
            color = FaceText,
            fontSize = if (narrow) 20.sp else 26.sp,
            letterSpacing = (-0.5).sp,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        Text(
            text = def.label,
            color = FaceText.copy(alpha = 0.82f),
            fontSize = if (narrow) 11.sp else 13.sp,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}

/** The compact 1x1 form: just the glyph, dimmed while off. */
@Composable
fun QuickToggleSmallFace(id: String?, interactive: Boolean, modifier: Modifier = Modifier) {
    val def = QuickToggleTile.defFor(id) ?: return
    val state = rememberQuickToggleState(def.id)
    Box(
        modifier = modifier.fillMaxSize().clickable(enabled = interactive, onClick = state.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TileIcons[def.icon],
            contentDescription = null,
            tint = FaceText.copy(alpha = if (state.on != false) 1f else 0.6f),
        )
    }
}
