package com.tileshell.feature.start

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationOwnershipTest {
    private val amazon = "in.amazon.mShop.android.shopping"
    private fun t(id: String, pkg: String, act: String) = OwnedTile(id, pkg, act)

    @Test fun onlyTheMainLauncherEntryKeepsTheNotifications() {
        val tiles = listOf(
            t("pay", amazon, "com.amazon.mShop.pay.Splash"),
            t("main", amazon, "com.amazon.mShop.home.HomeActivity"),
            t("now", amazon, "com.amazon.now.Launcher"),
        )
        val muted = notificationMutedTileIds(tiles) { "com.amazon.mShop.home.HomeActivity" }
        assertEquals(setOf("pay", "now"), muted)
    }

    @Test fun withoutAKnownMainEntryTheFirstTileOwnsThem() {
        val tiles = listOf(t("a", amazon, "x.A"), t("b", amazon, "x.B"))
        assertEquals(setOf("b"), notificationMutedTileIds(tiles) { null })
        assertEquals(setOf("b"), notificationMutedTileIds(tiles) { "x.Other" })
    }

    @Test fun singleTilesAndBlankPackagesAreNeverMuted() {
        val tiles = listOf(t("a", "com.one", "x"), t("w", "", "weather"), t("v", "", "clock"))
        assertEquals(emptySet<String>(), notificationMutedTileIds(tiles) { null })
    }

    @Test fun eachSharedPackageHasItsOwnOwner() {
        val tiles = listOf(t("1", "p.a", "A1"), t("2", "p.a", "A2"), t("3", "p.b", "B1"), t("4", "p.b", "B2"))
        assertEquals(setOf("2", "4"), notificationMutedTileIds(tiles) { null })
    }
}
