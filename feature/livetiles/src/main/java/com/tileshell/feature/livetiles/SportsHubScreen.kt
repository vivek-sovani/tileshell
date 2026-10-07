package com.tileshell.feature.livetiles

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.CRICKET_LEAGUE_SLUG
import com.tileshell.core.data.CRICKET_TEAMS
import com.tileshell.core.data.DetailTable
import com.tileshell.core.data.HubMatch
import com.tileshell.core.data.HubScope
import com.tileshell.core.data.IPL_TEAMS
import com.tileshell.core.data.MatchDetailData
import com.tileshell.core.data.ResultsRange
import com.tileshell.core.data.SPORTS_LEAGUES
import com.tileshell.core.data.SPORTS_LEAGUE_CATEGORY_ORDER
import com.tileshell.core.data.SportsFavorites
import com.tileshell.core.data.SportsLeague
import com.tileshell.core.data.SportsTeam
import com.tileshell.core.data.SportsTile
import com.tileshell.core.data.fetchCricketMatches
import com.tileshell.core.data.fetchMatchDetailData
import com.tileshell.core.data.fetchSportsScoreboard
import com.tileshell.core.data.fetchSportsTeams
import com.tileshell.core.data.hubResults
import com.tileshell.core.data.hubTodayMatches
import com.tileshell.core.data.inScope
import com.tileshell.core.data.involves
import com.tileshell.core.data.kickoffLabel
import com.tileshell.core.data.loadHubResults
import com.tileshell.core.data.resultLetter
import com.tileshell.core.data.snapshotFor
import com.tileshell.core.data.splitInningsScore
import com.tileshell.core.data.sportsLeagueFor
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.HubAppBar
import com.tileshell.core.design.HubAppBarAction
import com.tileshell.core.design.HubFilter
import com.tileshell.core.design.HubPanorama
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.ZoneId

private val SPORTS_PIVOTS = listOf("live", "results")

private val ResultWin = Color(0xFF35C759)
private val ResultLoss = Color(0xFFFF453A)

private val boardGate = Semaphore(4)

/** Today's matches in the sports being read, as of [nowMillis]. */
private data class LiveState(val matches: List<HubMatch> = emptyList(), val nowMillis: Long = 0L, val loaded: Boolean = false)

/** Finished matches read so far for the current scope and range; [loading] while more days are still being read. */
private data class ResultsState(val matches: List<HubMatch> = emptyList(), val nowMillis: Long = 0L, val loading: Boolean = false, val started: Boolean = false)

private sealed interface DetailState {
    data object Loading : DetailState
    data object Failed : DetailState
    data class Loaded(val data: MatchDetailData) : DetailState
}

/**
 * The sports hub, fully on demand: nothing refreshes by itself. It reads when
 * opened, when a different scope or window is chosen, and when the refresh
 * button in the app bar is pressed; the live tile on Start is what refreshes
 * on its own schedule.
 *
 * "live": today's matches — in play, still to come (with the kick-off time) and
 * finished. "results": finished matches from the last week or month. Both
 * show either only the games of the favourite teams ("my teams") or every
 * game of the favourite sports ("my sports"). Tap a match for its status and
 * scorecard (batters at the crease and bowling figures in cricket, events and
 * stats in football, a box score in basketball and the rest), with a link to
 * ESPN's own page for commentary. The + in the app bar picks the favourites.
 */
@Composable
fun SportsHubScreen(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    onPinHub: () -> Unit,
    onPinTeamTile: (SportsTile.Selection) -> Unit,
    pinMessages: kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.emptyFlow(),
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "sportsHubProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current
    val fav by SportsFavoritesStore.flow(context).collectAsState()
    val pagerState = rememberPagerState(pageCount = { SPORTS_PIVOTS.size })

    var chosenScope by remember { mutableStateOf<HubScope?>(null) }
    val scope = chosenScope ?: if (fav.teams.isNotEmpty()) HubScope.MY_TEAMS else HubScope.MY_SPORTS
    var range by remember { mutableStateOf(ResultsRange.WEEK) }
    var picking by remember { mutableStateOf(false) }
    var pickerLeague by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<HubMatch?>(null) }
    var liveTick by remember { mutableIntStateOf(0) }
    var resultsTick by remember { mutableIntStateOf(0) }
    var detailTick by remember { mutableIntStateOf(0) }

    fun back() {
        when {
            selected != null -> selected = null
            pickerLeague != null -> pickerLeague = null
            picking -> picking = false
            else -> onDismiss()
        }
    }
    BackHandler(enabled = visible) { back() }

    val zoneOffset = remember { java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong() }
    val onResultsPage = pagerState.currentPage == 1

    // One read per open, per choice and per press of refresh; no loop.
    val liveLeagues = remember(fav.sports) { fav.sports.ifEmpty { SPORTS_LEAGUES.map { it.slug } } }
    val live by produceState(LiveState(), visible, liveLeagues, liveTick) {
        if (visible) value = loadToday(liveLeagues)
    }
    val results by produceState(ResultsState(), visible, onResultsPage, scope, range, fav.sports, fav.teams, resultsTick) {
        if (!visible || !onResultsPage) return@produceState
        val nothingToRead = if (scope == HubScope.MY_TEAMS) fav.teams.isEmpty() else fav.sports.isEmpty()
        val now = System.currentTimeMillis()
        if (nothingToRead) {
            value = ResultsState(nowMillis = now, started = true)
            return@produceState
        }
        value = ResultsState(nowMillis = now, loading = true, started = true)
        loadHubResults(scope, range, fav, now, zoneOffset) { partial ->
            value = ResultsState(partial, now, loading = true, started = true)
        }
        value = value.copy(loading = false)
    }

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.bg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            val open = selected
            when {
                open != null -> MatchDetailScreen(
                    match = open,
                    tick = detailTick,
                    nowMillis = live.nowMillis.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    tokens = tokens,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                )
                picking -> SubScreen("my sports and teams", tokens, Modifier.weight(1f)) {
                    if (pickerLeague == null) {
                        SportPicker(fav, onOpenLeague = { pickerLeague = it }, onPinTeamTile = onPinTeamTile, tokens = tokens, accent = accent)
                    } else {
                        TeamPicker(pickerLeague!!, fav, onPinTeamTile, tokens, accent)
                    }
                }
                else -> HubPanorama(
                    title = "sports",
                    sections = SPORTS_PIVOTS,
                    pagerState = pagerState,
                    tokens = tokens,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    if (page == 0) {
                        LivePage(live, fav, scope, { chosenScope = it }, zoneOffset, tokens, accent, onOpen = { selected = it }, onPick = { picking = true })
                    } else {
                        ResultsPage(results, fav, scope, { chosenScope = it }, range, { range = it }, tokens, accent, onOpen = { selected = it }, onPick = { picking = true })
                    }
                }
            }
            HubPinNote(pinMessages, tokens, accent)
            HubAppBar(
                tokens = tokens,
                actions = listOf(
                    HubAppBarAction("back", "back") { back() },
                    HubAppBarAction("plus", "choose sports and teams", "choose") {
                        selected = null
                        picking = true
                        pickerLeague = null
                    },
                    HubAppBarAction("refresh", "refresh scores", "refresh") {
                        when {
                            selected != null -> detailTick++
                            picking -> Unit
                            onResultsPage -> resultsTick++
                            else -> liveTick++
                        }
                    },
                    HubAppBarAction("pin", "pin sports to start", "pin to start", onPinHub),
                ),
            )
        }
    }
}

/** Today's boards for [leagues], read once. */
private suspend fun loadToday(leagues: List<String>): LiveState = coroutineScope {
    val now = System.currentTimeMillis()
    val boards = leagues.map { slug ->
        async {
            boardGate.withPermit {
                val name = sportsLeagueFor(slug)?.displayName ?: slug
                val events = if (slug == CRICKET_LEAGUE_SLUG) fetchCricketMatches() else fetchSportsScoreboard(slug)
                events.map { HubMatch(it, slug, name) }
            }
        }
    }
    LiveState(boards.awaitAll().flatten(), now, loaded = true)
}

// --- pages ----------------------------------------------------------------------

@Composable
private fun ScopeRow(scope: HubScope, onScope: (HubScope) -> Unit, tokens: ColorTokens, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        HubScope.entries.forEach { s -> HubFilter(s.label, scope == s, tokens, accent) { onScope(s) } }
    }
}

@Composable
private fun EmptyHint(text: String, tokens: ColorTokens, action: String? = null, accent: Color = Color.Unspecified, onAction: () -> Unit = {}) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(text, color = tokens.fgDim, fontSize = 15.sp)
        if (action != null) {
            Text(action, color = accent, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp).clickable(onClick = onAction))
        }
    }
}

@Composable
private fun LivePage(
    live: LiveState,
    fav: SportsFavorites,
    scope: HubScope,
    onScope: (HubScope) -> Unit,
    zoneOffset: Long,
    tokens: ColorTokens,
    accent: Color,
    onOpen: (HubMatch) -> Unit,
    onPick: () -> Unit,
) {
    val shown = remember(live, fav, scope) { hubTodayMatches(live.matches.filter { inScope(it, scope, fav) }, live.nowMillis, zoneOffset) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "scope") { ScopeRow(scope, onScope, tokens, accent) }
        when {
            scope == HubScope.MY_TEAMS && fav.teams.isEmpty() -> item(key = "hint") {
                EmptyHint("follow a team to see its games here", tokens, "choose teams ›", accent, onPick)
            }
            scope == HubScope.MY_SPORTS && fav.sports.isEmpty() -> item(key = "hint") {
                EmptyHint("showing every sport", tokens, "choose yours ›", accent, onPick)
            }
        }
        if (shown.isEmpty() && !(scope == HubScope.MY_TEAMS && fav.teams.isEmpty())) {
            item(key = "empty") { EmptyHint(if (live.loaded) "no matches today" else "loading scores…", tokens) }
        }
        items(shown, key = { it.key }) { MatchCard(it, live.nowMillis, fav, null, tokens, accent) { onOpen(it) } }
    }
}

@Composable
private fun ResultsPage(
    results: ResultsState,
    fav: SportsFavorites,
    scope: HubScope,
    onScope: (HubScope) -> Unit,
    range: ResultsRange,
    onRange: (ResultsRange) -> Unit,
    tokens: ColorTokens,
    accent: Color,
    onOpen: (HubMatch) -> Unit,
    onPick: () -> Unit,
) {
    val shown = remember(results, fav, scope, range) {
        hubResults(results.matches.filter { inScope(it, scope, fav) }, range, results.nowMillis)
    }
    val needsTeams = scope == HubScope.MY_TEAMS && fav.teams.isEmpty()
    val needsSports = scope == HubScope.MY_SPORTS && fav.sports.isEmpty()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "scope") { ScopeRow(scope, onScope, tokens, accent) }
        item(key = "range") {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                ResultsRange.entries.forEach { r -> HubFilter(r.label, range == r, tokens, accent) { onRange(r) } }
            }
        }
        when {
            needsTeams -> item(key = "hint") { EmptyHint("follow a team to see its results here", tokens, "choose teams ›", accent, onPick) }
            needsSports -> item(key = "hint") { EmptyHint("choose your sports to see their results", tokens, "choose sports ›", accent, onPick) }
            results.loading -> item(key = "loading") { EmptyHint("reading results…", tokens) }
            results.started && shown.isEmpty() -> item(key = "empty") { EmptyHint("no results in this time", tokens) }
        }
        items(shown, key = { it.key }) { m ->
            val team = if (scope == HubScope.MY_TEAMS) fav.teams.firstOrNull { involves(m, it) } else null
            MatchCard(m, results.nowMillis, fav, team, tokens, accent) { onOpen(m) }
        }
    }
}

// --- match card -------------------------------------------------------------------

/** One match as two score lines with its status under (the kick-off time when it hasn't started); [team] adds a W/L for it. */
@Composable
private fun MatchCard(
    match: HubMatch,
    nowMillis: Long,
    fav: SportsFavorites,
    team: SportsTile.Selection?,
    tokens: ColorTokens,
    accent: Color,
    onClick: () -> Unit,
) {
    val e = match.event
    val live = e.state == "in"
    val favourite = fav.teams.any { involves(match, it) }
    val status = matchStatus(match, nowMillis)
    val letter = team?.let { resultLetter(snapshotFor(e, it.teamId)) }
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                (if (favourite) "★ " else "") + (if (live) "● live · " else "") + match.leagueName,
                color = if (live || favourite) accent else tokens.fgDim,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            if (letter != null) ResultLetter(letter, tokens)
        }
        ScoreLine(e.homeName, e.homeScore, e.state, tokens, 17.sp)
        ScoreLine(e.awayName, e.awayScore, e.state, tokens, 17.sp)
        Text(status, color = tokens.fgDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        Box(modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(0.5.dp).background(tokens.sheetLine))
    }
}

private fun matchStatus(match: HubMatch, nowMillis: Long): String {
    val e = match.event
    if (e.state == "pre") {
        return listOfNotNull(kickoffLabel(e.epochMillis, nowMillis, ZoneId.systemDefault()), e.matchLabel).joinToString(" · ")
    }
    return sportsStatusLine(snapshotFor(e, e.homeId), nowMillis)
}

@Composable
private fun ScoreLine(name: String, score: String, state: String, tokens: ColorTokens, size: androidx.compose.ui.unit.TextUnit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text(name, color = tokens.fg, fontSize = size, fontWeight = FontWeight.Light, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        if (state != "pre") {
            Text(splitInningsScore(score).lastOrNull() ?: score, color = tokens.fg, fontSize = size, fontWeight = FontWeight.Light, maxLines = 1)
        }
    }
}

@Composable
private fun ResultLetter(letter: String, tokens: ColorTokens) {
    Text(
        letter,
        color = when (letter) {
            "W" -> ResultWin
            "L" -> ResultLoss
            else -> tokens.fgDim
        },
        fontSize = 20.sp,
        fontWeight = FontWeight.Light,
    )
}

// --- sub screens ---------------------------------------------------------------------

/** A page of its own inside the hub, Lumia-style: the app name in small capitals over a large light title. */
@Composable
private fun SubScreen(title: String, tokens: ColorTokens, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "SPORTS",
            color = tokens.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 18.dp, top = 18.dp),
        )
        Text(
            title,
            color = tokens.fg, fontSize = 34.sp, fontWeight = FontWeight.Light, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun MatchDetailScreen(match: HubMatch, tick: Int, nowMillis: Long, tokens: ColorTokens, accent: Color, modifier: Modifier = Modifier) {
    val e = match.event
    val state by produceState<DetailState>(DetailState.Loading, match.key, tick) {
        value = DetailState.Loading
        value = fetchMatchDetailData(match)?.let { DetailState.Loaded(it) } ?: DetailState.Failed
    }
    val context = LocalContext.current
    SubScreen("${e.homeName} v ${e.awayName}", tokens, modifier) {
        val loaded = (state as? DetailState.Loaded)?.data
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
            item(key = "score") {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Text(match.leagueName + (e.matchLabel?.let { " · $it" } ?: ""), color = accent, fontSize = 13.sp)
                    ScoreLine(e.homeName, e.homeScore, e.state, tokens, 24.sp)
                    ScoreLine(e.awayName, e.awayScore, e.state, tokens, 24.sp)
                    Text(
                        if (e.state == "pre") matchStatus(match, nowMillis) else loaded?.statusLine?.takeIf { it.isNotEmpty() } ?: matchStatus(match, nowMillis),
                        color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            when (val s = state) {
                DetailState.Loading -> item(key = "loading") { EmptyHint("loading the scorecard…", tokens) }
                DetailState.Failed -> item(key = "failed") { EmptyHint("couldn't load details for this match. try refresh.", tokens) }
                is DetailState.Loaded -> {
                    val d = s.data
                    if (d.now.isNotEmpty()) {
                        item(key = "now-h") { Text("right now", color = accent, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) }
                        items(d.now.size, key = { "now-$it" }) { i ->
                            Text(d.now[i], color = tokens.fg, fontSize = 15.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(vertical = 3.dp))
                        }
                    }
                    items(d.tables.size, key = { "t-$it" }) { i -> DetailTableView(d.tables[i], tokens) }
                    d.note?.let { note ->
                        item(key = "note") { Text(note, color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
                    }
                    d.webUrl?.let { url ->
                        item(key = "web") {
                            Text(
                                if (e.state == "in") "live commentary and more on espn ›" else "full match page on espn ›",
                                color = accent, fontSize = 15.sp,
                                modifier = Modifier.padding(vertical = 16.dp).clickable { openUrl(context, url) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTableView(table: DetailTable, tokens: ColorTokens) {
    val columns = table.header.size
    val weights = table.widths?.takeIf { it.size == columns } ?: List(columns) { if (it == 0) 2.4f else 1f }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(table.title, color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        if (table.header.any { it.isNotEmpty() }) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                table.header.forEachIndexed { i, h ->
                    Text(h, color = tokens.fgDim, fontSize = 11.sp, maxLines = 1, textAlign = if (i == 0) TextAlign.Start else TextAlign.End, modifier = Modifier.weight(weights[i]))
                }
            }
        }
        table.rows.forEach { row ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                for (i in 0 until columns) {
                    val cell = row.getOrElse(i) { "" }
                    val lines = cell.split('\n')
                    Column(modifier = Modifier.weight(weights[i]), horizontalAlignment = if (i == 0) Alignment.Start else Alignment.End) {
                        Text(
                            lines.first(),
                            color = tokens.fg, fontSize = 13.sp, maxLines = if (i == 0) 2 else 1, overflow = TextOverflow.Ellipsis,
                            textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                        )
                        lines.drop(1).forEach { extra ->
                            Text(extra, color = tokens.fgDim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(tokens.sheetLine))
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// --- picker ----------------------------------------------------------------------------

@Composable
private fun SportPicker(
    fav: SportsFavorites,
    onOpenLeague: (String) -> Unit,
    onPinTeamTile: (SportsTile.Selection) -> Unit,
    tokens: ColorTokens,
    accent: Color,
) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        if (fav.teams.isNotEmpty()) {
            item(key = "my-teams-h") { Text("my teams", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) }
            items(fav.teams, key = { "mt-" + it.leagueSlug + "/" + it.teamId }) { team ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(team.teamLabel, color = tokens.fg, fontSize = 17.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(sportsLeagueFor(team.leagueSlug)?.displayName.orEmpty(), color = tokens.fgDim, fontSize = 12.sp)
                    }
                    Text("tile", color = accent, fontSize = 14.sp, modifier = Modifier.clickable { onPinTeamTile(team) }.padding(horizontal = 12.dp, vertical = 6.dp))
                    Text("✕", color = tokens.fgDim, fontSize = 18.sp, modifier = Modifier.clickable { SportsFavoritesStore.toggleTeam(context, team) }.padding(start = 6.dp, top = 4.dp, bottom = 4.dp))
                }
            }
        }
        item(key = "h") {
            Text(
                "tap ☆ to follow a sport. tap its name to pick teams.",
                color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )
        }
        SPORTS_LEAGUE_CATEGORY_ORDER.forEach { category ->
            val leagues: List<SportsLeague> = SPORTS_LEAGUES.filter { it.category == category }
            if (leagues.isEmpty()) return@forEach
            item(key = "c-$category") { Text(category, color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) }
            items(leagues, key = { it.slug }) { league ->
                val on = league.slug in fav.sports
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        league.displayName,
                        color = if (on) accent else tokens.fg, fontSize = 18.sp, fontWeight = FontWeight.Light,
                        modifier = Modifier.weight(1f).clickable { onOpenLeague(league.slug) }.padding(vertical = 10.dp),
                    )
                    Text(
                        if (on) "★" else "☆",
                        color = if (on) accent else tokens.fgDim, fontSize = 22.sp,
                        modifier = Modifier.clickable { SportsFavoritesStore.toggleSport(context, league.slug) }.padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamPicker(leagueSlug: String, fav: SportsFavorites, onPinTeamTile: (SportsTile.Selection) -> Unit, tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    val league = sportsLeagueFor(leagueSlug)
    val teams by produceState<List<SportsTeam>?>(null, leagueSlug) {
        value = if (leagueSlug == CRICKET_LEAGUE_SLUG) CRICKET_TEAMS + IPL_TEAMS else fetchSportsTeams(leagueSlug)
    }
    val internationalIds = remember { CRICKET_TEAMS.map { it.id }.toSet() }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "h") {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(league?.displayName.orEmpty(), color = accent, fontSize = 15.sp)
                Text("tap a team to follow it", color = tokens.fgDim, fontSize = 13.sp)
            }
        }
        val list = teams
        if (list == null) {
            item { EmptyHint("loading teams…", tokens) }
        } else if (list.isEmpty()) {
            item { EmptyHint("couldn't load teams. check your connection.", tokens) }
        }
        items(list.orEmpty(), key = { "tp-" + it.id }) { team ->
            val on = fav.isTeamFavorite(leagueSlug, team.id)
            val sel = SportsTile.Selection(leagueSlug, team.id, team.displayName)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { SportsFavoritesStore.toggleTeam(context, sel) }.padding(vertical = 10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(team.displayName, color = if (on) accent else tokens.fg, fontSize = 18.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (leagueSlug == CRICKET_LEAGUE_SLUG) {
                        Text(if (team.id in internationalIds) "international" else "ipl", color = tokens.fgDim, fontSize = 12.sp)
                    }
                }
                if (on) {
                    Text("tile", color = accent, fontSize = 14.sp, modifier = Modifier.clickable { onPinTeamTile(sel) }.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                Text(if (on) "★" else "☆", color = if (on) accent else tokens.fgDim, fontSize = 22.sp)
            }
        }
    }
}
