package com.tileshell.feature.start.feed

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.GestureDetector
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

    private val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                onLongPress?.invoke()
            }
        },
    )

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
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
