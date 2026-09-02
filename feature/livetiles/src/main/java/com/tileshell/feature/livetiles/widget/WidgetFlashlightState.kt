package com.tileshell.feature.livetiles.widget

import android.content.Context

/**
 * Last-known torch on/off state, shared by every placed flashlight widget
 * (there's only one physical flash regardless of how many widgets exist).
 * [android.hardware.camera2.CameraManager] has no synchronous "is the torch
 * on right now" query — only the async `TorchCallback` — so a widget (which
 * can't keep a live callback registered the way the in-app tile's
 * `DisposableEffect` does) has to track its own belief about the state
 * instead. This can drift from hardware truth if the torch is toggled by
 * something else entirely (Quick Settings, another app) while TileShell
 * itself is never opened — [rememberTorchOn]'s own `TorchCallback` corrects
 * it the moment the app *is* opened with a flashlight tile/card on screen
 * (same cascade pattern as weather/battery), same as any widget relying on
 * a value it can't independently verify.
 */
object WidgetFlashlightState {
    private const val PREFS = "tileshell_widget_flashlight"
    private const val KEY_ON = "on"

    fun isOn(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)

    fun setOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
