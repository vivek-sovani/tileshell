package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

class AppOpenCountsTest {

    @Test
    fun `consecutive activities of one app count as one open`() {
        val resumed = listOf("wa", "wa", "wa", "gm", "wa", "gm", "gm")
        assertEquals(mapOf("wa" to 2, "gm" to 2), countAppOpens(resumed))
    }

    @Test
    fun `empty history has no opens`() {
        assertEquals(emptyMap<String, Int>(), countAppOpens(emptyList()))
    }
}
