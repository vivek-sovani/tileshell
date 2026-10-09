package com.tileshell.feature.livetiles.shopping

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tileshell.feature.livetiles.HubAppChoices
import com.tileshell.feature.livetiles.HubKind
import com.tileshell.feature.livetiles.launcherEntries
import com.tileshell.feature.livetiles.packageOfEntry
import com.tileshell.feature.livetiles.rememberAppOpenCounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The sections of the shopping hub's apps page. */
enum class ShoppingAppKind { SHOPPING, FOOD, COURIER }

/** [packageName] is the entry id: the package for an app's main entry, "package/Class" for another entry of it (Amazon Now). */
data class ShoppingApp(val packageName: String, val label: String, val kind: ShoppingAppKind)

/** A store the hub knows by name: the words that name it in a message or app label, how it is shown, and its kind. */
internal data class KnownStore(val words: List<String>, val name: String, val kind: ShoppingAppKind)

internal val KNOWN_STORES: List<KnownStore> = listOf(
    KnownStore(listOf("amazon"), "Amazon", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("samsung shop"), "Samsung Shop", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("reliance digital"), "Reliance Digital", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("pharmeasy"), "PharmEasy", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("1mg", "tata 1mg"), "Tata 1mg", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("flipkart"), "Flipkart", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("myntra"), "Myntra", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("meesho"), "Meesho", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("ajio"), "Ajio", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("nykaa"), "Nykaa", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("snapdeal"), "Snapdeal", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("jiomart"), "JioMart", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("tata neu", "tatacliq", "tata cliq"), "Tata", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("croma"), "Croma", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("lenskart"), "Lenskart", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("firstcry"), "FirstCry", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("bigbasket"), "BigBasket", ShoppingAppKind.FOOD),
    KnownStore(listOf("swiggy", "instamart"), "Swiggy", ShoppingAppKind.FOOD),
    KnownStore(listOf("zomato"), "Zomato", ShoppingAppKind.FOOD),
    KnownStore(listOf("blinkit"), "Blinkit", ShoppingAppKind.FOOD),
    KnownStore(listOf("zepto"), "Zepto", ShoppingAppKind.FOOD),
    KnownStore(listOf("dunzo"), "Dunzo", ShoppingAppKind.FOOD),
    KnownStore(listOf("domino"), "Domino's", ShoppingAppKind.FOOD),
    KnownStore(listOf("mcdonald"), "McDonald's", ShoppingAppKind.FOOD),
    KnownStore(listOf("eatsure"), "EatSure", ShoppingAppKind.FOOD),
    KnownStore(listOf("licious"), "Licious", ShoppingAppKind.FOOD),
    KnownStore(listOf("delhivery"), "Delhivery", ShoppingAppKind.COURIER),
    KnownStore(listOf("bluedart", "blue dart"), "Blue Dart", ShoppingAppKind.COURIER),
    KnownStore(listOf("dtdc"), "DTDC", ShoppingAppKind.COURIER),
    KnownStore(listOf("ecom express", "ecomexpress"), "Ecom Express", ShoppingAppKind.COURIER),
    KnownStore(listOf("xpressbees"), "Xpressbees", ShoppingAppKind.COURIER),
    KnownStore(listOf("india post", "indiapost"), "India Post", ShoppingAppKind.COURIER),
    KnownStore(listOf("shadowfax"), "Shadowfax", ShoppingAppKind.COURIER),
    KnownStore(listOf("ekart"), "Ekart", ShoppingAppKind.COURIER),
    KnownStore(listOf("shiprocket"), "Shiprocket", ShoppingAppKind.COURIER),
    // United States, Europe and elsewhere abroad.
    KnownStore(listOf("walmart"), "Walmart", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("best buy", "bestbuy"), "Best Buy", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("home depot"), "Home Depot", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("lowe's", "lowes"), "Lowe's", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("costco"), "Costco", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("ebay"), "eBay", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("etsy"), "Etsy", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("wayfair"), "Wayfair", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("macy's", "macys"), "Macy's", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("nordstrom"), "Nordstrom", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("kohl's", "kohls"), "Kohl's", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("sephora"), "Sephora", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("ulta"), "Ulta", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("chewy"), "Chewy", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("sam's club"), "Sam's Club", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("overstock"), "Overstock", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("newegg"), "Newegg", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("b&h"), "B&H", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("ikea"), "IKEA", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("temu"), "Temu", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("shein"), "Shein", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("aliexpress"), "AliExpress", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("asos"), "ASOS", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("boohoo"), "Boohoo", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("zalando"), "Zalando", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("zara"), "Zara", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("h&m"), "H&M", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("uniqlo"), "Uniqlo", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("decathlon"), "Decathlon", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("mediamarkt", "media markt"), "MediaMarkt", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("fnac"), "Fnac", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("darty"), "Darty", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("cdiscount"), "Cdiscount", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("bol.com"), "bol.com", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("coolblue"), "Coolblue", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("allegro"), "Allegro", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("vinted"), "Vinted", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("kaufland"), "Kaufland", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("argos"), "Argos", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("currys"), "Currys", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("john lewis"), "John Lewis", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("marks & spencer", "marks and spencer"), "M&S", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("very.co.uk"), "Very", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("primark"), "Primark", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("zooplus"), "zooplus", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("otto.de"), "Otto", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("mercadona"), "Mercadona", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("el corte ingl"), "El Corte Inglés", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("pccomponentes"), "PcComponentes", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("leroy merlin"), "Leroy Merlin", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("rakuten"), "Rakuten", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("mercado libre", "mercadolibre"), "Mercado Libre", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("shopify"), "Shopify", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("lieferando"), "Lieferando", ShoppingAppKind.SHOPPING),
    KnownStore(listOf("doordash"), "DoorDash", ShoppingAppKind.FOOD),
    KnownStore(listOf("uber eats", "ubereats"), "Uber Eats", ShoppingAppKind.FOOD),
    KnownStore(listOf("grubhub"), "Grubhub", ShoppingAppKind.FOOD),
    KnownStore(listOf("postmates"), "Postmates", ShoppingAppKind.FOOD),
    KnownStore(listOf("instacart"), "Instacart", ShoppingAppKind.FOOD),
    KnownStore(listOf("gopuff"), "Gopuff", ShoppingAppKind.FOOD),
    KnownStore(listOf("deliveroo"), "Deliveroo", ShoppingAppKind.FOOD),
    KnownStore(listOf("just eat", "justeat"), "Just Eat", ShoppingAppKind.FOOD),
    KnownStore(listOf("glovo"), "Glovo", ShoppingAppKind.FOOD),
    KnownStore(listOf("wolt"), "Wolt", ShoppingAppKind.FOOD),
    KnownStore(listOf("foodpanda"), "foodpanda", ShoppingAppKind.FOOD),
    KnownStore(listOf("getir"), "Getir", ShoppingAppKind.FOOD),
    KnownStore(listOf("flink"), "Flink", ShoppingAppKind.FOOD),
    KnownStore(listOf("gorillas"), "Gorillas", ShoppingAppKind.FOOD),
    KnownStore(listOf("hellofresh", "hello fresh"), "HelloFresh", ShoppingAppKind.FOOD),
    KnownStore(listOf("ocado"), "Ocado", ShoppingAppKind.FOOD),
    KnownStore(listOf("tesco"), "Tesco", ShoppingAppKind.FOOD),
    KnownStore(listOf("sainsbury"), "Sainsbury's", ShoppingAppKind.FOOD),
    KnownStore(listOf("asda"), "Asda", ShoppingAppKind.FOOD),
    KnownStore(listOf("morrisons"), "Morrisons", ShoppingAppKind.FOOD),
    KnownStore(listOf("waitrose"), "Waitrose", ShoppingAppKind.FOOD),
    KnownStore(listOf("lidl"), "Lidl", ShoppingAppKind.FOOD),
    KnownStore(listOf("aldi"), "Aldi", ShoppingAppKind.FOOD),
    KnownStore(listOf("carrefour"), "Carrefour", ShoppingAppKind.FOOD),
    KnownStore(listOf("albert heijn"), "Albert Heijn", ShoppingAppKind.FOOD),
    KnownStore(listOf("rewe"), "REWE", ShoppingAppKind.FOOD),
    KnownStore(listOf("edeka"), "Edeka", ShoppingAppKind.FOOD),
    KnownStore(listOf("pizza hut"), "Pizza Hut", ShoppingAppKind.FOOD),
    KnownStore(listOf("kfc"), "KFC", ShoppingAppKind.FOOD),
    KnownStore(listOf("burger king"), "Burger King", ShoppingAppKind.FOOD),
    KnownStore(listOf("papa john"), "Papa John's", ShoppingAppKind.FOOD),
    KnownStore(listOf("starbucks"), "Starbucks", ShoppingAppKind.FOOD),
    KnownStore(listOf("thuisbezorgd"), "Thuisbezorgd", ShoppingAppKind.FOOD),
    KnownStore(listOf("takeaway.com"), "Takeaway.com", ShoppingAppKind.FOOD),
    KnownStore(listOf("freshdirect"), "FreshDirect", ShoppingAppKind.FOOD),
    KnownStore(listOf("walmart grocery"), "Walmart Grocery", ShoppingAppKind.FOOD),
    KnownStore(listOf("ups"), "UPS", ShoppingAppKind.COURIER),
    KnownStore(listOf("fedex", "fed ex"), "FedEx", ShoppingAppKind.COURIER),
    KnownStore(listOf("dhl"), "DHL", ShoppingAppKind.COURIER),
    KnownStore(listOf("usps"), "USPS", ShoppingAppKind.COURIER),
    KnownStore(listOf("royal mail"), "Royal Mail", ShoppingAppKind.COURIER),
    KnownStore(listOf("evri"), "Evri", ShoppingAppKind.COURIER),
    KnownStore(listOf("dpd"), "DPD", ShoppingAppKind.COURIER),
    KnownStore(listOf("gls"), "GLS", ShoppingAppKind.COURIER),
    KnownStore(listOf("postnl"), "PostNL", ShoppingAppKind.COURIER),
    KnownStore(listOf("parcelforce"), "Parcelforce", ShoppingAppKind.COURIER),
    KnownStore(listOf("yodel"), "Yodel", ShoppingAppKind.COURIER),
    KnownStore(listOf("inpost"), "InPost", ShoppingAppKind.COURIER),
    KnownStore(listOf("colissimo"), "Colissimo", ShoppingAppKind.COURIER),
    KnownStore(listOf("la poste"), "La Poste", ShoppingAppKind.COURIER),
    KnownStore(listOf("chronopost"), "Chronopost", ShoppingAppKind.COURIER),
    KnownStore(listOf("mondial relay"), "Mondial Relay", ShoppingAppKind.COURIER),
    KnownStore(listOf("deutsche post"), "Deutsche Post", ShoppingAppKind.COURIER),
    KnownStore(listOf("correos"), "Correos", ShoppingAppKind.COURIER),
    KnownStore(listOf("poste italiane"), "Poste Italiane", ShoppingAppKind.COURIER),
    KnownStore(listOf("canada post"), "Canada Post", ShoppingAppKind.COURIER),
    KnownStore(listOf("purolator"), "Purolator", ShoppingAppKind.COURIER),
    KnownStore(listOf("australia post", "auspost"), "Australia Post", ShoppingAppKind.COURIER),
    KnownStore(listOf("ontrac"), "OnTrac", ShoppingAppKind.COURIER),
    KnownStore(listOf("lasership"), "LaserShip", ShoppingAppKind.COURIER),
    KnownStore(listOf("amazon logistics"), "Amazon Logistics", ShoppingAppKind.COURIER),
    KnownStore(listOf("bpost"), "bpost", ShoppingAppKind.COURIER),
    KnownStore(listOf("postnord"), "PostNord", ShoppingAppKind.COURIER),
    KnownStore(listOf("an post"), "An Post", ShoppingAppKind.COURIER),
    KnownStore(listOf("swiss post", "die post"), "Swiss Post", ShoppingAppKind.COURIER),
    KnownStore(listOf("packlink"), "Packlink", ShoppingAppKind.COURIER),
    KnownStore(listOf("17track"), "17TRACK", ShoppingAppKind.COURIER),
)

private val SHOPPING_PACKAGES = setOf(
    "com.amazon.mShop.android.shopping", "in.amazon.mShop.android.shopping", "com.flipkart.android", "com.myntra.android",
    "com.meesho.supply", "com.ril.ajio", "com.fsn.nykaa", "com.snapdeal.main", "com.tatadigital.tcp",
    // United States and Europe.
    "com.ebay.mobile", "com.walmart.android", "com.target.ui", "com.etsy.android", "com.contextlogic.wish", "com.zzkko",
    "com.einnovation.temu", "com.alibaba.aliexpresshd", "de.zalando.mobile", "com.asos.app", "com.bestbuy.android",
    "com.thehomedepot", "com.lowes.android", "com.costco.app.android", "com.wayfair.wayfair", "com.ikea.app",
)
private val FOOD_PACKAGES = setOf(
    "in.swiggy.android", "com.application.zomato", "com.grofers.customerapp", "com.zeptoconsumerapp", "com.bigbasket.mobileapp",
    "com.dunzo.user",
    // United States and Europe.
    "com.dd.doordash", "com.grubhub.android", "com.ubercab.eats", "com.instacart.client", "com.deliveroo.orderapp",
    "com.justeat.app.uk", "com.glovoapp.glovo", "com.wolt.android", "com.tesco.grocery.view",
)
private val COURIER_PACKAGES = setOf(
    "com.delhivery.app", "com.bluedart.bluedartapp",
    "com.ups.mobile.android", "com.fedex.ida.android", "com.usps.mobile", "com.royalmail.mobile", "de.dhl.paket",
)

private fun hasWord(lower: String, word: String) = com.tileshell.feature.livetiles.cachedRegex("""(^|[^a-z])${Regex.escape(word)}([^a-z]|$)""").containsMatchIn(lower)

/** The store a text (an SMS, an email, an app label) names, or null. The earliest named one wins. Pure. */
internal fun storeIn(text: String): KnownStore? {
    val lower = text.lowercase()
    return KNOWN_STORES
        .mapNotNull { s -> s.words.mapNotNull { w ->
            // A short name ("ups", "gls", "kfc") must stand alone, or it is found inside other words.
            val tail = if (w.length <= 4) "([^a-z]|$)" else ""
            com.tileshell.feature.livetiles.cachedRegex("""(^|[^a-z])${Regex.escape(w)}$tail""").find(lower)?.range?.first
        }.minOrNull()?.let { s to it } }
        .minByOrNull { it.second }?.first
}

/** Amazon's shopping entries; every other Amazon app (Music, Prime Video, Alexa, Kindle, Audible, Pay, Photos…) is not shopping. */
private val AMAZON_SHOPPING_WORDS = listOf("shopping", "now", "fresh", "business")

private val CATEGORY_SHOPPING_WORDS = listOf("shopping", "shop", "shops", "mart", "marketplace", "outlet", "boutique", "pharmacy", "webshop", "winkel", "einkaufen", "compras", "achats", "acquisti")
private val CATEGORY_FOOD_WORDS = listOf("grocery", "groceries", "supermarket", "takeaway", "food delivery", "meal delivery", "pizza", "lieferdienst", "livraison", "supermercado", "supermercato", "supermarkt")
private val CATEGORY_COURIER_WORDS = listOf("parcel", "parcels", "courier", "shipment", "shipments", "package tracker", "paket", "colis", "paquete", "pacco", "pakket")

/** The built-in kind of an installed app, ignoring the user's choices: a known package or a store named in its label. */
internal fun builtInShoppingKind(packageName: String, label: String): ShoppingAppKind? {
    val l = label.lowercase()
    // Amazon makes many apps: only its shopping entries count (by label, whatever their package).
    if (hasWord(l, "amazon")) {
        val trimmed = l.trim()
        return if (trimmed == "amazon" || AMAZON_SHOPPING_WORDS.any { hasWord(l, it) }) ShoppingAppKind.SHOPPING else null
    }
    if (packageName in SHOPPING_PACKAGES) return ShoppingAppKind.SHOPPING
    if (packageName in FOOD_PACKAGES) return ShoppingAppKind.FOOD
    if (packageName in COURIER_PACKAGES) return ShoppingAppKind.COURIER
    KNOWN_STORES.firstOrNull { s -> s.words.any { hasWord(l, it) } }?.let { return it.kind }
    // Any app named for what it does, whatever its brand: "… Shopping", "… Mart", a grocery, a delivery or a parcel tracker.
    // ("store" alone is left out: it is also the Play Store, the Galaxy Store and the App Store.)
    if (CATEGORY_COURIER_WORDS.any { hasWord(l, it) }) return ShoppingAppKind.COURIER
    if (CATEGORY_FOOD_WORDS.any { hasWord(l, it) }) return ShoppingAppKind.FOOD
    if (CATEGORY_SHOPPING_WORDS.any { hasWord(l, it) }) return ShoppingAppKind.SHOPPING
    return null
}

/** Every shopping section an app is in: its built-in one plus what the user added, minus what they took off. */
fun shoppingAppKinds(entryId: String, label: String): List<ShoppingAppKind> {
    val choice = HubAppChoices.current(HubKind.SHOPPING)
    val names = choice.sectionsOf(entryId, setOfNotNull(builtInShoppingKind(packageOfEntry(entryId), label)?.name))
    return ShoppingAppKind.entries.filter { it.name in names }
}

/**
 * Every shopping section an app's notifications belong to. A notification names the app's package, but the user may have
 * added just one of its other launcher entries ("package/Class", like Amazon Now) to the hub, so those count too.
 */
internal fun shoppingKindsOfPackage(packageName: String, label: String): List<ShoppingAppKind> {
    val own = shoppingAppKinds(packageName, label)
    if (own.isNotEmpty()) return own
    val choice = HubAppChoices.current(HubKind.SHOPPING)
    val entries = choice.added.keys.filter { it != packageName && packageOfEntry(it) == packageName }
    if (entries.isEmpty()) return emptyList()
    val names = entries.flatMap { choice.sectionsOf(it, emptySet()) }.toSet()
    return ShoppingAppKind.entries.filter { it.name in names }
}

/** Every launcher entry of an installed app that is a shopping, food and grocery, or courier app (Amazon and Amazon Now apart). */
internal fun installedShoppingApps(context: Context): List<ShoppingApp> = runCatching {
    launcherEntries(context)
        .flatMap { e -> shoppingAppKinds(e.id, e.label).map { ShoppingApp(e.id, e.label, it) } }
        .distinctBy { it.packageName to it.kind }
}.getOrDefault(emptyList())

/** Installed shopping apps, most used first (when usage access is on), then by name. Null while loading. */
@Composable
internal fun rememberShoppingApps(): List<ShoppingApp>? {
    val context = LocalContext.current
    val choices by remember { HubAppChoices.state(context) }.collectAsState()
    val installed by produceState<List<ShoppingApp>?>(initialValue = null, choices) {
        value = withContext(Dispatchers.IO) { installedShoppingApps(context) }
    }
    val opens = rememberAppOpenCounts()
    return remember(installed, opens) {
        installed?.sortedWith(compareByDescending<ShoppingApp> { opens[packageOfEntry(it.packageName)] ?: 0 }.thenBy { it.label.lowercase() })
    }
}

/** The installed app of the store [merchant] ("Amazon"), for opening it from an order's card; null when none. */
internal fun appForMerchant(apps: List<ShoppingApp>, merchant: String): ShoppingApp? {
    val store = storeIn(merchant)
    // The store's main entry (Amazon) before its other ones (Amazon Now).
    val ordered = apps.sortedBy { it.packageName.contains('/') }
    return ordered.firstOrNull { a -> store != null && storeIn(a.label)?.name == store.name }
        ?: ordered.firstOrNull { it.label.equals(merchant, ignoreCase = true) }
}
