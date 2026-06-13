package com.tileshell.feature.applist

import com.tileshell.core.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class AppListFilterTest {

    private fun app(label: String) =
        AppEntry(packageName = "pkg.${label.lowercase()}", activityName = ".Main", label = label)

    private val apps = listOf(
        app("Calculator"), app("Calendar"), app("Camera"), app("Maps"), app("Messages"), app("9 Weather"),
    )

    @Test
    fun `blank query returns all apps`() {
        assertEquals(apps, AppListFilter.filter(apps, ""))
        assertEquals(apps, AppListFilter.filter(apps, "   "))
    }

    @Test
    fun `filter is case-insensitive substring on label`() {
        assertEquals(
            listOf("Calculator", "Calendar"),
            AppListFilter.filter(apps, "cal").map { it.label },
        )
        assertEquals(
            listOf("Messages"),
            AppListFilter.filter(apps, "SAGE").map { it.label },
        )
    }

    @Test
    fun `query is trimmed`() {
        assertEquals(listOf("Camera"), AppListFilter.filter(apps, "  camera  ").map { it.label })
    }

    @Test
    fun `no match yields empty list`() {
        assertEquals(emptyList<AppEntry>(), AppListFilter.filter(apps, "zzz"))
    }

    @Test
    fun `available letters are the distinct section keys`() {
        assertEquals(setOf("C", "M", "#"), AppListFilter.availableLetters(apps))
    }

    @Test
    fun `available letters of empty list is empty`() {
        assertEquals(emptySet<String>(), AppListFilter.availableLetters(emptyList()))
    }
}
