package com.tileshell.feature.personalize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.CRICKET_LEAGUE_SLUG
import com.tileshell.core.data.CRICKET_TEAMS
import com.tileshell.core.data.IPL_TEAMS
import com.tileshell.core.data.SPORTS_LEAGUES
import com.tileshell.core.data.SPORTS_LEAGUE_CATEGORY_ORDER
import com.tileshell.core.data.SportsLeague
import com.tileshell.core.data.SportsMatchEvent
import com.tileshell.core.data.SportsTeam
import com.tileshell.core.data.fetchCricketMatches
import com.tileshell.core.data.fetchSportsScoreboard
import com.tileshell.core.data.fetchSportsTeams
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens

/**
 * The Sports tile's own picker — opened by tapping a not-yet-configured
 * sports tile. Two steps in one sheet (league, then team), the same nullable-
 * state navigation [NotesSheet] uses for its list/editor split: [selectedLeague]
 * null means "showing the league list," non-null means "showing that
 * league's teams." Tapping a team pins it via [onTeamPicked] and closes the
 * sheet; there's no delete here, same as [StickyNoteEditorSheet]/
 * [CountdownEditorSheet] — removing a sports tile means unpinning it.
 */
@Composable
fun SportsPickerSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    tileId: String?,
    onTeamPicked: (id: String, leagueSlug: String, teamId: String, teamLabel: String) -> Unit,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "sportsPickerProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)

    var selectedLeague by remember { mutableStateOf<SportsLeague?>(null) }
    LaunchedEffect(visible) { if (visible) selectedLeague = null }

    BackHandler(enabled = visible) {
        if (selectedLeague != null) selectedLeague = null else onDismiss()
    }

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .graphicsLayer { translationY = size.height * (1f - progress) }
                    .background(tokens.sheet, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tokens.fgDim.copy(alpha = 0.5f)),
                )

                val league = selectedLeague
                if (league == null) {
                    LeagueListContent(tokens = tokens, onPick = { selectedLeague = it })
                } else {
                    TeamListContent(
                        league = league,
                        accent = accent,
                        tokens = tokens,
                        onBack = { selectedLeague = null },
                        onPick = { team ->
                            tileId?.let { id -> onTeamPicked(id, league.slug, team.id, team.displayName) }
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LeagueListContent(tokens: ColorTokens, onPick: (SportsLeague) -> Unit) {
    Text(
        text = "sports",
        color = tokens.fg,
        fontSize = 20.sp,
        fontWeight = FontWeight.W300,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
    )
    Text(
        text = "pick a league, then a team to follow",
        color = tokens.fgDim,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
    )
    HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp))
    val byCategory = SPORTS_LEAGUES.groupBy { it.category }
    LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        SPORTS_LEAGUE_CATEGORY_ORDER.forEach { category ->
            val leagues = byCategory[category].orEmpty()
            if (leagues.isEmpty()) return@forEach
            item(key = "category-$category") {
                Text(
                    text = category,
                    color = tokens.fgDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(leagues, key = { it.slug }) { league ->
                Text(
                    text = league.displayName,
                    color = tokens.fg,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onPick(league) })
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/** "live" / "final" / "upcoming" — matches [com.tileshell.feature.livetiles.sportsStateLabel]'s wording. */
private fun stateLabel(state: String): String = when (state) {
    "in" -> "live"
    "post" -> "final"
    else -> "upcoming"
}

@Composable
private fun TeamListContent(
    league: SportsLeague,
    accent: Color,
    tokens: ColorTokens,
    onBack: () -> Unit,
    onPick: (SportsTeam) -> Unit,
) {
    val isCricket = league.slug == CRICKET_LEAGUE_SLUG

    // The full roster: a fixed, curated list for cricket (see CRICKET_TEAMS'
    // own doc comment on why there's no "all teams" endpoint to fetch for it —
    // countries plus the 10 current IPL franchises, IPL_TEAMS), fetched live
    // for every club league.
    var teams by remember(league) {
        mutableStateOf<List<SportsTeam>?>(if (isCricket) CRICKET_TEAMS + IPL_TEAMS else null)
    }
    LaunchedEffect(league) { if (!isCricket) teams = fetchSportsTeams(league.slug) }

    // Matches happening right now (or today), so a team that's actually
    // playing can be picked straight away instead of scrolling to find it —
    // per user request, this applies to every league, not just cricket.
    var ongoing by remember(league) { mutableStateOf<List<SportsMatchEvent>?>(null) }
    LaunchedEffect(league) {
        ongoing = if (isCricket) fetchCricketMatches() else fetchSportsScoreboard(league.slug)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹ leagues",
            color = accent,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 6.dp, vertical = 8.dp),
        )
    }
    Text(
        text = league.displayName,
        color = tokens.fg,
        fontSize = 20.sp,
        fontWeight = FontWeight.W300,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
    )
    Text(
        text = "pick a team to follow",
        color = tokens.fgDim,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
    )
    HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp))

    val current = teams
    if (current == null) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp))
        }
        return
    }
    if (current.isEmpty()) {
        Text(
            text = "couldn't load teams — check your connection and try again",
            color = tokens.fgDim,
            fontSize = 14.sp,
            modifier = Modifier.padding(20.dp),
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        val liveOngoing = ongoing?.filter { it.state == "in" }.orEmpty()
        if (liveOngoing.isNotEmpty()) {
            item {
                Text(
                    text = "ongoing now",
                    color = tokens.fgDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(liveOngoing, key = { "ongoing-${it.id}" }) { match ->
                OngoingMatchRow(match = match, tokens = tokens, onPick = onPick)
            }
            item {
                HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
        }
        if (isCricket) {
            item {
                Text(
                    text = "international",
                    color = tokens.fgDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(CRICKET_TEAMS, key = { "intl-${it.id}" }) { team -> TeamRow(team, tokens, onPick) }
            item {
                Text(
                    text = "ipl",
                    color = tokens.fgDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(IPL_TEAMS, key = { "ipl-${it.id}" }) { team -> TeamRow(team, tokens, onPick) }
        } else {
            items(current, key = { it.id }) { team -> TeamRow(team, tokens, onPick) }
        }
    }
}

@Composable
private fun TeamRow(team: SportsTeam, tokens: ColorTokens, onPick: (SportsTeam) -> Unit) {
    Text(
        text = team.displayName,
        color = tokens.fg,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onPick(team) })
            .padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

/**
 * One currently-live match, with each side independently tappable — picking
 * either side follows that team directly. Built straight from the match's
 * own team names/ids rather than cross-referenced against the roster list,
 * so this works even for a team not in [CRICKET_TEAMS] (a domestic side
 * playing a one-off match, say) as long as it's actually on the board.
 */
@Composable
private fun OngoingMatchRow(match: SportsMatchEvent, tokens: ColorTokens, onPick: (SportsTeam) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = match.homeName,
                color = tokens.fg,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = { onPick(SportsTeam(match.homeId, match.homeName, match.homeAbbr)) })
                    .padding(vertical = 6.dp),
            )
            Text(
                text = "vs",
                color = tokens.fgDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Text(
                text = match.awayName,
                color = tokens.fg,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = { onPick(SportsTeam(match.awayId, match.awayName, match.awayAbbr)) })
                    .padding(vertical = 6.dp),
            )
        }
        Text(
            text = "${stateLabel(match.state)} · ${match.statusDetail}".trim(' ', '·'),
            color = tokens.fgDim,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
