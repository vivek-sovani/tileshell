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
 * built-in "custom tap action" hook (only lifecycle callbacks). The tap is a
 * [togglePendingIntent] aimed at [FlashlightWidgetActionReceiver] — a separate,
 * non-exported receiver. It used to be handled by an `onReceive` override on
 * this provider, but a provider has to be exported, which let any installed app
 * broadcast the toggle action and switch the user's torch; see
 * [FlashlightWidgetActionReceiver] for the full reasoning.
 *
 * There is only one physical torch, so the toggle is global: every placed
 * instance reflects the same [WidgetFlashlightState], the same way the
 * in-app tile and every other flashlight tile/gadget already share one torch.
 */
class FlashlightAppWidgetProvider : AppWidgetProvider() {

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
        fun togglePendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            // Private receiver, not this exported provider — see
            // FlashlightWidgetActionReceiver for why.
            val intent = Intent(context, FlashlightWidgetActionReceiver::class.java)
                .setAction(FlashlightWidgetActionReceiver.ACTION_TOGGLE_FLASHLIGHT)
            return PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
