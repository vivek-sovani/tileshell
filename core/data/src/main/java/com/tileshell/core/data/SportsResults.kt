package com.tileshell.core.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Finished days never change, so each past day's matches (once all of them are
 * over) are kept for the life of the process: opening "last week" after "last month" (or the other
 * way round, or a second visit) asks ESPN for nothing already seen.
 */
internal object SportsDayCache {
    private val days = ConcurrentHashMap<String, List<HubMatch>>()
    fun get(sportSlug: String, day: String): List<HubMatch>? = days["$sportSlug|$day"]
    fun put(sportSlug: String, day: String, matches: List<HubMatch>) {
        days["$sportSlug|$day"] = matches
    }
}

private const val DAYS_PER_STEP = 4

/**
 * Reads the matches the results page needs, on demand. ESPN's scoreboard
 * ignores date ranges (verified: `dates=A-B` returns nothing), so a sport is
 * read one day at a time, [DAYS_PER_STEP] days at once, newest first; after
 * each step [onPartial] gets everything read so far, so the page fills in
 * from the most recent day instead of waiting for the last.
 *
 * - "my sports": every day in [range] of every favourite sport.
 * - "my teams": each club team's own schedule (the whole season in one
 *   request), plus the cricket days when a cricket team is followed (cricket
 *   has no per-team schedule).
 *
 * The caller filters the result with [inScope] and [hubResults].
 */
suspend fun loadHubResults(
    scope: HubScope,
    range: ResultsRange,
    fav: SportsFavorites,
    nowMillis: Long,
    zoneOffsetMillis: Long,
    onPartial: suspend (List<HubMatch>) -> Unit,
) {
    val gate = Semaphore(4)
    val collected = LinkedHashMap<String, HubMatch>()
    fun add(matches: List<HubMatch>) = matches.forEach { collected.putIfAbsent(it.key, it) }

    val walkSports: List<String>
    if (scope == HubScope.MY_TEAMS) {
        val clubTeams = fav.teams.filter { it.leagueSlug != CRICKET_LEAGUE_SLUG }
        coroutineScope {
            clubTeams.map { team ->
                async {
                    gate.withPermit {
                        val name = sportsLeagueFor(team.leagueSlug)?.displayName ?: team.leagueSlug
                        fetchSportsSchedule(team.leagueSlug, team.teamId).map { HubMatch(it, team.leagueSlug, name) }
                    }
                }
            }.awaitAll().forEach(::add)
        }
        onPartial(collected.values.toList())
        walkSports = if (fav.teams.any { it.leagueSlug == CRICKET_LEAGUE_SLUG }) listOf(CRICKET_LEAGUE_SLUG) else emptyList()
    } else {
        walkSports = fav.sports
    }
    if (walkSports.isEmpty()) return

    val days = resultDays(nowMillis, range.days, zoneOffsetMillis)
    val today = days.first()
    days.chunked(DAYS_PER_STEP).forEach { step ->
        coroutineScope {
            step.flatMap { day -> walkSports.map { slug -> slug to day } }.map { (slug, day) ->
                async {
                    SportsDayCache.get(slug, day) ?: gate.withPermit {
                        val name = sportsLeagueFor(slug)?.displayName ?: slug
                        val events = if (slug == CRICKET_LEAGUE_SLUG) fetchCricketMatches(day) else fetchSportsScoreboardOn(slug, day)
                        events.map { HubMatch(it, slug, name) }.also { if (day != today && events.all { e -> e.state == SPORTS_STATE_FINAL }) SportsDayCache.put(slug, day, it) }
                    }
                }
            }.awaitAll().forEach(::add)
        }
        onPartial(collected.values.toList())
    }
}
