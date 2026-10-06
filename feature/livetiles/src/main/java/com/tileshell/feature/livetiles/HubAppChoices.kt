package com.tileshell.feature.livetiles

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The hubs whose apps page the user can edit. */
enum class HubKind(val key: String) {
    PEOPLE("people"),
    MONEY("money"),
    PRODUCTIVITY("productivity"),
}

/**
 * What the user changed on one hub's apps page, on top of the built-in app
 * lists: [placed] puts an app into a section (a newly added app, or a built-in
 * one moved) as `package → section key`; [dropped] takes an app off the page.
 * An app is in at most one of the two. Pure, so it is unit-tested.
 */
data class HubAppChoice(
    val placed: Map<String, String> = emptyMap(),
    val dropped: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = placed.isEmpty() && dropped.isEmpty()

    fun place(packageName: String, section: String) =
        copy(placed = placed + (packageName to section), dropped = dropped - packageName)

    fun drop(packageName: String) =
        copy(placed = placed - packageName, dropped = dropped + packageName)

    /** Brings every dropped app back (to wherever it normally is). */
    fun restoreDropped() = copy(dropped = emptySet())
}

/** One line per choice: `p|<package>|<SECTION>` or `d|<package>`. Tolerant of junk lines. */
object HubAppChoiceCodec {
    fun encode(choice: HubAppChoice): String =
        (choice.placed.map { (pkg, section) -> "p|$pkg|$section" } + choice.dropped.map { "d|$it" }).joinToString("\n")

    fun decode(text: String?): HubAppChoice {
        var placed = emptyMap<String, String>()
        var dropped = emptySet<String>()
        text.orEmpty().lineSequence().forEach { line ->
            val parts = line.trim().split('|')
            when {
                parts.size == 3 && parts[0] == "p" && parts[1].isNotEmpty() && parts[2].isNotEmpty() ->
                    placed = placed + (parts[1] to parts[2])
                parts.size == 2 && parts[0] == "d" && parts[1].isNotEmpty() -> dropped = dropped + parts[1]
            }
        }
        // An app can't be both; a drop wins over a stale placement.
        return HubAppChoice(placed - dropped, dropped)
    }
}

/**
 * The user's app choices for the People, Money and Productivity hubs, held in
 * memory so the pure directory functions (`peopleCategoryFor`, `moneyAppKind`,
 * `productivityApps`) can consult them. Saved in `tileshell.prefs`, one string
 * per hub, and carried by the manual backup (see [BackupExtras]). Empty until
 * [ensureLoaded] runs, which the hubs, the start screen and the notification
 * service all do.
 */
object HubAppChoices {
    private const val PREFS = "tileshell.prefs"

    private val _state = MutableStateFlow<Map<HubKind, HubAppChoice>>(emptyMap())
    @Volatile private var loaded = false

    internal fun prefKey(kind: HubKind) = "hub_apps_${kind.key}"

    fun ensureLoaded(context: Context) {
        if (loaded) return
        reload(context)
    }

    /** Re-reads everything from preferences (first load, or after a backup restore). */
    fun reload(context: Context) {
        val prefs = prefs(context)
        _state.value = HubKind.entries.associateWith { HubAppChoiceCodec.decode(prefs.getString(prefKey(it), null)) }
        loaded = true
    }

    fun state(context: Context): StateFlow<Map<HubKind, HubAppChoice>> {
        ensureLoaded(context)
        return _state.asStateFlow()
    }

    fun current(kind: HubKind): HubAppChoice = _state.value[kind] ?: HubAppChoice()

    fun update(context: Context, kind: HubKind, transform: (HubAppChoice) -> HubAppChoice) {
        ensureLoaded(context)
        val next = transform(current(kind))
        prefs(context).edit().putString(prefKey(kind), HubAppChoiceCodec.encode(next)).apply()
        _state.value = _state.value + (kind to next)
    }

    /** Test hook: sets a hub's choices without touching preferences. */
    internal fun setForTest(kind: HubKind, choice: HubAppChoice) {
        loaded = true
        _state.value = _state.value + (kind to choice)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
