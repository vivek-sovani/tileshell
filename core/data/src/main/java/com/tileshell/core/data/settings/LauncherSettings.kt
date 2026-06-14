package com.tileshell.core.data.settings

import com.tileshell.core.data.TileColors

/**
 * Persisted personalization (FR-7). Kept deliberately flat and framework-free so
 * it can be serialized by [SettingsCodec] and unit-tested without Android.
 *
 * @property dark dark theme when true, light when false (prototype `state.theme`)
 * @property accentId one of the 14 [TileColors] ids — the global accent
 *   (`state.accent`) used by app-list/chrome; Start tiles keep their own colours.
 */
data class LauncherSettings(
    val dark: Boolean = true,
    val accentId: String = "blue",
)

/**
 * Tiny line-oriented `key=value` codec for [LauncherSettings]. Pure Kotlin (no
 * org.json, no protobuf toolchain) so the round-trip is JVM-unit-testable, and
 * tolerant: unknown keys, malformed lines, and out-of-range values fall back to
 * the defaults rather than throwing (a corrupt store reads as defaults).
 */
object SettingsCodec {

    fun encode(settings: LauncherSettings): String =
        "dark=${settings.dark}\naccent=${settings.accentId}"

    fun decode(text: String): LauncherSettings {
        val defaults = LauncherSettings()
        var dark = defaults.dark
        var accentId = defaults.accentId
        text.lineSequence().forEach { line ->
            val sep = line.indexOf('=')
            if (sep <= 0) return@forEach
            val key = line.substring(0, sep).trim()
            val value = line.substring(sep + 1).trim()
            when (key) {
                "dark" -> dark = value.toBooleanStrictOrNull() ?: dark
                "accent" -> if (value in TileColors.IDS) accentId = value
            }
        }
        return LauncherSettings(dark = dark, accentId = accentId)
    }
}
