package com.tileshell.feature.livetiles

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The news hub's data: live news channels by country, the user's choice of
 * them, the saved ("read later") articles and which articles were opened.
 * Everything here is pure or plain file / preference storage so it can be
 * unit-tested; the screen is in `NewsHubScreen`.
 */

/** A live news channel: its YouTube [handle] (the part after `@`), name, language and the news region it belongs to. */
data class LiveChannel(
    val handle: String,
    val name: String,
    val language: String,
    val region: String,
    /** Part of the starter set a country shows before anything is chosen. */
    val default: Boolean = true,
)

/**
 * Channels known to broadcast on YouTube, probed live on 2026-10-07 (the live
 * link `youtube.com/@handle/live` always points at the channel's current
 * stream). Countries with no entry start empty and the user adds a channel by
 * its YouTube name. The region codes are the news feed's own
 * ([SELECTABLE_COUNTRIES], [INDIA_COUNTRY_CODE], [INTERNATIONAL_REGION_CODE]).
 */
val LIVE_CHANNELS: List<LiveChannel> = listOf(
    LiveChannel("NDTV", "NDTV 24x7", "english", "IN"),
    LiveChannel("IndiaToday", "India Today", "english", "IN"),
    LiveChannel("TimesNow", "Times Now", "english", "IN", default = false),
    LiveChannel("RepublicWorld", "Republic", "english", "IN", default = false),
    LiveChannel("mirrornow", "Mirror Now", "english", "IN", default = false),
    LiveChannel("aajtak", "Aaj Tak", "hindi", "IN"),
    LiveChannel("ABPNews", "ABP News", "hindi", "IN"),
    LiveChannel("ZeeNews", "Zee News", "hindi", "IN", default = false),
    LiveChannel("News18India", "News18 India", "hindi", "IN", default = false),
    LiveChannel("indiatv", "India TV", "hindi", "IN", default = false),
    LiveChannel("TV9Bharatvarsh", "TV9 Bharatvarsh", "hindi", "IN", default = false),
    LiveChannel("ndtvindia", "NDTV India", "hindi", "IN", default = false),
    LiveChannel("TIMESNOWNavbharat", "Times Now Navbharat", "hindi", "IN", default = false),
    LiveChannel("zee24taas", "Zee 24 Taas", "marathi", "IN"),
    LiveChannel("SaamTV", "Saam TV", "marathi", "IN"),
    LiveChannel("ZeeBusiness", "Zee Business", "business", "IN", default = false),
    LiveChannel("ETNowSwadesh", "ET Now Swadesh", "business", "IN", default = false),

    LiveChannel("ABCNews", "ABC News", "english", "US"),
    LiveChannel("NBCNews", "NBC News", "english", "US"),
    LiveChannel("CBSNews", "CBS News", "english", "US"),
    LiveChannel("CNN", "CNN", "english", "US"),
    LiveChannel("AssociatedPress", "Associated Press", "english", "US", default = false),

    LiveChannel("SkyNews", "Sky News", "english", "GB"),
    LiveChannel("TalkTV", "TalkTV", "english", "GB", default = false),

    LiveChannel("abcnewsaustralia", "ABC News Australia", "english", "AU"),
    LiveChannel("CBCNews", "CBC News", "english", "CA"),
    LiveChannel("CTVNews", "CTV News", "english", "CA", default = false),
    LiveChannel("globalnews", "Global News", "english", "CA", default = false),

    LiveChannel("DWNews", "DW News", "english", "DE"),
    LiveChannel("franceinfo", "franceinfo", "french", "FR"),
    LiveChannel("BFMTV", "BFMTV", "french", "FR"),
    LiveChannel("ANNnewsCH", "ANN News", "japanese", "JP"),
    LiveChannel("TBSNEWSDIG", "TBS News Dig", "japanese", "JP", default = false),
    LiveChannel("channelnewsasia", "CNA", "english", "SG"),
    LiveChannel("AlArabiya", "Al Arabiya", "arabic", "AE"),
    LiveChannel("skynewsarabia", "Sky News Arabia", "arabic", "AE"),
    LiveChannel("GeoNews", "Geo News", "urdu", "PK"),
    LiveChannel("SamaaTV", "Samaa TV", "urdu", "PK", default = false),
    LiveChannel("tvOneNews", "tvOne News", "indonesian", "ID"),
    LiveChannel("KompasTV", "Kompas TV", "indonesian", "ID"),
    LiveChannel("ABSCBNNews", "ABS-CBN News", "english", "PH"),
    LiveChannel("GMANews", "GMA News", "english", "PH"),
    LiveChannel("Milenio", "Milenio", "spanish", "MX"),
    LiveChannel("DWEspanol", "DW Español", "spanish", "ES"),
    LiveChannel("SkyTG24", "Sky TG24", "italian", "IT"),

    LiveChannel("aljazeeraenglish", "Al Jazeera English", "english", INTERNATIONAL_REGION_CODE),
    LiveChannel("DWNews", "DW News", "english", INTERNATIONAL_REGION_CODE),
    LiveChannel("France24_en", "France 24", "english", INTERNATIONAL_REGION_CODE),
    LiveChannel("euronews", "Euronews", "english", INTERNATIONAL_REGION_CODE),
    LiveChannel("SkyNews", "Sky News", "english", INTERNATIONAL_REGION_CODE),
)

/** The channels the catalog has for [region], starter set first. */
fun channelsForRegion(region: String): List<LiveChannel> =
    LIVE_CHANNELS.filter { it.region.equals(region, ignoreCase = true) }.sortedByDescending { it.default }

/**
 * What the live tv page shows: the starter channels of every followed region
 * until the user chooses ([chosen] null), then exactly the chosen handles
 * (those of the catalog and the [custom] ones), in catalog order, no repeats. Pure.
 */
fun effectiveLiveChannels(regions: Set<String>, chosen: Set<String>?, custom: List<LiveChannel>): List<LiveChannel> {
    val shownRegions = regions.ifEmpty { setOf(INTERNATIONAL_REGION_CODE) }
    val pool = (LIVE_CHANNELS + custom).distinctBy { it.region.uppercase() + "/" + it.handle.lowercase() }
    val picked = if (chosen == null) {
        pool.filter { it.default && shownRegions.any { r -> r.equals(it.region, ignoreCase = true) } }
    } else {
        pool.filter { it.handle in chosen }
    }
    return picked
}

/** The link that always opens the channel's current live stream. */
fun liveUrl(handle: String): String = "https://www.youtube.com/@${handle.trim().removePrefix("@")}/live"

/** A YouTube handle from what a person types: "@NDTV", "NDTV", a channel link, with stray spaces dropped; blank when nothing usable. Pure. */
fun youtubeHandleOf(input: String): String {
    var s = input.trim()
    s = s.substringAfter("youtube.com/", s).substringBefore('?').substringBefore("/live").trim('/')
    s = s.removePrefix("@")
    return s.filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }
}

/** Whether a channel is on air now, and the current video's id when the page gave one (for its thumbnail). */
data class LiveStatus(val live: Boolean, val videoId: String? = null)

/**
 * Reads a channel's `/live` page: "isLiveNow" says it is broadcasting; the stream is the last
 * "videoId" before that flag (the page carries other ids earlier, and for some channels the flag
 * sits near the end of a ~1 MB page). Pure.
 */
fun parseLiveStatus(html: String): LiveStatus {
    val marker = "\"isLiveNow\":true"
    var at = html.indexOf(marker)
    if (at < 0) {
        at = html.indexOf("\"isLive\":true")
        if (at < 0) return LiveStatus(false)
    }
    val window = html.substring(maxOf(0, at - 60_000), at)
    val ids = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"").findAll(window).map { it.groupValues[1] }.toList()
    val id = ids.lastOrNull() ?: Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"").find(html)?.groupValues?.get(1)
    return LiveStatus(true, id)
}

/** A live thumbnail for [videoId]. */
fun liveThumbnailUrl(videoId: String): String = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"

/** The most of a channel page read looking for its live flag. */
private const val LIVE_PAGE_MAX_CHARS = 2_500_000

private const val LIVE_PAGE_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

/** Reads one channel's status from YouTube's own page (not an official API, so any failure is simply "unknown"). */
suspend fun fetchLiveStatus(handle: String): LiveStatus? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = (URL(liveUrl(handle)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", LIVE_PAGE_UA)
            setRequestProperty("Accept-Language", "en")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            // The page is large (up to ~1 MB, sent compressed) and the flag may sit near its end: read on until
            // it shows (then a little more) or the cap, so a channel on air is found and one off air costs one page.
            val sb = StringBuilder()
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buf = CharArray(64 * 1024)
                var stop = -1
                while (sb.length < LIVE_PAGE_MAX_CHARS) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                    if (stop < 0 && sb.contains("\"isLiveNow\":true")) stop = sb.length + 4_000
                    if (stop in 0..sb.length) break
                }
            }
            val text = sb.toString()
            parseLiveStatus(text)
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}

/** The user's live tv choices, in `tileshell.prefs` (so a backup carries them). */
object NewsLivePrefs {
    private const val PREFS = "tileshell.prefs"
    const val KEY_CHOSEN = "news_live_chosen"
    const val KEY_CUSTOM = "news_live_custom"

    /** Chosen handles, or null while the user has never chosen (the starter sets show). */
    fun chosen(context: Context): Set<String>? {
        val raw = prefs(context).getString(KEY_CHOSEN, null) ?: return null
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun custom(context: Context): List<LiveChannel> = decodeCustom(prefs(context).getString(KEY_CUSTOM, null))

    /** Turns [channel] on or off, starting from [current] (what the page shows) the first time. */
    fun toggle(context: Context, channel: LiveChannel, current: List<LiveChannel>) {
        val set = (chosen(context) ?: current.map { it.handle }.toSet()).toMutableSet()
        if (!set.add(channel.handle)) set.remove(channel.handle)
        prefs(context).edit().putString(KEY_CHOSEN, set.joinToString(",")).apply()
    }

    /** Adds a channel by its YouTube name and turns it on. */
    fun addCustom(context: Context, channel: LiveChannel, current: List<LiveChannel>) {
        val list = (custom(context).filterNot { it.handle.equals(channel.handle, true) } + channel)
        val set = (chosen(context) ?: current.map { it.handle }.toSet()) + channel.handle
        prefs(context).edit()
            .putString(KEY_CUSTOM, encodeCustom(list))
            .putString(KEY_CHOSEN, set.joinToString(","))
            .apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

internal fun encodeCustom(list: List<LiveChannel>): String =
    list.joinToString("\n") { listOf(it.handle, it.name, it.language, it.region).joinToString("\t") { f -> f.replace('\t', ' ').replace('\n', ' ') } }

internal fun decodeCustom(text: String?): List<LiveChannel> = text.orEmpty().lines().mapNotNull { line ->
    val f = line.split('\t')
    if (f.size < 4 || f[0].isBlank()) null else LiveChannel(f[0], f[1].ifBlank { f[0] }, f[2], f[3])
}

// --- saved and read articles -----------------------------------------------------

internal fun encodeSaved(articles: List<FeedArticle>): String = articles.joinToString("\n") { a ->
    listOf(a.title, a.link, a.source, a.tag, a.imageUrl.orEmpty(), a.publishedAtMillis.toString())
        .joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ').trim() }
}

internal fun decodeSaved(text: String?): List<FeedArticle> = text.orEmpty().lines().mapNotNull { line ->
    val f = line.split('\t')
    if (f.size < 6 || f[1].isBlank()) null
    else FeedArticle(f[0], f[1], f[2], f[3], f[4].ifBlank { null }, f[5].toLongOrNull() ?: 0L)
}

/** How many saved articles are kept: enough to never matter, bounded so the file stays small. */
const val MAX_SAVED_NEWS = 200

/** How many opened-article links are remembered. */
const val MAX_READ_NEWS = 600

/** Pushes [article] to the front of [saved] or takes it out when already there, keeping at most [MAX_SAVED_NEWS]. Pure. */
fun toggleSaved(saved: List<FeedArticle>, article: FeedArticle): List<FeedArticle> =
    if (saved.any { it.link == article.link }) saved.filterNot { it.link == article.link }
    else (listOf(article) + saved).take(MAX_SAVED_NEWS)

/** Adds [link] to the opened set, dropping the oldest beyond [MAX_READ_NEWS]. Pure. */
fun markRead(read: List<String>, link: String): List<String> =
    (read.filterNot { it == link } + link).takeLast(MAX_READ_NEWS)

/** The news hub's saved ("read later") articles and opened-article memory, kept in two small files on the phone. */
object NewsMarks {
    private val savedFlow = MutableStateFlow<List<FeedArticle>>(emptyList())
    private val readFlow = MutableStateFlow<List<String>>(emptyList())
    @Volatile private var loaded = false

    val saved: StateFlow<List<FeedArticle>> = savedFlow.asStateFlow()
    val read: StateFlow<List<String>> = readFlow.asStateFlow()

    private fun savedFile(context: Context) = File(context.applicationContext.filesDir, "saved_news.txt")
    private fun readFile(context: Context) = File(context.applicationContext.filesDir, "read_news.txt")

    /** Loads both files once (cheap, small); safe to call from composition. */
    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            savedFlow.value = runCatching { decodeSaved(savedFile(context).takeIf { it.isFile }?.readText()) }.getOrDefault(emptyList())
            readFlow.value = runCatching { readFile(context).takeIf { it.isFile }?.readLines().orEmpty().filter { it.isNotBlank() } }.getOrDefault(emptyList())
            loaded = true
        }
    }

    fun toggleSaved(context: Context, article: FeedArticle) {
        load(context)
        val next = toggleSaved(savedFlow.value, article)
        savedFlow.value = next
        runCatching { savedFile(context).writeText(encodeSaved(next)) }
    }

    fun markRead(context: Context, link: String) {
        load(context)
        if (link in readFlow.value) return
        val next = markRead(readFlow.value, link)
        readFlow.value = next
        runCatching { readFile(context).writeText(next.joinToString("\n")) }
    }
}

/**
 * The topic a story belongs to: the category of the feed it came from (national news, sports,
 * technology…), not the story's own tags, which are free words ("paris", "death") that made a
 * poor filter. A story from a feed no longer subscribed falls to "other". Pure.
 */
fun topicOf(article: FeedArticle, sources: List<FeedSource>): String =
    sources.firstOrNull { it.url == article.feedUrl }?.category ?: "other"
