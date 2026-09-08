package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.widget.RemoteViews
import com.tileshell.core.data.TaskRepository
import com.tileshell.feature.livetiles.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The colour a widget's refresh icon flashes to the instant a tap is received — see [flashRefreshIcon]. */
private const val WIDGET_REFRESH_FLASH_COLOR = 0xFFFFC107.toInt()

/**
 * Brief "got it" flash on a widget's own refresh icon, shown the instant a
 * manual refresh tap is received (user-reported: "user cant understand
 * whether refresh is tapped or not") — a manual tap triggers a real network
 * fetch that can easily take a second or more, so without this nothing
 * visibly changes until that finishes, reading as a dead button in the
 * meantime.
 *
 * **A real spin animation was tried first and reverted** — user-reported,
 * twice: first this plain colour swap alone wasn't read as feedback, so an
 * `<animated-selector>`/`<animated-vector>` (a real 360° rotation, triggered
 * by `RemoteViews.setBoolean(id, "setActivated", true)`) replaced it. That
 * broke the widget outright ("widgets crashed"/went blank) — confirmed via
 * `adb logcat`: `RemoteViews$ActionException: view: android.widget.ImageView
 * can't use method with RemoteViews: setActivated(boolean)`. RemoteViews'
 * reflection setters (`setBoolean`/`setInt`/etc.) are *not* generic — each
 * checks the target method against an internal per-view-type allowlist, and
 * `setActivated` isn't on it (unlike `setColorFilter`, already used
 * extensively elsewhere in this codebase, e.g. every `onAccent` icon tint).
 * Reverted rather than guess at another boolean setter (`setSelected`,
 * `setEnabled`, ...) against the same real, already-placed widgets and risk
 * a second blank-widget incident — this colour flash is the confirmed-safe
 * baseline; a genuine smooth animation would need a RemoteViews-official API
 * (e.g. `setViewLayoutWidth`/`Height`, API 31+) rather than generic
 * reflection, and hasn't been attempted again this session.
 *
 * Uses [AppWidgetManager.partiallyUpdateAppWidget] (API 31+ only) rather
 * than a normal [AppWidgetManager.updateAppWidget] — the latter *replaces*
 * the widget's whole view tree with whatever the given [RemoteViews]
 * describes, which would blank every other view back to its XML default
 * (no accent gradient, no cached text) since this receiver has no cheap way
 * to also rebuild the widget's real content (weather/stock/sports each
 * fetch that from the network inside their own worker, not here). A partial
 * update instead replays only the one recorded action — tint this view —
 * against whatever is already live on screen, leaving everything else
 * untouched. [layoutRes] only needs to be *a* valid layout for this
 * provider, not necessarily the exact variant currently hosted (stock alone
 * has four - single/group × compact/full) — a partial update's own
 * `RemoteViews` is never inflated, only replayed by view id against the
 * already-inflated tree, and `R.id.widget_refresh` is the same id in every
 * variant of a given widget kind.
 *
 * No explicit "revert to normal" step: the real refresh this same tap
 * triggers repaints the icon back to its plain white tint as an ordinary
 * part of its own next content push, once the fetch it kicked off
 * completes. Below API 31 this is a silent no-op — the real refresh
 * completing promptly is still the feedback there, just without the
 * instant pre-flash.
 */
private fun flashRefreshIcon(context: Context, providerClass: Class<out AppWidgetProvider>, layoutRes: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, providerClass))
        if (ids.isEmpty()) return
        val flash = RemoteViews(context.packageName, layoutRes)
        flash.setInt(R.id.widget_refresh, "setColorFilter", WIDGET_REFRESH_FLASH_COLOR)
        ids.forEach { id -> manager.partiallyUpdateAppWidget(id, flash) }
    }
}

/**
 * Private receivers for the two widget actions that actually *change something*
 * — completing/deleting a task, and switching the torch.
 *
 * These used to live on [TasksAppWidgetProvider] and
 * [FlashlightAppWidgetProvider] themselves, which was a real vulnerability. An
 * [android.appwidget.AppWidgetProvider] has to be `exported="true"` (that is
 * how the OS delivers `APPWIDGET_UPDATE` to it), and an exported receiver is
 * reachable by an **explicit** intent from any other installed app regardless
 * of what its `intent-filter` lists. Neither provider authenticated the caller,
 * so:
 *
 * - any app could broadcast `ACTION_DELETE_TASK` with a guessed `task_id` and
 *   silently delete the user's tasks — and `taskId` is a small sequential Room
 *   primary key, so they are trivially enumerable. `TaskDao.delete`/`setDone`
 *   act on that global id with no list-ownership scoping, so this reached
 *   *every* list: home-screen widgets, Start tiles and glance cards alike. No
 *   permission, no user interaction, no UI.
 * - any app could broadcast `ACTION_TOGGLE_FLASHLIGHT` and switch the torch on
 *   or off at will.
 *
 * Splitting them onto their own `exported="false"` receivers closes both
 * completely. A [android.app.PendingIntent] is dispatched by the system using
 * the *creating* app's identity, so the widgets' own click intents still reach
 * these receivers exactly as before, while another app has no route to them at
 * all. This is preferable to a signature-level permission here: it removes the
 * attack surface outright rather than gating it, and needs no new permission
 * declaration for reviewers to assess.
 */
class TaskWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId == -1L) return
        val done: Boolean? = when (intent.action) {
            ACTION_TOGGLE_TASK -> intent.getBooleanExtra(EXTRA_TARGET_DONE, false)
            ACTION_DELETE_TASK -> null
            else -> return
        }
        val deleting = intent.action == ACTION_DELETE_TASK

        // The *entire* body is guarded, and finish() is in a finally.
        //
        // This runs on a bare CoroutineScope with no SupervisorJob and no
        // CoroutineExceptionHandler, so an uncaught throw anywhere in here is
        // fatal to the process — and this process is the user's Home screen.
        // Guarding only the repository call (as this originally did) left
        // refreshNow() exposed, which can genuinely throw in a cold
        // widget-host process where WorkManager isn't initialised yet. Missing
        // the finish() on that path would also earn a "BroadcastReceiver did
        // not call finish()" ANR warning on top of the crash.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = TaskRepository.create(context)
                if (deleting) repo.delete(taskId) else repo.setDone(taskId, done == true)
                TasksWidgetRefreshWorker.refreshNow(context)
            } catch (t: Throwable) {
                // Nothing actionable at a widget tap; the next refresh re-syncs.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_TASK = "com.tileshell.feature.livetiles.widget.ACTION_TOGGLE_TASK"
        const val ACTION_DELETE_TASK = "com.tileshell.feature.livetiles.widget.ACTION_DELETE_TASK"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TARGET_DONE = "target_done"
    }
}

/** See [TaskWidgetActionReceiver] — the torch toggle, off the exported provider. */
class FlashlightWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE_FLASHLIGHT) return
        // Fully guarded for the same reason as [TaskWidgetActionReceiver]: an
        // uncaught throw here would take the Home process down with it.
        runCatching {
            val next = !WidgetFlashlightState.isOn(context)
            runCatching {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    cameraManager.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return@runCatching
                cameraManager.setTorchMode(cameraId, next)
            }
            WidgetFlashlightState.setOn(context, next)
            FlashlightWidgetRefreshWorker.refreshNow(context)
        }
    }

    companion object {
        const val ACTION_TOGGLE_FLASHLIGHT = "com.tileshell.feature.livetiles.widget.ACTION_TOGGLE_FLASHLIGHT"
    }
}

/**
 * Manual "refresh now" tap target for the stock widget (user-requested,
 * alongside a faster automatic cadence while the market's open — see
 * [StockWidgetRefreshWorker]'s own doc comment). Same
 * `exported="false"`-behind-a-private-receiver shape as
 * [TaskWidgetActionReceiver]/[FlashlightWidgetActionReceiver] above — the
 * provider itself has to stay exported, so its click intents are routed
 * here instead of handled inline. [StockWidgetRefreshWorker.refreshNow]
 * always forces a real fetch regardless of market hours, matching a manual
 * refresh tap's own "I want current data right now" intent.
 */
class StockWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH_STOCK) return
        flashRefreshIcon(context, StockAppWidgetProvider::class.java, R.layout.widget_stock)
        runCatching { StockWidgetRefreshWorker.refreshNow(context) }
    }

    companion object {
        const val ACTION_REFRESH_STOCK = "com.tileshell.feature.livetiles.widget.ACTION_REFRESH_STOCK"
    }
}

/** See [StockWidgetActionReceiver] — the sports widget's own manual refresh tap. */
class SportsWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH_SPORTS) return
        flashRefreshIcon(context, SportsAppWidgetProvider::class.java, R.layout.widget_sports)
        runCatching { SportsWidgetRefreshWorker.refreshNow(context) }
    }

    companion object {
        const val ACTION_REFRESH_SPORTS = "com.tileshell.feature.livetiles.widget.ACTION_REFRESH_SPORTS"
    }
}

/**
 * See [StockWidgetActionReceiver] — the weather widget's own manual refresh
 * tap. Weather is the one widget in this batch where forcing
 * [com.tileshell.feature.livetiles.WeatherRefreshWorker] (the one that
 * actually fetches) is enough on its own: that worker's own `doWork` already
 * ends by calling `WeatherWidgetRefreshWorker.refreshNow` itself once the
 * fetch resolves (see its class doc comment) — [WeatherWidgetRefreshWorker]
 * never fetches anything itself, it only re-renders whatever's cached.
 *
 * A first version called *both* `refreshNow`s directly from here, reasoning
 * the widget-only one was needed to actually show data — user-reported
 * afterward: "the refresh action press is visible … but not for weather."
 * Root cause: `WeatherWidgetRefreshWorker.refreshNow` (no network, just a
 * cache read + render) runs essentially instantly next to the real fetch
 * `WeatherRefreshWorker.refreshNow` takes 1-3s+ for, so that extra call
 * repainted the icon back to normal — using the *old*, still-stale cache,
 * doubly pointless — within a spare handful of milliseconds of
 * [flashRefreshIcon] setting it, well before it could ever be seen. Stock/
 * sports don't have this race: each has exactly one worker that fetches
 * *and* renders in the same `doWork`, so there's a genuine network round
 * trip between the flash and the repaint that overwrites it.
 */
class WeatherWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH_WEATHER) return
        flashRefreshIcon(context, WeatherAppWidgetProvider::class.java, R.layout.widget_weather)
        runCatching { com.tileshell.feature.livetiles.WeatherRefreshWorker.refreshNow(context) }
    }

    companion object {
        const val ACTION_REFRESH_WEATHER = "com.tileshell.feature.livetiles.widget.ACTION_REFRESH_WEATHER"
    }
}
