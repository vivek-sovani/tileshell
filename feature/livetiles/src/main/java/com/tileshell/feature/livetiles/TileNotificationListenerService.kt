package com.tileshell.feature.livetiles

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap

/**
 * Mirrors the device's active notifications into [NotificationCenter] (FR-1.2
 * badges, FR-2 mail/messages faces). Opt-in: this only runs once the user grants
 * notification access in system settings (deep-linked from the personalize
 * sheet). Every post/removal recomputes the whole snapshot from
 * [getActiveNotifications] — cheap, and it keeps the count correct even if an
 * individual callback is missed.
 *
 * Reconnect handling: Android may unbind the listener (low memory, app update).
 * [onListenerDisconnected] clears the snapshot — so badges/faces degrade
 * immediately — and asks the platform to rebind; [onListenerConnected] then
 * republishes. Revoking access disconnects permanently, which is the graceful
 * opt-out.
 */
class TileNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        // Register so a tile tap can route through to cancelling this app's
        // notifications (FR-2 tap-to-open + clear).
        NotificationCenter.bindListener(this)
        refresh()
    }

    override fun onListenerDisconnected() {
        NotificationCenter.unbindListener(this)
        NotificationCenter.clear()
        // Best-effort rebind; a no-op (and harmless) if access was actually revoked.
        runCatching { requestRebind(ComponentName(this, javaClass)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        // activeNotifications throws if the listener is not connected — guard it.
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        NotificationCenter.publish(summarizeNotifications(active.mapNotNull { it.toItem() }))
        // Parallel tap-action map: how each package's tile opens + clears on tap.
        NotificationCenter.publishActions(tileNotificationActions(active.map { it.toActionRow() }))
        // Parallel image map: the newest notification's picture / contact photo per
        // package, shown alongside the text on the live face.
        NotificationCenter.publishImages(notificationImages(active))
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

private fun StatusBarNotification.toItem(): NotificationItem? {
    val extras = notification?.extras ?: return null
    return NotificationItem(
        packageName = tilePackageName(),
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        isClearable = isClearable,
        isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
        postTime = postTime,
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
