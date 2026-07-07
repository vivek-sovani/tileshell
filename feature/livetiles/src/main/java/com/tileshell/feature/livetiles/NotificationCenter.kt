package com.tileshell.feature.livetiles

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
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
    val notificationKey: String = "",
)

/** One pending notification's sender + snippet, used for cycling on the back face. */
data class ConversationItem(
    val sender: String,
    val snippet: String,
    val notificationKey: String = "",
)

/**
 * The conversation shown on a mail / messages (or generic notification) live face.
 * [sender]/[snippet] are the newest notification. [items] holds up to
 * [MAX_CONVERSATION_ITEMS] pending notifications newest-first so the back face can
 * cycle through each one in turn.
 */
data class ConversationPreview(
    val sender: String,
    val snippet: String,
    val count: Int,
    val items: List<ConversationItem>,
)

const val MAX_CONVERSATION_ITEMS = 5

/**
 * The newest notification's images for a package, shown on its live face like an
 * Android notification row: [avatar] is the sender / large-icon (a small circular
 * thumbnail beside the text), [picture] is the shared big-picture image (a small
 * rounded thumbnail at the end of the row). Either may be null — a plain text
 * notification carries neither, a text message carries only an avatar, a photo
 * message carries both.
 */
data class NotificationImages(
    val avatar: Bitmap? = null,
    val picture: Bitmap? = null,
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
        val newestFirst = list.sortedByDescending { it.postTime }
        val latest = newestFirst.first()
        ConversationPreview(
            sender = latest.title.orEmpty().trim(),
            snippet = latest.text.orEmpty().trim(),
            count = list.size,
            items = newestFirst.take(MAX_CONVERSATION_ITEMS).map {
                ConversationItem(
                    sender = it.title.orEmpty().trim(),
                    snippet = it.text.orEmpty().trim(),
                    notificationKey = it.notificationKey,
                )
            },
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

    // The newest notification's images per package (sender avatar + shared picture),
    // shown alongside the text on the live face (FR-2). A separate flow from the
    // pure snapshot since bitmaps are framework objects; empty when access is off /
    // no notification carries an image.
    private val _images = MutableStateFlow<Map<String, NotificationImages>>(emptyMap())
    val images: StateFlow<Map<String, NotificationImages>> = _images.asStateFlow()

    // Per-notification-key images for cycling faces — each WhatsApp group has its own
    // avatar, so the cycling back face looks up images by notification key rather than
    // the single per-package image above.
    private val _itemImages = MutableStateFlow<Map<String, NotificationImages>>(emptyMap())
    val itemImages: StateFlow<Map<String, NotificationImages>> = _itemImages.asStateFlow()

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

    /** Publishes the latest per-package notification images (alongside [publish]). */
    fun publishImages(images: Map<String, NotificationImages>) {
        _images.value = images
    }

    /** Publishes per-notification-key images for cycling faces (alongside [publish]). */
    fun publishItemImages(images: Map<String, NotificationImages>) {
        _itemImages.value = images
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
     * notifications, opens the newest one's content intent (so the user lands on the
     * relevant screen in the app) and clears that app's notifications. Returns true
     * when the content intent was launched — the caller then skips its normal app
     * launch. Returns false when the package has no notifications, or had only
     * intent-less / un-launchable ones (now cleared), so the caller falls back to a
     * plain app launch — guaranteeing the app still opens on tap.
     *
     * [context] is the (foreground) launcher activity; the content intent is sent
     * through it with background-activity-start allowed (API 34+) so notification
     * "trampolines" actually bring the target activity forward instead of silently
     * no-op'ing.
     */
    fun openAndClear(context: Context, packageName: String): Boolean {
        val action = actions[packageName] ?: return false
        val opened = action.contentIntent?.let { intent -> sendContentIntent(context, intent) } ?: false
        listener?.let { service ->
            if (action.keys.isNotEmpty()) {
                runCatching { service.cancelNotifications(action.keys.toTypedArray()) }
            }
        }
        return opened
    }

    private fun sendContentIntent(context: Context, intent: PendingIntent): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 34) {
            val options = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            intent.send(context, 0, null, null, null, null, options.toBundle())
        } else {
            intent.send()
        }
        true
    }.getOrDefault(false)

    /** Drops everything — used when the listener disconnects / access is revoked. */
    fun clear() {
        _snapshot.value = NotificationSnapshot.EMPTY
        actions = emptyMap()
        _images.value = emptyMap()
        _itemImages.value = emptyMap()
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
