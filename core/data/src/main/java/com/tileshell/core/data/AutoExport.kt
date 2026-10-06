package com.tileshell.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The "auto-export to a folder" choice: the user picks a folder once (Android's
 * folder picker, so Drive and other cloud folders work) and TileShell writes a
 * dated full backup there on a schedule, keeping the newest few.
 * [folderName] is only for display; [lastError] is null after a good run.
 */
data class AutoExportState(
    val enabled: Boolean = false,
    val folderUri: String? = null,
    val folderName: String? = null,
    val intervalDays: Int = DEFAULT_INTERVAL_DAYS,
    val lastRunAt: Long = 0L,
    val lastError: String? = null,
) {
    val ready: Boolean get() = enabled && folderUri != null

    companion object {
        const val DEFAULT_INTERVAL_DAYS = 7
        /** How many auto-exported files stay in the folder. */
        const val KEEP = 5
    }
}

/** File naming and clean-up for auto-exports. Pure, so it is unit-tested. */
object AutoExportNaming {
    private const val PREFIX = "tileshell-auto-"
    private val pattern = Regex("^tileshell-auto-\\d{4}-\\d{2}-\\d{2}-\\d{4}.*\\.json$")

    fun fileName(timeMillis: Long, locale: Locale = Locale.US): String =
        PREFIX + SimpleDateFormat("yyyy-MM-dd-HHmm", locale).format(Date(timeMillis)) + ".json"

    /** Only our own auto-export files; manual exports and other files are never touched. */
    fun isAutoExport(name: String): Boolean = pattern.matches(name)

    /** The files to delete so that only the newest [keep] auto-exports remain (names sort by time). */
    fun namesToDelete(names: List<String>, keep: Int = AutoExportState.KEEP): List<String> =
        names.filter(::isAutoExport)
            .sortedWith(compareByDescending<String> { it.substring(PREFIX.length, PREFIX.length + STAMP_LENGTH) }.thenByDescending(::duplicateIndex))
            .drop(keep.coerceAtLeast(1))

    /** The "(2)" a storage provider adds when two files share a name (two runs in one minute); 0 when absent. */
    private fun duplicateIndex(name: String): Int = duplicate.find(name)?.groupValues?.get(1)?.toInt() ?: 0
    private val duplicate = Regex("\\((\\d+)\\)\\.json$")
    private const val STAMP_LENGTH = 15 // yyyy-MM-dd-HHmm

    /** One line under the switch, e.g. "last export 2h ago · saved". */
    fun statusLine(state: AutoExportState, now: Long): String? {
        if (state.lastRunAt <= 0L) return null
        val minutes = ((now - state.lastRunAt) / 60_000L).coerceAtLeast(0)
        val ago = when {
            minutes < 2 -> "just now"
            minutes < 120 -> "${minutes}m ago"
            minutes < 48 * 60 -> "${minutes / 60}h ago"
            else -> "${minutes / (24 * 60)}d ago"
        }
        return state.lastError?.let { "last try $ago · $it" } ?: "last export $ago · saved"
    }
}

/** Where the auto-export settings live (not part of backups: a folder grant is per device). */
object AutoExportPrefs {
    private const val PREFS = "tileshell.prefs"
    private const val K_ENABLED = "auto_export_enabled"
    private const val K_URI = "auto_export_uri"
    private const val K_NAME = "auto_export_name"
    private const val K_DAYS = "auto_export_days"
    private const val K_LAST_AT = "auto_export_last_at"
    private const val K_LAST_ERR = "auto_export_last_error"

    private val _state = MutableStateFlow<AutoExportState?>(null)

    fun state(context: Context): StateFlow<AutoExportState> {
        current(context)
        @Suppress("UNCHECKED_CAST")
        return _state.asStateFlow() as StateFlow<AutoExportState>
    }

    fun current(context: Context): AutoExportState = _state.value ?: read(context).also { _state.value = it }

    private fun read(context: Context): AutoExportState {
        val p = prefs(context)
        return AutoExportState(
            enabled = p.getBoolean(K_ENABLED, false),
            folderUri = p.getString(K_URI, null),
            folderName = p.getString(K_NAME, null),
            intervalDays = p.getInt(K_DAYS, AutoExportState.DEFAULT_INTERVAL_DAYS).let { if (it <= 1) 1 else 7 },
            lastRunAt = p.getLong(K_LAST_AT, 0L),
            lastError = p.getString(K_LAST_ERR, null),
        )
    }

    fun update(context: Context, transform: (AutoExportState) -> AutoExportState) {
        val next = transform(current(context))
        prefs(context).edit()
            .putBoolean(K_ENABLED, next.enabled)
            .putString(K_URI, next.folderUri)
            .putString(K_NAME, next.folderName)
            .putInt(K_DAYS, next.intervalDays)
            .putLong(K_LAST_AT, next.lastRunAt)
            .putString(K_LAST_ERR, next.lastError)
            .apply()
        _state.value = next
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
