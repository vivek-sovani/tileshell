package com.tileshell

import android.app.Activity
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The transparency of a hub task's window (`Theme.TileShell.HubTask` starts transparent so the real Start shows
 * through while the hub grows and shrinks). A transparent window cannot be snapshotted for Android's recents, so
 * once a hub is fully open the window is turned opaque ([makeOpaqueSoon]) and, just before it closes, transparent
 * again ([makeTranslucent]). `Activity.setTranslucent` needs Android 13; before that the window just stays transparent.
 */
internal class HubWindow(private val activity: Activity, private val scope: CoroutineScope) {
    private var opaque = false
    private var job: Job? = null

    /** After the open animation, if [stillOpen], turns the window opaque. A later call replaces an earlier one. */
    fun makeOpaqueSoon(stillOpen: () -> Boolean) {
        job?.cancel()
        job = scope.launch {
            delay(OPAQUE_AFTER_MS)
            if (stillOpen()) setOpaque(true)
        }
    }

    /** Opaque at once (the task is leaving with a hub open), so recents never gets a picture of a transparent window. */
    fun makeOpaqueNow() {
        job?.cancel()
        setOpaque(true)
    }

    /** Turns the window transparent and gives the conversion a moment, so the hub's shrink shows the Start behind. */
    suspend fun makeTranslucent() {
        job?.cancel()
        if (opaque) {
            setOpaque(false)
            delay(SETTLE_MS)
        }
    }

    private fun setOpaque(value: Boolean) {
        if (Build.VERSION.SDK_INT < 33 || opaque == value) return
        runCatching { activity.setTranslucent(!value) }.onSuccess { opaque = value }
    }

    private companion object {
        /** A hub's open animation is 300 ms. */
        const val OPAQUE_AFTER_MS = 450L
        const val SETTLE_MS = 120L
    }
}
