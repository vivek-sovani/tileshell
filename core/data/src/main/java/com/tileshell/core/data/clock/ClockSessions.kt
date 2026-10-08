package com.tileshell.core.data.clock

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.tileshell.core.data.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The running timers and timer sets. No service and no new permission:
 *
 * - each step's end is one alarm. With the user's "Alarms & reminders" access
 *   (the same one task reminders ask for) it is an exact one
 *   ([AlarmManager.setExactAndAllowWhileIdle]); without it, an inexact one
 *   ([AlarmManager.setAndAllowWhileIdle]) that can come late with the screen off
 *   in deep sleep. It is deliberately **not** an alarm-clock alarm
 *   (`setAlarmClock`): that registers as the phone's "next alarm", so a 5 minute
 *   timer showed up as "next alarm 5:38 pm" on the alarm tile and in the status
 *   bar (user-reported). While the hub is open (or a tile ticking) [tick] moves a
 *   step on exactly on time either way, and a running set keeps the screen on, so
 *   deep-sleep throttling doesn't apply to it. Only the *next* step's alarm exists
 *   at a time; when it fires, [onAlarm] buzzes, moves on and sets the following one.
 * - the countdown on the notification is the system's own chronometer, so
 *   nothing runs to update it.
 * - the buzz is a vibration marked as an alarm's ([ClockBuzz]); with "sound as well" the chime
 *   is played directly on the alarm stream ([ClockChime]), not by a notification channel.
 *
 * Running sessions are kept in the prefs file so a late alarm or a killed
 * process loses nothing. An app update or force-stop clears the alarms, and any
 * session whose steps ended meanwhile finishes the next time it is read — and its
 * notification is cleared with it ([dropStaleNotifications]).
 */
object ClockSessions {
    const val ACTION_FIRE = "com.tileshell.action.CLOCK_FIRE"
    const val ACTION_PAUSE = "com.tileshell.action.CLOCK_PAUSE"
    const val ACTION_RESUME = "com.tileshell.action.CLOCK_RESUME"
    const val ACTION_SKIP = "com.tileshell.action.CLOCK_SKIP"
    const val ACTION_STOP = "com.tileshell.action.CLOCK_STOP"
    const val EXTRA_SESSION_ID = "sessionId"
    private val ACTIONS = listOf(ACTION_FIRE, ACTION_PAUSE, ACTION_RESUME, ACTION_SKIP, ACTION_STOP)

    /** Extra on TileShell's launch intent that opens a hub by name (the widgets use the same one). */
    const val EXTRA_OPEN_HUB = "com.tileshell.extra.OPEN_HUB"

    private const val PREFS = "tileshell.prefs"
    private const val KEY = "clock_sessions"
    private const val CHANNEL_QUIET = "clock_sessions_quiet"
    private const val CHANNEL_SOUND = "clock_sessions_sound"

    private val state = MutableStateFlow<List<Session>?>(null)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The running sessions (paused ones included), settled to now. */
    @Synchronized
    fun flow(context: Context): StateFlow<List<Session>> {
        if (state.value == null) {
            val loaded = decodeSessions(prefs(context).getString(KEY, null))
            val now = System.currentTimeMillis()
            // A step that ended while the alarms were gone (a reboot) moves on or finishes now.
            val settled = loaded.mapNotNull { it.advance(now).session }
            state.value = settled
            if (settled != loaded) write(context, settled)
            // A session that ended while the app was gone leaves its ongoing notification behind.
            dropStaleNotifications(context.applicationContext, settled)
        }
        @Suppress("UNCHECKED_CAST")
        return state.asStateFlow() as StateFlow<List<Session>>
    }

    /** Starts the saved [set]; null when it has no length. A tile pinned for it finds the run by its id. */
    fun startSet(context: Context, set: TimerSet): Session? =
        start(context, set.name.ifBlank { "timer set" }, flatten(set).map { SessionStep(it.label, it.ms, it.partName) }, setId = set.id)

    /** Starts a timer (`isTimer`) or a set; null when it has no length. */
    @Synchronized
    fun start(context: Context, title: String, steps: List<SessionStep>, isTimer: Boolean = false, setId: String? = null): Session? {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val id = if (isTimer) "timer-$now" else if (setId != null) sessionIdForSet(now, setId) else "set-$now"
        val session = startSession(id, title, steps, System.currentTimeMillis()) ?: return null
        write(app, flow(app).value + session)
        arm(app, session)
        notifyRunning(app, session)
        return session
    }

    @Synchronized
    fun pause(context: Context, id: String) = change(context, id) { it.pause(System.currentTimeMillis()) }

    @Synchronized
    fun resume(context: Context, id: String) = change(context, id) { it.resume(System.currentTimeMillis()) }

    /** Moves to the next step now; on the last one this ends the session. */
    @Synchronized
    fun skip(context: Context, id: String) {
        val app = context.applicationContext
        val session = flow(app).value.firstOrNull { it.id == id } ?: return
        val next = session.skip(System.currentTimeMillis())
        if (next == null) finish(app, session, buzz = false) else change(app, id) { next }
    }

    @Synchronized
    fun stop(context: Context, id: String) {
        val app = context.applicationContext
        flow(app).value.firstOrNull { it.id == id }?.let { remove(app, it) }
    }

    /** A step's alarm went off ([ClockSessionReceiver]). */
    @Synchronized
    fun onAlarm(context: Context, id: String) {
        val app = context.applicationContext
        val session = flow(app).value.firstOrNull { it.id == id } ?: return
        val advance = session.advance(System.currentTimeMillis())
        when {
            advance.stepsEnded == 0 -> arm(app, session) // fired a hair early: set it again
            advance.session == null -> finish(app, session, buzz = true)
            else -> {
                ClockBuzz.buzz(app, ClockStore.buzzStrength(app), finish = false)
                ClockChime.play(app, finish = false)
                change(app, id) { advance.session }
            }
        }
    }

    private fun change(context: Context, id: String, transform: (Session) -> Session) {
        val app = context.applicationContext
        val current = flow(app).value
        val old = current.firstOrNull { it.id == id } ?: return
        val updated = transform(old)
        write(app, current.map { if (it.id == id) updated else it })
        arm(app, updated)
        notifyRunning(app, updated)
    }

    private fun finish(app: Context, session: Session, buzz: Boolean) {
        remove(app, session)
        if (buzz) {
            ClockBuzz.buzz(app, ClockStore.buzzStrength(app), finish = true)
            ClockChime.play(app, finish = true)
        }
        notifyDone(app, session)
    }

    private fun remove(app: Context, session: Session) {
        write(app, flow(app).value.filterNot { it.id == session.id })
        disarm(app, session.id)
        app.getSystemService(NotificationManager::class.java)?.cancel(notificationId(session.id))
    }

    private fun write(app: Context, sessions: List<Session>) {
        prefs(app).edit().putString(KEY, encodeSessions(sessions)).apply()
        state.value = sessions
    }

    // --- the alarm ------------------------------------------------------------------

    /** Whether step alarms can be exact: the "Alarms & reminders" access (before Android 12 it needs none). */
    fun exactAlarmsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    private fun arm(app: Context, session: Session) {
        if (session.paused) {
            disarm(app, session.id)
            return
        }
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val fire = actionIntent(app, session.id, ACTION_FIRE)
        val exact = exactAlarmsAllowed(app) && runCatching {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, session.stepEndsAt, fire)
        }.onFailure { android.util.Log.w("ClockSessions", "exact step alarm refused, using an inexact one", it) }.isSuccess
        if (!exact) runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, session.stepEndsAt, fire) }
    }

    /**
     * Clears any timer notification that no running session stands behind: one
     * left by a session that ended while the app was updated or killed. Safe to
     * call often. Called when sessions are first read and whenever the hub opens.
     */
    fun dropStaleNotifications(context: Context, live: List<Session> = flow(context).value) {
        val app = context.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        val liveIds = live.map { notificationId(it.id) }.toSet()
        runCatching {
            nm.activeNotifications
                .filter { it.notification.channelId == CHANNEL_QUIET || it.notification.channelId == CHANNEL_SOUND }
                .filter { it.notification.flags and Notification.FLAG_ONGOING_EVENT != 0 && it.id !in liveIds }
                .forEach { nm.cancel(it.id) }
        }
    }

    /**
     * Moves on any running session whose step has ended. Called every second
     * while the hub is open or a tile is ticking, so a step ends exactly on
     * time with the screen on even when the alarm is only inexact. Does
     * nothing when no step is due, so it is safe to call often.
     */
    fun tick(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        flow(app).value.filter { !it.paused && now >= it.stepEndsAt }.forEach { onAlarm(app, it.id) }
    }

    private fun disarm(app: Context, id: String) {
        app.getSystemService(AlarmManager::class.java)?.cancel(actionIntent(app, id, ACTION_FIRE))
    }

    private fun actionIntent(app: Context, id: String, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            app,
            // One request code per (session, action) so they never overwrite each other.
            (id.hashCode() * 8 + ACTIONS.indexOf(action)),
            Intent(app, ClockSessionReceiver::class.java).setAction(action).putExtra(EXTRA_SESSION_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Opens TileShell on the clock hub (the alarm's "show" intent and the notification's tap). */
    fun openPendingIntent(app: Context): PendingIntent {
        val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)
            ?.putExtra(EXTRA_OPEN_HUB, "clock")
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: Intent()
        return PendingIntent.getActivity(app, 0x7C10, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    // --- the notification -----------------------------------------------------------

    private fun notificationsAllowed(app: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notificationId(id: String): Int = 0x7C100000 + (id.hashCode() and 0xFFFF)

    private fun notifyRunning(app: Context, s: Session) {
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        if (!notificationsAllowed(app)) return
        ensureChannels(app, nm)
        val next = s.next
        val nextText = next?.let { " · next: ${it.label.substringAfterLast(" · ")}, ${formatDuration(it.ms / 1000)}" }.orEmpty()
        val builder = Notification.Builder(app, CHANNEL_QUIET)
            .setSmallIcon(R.drawable.ic_notification_clock)
            .setContentTitle(s.title)
            .setContentText(if (s.paused) "${s.current.label} · paused" else s.current.label + nextText)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent(app))
        if (s.paused) {
            builder.setShowWhen(false)
            builder.addAction(Notification.Action.Builder(null, "resume", actionIntent(app, s.id, ACTION_RESUME)).build())
        } else {
            // The system draws the countdown itself; nothing here ticks.
            builder.setWhen(s.stepEndsAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            builder.addAction(Notification.Action.Builder(null, "pause", actionIntent(app, s.id, ACTION_PAUSE)).build())
        }
        if (s.next != null) builder.addAction(Notification.Action.Builder(null, "skip", actionIntent(app, s.id, ACTION_SKIP)).build())
        builder.addAction(Notification.Action.Builder(null, "stop", actionIntent(app, s.id, ACTION_STOP)).build())
        runCatching { nm.notify(notificationId(s.id), builder.build()) }
    }

    private fun notifyDone(app: Context, s: Session) {
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        if (!notificationsAllowed(app)) return
        ensureChannels(app, nm)
        val notification = Notification.Builder(app, CHANNEL_QUIET)
            .setSmallIcon(R.drawable.ic_notification_clock)
            .setContentTitle(s.title)
            .setContentText("done · ${formatDuration(s.totalMs() / 1000)}")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent(app))
            .build()
        runCatching { nm.notify(notificationId(s.id), notification) }
    }

    private fun ensureChannels(app: Context, nm: NotificationManager) {
        // No vibration of its own: the buzz is [ClockBuzz], so it can be an alarm's and the user's strength.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_QUIET, "timers and sets", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "the countdown of a running timer or timer set"
                setSound(null, null)
                enableVibration(false)
            },
        )
        // The chime is played by [ClockChime]; the old channel that carried it is dropped.
        runCatching { nm.deleteNotificationChannel(CHANNEL_SOUND) }
    }
}
