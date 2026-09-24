package com.tileshell.feature.livetiles

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
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
    val quickActions: Set<QuickAction> = emptySet(),
)

/**
 * A notification button the People Hub's "what's new" can press on the user's
 * behalf without opening the app. Only offered when the posting app itself put
 * that button on the notification (see [classifyQuickActions]).
 */
enum class QuickAction { REPLY, MARK_READ, ARCHIVE }

/**
 * One notification button reduced to what [classifyQuickActions] needs.
 * [semanticAction] is `Notification.Action.getSemanticAction()` (API 28+, 0 when
 * unknown); [acceptsFreeText] is true when the button carries a free-form
 * `RemoteInput` (a reply box).
 */
data class QuickActionInfo(
    val title: String,
    val semanticAction: Int,
    val acceptsFreeText: Boolean,
)

/**
 * Maps each [QuickAction] to the index of the notification button that performs
 * it. Prefers the app's declared semantic action, then falls back to the
 * button's title, since many apps (WhatsApp, Gmail) don't declare one. A reply
 * must carry a free-form text input, or there's nothing to type into. The first
 * matching button wins. Pure for unit testing.
 */
fun classifyQuickActions(actions: List<QuickActionInfo>): Map<QuickAction, Int> {
    val result = mutableMapOf<QuickAction, Int>()
    actions.forEachIndexed { index, action ->
        val title = action.title.trim().lowercase()
        val kind = when {
            action.acceptsFreeText &&
                (action.semanticAction == SEMANTIC_REPLY || action.semanticAction == 0) -> QuickAction.REPLY
            action.semanticAction == SEMANTIC_MARK_AS_READ -> QuickAction.MARK_READ
            action.semanticAction == SEMANTIC_ARCHIVE -> QuickAction.ARCHIVE
            action.semanticAction != 0 -> null
            title == "mark as read" || title == "mark read" || title == "read" -> QuickAction.MARK_READ
            title == "archive" -> QuickAction.ARCHIVE
            else -> null
        }
        if (kind != null) result.putIfAbsent(kind, index)
    }
    return result
}

// Notification.Action.SEMANTIC_ACTION_* values (API 28+), inlined so the pure
// classifier doesn't need the framework class.
internal const val SEMANTIC_REPLY = 1
internal const val SEMANTIC_MARK_AS_READ = 2
internal const val SEMANTIC_ARCHIVE = 5

/** One pending notification's sender + snippet, used for cycling on the back
 * face — [postTime] additionally lets the People Hub's "what's new" page sort
 * and time-label entries flattened across every package. */
data class ConversationItem(
    val sender: String,
    val snippet: String,
    val notificationKey: String = "",
    val postTime: Long = 0L,
    val quickActions: Set<QuickAction> = emptySet(),
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
 * What a tile does when its live notification is tapped: open a notification's
 * [contentIntent] (jumping straight to the relevant screen inside the app) — the
 * newest one by default, or whichever specific one [itemIntents] says the face was
 * actually showing at tap time — and clear that app's notifications by cancelling
 * [keys]. Holds live framework objects, so it is kept out of the pure
 * [summarizeNotifications] path and the recomposition-driving [NotificationSnapshot].
 *
 * @property itemIntents every dismissable, non-summary notification's own content
 *   intent, keyed by [NotificationActionRow.key] — lets a tap open the specific
 *   notification the cycling back face was displaying, not just the newest.
 */
data class TileNotificationAction(
    val contentIntent: PendingIntent?,
    val itemIntents: Map<String, PendingIntent?>,
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
                    postTime = it.postTime,
                    quickActions = it.quickActions,
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

    // Per-notification-key buttons the People Hub can press for the user (reply,
    // mark read, archive) — framework objects, so held imperatively like [actions].
    @Volatile private var quickActions: Map<String, Map<QuickAction, Notification.Action>> = emptyMap()
    @Volatile private var listener: NotificationListenerService? = null

    // The notification key each cycling back face is CURRENTLY showing per package,
    // reported by ConversationTileFace/NotificationTileFace on every step (and
    // cleared back to null whenever the front/count face is showing instead) — so a
    // tap opens whichever specific notification was actually on screen at that
    // moment, not always the newest. Same imperative, non-StateFlow pattern as
    // [actions]/[listener] above, for the same reason.
    @Volatile private var displayedKeys: Map<String, String> = emptyMap()

    /** Records which notification a cycling face is currently displaying for
     * [packageName], or clears it (pass null) once that face returns to its
     * front/count face — called from the tile face composables, not user code. */
    fun reportDisplayedKey(packageName: String, key: String?) {
        displayedKeys = if (key == null) displayedKeys - packageName else displayedKeys + (packageName to key)
    }

    // Which (package, notification key) the People Hub "what's new" tile's own
    // back face is currently showing — that tile aggregates across many
    // packages (unlike every other conversation-style tile, which is pinned to
    // one), so a tap can't be routed by the tile's own packageName the way
    // [openAndClear] is; it needs its own pointer instead. Same imperative,
    // non-StateFlow pattern as [displayedKeys], for the same reason.
    @Volatile private var whatsNewDisplayed: Pair<String, String>? = null

    /** Records which notification the "what's new" tile's back face is
     * currently showing, or clears it (pass null) once its front/count face
     * is showing instead — called from [PeopleHubPageTileFace], not user code. */
    fun reportWhatsNewDisplayed(packageName: String?, key: String?) {
        whatsNewDisplayed = if (packageName != null && key != null) packageName to key else null
    }

    /** Tapping the "what's new" tile while its back face shows a specific
     * notification opens that one and clears it, the same as [openAndClear]
     * for a single-package tile. Returns false (front/count face showing, or
     * nothing pending) so the caller falls back to opening the People Hub. */
    fun openWhatsNewDisplayed(context: Context): Boolean {
        val (packageName, key) = whatsNewDisplayed ?: return false
        reportDisplayedKey(packageName, key)
        return openAndClear(context, packageName)
    }

    fun publish(snapshot: NotificationSnapshot) {
        _snapshot.value = snapshot
    }

    /** Publishes the latest per-package tap actions (called alongside [publish]). */
    fun publishActions(actions: Map<String, TileNotificationAction>) {
        this.actions = actions
    }

    /** Publishes the per-notification-key quick-action buttons (alongside [publish]). */
    fun publishQuickActions(actions: Map<String, Map<QuickAction, Notification.Action>>) {
        quickActions = actions
    }

    /**
     * Presses [action]'s button on the notification [key], as if the user had
     * tapped it in the notification shade. For [QuickAction.REPLY], [replyText]
     * fills the button's free-form text input. Returns false when the button is
     * gone (the notification was updated or cleared meanwhile) or the app
     * rejected the intent, so the caller can fall back to opening the app.
     */
    fun performQuickAction(context: Context, key: String, action: QuickAction, replyText: String? = null): Boolean {
        val button = quickActions[key]?.get(action) ?: return false
        val intent = button.actionIntent ?: return false
        return runCatching {
            if (action == QuickAction.REPLY) {
                val inputs = button.remoteInputs?.takeIf { it.isNotEmpty() } ?: return false
                val fillIn = Intent()
                val results = Bundle()
                inputs.filter { it.allowFreeFormInput }.forEach { results.putCharSequence(it.resultKey, replyText.orEmpty()) }
                RemoteInput.addResultsToIntent(inputs, fillIn, results)
                if (Build.VERSION.SDK_INT >= 28) RemoteInput.setResultsSource(fillIn, RemoteInput.SOURCE_FREE_FORM_INPUT)
                intent.send(context, 0, fillIn)
            } else {
                intent.send()
            }
            true
        }.getOrDefault(false)
    }

    /** Clears just these notifications (the hub's "clear" button), leaving the
     * rest of each app's notifications in place. */
    fun clearKeys(keys: Collection<String>) {
        if (keys.isEmpty()) return
        listener?.let { service -> runCatching { service.cancelNotifications(keys.toTypedArray()) } }
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
        // Prefer whichever notification the cycling face was actually showing —
        // falling back to the newest whenever nothing was reported, or the
        // reported key belongs to a notification that's no longer in this action
        // (already cleared / superseded since it was last displayed).
        val preferredKey = displayedKeys[packageName]
        val intent = if (preferredKey != null && action.itemIntents.containsKey(preferredKey)) {
            action.itemIntents[preferredKey]
        } else {
            action.contentIntent
        }
        val opened = intent?.let { sendContentIntent(context, it) } ?: false
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
        quickActions = emptyMap()
        _images.value = emptyMap()
        _itemImages.value = emptyMap()
        displayedKeys = emptyMap()
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
            // Same non-summary rows summarizeNotifications hands out as
            // ConversationItem.notificationKey for the cycling face — so a key
            // reported as "currently displayed" always resolves here.
            itemIntents = list.filterNot { it.isGroupSummary }.associate { it.key to it.contentIntent },
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
