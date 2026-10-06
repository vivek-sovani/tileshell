package com.tileshell.feature.livetiles.money

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tileshell.feature.livetiles.rememberAppOpenCounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MoneyApp(val packageName: String, val label: String, val kind: MoneyAppKind)

/** Installed launcher apps that are payment/wallet or banking apps. */
internal fun installedMoneyApps(context: Context): List<MoneyApp> = runCatching {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(intent, 0)
        .mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val label = info.loadLabel(pm).toString()
            moneyAppKind(pkg, label)?.let { MoneyApp(pkg, label, it) }
        }
        .distinctBy { it.packageName }
}.getOrDefault(emptyList())

/** Installed money apps, most used first (when usage access is on), then by name. Null while loading. */
@Composable
internal fun rememberMoneyApps(): List<MoneyApp>? {
    val context = LocalContext.current
    val choices by remember { com.tileshell.feature.livetiles.HubAppChoices.state(context) }.collectAsState()
    val installed by produceState<List<MoneyApp>?>(initialValue = null, choices) {
        value = withContext(Dispatchers.IO) { installedMoneyApps(context) }
    }
    val opens = rememberAppOpenCounts()
    return remember(installed, opens) {
        installed?.sortedWith(compareByDescending<MoneyApp> { opens[it.packageName] ?: 0 }.thenBy { it.label.lowercase() })
    }
}
