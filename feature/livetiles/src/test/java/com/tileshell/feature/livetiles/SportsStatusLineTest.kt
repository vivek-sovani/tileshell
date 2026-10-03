package com.tileshell.feature.livetiles

import com.tileshell.core.data.SportsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SportsStatusLineTest {
    private val zone = ZoneOffset.UTC
    private fun at(date: String) = LocalDate.parse(date).atTime(10, 0).toInstant(zone).toEpochMilli()
    private fun snap(state: String, detail: String, label: String?, date: String) = SportsSnapshot(
        isHome = true, teamAbbr = "IND", teamName = "India", teamScore = "211/6",
        opponentAbbr = "PAK", opponentName = "Pakistan", opponentScore = "192/6",
        state = state, statusDetail = detail, matchLabel = label, epochMillis = at(date),
    )

    @Test
    fun `the day first, then status and which match`() {
        val now = at("2026-10-03") + 3_600_000
        assertEquals("today · Final · Final · Asian Games", sportsStatusLine(snap("post", "Final", "Final · Asian Games", "2026-10-03"), now, zone))
        assertEquals("today · Live · 3rd ODI · West Indies tour of India", sportsStatusLine(snap("in", "Live", "3rd ODI · West Indies tour of India", "2026-10-03"), now, zone))
        assertEquals("yesterday · Result · 2nd ODI", sportsStatusLine(snap("post", "Result", "2nd ODI", "2026-10-02"), now, zone))
        assertEquals("29 sep · Result · 1st ODI", sportsStatusLine(snap("post", "Result", "1st ODI", "2026-09-29"), now, zone))
        assertEquals("tomorrow · Sat 2:00 PM · 4th ODI", sportsStatusLine(snap("pre", "Sat 2:00 PM", "4th ODI", "2026-10-04"), now, zone))
        assertEquals("today · final", sportsStatusLine(snap("post", "", null, "2026-10-03"), now, zone))
    }
}
