package com.tileshell.feature.livetiles

import com.tileshell.feature.livetiles.money.MoneyAppKind
import com.tileshell.feature.livetiles.money.moneyAppKind
import com.tileshell.feature.livetiles.money.moneyAppKinds
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

    @Test fun anAppCanBeInSeveralSections() {
        val c = HubAppChoice().addTo("cred", "PAYMENT").addTo("cred", "CARD")
        assertEquals(setOf("PAYMENT", "CARD"), c.sectionsOf("cred", emptySet()))
        // Built-in section plus an added one.
        assertEquals(setOf("BANK", "CARD"), HubAppChoice().addTo("x", "CARD").sectionsOf("x", setOf("BANK")))
    }

    @Test fun removeAndMoveAreOnePerSection() {
        val c = HubAppChoice().addTo("a", "CHAT").addTo("a", "SOCIAL")
        assertEquals(setOf("SOCIAL"), c.removeFrom("a", "CHAT").sectionsOf("a", emptySet()))
        // Taking a built-in section off.
        val builtIn = HubAppChoice().removeFrom("a", "CHAT")
        assertEquals(emptySet<String>(), builtIn.sectionsOf("a", setOf("CHAT")))
        assertEquals(setOf("a"), builtIn.takenOff)
        // Moving leaves the app's other sections alone.
        val moved = HubAppChoice().addTo("a", "PAYMENT").move("a", "PAYMENT", "CARD")
        assertEquals(setOf("CARD"), moved.sectionsOf("a", emptySet()))
        val movedBuiltIn = HubAppChoice().move("a", "BANK", "CARD")
        assertEquals(setOf("CARD"), movedBuiltIn.sectionsOf("a", setOf("BANK")))
        assertEquals(HubAppChoice(), HubAppChoice().move("a", "X", "X"))
    }

    @Test fun addingBackUndoesARemoveAndRestoreClearsTakenOff() {
        val c = HubAppChoice().removeFrom("a", "CHAT").addTo("a", "CHAT")
        assertEquals(setOf("CHAT"), c.sectionsOf("a", setOf("CHAT")))
        assertTrue(c.removed.isEmpty())
        val off = HubAppChoice().removeFrom("a", "CHAT").removeFrom("b", "MAIL")
        assertTrue(off.restoreRemoved().isEmpty)
    }

    @Test fun codecRoundTripsAndReadsTheOlderForms() {
        val c = HubAppChoice(
            added = mapOf("com.pinterest" to setOf("SOCIAL"), "cred" to setOf("PAYMENT", "CARD")),
            removed = mapOf("com.whatsapp" to setOf("CHAT")),
            dropped = setOf("old.app"),
        )
        assertEquals(c, HubAppChoiceCodec.decode(HubAppChoiceCodec.encode(c)))
        assertEquals(HubAppChoice(), HubAppChoiceCodec.decode(null))
        assertEquals(HubAppChoice(), HubAppChoiceCodec.decode("junk\na|onlytwo\nq|a|b\nd|\np||X"))
        // The first build wrote `p|` for "placed in".
        assertEquals(setOf("SOCIAL"), HubAppChoiceCodec.decode("p|com.pinterest|SOCIAL").added["com.pinterest"])
        assertEquals(emptySet<String>(), HubAppChoiceCodec.decode("d|x").sectionsOf("x", setOf("CHAT")))
    }

    // ---- People

    @Test fun addedAppJoinsPeopleAndItsNotificationsCount() {
        assertNull(peopleCategoryFor("com.pinterest"))
        set(HubKind.PEOPLE, HubAppChoice().addTo("com.pinterest", PeopleCategory.SOCIAL.name))
        assertEquals(PeopleCategory.SOCIAL, peopleCategoryFor("com.pinterest"))
        assertTrue("com.pinterest" in PEOPLE_APP_PACKAGES)
        val apps = peopleApps(mapOf("com.pinterest" to "Pinterest"), mapOf("com.pinterest" to 2))
        assertEquals(listOf(PeopleCategory.SOCIAL), apps.map { it.category })
        val snap = NotificationSnapshot(mapOf("com.pinterest" to 2), emptyMap())
        assertEquals(listOf("com.pinterest" to 2), whatsNewApps(snap))
    }

    @Test fun droppedAndMovedBuiltInPeopleApps() {
        set(HubKind.PEOPLE, HubAppChoice().removeFrom("com.whatsapp", "CHAT").move("org.telegram.messenger", "CHAT", "MAIL"))
        assertNull(peopleCategoryFor("com.whatsapp"))
        assertTrue("com.whatsapp" !in PEOPLE_APP_PACKAGES)
        assertEquals(PeopleCategory.MAIL, peopleCategoryFor("org.telegram.messenger"))
        assertEquals(PeopleCategory.CHAT, builtInPeopleCategory("org.telegram.messenger"))
    }

    @Test fun anAppInTwoPeopleSectionsListsInBoth() {
        set(HubKind.PEOPLE, HubAppChoice().addTo("com.whatsapp", PeopleCategory.SOCIAL.name))
        val apps = peopleApps(mapOf("com.whatsapp" to "WhatsApp"), emptyMap())
        assertEquals(setOf(PeopleCategory.CHAT, PeopleCategory.SOCIAL), apps.map { it.category }.toSet())
    }

    // ---- Money

    @Test fun addedMoneyAppUsesItsSection() {
        set(HubKind.MONEY, HubAppChoice().addTo("com.example.newpay", MoneyAppKind.PAYMENT.name))
        assertEquals(MoneyAppKind.PAYMENT, moneyAppKind("com.example.newpay", "Newpay"))
    }

    @Test fun credCanBeBothAPaymentAndACardApp() {
        val cred = "com.dreamplug.androidapp"
        assertEquals(listOf(MoneyAppKind.CARD), moneyAppKinds(cred, "CRED"))
        set(HubKind.MONEY, HubAppChoice().addTo(cred, MoneyAppKind.PAYMENT.name))
        assertEquals(listOf(MoneyAppKind.PAYMENT, MoneyAppKind.CARD), moneyAppKinds(cred, "CRED"))
    }

    @Test fun droppedMoneyAppIsNoLongerOne() {
        assertEquals(MoneyAppKind.PAYMENT, moneyAppKind("com.phonepe.app", "PhonePe"))
        set(HubKind.MONEY, HubAppChoice().removeFrom("com.phonepe.app", MoneyAppKind.PAYMENT.name))
        assertNull(moneyAppKind("com.phonepe.app", "PhonePe"))
    }

    // ---- Productivity

    @Test fun productivityPlacementDropAndIndependenceFromPeople() {
        val gmail = "com.google.android.gm"
        assertEquals(ProductivityCategory.MAIL, productivityApps(mapOf(gmail to "Gmail")).single().category)
        // Dropping Gmail from People doesn't drop it from Productivity.
        set(HubKind.PEOPLE, HubAppChoice().removeFrom(gmail, PeopleCategory.MAIL.name))
        assertEquals(1, productivityApps(mapOf(gmail to "Gmail")).size)
        // An added app lands in its chosen section; a dropped one is gone.
        set(HubKind.PRODUCTIVITY, HubAppChoice().addTo("com.notion.id", ProductivityCategory.NOTES_FILES.name).removeFrom(gmail, ProductivityCategory.MAIL.name))
        val apps = productivityApps(mapOf("com.notion.id" to "Notion", gmail to "Gmail"))
        assertEquals(listOf(ProductivityCategory.NOTES_FILES), apps.map { it.category })
        assertTrue("com.notion.id" in PRODUCTIVITY_APP_PACKAGES)
        assertTrue(gmail !in PRODUCTIVITY_APP_PACKAGES)
    }
}
