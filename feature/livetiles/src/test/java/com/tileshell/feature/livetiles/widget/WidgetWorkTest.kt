package com.tileshell.feature.livetiles.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The screen-off gate on the frequent widget pollers. The midnight-aligned daily
 * workers (calendar system, moon phase, countdown) deliberately don't consult
 * this — see [WidgetWork.shouldSkipWidgetRefresh]'s own doc.
 */
class WidgetWorkTest {

    @Test
    fun `a periodic tick with the screen off is skipped`() {
        // The case this exists for: Battery and Steps waking every 15 minutes all
        // night to re-render a widget nobody can see.
        assertTrue(WidgetWork.shouldSkipWidgetRefresh(force = false, screenInteractive = false))
    }

    @Test
    fun `a periodic tick with the screen on still runs`() {
        assertFalse(WidgetWork.shouldSkipWidgetRefresh(force = false, screenInteractive = true))
    }

    @Test
    fun `a forced refresh runs even with the screen off`() {
        // refreshNow() one-offs: a provider event (plug/unplug, alarm change), a
        // config change, or the user's own manual refresh. None may be skipped —
        // several of them fire precisely while the screen is off.
        assertFalse(WidgetWork.shouldSkipWidgetRefresh(force = true, screenInteractive = false))
        assertFalse(WidgetWork.shouldSkipWidgetRefresh(force = true, screenInteractive = true))
    }
}
