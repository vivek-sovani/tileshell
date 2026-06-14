package com.tileshell.feature.livetiles

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

    fun publish(snapshot: NotificationSnapshot) {
        _snapshot.value = snapshot
    }

    /** Drops everything — used when the listener disconnects / access is revoked. */
    fun clear() {
        _snapshot.value = NotificationSnapshot.EMPTY
    }
}

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
