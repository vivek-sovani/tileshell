package com.tileshell.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.tileshell.core.data.TileColors
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

/**
 * Typed ("Proto"-style) DataStore serializer for [LauncherSettings]. Uses the
 * flat [SettingsCodec] text encoding rather than the protobuf toolchain — see
 * docs/DECISIONS.md (S17) — keeping the schema transactional, Flow-backed and
 * dependency-light. A new/missing store reads as [defaultValue]; a corrupt one
 * decodes tolerantly to the defaults.
 */
object SettingsSerializer : Serializer<LauncherSettings> {
    override val defaultValue = LauncherSettings()

    override suspend fun readFrom(input: InputStream): LauncherSettings =
        SettingsCodec.decode(input.readBytes().decodeToString())

    override suspend fun writeTo(t: LauncherSettings, output: OutputStream) {
        output.write(SettingsCodec.encode(t).encodeToByteArray())
    }
}

private val Context.settingsDataStore: DataStore<LauncherSettings> by dataStore(
    fileName = "launcher_settings.pb",
    serializer = SettingsSerializer,
)

/**
 * Reads and writes the personalization settings (FR-7). All writes are
 * transactional through DataStore; the [settings] flow emits the live value so
 * the UI re-applies theme/accent the instant a setter lands.
 */
class SettingsRepository(private val store: DataStore<LauncherSettings>) {

    val settings: Flow<LauncherSettings> = store.data

    suspend fun setDark(dark: Boolean) {
        store.updateData { it.copy(dark = dark) }
    }

    /** Sets the global accent; ignores ids outside the 14-colour palette. */
    suspend fun setAccent(accentId: String) {
        if (accentId !in TileColors.IDS) return
        store.updateData { it.copy(accentId = accentId) }
    }

    companion object {
        fun create(context: Context): SettingsRepository =
            SettingsRepository(context.applicationContext.settingsDataStore)
    }
}
