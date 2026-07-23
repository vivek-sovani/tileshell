package com.tileshell.feature.livetiles

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One contact shown in the people mosaic (FR-2 people tile): a [name] and the
 * contact's [photoUri] thumbnail. The mosaic shows *only* contacts that have a
 * profile photo (no initials avatars), so [photoUri] is always present here.
 */
data class Person(val name: String, val photoUri: String)

/**
 * A small stable palette for initials avatars (no Microsoft assets; WP-ish
 * saturated tones). [colorFor] hashes the name into it so a given contact always
 * gets the same colour across refreshes.
 */
private val AVATAR_COLORS = listOf(
    Color(0xFF2B78E4), Color(0xFF6B3FD4), Color(0xFFC4287E), Color(0xFFD6262B),
    Color(0xFFE5641E), Color(0xFFE2A200), Color(0xFF7CB518), Color(0xFF1F9E57),
    Color(0xFF0F9B9B), Color(0xFF1399C6), Color(0xFF5A6B7B), Color(0xFF9B6A8F),
)

/** Deterministic avatar colour for a contact name. Pure for unit testing. */
fun colorFor(name: String): Color {
    if (name.isEmpty()) return AVATAR_COLORS.first()
    val index = abs(name.hashCode()) % AVATAR_COLORS.size
    return AVATAR_COLORS[index]
}

/**
 * Reads up to [limit] **favourite + frequently-contacted** contacts that have a
 * profile photo (display name + thumbnail) for the people tile — and *only* those.
 * Uses the provider's "strequent" list (starred contacts, then most-contacted),
 * which needs only READ_CONTACTS, no call-log permission. The mosaic deliberately
 * does **not** fall back to other contacts, so it shows just the people the user
 * cares about; if none of them have a photo the tile degrades to static. (Note:
 * Android 10+ no longer tracks "frequently contacted" for privacy, so on modern
 * devices the strequent list is effectively the starred/favourite contacts — so
 * with no favourites starred, the tile stays static.)
 *
 * Caller must hold READ_CONTACTS — this throws SecurityException otherwise, so
 * guard the call. Contacts without a display name *or without a photo* are skipped
 * (the mosaic shows photos only). Distinct by name so a contact split across raw
 * accounts only fills one cell.
 */
fun queryContacts(context: Context, limit: Int = 50): List<Person> {
    val people = LinkedHashMap<String, Person>()
    @Suppress("DEPRECATION") // strequent still returns starred contacts on API 29+
    readPhotoContacts(context, ContactsContract.Contacts.CONTENT_STREQUENT_URI, null, people, limit)
    return people.values.toList()
}

/**
 * Appends photo-bearing contacts from [uri] (in [sortOrder]) into [into], distinct
 * by display name, until [limit] is reached. Photos are filtered in code rather
 * than via a selection so it works uniformly for the strequent URI (which doesn't
 * take an arbitrary selection). Query failures are swallowed — a missing provider
 * just contributes nothing.
 */
private fun readPhotoContacts(
    context: Context,
    uri: Uri,
    sortOrder: String?,
    into: LinkedHashMap<String, Person>,
    limit: Int,
) {
    val projection = arrayOf(
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
    )
    runCatching {
        context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
            while (cursor.moveToNext() && into.size < limit) {
                val name = cursor.getString(0)?.trim().orEmpty()
                if (name.isEmpty()) continue
                val photo = cursor.getString(1)?.ifBlank { null } ?: continue
                into.getOrPut(name) { Person(name, photo) }
            }
        }
    }
}

/**
 * Builds the [cellCount] mosaic cells from [people], cycling when there are fewer
 * contacts than cells so every cell is filled. Empty in → empty out (tile stays
 * static). Pure so the fill rule is unit-tested.
 */
fun mosaicCells(people: List<Person>, cellCount: Int): List<Person> {
    if (people.isEmpty()) return emptyList()
    return List(cellCount) { people[it % people.size] }
}

/**
 * One contact match for quick search: enough to render a row (name + optional
 * photo) and to reopen the contact card via [contactLookupUri].
 */
data class ContactMatch(
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
)

/**
 * Matches contacts by name/phone/email against [query] via the platform's own
 * contacts filter URI (the same lookup the Dialer/People app use), capped to
 * [limit]. Caller must hold READ_CONTACTS — query failures (denied access,
 * missing provider) are swallowed and return an empty list, degrading quick
 * search's contacts section to nothing rather than crashing.
 */
fun searchContacts(context: Context, query: String, limit: Int = 5): List<ContactMatch> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val projection = arrayOf(
        ContactsContract.Contacts._ID,
        ContactsContract.Contacts.LOOKUP_KEY,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
    )
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q))
    val matches = mutableListOf<ContactMatch>()
    runCatching {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext() && matches.size < limit) {
                val name = cursor.getString(2)?.trim().orEmpty()
                val lookupKey = cursor.getString(1)
                if (name.isEmpty() || lookupKey == null) continue
                matches += ContactMatch(
                    contactId = cursor.getLong(0),
                    lookupKey = lookupKey,
                    name = name,
                    photoUri = cursor.getString(3)?.ifBlank { null },
                )
            }
        }
    }
    return matches
}

/** The contact card URI for [contactId]/[lookupKey] — opened via `ACTION_VIEW`. */
fun contactLookupUri(contactId: Long, lookupKey: String): Uri =
    ContactsContract.Contacts.getLookupUri(contactId, lookupKey)

/**
 * The contact's primary phone number (super-primary first, else the first on
 * file), or null if it has none / access is denied — degrades the quick-search
 * call/message row rather than crashing.
 */
fun primaryPhoneNumber(context: Context, contactId: Long): String? {
    val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
    return runCatching {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC",
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0)?.ifBlank { null } else null }
    }.getOrNull()
}

/**
 * The device owner's own name (the "me" contact,
 * [ContactsContract.Profile]), or null if unset/inaccessible. Used to
 * best-effort seed the feed greeting's name once on first grant — caller must
 * hold READ_CONTACTS; a denied/missing profile just contributes nothing.
 */
fun queryProfileName(context: Context): String? {
    val projection = arrayOf(ContactsContract.Profile.DISPLAY_NAME_PRIMARY)
    return runCatching {
        context.contentResolver.query(ContactsContract.Profile.CONTENT_URI, projection, null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0)?.trim()?.ifBlank { null } else null }
    }.getOrNull()
}

/** The contact's current profile-photo thumbnail URI, or null if it has none. */
fun photoUriFor(context: Context, contactId: Long): String? {
    val projection = arrayOf(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
    return runCatching {
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.ifBlank { null } else null
        }
    }.getOrNull()
}

/**
 * The pinned-contact tile face's photo, re-resolved off the main thread each
 * time [contactId] changes — a pinned contact tile only stores the contact's
 * identity (see `ContactTile`), not a snapshot of their photo, so it's looked
 * up live and simply shows nothing if the contact/photo has since been removed.
 */
@Composable
fun rememberContactPhotoUri(contactId: Long): String? {
    val context = LocalContext.current
    val uri by produceState<String?>(initialValue = null, contactId) {
        value = withContext(Dispatchers.IO) { photoUriFor(context, contactId) }
    }
    return uri
}
