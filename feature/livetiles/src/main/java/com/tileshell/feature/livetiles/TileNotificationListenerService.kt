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
        HubAppChoices.ensureLoaded(applicationContext)
        // This service keeps TileShell's process alive, which is what lets the
        // battery log hear screen on/off and plug/unplug as they happen.
        BatteryLog.ensureStarted(this)
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
        synchronized(refreshLock) { imageCache.clear() }
        NotificationCenter.unbindListener(this)
        NotificationCenter.clear()
        // Best-effort rebind; a no-op (and harmless) if access was actually revoked.
        runCatching { requestRebind(ComponentName(this, javaClass)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshSignals.tryEmit(Unit)
        sbn?.let { com.tileshell.feature.livetiles.money.MoneyCapture.onPosted(this, it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshSignals.tryEmit(Unit)
    }

    // Decoded images per notification, kept across refreshes (see [imagesFor]).
    // Every post/removal rebuilds the whole picture, and used to re-decode every
    // pending notification's sender photo and shared picture each time — the
    // newest per package twice — so one new message in a busy chat re-decoded
    // dozens of unchanged bitmaps. A battery diagnosis found 53% of TileShell's
    // CPU time happening with the screen off, which is this work (it runs on
    // every notification, day and night). Guarded by [refreshLock], since the
    // connect-time refresh can overlap a debounced one.
    private val imageCache = HashMap<String, NotificationImages>()
    private val refreshLock = Any()

    /** [sbn]'s images, decoded once per version of the notification: an
     * updated notification (new `postTime`) is decoded again, an unchanged
     * one never is. */
    private fun imagesFor(sbn: StatusBarNotification): NotificationImages =
        imageCache.getOrPut(notificationImageCacheKey(sbn.key, sbn.postTime)) { sbn.extractImages(this) }

    /** Always invoked off the main thread — see the class doc. */
    private fun refresh() = synchronized(refreshLock) {
        // activeNotifications throws if the listener is not connected — guard it.
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        val snapshot = summarizeNotifications(active.mapNotNull { it.toItem() })
        NotificationCenter.publish(snapshot)
        // Who messaged — the People Hub's "recently messaged" (see MessagedLog).
        MessagedLog.record(this, snapshot)
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
        // Forget notifications that are gone (or superseded by a newer version).
        val live = active.mapTo(HashSet()) { notificationImageCacheKey(it.key, it.postTime) }
        imageCache.keys.retainAll(live)
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
                val images = imagesFor(newest)
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
                        val images = imagesFor(sbn)
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
    val extras = n.extras
    val avatarFull = n.getLargeIcon()
        ?.let { icon -> runCatching { icon.loadDrawable(context)?.toBitmap() }.getOrNull() }
    // The shared photo: a big-picture bitmap, else (Android 12+ apps that pass an
    // Icon) the picture icon, else a large icon that is really a photo (an app like
    // Pinterest puts the pin there). A small large icon is just the sender's avatar.
    val picture = ((extras?.get(Notification.EXTRA_PICTURE) as? Bitmap)
        ?: (extras?.get(EXTRA_PICTURE_ICON) as? android.graphics.drawable.Icon)
            ?.let { icon -> runCatching { icon.loadDrawable(context)?.toBitmap() }.getOrNull() }
        ?: avatarFull?.takeIf { isPhotoSized(it.width, it.height) })
        ?.downscaleIfNeeded(MAX_NOTIFICATION_IMAGE_PX)
    val avatar = avatarFull?.downscaleIfNeeded(MAX_NOTIFICATION_IMAGE_PX)
    return NotificationImages(avatar = avatar, picture = picture)
}

/** `Notification.EXTRA_PICTURE_ICON` (API 31); a plain string so it also compiles and runs below that. */
private const val EXTRA_PICTURE_ICON = "android.pictureIcon"

/** A large icon this big is a photo, not an avatar. Pure. */
internal fun isPhotoSized(width: Int, height: Int): Boolean = maxOf(width, height) >= PHOTO_MIN_PX

internal const val PHOTO_MIN_PX = 400

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
        fullText = fullNotificationText(
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            messages = chatMessageTexts(extras),
            lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }.orEmpty(),
        ),
        messages = separateMessages(extras),
    )
}

/**
 * The separate messages inside one notification: a chat's recent messages
 * (MessagingStyle), else an inbox-style list's lines. Only worth showing as
 * rows when there are two or more.
 */
@Suppress("DEPRECATION")
private fun separateMessages(extras: android.os.Bundle): List<NotificationMessage> {
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
    val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
    val chat = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        ?.filterIsInstance<android.os.Bundle>()
        ?.mapNotNull { b ->
            val text = b.getCharSequence("text")?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return@mapNotNull null
            val person = b.getCharSequence("sender")?.toString()
                ?: (b.get("sender_person") as? android.os.Bundle)?.getCharSequence("name")?.toString()
            NotificationMessage(
                sender = messageSenderLabel(person, conversation ?: title, isGroup, title),
                text = text,
                time = b.getLong("time", 0L),
            )
        }
        .orEmpty()
    if (chat.isNotEmpty()) return chat
    return extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        ?.map { it.toString().trim() }?.filter { it.isNotEmpty() }
        ?.map { NotificationMessage(sender = null, text = it) }
        .orEmpty()
}

/** A chat notification's recent messages (MessagingStyle), oldest first. */
@Suppress("DEPRECATION")
private fun chatMessageTexts(extras: android.os.Bundle): List<String> =
    extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        ?.filterIsInstance<android.os.Bundle>()
        ?.mapNotNull { it.getCharSequence("text")?.toString() }
        .orEmpty()

private fun StatusBarNotification.toActionRow(): NotificationActionRow =
    NotificationActionRow(
        packageName = tilePackageName(),
        key = key,
        contentIntent = notification?.contentIntent,
        isClearable = isClearable,
        isGroupSummary = ((notification?.flags ?: 0) and Notification.FLAG_GROUP_SUMMARY) != 0,
        postTime = postTime,
    )

/** Cache key for one version of a notification's decoded images. Pure. */
internal fun notificationImageCacheKey(key: String, postTime: Long): String = "$key@$postTime"
