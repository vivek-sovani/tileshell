package com.tileshell.feature.livetiles

import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A single active notification, reduced to the framework-free fields the live
 * tiles care about (FR-1.2 badges, FR-2 mail/messages faces). The listener
 * service maps each `StatusBarNotification` to one of these so the aggregation
 * in [summarizeNotifications] stays pure and unit-testable.
 *
 * @property packageName the posting app — tiles match on this.
 * @property title the notification title (the conversation tile's sender line).
 * @property text the notification body (the snippet line).
 * @property isClearable false for ongoing notifications (music, navigation,
 *   foreground services); these never contribute a badge count.
 * @property isGroupSummary true for the bundling summary row that mirrors its
 *   children — dropped so a 3-message thread counts as 3, not 4.
 * @property postTime when it was posted; the newest per package wins the preview.
 */
data class NotificationItem(
    val packageName: String,
    val title: String?,
    val text: String?,
    val isClearable: Boolean,
    val isGroupSummary: Boolean,
    val postTime: Long,
)

/** The latest conversation shown on a mail / messages live face (FR-2). */
data class ConversationPreview(
    val sender: String,
    val snippet: String,
    val count: Int,
)

/**
 * What a tile does when its live notification is tapped: open the newest
 * notification's [contentIntent] (jumping straight to the relevant screen inside
 * the app) and clear that app's notifications by cancelling [keys]. Holds live
 * framework objects, so it is kept out of the pure [summarizeNotifications] path
 * and the recomposition-driving [NotificationSnapshot].
 */
data class TileNotificationAction(
    val contentIntent: PendingIntent?,
    val keys: List<String>,
)

/**
 * The current notification picture the tiles render from: per-package badge
 * [badges] counts (FR-1.2) and the newest [conversations] preview per package
 * (FR-2). Empty when notification access is off or the listener is disconnected,
 * which is exactly the graceful opt-out state — tiles drop their badges and the
 * mail/messages faces fall back to a static glyph.
 */
data class NotificationSnapshot(
    val badges: Map<String, Int>,
    val conversations: Map<String, ConversationPreview>,
) {
    fun badgeFor(packageName: String): Int = badges[packageName] ?: 0
    fun conversationFor(packageName: String): ConversationPreview? = conversations[packageName]

    companion object {
        val EMPTY = NotificationSnapshot(emptyMap(), emptyMap())
    }
}

/**
 * Reduces the active notifications to a [NotificationSnapshot]. Ongoing and
 * group-summary rows are ignored; the badge count is the number of remaining
 * (dismissable) notifications per package, and the preview is the newest of
 * those. Pure so the counting / preview rules are unit-tested without a device.
 */
fun summarizeNotifications(items: List<NotificationItem>): NotificationSnapshot {
    val relevant = items.filter { it.isClearable && !it.isGroupSummary }
    if (relevant.isEmpty()) return NotificationSnapshot.EMPTY

    val byPackage = relevant.groupBy { it.packageName }
    val badges = byPackage.mapValues { (_, list) -> list.size }
    val conversations = byPackage.mapValues { (_, list) ->
        val latest = list.maxByOrNull { it.postTime } ?: list.first()
        ConversationPreview(
            sender = latest.title.orEmpty().trim(),
            snippet = latest.text.orEmpty().trim(),
            count = list.size,
        )
    }
    return NotificationSnapshot(badges, conversations)
}

/**
 * Process-wide live state published by [TileNotificationListenerService] and read
 * by the Start grid (badges) and the mail/messages tiles (previews). A plain
 * singleton `StateFlow` rather than a repository: notification state is ephemeral
 * — it is rebuilt from `getActiveNotifications()` whenever the listener (re)binds,
 * so there is nothing to persist.
 */
object NotificationCenter {
    private val _snapshot = MutableStateFlow(NotificationSnapshot.EMPTY)
    val snapshot: StateFlow<NotificationSnapshot> = _snapshot.asStateFlow()

    // Per-package tap actions (content intent + keys to clear), and the connected
    // listener used to cancel them. Read imperatively on a tile tap rather than via
    // StateFlow — these carry framework objects and must not drive recomposition.
    // @Volatile so the listener thread's writes are visible to the UI thread's reads.
    @Volatile private var actions: Map<String, TileNotificationAction> = emptyMap()
    @Volatile private var listener: NotificationListenerService? = null

    fun publish(snapshot: NotificationSnapshot) {
        _snapshot.value = snapshot
    }

    /** Publishes the latest per-package tap actions (called alongside [publish]). */
    fun publishActions(actions: Map<String, TileNotificationAction>) {
        this.actions = actions
    }

    /** Registers the connected listener so tile taps can cancel notifications. */
    fun bindListener(service: NotificationListenerService) {
        listener = service
    }

    /** Drops the listener if it is the one currently bound (on disconnect). */
    fun unbindListener(service: NotificationListenerService) {
        if (listener === service) listener = null
    }

    /**
     * Tapping a live notification tile: if [packageName] currently has
     * notifications, clears them and opens the newest one's content intent (so the
     * user lands on the relevant screen in the app). Returns true when a content
     * intent was launched — the caller then skips its normal app launch. Returns
     * false when the package has no notifications, or had only intent-less ones
     * (now cleared), so the caller falls back to a plain launch.
     */
    fun openAndClear(packageName: String): Boolean {
        val action = actions[packageName] ?: return false
        val opened = action.contentIntent?.let { intent ->
            runCatching { intent.send(); true }.getOrDefault(false)
        } ?: false
        listener?.let { service ->
            if (action.keys.isNotEmpty()) {
                runCatching { service.cancelNotifications(action.keys.toTypedArray()) }
            }
        }
        return opened
    }

    /** Drops everything — used when the listener disconnects / access is revoked. */
    fun clear() {
        _snapshot.value = NotificationSnapshot.EMPTY
        actions = emptyMap()
    }
}

/**
 * Reduces active notifications to the per-package [TileNotificationAction] map: the
 * newest dismissable notification's content intent and every dismissable key for
 * that package. Group-summary rows are dropped from the preview pick (they mirror
 * children) but their keys are still cleared so cancelling empties the whole group.
 */
fun tileNotificationActions(
    rows: List<NotificationActionRow>,
): Map<String, TileNotificationAction> {
    val clearable = rows.filter { it.isClearable }
    if (clearable.isEmpty()) return emptyMap()
    return clearable.groupBy { it.packageName }.mapValues { (_, list) ->
        val newest = list.filterNot { it.isGroupSummary }.maxByOrNull { it.postTime }
            ?: list.maxByOrNull { it.postTime }
            ?: list.first()
        TileNotificationAction(
            contentIntent = newest.contentIntent,
            keys = list.map { it.key },
        )
    }
}

/** A notification reduced to the fields [tileNotificationActions] needs. */
data class NotificationActionRow(
    val packageName: String,
    val key: String,
    val contentIntent: PendingIntent?,
    val isClearable: Boolean,
    val isGroupSummary: Boolean,
    val postTime: Long,
)

/**
 * Initials for a sender avatar (prototype `initials(name)`): the first letter of
 * the first and last whitespace-separated words, uppercased. Falls back to a
 * single dot for an empty / blank name. Pure for unit testing.
 */
fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "·"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}
