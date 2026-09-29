package com.tileshell.feature.livetiles.money

/**
 * One bank or payment transaction, recognised from a notification (a bank SMS
 * shown by the messages app, or a payment app's own "you paid" notice).
 * Amounts are in paise so sums stay exact.
 */
data class MoneyTxn(
    val time: Long,
    val amountPaise: Long,
    val credit: Boolean,
    val counterparty: String,
    val account: String?,
    val bank: String?,
    val method: String?,
    val balancePaise: Long?,
    val sourcePackage: String,
    /** The notification's title (the bank's SMS sender id, or the payment app's heading). */
    val sender: String = "",
    /** The full message, shown when the transaction is tapped. */
    val message: String = "",
)

private val AMOUNT = Regex("""(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
private val BALANCE = Regex(
    """(?:avl|avbl|available|clr|closing|a/c)\.?\s*(?:bal|balance|bal\.)\s*(?:is|:|-)?\s*(?:rs\.?|inr|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
    RegexOption.IGNORE_CASE,
)
private val ACCOUNT = Regex(
    """(?:a/c|ac|acct|account|card|a/c no\.?)\s*(?:no\.?|number|ending|ending with)?\s*[x*.\s]*([0-9]{3,6})\b""",
    RegexOption.IGNORE_CASE,
)
private val MASKED = Regex("""\b[xX*]{2,}([0-9]{3,6})\b""")

private val DEBIT_WORDS = listOf("debited", "spent", "paid", "sent", "withdrawn", "purchase", "payment of", "transferred to", "dr.", " dr ")
private val CREDIT_WORDS = listOf("credited", "received", "deposited", "refund", "refunded", "cr.", " cr ", "added to your")

/** Messages that mention money but aren't a completed transaction. */
private val REJECT_WORDS = listOf(
    "otp", "one time password", "verification code", "is due", "due on", "due date", "minimum amount due",
    "will be debited", "will be credited", "request", "requested", "pre-approved", "preapproved", "loan offer",
    "offer", "cashback of up to", "win ", "reward points", "emi of", "bill of", "declined", "failed",
    "reversed", "unsuccessful", "not been",
)

private val BANKS = listOf(
    "hdfc" to "hdfc", "icici" to "icici", "sbi" to "sbi", "state bank" to "sbi", "axis" to "axis", "kotak" to "kotak",
    "bank of baroda" to "bob", "bob" to "bob", "pnb" to "pnb", "punjab national" to "pnb", "canara" to "canara",
    "union bank" to "union", "idfc" to "idfc", "yes bank" to "yes", "indusind" to "indusind", "federal" to "federal",
    "idbi" to "idbi", "au bank" to "au", "bank of india" to "boi", "indian bank" to "indian", "rbl" to "rbl",
    "paytm payments" to "paytm", "airtel payments" to "airtel",
)

/**
 * Reads a transaction out of a notification's [title] and [text], or null
 * when it isn't one: no amount, no debit/credit word, or an OTP, a due notice,
 * an offer or a pending request. Pure, unit-tested.
 */
fun parseMoneyTxn(title: String, text: String, sourcePackage: String, time: Long): MoneyTxn? {
    val body = text.trim()
    if (body.isEmpty()) return null
    val all = "$title $body".lowercase()
    if (REJECT_WORDS.any { it in all }) return null

    val amountMatch = AMOUNT.find(body) ?: return null
    val amount = rupeesToPaise(amountMatch.groupValues[1]) ?: return null
    if (amount <= 0) return null

    val lower = " ${body.lowercase()} "
    val debitAt = DEBIT_WORDS.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
    val creditAt = CREDIT_WORDS.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
    val credit = when {
        debitAt == null && creditAt == null -> return null
        debitAt == null -> true
        creditAt == null -> false
        else -> creditAt < debitAt
    }

    val balance = BALANCE.find(body)?.let { rupeesToPaise(it.groupValues[1]) }
    val account = ACCOUNT.find(body)?.groupValues?.get(1)?.takeLast(4)
        ?: MASKED.find(body)?.groupValues?.get(1)?.takeLast(4)
    val bank = BANKS.firstOrNull { (key, _) -> key in all }?.second
    val method = when {
        "upi" in lower || "vpa" in lower -> "upi"
        "neft" in lower -> "neft"
        "imps" in lower -> "imps"
        "rtgs" in lower -> "rtgs"
        "atm" in lower -> "atm"
        "card" in lower -> "card"
        else -> null
    }
    return MoneyTxn(
        time = time,
        amountPaise = amount,
        credit = credit,
        counterparty = counterpartyOf(body, credit, title),
        account = account,
        bank = bank,
        method = method,
        balancePaise = balance,
        sourcePackage = sourcePackage,
        sender = title.trim(),
        message = body,
    )
}

/**
 * A bank or card message about money: a transaction, or an alert quoting an
 * amount against an account or card (balance, due, declined, an OTP for a
 * payment). The People hub leaves these out of "what's new" — they belong
 * in the Money hub. Pure, unit-tested.
 */
fun isBankMessage(sender: String, text: String): Boolean {
    if (parseMoneyTxn(sender, text, "", 0L) != null) return true
    val all = "$sender $text"
    if (!AMOUNT.containsMatchIn(all)) return false
    val lower = all.lowercase()
    return ACCOUNT.containsMatchIn(all) || MASKED.containsMatchIn(all) ||
        " upi" in " $lower" || BANKS.any { (key, _) -> key in lower } && "bank" in lower
}

private val TO_NAME = Regex("""\b(?:to|at|towards|for)\s+(?:vpa\s+)?([A-Za-z0-9@._&' -]{2,40})""", RegexOption.IGNORE_CASE)
private val FROM_NAME = Regex("""\bfrom\s+(?:vpa\s+)?([A-Za-z0-9@._&' -]{2,40})""", RegexOption.IGNORE_CASE)
private val BY_NAME = Regex("""\bby\s+(?:vpa\s+)?([A-Za-z0-9@._&' -]{2,40})""", RegexOption.IGNORE_CASE)
private val NAME_STOP = Regex("""\s+(?:on|via|ref|upi|a/c|ac|acct|account|avl|avbl|bal|dated|using|with|txn|imps|neft|rtgs|if|not|info|in|of|is)\b.*|[.,;:(\n].*""", RegexOption.IGNORE_CASE)

/** Who the money went to or came from, best effort; falls back to the notification title. */
internal fun counterpartyOf(body: String, credit: Boolean, title: String): String {
    val match = if (credit) FROM_NAME.find(body) ?: BY_NAME.find(body) else TO_NAME.find(body)
    val name = match?.groupValues?.get(1)
        ?.replace(NAME_STOP, "")
        ?.trim()
        ?.takeIf { it.length >= 2 && !it.matches(Regex("""[xX*0-9 ]+""")) && !isAccountWord(it) }
    return (name ?: title.trim().ifEmpty { if (credit) "received" else "payment" }).take(40).lowercase()
}

private fun isAccountWord(s: String): Boolean {
    val l = s.lowercase()
    return l.startsWith("a/c") || l.startsWith("your") || l.startsWith("ac ") || l.startsWith("account") || l.startsWith("card")
}

/** "1,23,456.7" → 12345670 paise. */
internal fun rupeesToPaise(s: String): Long? {
    val clean = s.replace(",", "")
    val parts = clean.split(".")
    val rupees = parts[0].toLongOrNull() ?: return null
    val paise = parts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0L
    return rupees * 100 + paise
}

/** "₹1,23,456" / "₹450.50" — Indian digit grouping. */
fun formatRupees(paise: Long): String {
    val rupees = paise / 100
    val rest = paise % 100
    val digits = rupees.toString()
    val grouped = if (digits.length <= 3) {
        digits
    } else {
        val last3 = digits.takeLast(3)
        val head = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        "$head,$last3"
    }
    return if (rest == 0L) "₹$grouped" else "₹$grouped.${rest.toString().padStart(2, '0')}"
}

/** Window in which the same amount and direction counts as the same payment. */
const val MONEY_DUPLICATE_WINDOW_MS = 5 * 60_000L

/**
 * True when [txn] repeats one already in [existing]: the bank's SMS and the
 * payment app both notify about one payment, or the same notification is
 * posted again.
 */
fun isDuplicateTxn(txn: MoneyTxn, existing: List<MoneyTxn>): Boolean = existing.any {
    it.amountPaise == txn.amountPaise && it.credit == txn.credit &&
        kotlin.math.abs(it.time - txn.time) <= MONEY_DUPLICATE_WINDOW_MS
}

enum class MoneyAppKind { PAYMENT, BANK }

private val PAYMENT_PACKAGES = setOf(
    "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp",
    "com.dreamplug.androidapp", "com.mobikwik_new", "com.freecharge.android", "com.google.android.apps.walletnfcrel",
    "com.samsung.android.spay", "com.paypal.android.p2pmobile", "com.amazon.mShop.android.shopping",
    "in.amazon.mShop.android.shopping", "money.jupiter", "com.bharatpe.app", "com.whatsapp.w4b.pay",
    "com.axis.mobile.upi", "com.sbi.upi", "com.naviapp", "com.slice", "com.fampay.in", "com.upi.axispay",
)
private val PAYMENT_WORDS = listOf("pay", "wallet", "upi", "bhim", "cred", "mobikwik", "freecharge")
private val BANK_WORDS = listOf(
    "bank", "yono", "imobile", "icici", "hdfc", "kotak", "axis mobile", "bob world", "pnb one", "canara", "fedmobile",
    "indusind", "idfc", "union", "ippb", "iob", "uco", "baroda", "sbi",
)

/**
 * Whether an installed app is a payment/wallet app, a banking app, or neither,
 * from a known package list plus label words. A known payment package wins
 * over a bank-sounding label. Pure, unit-tested.
 */
fun moneyAppKind(packageName: String, label: String): MoneyAppKind? {
    if (packageName in PAYMENT_PACKAGES) return MoneyAppKind.PAYMENT
    val l = label.lowercase()
    fun hasWord(w: String) = Regex("""(^|[^a-z])${Regex.escape(w)}([^a-z]|$)""").containsMatchIn(l)
    if (BANK_WORDS.any(::hasWord)) return MoneyAppKind.BANK
    if (PAYMENT_WORDS.any(::hasWord)) return MoneyAppKind.PAYMENT
    return null
}
