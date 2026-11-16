package com.tileshell.feature.livetiles

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tileshell.core.data.settings.UnitSystem
import java.util.Locale

/** What a label is actually measured in once "auto" has been resolved. */
enum class ResolvedUnits { METRIC, IMPERIAL }

/**
 * The measuring units the weather, battery and health labels use: °C / km/h / km, or °F / mph / miles.
 *
 * The choice is [UnitSystem] (auto follows the phone's region, or a fixed pick). The resolved value lives in
 * snapshot state so composables that call [tempLabel] and friends recompose when it changes, and is mirrored to
 * SharedPreferences so widget workers (which start in a bare process) read the same choice via [load].
 */
object Units {
    private const val PREFS = "tileshell.prefs"
    private const val KEY = "unit_system"

    /** Regions that measure in Fahrenheit, miles and mph. */
    private val IMPERIAL_REGIONS = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW", "FM", "MH", "PR", "GU", "VI", "AS", "MP")

    private var system by mutableStateOf(UnitSystem.AUTO)

    /** Resolved units right now. */
    val current: ResolvedUnits get() = resolve(system, Locale.getDefault().country)

    val isImperial: Boolean get() = current == ResolvedUnits.IMPERIAL

    fun resolve(system: UnitSystem, country: String): ResolvedUnits = when (system) {
        UnitSystem.METRIC -> ResolvedUnits.METRIC
        UnitSystem.IMPERIAL -> ResolvedUnits.IMPERIAL
        UnitSystem.AUTO -> if (country.uppercase(Locale.ROOT) in IMPERIAL_REGIONS) ResolvedUnits.IMPERIAL else ResolvedUnits.METRIC
    }

    /** Applies the user's choice (called from Start whenever the setting changes) and mirrors it for widget workers. */
    fun apply(context: Context, value: UnitSystem) {
        system = value
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, value.name).apply()
        }
    }

    /** Reads the mirrored choice; widget workers call this first. */
    fun load(context: Context) {
        val name = runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }.getOrNull()
        system = UnitSystem.entries.find { it.name == name } ?: UnitSystem.AUTO
    }

    fun celsiusToFahrenheit(c: Double): Double = c * 9.0 / 5.0 + 32.0

    fun kmToMiles(km: Double): Double = km * 0.621371

    fun kphToMph(kph: Double): Double = kph * 0.621371

    /** A temperature as an integer with its degree sign, e.g. `23°` or `73°`. */
    fun temp(tempC: Int, units: ResolvedUnits = current): String =
        if (units == ResolvedUnits.IMPERIAL) "${Math.round(celsiusToFahrenheit(tempC.toDouble()))}°" else "$tempC°"

    /** A temperature with its scale letter, e.g. `31 °C` or `88 °F` (battery). */
    fun tempWithScale(tempC: Double, decimals: Int = 0, units: ResolvedUnits = current): String {
        val imperial = units == ResolvedUnits.IMPERIAL
        val v = if (imperial) celsiusToFahrenheit(tempC) else tempC
        return String.format(Locale.US, "%.${decimals}f", v) + if (imperial) " °F" else " °C"
    }

    /** Wind speed, e.g. `12 km/h` or `7 mph`. */
    fun wind(kph: Int, units: ResolvedUnits = current): String =
        if (units == ResolvedUnits.IMPERIAL) "${Math.round(kphToMph(kph.toDouble()))} mph" else "$kph km/h"

    /** Distance, one decimal, e.g. `3.2 km` or `2.0 mi`. */
    fun distance(km: Double, units: ResolvedUnits = current): String =
        if (units == ResolvedUnits.IMPERIAL) String.format(Locale.US, "%.1f mi", kmToMiles(km))
        else String.format(Locale.US, "%.1f km", km)

    /** A height, e.g. `170 cm` or `5′7″`. */
    fun height(cm: Int, units: ResolvedUnits = current): String {
        if (units != ResolvedUnits.IMPERIAL) return "$cm cm"
        val totalIn = Math.round(cm / 2.54).toInt()
        return "${totalIn / 12}′${totalIn % 12}″"
    }
}
