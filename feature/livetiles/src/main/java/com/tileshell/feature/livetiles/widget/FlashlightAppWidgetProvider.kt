package com.tileshell.feature.livetiles.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Bundle

/**
 * Home-screen flashlight widget. Unlike every other widget in this package,
 * tapping it doesn't open an app or a config screen — it toggles the torch
 * directly, mirroring [com.tileshell.feature.livetiles.FlashlightTileFace]'s
 * "the whole tile IS the control" behaviour. [AppWidgetProvider] has no
 * built-in "custom tap action" hook (only lifecycle callbacks), so this
 * overrides [onReceive] to intercept [ACTION_TOGGLE] itself — set by
 * [togglePendingIntent] as the widget's own click target — before falling
 * through to `super.onReceive` for the standard AppWidget lifecycle actions.
 *
 * There is only one physical torch, so the toggle is global: every placed
 * instance reflects the same [WidgetFlashlightState], the same way the
 * in-app tile and every other flashlight tile/gadget already share one torch.
 */
class FlashlightAppWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            toggle(context)
            return
        }
        super.onReceive(context, intent)
    }

    private fun toggle(context: Context) {
        val next = !WidgetFlashlightState.isOn(context)
        runCatching {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return@runCatching
            cameraManager.setTorchMode(cameraId, next)
        }
        WidgetFlashlightState.setOn(context, next)
        FlashlightWidgetRefreshWorker.refreshNow(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        FlashlightWidgetRefreshWorker.refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        FlashlightWidgetRefreshWorker.refreshNow(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetColorStore.clear(context, it) }
    }

    companion object {
        private const val ACTION_TOGGLE = "com.tileshell.feature.livetiles.widget.ACTION_TOGGLE_FLASHLIGHT"

        fun togglePendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, FlashlightAppWidgetProvider::class.java).setAction(ACTION_TOGGLE)
            return PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
