package com.tileshell.feature.livetiles.health

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tileshell.feature.livetiles.HubAppChoices
import com.tileshell.feature.livetiles.HubKind
import com.tileshell.feature.livetiles.launcherEntries
import com.tileshell.feature.livetiles.packageOfEntry
import com.tileshell.feature.livetiles.rememberAppOpenCounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The sections of the health hub's apps page. */
enum class HealthAppKind { HEALTH, WELLBEING }

/** [packageName] is the entry id: the package for an app's main entry, "package/Class" for another entry of it. */
data class HealthApp(val packageName: String, val label: String, val kind: HealthAppKind)

private val HEALTH_PACKAGES = setOf(
    "com.sec.android.app.shealth", "com.google.android.apps.fitness", "com.fitbit.FitbitMobile", "com.strava",
    "com.garmin.android.apps.connectmobile", "com.xiaomi.hm.health", "com.huami.watch.hmwatch", "com.healthifyme.basic",
    "com.google.android.apps.healthdata",
)
private val WELLBEING_PACKAGES = setOf("com.google.android.apps.wellbeing", "com.samsung.android.forest")
private val HEALTH_WORDS = listOf(
    "health", "fit", "fitness", "workout", "yoga", "pedometer", "strava", "fitbit", "garmin", "zepp", "healthify", "cult", "steps",
    "running", "run", "cycling",
)
private val WELLBEING_WORDS = listOf("wellbeing", "meditation", "mindful", "calm", "headspace", "sleep", "digital wellbeing")

private fun hasWord(lower: String, word: String) = Regex("""(^|[^a-z])${Regex.escape(word)}([^a-z]|$)""").containsMatchIn(lower)

/** The built-in kind of an installed app, ignoring the user's choices: a known package or a word in its label. Pure. */
internal fun builtInHealthKind(packageName: String, label: String): HealthAppKind? {
    if (packageName in WELLBEING_PACKAGES) return HealthAppKind.WELLBEING
    if (packageName in HEALTH_PACKAGES) return HealthAppKind.HEALTH
    val l = label.lowercase()
    if (WELLBEING_WORDS.any { hasWord(l, it) }) return HealthAppKind.WELLBEING
    if (HEALTH_WORDS.any { hasWord(l, it) }) return HealthAppKind.HEALTH
    return null
}

/** Every health section an app is in: its built-in one plus what the user added, minus what they took off. */
fun healthAppKinds(entryId: String, label: String): List<HealthAppKind> {
    val choice = HubAppChoices.current(HubKind.HEALTH)
    val names = choice.sectionsOf(entryId, setOfNotNull(builtInHealthKind(packageOfEntry(entryId), label)?.name))
    return HealthAppKind.entries.filter { it.name in names }
}

internal fun installedHealthApps(context: Context): List<HealthApp> = runCatching {
    launcherEntries(context)
        .flatMap { e -> healthAppKinds(e.id, e.label).map { HealthApp(e.id, e.label, it) } }
        .distinctBy { it.packageName to it.kind }
}.getOrDefault(emptyList())

/** Installed health apps, most used first (when usage access is on), then by name. Null while loading. */
@Composable
internal fun rememberHealthApps(): List<HealthApp>? {
    val context = LocalContext.current
    val choices by remember { HubAppChoices.state(context) }.collectAsState()
    val installed by produceState<List<HealthApp>?>(initialValue = null, choices) {
        value = withContext(Dispatchers.IO) { installedHealthApps(context) }
    }
    val opens = rememberAppOpenCounts()
    return remember(installed, opens) {
        installed?.sortedWith(compareByDescending<HealthApp> { opens[packageOfEntry(it.packageName)] ?: 0 }.thenBy { it.label.lowercase() })
    }
}

/** The apps the tile shows: the marked ones, or all when none is marked (or none marked is still installed). Pure. */
fun healthAppsOnTile(apps: List<HealthApp>, marks: Set<String>): List<HealthApp> {
    if (marks.isEmpty()) return apps
    return apps.filter { it.packageName in marks }.ifEmpty { apps }
}
