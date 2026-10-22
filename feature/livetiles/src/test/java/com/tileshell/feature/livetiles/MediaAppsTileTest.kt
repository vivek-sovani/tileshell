package com.tileshell.feature.livetiles

import android.content.pm.ApplicationInfo
import com.tileshell.core.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaAppsTileTest {
    private fun app(pkg: String, label: String, category: Int = ApplicationInfo.CATEGORY_UNDEFINED) =
        AppEntry(packageName = pkg, activityName = "$pkg.Main", label = label, category = category)

    @Test
    fun `apps split into video and music, badges first then by name`() {
        val catalog = listOf(
            app("com.netflix.mediaclient", "Netflix"),
            app("com.google.android.youtube", "YouTube"),
            app("x.music", "Zing", ApplicationInfo.CATEGORY_AUDIO),
            app("x.music2", "Amp", ApplicationInfo.CATEGORY_AUDIO),
            app("x.notes", "Notes"),
        )
        val (video, music) = mediaAppsOf(catalog, mapOf("com.netflix.mediaclient" to 2))
        assertEquals(listOf("Netflix", "YouTube"), video.map { it.label })
        assertEquals(2, video.first().badge)
        assertEquals(listOf("Amp", "Zing"), music.map { it.label })
    }

    @Test
    fun `a package or name listed twice shows once`() {
        val catalog = listOf(
            app("com.google.android.youtube", "YouTube"),
            AppEntry("com.google.android.youtube", "com.google.android.youtube.Other", "YouTube"),
            app("other.pkg", "youtube", ApplicationInfo.CATEGORY_VIDEO),
        )
        assertEquals(1, mediaAppsOf(catalog, emptyMap()).first.size)
    }

    @Test
    fun `the user's own choices move apps between the sections`() {
        val catalog = listOf(
            app("com.netflix.mediaclient", "Netflix"),
            app("x.player", "Player"),
            app("x.music", "Amp", ApplicationInfo.CATEGORY_AUDIO),
        )
        val choice = HubAppChoice()
            .addTo("x.player", MediaAppKind.VIDEO.name)
            .removeFrom("com.netflix.mediaclient", MediaAppKind.VIDEO.name)
            .addTo("x.music", MediaAppKind.VIDEO.name)
        val (video, music) = mediaAppsOf(catalog, emptyMap(), choice)
        assertEquals(listOf("Amp", "Player"), video.map { it.label })
        assertEquals(listOf("Amp"), music.map { it.label })
    }

    @Test
    fun `most used apps come first when there are no notifications`() {
        val catalog = listOf(app("a.one", "Alpha", ApplicationInfo.CATEGORY_VIDEO), app("b.two", "Beta", ApplicationInfo.CATEGORY_VIDEO), app("c.three", "Gamma", ApplicationInfo.CATEGORY_VIDEO))
        val (video, _) = mediaAppsOf(catalog, emptyMap(), HubAppChoice(), mapOf("c.three" to 9, "b.two" to 3))
        assertEquals(listOf("Gamma", "Beta", "Alpha"), video.map { it.label })
        val (withBadge, _) = mediaAppsOf(catalog, mapOf("a.one" to 1), HubAppChoice(), mapOf("c.three" to 9))
        assertEquals("Alpha", withBadge.first().label)
    }
}
