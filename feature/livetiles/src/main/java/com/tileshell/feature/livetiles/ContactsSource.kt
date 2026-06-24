package com.tileshell.feature.livetiles

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

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
