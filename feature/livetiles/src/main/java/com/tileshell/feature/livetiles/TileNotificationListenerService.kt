package com.tileshell.feature.livetiles

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

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
    }
}

private fun StatusBarNotification.toItem(): NotificationItem? {
    val extras = notification?.extras ?: return null
    return NotificationItem(
        packageName = packageName,
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        isClearable = isClearable,
        isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
        postTime = postTime,
    )
}

private fun StatusBarNotification.toActionRow(): NotificationActionRow =
    NotificationActionRow(
        packageName = packageName,
        key = key,
        contentIntent = notification?.contentIntent,
        isClearable = isClearable,
        isGroupSummary = ((notification?.flags ?: 0) and Notification.FLAG_GROUP_SUMMARY) != 0,
        postTime = postTime,
    )
