package com.tileshell.feature.livetiles.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import com.tileshell.core.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
