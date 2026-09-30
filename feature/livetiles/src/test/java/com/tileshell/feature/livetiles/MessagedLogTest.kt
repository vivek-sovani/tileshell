package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessagedLogTest {
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `sender names skip summaries and the user`() {
        assertEquals("Asha Rao", cleanSenderName("  Asha Rao "))
        assertNull(cleanSenderName(""))
        assertNull(cleanSenderName("You"))
        assertNull(cleanSenderName("3 new messages"))
        assertNull(cleanSenderName("12 messages from 4 chats"))
    }

    @Test
    fun `merge keeps the newest per person, newest first, within 30 days`() {
        val now = 100 * day
        val existing = listOf(
            MessagedEntry("Asha Rao", "com.whatsapp", now - 2 * day),
            MessagedEntry("Old Friend", "com.whatsapp", now - 40 * day),
        )
        val seen = listOf(
            MessagedEntry("asha  rao", "com.google.android.apps.messaging", now - 1 * day),
            MessagedEntry("Ravi", "com.whatsapp", now - 3 * day),
        )
        val merged = mergeMessaged(existing, seen, now)
        assertEquals(listOf("asha  rao", "Ravi"), merged.map { it.name })
        assertEquals("com.google.android.apps.messaging", merged.first().packageName)
    }

    @Test
    fun `codec round trips and rejects bad lines`() {
        val e = MessagedEntry("Asha Rao", "com.whatsapp", 1234L)
        assertEquals(e, decodeMessaged(encodeMessaged(e)))
        assertNull(decodeMessaged("garbage"))
        assertNull(decodeMessaged("x\tcom.whatsapp\tAsha"))
    }

    @Test
    fun `match keeps contacts only, one row each`() {
        val asha = PersonSummary(1, "k1", "Asha Rao", null)
        val ravi = PersonSummary(2, "k2", "Ravi", null)
        val entries = listOf(
            MessagedEntry("asha rao", "com.whatsapp", 30),
            MessagedEntry("Family group", "com.whatsapp", 20),
            MessagedEntry("Asha Rao", "sms", 10),
            MessagedEntry("RAVI", "sms", 5),
        )
        assertEquals(listOf(1L, 2L), matchMessaged(entries, listOf(asha, ravi)).map { it.first.contactId })
    }

    @Test
    fun `ago labels`() {
        val now = 10 * day
        assertEquals("just now", messagedAgo(now - 20_000, now))
        assertEquals("5m ago", messagedAgo(now - 5 * 60_000, now))
        assertEquals("3h ago", messagedAgo(now - 3 * 3_600_000, now))
        assertEquals("yesterday", messagedAgo(now - 30 * 3_600_000, now))
        assertEquals("4d ago", messagedAgo(now - 4 * day, now))
    }
}
