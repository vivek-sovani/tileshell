package com.tileshell.feature.livetiles

import com.tileshell.feature.livetiles.money.MoneyAppKind
import com.tileshell.feature.livetiles.money.moneyAppKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HubAppChoicesTest {
    @After fun reset() {
        HubKind.entries.forEach { HubAppChoices.setForTest(it, HubAppChoice()) }
    }

    private fun set(kind: HubKind, choice: HubAppChoice) = HubAppChoices.setForTest(kind, choice)

    // ---- the choice itself and its storage

    @Test fun placeAndDropAreMutuallyExclusive() {
        val c = HubAppChoice().place("a", "SOCIAL").drop("b").place("b", "CHAT")
        assertEquals(mapOf("a" to "SOCIAL", "b" to "CHAT"), c.placed)
        assertTrue(c.dropped.isEmpty())
        val d = c.drop("a")
        assertEquals(setOf("a"), d.dropped)
        assertNull(d.placed["a"])
        assertTrue(d.restoreDropped().dropped.isEmpty())
        assertTrue(HubAppChoice().isEmpty)
    }

    @Test fun codecRoundTripsAndIgnoresJunk() {
        val c = HubAppChoice(mapOf("com.pinterest" to "SOCIAL", "x.y" to "CHAT"), setOf("com.whatsapp"))
        assertEquals(c, HubAppChoiceCodec.decode(HubAppChoiceCodec.encode(c)))
        assertEquals(HubAppChoice(), HubAppChoiceCodec.decode(null))
        assertEquals(HubAppChoice(), HubAppChoiceCodec.decode("junk\np|onlytwo\nq|a|b\nd|\np||X"))
    }

    @Test fun aDropWinsOverAStalePlacementInAFile() {
        val c = HubAppChoiceCodec.decode("p|a|CHAT\nd|a")
        assertEquals(setOf("a"), c.dropped)
        assertTrue(c.placed.isEmpty())
    }

    // ---- People

    @Test fun addedAppJoinsPeopleAndItsNotificationsCount() {
        assertNull(peopleCategoryFor("com.pinterest"))
        set(HubKind.PEOPLE, HubAppChoice().place("com.pinterest", PeopleCategory.SOCIAL.name))
        assertEquals(PeopleCategory.SOCIAL, peopleCategoryFor("com.pinterest"))
        assertTrue("com.pinterest" in PEOPLE_APP_PACKAGES)
        val apps = peopleApps(mapOf("com.pinterest" to "Pinterest"), mapOf("com.pinterest" to 2))
        assertEquals(listOf(PeopleCategory.SOCIAL), apps.map { it.category })
        val snap = NotificationSnapshot(mapOf("com.pinterest" to 2), emptyMap())
        assertEquals(listOf("com.pinterest" to 2), whatsNewApps(snap))
    }

    @Test fun droppedAndMovedBuiltInPeopleApps() {
        set(HubKind.PEOPLE, HubAppChoice().drop("com.whatsapp").place("org.telegram.messenger", PeopleCategory.MAIL.name))
        assertNull(peopleCategoryFor("com.whatsapp"))
        assertTrue("com.whatsapp" !in PEOPLE_APP_PACKAGES)
        assertEquals(PeopleCategory.MAIL, peopleCategoryFor("org.telegram.messenger"))
        assertEquals(PeopleCategory.CHAT, builtInPeopleCategory("org.telegram.messenger"))
    }

    // ---- Money

    @Test fun addedMoneyAppUsesItsSection() {
        set(HubKind.MONEY, HubAppChoice().place("com.example.newpay", MoneyAppKind.PAYMENT.name))
        assertEquals(MoneyAppKind.PAYMENT, moneyAppKind("com.example.newpay", "Newpay"))
    }

    @Test fun droppedMoneyAppIsNoLongerOne() {
        val before = moneyAppKind("com.phonepe.app", "PhonePe")
        assertEquals(MoneyAppKind.PAYMENT, before)
        set(HubKind.MONEY, HubAppChoice().drop("com.phonepe.app"))
        assertNull(moneyAppKind("com.phonepe.app", "PhonePe"))
    }

    // ---- Productivity

    @Test fun productivityPlacementDropAndIndependenceFromPeople() {
        val gmail = "com.google.android.gm"
        assertEquals(ProductivityCategory.MAIL, productivityApps(mapOf(gmail to "Gmail")).single().category)
        // Dropping Gmail from People doesn't drop it from Productivity.
        set(HubKind.PEOPLE, HubAppChoice().drop(gmail))
        assertEquals(1, productivityApps(mapOf(gmail to "Gmail")).size)
        // An added app lands in its chosen section; a dropped one is gone.
        set(HubKind.PRODUCTIVITY, HubAppChoice().place("com.notion.id", ProductivityCategory.NOTES_FILES.name).drop(gmail))
        val apps = productivityApps(mapOf("com.notion.id" to "Notion", gmail to "Gmail"))
        assertEquals(listOf(ProductivityCategory.NOTES_FILES), apps.map { it.category })
        assertTrue("com.notion.id" in PRODUCTIVITY_APP_PACKAGES)
        assertTrue(gmail !in PRODUCTIVITY_APP_PACKAGES)
    }
}
