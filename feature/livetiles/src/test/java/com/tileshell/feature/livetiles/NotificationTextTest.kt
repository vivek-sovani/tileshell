package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTextTest {
    @Test fun bigTextBeatsTheOneLinePreview() =
        assertEquals("the whole long message", fullNotificationText("the whole…", "the whole long message", emptyList(), emptyList()))

    @Test fun chatShowsItsRecentMessagesOldestFirst() =
        assertEquals("a\nb\nc", fullNotificationText("c", null, listOf("a", "b", "c"), emptyList()))

    @Test fun chatKeepsOnlyTheNewestFive() {
        val msgs = (1..8).map { "m$it" }
        assertEquals("m4\nm5\nm6\nm7\nm8", fullNotificationText("m8", null, msgs, emptyList()))
    }

    @Test fun inboxLinesAreJoined() =
        assertEquals("one\ntwo", fullNotificationText("2 new", null, emptyList(), listOf("one", "two")))

    @Test fun nothingExtraIsNull() {
        assertNull(fullNotificationText("hi", null, emptyList(), emptyList()))
        assertNull(fullNotificationText("hi", "hi", emptyList(), emptyList()))
        assertNull(fullNotificationText(null, null, emptyList(), emptyList()))
    }

    @Test fun neverShorterThanThePreview() =
        assertNull(fullNotificationText("a longer preview text", "short", emptyList(), emptyList()))

    @Test fun capsVeryLongText() {
        val out = fullNotificationText("x", "y".repeat(5000), emptyList(), emptyList())!!
        assertEquals(MAX_FULL_NOTIFICATION_TEXT, out.length)
        assertTrue(out.endsWith("…"))
    }

    @Test fun blankPartsAreIgnored() =
        assertEquals("real", fullNotificationText("r", "   ", listOf("", "real"), emptyList()))
}

class MessageSenderLabelTest {
    @Test fun groupChatNamesThePersonAndTheGroup() =
        assertEquals("Asha · Family", messageSenderLabel("Asha", "Family", true, "Family"))

    @Test fun oneToOneChatUsesJustThePerson() =
        assertEquals("Asha", messageSenderLabel("Asha", "Asha", false, "Asha"))

    @Test fun personWhoIsTheGroupNameIsNotRepeated() =
        assertEquals("family", messageSenderLabel("family", "Family", true, "x"))

    @Test fun missingPersonFallsBackToTheConversationThenTheTitle() {
        assertEquals("Family", messageSenderLabel(null, "Family", true, "t"))
        assertEquals("title", messageSenderLabel(" ", null, false, " title "))
    }
}

class PhotoSizedTest {
    @Test fun smallLargeIconsAreAvatarsAndBigOnesArePhotos() {
        assertEquals(false, isPhotoSized(128, 128))
        assertEquals(false, isPhotoSized(256, 300))
        assertEquals(true, isPhotoSized(400, 200))
        assertEquals(true, isPhotoSized(300, 720))
    }
}
