package com.tileshell.feature.livetiles.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock

/**
 * Tapping the widget's body should open its "respective app" — the same
 * expectation a real launcher's own gadget card sets, and the reason the
 * earlier "no tap action at all" call (S32 follow-up) was wrong: that
 * decision was really about not launching *TileShell's own* MainActivity
 * (jarring on a different launcher), not about having no tap target at all.
 * The gear icon's [reconfigurePendingIntent] is a separate, smaller tap
 * target layered on top — RemoteViews touch dispatch gives a child view's own
 * click priority over its parent's, so the two never conflict.
 *
 * No `resolveActivity` pre-check here: that call is filtered by *this app's*
 * manifest `<queries>` visibility, which has nothing to do with whether the
 * *system* can actually resolve the intent when the `PendingIntent` fires
 * later from the host launcher's process — pre-checking would just risk
 * silently dropping a click that would have worked. Worst case if nothing
 * resolves: the OS shows its own "no app found" message, same as any other
 * broken deep link.
 */
private fun activityPendingIntent(context: Context, appWidgetId: Int, intent: Intent): PendingIntent =
    PendingIntent.getActivity(
        context,
        appWidgetId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

private fun webSearchIntent(query: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))

/** Same fallback the in-app weather tile already uses for a blank-package tap. */
fun weatherAppPendingIntent(context: Context, appWidgetId: Int): PendingIntent =
    activityPendingIntent(context, appWidgetId, webSearchIntent("weather"))

/** Standard public intent (API 19+) that opens whatever the device's default clock app is. */
fun alarmAppPendingIntent(context: Context, appWidgetId: Int): PendingIntent =
    activityPendingIntent(context, appWidgetId, Intent(AlarmClock.ACTION_SHOW_ALARMS))

/** Opens the system's own battery-usage screen — there's no single "battery app" to open instead. */
fun batteryAppPendingIntent(context: Context, appWidgetId: Int): PendingIntent =
    activityPendingIntent(context, appWidgetId, Intent(Intent.ACTION_POWER_USAGE_SUMMARY))

/**
 * No OS-standard "moon phase app" exists — same web-search fallback pattern
 * as weather, since both are informational gadgets with no dedicated app.
 */
fun moonPhaseAppPendingIntent(context: Context, appWidgetId: Int): PendingIntent =
    activityPendingIntent(context, appWidgetId, webSearchIntent("moon phase today"))

/** Same web-search fallback as weather/moon phase — no OS-standard app for a specific calendar system. */
fun calendarSystemAppPendingIntent(context: Context, appWidgetId: Int, systemDisplayName: String): PendingIntent =
    activityPendingIntent(context, appWidgetId, webSearchIntent("$systemDisplayName calendar today"))

/** Same web-search fallback as weather/moon phase — no OS-standard "stock app" to deep-link into. */
fun stockAppPendingIntent(context: Context, appWidgetId: Int, displayName: String): PendingIntent =
    activityPendingIntent(context, appWidgetId, webSearchIntent("$displayName stock price"))

/** Same web-search fallback as weather/moon phase — no OS-standard "commodity app" to deep-link into. */
fun commodityAppPendingIntent(context: Context, appWidgetId: Int, displayName: String): PendingIntent =
    activityPendingIntent(context, appWidgetId, webSearchIntent("$displayName price today"))

/**
 * Opens the match's own ESPN web page when [webUrl] resolved on the last
 * refresh (rebuilt fresh every push, unlike the other widgets' fixed intents,
 * since which match — and its URL — changes over time) — the same "see the
 * real page" tap the in-app tile's own [com.tileshell.feature.livetiles
 * .SportsLinks] offers. Falls back to a web search when there's no match yet
 * (a team with nothing scheduled) or the summary call failed.
 */
fun sportsAppPendingIntent(context: Context, appWidgetId: Int, webUrl: String?, teamLabel: String): PendingIntent {
    val intent = if (webUrl != null) Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)) else webSearchIntent("$teamLabel score")
    return activityPendingIntent(context, appWidgetId, intent)
}
