package com.tileshell.feature.livetiles.shopping

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.tileshell.feature.livetiles.PeopleCategory
import com.tileshell.feature.livetiles.peopleCategoryFor

/** Where a notification's shopping meaning comes from: the store, whether it is food, and whether the title is the item. */
internal class ShoppingSource(val merchant: String, val food: Boolean, val titleIsItem: Boolean)

private val SMS_SHOPPING_APPS = setOf(
    "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
    "com.miui.mms", "com.oneplus.mms", "com.truecaller",
)

/**
 * Whether a notification can be about shopping at all, and for which store: it comes from a shopping, food or courier
 * app, or it is an SMS, email or chat that names a known store (a chat only when the store is who it is with). Pure.
 */
internal fun shoppingSourceOf(packageName: String, appLabel: String, title: String, text: String): ShoppingSource? {
    val appKinds = shoppingAppKinds(packageName, appLabel)
    if (appKinds.isNotEmpty()) {
        // A shopping app: the store is the app (its known name when it has one).
        val store = storeIn(appLabel)
        return ShoppingSource(store?.name ?: appLabel, ShoppingAppKind.FOOD in appKinds, titleIsItem = true)
    }
    val category = com.tileshell.feature.livetiles.peopleCategoryFor(packageName)
    val isSms = packageName in SMS_SHOPPING_APPS
    val isMail = category == com.tileshell.feature.livetiles.PeopleCategory.MAIL
    val isChat = category == com.tileshell.feature.livetiles.PeopleCategory.CHAT
    if (!isSms && !isMail && !isChat) return null
    // An SMS or an email counts only when it names a store the hub knows; a chat only when the store is who it is with.
    val store = storeIn(if (isChat) title else "$title $text") ?: return null
    return ShoppingSource(store.name, store.kind == ShoppingAppKind.FOOD, titleIsItem = false)
}

/**
 * True when the shopping hub takes this notification (an order update or a deal), so the people hub leaves it out and it
 * isn't shown in both. False when reading order messages is off. Pure apart from the cached setting.
 */
fun shoppingClaims(packageName: String, title: String, text: String, time: Long = 0L): Boolean {
    if (!ShoppingPrefs.readOrderMessagesCached()) return false
    val source = shoppingSourceOf(packageName, ShoppingCapture.cachedLabel(packageName), title, text) ?: return false
    return parseOrderMessage(title, text, packageName, source.merchant, source.food, time, source.titleIsItem) != null ||
        dealOf(title, text, packageName, source.merchant, source.food, time) != null
}

/**
 * Reads order updates ("out for delivery", "delivered") from new notifications as they arrive: those of
 * shopping, food and courier apps (the store is the app), and those of the SMS app and mail apps when they
 * name a store they know, and chat apps (WhatsApp) when the chat is with a store's business account. No SMS permission, no history: only what arrives after install, only while
 * "read order messages" is on, and everything stays on this phone ([ShoppingStore]).
 */
object ShoppingCapture {
    // Recently handled (notification key + text), so a re-post of the same notification isn't read twice. Bounded.
    private val seen = object : LinkedHashMap<String, Unit>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > 200
    }

    fun onPosted(context: Context, sbn: StatusBarNotification) {
        if (sbn.packageName == context.packageName) return
        // Ongoing and progress notifications (music, navigation, downloads) are never an order update.
        if (!sbn.isClearable) return
        if (!ShoppingPrefs.current(context).readOrderMessages) return
        val n = sbn.notification ?: return
        // A group summary repeats its children.
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return

        val source = shoppingSourceOf(sbn.packageName, appLabel(context, sbn.packageName), title, text) ?: return
        val merchant = source.merchant
        val food = source.food
        val titleIsItem = source.titleIsItem
        val time = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        val seenKey = "${sbn.key}|${text.hashCode()}"
        synchronized(seen) {
            if (seen.containsKey(seenKey)) return
            seen[seenKey] = Unit
        }
        val update = parseOrderMessage(title, text, sbn.packageName, merchant, food, time, titleIsItem)
        if (update != null) {
            ShoppingStore.update(context, update)
        } else {
            // Not an order update: a store's own promotion (its app, an SMS, a business chat) goes to the deals page.
            dealOf(title, text, sbn.packageName, merchant, food, time)?.let { ShoppingStore.addDeal(context, it) }
        }
    }

    // Labels by package: this runs for every notification of every app, on the listener's thread, and a
    // PackageManager lookup per post adds up. A label changes only on an app update, which restarts the process anyway.
    private val labels = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** An app's label once a notification of it has been read, else blank: lets the people hub recognise apps known only by name. */
    internal fun cachedLabel(packageName: String): String = labels[packageName].orEmpty()

    private fun appLabel(context: Context, packageName: String): String = labels.getOrPut(packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }
}
