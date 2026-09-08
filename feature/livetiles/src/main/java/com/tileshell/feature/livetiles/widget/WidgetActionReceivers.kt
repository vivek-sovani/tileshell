package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import com.tileshell.core.data.TaskRepository
import com.tileshell.feature.livetiles.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The colour a widget's refresh icon flashes to the instant a tap is received — see [flashRefreshIcon]. */
private const val WIDGET_REFRESH_FLASH_COLOR = 0xFFFFC107.toInt()

/** The icon's own normal size (matches every `widget_refresh` ImageView's `layout_width`/`height` in every layout XML) and its brief tap-pulse size, in dp. */
private const val WIDGET_REFRESH_NORMAL_DP = 24f
private const val WIDGET_REFRESH_PULSE_DP = 30f

/**
 * How long [flashRefreshIcon]'s tint + pulse is *guaranteed* to stay up,
 * regardless of how fast the real refresh this same tap triggers completes
 * — user-reported: "visible on weather and sports, but not on stock,"
 * confirmed as "showing but very fast disappearing." Root cause: stock's
 * own fetch (unlike weather/sports) goes through `QuoteCache`
 * (`:core:data`) — a 45s memo shared with the in-app tile, the glance card,
 * and every other placed widget tracking the same symbol. A tap arriving
 * while a recent fetch for that symbol is still cache-fresh resolves (and
 * so triggers the real content push that resets the flash) in single-digit
 * milliseconds — long before a human eye can register a flash that was
 * just set. Weather/sports have no comparable shared cache, so their own
 * real fetch (1-3s+) naturally left enough time for the flash to be seen
 * before anything reset it. See [scheduleFlashReset].
 */
private const val WIDGET_REFRESH_FLASH_MIN_VISIBLE_MS = 600L

/**
 * Resets the refresh icon back to its normal 24dp size, undoing
 * [flashRefreshIcon]'s own tap-pulse. Called from every real content-push
 * build function (weather/stock/sports) alongside their existing
 * `setColorFilter` reset — see e.g. `StockWidgetRefreshWorker`'s doc comment
 * on why that reset has to be explicit (a real content push runs on the
 * SAME already-inflated view via `RemoteViews.reapply`, not a fresh
 * inflate, so a size set by an earlier partial update otherwise persists
 * forever). API-gated the same way [flashRefreshIcon] is: unlike the
 * `setColorFilter` reset it sits beside, `RemoteViews.setViewLayoutWidth`/
 * `Height` are real methods added to the platform in API 31 — calling them
 * on an older device's own system `RemoteViews` class (not something this
 * app bundles) would fail to resolve at all, not just be ignored, so this
 * must never run un-gated the way the colour-filter reset safely can.
 */
fun resetRefreshIconSize(views: RemoteViews) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    views.setViewLayoutWidth(R.id.widget_refresh, WIDGET_REFRESH_NORMAL_DP, TypedValue.COMPLEX_UNIT_DIP)
    views.setViewLayoutHeight(R.id.widget_refresh, WIDGET_REFRESH_NORMAL_DP, TypedValue.COMPLEX_UNIT_DIP)
}

/**
 * Brief "got it" feedback on a widget's own refresh icon, shown the instant
 * a manual refresh tap is received (user-reported: "user cant understand
 * whether refresh is tapped or not") — a manual tap triggers a real network
 * fetch that can easily take a second or more, so without this nothing
 * visibly changes until that finishes, reading as a dead button in the
 * meantime. Two effects together: a colour tint (unchanged from the first
 * version) plus a brief size pulse (24dp → 30dp) — user-requested after the
 * colour alone "wasn't read as an animation."
 *
 * **A real spin animation was tried first and reverted.** User-reported,
 * twice: first the plain colour swap alone wasn't read as feedback, so an
 * `<animated-selector>`/`<animated-vector>` (a real 360° rotation, triggered
 * by `RemoteViews.setBoolean(id, "setActivated", true)`) replaced it. That
 * broke the widget outright ("widgets crashed"/went blank) — confirmed via
 * `adb logcat`: `RemoteViews$ActionException: view: android.widget.ImageView
 * can't use method with RemoteViews: setActivated(boolean)`. RemoteViews'
 * *generic* reflection setters (`setBoolean`/`setInt`/etc., called with an
 * arbitrary method-name string) are checked against an internal per-view-
 * type allowlist, and `setActivated` isn't on it — `setColorFilter` is
 * (already used extensively elsewhere in this codebase, e.g. every
 * `onAccent` icon tint), which is why that half kept working.
 *
 * The size pulse deliberately avoids that whole risk class: [RemoteViews
 * .setViewLayoutWidth]/[RemoteViews.setViewLayoutHeight] (API 31+) are
 * *dedicated, first-class* `RemoteViews` methods — not the generic
 * `setInt`/`setBoolean("someMethodName", …)` reflection path the allowlist
 * gates, so there is no equivalent "is this specific call permitted"
 * rejection to hit. They were added to the platform specifically so a
 * partial update like this one could resize a view without needing to
 * rebuild/reinflate anything.
 *
 * Uses [AppWidgetManager.partiallyUpdateAppWidget] (API 31+ only) rather
 * than a normal [AppWidgetManager.updateAppWidget] — the latter *replaces*
 * the widget's whole view tree with whatever the given [RemoteViews]
 * describes, which would blank every other view back to its XML default
 * (no accent gradient, no cached text) since this receiver has no cheap way
 * to also rebuild the widget's real content (weather/stock/sports each
 * fetch that from the network inside their own worker, not here). A partial
 * update instead replays only the recorded actions — tint and resize this
 * one view — against whatever is already live on screen, leaving everything
 * else untouched. [layoutRes] only needs to be *a* valid layout for this
 * provider, not necessarily the exact variant currently hosted (stock alone
 * has four - single/group × compact/full) — a partial update's own
 * `RemoteViews` is never inflated, only replayed by view id against the
 * already-inflated tree, and `R.id.widget_refresh` is the same id, at the
 * same normal 24dp size, in every variant of a given widget kind.
 *
 * No revert step of its own: the real refresh this same tap triggers
 * repaints the icon back to its plain tint *and* its normal size as an
 * ordinary part of its own next content push (see e.g.
 * `StockWidgetRefreshWorker`'s matching doc comment for why that reset has
 * to be explicit there), once the fetch it kicked off completes — *when*
 * that happens is out of this function's control, which is exactly why
 * [scheduleFlashReset] exists alongside it (see that function's own doc
 * comment for why relying solely on the real content push wasn't enough).
 * Below API 31 this whole function is a silent no-op — the real refresh
 * completing promptly is still the feedback there, just without the
 * instant pre-tap pulse.
 */
private fun flashRefreshIcon(context: Context, providerClass: Class<out AppWidgetProvider>, layoutRes: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, providerClass))
        if (ids.isEmpty()) return
        val flash = RemoteViews(context.packageName, layoutRes)
        flash.setInt(R.id.widget_refresh, "setColorFilter", WIDGET_REFRESH_FLASH_COLOR)
        flash.setViewLayoutWidth(R.id.widget_refresh, WIDGET_REFRESH_PULSE_DP, TypedValue.COMPLEX_UNIT_DIP)
        flash.setViewLayoutHeight(R.id.widget_refresh, WIDGET_REFRESH_PULSE_DP, TypedValue.COMPLEX_UNIT_DIP)
        ids.forEach { id -> manager.partiallyUpdateAppWidget(id, flash) }
    }
}

/**
 * Guarantees [flashRefreshIcon]'s tint + pulse stays visible for at least
 * [WIDGET_REFRESH_FLASH_MIN_VISIBLE_MS] — see that constant's own doc
 * comment for the "why" (stock's shared `QuoteCache` making the real
 * refresh sometimes resolve, and reset the flash, in single-digit
 * milliseconds). Suspends [WIDGET_REFRESH_FLASH_MIN_VISIBLE_MS] then resets
 * every placed widget's icon back to its own correct tint (each widget's
 * real [resolveWidgetAccent], not a fixed colour — the same value its real
 * content push would use) and normal size.
 *
 * This reset is deliberately unconditional — it always fires after the
 * delay, whether or not the real content push already got there first.
 * Landing *after* the real push (the common case once a fetch is slow
 * enough to leave the flash visible on its own, e.g. weather/sports) is a
 * harmless no-op repeat of what that push already set; landing *before*
 * it (the fast-cache case this exists for) is what actually shows the icon
 * settling back to normal instead of sitting pulsed/tinted indefinitely
 * until whenever the fetch eventually finishes.
 *
 * Callers run this from `goAsync()` (see e.g. [StockWidgetActionReceiver]),
 * the same pattern [TaskWidgetActionReceiver] already uses for its own
 * async work — a plain coroutine launched from `onReceive` with no lifetime
 * extension risks the OS tearing down the receiver (and this suspended
 * function with it) before the delay elapses.
 */
private suspend fun scheduleFlashReset(context: Context, providerClass: Class<out AppWidgetProvider>, layoutRes: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    delay(WIDGET_REFRESH_FLASH_MIN_VISIBLE_MS)
    runCatching {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, providerClass))
        ids.forEach { id ->
            val (_, onAccent) = resolveWidgetAccent(context, id)
            val reset = RemoteViews(context.packageName, layoutRes)
            reset.setInt(R.id.widget_refresh, "setColorFilter", onAccent)
            resetRefreshIconSize(reset)
            manager.partiallyUpdateAppWidget(id, reset)
        }
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
        // See scheduleFlashReset's own doc comment: stock's shared QuoteCache
        // can make the real refresh below resolve — and reset the flash —
        // in single-digit milliseconds, too fast to ever be seen without this.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                scheduleFlashReset(context, StockAppWidgetProvider::class.java, R.layout.widget_stock)
            } finally {
                pending.finish()
            }
        }
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
        // See scheduleFlashReset's own doc comment — same guaranteed-minimum-
        // visible-duration reasoning as StockWidgetActionReceiver, in case a
        // future cache/fast-path makes this fetch resolve unusually fast too.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                scheduleFlashReset(context, SportsAppWidgetProvider::class.java, R.layout.widget_sports)
            } finally {
                pending.finish()
            }
        }
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
        // See scheduleFlashReset's own doc comment — same guaranteed-minimum-
        // visible-duration reasoning as StockWidgetActionReceiver; weather's
        // own fetch already left enough of a gap on its own, but this is a
        // harmless no-op repeat in that case, not a risk.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                scheduleFlashReset(context, WeatherAppWidgetProvider::class.java, R.layout.widget_weather)
            } finally {
                pending.finish()
            }
        }
        runCatching { com.tileshell.feature.livetiles.WeatherRefreshWorker.refreshNow(context) }
    }

    companion object {
        const val ACTION_REFRESH_WEATHER = "com.tileshell.feature.livetiles.widget.ACTION_REFRESH_WEATHER"
    }
}
