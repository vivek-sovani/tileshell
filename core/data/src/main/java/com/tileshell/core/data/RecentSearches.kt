package com.tileshell.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

/**
 * Recently-submitted quick-search queries, persisted as an ordered list of
 * trimmed strings (most-recent first, capped at [MAX], case-insensitive
 * de-duped) — surfaced as suggestions before the user types anything, mirroring
 * [RecentApps].
 */
object RecentSearches {

    const val MAX = 6

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The queries, most-recent first. Empty until the first search. */
    fun recent(context: Context): Flow<List<String>> =
        context.applicationContext.recentSearchesStore.data

    /** Record a submitted query (fire-and-forget); moves it to the front, de-duped. */
    fun record(context: Context, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val app = context.applicationContext
        writeScope.launch {
            app.recentSearchesStore.updateData { current ->
                (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) }).take(MAX)
            }
        }
    }

    /** Removes one suggestion (its dismiss "x"). */
    fun remove(context: Context, query: String) {
        val app = context.applicationContext
        writeScope.launch {
            app.recentSearchesStore.updateData { current -> current.filterNot { it == query } }
        }
    }
}

/** Newline-delimited codec for the recent-query list (tolerant of empties). */
internal object RecentSearchesSerializer : Serializer<List<String>> {
    override val defaultValue: List<String> = emptyList()

    override suspend fun readFrom(input: InputStream): List<String> =
        input.readBytes().decodeToString()
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    override suspend fun writeTo(t: List<String>, output: OutputStream) {
        output.write(t.joinToString("\n").encodeToByteArray())
    }
}

internal val Context.recentSearchesStore: DataStore<List<String>> by dataStore(
    fileName = "recent_searches.pb",
    serializer = RecentSearchesSerializer,
)
