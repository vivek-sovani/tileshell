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

class WhatsNewFilterTest {

    private fun preview(sender: String, postTime: Long, count: Int = 1, key: String = sender) =
        ConversationPreview(sender, "hi", count, listOf(ConversationItem(sender, "hi", key, postTime)))

    private val snapshot = NotificationSnapshot(
        badges = mapOf(
            "com.whatsapp" to 2,
            "com.google.android.gm" to 7,
            "com.google.android.apps.messaging" to 1,
            "com.instagram.android" to 1,
            "com.google.android.dialer" to 1,
            "com.android.vending" to 3,
        ),
        conversations = mapOf(
            "com.whatsapp" to preview("priya", 5000),
            "com.google.android.gm" to preview("anand", 4000),
            "com.google.android.apps.messaging" to preview("airtel", 3000),
            "com.instagram.android" to preview("insta", 2000),
            "com.google.android.dialer" to preview("missed", 1000),
            "com.android.vending" to preview("promo", 9000),
        ),
    )

    @Test
    fun `mail is included under all, and every row carries its category`() {
        val entries = recentActivity(snapshot)
        assertEquals(listOf("priya", "anand", "airtel", "insta", "missed"), entries.map { it.sender })
        assertEquals(
            listOf(PeopleCategory.CHAT, PeopleCategory.MAIL, PeopleCategory.MESSAGES, PeopleCategory.SOCIAL, PeopleCategory.CALLS),
            entries.map { it.category },
        )
    }

    @Test
    fun `a category filter keeps only that category`() {
        assertEquals(listOf("anand"), recentActivity(snapshot, category = PeopleCategory.MAIL).map { it.sender })
        assertEquals(listOf("insta"), recentActivity(snapshot, category = PeopleCategory.SOCIAL).map { it.sender })
    }

    @Test
    fun `an app filter keeps only that app`() {
        assertEquals(listOf("anand"), recentActivity(snapshot, packageName = "com.google.android.gm").map { it.sender })
    }

    @Test
    fun `app chips list only people apps with something pending, most first`() {
        assertEquals(
            listOf(
                "com.google.android.gm" to 7,
                "com.whatsapp" to 2,
                "com.google.android.apps.messaging" to 1,
                "com.instagram.android" to 1,
                "com.google.android.dialer" to 1,
            ),
            whatsNewApps(snapshot),
        )
    }

    @Test
    fun `ties are broken by the newest notification`() {
        val tied = NotificationSnapshot(
            badges = mapOf("com.whatsapp" to 1, "com.instagram.android" to 1, "com.twitter.android" to 0),
            conversations = mapOf(
                "com.whatsapp" to preview("old", 1000),
                "com.instagram.android" to preview("new", 9000),
            ),
        )
        assertEquals(listOf("com.instagram.android", "com.whatsapp"), whatsNewApps(tied).map { it.first })
    }

    @Test
    fun `hidden count reports notifications beyond the rows shown`() {
        assertEquals(6, hiddenActivityCount(snapshot, "com.google.android.gm"))
        assertEquals(1, hiddenActivityCount(snapshot, "com.whatsapp"))
        assertEquals(0, hiddenActivityCount(snapshot, "com.google.android.apps.messaging"))
        assertEquals(0, hiddenActivityCount(snapshot, "com.unknown"))
    }
}

class PeopleAppsTest {

    private val installed = mapOf(
        "com.whatsapp" to "WhatsApp",
        "org.telegram.messenger" to "Telegram",
        "com.google.android.gm" to "Gmail",
        "com.instagram.android" to "Instagram",
        "com.google.android.dialer" to "Phone",
        "com.android.chrome" to "Chrome",
    )

    @Test
    fun `sorted by badge then label, calling and non-people apps dropped`() {
        val apps = peopleApps(installed, mapOf("com.google.android.gm" to 4, "com.whatsapp" to 5))
        assertEquals(
            listOf("com.whatsapp", "com.google.android.gm", "com.instagram.android", "org.telegram.messenger"),
            apps.map { it.packageName },
        )
        assertEquals(listOf(5, 4, 0, 0), apps.map { it.badge })
    }

    @Test
    fun `most opened comes first, ahead of notification count`() {
        val apps = peopleApps(
            installed,
            badges = mapOf("com.whatsapp" to 5),
            opens = mapOf("org.telegram.messenger" to 40, "com.google.android.gm" to 12, "com.whatsapp" to 12),
        )
        assertEquals(
            listOf("org.telegram.messenger", "com.whatsapp", "com.google.android.gm", "com.instagram.android"),
            apps.map { it.packageName },
        )
    }

    @Test
    fun `grouped in chat, messages, mail, social order with empty groups dropped`() {
        val groups = groupPeopleApps(peopleApps(installed, emptyMap()))
        assertEquals(listOf(PeopleCategory.CHAT, PeopleCategory.MAIL, PeopleCategory.SOCIAL), groups.map { it.first })
        assertEquals(listOf("Telegram", "WhatsApp"), groups.first().second.map { it.label })
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
