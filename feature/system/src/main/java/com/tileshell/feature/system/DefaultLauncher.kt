package com.tileshell.feature.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Helpers for the "set TileShell as default launcher" flow.
 *
 * API 29+ uses [RoleManager] (system dialog with a direct choice). API 26–28
 * has no role API, so we fall back to the Home settings screen.
 */
object DefaultLauncher {

    /** True when TileShell is the device's current default HOME app. */
    fun isDefault(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        // Pre-Q (or no role): resolve the preferred HOME activity. Not
        // authoritative when several launchers exist and none is preferred,
        // but the worst case is one redundant prompt.
        val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(probe, 0)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /**
     * Intent that asks the user to make TileShell the default launcher, or
     * null when there is nothing to ask (already default, or role unavailable
     * and no settings screen to open).
     */
    fun createPromptIntent(context: Context): Intent? {
        if (isDefault(context)) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
            return null
        }

        // API 26–28: open the system Home-app picker in Settings.
        return homeSettingsIntent(context)
    }

    /** The universal "Default apps · Home app" Settings screen, or null if none resolves. */
    fun homeSettingsIntent(context: Context): Intent? {
        val settings = Intent(Settings.ACTION_HOME_SETTINGS)
        return settings.takeIf { it.resolveActivity(context.packageManager) != null }
    }
}

/**
 * Live "is TileShell the default launcher" flag plus a trigger for
 * Personalize's manual "set as default launcher" row, paired the same way
 * [rememberAppUpdateState] pairs its state with its action. [isDefault] is
 * re-checked on every `ON_RESUME` (same pattern as [rememberNotificationAccess]),
 * since the user can back out to Settings, change the default there, and return
 * — plus immediately after the prompt closes, via
 * [ActivityResultContracts.StartActivityForResult], so the row disappears
 * right away on a successful switch instead of waiting for the next resume.
 *
 * The trigger only falls back to the universal "Default apps · Home app"
 * Settings screen when [DefaultLauncher.createPromptIntent] itself has
 * nothing to offer (role unavailable, no settings screen resolves either) —
 * never just because the user closed the dialog without picking TileShell.
 * That distinction matters: the dialog can't tell "declined" apart from
 * "silently failed," so treating any non-switch as a failure and immediately
 * reopening Settings turned one dismissal into two dialogs in a row.
 */
@Composable
fun rememberDefaultLauncherState(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isDefault by remember { mutableStateOf(DefaultLauncher.isDefault(context)) }

    fun refresh() {
        isDefault = DefaultLauncher.isDefault(context)
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refresh() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val request: () -> Unit = {
        val intent = DefaultLauncher.createPromptIntent(context)
        if (intent != null) {
            runCatching { roleLauncher.launch(intent) }
                .onFailure { DefaultLauncher.homeSettingsIntent(context)?.let { runCatching { context.startActivity(it) } } }
        } else {
            DefaultLauncher.homeSettingsIntent(context)?.let { runCatching { context.startActivity(it) } }
        }
    }

    return isDefault to request
}
