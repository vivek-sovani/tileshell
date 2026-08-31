package com.tileshell.core.data

/**
 * Encodes a "calendar systems" tile's single picked system into a
 * blank-package tile's `activityName` column — same no-schema-migration
 * trick as [CommodityTile]/[CountdownTile]. Only one system is ever picked
 * at a time (front face = that system's date, back face = the Roman/
 * Gregorian date always) — no multi-select basket like [StockTile].
 */
object CalendarSystemTile {
    const val ICON_KEY = "calsys"
    private const val PREFIX = "calsys:"

    fun encode(systemId: String): String = "$PREFIX$systemId"

    /** The picked system's id, or null when not yet picked / malformed / unknown. */
    fun decode(activityName: String): String? {
        if (!activityName.startsWith(PREFIX)) return null
        val id = activityName.removePrefix(PREFIX)
        if (id.isEmpty() || calendarSystemFor(id) == null) return null
        return id
    }
}
