package com.tileshell.feature.livetiles

import android.content.Context
import com.tileshell.core.data.calendarSystemFor
import com.tileshell.core.data.defaultCalendarSystemFor
import java.util.Locale

/** Which calendar system the calendar hub's "calendar systems" page is showing; chosen by chip, remembered. */
object CalendarSystemsPrefs {
    private const val PREFS = "tileshell.prefs"
    private const val KEY = "calhub_system"

    /** The remembered system, else the region's usual one, else null (nothing chosen yet). */
    fun selected(context: Context): String? {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (stored != null && calendarSystemFor(stored) != null) return stored
        return defaultCalendarSystemFor(Locale.getDefault().country)
    }

    fun select(context: Context, systemId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, systemId).apply()
    }
}
