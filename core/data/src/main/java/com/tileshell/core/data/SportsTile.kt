package com.tileshell.core.data

/**
 * A sports tile — one per pinned tile, like the countdown/sticky note tiles,
 * since a user might follow several different teams. Encodes the chosen
 * league + team into [TileModel.App.activityName] as
 * `sports:<leagueSlug>|<teamId>|<teamLabel>` (same blank-package,
 * no-schema-change trick as [ContactTile]/[CountdownTile]), written via the
 * same [LayoutRepository.setTileText] path. `|` separates fields rather than
 * `:` since [Selection.teamLabel] is free text and `leagueSlug` itself
 * already contains a `/` — `:` stays reserved for the prefix only.
 * [Selection.teamId] is ESPN's own team id, which [snapshotFor] matches a
 * fetched event's `homeId`/`awayId` against — not the abbreviation, which
 * cricket's international sides don't expose as reliably as club sports do.
 */
object SportsTile {
    /** [TileModel.App.iconKey] for a sports tile. */
    const val ICON_KEY = "sports"

    private const val PREFIX = "sports:"

    data class Selection(val leagueSlug: String, val teamId: String, val teamLabel: String)

    fun encode(leagueSlug: String, teamId: String, teamLabel: String): String =
        "$PREFIX$leagueSlug|$teamId|$teamLabel"

    /** Decodes an `activityName` back to a [Selection], or null if it isn't one or has no team picked yet. */
    fun decode(activityName: String): Selection? {
        if (!activityName.startsWith(PREFIX)) return null
        val parts = activityName.removePrefix(PREFIX).split("|", limit = 3)
        if (parts.size < 3) return null
        val (leagueSlug, teamId, teamLabel) = parts
        return if (leagueSlug.isEmpty() || teamId.isEmpty()) null else Selection(leagueSlug, teamId, teamLabel)
    }
}
