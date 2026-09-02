package com.tileshell.feature.livetiles.widget

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.tileshell.core.data.settings.SettingsRepository
import com.tileshell.core.design.TileAccents
import kotlinx.coroutines.flow.first

/** A resolved widget accent as plain ARGB ints, ready for `RemoteViews`. */
data class WidgetAccent(val accent: Int, val onAccent: Int)

/**
 * Resolves the colour a widget instance should render with: its own
 * per-instance override from [WidgetColorStore] if the user picked one via
 * [WidgetConfigureActivity], otherwise TileShell's global Personalize accent
 * — the same fallback chain every in-app tile without its own colour override
 * already uses. Shared by every widget kind's refresh worker so this lookup
 * (and the light/dark text-contrast rule) lives in exactly one place.
 */
suspend fun resolveWidgetAccent(context: Context, appWidgetId: Int): WidgetAccent {
    val overrideId = WidgetColorStore.colorId(context, appWidgetId)
    val globalAccentId = SettingsRepository.create(context).settings.first().accentId
    val accent = TileAccents.forId(overrideId ?: globalAccentId).toArgb()
    val onAccent = if (isLightAccent(accent)) 0xFF14141A.toInt() else 0xFFFFFFFF.toInt()
    return WidgetAccent(accent, onAccent)
}
