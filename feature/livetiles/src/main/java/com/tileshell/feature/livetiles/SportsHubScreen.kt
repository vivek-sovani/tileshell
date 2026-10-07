package com.tileshell.feature.livetiles

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.CRICKET_LEAGUE_SLUG
import com.tileshell.core.data.CRICKET_TEAMS
import com.tileshell.core.data.FixtureTab
import com.tileshell.core.data.HubMatch
import com.tileshell.core.data.IPL_TEAMS
import com.tileshell.core.data.SPORTS_LEAGUES
import com.tileshell.core.data.SPORTS_LEAGUE_CATEGORY_ORDER
import com.tileshell.core.data.SportsFavorites
import com.tileshell.core.data.SportsLeague
import com.tileshell.core.data.SportsMatchEvent
import com.tileshell.core.data.SportsTeam
import com.tileshell.core.data.SportsTile
import com.tileshell.core.data.fetchCricketHistory
import com.tileshell.core.data.fetchCricketMatchDetail
import com.tileshell.core.data.fetchCricketMatches
import com.tileshell.core.data.fetchMatchDetail
import com.tileshell.core.data.fetchRecentCricketMatchesForTeam
import com.tileshell.core.data.fetchSportsSchedule
import com.tileshell.core.data.fetchSportsScoreboard
import com.tileshell.core.data.fetchSportsScoreboardOn
import com.tileshell.core.data.fetchSportsTeams
import com.tileshell.core.data.hubFavoriteMatches
import com.tileshell.core.data.hubFixtures
import com.tileshell.core.data.hubLiveMatches
import com.tileshell.core.data.hubSportsRefreshDelayMs
import com.tileshell.core.data.involves
import com.tileshell.core.data.pickRelevantMatch
import com.tileshell.core.data.recentSportResults
import com.tileshell.core.data.resultLetter
import com.tileshell.core.data.settings.LiveRefreshRate
import com.tileshell.core.data.settings.resolveMs
import com.tileshell.core.data.snapshotFor
import com.tileshell.core.data.splitInningsScore
import com.tileshell.core.data.sportsLeagueFor
import com.tileshell.core.data.teamHistory
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val SPORTS_PIVOTS = listOf("live", "fixtures", "my teams")

private val ResultWin = Color(0xFF35C759)
private val ResultLoss = Color(0xFFFF453A)

/** The sports tile's own default rate; the user's "live data refresh" setting overrides it. */
private const val HUB_SPORTS_REFRESH_MS = 90_000L

private val sportsGate = Semaphore(4)

/** Everything the hub shows, fetched together so the three pages agree. */
private data class SportsHubData(
    val board: List<HubMatch> = emptyList(),
    val byTeam: Map<SportsTile.Selection, List<HubMatch>> = emptyMap(),
    val nowMillis: Long = 0L,
    val loaded: Boolean = false,
)

/**
 * The sports hub (user-approved mockup, reworked around favourites). The user
 * marks any number of sports and teams in "my teams" (the "+" in the app
 * bar). "live": the matches of those sports in play or starting or finishing
 * within the day (everything, until a sport is marked, or with "all sports"),
 * games of favourite teams first. "fixtures": today, the week ahead and
 * results for the favourite teams. "my teams": each team's latest game with
 * its history on tap, a tile for it, and recent results across the favourite
 * sports. Data is ESPN's public scoreboard, the same source as the sports tile.
 *
 * Refresh follows the sports tile's rules: nothing is fetched unless the hub
 * is open, and then only while Start is the live screen (resumed, no battery
 * saver, animations on; opening it under those conditions loads once), at the
 * user's "live data refresh" rate for sports while a match is live or about
 * to start and rarely otherwise ([hubSportsRefreshDelayMs]).
 */
@Composable
fun SportsHubScreen(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    refreshRate: LiveRefreshRate,
    onDismiss: () -> Unit,
    onPinHub: () -> Unit,
    onPinTeamTile: (SportsTile.Selection) -> Unit,
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
    var refreshTick by remember { mutableIntStateOf(0) }
    var showAll by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var pickerLeague by remember { mutableStateOf<String?>(null) }
    val pagerState = rememberPagerState(pageCount = { SPORTS_PIVOTS.size })
    val scope = rememberCoroutineScope()

    fun openPicker() {
        picking = true
        pickerLeague = null
        scope.launch { pagerState.animateScrollToPage(2) }
    }
    fun back() {
        when {
            pickerLeague != null -> pickerLeague = null
            picking -> picking = false
            else -> onDismiss()
        }
    }
    BackHandler(enabled = visible) { back() }

    val active = rememberLiveTilesActive(suspended = !visible)
    // Which leagues' boards to read: just the favourites unless "all sports" is on.
    val leagues = remember(fav.sports, showAll) {
        if (showAll || fav.sports.isEmpty()) SPORTS_LEAGUES.map { it.slug } else fav.sports
    }
    val data by produceState(SportsHubData(), visible, active, leagues, fav.teams, refreshTick) {
        if (!visible) return@produceState
        while (true) {
            val loaded = loadSportsHub(leagues, fav.teams)
            value = loaded
            // Under battery saver or while backgrounded: the one load on opening, no polling.
            if (!active) break
            val events = loaded.board.map { it.event } + loaded.byTeam.values.flatten().map { it.event }
            delayUntilNextRefresh(hubSportsRefreshDelayMs(events, loaded.nowMillis, refreshRate.resolveMs(HUB_SPORTS_REFRESH_MS)))
        }
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
            HubPanorama(
                title = "sports",
                sections = SPORTS_PIVOTS,
                pagerState = pagerState,
                tokens = tokens,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> LivePage(data, fav, showAll, { showAll = it }, tokens, accent, onPick = ::openPicker)
                    1 -> FixturesPage(data, fav, tokens, accent)
                    else -> MyTeamsPage(
                        data, fav, pagerState, active, refreshTick, picking, pickerLeague,
                        onOpenPicker = ::openPicker,
                        onPickLeague = { pickerLeague = it },
                        onPinTeamTile = onPinTeamTile,
                        tokens = tokens,
                        accent = accent,
                    )
                }
            }
            HubAppBar(
                tokens = tokens,
                actions = listOf(
                    HubAppBarAction("back", "back") { back() },
                    HubAppBarAction("plus", "add sports and teams", "add") { openPicker() },
                    HubAppBarAction("refresh", "refresh scores", "refresh") { refreshTick++ },
                    HubAppBarAction("pin", "pin sports to start", "pin to start", onPinHub),
                ),
            )
        }
    }
}

private suspend fun loadSportsHub(leagues: List<String>, teams: List<SportsTile.Selection>): SportsHubData = coroutineScope {
    val now = System.currentTimeMillis()
    val boards = leagues.map { slug ->
        async {
            sportsGate.withPermit {
                val name = sportsLeagueFor(slug)?.displayName ?: slug
                val events = if (slug == CRICKET_LEAGUE_SLUG) fetchCricketMatches() else fetchSportsScoreboard(slug)
                events.map { HubMatch(it, slug, name) }
            }
        }
    }
    val byTeam = teams.map { team ->
        async {
            sportsGate.withPermit {
                val events = if (team.leagueSlug == CRICKET_LEAGUE_SLUG) {
                    fetchRecentCricketMatchesForTeam(team.teamId, now)
                } else {
                    fetchSportsSchedule(team.leagueSlug, team.teamId)
                }
                val name = sportsLeagueFor(team.leagueSlug)?.displayName ?: team.leagueSlug
                team to events.map { HubMatch(it, team.leagueSlug, name) }
            }
        }
    }
    SportsHubData(
        board = boards.awaitAll().flatten(),
        byTeam = byTeam.awaitAll().toMap(),
        nowMillis = now,
        loaded = true,
    )
}

@Composable
private fun LivePage(
    data: SportsHubData,
    fav: SportsFavorites,
    showAll: Boolean,
    onShowAll: (Boolean) -> Unit,
    tokens: ColorTokens,
    accent: Color,
    onPick: () -> Unit,
) {
    val shown = remember(data, fav, showAll) {
        if (showAll || fav.sports.isEmpty()) hubLiveMatches(data.board, data.nowMillis) else hubFavoriteMatches(data.board, fav, data.nowMillis)
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        if (fav.sports.isEmpty()) {
            item(key = "pick") {
                Text(
                    "showing every sport. choose yours to see just those ›",
                    color = accent,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp).clickable(onClick = onPick),
                )
            }
        } else {
            item(key = "scope") {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    HubFilter("my sports", !showAll, tokens, accent) { onShowAll(false) }
                    HubFilter("all sports", showAll, tokens, accent) { onShowAll(true) }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (data.loaded) "no matches on right now" else "loading scores…",
                    color = tokens.fgDim,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        items(shown, key = { it.key }) { MatchCard(it, data.nowMillis, fav, tokens, accent) }
    }
}

@Composable
private fun FixturesPage(data: SportsHubData, fav: SportsFavorites, tokens: ColorTokens, accent: Color) {
    var tab by remember { mutableStateOf(FixtureTab.TODAY) }
    // Today comes from the favourite sports' boards; the week ahead and the
    // results from the favourite teams' own schedules.
    val source = remember(data, fav) {
        val board = if (fav.sports.isEmpty()) data.board else data.board.filter { it.leagueSlug in fav.sports }
        (board + data.byTeam.values.flatten()).distinctBy { it.key }
    }
    val zoneOffset = remember { java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong() }
    val shown = remember(source, tab, data.nowMillis) { hubFixtures(source, tab, data.nowMillis, zoneOffset) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "tabs") {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                FixtureTab.entries.forEach { t -> HubFilter(t.label, tab == t, tokens, accent) { tab = t } }
            }
        }
        if (fav.teams.isEmpty() && tab != FixtureTab.TODAY) {
            item(key = "hint") {
                Text(
                    "the week ahead and results come from your favourite teams. add one in my teams.",
                    color = tokens.fgDim,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        } else if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (data.loaded) "nothing here" else "loading fixtures…",
                    color = tokens.fgDim,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        items(shown, key = { it.key }) { MatchCard(it, data.nowMillis, fav, tokens, accent) }
    }
}

@Composable
private fun MyTeamsPage(
    data: SportsHubData,
    fav: SportsFavorites,
    pagerState: PagerState,
    active: Boolean,
    tick: Int,
    picking: Boolean,
    pickerLeague: String?,
    onOpenPicker: () -> Unit,
    onPickLeague: (String) -> Unit,
    onPinTeamTile: (SportsTile.Selection) -> Unit,
    tokens: ColorTokens,
    accent: Color,
) {
    if (picking) {
        if (pickerLeague == null) SportPicker(fav, onPickLeague, tokens, accent) else TeamPicker(pickerLeague, fav, tokens, accent)
        return
    }
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(setOf<String>()) }
    // Recent results across the favourite sports: only read while this page is the one showing.
    val onThisPage = pagerState.currentPage == 2
    val results by produceState(emptyList<HubMatch>(), fav.sports, onThisPage, tick) {
        value = if (onThisPage && fav.sports.isNotEmpty()) loadRecentSportResults(fav.sports) else emptyList()
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "sports-h") {
            Text("my sports", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
        }
        if (fav.sports.isEmpty()) {
            item(key = "sports-empty") {
                Text(
                    "choose the sports you follow. the live page then shows just those.",
                    color = tokens.fg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
        items(fav.sports, key = { "s-$it" }) { slug ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(
                    sportsLeagueFor(slug)?.displayName ?: slug,
                    color = tokens.fg, fontSize = 17.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f),
                )
                Text(
                    "✕", color = tokens.fgDim, fontSize = 18.sp,
                    modifier = Modifier.clickable { SportsFavoritesStore.toggleSport(context, slug) }.padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
                )
            }
        }
        item(key = "add") {
            Text("add sports and teams ›", color = accent, fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp).clickable(onClick = onOpenPicker))
        }
        if (fav.teams.isNotEmpty()) {
            item(key = "teams-h") {
                Text("my teams", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            }
        }
        items(fav.teams, key = { "t-" + it.leagueSlug + "/" + it.teamId }) { team ->
            val id = team.leagueSlug + "/" + team.teamId
            TeamBlock(
                team = team,
                matches = data.byTeam[team].orEmpty(),
                nowMillis = data.nowMillis,
                loaded = data.loaded,
                open = id in expanded,
                onToggle = { expanded = if (id in expanded) expanded - id else expanded + id },
                onPinTile = { onPinTeamTile(team) },
                onRemove = { SportsFavoritesStore.toggleTeam(context, team) },
                tokens = tokens,
                accent = accent,
            )
        }
        if (results.isNotEmpty()) {
            item(key = "results-h") {
                Text("recent results in my sports", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
            }
            items(results, key = { "r-" + it.key }) { MatchCard(it, data.nowMillis, fav, tokens, accent) }
        }
    }
}

/** One favourite team: its latest game, and on tap its history, a tile for it and a way to drop it. */
@Composable
private fun TeamBlock(
    team: SportsTile.Selection,
    matches: List<HubMatch>,
    nowMillis: Long,
    loaded: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
    onPinTile: () -> Unit,
    onRemove: () -> Unit,
    tokens: ColorTokens,
    accent: Color,
) {
    val latest = pickRelevantMatch(matches.map { it.event }, nowMillis)
    val snapshot = latest?.let { snapshotFor(it, team.teamId) }
    val letter = snapshot?.let(::resultLetter)
    // Cricket has no per-team schedule: its history is read from past days' feeds, only once opened.
    val cricketHistory by produceState(emptyList<HubMatch>(), team, open) {
        value = if (open && team.leagueSlug == CRICKET_LEAGUE_SLUG) {
            fetchCricketHistory(team.teamId, nowMillis).map { HubMatch(it, team.leagueSlug, "Cricket") }
        } else {
            emptyList()
        }
    }
    val history = remember(matches, cricketHistory, nowMillis) { teamHistory(matches + cricketHistory, team.teamId, nowMillis) }

    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(team.teamLabel, color = tokens.fg, fontSize = 17.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sportsLeagueFor(team.leagueSlug)?.displayName.orEmpty(), color = tokens.fgDim, fontSize = 12.sp)
            }
            when {
                snapshot?.state == "in" -> Text("live", color = accent, fontSize = 15.sp)
                letter != null -> ResultLetter(letter, tokens)
            }
            Text(if (open) "▴" else "▾", color = tokens.fgDim, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        }
        if (snapshot != null) {
            Text(
                scoreLine(snapshot.teamAbbr, snapshot.teamScore, snapshot.opponentAbbr, snapshot.opponentScore),
                color = tokens.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(sportsStatusLine(snapshot, nowMillis), color = tokens.fgDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else if (loaded) {
            Text("no games found", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (open) {
            Text("history", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            if (history.isEmpty()) {
                Text(if (loaded) "no finished games yet" else "loading…", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
            history.forEach { m ->
                val s = snapshotFor(m.event, team.teamId)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            scoreLine(s.teamAbbr, s.teamScore, s.opponentAbbr, s.opponentScore),
                            color = tokens.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(sportsStatusLine(s, nowMillis), color = tokens.fgDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    resultLetter(s)?.let { ResultLetter(it, tokens) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 10.dp)) {
                Text("pin a tile", color = accent, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onPinTile))
                Text("remove", color = tokens.fgDim, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onRemove))
            }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(0.5.dp).background(tokens.sheetLine))
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

/** `BKN 124  v  CHA 90`, with a cricket side's score trimmed to its latest innings. */
private fun scoreLine(abbr: String, score: String, opponentAbbr: String, opponentScore: String): String {
    val ours = splitInningsScore(score).lastOrNull() ?: score
    val theirs = splitInningsScore(opponentScore).lastOrNull() ?: opponentScore
    return "$abbr $ours  v  $opponentAbbr $theirs"
}

/** Finished games in the favourite sports over the last few days; yesterday's and the day before's boards are read here. */
private suspend fun loadRecentSportResults(sports: List<String>): List<HubMatch> = coroutineScope {
    val now = System.currentTimeMillis()
    val dates = (0..2).map { back ->
        val c = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -back) }
        String.format(java.util.Locale.US, "%04d%02d%02d", c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
    }
    val all = sports.flatMap { slug -> dates.map { slug to it } }.map { (slug, date) ->
        async {
            sportsGate.withPermit {
                val name = sportsLeagueFor(slug)?.displayName ?: slug
                val events: List<SportsMatchEvent> =
                    if (slug == CRICKET_LEAGUE_SLUG) fetchCricketMatches(date) else fetchSportsScoreboardOn(slug, date)
                events.map { HubMatch(it, slug, name) }
            }
        }
    }.awaitAll().flatten()
    recentSportResults(all, sports, now)
}

@Composable
private fun SportPicker(fav: SportsFavorites, onOpen: (String) -> Unit, tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "h") {
            Text(
                "tap ☆ to follow a sport. tap its name to pick teams.",
                color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        SPORTS_LEAGUE_CATEGORY_ORDER.forEach { category ->
            val leagues: List<SportsLeague> = SPORTS_LEAGUES.filter { it.category == category }
            if (leagues.isEmpty()) return@forEach
            item(key = "c-$category") {
                Text(category, color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            }
            items(leagues, key = { it.slug }) { league ->
                val on = league.slug in fav.sports
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        league.displayName,
                        color = if (on) accent else tokens.fg, fontSize = 18.sp, fontWeight = FontWeight.Light,
                        modifier = Modifier.weight(1f).clickable { onOpen(league.slug) }.padding(vertical = 10.dp),
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
private fun TeamPicker(leagueSlug: String, fav: SportsFavorites, tokens: ColorTokens, accent: Color) {
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
            item { Text("loading teams…", color = tokens.fgDim, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp)) }
        } else if (list.isEmpty()) {
            item { Text("couldn't load teams. check your connection.", color = tokens.fgDim, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp)) }
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
                Spacer(Modifier.width(12.dp))
                Text(if (on) "★" else "☆", color = if (on) accent else tokens.fgDim, fontSize = 22.sp)
            }
        }
    }
}

/** One match as two score lines with its status under; tap opens ESPN's page for it. */
@Composable
private fun MatchCard(match: HubMatch, nowMillis: Long, fav: SportsFavorites, tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val e = match.event
    val live = e.state == "in"
    val favourite = fav.teams.any { involves(match, it) }
    val status = sportsStatusLine(snapshotFor(e, e.homeId), nowMillis)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { scope.launch { openMatchPage(context, match) } }
            .padding(vertical = 9.dp),
    ) {
        Text(
            (if (favourite) "★ " else "") + (if (live) "● live · " else "") + match.leagueName,
            color = if (live || favourite) accent else tokens.fgDim,
            fontSize = 12.sp,
        )
        ScoreLine(e.homeName, e.homeScore, e.state, tokens)
        ScoreLine(e.awayName, e.awayScore, e.state, tokens)
        Text(status, color = tokens.fgDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        Box(modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(0.5.dp).background(tokens.sheetLine))
    }
}

@Composable
private fun ScoreLine(name: String, score: String, state: String, tokens: ColorTokens) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text(name, color = tokens.fg, fontSize = 17.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        if (state != "pre") {
            Text(
                splitInningsScore(score).lastOrNull() ?: score,
                color = tokens.fg,
                fontSize = 17.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
            )
        }
    }
}

private suspend fun openMatchPage(context: Context, match: HubMatch) {
    val detail = if (match.leagueSlug == CRICKET_LEAGUE_SLUG) {
        match.event.leagueId?.let { fetchCricketMatchDetail(it, match.event.id) }
    } else {
        fetchMatchDetail(match.leagueSlug, match.event.id)
    }
    val url = detail?.webUrl
    if (url == null) {
        Toast.makeText(context, "no match page available", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
