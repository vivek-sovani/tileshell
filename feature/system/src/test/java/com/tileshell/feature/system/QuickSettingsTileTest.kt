package com.tileshell.feature.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSettingsTileTest {
    @Test fun `first run offers the tile once where it can be asked`() {
        assertTrue(QuickSettingsTile.shouldOfferFirstRun(supported = true, asked = false, added = false))
    }

    @Test fun `never offered twice`() {
        assertFalse(QuickSettingsTile.shouldOfferFirstRun(supported = true, asked = true, added = false))
    }

    @Test fun `not offered when already on the panel`() {
        assertFalse(QuickSettingsTile.shouldOfferFirstRun(supported = true, asked = false, added = true))
    }

    @Test fun `not offered where the system cannot be asked`() {
        assertFalse(QuickSettingsTile.shouldOfferFirstRun(supported = false, asked = false, added = false))
    }
}
