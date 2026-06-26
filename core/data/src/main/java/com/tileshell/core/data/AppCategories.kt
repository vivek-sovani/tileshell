package com.tileshell.core.data

import android.content.pm.ApplicationInfo

/**
 * Pure, framework-free categorisation of installed apps into named groups
 * ("social", "shopping", "payments", …) so the personalize sheet can offer
 * one-tap **category folders**.
 *
 * Two signals decide membership, OR-ed together:
 *  1. the app's declared [ApplicationInfo] category ([AppEntry.category]) — used
 *     for the buckets Android actually models (social, games, news, productivity,
 *     audio/video → entertainment, maps → travel); and
 *  2. a curated, India-centric list of package-name substrings — for the buckets
 *     Android does not model at all (banking, payments, shopping, food).
 *
 * Matching only ever runs over apps that are installed on the device (the
 * [AppEntry] list comes from the launcher catalogue), so categories with no
 * installed apps simply yield an empty list and are hidden by the UI.
 *
 * The `ApplicationInfo.CATEGORY_*` values referenced here are compile-time
 * constants, so this object stays unit-testable on the plain JVM.
 */
object AppCategories {

    /**
     * One offerable category.
     *
     * @property id stable key persisted/used in callbacks
     * @property label lowercase display name (also the default folder name)
     * @property androidCategories `ApplicationInfo.CATEGORY_*` values that map here
     * @property keywords lowercase package-name substrings that map here
     */
    data class Category(
        val id: String,
        val label: String,
        val androidCategories: Set<Int> = emptySet(),
        val keywords: List<String> = emptyList(),
    )

    /** The shipped category set, in display order. */
    val ALL: List<Category> = listOf(
        Category(
            id = "social",
            label = "social",
            androidCategories = setOf(ApplicationInfo.CATEGORY_SOCIAL),
            keywords = listOf(
                "whatsapp", "instagram", "facebook", "messenger", "telegram",
                "snapchat", "twitter", ".twitter", "discord", "linkedin",
                "reddit", "threads", "signal", "pinterest", "tumblr", "mastodon",
                "sharechat", "koo",
            ),
        ),
        Category(
            id = "shopping",
            label = "shopping",
            keywords = listOf(
                "amazon.mShop", "flipkart", "myntra", "ajio", "meesho",
                "snapdeal", "nykaa", "tatacliq", "shopsy", "jiomart", "ebay",
                "aliexpress", "shein", "firstcry", "lenskart", "bigbasket",
                "blinkit", "grofers", "zepto", "dmart",
            ),
        ),
        Category(
            id = "payments",
            label = "payments",
            keywords = listOf(
                "phonepe", "one97.paytm", "nbu.paisa", "bhim", "mobikwik",
                "freecharge", ".cred", "amazonpay", "payzapp", "airtel.money",
                "jio.jiopay", "razorpay",
            ),
        ),
        Category(
            id = "banking",
            label = "banking",
            keywords = listOf(
                "sbi", "icici", "hdfc", "axis", "kotak", "yesbank", "pnb",
                "bankofbaroda", "idfc", "indusind", "federalbank", "rblbank",
                "aubank", "canara", "unionbank", "bandhan", "idbi", "csam",
                "centralbank", "iobnet",
            ),
        ),
        Category(
            id = "travel",
            label = "travel",
            androidCategories = setOf(ApplicationInfo.CATEGORY_MAPS),
            keywords = listOf(
                "makemytrip", "goibibo", "olacabs", "ubercab", ".uber",
                "rapido", "irctc", "redbus", "ixigo", "cleartrip", "oyo",
                "booking", "airbnb", "indigo", "goair", "vistara", "yatra",
                "maps",
            ),
        ),
        Category(
            id = "food",
            label = "food",
            keywords = listOf(
                "zomato", "swiggy", "dominos", "mcdonald", "kfc", "dunkin",
                "eatfit", "faasos", "behrouz", " subway", "pizzahut",
                "starbucks", "chaayos",
            ),
        ),
        Category(
            id = "entertainment",
            label = "entertainment",
            androidCategories = setOf(
                ApplicationInfo.CATEGORY_AUDIO,
                ApplicationInfo.CATEGORY_VIDEO,
            ),
            keywords = listOf(
                "netflix", "hotstar", "primevideo", "spotify", "youtube",
                "gaana", "jiosaavn", "saavn", "sonyliv", "zee5", "voot",
                "wynk", "jiocinema", "mxplayer", "altbalaji", "soundcloud",
            ),
        ),
        Category(
            id = "productivity",
            label = "productivity",
            androidCategories = setOf(ApplicationInfo.CATEGORY_PRODUCTIVITY),
            keywords = listOf(
                "docs", "sheets", "slides", "office", ".word", ".excel",
                "powerpoint", "notion", "evernote", "android.apps.docs",
                "dropbox", "outlook", "trello", "slack", "zoom", "android.apps.meetings",
                "microsoft.teams", "keep", "todoist", "anydo",
            ),
        ),
        Category(
            id = "games",
            label = "games",
            androidCategories = setOf(ApplicationInfo.CATEGORY_GAME),
            keywords = listOf("pubg", "ludo", "candycrush", "freefire", "callofduty"),
        ),
        Category(
            id = "news",
            label = "news",
            androidCategories = setOf(ApplicationInfo.CATEGORY_NEWS),
            keywords = listOf(
                "inshorts", "dailyhunt", "ndtv", ".toi", "hindustantimes",
                "indianexpress", "news", "moneycontrol", "livemint",
            ),
        ),
    )

    /** True if [app] belongs in [category] by either signal. */
    fun matches(category: Category, app: AppEntry): Boolean {
        if (app.category != ApplicationInfo.CATEGORY_UNDEFINED &&
            app.category in category.androidCategories
        ) {
            return true
        }
        val pkg = app.packageName.lowercase()
        return category.keywords.any { pkg.contains(it.trim()) }
    }

    /** All installed [apps] that match the category with the given [categoryId]. */
    fun match(categoryId: String, apps: List<AppEntry>): List<AppEntry> {
        val category = ALL.firstOrNull { it.id == categoryId } ?: return emptyList()
        return apps.filter { matches(category, it) }
    }

    /** Map of category id → its matching installed apps (may be empty per id). */
    fun categorize(apps: List<AppEntry>): Map<String, List<AppEntry>> =
        ALL.associate { cat -> cat.id to apps.filter { matches(cat, it) } }
}
