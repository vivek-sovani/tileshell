package com.tileshell.feature.start.feed

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent

/**
 * An [AppWidgetHostView] that reports a long-press to [onLongPress] without
 * swallowing normal touches — the hosted widget keeps working (taps, list
 * scrolling). A plain `setOnLongClickListener` is unreliable here because the
 * widget's own child views consume the gesture, so we feed every dispatched event
 * to a [GestureDetector] from `onInterceptTouchEvent` (which always sees them) and
 * return false so children still handle them.
 */
class FeedWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null

    // Set when the long-press fires; from then on we intercept the rest of the
    // gesture so the widget's child views get a CANCEL instead of a click — i.e.
    // a long-press enters edit mode and does NOT also launch the widget's action.
    private var longPressed = false

    private val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                longPressed = true
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onLongPress?.invoke()
            }
        },
    )

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) longPressed = false
        detector.onTouchEvent(ev)
        // Once the long-press has fired, take over the gesture (children receive
        // CANCEL, so no tap-through launch); otherwise let children handle touches.
        return longPressed
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        if (longPressed) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                longPressed = false
            }
            return true
        }
        return super.onTouchEvent(ev)
    }
}

/** [AppWidgetHost] that hosts [FeedWidgetHostView]s so long-press edit works. */
class FeedAppWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView = FeedWidgetHostView(context)
}
