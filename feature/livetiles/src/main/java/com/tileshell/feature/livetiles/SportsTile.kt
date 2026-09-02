package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.CRICKET_LEAGUE_SLUG
import com.tileshell.core.data.SportsMatchDetail
import com.tileshell.core.data.SportsSnapshot
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.fetchCricketMatchDetail
import com.tileshell.core.data.fetchMatchDetail
import com.tileshell.core.data.fetchRecentCricketMatchForTeam
import com.tileshell.core.data.fetchSportsSchedule
import com.tileshell.core.data.pickRelevantMatch
import com.tileshell.core.data.settings.LiveRefreshRate
import com.tileshell.core.data.settings.resolveMs
import com.tileshell.core.data.snapshotFor
import com.tileshell.core.data.splitInningsScore
import com.tileshell.core.design.LocalTileFaceColor

/** How often the tile re-polls ESPN while it's on screen, absent a Personalize override — see [LiveRefreshRate]. */
private const val SPORTS_REFRESH_MS = 90_000L

/**
 * The last-known web page for each pinned sports tile's current match, keyed
 * by tile id — written by [SportsTileFace]'s own poll loop, read at tap time
 * by Start's tile-click routing (see `StartScreen.kt`'s `onTileClick`) so a
 * configured tile opens the match's real ESPN page instead of reopening the
 * team picker. A plain in-memory map, not persisted: the same "only matters
 * while the tile has actually loaded once this process" scope [MediaCenter]
 * already uses for now-playing state.
 */
object SportsLinks {
    private val urls = HashMap<String, String>()

    @Synchronized
    fun set(tileId: String, url: String) {
        urls[tileId] = url
    }

    @Synchronized
    fun get(tileId: String): String? = urls[tileId]
}

/** "live" / "final" / "upcoming" — pure, off just the ESPN `state` string. */
fun sportsStateLabel(state: String): String = when (state) {
    "in" -> "live"
    "post" -> "final"
    else -> "upcoming"
}

/**
 * A coarse "how much room do we actually have" tier beyond the original
 * narrow/short/LARGE split — [SportsFront]/[SportsBack] used to lump every
 * non-narrow, non-short, non-[TileSize.LARGE] size (MEDIUM, WIDE,
 * [TileSize.WIDE_MEDIUM], [TileSize.TALL_MEDIUM], [TileSize.XLARGE]) into one
 * identically-sized default, so a 4×4 XLARGE tile rendered the exact same
 * text size as a 2×2 MEDIUM one. Mirrors the tuned-branch-per-size treatment
 * this app already gave the generic notification tile for the same reason
 * (see docs/DECISIONS.md "Non-standard notification tile sizes now use
 * their full available space").
 */
private enum class SportsSizeTier { COMPACT, ROOMY, LARGE, EXTRA_LARGE }

private fun sportsSizeTier(size: TileSize): SportsSizeTier = when (size) {
    TileSize.XLARGE -> SportsSizeTier.EXTRA_LARGE
    TileSize.LARGE -> SportsSizeTier.LARGE
    TileSize.WIDE_MEDIUM, TileSize.TALL_MEDIUM -> SportsSizeTier.ROOMY
    else -> SportsSizeTier.COMPACT // MEDIUM, WIDE
}

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

/**
 * The live sports tile: the followed team's most relevant game right now —
 * live if one's in progress, else the last final score, else the next
 * scheduled matchup (see [pickRelevantMatch]). One tile is one team, like the
 * countdown/sticky note tiles, so more than one can be pinned. Re-polls
 * ESPN's public scoreboard every [SPORTS_REFRESH_MS] while [active] (a
 * Personalize "live data refresh" [refreshRate] overrides that interval,
 * and [delayUntilNextRefresh] aligns every sports tile sharing the same
 * interval to the same wall-clock instants), the same "only while actually
 * on screen" gate the music/clock tiles use, also fetching the match's own
 * detail (contributors + web link — see [SportsMatchDetail]) on the same
 * cadence. Shows "tap to choose a team" until
 * [com.tileshell.feature.personalize.SportsPickerSheet] has actually picked
 * one.
 */
@Composable
fun SportsTileFace(
    size: TileSize,
    flipped: Boolean,
    active: Boolean,
    tileId: String,
    leagueSlug: String,
    teamId: String,
    teamLabel: String,
    refreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    modifier: Modifier = Modifier,
) {
    if (leagueSlug.isBlank() || teamId.isBlank()) {
        NoTeamPickedFace(size, modifier)
        return
    }

    var snapshot by remember(leagueSlug, teamId) { mutableStateOf<SportsSnapshot?>(null) }
    var detail by remember(leagueSlug, teamId) { mutableStateOf<SportsMatchDetail?>(null) }
    LaunchedEffect(leagueSlug, teamId, active, refreshRate) {
        if (!active) return@LaunchedEffect
        while (true) {
            // Cricket has no per-team schedule endpoint (see CRICKET_LEAGUE_SLUG's
            // own doc comment) — fetchRecentCricketMatchForTeam checks today's
            // live/imminent cross-tournament feed first, then walks backward day
            // by day when that's empty, since a finished match otherwise drops
            // out of that feed within about a day (verified live).
            val relevant = if (leagueSlug == CRICKET_LEAGUE_SLUG) {
                fetchRecentCricketMatchForTeam(teamId, System.currentTimeMillis())
            } else {
                pickRelevantMatch(fetchSportsSchedule(leagueSlug, teamId), System.currentTimeMillis())
            }
            val matchDetail = relevant?.let { ev ->
                if (leagueSlug == CRICKET_LEAGUE_SLUG) {
                    ev.leagueId?.let { fetchCricketMatchDetail(it, ev.id) }
                } else {
                    fetchMatchDetail(leagueSlug, ev.id)
                }
            }
            detail = matchDetail
            // The schedule call's own score is never trustworthy for a soccer
            // team (see scoreOf's doc comment) — patch in the summary call's
            // always-inline number once it's back.
            val resolvedHome = matchDetail?.homeScore
            val resolvedAway = matchDetail?.awayScore
            val corrected = if (resolvedHome != null && resolvedAway != null) {
                relevant?.copy(homeScore = resolvedHome, awayScore = resolvedAway)
            } else {
                relevant
            }
            snapshot = corrected?.let { snapshotFor(it, teamId) }
            matchDetail?.webUrl?.let { SportsLinks.set(tileId, it) }
            delayUntilNextRefresh(refreshRate.resolveMs(SPORTS_REFRESH_MS))
        }
    }

    val current = snapshot
    if (current == null) {
        NoDataFace(size, teamLabel, modifier)
        return
    }

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { SportsFront(current, size) },
        back = {
            SportsBack(
                snapshot = current,
                teamLabel = teamLabel,
                scorerLines = detail?.contributorLines.orEmpty(),
                battingLines = detail?.battingLines.orEmpty(),
                bowlingLines = detail?.bowlingLines.orEmpty(),
                size = size,
            )
        },
    )
}

@Composable
private fun NoTeamPickedFace(size: TileSize, modifier: Modifier) {
    val narrow = size.narrowLive
    val short = size.shortLive
    Column(
        modifier = modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = "tap to choose a team",
            color = FaceText.copy(alpha = 0.82f),
            fontSize = if (short) 12.sp else 14.sp,
            maxLines = if (narrow) 3 else 2,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}

@Composable
private fun NoDataFace(size: TileSize, teamLabel: String, modifier: Modifier) {
    val narrow = size.narrowLive
    val short = size.shortLive
    Column(
        modifier = modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = teamLabel.ifBlank { "sports" },
            color = FaceText,
            fontSize = if (short) 15.sp else 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        Text(
            text = "no schedule data yet",
            color = FaceText.copy(alpha = 0.7f),
            fontSize = if (short) 11.sp else 13.sp,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}

/**
 * Both sides at once — team vs. opponent, each with its own score column —
 * rather than a single "us vs. them" line, so a multi-innings cricket score
 * (see [splitInningsScore]) has somewhere to put a second line per side, the
 * same shape ESPN's own match header uses. Narrow (1-column-wide) tiles keep
 * the older single-line layout instead — there's no room for two side-by-side
 * columns there.
 */
@Composable
private fun SportsFront(snapshot: SportsSnapshot, size: TileSize) {
    val narrow = size.narrowLive
    val short = size.shortLive
    val tier = sportsSizeTier(size)
    val roomyOrBigger = tier != SportsSizeTier.COMPACT

    if (narrow) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${if (snapshot.isHome) "vs" else "at"} ${snapshot.opponentAbbr.ifBlank { snapshot.opponentName }}",
                color = FaceText.copy(alpha = 0.82f),
                fontSize = 12.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "${snapshot.teamScore} – ${snapshot.opponentScore}",
                color = FaceText,
                fontSize = 24.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-1).sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = sportsStateLabel(snapshot.state),
                color = FaceText.copy(alpha = 0.82f),
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val abbrSize = if (short) {
        11.sp
    } else {
        when (tier) {
            SportsSizeTier.COMPACT -> 12.sp
            SportsSizeTier.ROOMY -> 13.sp
            SportsSizeTier.LARGE -> 14.sp
            SportsSizeTier.EXTRA_LARGE -> 17.sp
        }
    }
    val scoreSize = if (short) {
        16.sp
    } else {
        when (tier) {
            SportsSizeTier.COMPACT -> 20.sp
            SportsSizeTier.ROOMY -> 23.sp
            SportsSizeTier.LARGE -> 26.sp
            SportsSizeTier.EXTRA_LARGE -> 34.sp
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(if (short) 6.dp else if (tier == SportsSizeTier.EXTRA_LARGE) 16.dp else 11.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TeamScoreColumn(
                abbr = snapshot.teamAbbr.ifBlank { snapshot.teamName },
                scoreLines = splitInningsScore(snapshot.teamScore),
                abbrSize = abbrSize,
                scoreSize = scoreSize,
                alignEnd = false,
            )
            TeamScoreColumn(
                abbr = snapshot.opponentAbbr.ifBlank { snapshot.opponentName },
                scoreLines = splitInningsScore(snapshot.opponentScore),
                abbrSize = abbrSize,
                scoreSize = scoreSize,
                alignEnd = true,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = snapshot.statusDetail.ifBlank { sportsStateLabel(snapshot.state) },
            color = FaceText.copy(alpha = 0.82f),
            fontSize = if (short) 10.sp else if (tier == SportsSizeTier.EXTRA_LARGE) 15.sp else 12.sp,
            maxLines = if (roomyOrBigger) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (tier == SportsSizeTier.LARGE || tier == SportsSizeTier.EXTRA_LARGE) {
            Text(
                "sports",
                color = FaceText.copy(alpha = 0.6f),
                fontSize = if (tier == SportsSizeTier.EXTRA_LARGE) 14.sp else 11.sp,
            )
        }
    }
}

@Composable
private fun TeamScoreColumn(
    abbr: String,
    scoreLines: List<String>,
    abbrSize: TextUnit,
    scoreSize: TextUnit,
    alignEnd: Boolean,
) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            text = abbr,
            color = FaceText.copy(alpha = 0.82f),
            fontSize = abbrSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // At most the last two innings — a rain-shortened Test could carry a
        // third, but the tile has no room for it and the two most recent are
        // what's actually relevant right now.
        scoreLines.takeLast(2).forEach { line ->
            Text(
                text = line,
                color = FaceText,
                fontSize = scoreSize,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SportsBack(
    snapshot: SportsSnapshot,
    teamLabel: String,
    scorerLines: List<String>,
    battingLines: List<String>,
    bowlingLines: List<String>,
    size: TileSize,
) {
    val narrow = size.narrowLive
    val short = size.shortLive
    val tier = sportsSizeTier(size)
    val big = tier == SportsSizeTier.LARGE || tier == SportsSizeTier.EXTRA_LARGE
    // A WIDE_MEDIUM (3 cols) has almost as much spare width as WIDE (4 cols)
    // and just as little height to work with — the same side-by-side
    // batting/bowling layout fits it well, unlike TALL_MEDIUM (2 cols) which
    // has the opposite shape (room to stack, not to spread sideways).
    val sideBySide = size == TileSize.WIDE || size == TileSize.WIDE_MEDIUM
    val hasCricket = battingLines.isNotEmpty() || bowlingLines.isNotEmpty()
    val hasScorers = scorerLines.isNotEmpty()

    if (!hasCricket && !hasScorers) {
        Column(
            modifier = Modifier.fillMaxSize().padding(if (narrow) 4.dp else 11.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Text(
                text = teamLabel.ifBlank { "sports" },
                color = FaceText,
                fontSize = if (narrow) 18.sp else if (tier == SportsSizeTier.EXTRA_LARGE) 30.sp else 22.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = snapshot.statusDetail.ifBlank { sportsStateLabel(snapshot.state) },
                color = FaceText.copy(alpha = 0.65f),
                fontSize = if (narrow) 11.sp else if (tier == SportsSizeTier.EXTRA_LARGE) 16.sp else 13.sp,
                maxLines = 2,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
        }
        return
    }

    // Not much room even at LARGE (3×3): who's contributing right now takes
    // priority over the team name once there's actually something to say,
    // since the front face already names the team. Batting/bowling get their
    // own labeled sections with one player per line — filling the tile's
    // real height/width instead of one cramped, comma-joined sentence — and
    // a wide-enough tile ([sideBySide]) puts them side by side since it has
    // width to spare but not much height.
    Column(
        modifier = Modifier.fillMaxSize().padding(if (big) 12.dp else if (short) 6.dp else 9.dp),
    ) {
        Text(
            text = snapshot.statusDetail.ifBlank { sportsStateLabel(snapshot.state) },
            color = FaceText.copy(alpha = 0.6f),
            fontSize = if (tier == SportsSizeTier.EXTRA_LARGE) 14.sp else if (big) 12.sp else 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(if (big) 10.dp else 6.dp))
        when {
            hasCricket && sideBySide -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (battingLines.isNotEmpty()) {
                    ContributorColumn(
                        modifier = Modifier.weight(1f),
                        label = "batting",
                        lines = battingLines.take(2),
                        tier = tier,
                    )
                }
                if (bowlingLines.isNotEmpty()) {
                    ContributorColumn(
                        modifier = Modifier.weight(1f),
                        label = "bowling",
                        lines = bowlingLines.take(2),
                        tier = tier,
                    )
                }
            }
            hasCricket -> {
                val maxLines = when {
                    tier == SportsSizeTier.EXTRA_LARGE -> 6
                    big -> 4
                    short -> 1
                    else -> 2
                }
                if (battingLines.isNotEmpty()) {
                    ContributorSection("batting", battingLines.take(maxLines), tier)
                }
                if (bowlingLines.isNotEmpty()) {
                    if (battingLines.isNotEmpty()) Spacer(Modifier.height(if (big) 10.dp else 6.dp))
                    ContributorSection("bowling", bowlingLines.take(if (tier == SportsSizeTier.EXTRA_LARGE) 5 else if (big) 3 else if (short) 1 else 2), tier)
                }
            }
            else -> ContributorSection("goals", scorerLines.take(if (tier == SportsSizeTier.EXTRA_LARGE) 6 else if (big) 4 else 2), tier)
        }
    }
}

@Composable
private fun ContributorSection(label: String, lines: List<String>, tier: SportsSizeTier) {
    Column {
        ContributorLabel(label, tier)
        lines.forEach { line ->
            Text(
                text = line,
                color = FaceText,
                fontSize = when (tier) {
                    SportsSizeTier.EXTRA_LARGE -> 17.sp
                    SportsSizeTier.LARGE -> 14.sp
                    SportsSizeTier.ROOMY -> 13.sp
                    SportsSizeTier.COMPACT -> 12.sp
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ContributorColumn(modifier: Modifier, label: String, lines: List<String>, tier: SportsSizeTier) {
    Column(modifier = modifier) {
        ContributorLabel(label, tier)
        lines.forEach { line ->
            Text(
                text = line,
                color = FaceText,
                fontSize = when (tier) {
                    SportsSizeTier.EXTRA_LARGE -> 15.sp
                    SportsSizeTier.LARGE -> 13.sp
                    SportsSizeTier.ROOMY -> 12.sp
                    SportsSizeTier.COMPACT -> 11.sp
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ContributorLabel(text: String, tier: SportsSizeTier) {
    Text(
        text = text,
        color = FaceText.copy(alpha = 0.55f),
        fontSize = if (tier == SportsSizeTier.EXTRA_LARGE) 13.sp else if (tier == SportsSizeTier.LARGE) 11.sp else 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        maxLines = 1,
    )
}
