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
    /** A card alert, not money moving: a statement, bill due or card payment
     * received notice. Listed under cards, left out of totals. */
    val alert: Boolean = false,
)

/** A credit or debit card transaction, or a card alert. Pure, unit-tested. */
fun isCardTxn(t: MoneyTxn): Boolean {
    if (t.alert || t.method == "card") return true
    val l = t.message.lowercase()
    return CARD_HINTS.any { it in l }
}

private val CARD_HINTS = listOf("credit card", "debit card", "card ending", "card no", "card xx", "card **", "cc ending")

/** A card issuer confirming a bill payment ("payment … received towards your … card"). */
private fun isCardPaymentReceived(lower: String): Boolean =
    "payment" in lower && ("received" in lower || "thank you" in lower) &&
        (CARD_HINTS.any { it in lower } || TOWARDS_CARD.containsMatchIn(lower))

/** "towards your SBI card", "towards your ICICI Bank credit card". */
private val TOWARDS_CARD = Regex("""towards\s+(?:your\s+)?(?:[a-z]+\s+){0,3}card""")

/**
 * A notification read as a transaction, or else as a card alert (statement,
 * bill due, payment received), or null — what [MoneyCapture] stores.
 */
fun parseMoneyMessage(title: String, text: String, sourcePackage: String, time: Long): MoneyTxn? =
    parseMoneyTxn(title, text, sourcePackage, time) ?: parseCardAlert(title, text, sourcePackage, time)

/** The parts of the cards section: what was spent, bills due, bills paid. */
enum class CardKind { SPEND, DUE, PAYMENT }

/** A card bill paid from a bank account or a payment app ("…debited towards your credit card…"). */
private val BILL_PAY_HINTS = listOf(
    "card bill", "cc bill", "credit card payment", "credit card bill", "towards your credit card",
    "towards credit card", "towards your card", "payment to credit card", "payment to your credit card",
    "credit card dues", "card dues",
)

/**
 * Which part of the cards section [t] belongs to: a statement or bill-due
 * message is DUE, a "payment received" alert or a bank / app message paying
 * a card bill is PAYMENT (moving your own money, not spending), anything
 * else on a card is a SPEND. Pure, unit-tested.
 */
fun cardKind(t: MoneyTxn): CardKind = when {
    t.alert && t.counterparty == "card payment received" -> CardKind.PAYMENT
    t.alert -> CardKind.DUE
    t.message.lowercase().let { m -> BILL_PAY_HINTS.any { it in m } || TOWARDS_CARD.containsMatchIn(m) } -> CardKind.PAYMENT
    else -> CardKind.SPEND
}

/** A card bill payment: not spending, so left out of every "spent" total. */
fun isCardBillPayment(t: MoneyTxn): Boolean = isCardTxn(t) && cardKind(t) == CardKind.PAYMENT

/** Several messages about one bill, shown as one row: the newest, and how many there were. */
data class CardGroup(val latest: MoneyTxn, val members: List<MoneyTxn>)

/** Reminders for one bill: same card, same amount, within this long of each other. */
const val DUE_GROUP_WINDOW_MS = 35L * 86_400_000L

/** One payment told twice — the bank's debit and the card's "received" — within this long. */
const val PAYMENT_GROUP_WINDOW_MS = 3L * 86_400_000L

/**
 * Card messages as rows, newest first. Bills due: a statement and the
 * reminders after it are one bill — same card, and the same due date (or,
 * when a message names none, the same amount), within [DUE_GROUP_WINDOW_MS]
 * (next month's bill, even of the same amount, is its own). A reminder that
 * quotes the minimum or what's left still joins its statement by due date.
 * Payments:
 * the same amount within [PAYMENT_GROUP_WINDOW_MS] is one row (the bank
 * names its account, the card its own number, so the amount is what
 * matches). Spends stay one row each. Pure, unit-tested.
 */
fun groupCardMessages(txns: List<MoneyTxn>, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<CardGroup> {
    val groups = ArrayList<MutableList<MoneyTxn>>()
    val dueDates = HashMap<MoneyTxn, java.time.LocalDate?>()
    fun due(t: MoneyTxn) = dueDates.getOrPut(t) {
        dueDateOf(t.message, java.time.Instant.ofEpochMilli(t.time).atZone(zone).toLocalDate())
    }
    fun sameBill(a: MoneyTxn, b: MoneyTxn): Boolean {
        if (cardLabel(a) != cardLabel(b)) return false
        val da = due(a)
        val db = due(b)
        return if (da != null && db != null) da == db else a.amountPaise == b.amountPaise
    }
    for (t in txns.sortedByDescending { it.time }) {
        val kind = cardKind(t)
        val group = when (kind) {
            CardKind.SPEND -> null
            CardKind.DUE -> groups.firstOrNull { g ->
                cardKind(g.first()) == CardKind.DUE && g.any { sameBill(it, t) } &&
                    g.last().time - t.time <= DUE_GROUP_WINDOW_MS
            }
            CardKind.PAYMENT -> groups.firstOrNull { g ->
                val first = g.first()
                cardKind(first) == CardKind.PAYMENT && first.amountPaise == t.amountPaise &&
                    g.last().time - t.time <= PAYMENT_GROUP_WINDOW_MS
            }
        }
        if (group != null) group += t else groups += mutableListOf(t)
    }
    return groups.map { CardGroup(it.first(), it.toList()) }
}

/** The card a due message is about: its last digits, else its bank. */
private fun cardLabel(t: MoneyTxn): String = t.account ?: t.bank.orEmpty()

private val CARD_ALERT_OTP = listOf("otp", "one time password", "verification code")

/**
 * A credit card statement, bill-due or payment-received message, which
 * [parseMoneyTxn] rejects as not a transaction: kept as an [MoneyTxn.alert]
 * for the cards section. Needs a card mention and an amount. Pure, unit-tested.
 */
fun parseCardAlert(title: String, text: String, sourcePackage: String, time: Long): MoneyTxn? {
    val body = text.trim()
    if (body.isEmpty()) return null
    val lower = body.lowercase()
    val cardish = CARD_HINTS.any { it in lower } || " cc " in " $lower " || TOWARDS_CARD.containsMatchIn(lower) ||
        ("card" in lower && (MASKED.containsMatchIn(body) || ACCOUNT.containsMatchIn(body)))
    if (!cardish) return null
    if (CARD_ALERT_OTP.any { it in lower }) return null
    val kind = when {
        "statement" in lower -> "statement"
        "payment" in lower && ("received" in lower || "credited" in lower || "thank you" in lower) -> "card payment received"
        "due" in lower -> "bill due"
        else -> return null
    }
    // The total due, not the minimum, when both are quoted.
    val total = TOTAL_DUE.find(body)?.let { rupeesToPaise(it.groupValues[1]) }
    val amount = total ?: AMOUNT.find(body)?.let { rupeesToPaise(it.groupValues[1]) } ?: return null
    if (amount <= 0) return null
    val all = "$title $body".lowercase()
    return MoneyTxn(
        time = time,
        amountPaise = amount,
        credit = kind == "card payment received",
        counterparty = kind,
        account = ACCOUNT.find(body)?.groupValues?.get(1)?.takeLast(4) ?: MASKED.find(body)?.groupValues?.get(1)?.takeLast(4),
        bank = BANKS.firstOrNull { (key, _) -> key in all }?.second,
        method = "card",
        balancePaise = null,
        sourcePackage = sourcePackage,
        sender = title.trim(),
        message = body,
        alert = true,
    )
}

private val TOTAL_DUE = Regex(
    """(?:total\s+(?:amount\s+)?due|total\s+outstanding|statement\s+(?:amount|balance))\s*(?:of|is|:|-)?\s*(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
    RegexOption.IGNORE_CASE,
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

private val DEBIT_WORDS = listOf(
    "debited", "spent", "paid", "sent", "withdrawn", "purchase", "payment of", "transferred to", "dr.", " dr ",
    // Card alert emails: "…Credit Card XX1234 has been used for a transaction of INR 450.00…"
    "has been used for", "used for a transaction",
)
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
    // "Payment of Rs 800 received towards your SBI Card": a card bill paid, which
    // parseCardAlert reads — not a spend ("payment of" would make it one).
    if (isCardPaymentReceived(body.lowercase())) return null

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

private val ACCOUNT_WORDS = listOf("credit card", "debit card", "a/c", "bank account", "savings account", "credit cards")
private val ALERT_WORDS = listOf("transaction", "debited", "credited", "spent", "statement", "payment", "alert", "balance")

/**
 * A bank or card message about money: a transaction, or an alert quoting an
 * amount against an account or card (balance, due, declined, an OTP for a
 * payment). The People hub leaves these out of "what's new" — they belong
 * in the Money hub. Pure, unit-tested.
 */
fun isBankMessage(sender: String, text: String): Boolean {
    if (parseMoneyTxn(sender, text, "", 0L) != null) return true
    // A card or account alert whose preview has no amount — an email's subject,
    // like "Transaction alert for your ICICI Bank Credit Card".
    val l = "$sender $text".lowercase().replace('_', ' ')
    if (ACCOUNT_WORDS.any { it in l } && ALERT_WORDS.any { it in l }) return true
    val all = "$sender $text"
    if (!AMOUNT.containsMatchIn(all)) return false
    val lower = all.lowercase()
    return ACCOUNT.containsMatchIn(all) || MASKED.containsMatchIn(all) ||
        " upi" in " $lower" || BANKS.any { (key, _) -> key in lower } && "bank" in lower
}

private val INFO_NAME = Regex("""\bInfo:\s*([^.\n]{2,40})""", RegexOption.IGNORE_CASE)
private val TO_NAME = Regex("""\b(?:to|at|towards|for)\s+(?:vpa\s+)?([A-Za-z0-9@._&' -]{2,40})""", RegexOption.IGNORE_CASE)
private val FROM_NAME = Regex("""\bfrom\s+(?:vpa\s+)?([A-Za-z0-9@._&' -]{2,40})""", RegexOption.IGNORE_CASE)
private val BY_NAME = Regex("""\bby\s+(?:vpa\s+)?([A-Za-z0-9@._&' -]{2,40})""", RegexOption.IGNORE_CASE)
private val NAME_STOP = Regex("""\s+(?:on|via|ref|upi|a/c|ac|acct|account|avl|avbl|bal|dated|using|with|txn|imps|neft|rtgs|if|not|info|in|of|is)\b.*|[.,;:(\n].*""", RegexOption.IGNORE_CASE)

/** Who the money went to or came from, best effort; falls back to the notification title. */
internal fun counterpartyOf(body: String, credit: Boolean, title: String): String {
    // Card alert emails name the merchant after "Info:" ("Info: AMAZON PAY IN E COMMERCE.").
    INFO_NAME.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 2 }?.let { return it.take(40).lowercase() }
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
    it.amountPaise == txn.amountPaise && it.credit == txn.credit && it.alert == txn.alert &&
        kotlin.math.abs(it.time - txn.time) <= MONEY_DUPLICATE_WINDOW_MS
}

enum class MoneyAppKind { PAYMENT, BANK, CARD }

private val PAYMENT_PACKAGES = setOf(
    "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp",
    "com.mobikwik_new", "com.freecharge.android", "com.google.android.apps.walletnfcrel",
    "com.samsung.android.spay", "com.paypal.android.p2pmobile", "com.amazon.mShop.android.shopping",
    "in.amazon.mShop.android.shopping", "money.jupiter", "com.bharatpe.app", "com.whatsapp.w4b.pay",
    "com.axis.mobile.upi", "com.sbi.upi", "com.naviapp", "com.slice", "com.fampay.in", "com.upi.axispay",
)
private val PAYMENT_WORDS = listOf("pay", "wallet", "upi", "bhim", "mobikwik", "freecharge")
/** CRED (card bills) and card issuers' own apps. */
private val CARD_PACKAGES = setOf("com.dreamplug.androidapp")
private val CARD_BRAND_WORDS = listOf(
    "cred", "onecard", "bobcard", "amex", "american express", "scapia", "sbi card", "mycards", "my cards",
    "credit card", "credit cards", "debit card",
)
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
    if (packageName in CARD_PACKAGES) return MoneyAppKind.CARD
    if (packageName in PAYMENT_PACKAGES) return MoneyAppKind.PAYMENT
    val l = label.lowercase()
    fun hasWord(w: String) = Regex("""(^|[^a-z])${Regex.escape(w)}([^a-z]|$)""").containsMatchIn(l)
    // A card app: a card brand, or "card(s)" together with a bank name
    // ("HDFC Bank MyCards"); a bare "card" (a card-scanner app) doesn't count.
    if (CARD_BRAND_WORDS.any(::hasWord)) return MoneyAppKind.CARD
    if ((hasWord("card") || hasWord("cards")) && BANK_WORDS.any(::hasWord)) return MoneyAppKind.CARD
    if (BANK_WORDS.any(::hasWord)) return MoneyAppKind.BANK
    if (PAYMENT_WORDS.any(::hasWord)) return MoneyAppKind.PAYMENT
    return null
}

private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

/** Where a due date is named: "due on", "due by", "due date:", "on or before", "pay by". */
private val DUE_CUE = Regex("""(?:due\s+(?:date\s*)?(?:on|by|is|:|-)?|on\s+or\s+before|pay(?:ment)?\s+by|before)\s*:?\s*""", RegexOption.IGNORE_CASE)

/** 15-Oct-26, 15 Oct 2026, 15th October, 15-OCT. */
private val DAY_MONTH = Regex("""^(\d{1,2})(?:st|nd|rd|th)?[\s\-/.,]*([A-Za-z]{3,9})\.?(?:[\s\-/.,]*(\d{4}|\d{2}))?""")

/** Oct 15, 2026 / October 15. */
private val MONTH_DAY = Regex("""^([A-Za-z]{3,9})\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s*(\d{4}))?""")

/** 15/10/2026, 15-10-26, 15.10.2026 (Indian day-first). */
private val NUMERIC = Regex("""^(\d{1,2})[/\-.](\d{1,2})(?:[/\-.](\d{4}|\d{2}))?""")

/** 2026-10-15. */
private val ISO = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""")

/**
 * The due date a bill message names ("payment due on 15-Oct-26", "due by
 * 15/10/2026", "on or before 15th Oct"), or null. A date without a year is
 * the next one on or after a few weeks before [received]. Pure, unit-tested.
 */
fun dueDateOf(message: String, received: java.time.LocalDate): java.time.LocalDate? {
    for (cue in DUE_CUE.findAll(message)) {
        val rest = message.substring(cue.range.last + 1).take(30).trim()
        parseDate(rest, received)?.let { return it }
    }
    return null
}

private fun parseDate(s: String, received: java.time.LocalDate): java.time.LocalDate? {
    fun month(name: String): Int? = MONTHS.indexOf(name.lowercase().take(3)).takeIf { it >= 0 }?.plus(1)
    fun year(y: String?): Int? = y?.toIntOrNull()?.let { if (it < 100) 2000 + it else it }
    val (d, m, y) = ISO.find(s)?.let { Triple(it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
        ?: DAY_MONTH.find(s)?.let { r -> month(r.groupValues[2])?.let { Triple(r.groupValues[1].toInt(), it, year(r.groupValues[3].ifEmpty { null })) } }
        ?: MONTH_DAY.find(s)?.let { r -> month(r.groupValues[1])?.let { Triple(r.groupValues[2].toInt(), it, year(r.groupValues[3].ifEmpty { null })) } }
        ?: NUMERIC.find(s)?.let { Triple(it.groupValues[1].toInt(), it.groupValues[2].toInt(), year(it.groupValues[3].ifEmpty { null })) }
        ?: return null
    if (m !in 1..12 || d !in 1..31) return null
    return runCatching {
        if (y != null) {
            java.time.LocalDate.of(y, m, d)
        } else {
            // No year: the first such date from a few weeks before the message on.
            val start = received.minusDays(DUE_DATE_LOOKBACK_DAYS)
            val thisYear = java.time.LocalDate.of(start.year, m, d)
            if (thisYear.isBefore(start)) thisYear.plusYears(1) else thisYear
        }
    }.getOrNull()
}

private const val DUE_DATE_LOOKBACK_DAYS = 20L

/**
 * A bill due, read from its statement and reminders: the amount (the
 * statement's total when there is one, else the newest reminder's), the due
 * date, the statement if it came, how many reminders, and whether a card
 * payment since covers it.
 */
data class DueBill(
    val group: CardGroup,
    val dueDate: java.time.LocalDate?,
    val paid: Boolean,
    val amountPaise: Long = group.latest.amountPaise,
    val statement: MoneyTxn? = null,
    val reminders: Int = group.members.size,
)

/**
 * The bills due in [groups] (from [groupCardMessages]) in the order to deal
 * with them: unpaid ones by due date, soonest first (those without a date
 * after, newest first), then paid ones. A bill is paid when a card payment
 * of at least its amount came after its first reminder (on the same card,
 * when both name one). Pure, unit-tested.
 */
fun dueBills(groups: List<CardGroup>, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<DueBill> {
    val payments = groups.filter { cardKind(it.latest) == CardKind.PAYMENT }.flatMap { it.members }
    fun date(t: Long) = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
    val bills = groups.filter { cardKind(it.latest) == CardKind.DUE }.map { g ->
        val due = g.members.firstNotNullOfOrNull { dueDateOf(it.message, date(it.time)) }
        val statement = g.members.filter { it.counterparty == "statement" }.maxByOrNull { it.time }
        val amount = statement?.amountPaise ?: g.latest.amountPaise
        val since = g.members.minOf { it.time }
        val paid = payments.any { p ->
            p.time >= since && p.amountPaise >= amount &&
                (p.account == null || g.latest.account == null || !p.alert || p.account == g.latest.account)
        }
        DueBill(g, due, paid, amount, statement, g.members.count { it !== statement })
    }
    return bills.sortedWith(
        compareBy<DueBill>({ it.paid }, { it.dueDate == null }, { it.dueDate }, { -it.group.latest.time }),
    )
}
