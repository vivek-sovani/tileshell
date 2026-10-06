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
 * lists. An app can be in several sections (CRED is both a payment app and a
 * card app): [added] puts it into more sections, [removed] takes it out of
 * sections, and [dropped] is the older "off every section" form. The sections
 * an app is in are its built-in ones plus [added] minus [removed]
 * ([sectionsOf]). Pure, so it is unit-tested.
 */
data class HubAppChoice(
    val added: Map<String, Set<String>> = emptyMap(),
    val removed: Map<String, Set<String>> = emptyMap(),
    val dropped: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && dropped.isEmpty()

    /** Every package taken off at least one section (for "n apps taken off this page"). */
    val takenOff: Set<String> get() = removed.keys + dropped

    fun sectionsOf(packageName: String, builtIn: Set<String>): Set<String> =
        if (packageName in dropped) emptySet() else (builtIn + added[packageName].orEmpty()) - removed[packageName].orEmpty()

    fun addTo(packageName: String, section: String) = copy(
        added = added.withSection(packageName, section),
        removed = removed.withoutSection(packageName, section),
        dropped = dropped - packageName,
    )

    fun removeFrom(packageName: String, section: String) = copy(
        added = added.withoutSection(packageName, section),
        removed = removed.withSection(packageName, section),
    )

    /** Moves an app out of [from] and into [to] (it stays in its other sections). */
    fun move(packageName: String, from: String, to: String) =
        if (from == to) this else removeFrom(packageName, from).addTo(packageName, to)

    /** Brings every taken-off app back (to wherever it normally is). */
    fun restoreRemoved() = copy(removed = emptyMap(), dropped = emptySet())

    private fun Map<String, Set<String>>.withSection(pkg: String, section: String) = this + (pkg to (this[pkg].orEmpty() + section))

    private fun Map<String, Set<String>>.withoutSection(pkg: String, section: String): Map<String, Set<String>> {
        val left = this[pkg].orEmpty() - section
        return if (left.isEmpty()) this - pkg else this + (pkg to left)
    }
}

/**
 * One line per choice: `a|<package>|<SECTION>` (added to a section),
 * `r|<package>|<SECTION>` (taken off one), `d|<package>` (off every section).
 * `p|…` is the older "placed in" form and reads as an add. Tolerant of junk lines.
 */
object HubAppChoiceCodec {
    fun encode(choice: HubAppChoice): String =
        (choice.added.flatMap { (pkg, set) -> set.sorted().map { "a|$pkg|$it" } } +
            choice.removed.flatMap { (pkg, set) -> set.sorted().map { "r|$pkg|$it" } } +
            choice.dropped.map { "d|$it" }).joinToString("\n")

    fun decode(text: String?): HubAppChoice {
        var choice = HubAppChoice()
        text.orEmpty().lineSequence().forEach { line ->
            val parts = line.trim().split('|')
            when {
                parts.size == 3 && parts[0] in setOf("a", "p") && parts[1].isNotEmpty() && parts[2].isNotEmpty() ->
                    choice = choice.copy(added = choice.added + (parts[1] to (choice.added[parts[1]].orEmpty() + parts[2])))
                parts.size == 3 && parts[0] == "r" && parts[1].isNotEmpty() && parts[2].isNotEmpty() ->
                    choice = choice.copy(removed = choice.removed + (parts[1] to (choice.removed[parts[1]].orEmpty() + parts[2])))
                parts.size == 2 && parts[0] == "d" && parts[1].isNotEmpty() -> choice = choice.copy(dropped = choice.dropped + parts[1])
            }
        }
        return choice
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
