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
)

private val SHOPPING_PACKAGES = setOf(
    "com.amazon.mShop.android.shopping", "in.amazon.mShop.android.shopping", "com.flipkart.android", "com.myntra.android",
    "com.meesho.supply", "com.ril.ajio", "com.fsn.nykaa", "com.snapdeal.main", "com.tatadigital.tcp",
)
private val FOOD_PACKAGES = setOf(
    "in.swiggy.android", "com.application.zomato", "com.grofers.customerapp", "com.zeptoconsumerapp", "com.bigbasket.mobileapp",
    "com.dunzo.user",
)
private val COURIER_PACKAGES = setOf("com.delhivery.app", "com.bluedart.bluedartapp")

private fun hasWord(lower: String, word: String) = com.tileshell.feature.livetiles.cachedRegex("""(^|[^a-z])${Regex.escape(word)}([^a-z]|$)""").containsMatchIn(lower)

/** The store a text (an SMS, an email, an app label) names, or null. The earliest named one wins. Pure. */
internal fun storeIn(text: String): KnownStore? {
    val lower = text.lowercase()
    return KNOWN_STORES
        .mapNotNull { s -> s.words.mapNotNull { w -> com.tileshell.feature.livetiles.cachedRegex("""(^|[^a-z])${Regex.escape(w)}""").find(lower)?.range?.first }.minOrNull()?.let { s to it } }
        .minByOrNull { it.second }?.first
}

/** Amazon's shopping entries; every other Amazon app (Music, Prime Video, Alexa, Kindle, Audible, Pay, Photos…) is not shopping. */
private val AMAZON_SHOPPING_WORDS = listOf("shopping", "now", "fresh", "business")

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
    if (hasWord(l, "shopping") || hasWord(l, "shop") || hasWord(l, "mart")) return ShoppingAppKind.SHOPPING
    return null
}

/** Every shopping section an app is in: its built-in one plus what the user added, minus what they took off. */
fun shoppingAppKinds(entryId: String, label: String): List<ShoppingAppKind> {
    val choice = HubAppChoices.current(HubKind.SHOPPING)
    val names = choice.sectionsOf(entryId, setOfNotNull(builtInShoppingKind(packageOfEntry(entryId), label)?.name))
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
