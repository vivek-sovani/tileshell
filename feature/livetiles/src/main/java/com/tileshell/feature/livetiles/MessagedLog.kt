package com.tileshell.feature.livetiles

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Someone who messaged the user, as seen in a chat/SMS notification. */
data class MessagedEntry(val name: String, val packageName: String, val time: Long)

/**
 * Who messaged the user recently, for the People Hub's "favourites" page.
 * Android's own "last contacted" field stopped being kept up to date around
 * Android 10, and the call log needs READ_CALL_LOG (a Play-declared
 * permission), so TileShell keeps this itself from the chat and SMS
 * notifications it already reads for "what's new": one line per person in
 * `files/messaged_log.txt`, newest first, for [KEEP_MS]. Messages only — calls
 * aren't visible to it. Stays on the phone.
 */
object MessagedLog {
    private const val FILE = "messaged_log.txt"
    const val KEEP_MS = 30L * 24 * 60 * 60 * 1000
    const val MAX = 100

    private val _entries = MutableStateFlow<List<MessagedEntry>>(emptyList())
    val entries: StateFlow<List<MessagedEntry>> = _entries.asStateFlow()

    @Volatile private var loaded = false
    private val thread by lazy { HandlerThread("messaged-log").apply { start() } }
    private val handler by lazy { Handler(thread.looper) }

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val app = context.applicationContext
        handler.post {
            val lines = runCatching { File(app.filesDir, FILE).readLines() }.getOrDefault(emptyList())
            _entries.value = mergeMessaged(emptyList(), lines.mapNotNull(::decodeMessaged), System.currentTimeMillis())
        }
    }

    /** Records the chat/SMS senders currently in the notification [snapshot]. */
    fun record(context: Context, snapshot: NotificationSnapshot) {
        val seen = messagedFromSnapshot(snapshot)
        if (seen.isEmpty()) return
        ensureLoaded(context)
        val app = context.applicationContext
        handler.post {
            val current = _entries.value
            val next = mergeMessaged(current, seen, System.currentTimeMillis())
            if (next == current) return@post
            _entries.value = next
            runCatching { File(app.filesDir, FILE).writeText(next.joinToString("\n", transform = ::encodeMessaged)) }
        }
    }
}

/** Chat and SMS senders in [snapshot] (mail and social are left out: those aren't "people you talk with"). */
internal fun messagedFromSnapshot(snapshot: NotificationSnapshot): List<MessagedEntry> =
    recentActivity(snapshot, limit = 200)
        .filter { it.category == PeopleCategory.CHAT || it.category == PeopleCategory.MESSAGES }
        .mapNotNull { entry ->
            cleanSenderName(entry.sender)?.let { MessagedEntry(it, entry.packageName, entry.postTime) }
        }

/**
 * A notification title as a person's name, or null when it isn't one: blank,
 * a count summary ("3 new messages"), or the user themselves ("you").
 */
private val COUNT_SUMMARY = Regex("""^\d+\s+(new\s+)?(messages?|chats?)\b.*""")
private val WHITESPACE_RUN = Regex("\\s+")

internal fun cleanSenderName(raw: String): String? {
    val name = raw.trim()
    if (name.isEmpty() || name.length > 60) return null
    val lower = name.lowercase()
    if (lower == "you" || lower == "me") return null
    if (COUNT_SUMMARY.matches(lower)) return null
    return name
}

/** Same person regardless of case or spacing. */
internal fun messagedKey(name: String): String = name.trim().lowercase().replace(WHITESPACE_RUN, " ")

/**
 * Newest entry per person, newest first, dropping ones older than
 * [MessagedLog.KEEP_MS] and capping at [MessagedLog.MAX]. Pure.
 */
internal fun mergeMessaged(existing: List<MessagedEntry>, seen: List<MessagedEntry>, now: Long): List<MessagedEntry> =
    (existing + seen)
        .filter { now - it.time <= MessagedLog.KEEP_MS }
        .groupBy { messagedKey(it.name) }
        .map { (_, group) -> group.maxBy { it.time } }
        .sortedByDescending { it.time }
        .take(MessagedLog.MAX)

internal fun encodeMessaged(e: MessagedEntry): String =
    listOf(e.time.toString(), e.packageName, e.name.replace('\t', ' ').replace('\n', ' ')).joinToString("\t")

internal fun decodeMessaged(line: String): MessagedEntry? {
    val parts = line.split('\t')
    if (parts.size != 3) return null
    val time = parts[0].toLongOrNull() ?: return null
    return MessagedEntry(parts[2], parts[1], time)
}

/**
 * Log entries matched to contacts by name (case/spacing-insensitive), newest
 * first, one row per contact. Names that aren't a saved contact (groups,
 * businesses, unknown numbers) are left out. Pure.
 */
internal fun matchMessaged(entries: List<MessagedEntry>, contacts: List<PersonSummary>): List<Pair<PersonSummary, MessagedEntry>> {
    val byName = contacts.associateBy { messagedKey(it.name) }
    val seen = HashSet<Long>()
    return entries.mapNotNull { e ->
        val person = byName[messagedKey(e.name)] ?: return@mapNotNull null
        if (seen.add(person.contactId)) person to e else null
    }
}
