package com.tileshell.core.data

import android.content.pm.ApplicationInfo

/**
 * Pure, framework-free categorisation of installed apps into named groups
 * ("social", "communication", "shopping", …) so the personalize sheet can offer
 * one-tap **category folders**.
 *
 * Membership is decided by a **curated list of specific package identifiers**
 * (substring of the lowercased package name — chosen to be distinctive, e.g.
 * `com.facebook.katana` not `facebook`, so Messenger doesn't leak into "social").
 * We deliberately do **not** trust the app's declared [ApplicationInfo] category
 * for most buckets — it is unreliable (Google Messages/Gmail/Meet declare
 * `SOCIAL`, X declares `NEWS`, Maps declares `MAPS`), which mis-files apps. The
 * one exception is **games**, where `CATEGORY_GAME` is accurate and there are far
 * too many titles to curate by hand.
 *
 * Matching only ever runs over apps installed on the device (the [AppEntry] list
 * comes from the launcher catalogue), so categories with no installed apps yield
 * an empty list and are hidden by the UI. The review screen lets the user add a
 * miss or untick a stray, so this errs toward precision over recall.
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
     * @property androidCategories `ApplicationInfo.CATEGORY_*` values that map
     *   here. Used sparingly — only `games` relies on it (see class docs).
     * @property packages distinctive lowercase package-name substrings that map
     *   here. An app matches when its lowercased package contains any of these.
     */
    data class Category(
        val id: String,
        val label: String,
        val androidCategories: Set<Int> = emptySet(),
        val packages: List<String> = emptyList(),
    )

    /** The shipped category set, in display order. */
    val ALL: List<Category> = listOf(
        Category(
            id = "social",
            label = "social",
            packages = listOf(
                "com.instagram.android", "com.instagram.barcelona", "com.facebook.katana",
                "com.twitter.android", "com.snapchat.android", "com.reddit.frontpage",
                "com.linkedin.android", "com.pinterest", "com.zhiliaoapp.musically",
                "com.ss.android.ugc", "com.tumblr", "org.joinmastodon.android",
                "in.mohalla.sharechat", "app.bsky", "com.quora.android", "com.vkontakte",
            ),
        ),
        Category(
            id = "communication",
            label = "communication",
            packages = listOf(
                "com.whatsapp", "org.telegram.messenger", "org.thoughtcrime.securesms",
                "com.facebook.orca", "com.google.android.apps.messaging",
                "com.samsung.android.messaging", "com.truecaller",
                "com.google.android.gm", "com.microsoft.office.outlook",
                "com.yahoo.mobile.client.android.mail", "ch.protonmail.android",
                "com.google.android.apps.meetings", "com.google.android.apps.tachyon",
                "us.zoom.videomeetings", "com.microsoft.teams", "com.skype.raider",
                "com.discord", "com.viber.voip", "jp.naver.line.android",
                "com.google.android.gm.lite",
            ),
        ),
        Category(
            id = "shopping",
            label = "shopping",
            packages = listOf(
                "com.amazon.mshop", "com.flipkart.android", "com.myntra.android",
                "com.ril.ajio", "com.meesho.supply", "com.snapdeal.main",
                "com.fsn.nykaa", "com.tul.tatacliq", "jiomart", "com.bigbasket",
                "com.grofers.customerapp", "zepto", "com.ebay.mobile",
                "com.alibaba.aliexpresshd", "com.zzkko", "com.firstcry",
                "com.lenskart", "com.dmart", "club.shopsy",
            ),
        ),
        Category(
            id = "payments",
            label = "payments",
            packages = listOf(
                "com.phonepe.app", "net.one97.paytm", "com.google.android.apps.nbu.paisa.user",
                "in.org.npci.upiapp", "com.dreamplug.androidapp", "com.mobikwik_new",
                "com.freecharge.android", "com.payzapp", "com.amazon.amazonpay",
            ),
        ),
        Category(
            id = "banking",
            label = "banking",
            packages = listOf(
                "com.sbi.", "com.csam.icici", "com.icicibank", "com.snapwork.hdfc",
                "com.hdfcbank", "com.axisbank", "com.axis.mobile", "com.msf.kbank",
                "com.kotak", "com.yesbank", "com.fss.pnbpsp", "com.bankofbaroda",
                "com.idfcfirstbank", "com.fss.idfc", "com.indusind", "com.fedmobile",
                "com.rblbank", "com.aubank", "com.canarabank", "com.unionbankofindia",
                "com.bandhan", "com.idbibank", "com.fss.bobpsp",
            ),
        ),
        Category(
            id = "travel",
            label = "travel",
            packages = listOf(
                "com.makemytrip", "com.goibibo", "com.olacabs", "com.ubercab",
                "com.rapido", "com.confirmtkt", "cris.org.in", "com.irctc",
                "in.redbus", "com.ixigo", "com.cleartrip", "com.oyo",
                "com.booking", "com.airbnb.android", "com.goindigo", "com.yatra",
            ),
        ),
        Category(
            id = "food",
            label = "food",
            packages = listOf(
                "com.application.zomato", "in.swiggy.android", "com.dominos",
                "com.mcdonalds", "com.yum.kfc", "com.done.faasos", "com.eatfit",
                "com.subway", "com.pizzahut", "com.starbucks", "com.dunkin",
            ),
        ),
        Category(
            id = "entertainment",
            label = "entertainment",
            packages = listOf(
                "com.netflix.mediaclient", "in.startv.hotstar", "com.hotstar",
                "com.amazon.avod", "com.spotify.music", "com.google.android.youtube",
                "com.gaana", "com.jio.media.jiobeats", "com.sonyliv",
                "com.graymatrix.did", "com.jio.media.ondemand", "com.mxtech.videoplayer",
                "com.wynk.music", "com.soundcloud.android",
            ),
        ),
        Category(
            id = "productivity",
            label = "productivity",
            packages = listOf(
                "com.google.android.apps.docs", "com.microsoft.office.word",
                "com.microsoft.office.excel", "com.microsoft.office.powerpoint",
                "com.microsoft.office.officehubrow", "notion", "com.evernote",
                "com.todoist", "com.google.android.keep", "com.trello",
                "com.dropbox.android", "com.anydo", "com.adobe.reader",
            ),
        ),
        Category(
            id = "games",
            label = "games",
            androidCategories = setOf(ApplicationInfo.CATEGORY_GAME),
        ),
        Category(
            id = "news",
            label = "news",
            packages = listOf(
                "com.nis.app", "com.eterno", "com.google.android.apps.magazines",
                "com.july.ndtv", "com.toi.reader", "com.hindustantimes",
                "com.indianexpress", "moneycontrol", "com.htmedia.mint",
                "flipboard.app",
            ),
        ),
    )

    /** True if [app] belongs in [category] by the curated package list or, for
     * games only, by its declared [ApplicationInfo] category. */
    fun matches(category: Category, app: AppEntry): Boolean {
        if (app.category != ApplicationInfo.CATEGORY_UNDEFINED &&
            app.category in category.androidCategories
        ) {
            return true
        }
        val pkg = app.packageName.lowercase()
        return category.packages.any { pkg.contains(it) }
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
