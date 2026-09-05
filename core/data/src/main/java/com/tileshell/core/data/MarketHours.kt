package com.tileshell.core.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When the exchange behind a given Yahoo Finance symbol is actually open.
 *
 * Replaces the previous "9am-4pm in the *device's* timezone, for everything"
 * approximation. That was wrong in both directions and cost real battery: an
 * Indian user watching a US stock polled hardest at 9am IST (7pm ET, market
 * long closed) and went quiet at 7pm IST (9:30am ET, the opening bell). It
 * also throttled instruments that never close — a currency pair or a crypto
 * pair does not observe NYSE hours.
 *
 * The exchange is derived from the symbol's own suffix, which Yahoo already
 * encodes and the app already stores — `RELIANCE.NS` is NSE/Mumbai,
 * `VOD.L` is London, a bare `AAPL` is US. No network lookup, no per-symbol
 * configuration, and pure, so it is unit-testable against a fixed clock.
 *
 * Session times are the regular cash session and deliberately ignore
 * pre/post-market, lunch breaks (Tokyo), half-days and public holidays: this
 * only ever decides *how often to poll*, never what to display, so being a
 * few minutes or one holiday out costs a little extra polling and nothing
 * else. The old doc comment used that same reasoning to justify not doing
 * this at all; the difference is that a suffix lookup is essentially free,
 * whereas a real holiday calendar would not be.
 */
data class MarketSession(
    val zone: ZoneId,
    /** Inclusive minute-of-day the regular session opens. */
    val openMinuteOfDay: Int,
    /** Exclusive minute-of-day the regular session closes. */
    val closeMinuteOfDay: Int,
    /** Days the exchange trades — Mon-Fri nearly everywhere, Sun-Thu in Tel Aviv. */
    val tradingDays: Set<DayOfWeek> = MON_TO_FRI,
) {
    /** True for an instrument that never closes (crypto), which skips gating entirely. */
    val alwaysOpen: Boolean
        get() = tradingDays.size == 7 && openMinuteOfDay == 0 && closeMinuteOfDay >= MINUTES_PER_DAY

    companion object {
        val MON_TO_FRI: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
        val SUN_TO_THU: Set<DayOfWeek> = setOf(
            DayOfWeek.SUNDAY,
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
        )
    }
}

const val MINUTES_PER_DAY = 24 * 60

private fun session(zone: String, openHour: Int, openMinute: Int, closeHour: Int, closeMinute: Int) =
    MarketSession(ZoneId.of(zone), openHour * 60 + openMinute, closeHour * 60 + closeMinute)

/** Regular cash session per Yahoo exchange suffix (lowercase, without the dot). */
private val SESSIONS: Map<String, MarketSession> = mapOf(
    // India — NSE and BSE both 09:15-15:30 IST.
    "ns" to session("Asia/Kolkata", 9, 15, 15, 30),
    "bo" to session("Asia/Kolkata", 9, 15, 15, 30),
    // United Kingdom / Ireland.
    "l" to session("Europe/London", 8, 0, 16, 30),
    "ir" to session("Europe/Dublin", 8, 0, 16, 30),
    // Continental Europe (Xetra and the Euronext venues share 09:00-17:30 local).
    "de" to session("Europe/Berlin", 9, 0, 17, 30),
    "f" to session("Europe/Berlin", 9, 0, 17, 30),
    "sg" to session("Europe/Berlin", 9, 0, 17, 30),
    "mu" to session("Europe/Berlin", 9, 0, 17, 30),
    "pa" to session("Europe/Paris", 9, 0, 17, 30),
    "as" to session("Europe/Amsterdam", 9, 0, 17, 30),
    "br" to session("Europe/Brussels", 9, 0, 17, 30),
    "mi" to session("Europe/Rome", 9, 0, 17, 30),
    "mc" to session("Europe/Madrid", 9, 0, 17, 30),
    "st" to session("Europe/Stockholm", 9, 0, 17, 30),
    "ol" to session("Europe/Oslo", 9, 0, 16, 20),
    "sw" to session("Europe/Zurich", 9, 0, 17, 30),
    // Asia-Pacific.
    "t" to session("Asia/Tokyo", 9, 0, 15, 0),
    "hk" to session("Asia/Hong_Kong", 9, 30, 16, 0),
    "ss" to session("Asia/Shanghai", 9, 30, 15, 0),
    "sz" to session("Asia/Shanghai", 9, 30, 15, 0),
    "ks" to session("Asia/Seoul", 9, 0, 15, 30),
    "kq" to session("Asia/Seoul", 9, 0, 15, 30),
    "tw" to session("Asia/Taipei", 9, 0, 13, 30),
    "si" to session("Asia/Singapore", 9, 0, 17, 0),
    "ax" to session("Australia/Sydney", 10, 0, 16, 0),
    "nz" to session("Pacific/Auckland", 10, 0, 16, 45),
    // Americas.
    "to" to session("America/Toronto", 9, 30, 16, 0),
    "v" to session("America/Toronto", 9, 30, 16, 0),
    "sa" to session("America/Sao_Paulo", 10, 0, 17, 0),
    "mx" to session("America/Mexico_City", 8, 30, 15, 0),
    // Africa / Middle East. Tel Aviv trades Sunday-Thursday.
    "jo" to session("Africa/Johannesburg", 9, 0, 17, 0),
    "ta" to session("Asia/Jerusalem", 9, 45, 17, 15)
        .copy(tradingDays = MarketSession.SUN_TO_THU),
)

/** US listings carry no suffix — the default for a bare ticker like `AAPL`. */
private val US_SESSION = session("America/New_York", 9, 30, 16, 0)

/**
 * Futures and FX (`GC=F`, `EURUSD=X`) trade essentially around the clock on
 * weekdays rather than in a cash session, so they get a full-day weekday
 * window instead of 9-to-4. Gating these to an equities session was simply
 * incorrect — a currency pair moves all night.
 */
private val WEEKDAY_CONTINUOUS = MarketSession(ZoneId.of("America/New_York"), 0, MINUTES_PER_DAY)

/** Crypto (`BTC-USD`) never closes. */
private val ALWAYS_OPEN = MarketSession(
    ZoneId.of("UTC"),
    0,
    MINUTES_PER_DAY,
    tradingDays = DayOfWeek.entries.toSet(),
)

/** The trading session for [symbol], resolved from its Yahoo exchange suffix. */
fun marketSessionFor(symbol: String): MarketSession {
    val s = symbol.trim()
    if (s.isEmpty()) return US_SESSION
    if (s.endsWith("=F", ignoreCase = true) || s.endsWith("=X", ignoreCase = true)) return WEEKDAY_CONTINUOUS
    // Yahoo writes crypto as BASE-QUOTE (BTC-USD, ETH-EUR).
    if (s.contains('-') && !s.contains('.')) return ALWAYS_OPEN
    val suffix = s.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    if (suffix.isEmpty()) return US_SESSION
    return SESSIONS[suffix] ?: US_SESSION
}

/** Whether [symbol]'s exchange is inside its regular session at [nowMillis]. */
fun isMarketOpenFor(symbol: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
    val session = marketSessionFor(symbol)
    if (session.alwaysOpen) return true
    val local = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), session.zone)
    if (local.dayOfWeek !in session.tradingDays) return false
    val minuteOfDay = local.hour * 60 + local.minute
    return minuteOfDay >= session.openMinuteOfDay && minuteOfDay < session.closeMinuteOfDay
}

/**
 * Milliseconds from [nowMillis] until [symbol]'s exchange next opens, so a
 * caller can sleep exactly that long instead of waking repeatedly to
 * re-discover that the market is still shut. Returns 0 when it is already
 * open.
 *
 * Capped at [MAX_CLOSED_SLEEP_MS] so a long weekend still produces a few
 * refreshes — which keeps a post-close price adjustment visible, and means a
 * device whose clock or timezone changed while sleeping re-evaluates before
 * too long rather than staying parked on a now-wrong deadline.
 */
fun millisUntilMarketOpen(symbol: String, nowMillis: Long = System.currentTimeMillis()): Long {
    if (isMarketOpenFor(symbol, nowMillis)) return 0L
    val session = marketSessionFor(symbol)
    if (session.alwaysOpen) return 0L

    val local = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), session.zone)
    // Walk forward at most a week; some exchange somewhere is open every day
    // of that window, so this always terminates with an answer.
    var day = local
    repeat(8) { offset ->
        val candidate = day.toLocalDate()
        if (day.dayOfWeek in session.tradingDays) {
            val open = candidate
                .atStartOfDay(session.zone)
                .plusMinutes(session.openMinuteOfDay.toLong())
            val openMillis = open.toInstant().toEpochMilli()
            if (openMillis > nowMillis) {
                return (openMillis - nowMillis).coerceAtMost(MAX_CLOSED_SLEEP_MS)
            }
        }
        day = day.plusDays(1)
        if (offset >= 0) day = day.toLocalDate().atStartOfDay(session.zone)
    }
    return MAX_CLOSED_SLEEP_MS
}

/**
 * Upper bound on how long a closed-market sleep may last. Six hours turns a
 * ~62-hour weekend into about ten refreshes instead of the ~250 the old
 * 15-minute closed-market floor produced, while still bounding the damage
 * from a stale deadline.
 */
const val MAX_CLOSED_SLEEP_MS = 6L * 60 * 60 * 1000

/**
 * How long a live tile should wait before its next fetch: the user's chosen
 * [configuredMs] while [symbol]'s market is open, otherwise straight through
 * to the opening bell.
 */
fun nextMarketRefreshDelayMs(
    symbol: String,
    configuredMs: Long,
    nowMillis: Long = System.currentTimeMillis(),
): Long = if (isMarketOpenFor(symbol, nowMillis)) {
    configuredMs
} else {
    millisUntilMarketOpen(symbol, nowMillis).coerceAtLeast(configuredMs)
}
