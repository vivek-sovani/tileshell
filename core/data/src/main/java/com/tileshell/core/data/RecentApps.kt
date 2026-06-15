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
 * Most-recently-launched apps, persisted as an ordered list of component keys
 * ("package/activity", most-recent first, capped at [MAX]). Recorded at the single
 * launch choke point ([AppLauncher.launch]) so it reflects every launch — Start
 * tiles, folder children and the app list alike — without a usage-access
 * permission. Drives the "recent" section at the top of the app list.
 */
object RecentApps {

    const val MAX = 12

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The component keys, most-recent first. Empty until the first launch. */
    fun recent(context: Context): Flow<List<String>> =
        context.applicationContext.recentAppsStore.data

    /** Record a launch (fire-and-forget); moves the app to the front, de-duped. */
    fun record(context: Context, packageName: String, activityName: String) {
        val app = context.applicationContext
        val key = "$packageName/$activityName"
        writeScope.launch {
            app.recentAppsStore.updateData { current ->
                (listOf(key) + current.filter { it != key }).take(MAX)
            }
        }
    }
}

/** Newline-delimited codec for the recent-component-key list (tolerant of empties). */
internal object RecentAppsSerializer : Serializer<List<String>> {
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

internal val Context.recentAppsStore: DataStore<List<String>> by dataStore(
    fileName = "recent_apps.pb",
    serializer = RecentAppsSerializer,
)
