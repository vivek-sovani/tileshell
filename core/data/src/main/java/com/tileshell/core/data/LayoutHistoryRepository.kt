package com.tileshell.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

private const val MAX_HISTORY = 10
private const val HISTORY_FILE = "layout_history.pb"

/**
 * One point-in-time snapshot of the Start layout stored in the rolling history.
 * [json] is the full [BackupManager]-formatted JSON, ready for direct restore.
 * [contentHash] enables deduplication — a snapshot is only appended when the
 * layout structure changed since the last save (settings changes are ignored).
 */
data class LayoutSnapshot(
    /** Epoch-ms string — stable unique key and natural sort order (newest first). */
    val id: String,
    val timestamp: Long,
    /** "auto" (scheduled worker) or "manual" (user-triggered). */
    val label: String,
    val tileCount: Int,
    val folderCount: Int,
    val contentHash: String,
    val json: String,
)

// ── Codec ─────────────────────────────────────────────────────────────────────

internal object LayoutHistoryCodec : Serializer<List<LayoutSnapshot>> {

    override val defaultValue: List<LayoutSnapshot> = emptyList()

    override suspend fun readFrom(input: InputStream): List<LayoutSnapshot> =
        runCatching { decode(input.readBytes().decodeToString()) }.getOrDefault(emptyList())

    override suspend fun writeTo(value: List<LayoutSnapshot>, output: OutputStream) {
        output.write(encode(value).encodeToByteArray())
    }

    fun encode(list: List<LayoutSnapshot>): String = JSONArray().also { arr ->
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("timestamp", s.timestamp)
                put("label", s.label)
                put("tileCount", s.tileCount)
                put("folderCount", s.folderCount)
                put("contentHash", s.contentHash)
                put("json", s.json)
            })
        }
    }.toString()

    fun decode(text: String): List<LayoutSnapshot> {
        val arr = runCatching { JSONArray(text) }.getOrElse { return emptyList() }
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                LayoutSnapshot(
                    id = o.getString("id"),
                    timestamp = o.getLong("timestamp"),
                    label = o.optString("label", "auto"),
                    tileCount = o.optInt("tileCount", 0),
                    folderCount = o.optInt("folderCount", 0),
                    contentHash = o.getString("contentHash"),
                    json = o.getString("json"),
                )
            }.getOrNull()
        }
    }
}

// ── DataStore extension ───────────────────────────────────────────────────────

private val Context.layoutHistoryDataStore: DataStore<List<LayoutSnapshot>> by dataStore(
    fileName = HISTORY_FILE,
    serializer = LayoutHistoryCodec,
)

// ── Repository ────────────────────────────────────────────────────────────────

class LayoutHistoryRepository(private val context: Context) {

    /** Emits the history list (newest first, max [MAX_HISTORY] entries). */
    val snapshots: Flow<List<LayoutSnapshot>> = context.layoutHistoryDataStore.data

    /**
     * Prepend [snapshot] to the history. No-op when [snapshot.contentHash] matches
     * the current head (layout unchanged since last save). Trims to [MAX_HISTORY].
     */
    suspend fun addSnapshot(snapshot: LayoutSnapshot) {
        context.layoutHistoryDataStore.updateData { current ->
            if (current.firstOrNull()?.contentHash == snapshot.contentHash) current
            else (listOf(snapshot) + current).take(MAX_HISTORY)
        }
    }

    suspend fun deleteSnapshot(id: String) {
        context.layoutHistoryDataStore.updateData { current ->
            current.filter { it.id != id }
        }
    }
}
