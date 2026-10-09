package com.tileshell.feature.livetiles.shopping

/**
 * The shopping hub's logic: reading an order's progress out of a notification ("out for delivery", "delivered: …"),
 * merging each update into one card per order, and the lists the hub and tile show. All pure and unit-tested.
 */

/** How far an order has got. [step] 0..3 is the progress line; cancelled and returned end it. */
enum class OrderStatus(val step: Int, val label: String) {
    PLACED(0, "placed"),
    SHIPPED(1, "shipped"),
    OUT_FOR_DELIVERY(2, "out for delivery"),
    DELIVERED(3, "delivered"),
    CANCELLED(-1, "cancelled"),
    RETURNED(-1, "returned"),
    ;

    val closed: Boolean get() = this == DELIVERED || this == CANCELLED || this == RETURNED
}

/** One order as the hub shows it. */
data class Order(
    val key: String,
    val merchant: String,
    val title: String,
    val status: OrderStatus,
    /** "by 8 pm", "in 12 min", "today"…, when the message said so and the order is still open. */
    val eta: String?,
    /** A delivery OTP, kept until the order closes. */
    val otp: String?,
    val sourcePackage: String,
    val food: Boolean,
    val firstSeen: Long,
    val updated: Long,
    /** The latest message about it in full (the notification's text), for expanding a row; blank for older entries. */
    val message: String = "",
)

/** What one notification says about an order. */
data class OrderUpdate(
    val ref: String?,
    val title: String?,
    val status: OrderStatus,
    val eta: String?,
    val otp: String?,
    val merchant: String,
    val sourcePackage: String,
    val food: Boolean,
    val time: Long,
    val message: String = "",
)

/** The whole message a notification carried: its text, with the title above it when that adds something. Pure. */
internal fun messageOf(title: String, text: String): String {
    val t = title.trim()
    val b = text.trim()
    val joined = when {
        b.isEmpty() || b.equals(t, ignoreCase = true) -> t
        t.isEmpty() || b.startsWith(t, ignoreCase = true) -> b
        else -> "$t\n$b"
    }
    return joined.take(1000)
}

private val NOT_YET_DELIVERED = Regex("""will be delivered|to be delivered|be delivered (?:by|on|today|tomorrow)|expected (?:to be )?deliver|delivery (?:is )?expected|delivered by|wird (?:voraussichtlich )?(?:am |bis )|sera livr[ée]e? (?:le|demain|entre)|ser[áa] entregado|sar[àa] consegnat|wordt (?:morgen|vandaag) bezorgd|zal (?:morgen|vandaag) worden bezorgd""")
private val DELIVERED = Regex("""(?:has been|was|is|been|successfully) delivered|delivered (?:to|at|on|successfully|by)|(?:^|[.:!]\s*)delivered\b|order delivered|handed (?:it )?over|your package (?:was |has been )?(?:left|delivered)|your (?:package|parcel|order) has arrived|was left (?:at|in|on|with)|left (?:at|in|on|with) (?:your|the) (?:front|door|porch|mailbox|neighbo)|(?:wurde|ist|erfolgreich) zugestellt|wurde geliefert|ist angekommen|a [ée]t[ée] livr[ée]e?|est livr[ée]e?|livr[ée]e? (?:le|à|chez)|ha sido entregado|fue entregado|entregado en|[èe] stato consegnato|consegnato|is bezorgd|werd bezorgd|pakket bezorgd""")
private val OUT_FOR_DELIVERY = Regex(
    """out for delivery|arriving today|arrives today|will be delivered today|on (?:its|the) way to you|is on the way|is on its way to you|""" +
        """(?:delivery )?(?:partner|agent|executive|rider|person) (?:is |has )?(?:on the way|nearby|reaching|arriving|assigned|picked)|reaching you|arriving in \d+|picked up your order|""" +
        // The same in the languages of the main European markets, and the usual US wording.
        """out for delivery today|delivery attempt|driver is (?:nearby|\d+ stops? away)|your driver|""" +
        """in zustellung|wird heute (?:geliefert|zugestellt)|zustellung heute|heute (?:bei ihnen|zugestellt)|""" +
        """en cours de livraison|livraison pr[ée]vue aujourd|sera livr[ée]e? aujourd|""" +
        """en reparto|llegar[áa] hoy|reparto hoy|in consegna|consegna prevista oggi|oggi in consegna|""" +
        """onderweg naar u|wordt vandaag bezorgd|bezorger is onderweg""",
)
private val SHIPPED = Regex("""(?:has been |is |was |just )?(?:shipped|dispatched)\b|in transit|left the (?:hub|facility|warehouse)|handed over to (?:the )?(?:courier|delivery)|on its way|has shipped|was shipped|label created|picked up by (?:the )?carrier|departed (?:from )?(?:facility|hub)|arrived at (?:facility|hub)|versandt|versendet|auf dem weg|exp[ée]di[ée]e?|pris en charge|enviado|en camino|spedito|in viaggio|verzonden|onderweg""")
private val PLACED = Regex(
    """order (?:has been |is )?(?:placed|confirmed|received|accepted)|thank you for (?:your )?order|we(?:'|’)?ve received your order|""" +
        """your order (?:is|has been) (?:confirmed|placed|accepted)|being prepared|preparing your order|order summary|""" +
        // Amazon's own wording: "Ordered: <item>", "You've ordered…", "Arriving tomorrow" / "Arriving Thursday", and
        // quick-commerce ones (Amazon Now, Blinkit, Zepto): "packing your order", "will arrive in 10 mins".
        """\bordered\s*[:\-]|you(?:'ve| have)? ordered|thanks? for (?:your )?order(?:ing)?|thank you for (?:shopping|ordering)|""" +
        """(?:order|items?) (?:is |are )?(?:being )?packed|packing your order|""" +
        """(?:will |to )(?:arrive|be delivered) in \d+|arriving (?:tomorrow|on |by |\w+day\b|between)|arrives (?:tomorrow|on |by |\w+day\b)|""" +
        """order (?:#\S+ )?(?:is )?(?:confirmed|received)|we got your order|thanks for shopping|your order was placed|""" +
        """bestellung (?:ist )?(?:eingegangen|best[äa]tigt)|vielen dank f[üu]r ihre bestellung|""" +
        """commande (?:bien )?(?:re[çc]ue|confirm[ée]e|valid[ée]e)|merci pour votre commande|""" +
        """pedido (?:recibido|confirmado)|gracias por tu pedido|""" +
        """ordine (?:ricevuto|confermato)|grazie per il tuo ordine|""" +
        """bestelling (?:ontvangen|bevestigd)|bedankt voor je bestelling""",
)
private val CANCELLED = Regex("""order (?:has been |was |is )?cancel+ed|cancel+ation of your order|your order (?:has been |was )?cancel+ed|bestellung (?:wurde )?storniert|storniert|commande (?:a [ée]t[ée] )?annul[ée]e|pedido (?:ha sido )?cancelado|ordine (?:[èe] stato )?annullato|bestelling (?:is )?geannuleerd""")
private val RETURNED = Regex("""return (?:has been |is )?(?:picked up|completed|successful|received)|has been returned|returned to (?:seller|warehouse)|r[üu]cksendung|retour (?:re[çc]u|effectu)|devoluci[óo]n (?:recibida|completada)|reso (?:ricevuto|completato)|retour ontvangen""")
private val PROMO = Regex("""\b(?:offer|offers|% off|sale|coupon|cashback|discount|deal|deals|reorder|order now|order again|use code|win|promo|promotion|save up to|angebot|rabatt|gutschein|soldes|promotion|r[ée]duction|oferta|descuento|cup[óo]n|sconto|offerta|aanbieding|korting)\b""")

/** The status a message names, strongest first, or null when it names none. */
internal fun statusOf(lower: String): OrderStatus? = when {
    CANCELLED.containsMatchIn(lower) -> OrderStatus.CANCELLED
    RETURNED.containsMatchIn(lower) -> OrderStatus.RETURNED
    DELIVERED.containsMatchIn(lower) && !NOT_YET_DELIVERED.containsMatchIn(lower) -> OrderStatus.DELIVERED
    OUT_FOR_DELIVERY.containsMatchIn(lower) -> OrderStatus.OUT_FOR_DELIVERY
    SHIPPED.containsMatchIn(lower) -> OrderStatus.SHIPPED
    PLACED.containsMatchIn(lower) -> OrderStatus.PLACED
    else -> null
}

private val AMAZON_REF = Regex("""\b\d{3}-\d{7}-\d{7}\b""")
private val FLIPKART_REF = Regex("""\bOD\d{12,}\b""")
private val LABELLED_REF = Regex(
    """(?i)(?:order\s*(?:id|no\.?|number|#)|awb|tracking\s*(?:id|no\.?|number)|consignment\s*(?:no\.?|number))\s*[:#\-]?\s*([A-Z0-9][A-Z0-9\-]{5,24})""",
)
private val HASH_REF = Regex("""#(\d{5,})""")

/** An order or tracking number named in the message (upper-cased), or null. */
internal fun refOf(original: String): String? {
    AMAZON_REF.find(original)?.let { return it.value }
    FLIPKART_REF.find(original)?.let { return it.value }
    LABELLED_REF.find(original)?.groupValues?.get(1)?.takeIf { v -> v.any(Char::isDigit) }?.let { return it.uppercase() }
    HASH_REF.find(original)?.let { return it.groupValues[1] }
    return null
}

private val QUOTED = Regex("""[“"]([^”"]{3,60})[”"]""")
private val ORDER_FOR = Regex("""(?i)your (?:[a-z']+ )?order (?:(?:#|id:? ?|no\.? ?)[A-Za-z0-9-]+ )?(?:for|of|containing)\s+(.{3,60}?)(?:\s+(?:has|is|was|will|from|with)\b|[.,!]|$)""")
private val ORDER_NAMED = Regex("""(?i)your (?:[a-z']+ )?order (?!has\b|is\b|was\b|will\b|#|id\b|no\b|number\b|for\b|of\b|containing\b|from\b)(.{3,40}?)\s+(?:is|has|was|will|are)\b""")
private val LABEL_THEN_ITEM = Regex("""(?i)(?:delivered|shipped|dispatched|ordered|arriving|out for delivery)\s*[:\-]\s*(.{3,60}?)(?:\s+(?:has|is|was|will|from|to)\b|[.,!]|$)""")
/** An SMS sender header ("AMZNIN", "VK-SWIGGY"), never an item. */
private val SENDER_CODE = Regex("""[A-Z0-9][A-Z0-9\-]{3,10}""")
private val GENERIC_TITLE = Regex("""(?i)order|deliver|shipp|dispatch|arriv|update|track|package|parcel|confirm|placed|your |thank""")

/** The item an order is for, from the message or (if it reads like an item) the notification title. */
internal fun titleOf(title: String, text: String, merchant: String, titleIsItem: Boolean = true): String? {
    val full = "$title. $text"
    QUOTED.find(full)?.groupValues?.get(1)?.let { return clip(it) }
    ORDER_FOR.find(full)?.groupValues?.get(1)?.let { return clip(it) }
    LABEL_THEN_ITEM.find(full)?.groupValues?.get(1)?.let { return clip(it) }
    ORDER_NAMED.find(full)?.groupValues?.get(1)?.let { return clip(it) }
    PACKAGE_OF.find(full)?.groupValues?.get(1)?.let { return if (it == "1") "1 item" else "$it items" }
    if (!titleIsItem) return null
    val t = title.trim()
    if (t.length >= 3 && !SENDER_CODE.matches(t) && !GENERIC_TITLE.containsMatchIn(t) && !t.equals(merchant, ignoreCase = true) && !t.all { it.isDigit() }) return clip(t)
    return null
}

/** "your Amazon package 📦 with 2 item(s)": no item name, but how many. */
private val PACKAGE_OF = Regex("""(?i)(?:package|parcel|shipment)\W{0,4}(?:with|of|containing)\s+(\d{1,2})\s+item""")

private fun clip(s: String): String = s.trim().trim('.', ',', '!', ':', '-', ' ').take(60)

private val IN_MINUTES = Regex("""\bin (\d{1,3})(?:\s?-\s?\d{1,3})? ?min(?:ute)?s?\b""")
private val BY_TIME = Regex("""\bby (\d{1,2})(?::(\d{2}))?\s?(am|pm)\b""")
private val BETWEEN = Regex("""\bbetween (\d{1,2}(?::\d{2})?)\s?(am|pm)?\s?(?:-|–|to|and)\s?(\d{1,2}(?::\d{2})?)\s?(am|pm)\b""")
private val DAY_WORD = Regex("""\b(today|tomorrow|tonight)\b""")
private val WEEKDAY = Regex("""\b(mon|tues|wednes|thurs|fri|satur|sun)day\b""")
private val DATE = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\b""")

/** When it should arrive, in a few words ("in 12 min", "by 8 pm", "today"), or null. */
internal fun etaOf(lower: String): String? {
    IN_MINUTES.find(lower)?.let { return "in ${it.groupValues[1]} min" }
    BY_TIME.find(lower)?.let { m ->
        val minutes = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { ":$it" }.orEmpty()
        return "by ${m.groupValues[1]}$minutes ${m.groupValues[3]}"
    }
    BETWEEN.find(lower)?.let { m ->
        val first = m.groupValues[1] + m.groupValues[2].let { if (it.isEmpty()) "" else " $it" }
        return "$first–${m.groupValues[3]} ${m.groupValues[4]}"
    }
    DAY_WORD.find(lower)?.let { return it.groupValues[1] }
    WEEKDAY.find(lower)?.let { return it.value }
    DATE.find(lower)?.let { return "${it.groupValues[1]} ${it.groupValues[2]}" }
    return null
}

private val OTP_AFTER = Regex("""otp\D{0,25}(\d{4,6})\b""")
private val OTP_BEFORE = Regex("""\b(\d{4,6}) is (?:your|the) (?:delivery )?otp""")
private val DELIVERY_CONTEXT = Regex("""deliver|hand\s?over|rider|agent|partner|share (?:this|the) otp""")
private val NOT_DELIVERY_OTP = Regex("""login|log in|sign in|verify your (?:mobile|phone|number|account)|registration|transaction|payment|bank|card|upi""")

/** A delivery OTP ("share OTP 4821 with the delivery partner"), never a login or payment one. */
internal fun otpOf(lower: String): String? {
    if (!DELIVERY_CONTEXT.containsMatchIn(lower) || NOT_DELIVERY_OTP.containsMatchIn(lower)) return null
    return OTP_AFTER.find(lower)?.groupValues?.get(1) ?: OTP_BEFORE.find(lower)?.groupValues?.get(1)
}

/**
 * What one notification says about an order, or null when it isn't about one (login codes, offers, chat).
 * [merchant] is who it is from (the app's name, or the store named in an SMS or email) and [food] whether
 * that is a food or grocery delivery. Pure.
 */
fun parseOrderMessage(
    title: String,
    text: String,
    sourcePackage: String,
    merchant: String,
    food: Boolean,
    time: Long,
    /** Whether the notification's title can name the item (an app's can; an SMS's is the sender). */
    titleIsItem: Boolean = true,
): OrderUpdate? {
    val full = "$title. $text"
    val lower = full.lowercase().replace('’', '\'')
    val status = statusOf(lower) ?: return null
    // An order message that is only a "placed"/"preparing" word among offers is an advert.
    if (status == OrderStatus.PLACED && PROMO.containsMatchIn(lower)) return null
    val open = !status.closed
    return OrderUpdate(
        ref = refOf(full),
        title = titleOf(title, text, merchant, titleIsItem),
        status = status,
        eta = if (open) etaOf(lower) else null,
        otp = if (open) otpOf(lower) else null,
        merchant = merchant,
        sourcePackage = sourcePackage,
        food = food,
        time = time,
        // An app's title can be part of the message ("Delivered: Phone case"); an SMS's or a chat's is the sender.
        message = if (titleIsItem) messageOf(title, text) else text.trim().ifEmpty { title.trim() }.take(1000),
    )
}

private const val DAY_MS = 24L * 60 * 60 * 1000
const val KEEP_OPEN_MS = 30 * DAY_MS
const val KEEP_CLOSED_MS = 90 * DAY_MS

private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }.take(24)

/**
 * Folds [u] into [orders]: an update with an order number goes to that order; one without goes to the merchant's latest
 * open order (or starts one). Progress only moves forward (delivered never reverts; delivered → returned is allowed),
 * a closed order sheds its ETA and OTP, and old orders are dropped. Newest update first. Pure.
 */
fun mergeOrder(orders: List<Order>, u: OrderUpdate, now: Long = u.time): List<Order> {
    val merchantKey = u.merchant.lowercase()
    val match: Order? = if (u.ref != null) {
        orders.firstOrNull { it.key == "$merchantKey|${u.ref}" }
    } else {
        orders
            .filter { it.merchant.lowercase() == merchantKey && !it.status.closed && now - it.updated <= 10 * DAY_MS }
            .firstOrNull { o ->
                u.title == null || norm(o.title) == norm(u.title) || o.title == "order" ||
                    // A message about a different item that is not further along is another order.
                    u.status.step > o.status.step
            }
    }
    val next: Order = if (match == null) {
        Order(
            key = "$merchantKey|${u.ref ?: u.time}",
            merchant = u.merchant,
            title = u.title ?: "order",
            status = u.status,
            eta = u.eta,
            otp = u.otp,
            sourcePackage = u.sourcePackage,
            food = u.food,
            firstSeen = u.time,
            updated = u.time,
            message = u.message,
        )
    } else {
        val forward = when {
            match.status == OrderStatus.DELIVERED -> u.status == OrderStatus.RETURNED
            match.status.closed -> false
            u.status.step < 0 -> true
            else -> u.status.step >= match.status.step
        }
        val status = if (forward) u.status else match.status
        val closed = status.closed
        match.copy(
            title = if (match.title == "order" && u.title != null) u.title else match.title,
            status = status,
            eta = if (closed) null else (u.eta ?: match.eta.takeIf { !forward || u.status == match.status }),
            otp = if (closed) null else (u.otp ?: match.otp),
            updated = maxOf(match.updated, u.time),
            message = if (u.time >= match.updated && u.message.isNotBlank()) u.message else match.message,
        )
    }
    return (listOf(next) + orders.filter { it.key != next.key })
        .filter { o -> now - o.updated <= (if (o.status.closed) KEEP_CLOSED_MS else KEEP_OPEN_MS) }
        .sortedByDescending { it.updated }
}

/** Orders still on their way: out for delivery first, then shipped, then placed; newest first within each. */
fun arrivingOrders(orders: List<Order>): List<Order> =
    orders.filter { !it.status.closed }.sortedWith(compareByDescending<Order> { it.status.step }.thenByDescending { it.updated })

/** Orders that have ended (delivered, cancelled, returned), newest first. */
fun pastOrders(orders: List<Order>): List<Order> = orders.filter { it.status.closed }.sortedByDescending { it.updated }

// --- deals ----------------------------------------------------------------------------

/** One promotional message from a store (its app, an SMS or a business chat), kept in the hub's deals page. */
data class Deal(
    val key: String,
    val merchant: String,
    val title: String,
    val text: String,
    val sourcePackage: String,
    val food: Boolean,
    val time: Long,
)

private val NOT_A_DEAL = Regex("""\botp\b|one[- ]time (?:password|pin|code)|verification code|security code|password|log ?in|sign ?in|\bcvv\b""")

const val KEEP_DEALS_MS = 14 * DAY_MS
const val MAX_DEALS = 60

/**
 * A store's non-order message ("8 PM deals are live now!", "Flat 20% off on shoes") as a [Deal], or null when it
 * is empty, a login or verification code, or the same text as its own title. Order updates are never deals: the
 * caller tries [parseOrderMessage] first. Pure.
 */
fun dealOf(title: String, text: String, sourcePackage: String, merchant: String, food: Boolean, time: Long): Deal? {
    val t = title.trim()
    val body = text.trim()
    if (t.isEmpty() && body.isEmpty()) return null
    if (NOT_A_DEAL.containsMatchIn("$t $body".lowercase())) return null
    val shownTitle = t.ifEmpty { body }
    val shownText = if (t.isEmpty() || body.equals(t, ignoreCase = true)) "" else body
    val key = "${merchant.lowercase()}|${(shownTitle + shownText).lowercase().filter { it.isLetterOrDigit() }.hashCode()}"
    return Deal(key, merchant, shownTitle.take(120), shownText.take(1000), sourcePackage, food, time)
}

/** Adds [d] (a repeat of the same message only refreshes its time), newest first, within [KEEP_DEALS_MS] and [MAX_DEALS]. Pure. */
fun mergeDeal(deals: List<Deal>, d: Deal, now: Long = d.time): List<Deal> =
    (listOf(d) + deals.filter { it.key != d.key })
        .filter { now - it.time <= KEEP_DEALS_MS }
        .sortedByDescending { it.time }
        .take(MAX_DEALS)
