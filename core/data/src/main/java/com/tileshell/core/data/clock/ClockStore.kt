package com.tileshell.core.data.clock

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The clock hub's saved things, in the shared prefs file (the manual backup
 * carries them): timer sets, the world clock's cities, the stopwatch, and the
 * buzz settings. Running sessions are kept by [ClockSessions], not here.
 */
object ClockStore {
    private const val PREFS = "tileshell.prefs"
    private const val KEY_SETS = "clock_timer_sets"
    private const val KEY_CITIES = "clock_world_cities"
    private const val KEY_STOPWATCH = "clock_stopwatch"
    private const val KEY_BUZZ = "clock_buzz"
    private const val KEY_SOUND = "clock_sound"
    private const val KEY_DIM = "clock_dim"

    /** The keys the backup carries (everything but a running stopwatch). */
    val BACKUP_KEYS = listOf(KEY_SETS, KEY_CITIES, KEY_BUZZ, KEY_SOUND, KEY_DIM)

    private val sets = MutableStateFlow<List<TimerSet>?>(null)
    private val cities = MutableStateFlow<List<String>?>(null)
    private val stopwatch = MutableStateFlow<StopwatchState?>(null)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --- timer sets
    fun setsFlow(context: Context): StateFlow<List<TimerSet>> {
        if (sets.value == null) sets.value = decodeTimerSets(prefs(context).getString(KEY_SETS, null))
        @Suppress("UNCHECKED_CAST")
        return sets.asStateFlow() as StateFlow<List<TimerSet>>
    }

    fun saveSet(context: Context, set: TimerSet) {
        val current = setsFlow(context).value
        val next = if (current.any { it.id == set.id }) current.map { if (it.id == set.id) set else it } else current + set
        writeSets(context, next)
    }

    fun deleteSet(context: Context, id: String) = writeSets(context, setsFlow(context).value.filterNot { it.id == id })

    private fun writeSets(context: Context, next: List<TimerSet>) {
        prefs(context).edit().putString(KEY_SETS, encodeTimerSets(next)).apply()
        sets.value = next
    }

    // --- world clock: city names, home (the phone's own zone) always first and not stored
    fun citiesFlow(context: Context): StateFlow<List<String>> {
        if (cities.value == null) cities.value = readCities(context)
        @Suppress("UNCHECKED_CAST")
        return cities.asStateFlow() as StateFlow<List<String>>
    }

    fun addCity(context: Context, name: String) {
        val current = citiesFlow(context).value
        if (name !in current) writeCities(context, current + name)
    }

    fun removeCity(context: Context, name: String) = writeCities(context, citiesFlow(context).value - name)

    private fun writeCities(context: Context, next: List<String>) {
        prefs(context).edit().putString(KEY_CITIES, next.joinToString("\n").ifEmpty { "\n" }).apply()
        cities.value = next
    }

    private fun readCities(context: Context): List<String> =
        prefs(context).getString(KEY_CITIES, null)?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("London", "New York")

    // --- stopwatch
    fun stopwatchFlow(context: Context): StateFlow<StopwatchState> {
        if (stopwatch.value == null) stopwatch.value = decodeStopwatch(prefs(context).getString(KEY_STOPWATCH, null))
        @Suppress("UNCHECKED_CAST")
        return stopwatch.asStateFlow() as StateFlow<StopwatchState>
    }

    fun updateStopwatch(context: Context, change: (StopwatchState) -> StopwatchState) {
        val next = change(stopwatchFlow(context).value)
        prefs(context).edit().putString(KEY_STOPWATCH, encodeStopwatch(next)).apply()
        stopwatch.value = next
    }

    // --- buzz
    fun buzzStrength(context: Context): BuzzStrength =
        BuzzStrength.entries.find { it.name == prefs(context).getString(KEY_BUZZ, null) } ?: BuzzStrength.SHORT

    fun setBuzzStrength(context: Context, strength: BuzzStrength) {
        prefs(context).edit().putString(KEY_BUZZ, strength.name).apply()
    }

    fun soundToo(context: Context): Boolean = prefs(context).getBoolean(KEY_SOUND, false)

    fun setSoundToo(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_SOUND, on).apply()
    }

    /** Dim the screen (and keep it on) while a timer set runs in front: the default, so steps stay exact. */
    fun dimWhileRunning(context: Context): Boolean = prefs(context).getBoolean(KEY_DIM, true)

    fun setDimWhileRunning(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_DIM, on).apply()
    }

    /** Re-reads everything (after a backup restore wrote it). */
    fun reload(context: Context) {
        sets.value = decodeTimerSets(prefs(context).getString(KEY_SETS, null))
        cities.value = readCities(context)
    }
}
