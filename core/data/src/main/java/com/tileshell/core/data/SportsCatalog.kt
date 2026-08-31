package com.tileshell.core.data

/**
 * A league the sports tile can follow a team in. [slug] is either the exact
 * `{sport}/{league}` path segment ESPN's public, unauthenticated site API
 * (`site.api.espn.com/apis/site/v2/sports/{slug}/...`) expects — verified
 * live (`/teams` returns 200) for every club-league entry here — or
 * [CRICKET_LEAGUE_SLUG], a sentinel routing to [fetchCricketMatches]/
 * [CRICKET_TEAMS] instead, since cricket has no single evergreen league id
 * the way a club league does (each competition — one World Cup, one IPL
 * season — is its own numeric, season-specific id; espncricinfo's own API,
 * the natural fallback, returned 403 when checked directly).
 *
 * [category] groups the league picker into labeled sections ("football" /
 * "cricket" / "other") — "football" here means soccer (the everyday sense
 * this app's own users mean by the word, not ESPN's own sport-name-as-slug
 * for American football, which is filed under "other" instead alongside
 * basketball/baseball/hockey).
 */
data class SportsLeague(val slug: String, val displayName: String, val category: String)

val SPORTS_LEAGUES: List<SportsLeague> = listOf(
    SportsLeague("soccer/eng.1", "Premier League", category = "football"),
    SportsLeague("soccer/esp.1", "La Liga", category = "football"),
    SportsLeague("soccer/ita.1", "Serie A", category = "football"),
    SportsLeague("soccer/uefa.champions", "Champions League", category = "football"),
    SportsLeague(CRICKET_LEAGUE_SLUG, "Cricket", category = "cricket"),
    SportsLeague("basketball/nba", "NBA", category = "other"),
    SportsLeague("football/nfl", "NFL", category = "other"),
    SportsLeague("baseball/mlb", "MLB", category = "other"),
    SportsLeague("hockey/nhl", "NHL", category = "other"),
)

/** Display order for [SportsLeague.category] sections in the league picker. */
val SPORTS_LEAGUE_CATEGORY_ORDER: List<String> = listOf("football", "cricket", "other")

fun sportsLeagueFor(slug: String): SportsLeague? = SPORTS_LEAGUES.find { it.slug == slug }
