package com.tileshell.core.data

/**
 * A contact pinned to Start (quick search → "pin to start") renders as a plain
 * [TileModel.App] with no resolvable launch component — `packageName` blank,
 * exactly like the weather/calendar `liveOnly` tiles (see `DefaultTile.liveOnly`).
 * [TileModel.App.activityName] instead encodes the contact's identity so the
 * tile can reopen the right contact card. This avoids a schema change (no new
 * tile kind, no DB migration) and the tile gets merge/resize/drag/accent-override
 * for free by reusing the App tile machinery.
 */
object ContactTile {
    /** [TileModel.App.iconKey] for a pinned contact. */
    const val ICON_KEY = "contact"

    private const val PREFIX = "contact:"

    /** Encodes [contactId]/[lookupKey] into an `activityName`-shaped string. */
    fun encode(contactId: Long, lookupKey: String): String = "$PREFIX$contactId:$lookupKey"

    /** Decodes an `activityName` back to (contactId, lookupKey), or null if it isn't one. */
    fun decode(activityName: String): Pair<Long, String>? {
        if (!activityName.startsWith(PREFIX)) return null
        val rest = activityName.removePrefix(PREFIX)
        val contactId = rest.substringBefore(':').toLongOrNull() ?: return null
        val lookupKey = rest.substringAfter(':', missingDelimiterValue = "")
        return if (lookupKey.isEmpty()) null else contactId to lookupKey
    }
}
