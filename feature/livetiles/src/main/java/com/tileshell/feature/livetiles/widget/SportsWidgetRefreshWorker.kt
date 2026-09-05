package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tileshell.core.data.CRICKET_LEAGUE_SLUG
import com.tileshell.core.data.SportsTile
import com.tileshell.core.data.fetchCricketMatchDetail
import com.tileshell.core.data.fetchMatchDetail
import com.tileshell.core.data.fetchRecentCricketMatchForTeam
import com.tileshell.core.data.fetchSportsSchedule
import com.tileshell.core.data.pickRelevantMatch
import com.tileshell.core.data.shouldFetchSports
import com.tileshell.core.data.snapshotFor
import com.tileshell.core.data.splitInningsScore
import com.tileshell.core.data.sportsLeagueFor
import com.tileshell.feature.livetiles.R
import com.tileshell.feature.livetiles.sportsStateLabel
import java.util.concurrent.TimeUnit

private val MEMBER_LINE_IDS = listOf(R.id.widget_line_1, R.id.widget_line_2, R.id.widget_line_3, R.id.widget_line_4)

/**
 * Builds + pushes the sports widget's [RemoteViews]. Mirrors the in-app
 * tile's own fetch shape: cricket has no per-team schedule endpoint, so a
 * followed cricket team means [fetchRecentCricketMatchForTeam] — today's
 * live/imminent feed first, falling back to walking backward day by day when
 * that's empty, since a finished international match otherwise drops out of
 * the "right now" feed within about a day (verified live) — while a club
 * league just calls [fetchSportsSchedule] directly and [pickRelevantMatch]
 * on the result (live game first, else most-recently-finished, else
 * soonest-upcoming). A soccer match's schedule-derived score is a `$ref`
 * link with no inline value (see [com.tileshell.core.data.scoreOf]'s own
 * doc comment), so it's patched from [fetchMatchDetail]'s always-inline
 * header score the same way [com.tileshell.feature.livetiles.SportsTileFace]
 * patches it in-app — skipping that patch here would show soccer scores as
 * "-" about half the time.
 *
 * No cache layer — same "fetch inline in doWork" choice as
 * [StockWidgetRefreshWorker]/[CommodityWidgetRefreshWorker], since the
 * in-app tile has none either. Periodic cadence is a flat 30 min
 * (`updatePeriodMillis` in `sports_widget_info.xml`, same value here) —
 * WorkManager's own periodic floor is 15 min either way, far coarser than
 * the in-app tile's 90s `LaunchedEffect` poll, so `refreshNow()` (called
 * right after the configure activity saves a pick) is what gives the first
 * paint its real data instead of waiting on the periodic tick.
 */
class SportsWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        pushAll(applicationContext, force = inputData.getBoolean(KEY_FORCE, false))
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC = "tileshell_sports_widget_refresh"
        private const val UNIQUE_NOW = "tileshell_sports_widget_refresh_now"

        /**
         * Set on the one-off requests that must always fetch — a fresh pick,
         * a resize needing a different layout, placement. Only the *periodic*
         * tick is allowed to skip via [shouldFetchSports].
         */
        private const val KEY_FORCE = "force"

        fun ensureScheduled(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SportsWidgetRefreshWorker>(30, TimeUnit.MINUTES)
                    .setConstraints(WidgetWork.networkConstraints())
                    .build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_PERIODIC)
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SportsWidgetRefreshWorker>()
                    .setInputData(workDataOf(KEY_FORCE to true))
                    .build(),
            )
        }

        /**
         * [force] bypasses the "is anything actually live" check — used by
         * every explicit trigger (placement, resize, a newly saved pick). A
         * plain periodic tick leaves it false, which is what lets a widget
         * showing a finished match stop hitting the network entirely until
         * there is a reason to look again (see [shouldFetchSports]).
         */
        suspend fun pushAll(context: Context, force: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SportsAppWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val now = System.currentTimeMillis()
            ids.forEach { id ->
                if (!force) {
                    val last = WidgetSportsStateStore.snapshot(context, id)
                    val due = shouldFetchSports(last.state, last.kickoffMillis, last.fetchedAtMillis, now)
                    // Nothing to learn: the last-rendered match is over or has
                    // not started, and it's not yet time for the slow re-check.
                    // Leave the widget showing exactly what it already shows.
                    if (!due) return@forEach
                }
                val selection = WidgetConfigStore.sportsSelectionEncoded(context, id)?.let { SportsTile.decode(it) }
                val minWidthDp = manager.getAppWidgetOptions(id)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                val (accent, onAccent) = resolveWidgetAccent(context, id)
                val views = buildRemoteViews(context, id, selection, accent, onAccent, isCompactWidget(minWidthDp))
                manager.updateAppWidget(id, views)
            }
        }

        private suspend fun buildRemoteViews(
            context: Context,
            appWidgetId: Int,
            selection: SportsTile.Selection?,
            accent: Int,
            onAccent: Int,
            compact: Boolean,
        ): RemoteViews {
            val layout = if (compact) R.layout.widget_sports_compact else R.layout.widget_sports
            val views = RemoteViews(context.packageName, layout)
            views.setImageViewBitmap(R.id.widget_bg, accentGradientBitmap(accent))
            // Rounds the widget's own corners (see widget_rounded_background.xml) —
            // the background drawable provides an Outline; clipToOutline clips
            // widget_bg's full-bleed gradient (and everything else) to it.
            views.setBoolean(R.id.widget_root, "setClipToOutline", true)
            views.setOnClickPendingIntent(R.id.widget_settings, reconfigurePendingIntent(context, appWidgetId))
            setBaseColors(views, onAccent, compact)
            views.setImageViewResource(R.id.widget_icon, sportsIconRes(selection?.leagueSlug))

            if (selection == null) {
                views.setOnClickPendingIntent(R.id.widget_root, reconfigurePendingIntent(context, appWidgetId))
                views.setTextViewText(R.id.widget_team_abbr, "no team picked")
                views.setTextViewText(R.id.widget_team_score, "tap the gear to choose one")
                views.setTextViewText(R.id.widget_status, "")
                views.setTextViewText(R.id.widget_back_team, "no team picked")
                views.setTextViewText(R.id.widget_back_league, "")
                if (!compact) {
                    views.setTextViewText(R.id.widget_opp_abbr, "")
                    views.setTextViewText(R.id.widget_opp_score, "")
                }
                return views
            }

            val leagueName = sportsLeagueFor(selection.leagueSlug)?.displayName ?: selection.leagueSlug
            views.setTextViewText(R.id.widget_back_team, selection.teamLabel)
            views.setTextViewText(R.id.widget_back_league, leagueName)

            val relevant = if (selection.leagueSlug == CRICKET_LEAGUE_SLUG) {
                fetchRecentCricketMatchForTeam(selection.teamId, System.currentTimeMillis())
            } else {
                pickRelevantMatch(fetchSportsSchedule(selection.leagueSlug, selection.teamId), System.currentTimeMillis())
            }

            // Remember what we just learned so the next periodic tick can decide
            // whether it needs to fetch at all — a finished or not-yet-started
            // match means there is nothing to poll for (see shouldFetchSports).
            WidgetSportsStateStore.record(
                context = context,
                appWidgetId = appWidgetId,
                state = relevant?.state,
                kickoffMillis = relevant?.epochMillis,
                fetchedAtMillis = System.currentTimeMillis(),
            )

            if (relevant == null) {
                views.setOnClickPendingIntent(R.id.widget_root, sportsAppPendingIntent(context, appWidgetId, null, selection.teamLabel))
                views.setTextViewText(R.id.widget_team_abbr, selection.teamLabel)
                views.setTextViewText(R.id.widget_team_score, "no matches found")
                views.setTextViewText(R.id.widget_status, "")
                if (!compact) {
                    views.setTextViewText(R.id.widget_opp_abbr, "")
                    views.setTextViewText(R.id.widget_opp_score, "")
                }
                hideLines(views)
                return views
            }

            var snapshot = snapshotFor(relevant, selection.teamId)
            var webUrl: String? = null
            var lines: List<String> = emptyList()

            if (selection.leagueSlug == CRICKET_LEAGUE_SLUG) {
                val leagueId = relevant.leagueId
                if (leagueId != null) {
                    val detail = fetchCricketMatchDetail(leagueId, relevant.id)
                    webUrl = detail.webUrl
                    lines = (detail.battingLines + detail.bowlingLines).take(4)
                }
            } else {
                val detail = fetchMatchDetail(selection.leagueSlug, relevant.id)
                webUrl = detail.webUrl
                if (selection.leagueSlug.startsWith("soccer/")) {
                    lines = detail.contributorLines.take(4)
                    snapshot = snapshot.copy(
                        teamScore = (if (snapshot.isHome) detail.homeScore else detail.awayScore) ?: snapshot.teamScore,
                        opponentScore = (if (snapshot.isHome) detail.awayScore else detail.homeScore) ?: snapshot.opponentScore,
                    )
                }
            }

            views.setOnClickPendingIntent(R.id.widget_root, sportsAppPendingIntent(context, appWidgetId, webUrl, selection.teamLabel))

            if (compact) {
                // Not enough width here for a full multi-innings cricket score —
                // just the most recent innings, same trade-off the front face's
                // narrow single-column layout already makes in-app.
                val teamScore = splitInningsScore(snapshot.teamScore).last()
                val oppScore = splitInningsScore(snapshot.opponentScore).last()
                views.setTextViewText(R.id.widget_team_abbr, "${snapshot.teamAbbr} vs ${snapshot.opponentAbbr}")
                views.setTextViewText(R.id.widget_team_score, "$teamScore – $oppScore")
            } else {
                views.setTextViewText(R.id.widget_team_abbr, snapshot.teamAbbr)
                views.setTextViewText(R.id.widget_opp_abbr, snapshot.opponentAbbr)
                setScoreLines(views, R.id.widget_team_score, R.id.widget_team_score_2, snapshot.teamScore, onAccent)
                setScoreLines(views, R.id.widget_opp_score, R.id.widget_opp_score_2, snapshot.opponentScore, onAccent)
            }
            views.setTextViewText(R.id.widget_status, snapshot.statusDetail.ifBlank { sportsStateLabel(snapshot.state) })

            if (!compact) {
                MEMBER_LINE_IDS.forEachIndexed { index, id ->
                    val text = lines.getOrNull(index)
                    if (text == null) {
                        views.setViewVisibility(id, View.GONE)
                    } else {
                        views.setViewVisibility(id, View.VISIBLE)
                        views.setTextViewText(id, text)
                        views.setTextColor(id, onAccent)
                    }
                }
            }
            return views
        }

        private fun hideLines(views: RemoteViews) {
            MEMBER_LINE_IDS.forEach { views.setViewVisibility(it, View.GONE) }
        }

        /** Resolves [sportsIconKeyFor]'s pure key to a real drawable — user-requested per-game icon. */
        private fun sportsIconRes(leagueSlug: String?): Int = when (sportsIconKeyFor(leagueSlug)) {
            "cricket" -> R.drawable.ic_widget_sport_cricket
            "soccer" -> R.drawable.ic_widget_sport_soccer
            "basketball" -> R.drawable.ic_widget_sport_basketball
            "football" -> R.drawable.ic_widget_sport_football
            "baseball" -> R.drawable.ic_widget_sport_baseball
            "hockey" -> R.drawable.ic_widget_sport_hockey
            else -> R.drawable.ic_widget_sports
        }

        /**
         * Splits [score] the same way [com.tileshell.feature.livetiles.SportsTile]'s
         * own `TeamScoreColumn` does (up to the last 2 cricket innings — a rain-
         * shortened Test could carry a third, but there's no room for it and the
         * two most recent are what's relevant) across [lineId]/[line2Id]; every
         * other sport's plain single-value score just fills [lineId] and leaves
         * [line2Id] hidden.
         */
        private fun setScoreLines(views: RemoteViews, lineId: Int, line2Id: Int, score: String, onAccent: Int) {
            val innings = splitInningsScore(score).takeLast(2)
            views.setTextViewText(lineId, innings.first())
            views.setTextColor(lineId, onAccent)
            if (innings.size > 1) {
                views.setViewVisibility(line2Id, View.VISIBLE)
                views.setTextViewText(line2Id, innings[1])
                views.setTextColor(line2Id, onAccent)
            } else {
                views.setViewVisibility(line2Id, View.GONE)
            }
        }

        private fun setBaseColors(views: RemoteViews, onAccent: Int, compact: Boolean) {
            views.setTextColor(R.id.widget_team_abbr, onAccent)
            views.setTextColor(R.id.widget_team_score, onAccent)
            views.setTextColor(R.id.widget_status, onAccent)
            views.setTextColor(R.id.widget_back_team, onAccent)
            views.setTextColor(R.id.widget_back_league, onAccent)
            views.setInt(R.id.widget_icon, "setColorFilter", onAccent)
            if (!compact) {
                views.setTextColor(R.id.widget_opp_abbr, onAccent)
                views.setTextColor(R.id.widget_opp_score, onAccent)
            }
        }
    }
}
