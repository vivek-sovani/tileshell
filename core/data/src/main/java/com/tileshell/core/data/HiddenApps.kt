package com.tileshell.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

/**
 * Packages hidden from the app list — long-pressed and "hide"-d there, brought
 * back from the personalize "hidden apps" sheet. Keyed by package name (hiding
 * is per-app, not per-activity). Kept in its own DataStore, mirroring
 * [RecentApps].
 */
object HiddenApps {

    /** The hidden package names. Empty until the first hide. */
    fun hidden(context: Context): Flow<Set<String>> =
        context.applicationContext.hiddenAppsStore.data

    suspend fun hide(context: Context, packageName: String) {
        context.applicationContext.hiddenAppsStore.updateData { it + packageName }
    }

    suspend fun unhide(context: Context, packageName: String) {
        context.applicationContext.hiddenAppsStore.updateData { it - packageName }
    }
}

/** Newline-delimited codec for the hidden-package set (tolerant of empties). */
internal object HiddenAppsSerializer : Serializer<Set<String>> {
    override val defaultValue: Set<String> = emptySet()

    override suspend fun readFrom(input: InputStream): Set<String> =
        input.readBytes().decodeToString()
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    override suspend fun writeTo(t: Set<String>, output: OutputStream) {
        output.write(t.joinToString("\n").encodeToByteArray())
    }
}

internal val Context.hiddenAppsStore: DataStore<Set<String>> by dataStore(
    fileName = "hidden_apps.pb",
    serializer = HiddenAppsSerializer,
)
