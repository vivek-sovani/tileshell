package com.tileshell.feature.livetiles

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import com.tileshell.feature.livetiles.money.isBankMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One contact as shown in the People Hub's lists (the "all"/"frequent"/
 * "favourites" rows) — enough identity to render a row (photo or initials avatar +
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
    ContactsContract.Contacts.PHOTO_URI,
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
 * Starred contacts, alphabetical — the People Hub's "favourites". Starring is
 * the user's own choice, made in Contacts or the dialer, and every phone keeps
 * it (unlike "last contacted", which replaced "recent" for that reason).
 * Needs only READ_CONTACTS.
 */
fun queryFavouriteContacts(context: Context, limit: Int = 200): List<PersonSummary> {
    val out = mutableListOf<PersonSummary>()
    runCatching {
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            SUMMARY_PROJECTION,
            "${ContactsContract.Contacts.STARRED} = 1",
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

    /** A person tapped on the favourites tile: Start shows their quick-action
     * sheet (call / message / chat app / contact) over the home screen. */
    private val _quickActions = MutableStateFlow<PersonSummary?>(null)
    val quickActions: StateFlow<PersonSummary?> = _quickActions.asStateFlow()

    fun showQuickActions(person: PersonSummary) {
        _quickActions.value = person
    }

    fun dismissQuickActions() {
        _quickActions.value = null
    }
}

/** One row on the People Hub's "what's new" page — a single pending
 * notification from a person, flattened out of [NotificationSnapshot]
 * (already tracked for badges/mail-and-messages faces) and tagged with which
 * app it came from so the row can show that app's own icon as a small
 * corner badge, and which [PeopleCategory] it belongs to for the filter chips.
 * [quickActions] are the buttons the app put on the notification that the hub
 * can press for the user (see [classifyQuickActions]). */
data class ActivityEntry(
    val packageName: String,
    val sender: String,
    val snippet: String,
    val postTime: Long,
    val notificationKey: String,
    val category: PeopleCategory = PeopleCategory.CHAT,
    val quickActions: Set<QuickAction> = emptySet(),
    val fullText: String = "",
    /** Unique per row; several rows share one [notificationKey] when a chat's messages are split. */
    val rowId: String = notificationKey,
)

/**
 * Which kind of person-to-person app a package is, for the apps page and the
 * pinned apps tile. [CALLS] isn't shown on either: missed calls only appear in
 * "what's new".
 */
enum class PeopleCategory(val label: String) {
    CHAT("chat"),
    MESSAGES("messages"),
    MAIL("mail"),
    SOCIAL("social"),
    CALLS("calls"),
}

/**
 * Packages whose notifications are inherently person-to-person (a message, a
 * mail, a call, a DM/comment) — user-requested: "whats new should be only
 * related with contacts". [NotificationSnapshot] carries *every* app's
 * notifications (it's shared with badges/mail-and-messages faces), including
 * plain content/promo ones (Play Store, a news feed, a streaming app's "new
 * release") that have nothing to do with a person — this allowlist is what
 * keeps those off the "what's new" page. Not exhaustive (regional apps vary),
 * but covers the major chat/SMS/mail/social/calling apps; extend when a
 * specific gap is reported, same convention as [NOTIFICATION_PACKAGE_ALIASES].
 */
private val PEOPLE_APP_CATEGORIES: Map<String, PeopleCategory> = buildMap {
    listOf(
        "com.whatsapp", "com.whatsapp.w4b",
        "com.facebook.orca",
        "org.telegram.messenger", "org.telegram.messenger.web",
        "org.thoughtcrime.securesms",
        "com.snapchat.android",
        "com.skype.raider",
        "com.viber.voip",
        "com.Slack",
        "com.microsoft.teams",
        "com.discord",
        "com.tencent.mm",
        "jp.naver.line.android",
        "com.kakao.talk",
        "com.google.android.apps.dynamite", // Google Chat
    ).forEach { put(it, PeopleCategory.CHAT) }
    listOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
    ).forEach { put(it, PeopleCategory.MESSAGES) }
    listOf(
        "com.google.android.gm", "com.google.android.gm.lite",
        "com.microsoft.office.outlook",
        "com.samsung.android.email.provider",
        "com.yahoo.mobile.client.android.mail",
        "ch.protonmail.android", "me.proton.android.mail",
        "com.zoho.mail",
        "com.readdle.spark",
        "com.fsck.k9", "net.thunderbird.android",
        "com.rediff.mail.and",
        "com.android.email",
    ).forEach { put(it, PeopleCategory.MAIL) }
    listOf(
        "com.facebook.katana",
        "com.instagram.android",
        "com.instagram.barcelona", // Threads
        "com.twitter.android",
        "com.linkedin.android",
    ).forEach { put(it, PeopleCategory.SOCIAL) }
    listOf(
        "com.android.dialer", "com.samsung.android.dialer", "com.google.android.dialer",
    ).forEach { put(it, PeopleCategory.CALLS) }
}

/** [packageName]'s [PeopleCategory], or null when it isn't a people app. */
fun peopleCategoryFor(packageName: String): PeopleCategory? = peopleSectionsFor(packageName).firstOrNull()

/** Every People section [packageName] is in (built-in plus what the user added, minus what they took off), in display order. */
fun peopleSectionsFor(packageName: String): List<PeopleCategory> {
    val choice = HubAppChoices.current(HubKind.PEOPLE)
    val names = choice.sectionsOf(packageName, setOfNotNull(PEOPLE_APP_CATEGORIES[packageName]?.name))
    return PeopleCategory.entries.filter { it.name in names }
}

/** The built-in category, ignoring anything the user added, moved or dropped. */
internal fun builtInPeopleCategory(packageName: String): PeopleCategory? = PEOPLE_APP_CATEGORIES[packageName]

internal val PEOPLE_BUILT_IN_PACKAGES: Set<String> get() = PEOPLE_APP_CATEGORIES.keys

/** Every package [PEOPLE_APP_CATEGORIES] knows, for the apps page's
 * installed-app lookup. */
val PEOPLE_APP_PACKAGES: Set<String>
    get() {
        val choice = HubAppChoices.current(HubKind.PEOPLE)
        return (PEOPLE_APP_CATEGORIES.keys + choice.added.keys).filter { peopleSectionsFor(it).isNotEmpty() }.toSet()
    }

/** An installed people app, for the apps page and the pinned apps tile. */
data class PeopleApp(
    val packageName: String,
    val label: String,
    val category: PeopleCategory,
    val badge: Int,
)

/** The categories the apps page and tile show, in order. Calling apps are
 * left out: they're neither a conversation nor social. */
val PEOPLE_APP_GROUPS = listOf(PeopleCategory.CHAT, PeopleCategory.MESSAGES, PeopleCategory.MAIL, PeopleCategory.SOCIAL)

/**
 * Turns the installed people apps ([installed], package to label) into
 * [PeopleApp]s with their pending [badges], sorted by how often each was
 * opened ([opens], times opened in the last 30 days; empty without usage
 * access), then by most notifications, then by label. Non-people packages and
 * calling apps are dropped. Pure.
 */
fun peopleApps(
    installed: Map<String, String>,
    badges: Map<String, Int>,
    opens: Map<String, Int> = emptyMap(),
): List<PeopleApp> =
    installed.flatMap { (packageName, label) ->
        peopleSectionsFor(packageName).filter { it in PEOPLE_APP_GROUPS }
            .map { category -> PeopleApp(packageName, label, category, badges[packageName] ?: 0) }
    }.sortedWith(
        compareByDescending<PeopleApp> { opens[it.packageName] ?: 0 }
            .thenByDescending { it.badge }
            .thenBy { it.label.lowercase() },
    )

/** [apps] grouped for the apps page, in [PEOPLE_APP_GROUPS] order, empty
 * groups dropped. Keeps [peopleApps]'s order within each group. Pure. */
fun groupPeopleApps(apps: List<PeopleApp>): List<Pair<PeopleCategory, List<PeopleApp>>> =
    PEOPLE_APP_GROUPS.mapNotNull { category ->
        apps.filter { it.category == category }.takeIf { it.isNotEmpty() }?.let { category to it }
    }

/**
 * Flattens every people app's pending [ConversationItem]s (already capped at
 * [MAX_CONVERSATION_ITEMS] per package) into one newest-first list, capped at
 * [limit] — narrowed to one app when [packageName] is given (an app filter
 * chip; null = "all"), or to one [category]. Pure — the hub reads
 * [NotificationCenter.snapshot] itself and passes it in.
 */
fun recentActivity(
    snapshot: NotificationSnapshot,
    limit: Int = 40,
    category: PeopleCategory? = null,
    packageName: String? = null,
): List<ActivityEntry> =
    snapshot.conversations.entries
        .mapNotNull { (pkg, preview) ->
            val kind = peopleCategoryFor(pkg) ?: return@mapNotNull null
            when {
                packageName != null && pkg != packageName -> null
                category != null && kind != category -> null
                else -> Triple(pkg, preview, kind)
            }
        }
        .flatMap { (packageName, preview, kind) ->
            peopleItems(packageName, preview).flatMap { item -> rowsFor(packageName, item, kind) }
        }
        .sortedByDescending { it.postTime }
        .take(limit)

/** A people app's pending notifications minus bank/card money messages
 * (see [isBankMessage]), which go to the Money hub instead. */
private fun peopleItems(packageName: String, preview: ConversationPreview): List<ConversationItem> =
    preview.items.filterNot {
        isBankMessage(it.sender, it.snippet) ||
            // A store's order update or deal belongs to the shopping hub, not here as well.
            com.tileshell.feature.livetiles.shopping.shoppingClaims(packageName, it.sender, it.fullText.ifBlank { it.snippet }, it.postTime)
    }

/**
 * The app filter chips on "what's new": every people app that has pending
 * notifications right now, with its count — most notifications first, then the
 * app with the newest one. Apps with nothing pending get no chip (user-
 * requested). Pure.
 */
fun whatsNewApps(snapshot: NotificationSnapshot): List<Pair<String, Int>> =
    snapshot.badges
        .filter { (packageName, count) -> count > 0 && peopleCategoryFor(packageName) != null }
        .map { (packageName, count) ->
            // Bank messages aren't listed, so they don't count either.
            val bank = snapshot.conversations[packageName]?.let { it.items.size - peopleItems(packageName, it).size } ?: 0
            packageName to (count - bank)
        }
        .filter { it.second > 0 }
        .sortedWith(
            compareByDescending<Pair<String, Int>> { it.second }
                .thenByDescending { (packageName, _) ->
                    snapshot.conversations[packageName]?.items?.maxOfOrNull { it.postTime } ?: 0L
                },
        )

/**
 * How many of [packageName]'s pending notifications "what's new" isn't
 * listing (the app has more than the [MAX_CONVERSATION_ITEMS] rows kept per
 * package) — the "3 more in gmail · open gmail" footer. Pure.
 */
fun hiddenActivityCount(snapshot: NotificationSnapshot, packageName: String): Int {
    val shown = snapshot.conversations[packageName]?.items?.size ?: 0
    return ((snapshot.badges[packageName] ?: 0) - shown).coerceAtLeast(0)
}

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

/**
 * One row per message when an app packs two or more into a single
 * notification (a group chat's recent messages, an inbox-style list), newest
 * first, up to [MAX_MESSAGE_ROWS]; otherwise the notification's own single
 * row. Rows from one notification share its key, so reply / mark read /
 * dismiss still act on that notification. Pure.
 */
internal fun rowsFor(packageName: String, item: ConversationItem, kind: PeopleCategory): List<ActivityEntry> {
    val messages = item.messages.filter { it.text.isNotBlank() }
    if (messages.size < 2) {
        return listOf(
            ActivityEntry(
                packageName = packageName,
                sender = item.sender,
                snippet = item.snippet,
                postTime = item.postTime,
                notificationKey = item.notificationKey,
                category = kind,
                quickActions = item.quickActions,
                fullText = item.fullText,
            ),
        )
    }
    return messages.asReversed().take(MAX_MESSAGE_ROWS).mapIndexed { index, m ->
        ActivityEntry(
            packageName = packageName,
            sender = m.sender ?: item.sender,
            snippet = m.text,
            // Without a message time, keep the app's own order just below the notification's time.
            postTime = if (m.time > 0L) m.time else item.postTime - index,
            notificationKey = item.notificationKey,
            category = kind,
            quickActions = item.quickActions,
            rowId = "${item.notificationKey}#$index",
        )
    }
}
