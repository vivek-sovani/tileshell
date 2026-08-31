package com.tileshell.core.data

/**
 * A countdown tile — one per pinned tile, like the sticky note tile, since a
 * user typically wants several independent countdowns (a birthday, an exam,
 * a trip) rather than one shared list. Encodes its target date and label into
 * [TileModel.App.activityName] as `countdown:<ISO date>:<label>` — the same
 * blank-package, no-schema-change trick [ContactTile] and the sticky note
 * tile already use, written via the same [LayoutRepository.setTileText] path.
 */
object CountdownTile {
    /** [TileModel.App.iconKey] for a countdown tile. */
    const val ICON_KEY = "countdown"

    private const val PREFIX = "countdown:"

    /** Encodes [targetIsoDate] (`"2026-09-15"`, [java.time.LocalDate.toString] format) and [label]. */
    fun encode(targetIsoDate: String, label: String): String = "$PREFIX$targetIsoDate:$label"

    /**
     * Decodes an `activityName` back to (targetIsoDate, label), or null if it
     * isn't one or has no date yet (a freshly-pinned tile before the user has
     * picked one). [label] may be blank — a countdown with no title is still
     * valid, just shown under a generic "countdown" heading.
     */
    fun decode(activityName: String): Pair<String, String>? {
        if (!activityName.startsWith(PREFIX)) return null
        val rest = activityName.removePrefix(PREFIX)
        val isoDate = rest.substringBefore(':')
        val label = rest.substringAfter(':', missingDelimiterValue = "")
        return if (isoDate.isEmpty()) null else isoDate to label
    }
}
