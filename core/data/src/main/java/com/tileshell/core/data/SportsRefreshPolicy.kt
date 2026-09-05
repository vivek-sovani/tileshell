package com.tileshell.core.data

/**
 * When a sports widget actually needs to hit the network again.
 *
 * The observation this encodes: **a match that isn't in progress has nothing
 * to poll for.** A finished match's scoreline is final, and a fixture that
 * hasn't started yet shows the same "vs / kick-off at" face no matter how
 * often it's re-fetched. Only a live match changes minute to minute. Before
 * this, every sports widget re-fetched on a flat 30-minute cadence forever —
 * for a followed team, that is roughly 48 rounds of network requests a day to
 * re-render an unchanged final score, and for cricket each of those rounds
 * could itself fan out to dozens of requests (see
 * [fetchRecentCricketMatchForTeam]'s lookback).
 *
 * Pure and side-effect free so the decision is unit-testable without a
 * network, a widget host, or a clock.
 *
 * @param lastState the [SportsMatchEvent.state] of the match last rendered —
 *   `"in"` (live), `"post"` (finished), anything else (scheduled/unknown), or
 *   null when nothing has been rendered yet.
 * @param lastKickoffMillis the scheduled start of the last rendered match, if
 *   known — used to wake up in time for a fixture that is about to begin.
 * @param lastFetchedAtMillis when the last real fetch happened, or null if
 *   there has never been one.
 */
fun shouldFetchSports(
    lastState: String?,
    lastKickoffMillis: Long?,
    lastFetchedAtMillis: Long?,
    nowMillis: Long,
): Boolean {
    // Never fetched, or a clock that has moved backwards (timezone change,
    // manual clock set, restored backup) — always fetch and re-establish.
    if (lastFetchedAtMillis == null || nowMillis < lastFetchedAtMillis) return true

    val sinceFetch = nowMillis - lastFetchedAtMillis

    return when (lastState) {
        // Live: the whole point of the widget. Refresh on every tick.
        SPORTS_STATE_LIVE -> true

        // Finished: the result is final. Keep checking, but only slowly —
        // enough to notice the team's *next* fixture appearing.
        SPORTS_STATE_FINAL -> sinceFetch >= SPORTS_IDLE_REFRESH_MS

        // Scheduled or unknown. If we know when it starts, stay quiet until
        // the run-up to kick-off, then resume normal cadence so the widget is
        // live the moment the match is. With no kick-off time, fall back to
        // the same slow idle cadence.
        else -> {
            if (lastKickoffMillis == null) return sinceFetch >= SPORTS_IDLE_REFRESH_MS
            val untilKickoff = lastKickoffMillis - nowMillis
            if (untilKickoff <= SPORTS_PREGAME_WAKE_MS) true
            else sinceFetch >= SPORTS_IDLE_REFRESH_MS
        }
    }
}

/** [SportsMatchEvent.state] for a match currently being played. */
const val SPORTS_STATE_LIVE = "in"

/** [SportsMatchEvent.state] for a match that has finished. */
const val SPORTS_STATE_FINAL = "post"

/**
 * How long to wait between fetches when nothing is live. Three hours is short
 * enough that a newly announced fixture shows up the same day and a match that
 * started unexpectedly early is picked up well within its first half, and long
 * enough to cut an idle team's daily request count by roughly an order of
 * magnitude versus the old flat 30-minute poll.
 */
const val SPORTS_IDLE_REFRESH_MS = 3L * 60 * 60 * 1000

/**
 * How far ahead of a known kick-off to start refreshing normally again, so the
 * widget is already showing the match when it begins rather than up to
 * [SPORTS_IDLE_REFRESH_MS] late.
 */
const val SPORTS_PREGAME_WAKE_MS = 30L * 60 * 1000
