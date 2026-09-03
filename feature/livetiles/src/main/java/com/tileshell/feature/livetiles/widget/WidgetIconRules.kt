package com.tileshell.feature.livetiles.widget

import com.tileshell.core.data.CRICKET_LEAGUE_SLUG

/**
 * Which of the per-sport icon drawables the sports widget should show for a
 * given league — user-requested: "game based symbol say for cricket,
 * football and basketball" instead of one generic ball for every league.
 * Pure/string-keyed (not a direct drawable resource id) so the mapping is
 * unit-testable without an Android [android.content.res.Resources] instance;
 * the worker resolves the key to a real `R.drawable.*` id. Keyed off the
 * league slug's own sport-segment prefix (see `SportsCatalog.kt`'s doc
 * comment on [com.tileshell.core.data.SportsLeague.slug]) rather than
 * [com.tileshell.core.data.SportsLeague.category], since category lumps
 * basketball/American football/baseball/hockey together as "other" — too
 * coarse for a distinct icon per game.
 */
fun sportsIconKeyFor(leagueSlug: String?): String = when {
    leagueSlug == null -> "generic"
    leagueSlug == CRICKET_LEAGUE_SLUG -> "cricket"
    leagueSlug.startsWith("soccer/") -> "soccer"
    leagueSlug.startsWith("basketball/") -> "basketball"
    leagueSlug.startsWith("football/") -> "football"
    leagueSlug.startsWith("baseball/") -> "baseball"
    leagueSlug.startsWith("hockey/") -> "hockey"
    else -> "generic"
}

/**
 * Which of the per-category icon drawables the commodity widget should show
 * for a picked symbol — user-requested, same as sports: a metal bar for
 * gold/silver/platinum/copper, a droplet for oil/gas, an exchange glyph for
 * any currency pair, instead of one generic coin for everything. Keyed off
 * the ticker's own shape (`=F` futures vs `=X` currency pair, see
 * `CommodityCatalog.kt`) rather than a lookup into [com.tileshell.core.data
 * .COMMODITY_ITEMS], so a user-built custom currency pair (which never gets
 * an entry there — see that file's own doc comment) still resolves
 * correctly via its `=X` suffix alone.
 */
fun commodityIconKeyFor(symbol: String): String = when {
    symbol.endsWith("=X") -> "currency"
    symbol in ENERGY_SYMBOLS -> "energy"
    symbol.endsWith("=F") -> "metal"
    else -> "generic"
}

private val ENERGY_SYMBOLS = setOf("CL=F", "BZ=F", "NG=F")
