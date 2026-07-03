package com.tileshell.feature.system

import android.app.Activity
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
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/** Drives the Start-screen "update available" banner. */
enum class AppUpdateState { NONE, AVAILABLE, DOWNLOADING, READY_TO_INSTALL }

/**
 * Play Core in-app update check, flexible flow only — a launcher is the user's
 * Home, so it must never block behind a full-screen immediate-update takeover
 * the way a normal app could. Downloads happen silently in the background;
 * the banner only asks the user to restart once the download is done.
 * Re-checks on every `ON_RESUME` the same way [rememberNotificationAccess]
 * re-checks its permission, since Play can flag a new version mid-session.
 */
@Composable
fun rememberAppUpdateState(): Pair<AppUpdateState, () -> Unit> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appUpdateManager = remember { AppUpdateManagerFactory.create(context) }
    var state by remember { mutableStateOf(AppUpdateState.NONE) }

    val updateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { /* a cancelled/failed flow just leaves the banner up so the user can retry */ }

    fun refresh() {
        runCatching {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                state = when {
                    info.installStatus() == InstallStatus.DOWNLOADED -> AppUpdateState.READY_TO_INSTALL
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateState.AVAILABLE
                    else -> AppUpdateState.NONE
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val installListener = InstallStateUpdatedListener { installState ->
            state = when (installState.installStatus()) {
                InstallStatus.DOWNLOADED -> AppUpdateState.READY_TO_INSTALL
                InstallStatus.DOWNLOADING, InstallStatus.PENDING, InstallStatus.INSTALLING -> AppUpdateState.DOWNLOADING
                InstallStatus.FAILED, InstallStatus.CANCELED -> AppUpdateState.NONE
                else -> state
            }
        }
        runCatching { appUpdateManager.registerListener(installListener) }
        refresh()

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            runCatching { appUpdateManager.unregisterListener(installListener) }
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    fun act() {
        when (state) {
            AppUpdateState.READY_TO_INSTALL -> runCatching { appUpdateManager.completeUpdate() }
            AppUpdateState.AVAILABLE -> {
                if (context !is Activity) return
                appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                    if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                        runCatching {
                            appUpdateManager.startUpdateFlowForResult(
                                info,
                                updateLauncher,
                                AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE),
                            )
                        }
                    }
                }
            }
            else -> {}
        }
    }

    return state to ::act
}
