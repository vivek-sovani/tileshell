package com.tileshell.core.data.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Private receiver for running timers: a step's alarm going off and the
 * notification's pause / resume / skip / stop buttons. Only reached through
 * TileShell's own immutable PendingIntents (exported=false). The work is a few
 * prefs reads and writes and a vibration, so it runs inside the broadcast.
 */
class ClockSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ClockSessions.EXTRA_SESSION_ID) ?: return
        when (intent.action) {
            ClockSessions.ACTION_FIRE -> ClockSessions.onAlarm(context, id)
            ClockSessions.ACTION_PAUSE -> ClockSessions.pause(context, id)
            ClockSessions.ACTION_RESUME -> ClockSessions.resume(context, id)
            ClockSessions.ACTION_SKIP -> ClockSessions.skip(context, id)
            ClockSessions.ACTION_STOP -> ClockSessions.stop(context, id)
        }
    }
}
