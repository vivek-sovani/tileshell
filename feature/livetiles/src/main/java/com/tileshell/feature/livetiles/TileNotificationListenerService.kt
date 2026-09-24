package com.tileshell.feature.livetiles

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Mirrors the device's active notifications into [NotificationCenter] (FR-1.2
 * badges, FR-2 mail/messages faces). Opt-in: this only runs once the user grants
 * notification access in system settings (deep-linked from the personalize
 * sheet). Every post/removal recomputes the whole snapshot from
 * [getActiveNotifications], which keeps the count correct even if an individual
 * callback is missed.
 *
 * That recompute is **debounced and moved off the main thread**. The comment
 * here used to call it "cheap"; it isn't. Each pass walks the live notification
 * array four times, and two of those decode and rescale bitmaps
 * ([scaledTo]/image extraction) with no cache, so the same avatar is re-decoded
 * every time. `NotificationListenerService` callbacks arrive on the main
 * thread, and a burst — a busy group chat, a mail sync — fires one callback per
 * notification, so N notifications meant N full recomputes with N rounds of
 * bitmap work on the UI thread. Since TileShell *is* the home screen and is
 * usually foregrounded, that showed up directly as jank. Coalescing a burst
 * into a single recompute on a background dispatcher fixes both halves.
 *
 * Reconnect handling: Android may unbind the listener (low memory, app update).
 * [onListenerDisconnected] clears the snapshot — so badges/faces degrade
 * immediately — and asks the platform to rebind; [onListenerConnected] then
 * republishes. Revoking access disconnects permanently, which is the graceful
 * opt-out.
 */
@OptIn(FlowPreview::class)
class TileNotificationListenerService : NotificationListenerService() {

    // The service outlives any single callback, so it owns its own scope,
    // cancelled in onDestroy.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Conflated: a burst needs exactly one recompute, and it must reflect the
    // state *after* the burst, so dropping intermediate signals is correct.
    private val refreshSignals = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            refreshSignals.debounce(REFRESH_DEBOUNCE_MS).collect {
                runCatching { refresh() }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        // Register so a tile tap can route through to cancelling this app's
        // notifications (FR-2 tap-to-open + clear).
        NotificationCenter.bindListener(this)
        // Immediate rather than debounced: on connect there is no burst to
        // coalesce and the snapshot is empty, so badges should populate at once.
        scope.launch { runCatching { refresh() } }
    }

    override fun onListenerDisconnected() {
        NotificationCenter.unbindListener(this)
        NotificationCenter.clear()
        // Best-effort rebind; a no-op (and harmless) if access was actually revoked.
        runCatching { requestRebind(ComponentName(this, javaClass)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshSignals.tryEmit(Unit)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshSignals.tryEmit(Unit)
    }

    /** Always invoked off the main thread — see the class doc. */
    private fun refresh() {
        // activeNotifications throws if the listener is not connected — guard it.
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        NotificationCenter.publish(summarizeNotifications(active.mapNotNull { it.toItem() }))
        // Parallel tap-action map: how each package's tile opens + clears on tap.
        NotificationCenter.publishActions(tileNotificationActions(active.map { it.toActionRow() }))
        // Reply / mark read / archive buttons per notification, for the People
        // Hub's "what's new" (pressed there on the user's behalf).
        NotificationCenter.publishQuickActions(quickActionButtons(active))
        // Per-package newest image (used by non-cycling faces like PhotosTileFace).
        NotificationCenter.publishImages(notificationImages(active))
        // Per-notification-key images for the cycling back face — each group/sender
        // in WhatsApp etc. has its own avatar, so we decode up to MAX_CONVERSATION_ITEMS
        // images per package so the cycling face can show the right one for each item.
        NotificationCenter.publishItemImages(notificationItemImages(active))
    }

    /** Newest dismissable notification's images per package (empty entries dropped). */
    private fun notificationImages(
        active: Array<out StatusBarNotification>,
    ): Map<String, NotificationImages> =
        active
            .filter {
                it.isClearable &&
                    ((it.notification?.flags ?: 0) and Notification.FLAG_GROUP_SUMMARY) == 0
            }
            .groupBy { it.tilePackageName() }
            .mapNotNull { (pkg, list) ->
                val newest = list.maxByOrNull { it.postTime } ?: return@mapNotNull null
                val images = newest.extractImages(this)
                if (images.avatar == null && images.picture == null) null else pkg to images
            }
            .toMap()

    /**
     * Per-notification-key images for the cycling back face. Decodes images for up to
     * [MAX_CONVERSATION_ITEMS] notifications per package (newest first) so a cycling
     * tile can show each group's / sender's own avatar instead of always using the
     * newest one.
     */
    private fun notificationItemImages(
        active: Array<out StatusBarNotification>,
    ): Map<String, NotificationImages> {
        val result = mutableMapOf<String, NotificationImages>()
        active
            .filter {
                it.isClearable &&
                    ((it.notification?.flags ?: 0) and Notification.FLAG_GROUP_SUMMARY) == 0
            }
            .groupBy { it.tilePackageName() }
            .forEach { (_, list) ->
                list.sortedByDescending { it.postTime }
                    .take(MAX_CONVERSATION_ITEMS)
                    .forEach { sbn ->
                        val images = sbn.extractImages(this)
                        if (images.avatar != null || images.picture != null) {
                            result[sbn.key] = images
                        }
                    }
            }
        return result
    }

    private companion object {
        /**
         * Long enough to swallow a notification burst (they arrive within a
         * few tens of ms of each other), short enough that a lone
         * notification's badge still looks instant.
         */
        const val REFRESH_DEBOUNCE_MS = 200L
    }
}

/**
 * A couple of OEM notifications aren't posted by the app the user thinks of —
 * Samsung's Gallery "story"/highlights feature posts under a separate companion
 * service package rather than the Gallery app itself, so a tile pinned to the
 * Gallery app would never match it by package name. Remapped to the package the
 * notification should surface on; deliberately a small, explicit table rather
 * than a general heuristic — extend only for a confirmed, specific OEM split.
 */
private val NOTIFICATION_PACKAGE_ALIASES = mapOf(
    "com.samsung.storyservice" to "com.sec.android.gallery3d",
)

/** [StatusBarNotification.packageName], remapped through [NOTIFICATION_PACKAGE_ALIASES]. */
private fun StatusBarNotification.tilePackageName(): String =
    NOTIFICATION_PACKAGE_ALIASES[packageName] ?: packageName

/**
 * Pulls the displayable images out of a notification, kept separate so the live
 * face can render them like an Android notification row: the [NotificationImages.avatar]
 * is the large icon (typically the sender's contact photo) and the
 * [NotificationImages.picture] is the big-picture style shared photo. Either may be
 * null — a plain text notification carries neither.
 *
 * Both bitmaps are downscaled to [MAX_NOTIFICATION_IMAGE_PX] — full-res photos from
 * messaging apps can be several MB and holding many in a StateFlow map risks OOM on
 * memory-constrained devices (S28 crash hardening).
 */
private fun StatusBarNotification.extractImages(context: Context): NotificationImages {
    val n = notification ?: return NotificationImages()
    val picture = (n.extras?.get(Notification.EXTRA_PICTURE) as? Bitmap)
        ?.downscaleIfNeeded(MAX_NOTIFICATION_IMAGE_PX)
    val avatar = n.getLargeIcon()
        ?.let { icon -> runCatching { icon.loadDrawable(context)?.toBitmap() }.getOrNull() }
        ?.downscaleIfNeeded(MAX_NOTIFICATION_IMAGE_PX)
    return NotificationImages(avatar = avatar, picture = picture)
}

private const val MAX_NOTIFICATION_IMAGE_PX = 600

private fun Bitmap.downscaleIfNeeded(maxPx: Int): Bitmap {
    if (width <= maxPx && height <= maxPx) return this
    val scale = maxPx.toFloat() / maxOf(width, height)
    val w = (width * scale).toInt().coerceAtLeast(1)
    val h = (height * scale).toInt().coerceAtLeast(1)
    return runCatching { Bitmap.createScaledBitmap(this, w, h, true) }.getOrDefault(this)
}

/** Every dismissable notification's classified quick-action buttons, by key. */
private fun quickActionButtons(
    active: Array<out StatusBarNotification>,
): Map<String, Map<QuickAction, Notification.Action>> =
    active.mapNotNull { sbn ->
        if (!sbn.isClearable) return@mapNotNull null
        val buttons = sbn.notification?.actions?.toList().orEmpty()
        if (buttons.isEmpty()) return@mapNotNull null
        val classified = classifyQuickActions(buttons.map { it.toInfo() })
        if (classified.isEmpty()) null else sbn.key to classified.mapValues { (_, index) -> buttons[index] }
    }.toMap()

private fun Notification.Action.toInfo(): QuickActionInfo = QuickActionInfo(
    title = title?.toString().orEmpty(),
    semanticAction = if (android.os.Build.VERSION.SDK_INT >= 28) semanticAction else 0,
    acceptsFreeText = remoteInputs?.any { it.allowFreeFormInput } == true,
)

private fun StatusBarNotification.toItem(): NotificationItem? {
    val extras = notification?.extras ?: return null
    val quickActions = notification.actions?.toList().orEmpty()
        .let { buttons -> classifyQuickActions(buttons.map { it.toInfo() }).keys }
    return NotificationItem(
        packageName = tilePackageName(),
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        isClearable = isClearable,
        isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
        postTime = postTime,
        notificationKey = key,
        quickActions = quickActions,
    )
}

private fun StatusBarNotification.toActionRow(): NotificationActionRow =
    NotificationActionRow(
        packageName = tilePackageName(),
        key = key,
        contentIntent = notification?.contentIntent,
        isClearable = isClearable,
        isGroupSummary = ((notification?.flags ?: 0) and Notification.FLAG_GROUP_SUMMARY) != 0,
        postTime = postTime,
    )
