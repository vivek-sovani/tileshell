package com.tileshell.feature.livetiles.health

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.tileshell.core.data.StepsPrefs
import com.tileshell.feature.livetiles.Units

/**
 * Keeps a walk counting with the screen off: a foreground service of type `health` with an ongoing "walking"
 * notification (time, steps, distance). The sensors themselves are [WalkSession]'s; this only holds the process and
 * keeps the notification current (every few seconds). It stops itself when the walk ends.
 */
class WalkService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!WalkSession.state.value.running) {
                stopSelf()
                return
            }
            runCatching { getSystemService(NotificationManager::class.java).notify(ID, build()) }
            handler.postDelayed(this, 5_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            WalkSession.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "walking", NotificationManager.IMPORTANCE_LOW).apply {
            description = "shown while a walk is being counted"
            setShowBadge(false)
        })
        val ok = runCatching {
            startForeground(ID, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        }.isSuccess
        if (!ok || !WalkSession.state.value.running) {
            if (!ok) WalkSession.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 5_000L)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        // The system ended the service: do not leave a walk running with nothing holding the process.
        if (WalkSession.state.value.running) WalkSession.stop(this)
        super.onDestroy()
    }

    private fun build(): Notification {
        val s = WalkSession.state.value
        val km = distanceKm(s.steps, HealthPrefs.current(this).heightCm)
        val text = "${clockDuration(WalkSession.elapsedMs())} · ${s.steps} steps · ${Units.distance(km)}"
        val open = packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val openPi = open?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, WalkService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_menu_directions)
            .setContentTitle(if (s.paused) "walk paused" else "walking")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPi)
            .addAction(Notification.Action.Builder(null, "stop", stopPi).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "walk"
        private const val ID = 7421
        const val ACTION_STOP = "com.tileshell.walk.STOP"
    }
}
