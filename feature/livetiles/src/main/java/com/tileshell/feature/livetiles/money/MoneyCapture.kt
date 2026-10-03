package com.tileshell.feature.livetiles.money

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification

/**
 * Turns newly posted notifications into money-hub transactions: bank SMS as
 * shown by the messages app, and payment apps' own "you paid" notices. Only
 * new posts are read (no SMS history), only while "read bank messages" is on,
 * and everything stays on this phone ([MoneyStore]).
 */
object MoneyCapture {
    // Recently handled (notification key + text), so a re-post of the same
    // notification isn't read twice. Bounded.
    private val seen = object : LinkedHashMap<String, Unit>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > 200
    }

    fun onPosted(context: Context, sbn: StatusBarNotification) {
        if (sbn.packageName == context.packageName) return
        if (!MoneyPrefs.current(context).readBankMessages) return
        // Only the SMS app and payment/banking apps: a chat saying "I paid Rs 500"
        // is not a transaction.
        val mail = com.tileshell.feature.livetiles.peopleCategoryFor(sbn.packageName) ==
            com.tileshell.feature.livetiles.PeopleCategory.MAIL
        if (!mail && !isMoneySource(context, sbn.packageName)) return
        val n = sbn.notification ?: return
        // A group summary repeats its children.
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        // From a mail app, only a bank or card email (the card-alert kind) counts.
        if (mail && !isBankMessage(title, text)) return
        val time = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        val seenKey = "${sbn.key}|${text.hashCode()}"
        synchronized(seen) {
            if (seen.containsKey(seenKey)) return
            seen[seenKey] = Unit
        }
        val txn = parseMoneyMessage(title, text, sbn.packageName, time) ?: return
        MoneyStore.add(context, txn)
    }

    private val SMS_APPS = setOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.miui.mms", "com.oneplus.mms", "com.truecaller",
    )

    private fun isMoneySource(context: Context, packageName: String): Boolean {
        if (packageName in SMS_APPS) return true
        val defaultSms = runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
        if (packageName == defaultSms) return true
        val label = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault("")
        return moneyAppKind(packageName, label) != null
    }
}
