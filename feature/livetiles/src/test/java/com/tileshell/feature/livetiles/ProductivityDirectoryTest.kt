package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

class MeetingLinkTest {

    @Test
    fun `finds meet, zoom, teams and webex links`() {
        assertEquals(MeetingLink("https://meet.google.com/abc-defg-hij", "google meet"), meetingLinkFrom("Join: https://meet.google.com/abc-defg-hij"))
        assertEquals("zoom", meetingLinkFrom(null, "Zoom https://us02web.zoom.us/j/8123456789?pwd=xyz.")?.provider)
        assertEquals("https://us02web.zoom.us/j/8123456789?pwd=xyz", meetingLinkFrom("https://us02web.zoom.us/j/8123456789?pwd=xyz.")?.url)
        assertEquals("teams", meetingLinkFrom("<https://teams.microsoft.com/l/meetup-join/19%3ameeting_x>")?.provider)
        assertEquals("webex", meetingLinkFrom("https://acme.webex.com/meet/pr123")?.provider)
    }

    @Test
    fun `skips non-meeting links and look-alike hosts`() {
        assertEquals(null, meetingLinkFrom("Office, https://maps.google.com/?q=pune", "agenda https://docs.google.com/d/1"))
        assertEquals(null, meetingLinkFrom("https://notzoom.us.evil.com/j/1"))
        assertEquals(null, meetingLinkFrom("", null))
    }

    @Test
    fun `location wins over description`() {
        assertEquals("zoom", meetingLinkFrom("https://zoom.us/j/1", "https://meet.google.com/x")?.provider)
    }
}

class ProductivityAppsTest {

    private val installed = mapOf(
        "com.microsoft.office.word" to "Word",
        "com.google.android.keep" to "Keep",
        "us.zoom.videomeetings" to "Zoom",
        "com.oem.calc" to "Calculator",
        "com.whatsapp" to "WhatsApp",
    )

    @Test
    fun `known and role-resolved apps are grouped, others dropped`() {
        val groups = groupProductivityApps(productivityApps(installed, tools = setOf("com.oem.calc")))
        assertEquals(
            listOf(ProductivityCategory.OFFICE, ProductivityCategory.NOTES_FILES, ProductivityCategory.MEETINGS, ProductivityCategory.TOOLS),
            groups.map { it.first },
        )
        assertEquals(listOf("com.oem.calc"), groups.last().second.map { it.packageName })
    }

    @Test
    fun `most opened first, then name`() {
        val apps = productivityApps(installed, opens = mapOf("us.zoom.videomeetings" to 9, "com.google.android.keep" to 2))
        assertEquals(listOf("Zoom", "Keep", "Word"), apps.map { it.label })
    }
}
