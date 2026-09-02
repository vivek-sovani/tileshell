package com.tileshell.feature.livetiles.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent

/**
 * A tap target that reopens [WidgetConfigureActivity] for an *already-placed*
 * widget — the OS only auto-launches `android:configure` once, at add time
 * (user-reported gap: there was no way back into the colour picker
 * afterwards). [WidgetConfigureActivity] doesn't care who started it, only
 * that [AppWidgetManager.EXTRA_APPWIDGET_ID] is in the launch intent, so a
 * plain manual [PendingIntent] works the same as the OS's own configure
 * launch. `requestCode = appWidgetId` keeps each widget's PendingIntent
 * distinct — [PendingIntent] equality ignores extras, only action/data/
 * component/categories and request code, so without a unique request code
 * every widget of the same kind would collide onto one shared PendingIntent.
 */
fun reconfigurePendingIntent(context: Context, appWidgetId: Int): PendingIntent {
    val intent = Intent(context, WidgetConfigureActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    return PendingIntent.getActivity(
        context,
        appWidgetId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
