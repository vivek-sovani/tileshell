package com.tileshell.feature.livetiles

import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun item(
    pkg: String,
    title: String? = null,
    text: String? = null,
    clearable: Boolean = true,
    summary: Boolean = false,
    postTime: Long = 0L,
) = NotificationItem(pkg, title, text, clearable, summary, postTime)

class NotificationSummaryTest {

    @Test
    fun `badge counts dismissable notifications per package`() {
        val snapshot = summarizeNotifications(
            listOf(
                item("mail", "Aria", "hi"),
                item("mail", "Ben", "yo"),
                item("messages", "Cy", "sup"),
            ),
        )
        assertEquals(2, snapshot.badgeFor("mail"))
        assertEquals(1, snapshot.badgeFor("messages"))
        assertEquals(0, snapshot.badgeFor("weather"))
    }

    @Test
    fun `ongoing and group-summary rows never count`() {
        val snapshot = summarizeNotifications(
            listOf(
                item("music", "Track", "Artist", clearable = false),
                item("mail", "Summary", "3 new", summary = true),
                item("mail", "Aria", "hi"),
            ),
        )
        assertEquals(0, snapshot.badgeFor("music"))
        // The summary row is dropped, leaving the one real mail notification.
        assertEquals(1, snapshot.badgeFor("mail"))
        assertNull(snapshot.conversationFor("music"))
    }

    @Test
    fun `conversation preview is the newest notification for the package`() {
        val snapshot = summarizeNotifications(
            listOf(
                item("mail", "Aria", "old", postTime = 100),
                item("mail", "Ben", "newest", postTime = 300),
                item("mail", "Cy", "mid", postTime = 200),
            ),
        )
        val preview = snapshot.conversationFor("mail")!!
        assertEquals("Ben", preview.sender)
        assertEquals("newest", preview.snippet)
        assertEquals(3, preview.count)
    }

    @Test
    fun `empty input yields the empty snapshot`() {
        assertTrue(summarizeNotifications(emptyList()).badges.isEmpty())
        assertTrue(summarizeNotifications(listOf(item("x", clearable = false))).conversations.isEmpty())
    }

    @Test
    fun `preview trims sender and snippet`() {
        val preview = summarizeNotifications(listOf(item("mail", "  Aria  ", "  hi  ")))
            .conversationFor("mail")!!
        assertEquals("Aria", preview.sender)
        assertEquals("hi", preview.snippet)
    }

    @Test
    fun `items are ordered newest first`() {
        val snapshot = summarizeNotifications(
            listOf(
                item("mail", "Aria", "old", postTime = 100),
                item("mail", "Ben", "newest", postTime = 300),
                item("mail", "Cy", "mid", postTime = 200),
            ),
        )
        val items = snapshot.conversationFor("mail")!!.items
        assertEquals(3, items.size)
        assertEquals("Ben", items[0].sender)
        assertEquals("Cy", items[1].sender)
        assertEquals("Aria", items[2].sender)
    }

    @Test
    fun `items are capped at MAX_CONVERSATION_ITEMS`() {
        val notifications = (1..10).map { i -> item("mail", "Sender$i", "msg$i", postTime = i.toLong()) }
        val items = summarizeNotifications(notifications).conversationFor("mail")!!.items
        assertEquals(MAX_CONVERSATION_ITEMS, items.size)
        assertEquals("Sender10", items[0].sender)
    }

    @Test
    fun `single notification produces one item matching sender and snippet`() {
        val preview = summarizeNotifications(listOf(item("mail", "Aria", "hello")))
            .conversationFor("mail")!!
        assertEquals(1, preview.items.size)
        assertEquals("Aria", preview.items[0].sender)
        assertEquals("hello", preview.items[0].snippet)
    }
}

class TileNotificationActionsTest {

    private fun row(
        pkg: String,
        key: String,
        clearable: Boolean = true,
        summary: Boolean = false,
        postTime: Long = 0L,
    ) = NotificationActionRow(pkg, key, contentIntent = null, clearable, summary, postTime)

    @Test
    fun `keys cover every dismissable notification for the package`() {
        val actions = tileNotificationActions(
            listOf(
                row("mail", "m1", postTime = 100),
                row("mail", "m2", postTime = 200),
                row("messages", "x1"),
            ),
        )
        assertEquals(listOf("m1", "m2"), actions.getValue("mail").keys)
        assertEquals(listOf("x1"), actions.getValue("messages").keys)
    }

    @Test
    fun `ongoing notifications produce no tap action`() {
        val actions = tileNotificationActions(listOf(row("music", "track", clearable = false)))
        assertNull(actions["music"])
        assertTrue(tileNotificationActions(emptyList()).isEmpty())
    }

    @Test
    fun `group summary keys are still cleared`() {
        val actions = tileNotificationActions(
            listOf(
                row("mail", "summary", summary = true, postTime = 300),
                row("mail", "real", postTime = 100),
            ),
        )
        // Both keys clear (so the whole group empties)...
        assertEquals(setOf("summary", "real"), actions.getValue("mail").keys.toSet())
    }
}

class InitialsTest {

    @Test
    fun `two words give two upper initials`() {
        assertEquals("AC", initials("Aria Cole"))
        assertEquals("BI", initials("ben ito"))
    }

    @Test
    fun `single word gives one initial`() {
        assertEquals("A", initials("Aria"))
    }

    @Test
    fun `extra whitespace uses first and last words`() {
        assertEquals("AZ", initials("  Aria  Mary  Zed "))
    }

    @Test
    fun `blank name falls back to a dot`() {
        assertEquals("·", initials("   "))
        assertEquals("·", initials(""))
    }
}

class MailMessagesFaceMappingTest {

    @Test
    fun `mail and messages icon keys map to flippable faces`() {
        assertEquals(LiveFace.MAIL, LiveFace.forIconKey("mail", TileSize.MEDIUM))
        assertEquals(LiveFace.MESSAGES, LiveFace.forIconKey("messages", TileSize.WIDE))
        assertTrue(LiveFace.MAIL.flips)
        assertTrue(LiveFace.MESSAGES.flips)
    }

    @Test
    fun `small tiles stay static even for mail and messages`() {
        assertNull(LiveFace.forIconKey("mail", TileSize.SMALL))
        assertNull(LiveFace.forIconKey("messages", TileSize.SMALL))
    }
}
