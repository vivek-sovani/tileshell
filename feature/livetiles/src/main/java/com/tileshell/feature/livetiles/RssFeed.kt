package com.tileshell.feature.livetiles

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

/**
 * One parsed article from a news feed (FR — left feed discover section). Pure data
 * so the parser and formatters are JVM-unit-testable without Android.
 */
data class FeedArticle(
    val title: String,
    val link: String,
    val source: String,
    val tag: String,
    val imageUrl: String?,
    val publishedAtMillis: Long,
    /**
     * The subscribed [FeedSource.url] this article came from — not the same thing
     * as [source], which is the *channel's own* title (feeds routinely name
     * themselves differently from the URL they're subscribed under, and several
     * feeds can share one channel title). Needed so a conditional refresh can
     * keep exactly the articles belonging to a feed that answered
     * `304 Not Modified` without re-downloading it (see `FeedRefreshWorker`).
     * Defaults to empty for an article decoded from a cache file written before
     * this field existed; the refresh path never sends a validator for a feed it
     * has no attributable articles for, so a 304 can't strand a legacy entry.
     */
    val feedUrl: String = "",
)

/**
 * A subscribed news feed: its [url], a display [name], the [category] it belongs to
 * (for category-level selection in personalize), and whether it is currently
 * [enabled]. Disabled feeds are kept (so the toggle is reversible) but skipped on
 * refresh. User-added feeds use the [CUSTOM_CATEGORY].
 */
data class FeedSource(
    val url: String,
    val name: String,
    val category: String = CUSTOM_CATEGORY,
    val enabled: Boolean = true,
)

const val CUSTOM_CATEGORY = "custom"

/**
 * Default feeds removed in a newer version. `reconcileDefaults` drops these from
 * existing installs (DataStore keeps the first-seen list, so a dropped default
 * would otherwise linger). Only ever list *former defaults* here — never a URL a
 * user might add themselves.
 */
val DEPRECATED_FEED_URLS: Set<String> = setOf(
    "https://indianexpress.com/feed/",
    "https://timesofindia.indiatimes.com/rssfeeds/-2128932452.cms", // generic "TOI City"
    "https://feeds.feedburner.com/ndtvnews-cities-news", // generic "NDTV Cities"
)

/**
 * Selectable news categories, in display order (the user toggles whole categories in
 * personalize). [CUSTOM_CATEGORY] is handled separately (per-feed add/remove).
 */
val FEED_CATEGORIES: List<String> = listOf(
    "nation", "entertainment", "cricket", "sports", "tech", "business", "food",
)

/**
 * The India-specific default feeds, tagged by [FEED_CATEGORIES]. Free RSS/Atom, no
 * API key, all verified reachable. The refresh worker tolerates a dead feed (skips
 * it, keeps the rest). A sensible subset is enabled out of the box; the rest are
 * opt-in via the category toggles.
 */
val DEFAULT_FEED_SOURCES: List<FeedSource> = listOf(
    FeedSource("https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en", "Google News", "nation", enabled = true),
    FeedSource("https://www.thehindu.com/news/national/feeder/default.rss", "The Hindu", "nation", enabled = true),
    FeedSource("https://feeds.feedburner.com/ndtvnews-top-stories", "NDTV", "nation", enabled = true),
    FeedSource("https://timesofindia.indiatimes.com/rssfeedstopstories.cms", "TOI Top Stories", "nation", enabled = true),
    FeedSource("https://feeds.feedburner.com/ndtvmovies-latest", "NDTV Movies", "entertainment", enabled = true),
    FeedSource("https://timesofindia.indiatimes.com/rssfeeds/1081479906.cms", "TOI Entertainment", "entertainment", enabled = true),
    FeedSource("https://www.espncricinfo.com/rss/content/story/feeds/0.xml", "ESPNcricinfo", "cricket", enabled = true),
    FeedSource("https://timesofindia.indiatimes.com/rssfeeds/54829575.cms", "TOI Cricket", "cricket", enabled = true),
    FeedSource("https://feeds.feedburner.com/ndtvsports-latest", "NDTV Sports", "sports", enabled = true),
    FeedSource("https://timesofindia.indiatimes.com/rssfeeds/4719148.cms", "TOI Sports", "sports", enabled = false),
    FeedSource("https://feeds.feedburner.com/gadgets360-latest", "Gadgets 360", "tech", enabled = true),
    FeedSource("https://timesofindia.indiatimes.com/rssfeeds/66949542.cms", "TOI Tech", "tech", enabled = false),
    FeedSource("https://www.moneycontrol.com/rss/latestnews.xml", "Moneycontrol", "business", enabled = false),
    FeedSource("https://economictimes.indiatimes.com/markets/rssfeeds/1977021501.cms", "ET Markets", "business", enabled = false),
    FeedSource("https://feeds.feedburner.com/ndtvcooks-latest", "NDTV Food", "food", enabled = false),
)

/**
 * Generic international default feeds (English-language, globally reachable), used
 * for any device country other than India. Tagged by the same [FEED_CATEGORIES] as
 * [DEFAULT_FEED_SOURCES] so they drop into the same category UI/toggles; "cricket"
 * has no sensible global equivalent and is simply left unpopulated. All
 * `https://` — plain `http://` is blocked by Android's default cleartext policy
 * (targetSdk ≥ 28) and `FeedRefreshWorker`'s fetch sets no network security config
 * exception, so an `http://` source would silently never populate (verified live: the
 * former `entertainment_arts` path here also 302'd to a 404 over https — both bugs
 * fixed in the same pass, see DECISIONS "Feed sources must be https and dead-link
 * verified").
 */
val INTERNATIONAL_FEED_SOURCES: List<FeedSource> = listOf(
    FeedSource("https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en", "Google News", "nation", enabled = true),
    FeedSource("https://feeds.bbci.co.uk/news/world/rss.xml", "BBC World", "nation", enabled = true),
    FeedSource("https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml", "BBC Entertainment", "entertainment", enabled = true),
    FeedSource("https://feeds.bbci.co.uk/sport/rss.xml?edition=int", "BBC Sport", "sports", enabled = true),
    FeedSource("https://feeds.bbci.co.uk/news/technology/rss.xml", "BBC Technology", "tech", enabled = true),
    FeedSource("https://feeds.bbci.co.uk/news/business/rss.xml", "BBC Business", "business", enabled = false),
    FeedSource("https://rss.nytimes.com/services/xml/rss/nyt/Food.xml", "NYT Food", "food", enabled = false),
)

/** Two-letter ISO country code the India-specific default feeds are tuned for. */
const val INDIA_COUNTRY_CODE = "IN"

/**
 * Region code stored for the manual "international" choice in feed settings — not a
 * real ISO country, just a value [defaultFeedSourcesForCountry] treats as "none of the
 * named countries below", falling back to the generic [INTERNATIONAL_FEED_SOURCES].
 */
const val INTERNATIONAL_REGION_CODE = "INTL"

/** A selectable country preset: its ISO code and display name for the region picker. */
data class CountryPreset(val code: String, val displayName: String)

/**
 * Countries with a generated (Google News-templated) feed preset, beyond India's own
 * hand-curated list — the ~20-country spread requested for the region picker. Kept to
 * major markets rather than an exhaustive country list, per DECISIONS "Feed region:
 * Google News-templated country presets".
 */
val SELECTABLE_COUNTRIES: List<CountryPreset> = listOf(
    CountryPreset("US", "United States"),
    CountryPreset("GB", "United Kingdom"),
    CountryPreset("AU", "Australia"),
    CountryPreset("CA", "Canada"),
    CountryPreset("DE", "Germany"),
    CountryPreset("FR", "France"),
    CountryPreset("JP", "Japan"),
    CountryPreset("CN", "China"),
    CountryPreset("HK", "Hong Kong"),
    CountryPreset("TW", "Taiwan"),
    CountryPreset("BR", "Brazil"),
    CountryPreset("SG", "Singapore"),
    CountryPreset("AE", "United Arab Emirates"),
    CountryPreset("PK", "Pakistan"),
    CountryPreset("BD", "Bangladesh"),
    CountryPreset("ZA", "South Africa"),
    CountryPreset("NG", "Nigeria"),
    CountryPreset("ID", "Indonesia"),
    CountryPreset("PH", "Philippines"),
    CountryPreset("MX", "Mexico"),
    CountryPreset("IT", "Italy"),
    CountryPreset("ES", "Spain"),
)

/**
 * A Google News RSS URL for [countryCode], optionally scoped to a [topic] section
 * (`"BUSINESS"`/`"TECHNOLOGY"`/`"ENTERTAINMENT"`/`"SPORTS"` — Google's own topic
 * slugs); `topic = null` is the plain top-stories edition. `hl` is pinned to
 * `en-US` (rather than varying per country) since the app's UI/parsing assumes
 * English content throughout; `gl`/`ceid` alone scope the edition to the country.
 */
private fun googleNewsFeed(countryCode: String, topic: String? = null): String {
    val section = topic?.let { "/headlines/section/topic/$it" }.orEmpty()
    return "https://news.google.com/rss$section?hl=en-US&gl=$countryCode&ceid=$countryCode:en"
}

/**
 * Real, image-bearing top-stories source overriding the generated Google News
 * edition in [countryFeedSources]'s "nation" slot, for the handful of countries this
 * matters most for (requested explicitly: US/UK/Australia/Canada/UAE — the highest-
 * eCPM markets plus UAE). Google News RSS carries **no per-article image at all**
 * (verified directly against a live feed — no `media:content`/`enclosure`, and the
 * `<description>` is just a text link list), so every article from a Google-News-only
 * country renders as a bare text card. Each of these was live-verified: reachable over
 * https (not just http — see [INTERNATIONAL_FEED_SOURCES]'s doc comment) and actually
 * carrying `media:content`/`media:thumbnail`/`enclosure` or an inline `<img>` the
 * existing parser already extracts. CNN was tried for the US and rejected — its feed
 * only serves over plain http (https handshake fails), which cleartext policy blocks.
 */
private val CURATED_TOP_STORIES: Map<String, FeedSource> = mapOf(
    "US" to FeedSource("https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml", "NYT", "nation", enabled = true),
    "GB" to FeedSource("https://feeds.bbci.co.uk/news/uk/rss.xml", "BBC UK", "nation", enabled = true),
    "AU" to FeedSource("https://www.abc.net.au/news/feed/51120/rss.xml", "ABC News", "nation", enabled = true),
    "CA" to FeedSource("https://www.cbc.ca/webfeed/rss/rss-topstories", "CBC News", "nation", enabled = true),
    "AE" to FeedSource("https://www.gulftoday.ae/rssFeed/0/", "Gulf Today", "nation", enabled = true),
)

private fun src(url: String, name: String, category: String = "nation", enabled: Boolean = true) =
    FeedSource(url, name, category, enabled)

/**
 * Whole hand-picked feed sets for countries Google News cannot template: its English edition for CN / HK / TW
 * answers with the US one (checked live), so these are real outlets instead. Every URL was fetched over https
 * and returned items (the SCMP section numbers are its own: 4 China, 2 Hong Kong, 92 Business, 36 Tech, 95 Sport,
 * 322296 Culture, 318421 China Economy).
 */
private val CURATED_COUNTRY_FEEDS: Map<String, List<FeedSource>> = mapOf(
    "CN" to listOf(
        src("https://www.cgtn.com/subscribe/rss/section/china.xml", "CGTN China"),
        src("https://www.scmp.com/rss/4/feed/", "SCMP China"),
        src("https://english.news.cn/rss/chinarss.xml", "Xinhua"),
        src("https://en.people.cn/rss/90785.xml", "People's Daily", enabled = false),
        src("https://www.globaltimes.cn/rss/outbrain.xml", "Global Times", enabled = false),
        src("https://api.sixthtone.com/cont/output/rssApi", "Sixth Tone", enabled = false),
        src("https://www.cgtn.com/subscribe/rss/section/culture.xml", "CGTN Culture", "entertainment"),
        src("https://www.cgtn.com/subscribe/rss/section/sports.xml", "CGTN Sports", "sports"),
        src("https://www.scmp.com/rss/36/feed/", "SCMP Tech", "tech"),
        src("https://www.scmp.com/rss/318421/feed/", "SCMP China Economy", "business", enabled = false),
        src("https://www.cgtn.com/subscribe/rss/section/business.xml", "CGTN Business", "business", enabled = false),
    ),
    "HK" to listOf(
        src("https://www.scmp.com/rss/2/feed/", "SCMP Hong Kong"),
        src("https://hongkongfp.com/feed/", "Hong Kong Free Press"),
        src("https://rthk9.rthk.hk/rthk/news/rss/e_expressnews_elocal.xml", "RTHK News"),
        src("https://www.scmp.com/rss/322296/feed/", "SCMP Culture", "entertainment"),
        src("https://www.scmp.com/rss/95/feed/", "SCMP Sport", "sports"),
        src("https://www.scmp.com/rss/36/feed/", "SCMP Tech", "tech"),
        src("https://www.scmp.com/rss/92/feed/", "SCMP Business", "business", enabled = false),
    ),
    "TW" to listOf(
        src("https://feeds.feedburner.com/rsscna/engnews", "Focus Taiwan"),
        src("https://www.taipeitimes.com/xml/index.rss", "Taipei Times"),
    ),
)

/**
 * Generates a small feed set for a [SELECTABLE_COUNTRIES] entry, purely from its ISO
 * [countryCode] for most countries — no per-country manual curation, so there's no
 * dead-URL risk beyond Google News itself. The "nation" slot uses [CURATED_TOP_STORIES]
 * instead where one exists (real images, vs. Google News' text-only articles).
 * Enabled defaults mirror [INTERNATIONAL_FEED_SOURCES]'s: nation/entertainment/sports/
 * tech on, business off. No food/state/cricket equivalent, same as the other non-India
 * presets.
 */
fun countryFeedSources(countryCode: String): List<FeedSource> {
    val cc = countryCode.uppercase()
    CURATED_COUNTRY_FEEDS[cc]?.let { return it }
    val name = SELECTABLE_COUNTRIES.firstOrNull { it.code == cc }?.displayName ?: cc
    val nation = CURATED_TOP_STORIES[cc] ?: FeedSource(googleNewsFeed(cc), "Google News · $name", "nation", enabled = true)
    return listOf(
        nation,
        FeedSource(googleNewsFeed(cc, "ENTERTAINMENT"), "Google News · Entertainment", "entertainment", enabled = true),
        FeedSource(googleNewsFeed(cc, "SPORTS"), "Google News · Sports", "sports", enabled = true),
        FeedSource(googleNewsFeed(cc, "TECHNOLOGY"), "Google News · Technology", "tech", enabled = true),
        FeedSource(googleNewsFeed(cc, "BUSINESS"), "Google News · Business", "business", enabled = false),
    )
}

/** Region codes of the picker's "languages" group start with this, so they can never collide with an ISO country code. */
const val NEWS_LANGUAGE_PREFIX = "LANG_"

/** Whether [regionCode] is a news language (Hindi, Tamil, Chinese…) rather than a country or "international". */
fun isNewsLanguage(regionCode: String): Boolean = regionCode.uppercase().startsWith(NEWS_LANGUAGE_PREFIX)

/**
 * News in a language rather than a country: the Indian regional languages and Chinese. Each is a region like any
 * other (several can be on at once, each adds its own feeds), shown under "languages" in the pickers.
 */
val NEWS_LANGUAGES: List<CountryPreset> = listOf(
    CountryPreset("LANG_HI", "Hindi (हिन्दी)"),
    CountryPreset("LANG_MR", "Marathi (मराठी)"),
    CountryPreset("LANG_GU", "Gujarati (ગુજરાતી)"),
    CountryPreset("LANG_BN", "Bengali (বাংলা)"),
    CountryPreset("LANG_TA", "Tamil (தமிழ்)"),
    CountryPreset("LANG_TE", "Telugu (తెలుగు)"),
    CountryPreset("LANG_KN", "Kannada (ಕನ್ನಡ)"),
    CountryPreset("LANG_ML", "Malayalam (മലയാളം)"),
    CountryPreset("LANG_PA", "Punjabi (ਪੰਜਾਬੀ)"),
    CountryPreset("LANG_OR", "Odia (ଓଡ଼ିଆ)"),
    CountryPreset("LANG_UR", "Urdu (اردو)"),
    CountryPreset("LANG_AS", "Assamese (অসমীয়া)"),
    CountryPreset("LANG_ZH", "Chinese (中文)"),
)

/**
 * Google News in a language: top stories plus its entertainment / sports / tech / business sections. Google only
 * serves some languages (Kannada, Odia, Urdu and Assamese fall back to Hindi, checked live), so those get none.
 * [ceid] is Google's edition id ("IN:hi", "CN:zh-Hans"). Text only, like every Google News feed.
 */
private fun googleNewsInLanguage(hl: String, gl: String, ceid: String, name: String): List<FeedSource> {
    fun url(topic: String? = null): String {
        val section = topic?.let { "/headlines/section/topic/$it" }.orEmpty()
        return "https://news.google.com/rss$section?hl=$hl&gl=$gl&ceid=$ceid"
    }
    return listOf(
        src(url(), "Google News · $name"),
        src(url("ENTERTAINMENT"), "Google News · $name · entertainment", "entertainment"),
        src(url("SPORTS"), "Google News · $name · sports", "sports"),
        src(url("TECHNOLOGY"), "Google News · $name · tech", "tech"),
        src(url("BUSINESS"), "Google News · $name · business", "business", enabled = false),
    )
}

/**
 * The feeds of each [NEWS_LANGUAGES] entry, all fetched over https with items in the language when they were
 * checked: the BBC's language services, the TV9 / ABP / News18 / Asianet / OneIndia regional sites and the big
 * regional papers that publish RSS, with Google News added where it supports the language. Several are plain
 * text; the rest carry pictures. The first few are on by default, the rest are opt-in from the feed list.
 */
private val LANGUAGE_FEEDS: Map<String, List<FeedSource>> = mapOf(
    "LANG_HI" to listOf(
        src("https://www.bhaskar.com/rss-v1--category-1061.xml", "Dainik Bhaskar"),
        src("https://hindi.news18.com/rss/khabar/nation/nation.xml", "News18 Hindi"),
        src("https://feeds.bbci.co.uk/hindi/rss.xml", "BBC Hindi"),
        src("https://api.livehindustan.com/feeds/rss/national/rssfeed.xml", "Live Hindustan", enabled = false),
        src("https://www.abplive.com/home/feed", "ABP Live", enabled = false),
    ) + googleNewsInLanguage("hi", "IN", "IN:hi", "Hindi"),
    "LANG_MR" to listOf(
        src("https://feeds.bbci.co.uk/marathi/rss.xml", "BBC Marathi"),
        src("https://www.tv9marathi.com/feed", "TV9 Marathi"),
        src("https://saamtv.esakal.com/stories.rss", "Saam TV"),
        src("https://marathi.abplive.com/home/feed", "ABP Majha", enabled = false),
        src("https://marathi.oneindia.com/rss/marathi-news-fb.xml", "OneIndia Marathi", enabled = false),
    ) + googleNewsInLanguage("mr", "IN", "IN:mr", "Marathi"),
    "LANG_GU" to listOf(
        src("https://feeds.bbci.co.uk/gujarati/rss.xml", "BBC Gujarati"),
        src("https://tv9gujarati.com/feed", "TV9 Gujarati"),
        src("https://gujarati.oneindia.com/rss/gujarati-news-fb.xml", "OneIndia Gujarati", enabled = false),
    ) + googleNewsInLanguage("gu", "IN", "IN:gu", "Gujarati"),
    "LANG_BN" to listOf(
        src("https://feeds.bbci.co.uk/bengali/rss.xml", "BBC Bengali"),
        src("https://tv9bangla.com/feed", "TV9 Bangla"),
        src("https://bengali.abplive.com/home/feed", "ABP Ananda", enabled = false),
        src("https://www.prothomalo.com/stories.rss", "Prothom Alo", enabled = false),
        src("https://bengali.oneindia.com/rss/bengali-news-fb.xml", "OneIndia Bengali", enabled = false),
    ) + googleNewsInLanguage("bn", "IN", "IN:bn", "Bengali"),
    "LANG_TA" to listOf(
        src("https://feeds.bbci.co.uk/tamil/rss.xml", "BBC Tamil"),
        src("https://www.hindutamil.in/stories.rss", "Hindu Tamil Thisai"),
        src("https://tamil.abplive.com/home/feed", "ABP Nadu", enabled = false),
        src("https://tamil.oneindia.com/rss/tamil-news-fb.xml", "OneIndia Tamil", enabled = false),
    ) + googleNewsInLanguage("ta", "IN", "IN:ta", "Tamil"),
    "LANG_TE" to listOf(
        src("https://feeds.bbci.co.uk/telugu/rss.xml", "BBC Telugu"),
        src("https://tv9telugu.com/feed", "TV9 Telugu"),
        src("https://www.sakshi.com/rss.xml", "Sakshi", enabled = false),
        src("https://telugu.abplive.com/home/feed", "ABP Desam", enabled = false),
        src("https://telugu.oneindia.com/rss/telugu-news-fb.xml", "OneIndia Telugu", enabled = false),
    ) + googleNewsInLanguage("te", "IN", "IN:te", "Telugu"),
    "LANG_KN" to listOf(
        src("https://kannada.asianetnews.com/rss", "Asianet Suvarna News"),
        src("https://tv9kannada.com/feed", "TV9 Kannada"),
        src("https://kannada.oneindia.com/rss/kannada-news-fb.xml", "OneIndia Kannada", enabled = false),
    ),
    "LANG_ML" to listOf(
        src("https://www.mathrubhumi.com/rss", "Mathrubhumi"),
        src("https://www.asianetnews.com/rss", "Asianet News"),
        src("https://malayalam.oneindia.com/rss/malayalam-news-fb.xml", "OneIndia Malayalam", enabled = false),
    ) + googleNewsInLanguage("ml", "IN", "IN:ml", "Malayalam"),
    "LANG_PA" to listOf(
        src("https://feeds.bbci.co.uk/punjabi/rss.xml", "BBC Punjabi"),
        src("https://punjabi.abplive.com/home/feed", "ABP Sanjha"),
    ) + googleNewsInLanguage("pa", "IN", "IN:pa", "Punjabi"),
    "LANG_OR" to listOf(
        src("https://odishatv.in/rss", "OdishaTV"),
        src("https://www.dharitri.com/feed/", "Dharitri"),
        src("https://odia.oneindia.com/rss/odia-news-fb.xml", "OneIndia Odia", enabled = false),
    ),
    "LANG_UR" to listOf(
        src("https://feeds.bbci.co.uk/urdu/rss.xml", "BBC Urdu"),
    ),
    "LANG_AS" to listOf(
        src("https://www.pratidintime.com/rss", "Pratidin Time"),
    ),
    "LANG_ZH" to listOf(
        src("https://feeds.bbci.co.uk/zhongwen/simp/rss.xml", "BBC 中文"),
        src("https://www.rfi.fr/cn/rss", "RFI 中文"),
        src("https://rss.dw.com/xml/rss-chi-all", "DW 中文"),
        src("https://cn.nytimes.com/rss/", "纽约时报中文网", enabled = false),
        src("https://www.ftchinese.com/rss/news", "FT 中文网", enabled = false),
        src("https://www.people.com.cn/rss/politics.xml", "人民网", enabled = false),
        src("https://news.ltn.com.tw/rss/all.xml", "自由時報", enabled = false),
        src("https://feeds.feedburner.com/rsscna/politics", "中央社", enabled = false),
    ) + googleNewsInLanguage("zh-CN", "CN", "CN:zh-Hans", "中文"),
)

/**
 * The picker's groups in display order, each a heading and its region codes: India and the world-wide edition, the
 * named countries, then the languages. One list so every picker (personalize, the feed page's settings, the news
 * hub's settings) shows the same options in the same order.
 */
val NEWS_REGION_GROUPS: List<Pair<String, List<String>>> = listOf(
    "india and world" to listOf(INDIA_COUNTRY_CODE, INTERNATIONAL_REGION_CODE),
    "countries" to SELECTABLE_COUNTRIES.map { it.code },
    "languages" to NEWS_LANGUAGES.map { it.code },
)

/** Display name for any region code the picker/settings UI shows (India/International/a named country/a language). */
fun regionDisplayName(regionCode: String): String = when {
    regionCode.equals(INDIA_COUNTRY_CODE, ignoreCase = true) -> "India"
    regionCode.equals(INTERNATIONAL_REGION_CODE, ignoreCase = true) -> "International"
    else -> (SELECTABLE_COUNTRIES + NEWS_LANGUAGES).firstOrNull { it.code.equals(regionCode, ignoreCase = true) }?.displayName ?: regionCode
}

/**
 * Picks the default feed preset for a device/locale or manually-chosen [countryCode]:
 * the curated India list for `IN`, a language's own list for a [NEWS_LANGUAGES] code, a generated [countryFeedSources] set for any of
 * [SELECTABLE_COUNTRIES], else the generic [INTERNATIONAL_FEED_SOURCES] fallback (also
 * what an unrecognised/blank locale resolves to). Pure + exhaustive so it's
 * unit-testable without Android's Locale machinery.
 */
fun defaultFeedSourcesForCountry(countryCode: String): List<FeedSource> {
    val cc = countryCode.uppercase()
    return when {
        cc == INDIA_COUNTRY_CODE -> DEFAULT_FEED_SOURCES
        cc in LANGUAGE_FEEDS -> LANGUAGE_FEEDS.getValue(cc)
        SELECTABLE_COUNTRIES.any { it.code == cc } -> countryFeedSources(cc)
        else -> INTERNATIONAL_FEED_SOURCES
    }
}

private val RSS_DATE_FORMATS = listOf(
    "EEE, dd MMM yyyy HH:mm:ss Z",   // RFC-822 with numeric offset
    "EEE, dd MMM yyyy HH:mm:ss zzz", // RFC-822 with zone name (GMT/UTC)
    "yyyy-MM-dd'T'HH:mm:ssXXX",      // RFC-3339 / Atom
    "yyyy-MM-dd'T'HH:mm:ss'Z'",      // RFC-3339 UTC literal Z
    "yyyy-MM-dd'T'HH:mm:ssZ",        // RFC-3339 numeric offset, no colon
)

/** Parses an RSS `pubDate` / Atom `published` string to epoch millis, or null. */
fun parseFeedDate(raw: String?): Long? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    for (pattern in RSS_DATE_FORMATS) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = true
            }.parse(text)
        }.getOrNull()
        if (parsed != null) return parsed.time
    }
    return null
}

/**
 * Strips HTML tags and unescapes the common entities from a feed title/snippet,
 * collapsing whitespace. Feed descriptions are frequently HTML; this yields plain
 * text safe to render in a Compose `Text`.
 */
fun stripHtml(raw: String?): String {
    if (raw.isNullOrEmpty()) return ""
    return raw
        .replace(Regex("(?s)<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/** Relative "time ago" label for [publishedMillis] vs [nowMillis] (now/Xm/Xh/Xd). */
fun feedAgo(publishedMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val delta = nowMillis - publishedMillis
    if (publishedMillis <= 0L || delta < 0L) return "now"
    val minutes = delta / 60_000L
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 1_440 -> "${minutes / 60}h"
        else -> "${minutes / 1_440}d"
    }
}

/**
 * Parses an RSS 2.0 or Atom document into articles. Namespace-unaware DOM, so
 * prefixed tags (`media:content`, `media:thumbnail`) match by their literal name.
 * [sourceName] labels each article (the channel/feed title wins when present).
 * Any parse failure yields an empty list — a broken feed degrades to "no articles".
 */
fun parseFeed(xml: String, sourceName: String, feedUrl: String = ""): List<FeedArticle> = runCatching {
    // Hardened: feeds are arbitrary third-party URLs (the user can add any
    // custom feed), so this parses genuinely untrusted XML. RSS and Atom never
    // need a DOCTYPE, and allowing one exposes entity-expansion ("billion
    // laughs") memory blowups and external-entity resolution. Disallowing
    // doctypes outright closes both; the other flags are belt-and-braces for
    // parsers that honour them.
    val doc = DocumentBuilderFactory.newInstance()
        .apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { isExpandEntityReferences = false }
            runCatching { isXIncludeAware = false }
        }
        .newDocumentBuilder()
        .parse(xml.byteInputStream())
    doc.documentElement?.normalize()

    val channelTitle = firstChildText(doc.documentElement, "title")
    val label = channelTitle?.takeIf { it.isNotBlank() } ?: sourceName

    val items = doc.getElementsByTagName("item")
    val nodes = if (items.length > 0) items else doc.getElementsByTagName("entry")
    (0 until nodes.length).mapNotNull { i ->
        (nodes.item(i) as? Element)?.let { articleFrom(it, label, feedUrl) }
    }
}.getOrDefault(emptyList())

private fun articleFrom(item: Element, source: String, feedUrl: String): FeedArticle? {
    val title = stripHtml(childText(item, "title"))
    if (title.isEmpty()) return null
    val link = linkOf(item)
    val date = parseFeedDate(childText(item, "pubDate"))
        ?: parseFeedDate(childText(item, "published"))
        ?: parseFeedDate(childText(item, "updated"))
        ?: 0L
    val tag = stripHtml(childText(item, "category")).lowercase().takeIf { it.isNotEmpty() && it.length <= 24 }
    return FeedArticle(
        title = title,
        link = link,
        source = source,
        tag = tag ?: "news",
        imageUrl = imageOf(item),
        publishedAtMillis = date,
        feedUrl = feedUrl,
    )
}

/** RSS `<link>text</link>` or Atom `<link href="…">` (prefers an alternate link). */
private fun linkOf(item: Element): String {
    val text = childText(item, "link")?.trim()
    if (!text.isNullOrEmpty()) return text
    val links = item.getElementsByTagName("link")
    for (i in 0 until links.length) {
        val el = links.item(i) as? Element ?: continue
        val rel = el.getAttribute("rel")
        if (rel.isNullOrEmpty() || rel == "alternate") {
            val href = el.getAttribute("href")
            if (href.isNotEmpty()) return href
        }
    }
    return ""
}

/**
 * First image URL for an item, trying the common feed conventions in order:
 * media:content / media:thumbnail (incl. inside media:group), an image enclosure,
 * itunes:image, then an `<img>` (or lazy `data-src`) inside the HTML body
 * (content:encoded / description / content). Returns null when none is present.
 */
private fun imageOf(item: Element): String? {
    listOf("media:content", "media:thumbnail").forEach { tag ->
        val nodes = item.getElementsByTagName(tag)
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            val type = el.getAttribute("type")
            if (type.isNotEmpty() && !type.startsWith("image")) continue // skip video/audio media
            val url = el.getAttribute("url")
            if (url.isNotEmpty()) return normalizeImageUrl(url)
        }
    }
    val enclosures = item.getElementsByTagName("enclosure")
    for (i in 0 until enclosures.length) {
        val el = enclosures.item(i) as? Element ?: continue
        val type = el.getAttribute("type")
        val url = el.getAttribute("url")
        if (url.isNotEmpty() && (type.isNullOrEmpty() || type.startsWith("image"))) {
            return normalizeImageUrl(url)
        }
    }
    val itunes = item.getElementsByTagName("itunes:image")
    (itunes.item(0) as? Element)?.getAttribute("href")?.takeIf { it.isNotEmpty() }
        ?.let { return normalizeImageUrl(it) }

    // ESPNcricinfo and some Indian feeds carry the image as a <coverImages> element.
    childText(item, "coverImages")?.takeIf { it.startsWith("http") || it.startsWith("//") }
        ?.let { return normalizeImageUrl(it) }

    val html = listOf("content:encoded", "description", "content")
        .joinToString(" ") { childText(item, it).orEmpty() }
    return Regex("<img[^>]+(?:src|data-src)=[\"']([^\"']+)[\"']").find(html)
        ?.groupValues?.getOrNull(1)
        ?.let { normalizeImageUrl(it) }
}

/**
 * Normalises an image URL so it actually loads on Android: protocol-relative
 * `//host` → https, and **cleartext `http://` → `https://`** (Android blocks
 * cleartext by default, and the image hosts that use it — e.g. ESPNcricinfo's
 * `p.imgci.com` — also serve https).
 */
private fun normalizeImageUrl(url: String): String = when {
    url.startsWith("//") -> "https:$url"
    url.startsWith("http://") -> "https://" + url.substring("http://".length)
    else -> url
}

/** Text of the first direct-or-descendant child element named [name]. */
private fun childText(parent: Element, name: String): String? {
    val nodes = parent.getElementsByTagName(name)
    return (nodes.item(0))?.textContent?.trim()
}

private fun firstChildText(parent: Element?, name: String): String? {
    parent ?: return null
    val nodes = parent.getElementsByTagName(name)
    return (0 until nodes.length).asSequence()
        .map { nodes.item(it) }
        .firstOrNull { it.nodeType == Node.ELEMENT_NODE }
        ?.textContent?.trim()
}
