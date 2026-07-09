package com.tileshell.core.data

import android.content.pm.ApplicationInfo

/**
 * Pure, framework-free categorisation of installed apps into named groups for the
 * personalize "category folders" feature. Each app is placed into **at most one**
 * bucket by a layered, OS-driven classifier — no brand names, no region-specific
 * lists:
 *
 *  1. **standard app role** ([AppEntry.role]) — resolved by the platform from the
 *     app's declared `Intent.CATEGORY_APP_*` manifest category (email, messaging,
 *     maps, music, …). The strongest, most specific signal: the OS itself knows a
 *     given app is "an email app", so this cleanly separates communication from
 *     social without naming anyone.
 *  2. **declared OS category** ([AppEntry.category], `ApplicationInfo.CATEGORY_*`)
 *     — the coarse base (games, social, news, audio/video, image). Deliberately
 *     excludes `CATEGORY_MAPS`/`CATEGORY_PRODUCTIVITY`: both are unreliable in
 *     practice (ride-hailing apps commonly declare Maps for Play Store search
 *     visibility; Productivity is a Play Console catch-all reached for by
 *     unrelated apps), so they're left to role + token matching below instead.
 *  3. **generic dictionary tokens** in the package name + label — only as a last
 *     resort and only for buckets the OS models no category for (banking,
 *     payments, shopping, food, travel, health, tools). These are plain English
 *     category words ("bank", "shop", "flight"), never brand or app names.
 *
 * Matching only ever runs over apps installed on the device, so empty categories
 * are simply hidden by the UI. The review screen lets the user add a miss or
 * untick a stray, so this favours predictable OS-aligned labelling over chasing
 * every app. An app the OS leaves `UNDEFINED` with no role and no token hit stays
 * uncategorised (offered in no folder until the user adds it manually).
 */
object AppCategories {

    // Standard app-role keys (mirror Intent.CATEGORY_APP_*; set by the catalogue).
    const val ROLE_EMAIL = "email"
    const val ROLE_MESSAGING = "messaging"
    const val ROLE_CONTACTS = "contacts"
    const val ROLE_MUSIC = "music"
    const val ROLE_GALLERY = "gallery"
    const val ROLE_MAPS = "maps"
    const val ROLE_BROWSER = "browser"
    const val ROLE_CALCULATOR = "calculator"
    const val ROLE_FILES = "files"
    const val ROLE_WEATHER = "weather"
    const val ROLE_CALENDAR = "calendar"
    const val ROLE_MARKET = "market"
    const val ROLE_FITNESS = "fitness"

    /** One offerable category: a stable [id] and its lowercase display [label]. */
    data class Category(val id: String, val label: String)

    /** The category set, in display order. Only non-empty ones are surfaced. */
    val ALL: List<Category> = listOf(
        Category("communication", "communication"),
        Category("social", "social"),
        Category("entertainment", "entertainment"),
        Category("photos", "photos"),
        Category("games", "games"),
        Category("news", "news"),
        Category("navigation", "navigation"),
        Category("tools", "tools"),
        Category("payments", "payments"),
        Category("banking", "banking"),
        Category("shopping", "shopping"),
        Category("food", "food"),
        Category("travel", "travel"),
        Category("health", "health"),
    )

    private fun fromRole(role: String?): String? = when (role) {
        ROLE_EMAIL, ROLE_MESSAGING, ROLE_CONTACTS -> "communication"
        ROLE_MUSIC -> "entertainment"
        ROLE_GALLERY -> "photos"
        ROLE_MAPS -> "navigation"
        ROLE_BROWSER, ROLE_CALCULATOR, ROLE_FILES, ROLE_WEATHER, ROLE_MARKET, ROLE_CALENDAR -> "tools"
        ROLE_FITNESS -> "health"
        else -> null
    }

    // No "productivity" or "maps" entries here (deliberately dropped, not renamed):
    // Play Store's own declared categories for these two are unreliable in practice —
    // CATEGORY_PRODUCTIVITY is a Play Console catch-all developers reach for even for
    // unrelated apps (finance, utilities, notes), so trusting it swept ~87 unrelated
    // apps into one bucket on a real device and hid genuine finance apps from
    // "payments"/"banking" (this OS layer runs before token matching, so it won the
    // priority race); CATEGORY_MAPS is commonly declared by ride-hailing apps (Ola,
    // Uber) for Play Store search visibility despite not being navigation apps, which
    // put them in "navigation" instead of "travel". Removing both lets apps with no
    // better signal fall through to token matching, which already has narrower,
    // more accurate rules for both (tools' office/notes tokens; travel's cab/taxi).
    private fun fromOsCategory(category: Int): String? = when (category) {
        ApplicationInfo.CATEGORY_GAME -> "games"
        ApplicationInfo.CATEGORY_SOCIAL -> "social"
        ApplicationInfo.CATEGORY_NEWS -> "news"
        ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> "entertainment"
        ApplicationInfo.CATEGORY_IMAGE -> "photos"
        else -> null
    }

    /**
     * Generic dictionary tokens per category, in match-priority order. Searched
     * against the lowercased "packageName label" only when role + OS category both
     * come up empty. Ordered so the distinctive, OS-unmodelled buckets win first.
     */
    private val TOKEN_RULES: List<Pair<String, List<String>>> = listOf(
        "banking" to listOf("bank", "banking", "finance", "financial"),
        "payments" to listOf("wallet", "upi", "payment", "paytm", "gpay", " pay", "paisa"),
        "shopping" to listOf(
            "shop", "shopping", "store", "mall", "mart", "cart", "bazaar",
            "commerce", "ecommerce", "retail", "grocery", "grocer",
        ),
        "food" to listOf("food", "restaurant", "kitchen", "recipe", "cafe", "meal", "delivery", "diner"),
        "travel" to listOf(
            "travel", "trip", "flight", "hotel", "airline", "taxi", "cab", "train",
            "railway", "metro", "tour", "booking", "transit", "rail",
        ),
        "health" to listOf(
            "health", "fitness", "workout", "gym", "medic", "pharma", "doctor",
            "yoga", "diet", "wellness", "clinic",
        ),
        "news" to listOf("news", "headline", "newspaper", "magazine"),
        "communication" to listOf("mail", "messeng", "messag", "chat", "dialer", "telephon"),
        "entertainment" to listOf(
            "music", "audio", "video", "movie", "stream", "podcast", "radio",
            "player", "song", "cinema",
        ),
        "photos" to listOf("photo", "camera", "gallery", "selfie"),
        "navigation" to listOf("maps", "navigation", "gps", "route"),
        // Folded in what used to be a separate "productivity" bucket (removed —
        // see the class doc) since these tokens are precise enough on their own
        // without the noisy OS category riding along.
        "tools" to listOf(
            "browser", "weather", "calculator", "vpn", "cleaner", "flashlight",
            "office", "document", "docs", "note", "todo", "task", "spreadsheet", "scanner",
        ),
        "social" to listOf("social", "community", "forum", "dating"),
    )

    private fun fromTokens(packageName: String, label: String): String? {
        // "smart" is an extremely common Android/OEM app-name prefix that is never
        // actually shopping-related (SmartThings, Smart Switch, Smart Manager, Smart
        // Launcher, Smart Tutor, …) but happens to contain the "mart" shopping token
        // as a trailing fragment with no word boundary between them; strip it before
        // matching so those utility/app names don't get swept into "shopping".
        val hay = (packageName + " " + label).lowercase().replace("smart", " ")
        for ((id, tokens) in TOKEN_RULES) {
            if (tokens.any { hay.contains(it) }) return id
        }
        return null
    }

    /**
     * The single best category id for [app], or null when nothing classifies it.
     * Role wins over the OS category, which wins over generic tokens.
     */
    fun classify(app: AppEntry): String? =
        fromRole(app.role)
            ?: fromOsCategory(app.category)
            ?: fromTokens(app.packageName, app.label)

    /**
     * Whether a tile may cycle up to the 3×3 [TileSize.LARGE] size. Large is offered
     * for **any** app tile on any grid density (4/5/6 columns) — a 3-wide tile still
     * fits within the minimum 4-column grid, it just uses more of the row.
     * ([iconKey]/[app]/[columns] are unused now — kept for call-site compatibility.)
     * Pure so the gating stays unit-testable.
     */
    fun allowsLargeTile(iconKey: String?, app: AppEntry?, columns: Int): Boolean = true

    /** All installed [apps] that classify into the category with id [categoryId]. */
    fun match(categoryId: String, apps: List<AppEntry>): List<AppEntry> =
        if (ALL.none { it.id == categoryId }) emptyList()
        else apps.filter { classify(it) == categoryId }

    /** Map of category id → its classified installed apps (may be empty per id). */
    fun categorize(apps: List<AppEntry>): Map<String, List<AppEntry>> {
        val grouped = apps.groupBy { classify(it) }
        return ALL.associate { cat -> cat.id to (grouped[cat.id] ?: emptyList()) }
    }
}
