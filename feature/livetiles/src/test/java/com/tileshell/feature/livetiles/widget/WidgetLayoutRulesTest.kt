package com.tileshell.feature.livetiles.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutRulesTest {

    @Test
    fun `narrower than 180dp is compact`() {
        assertTrue(isCompactWidget(110))
        assertTrue(isCompactWidget(179))
    }

    @Test
    fun `180dp and wider is not compact`() {
        assertFalse(isCompactWidget(180))
        assertFalse(isCompactWidget(300))
    }

    @Test
    fun `light accent needs dark text`() {
        // Amber (#E2A200) is light enough to read as light in luminance terms.
        assertTrue(isLightAccent(0xFFE2A200.toInt()))
    }

    @Test
    fun `dark or mid accent needs white text`() {
        // Blue (#2B78E4), the default global accent.
        assertFalse(isLightAccent(0xFF2B78E4.toInt()))
        // Slate (#3A4554), the darkest accent.
        assertFalse(isLightAccent(0xFF3A4554.toInt()))
    }

    @Test
    fun `white and black are the extremes`() {
        assertTrue(isLightAccent(0xFFFFFFFF.toInt()))
        assertFalse(isLightAccent(0xFF000000.toInt()))
    }

    @Test
    fun `default-sized list widget shows 3 rows`() {
        assertEquals(3, listWidgetRowsForHeight(110))
    }

    @Test
    fun `a taller resize reveals more rows, up to a cap of 6`() {
        assertEquals(4, listWidgetRowsForHeight(160))
        assertEquals(5, listWidgetRowsForHeight(220))
        assertEquals(6, listWidgetRowsForHeight(400))
    }
}
