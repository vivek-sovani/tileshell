package com.tileshell.feature.livetiles.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetIconRulesTest {

    @Test
    fun `each club league slug maps to its own sport icon`() {
        assertEquals("soccer", sportsIconKeyFor("soccer/eng.1"))
        assertEquals("soccer", sportsIconKeyFor("soccer/uefa.champions"))
        assertEquals("basketball", sportsIconKeyFor("basketball/nba"))
        assertEquals("football", sportsIconKeyFor("football/nfl"))
        assertEquals("baseball", sportsIconKeyFor("baseball/mlb"))
        assertEquals("hockey", sportsIconKeyFor("hockey/nhl"))
    }

    @Test
    fun `cricket's sentinel slug maps to the cricket icon`() {
        assertEquals("cricket", sportsIconKeyFor("cricket"))
    }

    @Test
    fun `no selection or an unrecognized slug falls back to the generic ball`() {
        assertEquals("generic", sportsIconKeyFor(null))
        assertEquals("generic", sportsIconKeyFor("rugby/six-nations"))
    }

    @Test
    fun `metal futures map to the metal icon`() {
        assertEquals("metal", commodityIconKeyFor("GC=F"))
        assertEquals("metal", commodityIconKeyFor("SI=F"))
        assertEquals("metal", commodityIconKeyFor("PL=F"))
        assertEquals("metal", commodityIconKeyFor("HG=F"))
    }

    @Test
    fun `energy futures map to the energy icon`() {
        assertEquals("energy", commodityIconKeyFor("CL=F"))
        assertEquals("energy", commodityIconKeyFor("BZ=F"))
        assertEquals("energy", commodityIconKeyFor("NG=F"))
    }

    @Test
    fun `any currency pair maps to the currency icon, including a custom one`() {
        assertEquals("currency", commodityIconKeyFor("USDINR=X"))
        assertEquals("currency", commodityIconKeyFor("EURJPY=X"))
    }

    @Test
    fun `an unrecognized symbol falls back to the generic coin`() {
        assertEquals("generic", commodityIconKeyFor("UNKNOWN"))
    }
}
