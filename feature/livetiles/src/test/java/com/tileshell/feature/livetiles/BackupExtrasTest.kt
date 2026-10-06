package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupExtrasTest {
    private fun rt(v: Any) = BackupExtras.decodePref(BackupExtras.encodePref(v)!!)

    @Test fun preferenceValuesRoundTrip() {
        assertEquals(true, rt(true))
        assertEquals(42, rt(42))
        assertEquals(5_000_000_000L, rt(5_000_000_000L))
        assertEquals(1.5f, rt(1.5f))
        assertEquals("a:b|c\nd", rt("a:b|c\nd"))
        assertEquals(setOf("x", "y z"), rt(setOf("x", "y z")))
        assertEquals(emptySet<String>(), rt(emptySet<String>()))
    }

    @Test fun unsupportedOrBrokenValuesAreNull() {
        assertNull(BackupExtras.encodePref(null))
        assertNull(BackupExtras.encodePref(listOf(1)))
        assertNull(BackupExtras.decodePref(""))
        assertNull(BackupExtras.decodePref("zzz"))
        assertNull(BackupExtras.decodePref("i:notanumber"))
        assertNull(BackupExtras.decodePref("b:maybe"))
        assertNull(BackupExtras.decodePref("S:[unclosed"))
    }

    @Test fun everyBackedUpKeyIsDistinct() =
        assertEquals(BackupExtras.PREF_KEYS.size, BackupExtras.PREF_KEYS.toSet().size)
}
