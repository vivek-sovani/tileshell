package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsFuturesSymbolTest {

    @Test
    fun `a futures contract symbol ends with the F suffix`() {
        assertTrue(isFuturesSymbol("GC=F"))
        assertTrue(isFuturesSymbol("CL=F"))
    }

    @Test
    fun `a currency pair is not a futures symbol`() {
        assertFalse(isFuturesSymbol("USDINR=X"))
        assertFalse(isFuturesSymbol("EURUSD=X"))
    }
}

class CommodityCatalogTest {

    @Test
    fun `every symbol is unique and non-blank`() {
        val symbols = COMMODITY_ITEMS.map { it.symbol }
        assertEquals(symbols.size, symbols.toSet().size)
        assertTrue(symbols.all { it.isNotBlank() })
    }

    @Test
    fun `every item's category is one of the declared display-order categories`() {
        COMMODITY_ITEMS.forEach {
            assertTrue("${it.symbol}'s category '${it.category}' isn't in the display order", it.category in COMMODITY_CATEGORY_ORDER)
        }
    }

    @Test
    fun `commodityItemFor finds a real item and returns null for an unknown symbol`() {
        assertEquals("Gold", commodityItemFor("GC=F")?.displayName)
        assertNull(commodityItemFor("no-such-symbol"))
    }
}

class CurrencyPairTest {

    @Test
    fun `builds Yahoo's own base-then-target ticker convention`() {
        assertEquals("USDINR=X", currencyPairTicker("USD", "INR"))
        assertEquals("INRUSD=X", currencyPairTicker("INR", "USD"))
    }

    @Test
    fun `label matches the wording COMMODITY_ITEMS' own presets already use`() {
        assertEquals("USD / INR", currencyPairLabel("USD", "INR"))
    }

    @Test
    fun `every currency code is unique and non-blank`() {
        val codes = CURRENCY_CODES.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it.isNotBlank() })
    }
}

class CommodityTileCodecTest {

    @Test
    fun `round-trips a symbol and display name`() {
        val encoded = CommodityTile.encode("GC=F", "Gold")
        assertEquals("GC=F" to "Gold", CommodityTile.decode(encoded))
    }

    @Test
    fun `decode rejects a string with no commodity prefix`() {
        assertNull(CommodityTile.decode("GC=F|Gold"))
    }

    @Test
    fun `decode rejects a not-yet-picked or malformed tile`() {
        assertNull(CommodityTile.decode("commodity:"))
        assertNull(CommodityTile.decode("commodity:|Gold"))
    }
}
