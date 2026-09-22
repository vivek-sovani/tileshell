package com.tileshell.feature.livetiles

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One contact as shown in the People Hub's lists (the "all"/"frequent"/
 * "recent" rows) — enough identity to render a row (photo or initials avatar +
 * name) and reopen the contact in the device's own Contacts app
 * ([openContactCard]). Falls back to an initials avatar tinted by [colorFor]
 * (see `ContactsSource.kt`) whenever [photoUri] is null.
 */
data class PersonSummary(
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
)

private val SUMMARY_PROJECTION = arrayOf(
    ContactsContract.Contacts._ID,
    ContactsContract.Contacts.LOOKUP_KEY,
    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
)

/**
 * Every contact with a display name, alphabetical (`DISPLAY_NAME_PRIMARY
 * ASC`) — the People Hub's "all" list. Caller must hold READ_CONTACTS; a
 * denied/failed query degrades to an empty list rather than crashing.
 *
 * [limit] is a safety cap, not a deliberate scope limit — user-reported: a
 * heavily-populated address book (2000+ real contacts, common with years of
 * saved business/service entries) was silently truncated at the old default
 * of 1000, so "all" looked incomplete.
 */
fun queryAllContacts(context: Context, limit: Int = 10_000): List<PersonSummary> {
    val out = mutableListOf<PersonSummary>()
    runCatching {
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            SUMMARY_PROJECTION,
            null,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext() && out.size < limit) {
                val name = cursor.getString(2)?.trim().orEmpty()
                if (name.isEmpty()) continue
                out += PersonSummary(
                    contactId = cursor.getLong(0),
                    lookupKey = cursor.getString(1),
                    name = name,
                    photoUri = cursor.getString(3)?.ifBlank { null },
                )
            }
        }
    }
    return out
}

/**
 * Up to [limit] favourite + frequently-contacted contacts (the provider's own
 * "strequent" list — see [queryContacts]'s own doc for why this needs only
 * READ_CONTACTS), for the "all" page's "frequent" strip. Unlike [queryContacts]
 * this does **not** require a photo — the hub always renders initials anyway.
 */
fun queryFrequentContacts(context: Context, limit: Int = 10): List<PersonSummary> {
    val out = mutableListOf<PersonSummary>()
    runCatching {
        @Suppress("DEPRECATION")
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_STREQUENT_URI,
            SUMMARY_PROJECTION,
            null,
            null,
            null,
        )?.use { cursor ->
            val seen = HashSet<Long>()
            while (cursor.moveToNext() && out.size < limit) {
                val name = cursor.getString(2)?.trim().orEmpty()
                if (name.isEmpty()) continue
                val id = cursor.getLong(0)
                if (!seen.add(id)) continue
                out += PersonSummary(
                    contactId = id,
                    lookupKey = cursor.getString(1),
                    name = name,
                    photoUri = cursor.getString(3)?.ifBlank { null },
                )
            }
        }
    }
    return out
}

/**
 * Contacts sorted by [ContactsContract.Contacts.LAST_TIME_CONTACTED],
 * newest first, excluding ones never contacted (0) — needs only
 * READ_CONTACTS (this column is populated by the OS from call/SMS history
 * itself, no READ_CALL_LOG/READ_SMS required). The "recent" pivot.
 */
@Suppress("DEPRECATION") // LAST_TIME_CONTACTED still works fine; there is no non-deprecated replacement
fun queryRecentContacts(context: Context, limit: Int = 30): List<PersonSummary> {
    val out = mutableListOf<PersonSummary>()
    runCatching {
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            SUMMARY_PROJECTION,
            "${ContactsContract.Contacts.LAST_TIME_CONTACTED} > 0",
            null,
            "${ContactsContract.Contacts.LAST_TIME_CONTACTED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext() && out.size < limit) {
                val name = cursor.getString(2)?.trim().orEmpty()
                if (name.isEmpty()) continue
                out += PersonSummary(
                    contactId = cursor.getLong(0),
                    lookupKey = cursor.getString(1),
                    name = name,
                    photoUri = cursor.getString(3)?.ifBlank { null },
                )
            }
        }
    }
    return out
}

/** "aarav mehta" -> "AM"; a single-word name uses its first two letters. Pure. */
fun initialsFor(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/**
 * Groups [people] (already alphabetically sorted, e.g. from [queryAllContacts])
 * into `(letter, contacts)` sections keyed by the uppercase first letter of
 * each name, preserving input order within a section. Pure — the hub's own
 * "a" / "m" / … section headers.
 */
fun groupContactsByLetter(people: List<PersonSummary>): List<Pair<String, List<PersonSummary>>> {
    val sections = LinkedHashMap<String, MutableList<PersonSummary>>()
    for (person in people) {
        val letter = person.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "#"
        sections.getOrPut(letter) { mutableListOf() } += person
    }
    return sections.map { (letter, list) -> letter to list }
}

/** Opens this contact's real card in the device's own Contacts app —
 * per direct user instruction, the People Hub never rebuilds a custom
 * profile page; it just hands off to whichever app already owns that data. */
fun openContactCard(context: Context, contactId: Long, lookupKey: String) {
    val intent = Intent(Intent.ACTION_VIEW, contactLookupUri(contactId, lookupKey))
    runCatching { context.startActivity(intent) }
}

/** Dials [number] — `ACTION_DIAL`, same as `QuickSearchOverlay`'s own contact
 * quick action, so it still requires the user's own confirming tap in the
 * dialer rather than placing a call outright. */
fun callContact(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${android.net.Uri.encode(number)}"))
    runCatching { context.startActivity(intent) }
}

/** Opens the default SMS app pre-addressed to [number] — same
 * `ACTION_SENDTO`/`smsto:` pattern as `QuickSearchOverlay`. */
fun messageContact(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:${android.net.Uri.encode(number)}"))
    runCatching { context.startActivity(intent) }
}

/** True when WhatsApp is installed — gates the People Hub's WhatsApp quick
 * action so it only ever shows when it could actually work. */
fun isWhatsAppInstalled(context: Context): Boolean =
    runCatching { context.packageManager.getPackageInfo("com.whatsapp", 0) }.isSuccess

/** Opens a WhatsApp chat with [number] via the public `wa.me` deep link —
 * works without the contact having to already exist inside WhatsApp itself,
 * unlike Facebook Messenger (no equivalent phone-number deep link exists;
 * Messenger threads are addressed by Facebook identity, not phone number, so
 * there is no reliable way to jump to a specific contact's Messenger chat
 * from just a phone number — omitted for that reason, not an oversight). */
fun whatsAppContact(context: Context, number: String) {
    val digits = number.filter { it.isDigit() }
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$digits"))
    runCatching { context.startActivity(intent) }
}

/** Opens the system Contacts app's "new contact" screen. */
fun openAddContact(context: Context) {
    val intent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
    runCatching { context.startActivity(intent) }
}

/** Opens the device's own Contacts app (its main list), for the hub's
 * "open contacts app" action — mirrors the calendar hub's `openCalendarApp`. */
fun openContactsApp(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
    runCatching { context.startActivity(intent) }
}

/** A pending "pin this contact to Start" request from the People Hub's own
 * long-press menu. `:feature:livetiles` has no visibility into
 * `StartViewModel.pinContact` (in `:feature:start`, which depends on
 * `:feature:livetiles`, not the other way around) — same cross-module-bridging
 * shape as [MusicHubNavigation]. `StartScreen` observes [pendingPin] once, at
 * its own top level, and calls the real `StartViewModel.pinContact(...)`. */
object PeopleHubNavigation {
    private val _pendingPin = MutableStateFlow<PersonSummary?>(null)
    val pendingPin: StateFlow<PersonSummary?> = _pendingPin.asStateFlow()

    fun requestPin(person: PersonSummary) {
        _pendingPin.value = person
    }

    /** Called once the request has been acted on, so it isn't replayed. */
    fun consume() {
        _pendingPin.value = null
    }
}

/** One row on the People Hub's "what's new" page — a single pending
 * messaging/social notification, flattened out of [NotificationSnapshot]
 * (already tracked for badges/mail-and-messages faces) and tagged with which
 * app it came from so the row can show that app's own icon as a small
 * corner badge. */
data class ActivityEntry(
    val packageName: String,
    val sender: String,
    val snippet: String,
    val postTime: Long,
    val notificationKey: String,
)

/**
 * Packages whose notifications are inherently person-to-person (a message, a
 * call, a DM/comment) — user-requested: "whats new should be only related
 * with contacts". [NotificationSnapshot] carries *every* app's notifications
 * (it's shared with badges/mail-and-messages faces), including plain content/
 * promo ones (Play Store, a news feed, a streaming app's "new release") that
 * have nothing to do with a person — this allowlist is what keeps those off
 * the "what's new" page. Not exhaustive (regional apps vary), but covers the
 * major messaging/calling/social apps; extend when a specific gap is
 * reported, same convention as [NOTIFICATION_PACKAGE_ALIASES].
 */
private val PEOPLE_NOTIFICATION_PACKAGES = setOf(
    "com.whatsapp", "com.whatsapp.w4b",
    "com.facebook.orca", "com.facebook.katana",
    "com.instagram.android",
    "com.instagram.barcelona", // Threads
    "org.telegram.messenger", "org.telegram.messenger.web",
    "org.thoughtcrime.securesms",
    "com.snapchat.android",
    "com.twitter.android",
    "com.google.android.apps.messaging",
    "com.samsung.android.messaging",
    "com.android.mms",
    "com.android.dialer", "com.samsung.android.dialer", "com.google.android.dialer",
    "com.skype.raider",
    "com.viber.voip",
    "com.linkedin.android",
    "com.Slack",
    "com.microsoft.teams",
    "com.discord",
    "com.tencent.mm",
    "jp.naver.line.android",
    "com.kakao.talk",
)

/**
 * Flattens every people-related package's pending [ConversationItem]s
 * (already capped at [MAX_CONVERSATION_ITEMS] per package) into one
 * newest-first list, capped at [limit]. Pure — the hub reads
 * [NotificationCenter.snapshot] itself and passes it in.
 */
fun recentActivity(snapshot: NotificationSnapshot, limit: Int = 40): List<ActivityEntry> =
    snapshot.conversations.entries
        .filter { (packageName, _) -> packageName in PEOPLE_NOTIFICATION_PACKAGES }
        .flatMap { (packageName, preview) ->
            preview.items.map { item ->
                ActivityEntry(
                    packageName = packageName,
                    sender = item.sender,
                    snippet = item.snippet,
                    postTime = item.postTime,
                    notificationKey = item.notificationKey,
                )
            }
        }
        .sortedByDescending { it.postTime }
        .take(limit)

/** "2 min ago" / "18 min ago" / "1 hr ago" / "3 days ago" — the "what's new"
 * page's own relative-time label (a distinct wording style from the feed's
 * compact `feedAgo`, e.g. "2m"/"3h"). Pure. */
fun activityAgo(postTimeMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    if (postTimeMillis <= 0L) return ""
    val deltaMinutes = ((nowMillis - postTimeMillis).coerceAtLeast(0L)) / 60_000L
    return when {
        deltaMinutes < 1 -> "just now"
        deltaMinutes < 60 -> "$deltaMinutes min ago"
        deltaMinutes < 1_440 -> "${deltaMinutes / 60} hr ago"
        else -> "${deltaMinutes / 1_440} day${if (deltaMinutes / 1_440 == 1L) "" else "s"} ago"
    }
}
