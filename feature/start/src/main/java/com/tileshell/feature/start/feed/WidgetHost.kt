package com.tileshell.feature.start.feed

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.RemoteViews

/**
 * An [AppWidgetHostView] that rounds its own corners to [cornerRadiusPx]
 * (user-reported: "borders are square [while] as glance card borders are
 * curved" — real launchers, e.g. Samsung's One UI, do round third-party
 * widgets, so it's clearly achievable). A first attempt set
 * `clipToOutline`/`outlineProvider` once, from the Compose call site right
 * after [AppWidgetHost.createView] — confirmed via logging to run with the
 * right size/radius/hardware-acceleration, but visibly had no effect.
 * Following the same pattern real launchers use (AOSP Launcher3's
 * `LauncherAppWidgetHostView`/`RoundedCornerEnforcement` re-applies its clip
 * inside `updateAppWidget`, not just once at construction): this re-asserts
 * `clipToOutline`/`outlineProvider` and forces `invalidateOutline()` every
 * time the widget's real content is (re)applied — [updateAppWidget] is
 * called far more often than construction (initial bind, every periodic
 * refresh from our own workers), and outline clipping not surviving one of
 * those content swaps would look exactly like "never clips" from the
 * outside. Paired with each widget's own `widget_rounded_background.xml`
 * background (its own Outline source) for the actual visible rounding.
 *
 * Used to also detect a long-press here to enter edit mode — removed
 * (user-reported: still entering edit mode while scrolling, on both cards and
 * hosted widgets, even after several rounds of tightening the cancel-on-
 * movement threshold; edit mode is still reachable via the feed's own
 * explicit "edit" header action, so nothing is lost by dropping the gesture).
 */
class FeedWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var cornerRadiusPx: Float = 0f
        set(value) {
            field = value
            applyRoundedCorners()
        }

    private val roundedOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
        }
    }

    private fun applyRoundedCorners() {
        clipToOutline = cornerRadiusPx > 0f
        outlineProvider = roundedOutlineProvider
        invalidateOutline()
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        // The freshly-applied content is a new child view — re-assert the clip
        // (and force it to be re-queried) in case that swap dropped it.
        applyRoundedCorners()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateOutline()
    }
}

/** [AppWidgetHost] that hosts [FeedWidgetHostView]s so hosted widgets round their own corners. */
class FeedAppWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView = FeedWidgetHostView(context)
}
