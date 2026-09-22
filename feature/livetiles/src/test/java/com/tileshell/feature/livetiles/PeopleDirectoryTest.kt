package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsForTest {

    @Test
    fun `two-word name uses first letter of each word`() {
        assertEquals("AM", initialsFor("aarav mehta"))
        assertEquals("MP", initialsFor("Meera Patil"))
    }

    @Test
    fun `single-word name uses its first two letters`() {
        assertEquals("MA", initialsFor("madonna"))
    }

    @Test
    fun `blank name falls back to a placeholder`() {
        assertEquals("?", initialsFor(""))
        assertEquals("?", initialsFor("   "))
    }

    @Test
    fun `extra whitespace between words is ignored`() {
        assertEquals("AM", initialsFor("  aarav   mehta  "))
    }
}

class GroupContactsByLetterTest {

    private fun person(name: String) = PersonSummary(contactId = name.hashCode().toLong(), lookupKey = name, name = name, photoUri = null)

    @Test
    fun `groups by uppercase first letter, preserving input order within a section`() {
        val people = listOf(person("aarav mehta"), person("anita kulkarni"), person("meera patil"), person("mohan rao"))
        val sections = groupContactsByLetter(people)
        assertEquals(listOf("A", "M"), sections.map { it.first })
        assertEquals(listOf("aarav mehta", "anita kulkarni"), sections[0].second.map { it.name })
        assertEquals(listOf("meera patil", "mohan rao"), sections[1].second.map { it.name })
    }

    @Test
    fun `empty input yields no sections`() {
        assertEquals(emptyList<Pair<String, List<PersonSummary>>>(), groupContactsByLetter(emptyList()))
    }

    @Test
    fun `a name with no letters falls back to a hash section`() {
        val sections = groupContactsByLetter(listOf(person("123")))
        assertEquals("1", sections.single().first)
    }
}

class RecentActivityTest {

    private fun item(sender: String, postTime: Long, key: String = sender) =
        ConversationItem(sender = sender, snippet = "hi", notificationKey = key, postTime = postTime)

    @Test
    fun `flattens every package's items into one newest-first list`() {
        val snapshot = NotificationSnapshot(
            badges = emptyMap(),
            conversations = mapOf(
                "com.whatsapp" to ConversationPreview("a", "hi", 1, listOf(item("a", 3000))),
                "com.instagram.android" to ConversationPreview("b", "hi", 1, listOf(item("b", 5000))),
            ),
        )
        val entries = recentActivity(snapshot)
        assertEquals(listOf("b", "a"), entries.map { it.sender })
        assertEquals(listOf("com.instagram.android", "com.whatsapp"), entries.map { it.packageName })
    }

    @Test
    fun `caps at the requested limit after sorting`() {
        val snapshot = NotificationSnapshot(
            badges = emptyMap(),
            conversations = mapOf(
                "com.whatsapp" to ConversationPreview("a", "hi", 1, listOf(item("a", 100), item("a2", 50))),
            ),
        )
        assertEquals(listOf("a"), recentActivity(snapshot, limit = 1).map { it.sender })
    }

    @Test
    fun `non-messaging apps are excluded even when present in the snapshot`() {
        // User-reported: "whats new should be only related with contacts" —
        // a promo/content notification (Play Store, a news feed, a streaming
        // app) has nothing to do with a person and must not show up here.
        val snapshot = NotificationSnapshot(
            badges = emptyMap(),
            conversations = mapOf(
                "com.android.vending" to ConversationPreview("promo", "hi", 1, listOf(item("promo", 9000))),
                "com.whatsapp" to ConversationPreview("a", "hi", 1, listOf(item("a", 3000))),
            ),
        )
        assertEquals(listOf("a"), recentActivity(snapshot).map { it.sender })
    }

    @Test
    fun `empty snapshot yields no entries`() {
        assertEquals(emptyList<ActivityEntry>(), recentActivity(NotificationSnapshot.EMPTY))
    }
}

class ActivityAgoTest {

    @Test
    fun `under a minute reads as just now`() {
        assertEquals("just now", activityAgo(postTimeMillis = 1000L, nowMillis = 1000L + 30_000L))
    }

    @Test
    fun `minutes and hours format distinctly`() {
        assertEquals("2 min ago", activityAgo(1L, nowMillis = 1L + 2 * 60_000L))
        assertEquals("18 min ago", activityAgo(1L, nowMillis = 1L + 18 * 60_000L))
        assertEquals("1 hr ago", activityAgo(1L, nowMillis = 1L + 60 * 60_000L))
    }

    @Test
    fun `days pluralize correctly`() {
        assertEquals("1 day ago", activityAgo(1L, nowMillis = 1L + 24 * 60 * 60_000L))
        assertEquals("3 days ago", activityAgo(1L, nowMillis = 1L + 3 * 24 * 60 * 60_000L))
    }

    @Test
    fun `a non-positive post time yields a blank label`() {
        assertEquals("", activityAgo(0L, nowMillis = 5000L))
    }
}
