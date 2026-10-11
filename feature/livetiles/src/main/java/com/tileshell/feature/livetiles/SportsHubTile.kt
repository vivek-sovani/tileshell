package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.CRICKET_LEAGUE_SLUG
import com.tileshell.core.data.HubMatch
import com.tileshell.core.data.HubScope
import com.tileshell.core.data.SPORTS_LEAGUES
import com.tileshell.core.data.SPORTS_STATE_LIVE
import com.tileshell.core.data.SportsFavorites
import com.tileshell.core.data.SportsMatchDetail
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.fetchCricketMatchDetail
import com.tileshell.core.data.fetchMatchDetail
import com.tileshell.core.data.hubTodayMatches
import com.tileshell.core.data.inScope
import com.tileshell.core.data.involves
import com.tileshell.core.data.settings.LiveRefreshRate
import com.tileshell.core.data.settings.resolveMs
import com.tileshell.core.data.snapshotFor
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileIcons
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private const val SPORTS_HUB_TILE_REFRESH_MS = 90_000L

/** How many of today's matches get their players read on each refresh (the ones that will be shown first). */
private const val SPORTS_HUB_TILE_DETAILS = 4

/**
 * Which of today's [matches] the sports hub tile shows: those of the followed teams when any are followed,
 * else those of the followed sports (every sport when none is), live first, then still to come, then finished.
 * Pure.
 */
internal fun sportsHubTileMatches(
    matches: List<HubMatch>,
    fav: SportsFavorites,
    nowMillis: Long,
    zoneOffsetMillis: Long,
): List<HubMatch> {
    val scope = if (fav.teams.isNotEmpty()) HubScope.MY_TEAMS else HubScope.MY_SPORTS
    return hubTodayMatches(matches.filter { inScope(it, scope, fav) }, nowMillis, zoneOffsetMillis)
}

/**
 * The side the tile reads the match from: the followed team in it when there is one, else the home side, so the
 * two columns of the score read the same way every time. Pure.
 */
internal fun sportsHubTileViewpoint(match: HubMatch, fav: SportsFavorites): Pair<String, String> {
    val team = fav.teams.firstOrNull { involves(match, it) }
    return if (team != null) team.teamId to team.teamLabel else match.event.homeId to match.event.homeShown
}

/**
 * The sports hub's live tile, a flip tile like the team sports tile: the front is the score of one of today's
 * matches (both sides, each with its own column, then the minute, the kick-off time or "final"), the back is
 * who is playing (in cricket the batters at the crease and the bowling figures, in other sports the scorers and
 * the like). The matches are those of the teams followed on the hub, else of the followed sports, else every
 * sport, live ones first, and take turns each time the tile comes back to its front. The faces are the team
 * tile's own, so their type is already tuned to fill every tile size. ESPN's scoreboards are read only while
 * [active]: every 90 seconds while a match is on and every 15 minutes otherwise (the Personalize "live data
 * refresh" rate can slow it). A small tile, or a day with nothing on, shows the glyph; tapping opens the hub.
 */
@Composable
fun SportsHubTileFace(
    size: TileSize,
    flipped: Boolean = false,
    active: Boolean = true,
    refreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val color = LocalTileFaceColor.current
    val fav by remember { SportsFavoritesStore.flow(context) }.collectAsState()
    val zoneOffset = remember { java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong() }
    var board by remember { mutableStateOf(LiveState()) }
    var details by remember { mutableStateOf<Map<String, SportsMatchDetail?>>(emptyMap()) }
    val leagues = remember(fav.sports) { fav.sports.ifEmpty { SPORTS_LEAGUES.map { it.slug } } }
    LaunchedEffect(active, leagues, fav.teams, refreshRate, size == TileSize.SMALL) {
        if (!active || size == TileSize.SMALL) return@LaunchedEffect
        while (true) {
            val read = loadToday(leagues)
            board = read
            // The players of the first few matches to be shown, read together.
            val first = sportsHubTileMatches(read.matches, fav, read.nowMillis, zoneOffset).take(SPORTS_HUB_TILE_DETAILS)
            details = coroutineScope {
                first.map { m ->
                    async {
                        m.key to if (m.leagueSlug == CRICKET_LEAGUE_SLUG) {
                            m.event.leagueId?.let { fetchCricketMatchDetail(it, m.event.id) }
                        } else {
                            fetchMatchDetail(m.leagueSlug, m.event.id)
                        }
                    }
                }.map { it.await() }.toMap()
            }
            val baseline = refreshRate.resolveMs(SPORTS_HUB_TILE_REFRESH_MS)
            val anyLive = read.matches.any { it.event.state == SPORTS_STATE_LIVE }
            delayUntilNextRefresh(if (anyLive) baseline else maxOf(baseline, 15 * 60_000L))
        }
    }
    val shown = remember(board, fav) { sportsHubTileMatches(board.matches, fav, board.nowMillis, zoneOffset) }
    var turn by remember { mutableIntStateOf(0) }
    // The next match each time the tile turns back to its front.
    LaunchedEffect(flipped, shown.size) {
        if (!flipped && shown.size > 1) turn = (turn + 1) % shown.size
    }

    if (size == TileSize.SMALL || shown.isEmpty()) {
        Box(modifier = modifier.fillMaxSize()) {
            Icon(TileIcons["sportshub"], contentDescription = null, tint = color, modifier = Modifier.align(Alignment.Center).size(if (size == TileSize.SMALL) 28.dp else 44.dp))
            if (size != TileSize.SMALL) {
                Text(
                    if (board.loaded) "sports · nothing on today" else "sports",
                    color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                )
            }
        }
        return
    }

    val match = shown[turn % shown.size]
    val detail = details[match.key]
    val (viewId, viewLabel) = sportsHubTileViewpoint(match, fav)
    // The scoreboard's own score is not trustworthy for a soccer side; the summary call's inline one is.
    val ev = if (detail?.homeScore != null && detail.awayScore != null) {
        match.event.copy(homeScore = detail.homeScore!!, awayScore = detail.awayScore!!)
    } else {
        match.event
    }
    val snapshot = snapshotFor(ev, viewId)
    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { SportsFront(snapshot, size, turn = if (shown.size > 1) "${turn % shown.size + 1}/${shown.size}" else null) },
        back = {
            SportsBack(
                snapshot = snapshot,
                teamLabel = viewLabel,
                scorerLines = detail?.contributorLines.orEmpty(),
                battingLines = detail?.battingLines.orEmpty(),
                bowlingLines = detail?.bowlingLines.orEmpty(),
                size = size,
            )
        },
    )
}
