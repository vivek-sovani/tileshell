package com.tileshell.feature.livetiles.shopping

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.tileshell.feature.livetiles.PeopleCategory
import com.tileshell.feature.livetiles.peopleCategoryFor

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

    private val SMS_APPS = setOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.miui.mms", "com.oneplus.mms", "com.truecaller",
    )

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

        val appLabel = appLabel(context, sbn.packageName)
        val appKinds = shoppingAppKinds(sbn.packageName, appLabel)
        val merchant: String
        val food: Boolean
        var titleIsItem = true
        if (appKinds.isNotEmpty()) {
            // A shopping app: the store is the app (its known name when it has one).
            val store = storeIn(appLabel)
            merchant = store?.name ?: appLabel
            food = ShoppingAppKind.FOOD in appKinds
        } else {
            val isSms = sbn.packageName in SMS_APPS
            val category = peopleCategoryFor(sbn.packageName)
            val isMail = category == PeopleCategory.MAIL
            // Stores message from business chats too ("Amazon India" on WhatsApp): the chat's own name must be the store.
            val isChat = category == PeopleCategory.CHAT
            if (!isSms && !isMail && !isChat) return
            // An SMS or an email counts only when it names a store the hub knows; a chat only when the store is who it is with.
            val store = storeIn(if (isChat) title else "$title $text") ?: return
            titleIsItem = false
            merchant = store.name
            food = store.kind == ShoppingAppKind.FOOD
        }
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

    private fun appLabel(context: Context, packageName: String): String = labels.getOrPut(packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }
}
