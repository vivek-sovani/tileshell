package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.HubMatch
import com.tileshell.core.data.HubScope
import com.tileshell.core.data.SPORTS_LEAGUES
import com.tileshell.core.data.SPORTS_STATE_LIVE
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.hubTodayMatches
import com.tileshell.core.data.inScope
import com.tileshell.core.data.settings.LiveRefreshRate
import com.tileshell.core.data.settings.resolveMs
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileIcons

/** How long a page of matches stays on the sports hub tile before the next rolls in. */
internal const val SPORTS_HUB_TILE_ROLL_MS = 4_500L

private const val SPORTS_HUB_TILE_REFRESH_MS = 90_000L

/**
 * Which of today's [matches] the sports hub tile shows: those of the followed teams when any are followed,
 * else those of the followed sports (every sport when none is), live first, then still to come, then finished.
 * Pure.
 */
internal fun sportsHubTileMatches(
    matches: List<HubMatch>,
    fav: com.tileshell.core.data.SportsFavorites,
    nowMillis: Long,
    zoneOffsetMillis: Long,
): List<HubMatch> {
    val scope = if (fav.teams.isNotEmpty()) HubScope.MY_TEAMS else HubScope.MY_SPORTS
    return hubTodayMatches(matches.filter { inScope(it, scope, fav) }, nowMillis, zoneOffsetMillis)
}

/** [items] split into pages of [perPage] (at least 1), in order. Pure. */
internal fun <T> sportsHubTilePages(items: List<T>, perPage: Int): List<List<T>> =
    if (items.isEmpty()) emptyList() else items.chunked(perPage.coerceAtLeast(1))

/** How many two-line matches fit under [heightDp] of tile (a match is about 54dp, the first row also has the padding). Pure. */
internal fun sportsHubTileRows(heightDp: Float): Int = ((heightDp - 20f) / 54f).toInt().coerceAtLeast(1)

/**
 * The sports hub's live tile: today's matches of the teams (else sports) followed on the hub, live ones first with a
 * "live" mark, each as its two sides with their scores and a status line (the minute, the kick-off time, "final"),
 * paged with the same slide-up as the other tiles when there are more than fit. Reads ESPN's public scoreboards
 * only while [active], every 90 seconds while a match is on and every 15 minutes otherwise (the Personalize "live
 * data refresh" rate can slow it); tapping opens the hub. A small tile, or a day with nothing on, shows the glyph.
 */
@Composable
fun SportsHubTileFace(
    size: TileSize,
    active: Boolean = true,
    refreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val color = LocalTileFaceColor.current
    val fav by remember { SportsFavoritesStore.flow(context) }.collectAsState()
    val zoneOffset = remember { java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong() }
    var board by remember { mutableStateOf(LiveState()) }
    val leagues = remember(fav.sports) { fav.sports.ifEmpty { SPORTS_LEAGUES.map { it.slug } } }
    LaunchedEffect(active, leagues, refreshRate, size == TileSize.SMALL) {
        if (!active || size == TileSize.SMALL) return@LaunchedEffect
        while (true) {
            board = loadToday(leagues)
            val baseline = refreshRate.resolveMs(SPORTS_HUB_TILE_REFRESH_MS)
            val anyLive = board.matches.any { it.event.state == SPORTS_STATE_LIVE }
            delayUntilNextRefresh(if (anyLive) baseline else maxOf(baseline, 15 * 60_000L))
        }
    }
    val shown = remember(board, fav) { sportsHubTileMatches(board.matches, fav, board.nowMillis, zoneOffset) }

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

    val narrow = size.cols <= 1
    val oneRow = size.rows <= 1
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val perPage = if (oneRow) 1 else sportsHubTileRows(maxHeight.value)
        val pages = remember(shown, perPage) { sportsHubTilePages(shown, perPage) }
        var page by remember { mutableIntStateOf(0) }
        LaunchedEffect(active, pages.size) {
            if (!active || pages.size < 2) { page = 0; return@LaunchedEffect }
            while (true) {
                kotlinx.coroutines.delay(SPORTS_HUB_TILE_ROLL_MS)
                page = (page + 1) % pages.size
            }
        }
        MessageSlide(page.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) { i ->
            val rows = pages.getOrNull(i.coerceIn(0, pages.lastIndex)).orEmpty()
            Column(
                verticalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxSize().padding(horizontal = if (narrow) 5.dp else 10.dp, vertical = if (oneRow) 4.dp else 8.dp),
            ) {
                rows.forEach { m -> HubTileMatch(m, board.nowMillis, narrow, oneRow, color) }
            }
        }
    }
}

@Composable
private fun HubTileMatch(m: HubMatch, nowMillis: Long, narrow: Boolean, oneRow: Boolean, color: androidx.compose.ui.graphics.Color) {
    val e = m.event
    val live = e.state == SPORTS_STATE_LIVE
    val status = matchStatus(m, nowMillis)
    if (oneRow) {
        // One line: "IND 2 – 1 AUS" with the live mark, nothing more fits.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                (if (live) "● " else "") + "${e.homeAbbr} ${e.homeScore}".trim() + "  ${e.awayScore} ${e.awayAbbr}".trimEnd(),
                color = color, fontSize = if (narrow) 11.sp else 14.sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
        }
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!narrow) {
            Text(
                (if (live) "● live · " else "") + m.leagueName,
                color = color.copy(alpha = if (live) 1f else 0.7f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        TeamLine(e.homeAbbr, e.homeScore, narrow, color)
        TeamLine(e.awayAbbr, e.awayScore, narrow, color)
        if (!narrow) {
            Text(status, color = color.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TeamLine(abbr: String, score: String, narrow: Boolean, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(abbr, color = color, fontSize = if (narrow) 11.sp else 14.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(score, color = color, fontSize = if (narrow) 11.sp else 14.sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false, textAlign = TextAlign.End)
    }
}
