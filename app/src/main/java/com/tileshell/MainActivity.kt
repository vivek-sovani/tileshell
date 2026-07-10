package com.tileshell

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tileshell.feature.livetiles.WeatherRefreshWorker
import com.tileshell.feature.start.StartScreen
import com.tileshell.feature.start.StartViewModel
import com.tileshell.feature.system.DefaultLauncher

class MainActivity : ComponentActivity() {

    private val startViewModel: StartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        startViewModel.personalizeOpen.value -> startViewModel.closePersonalize()
                        startViewModel.expandedFolderId.value != null -> startViewModel.collapseFolder()
                        startViewModel.editMode.value -> startViewModel.exitEdit()
                        startViewModel.isAppList.value -> startViewModel.goHome()
                    }
                }
            },
        )

        setContent {
            DefaultLauncherPromptOnFirstRun()
            RequestRuntimePermissionsOnStart()
            val ctx = LocalContext.current
            var showLockDisclosure by remember { mutableStateOf(false) }
            var showRecentsDisclosure by remember { mutableStateOf(false) }

            StartScreen(
                viewModel = startViewModel,
                onRecents = {
                    if (!LockAccessibilityService.showRecents()) {
                        showRecentsDisclosure = true
                    }
                },
                onLockScreen = {
                    // If the accessibility service is already connected, lock immediately.
                    // Otherwise show the prominent disclosure required by Google Play before
                    // sending the user to Accessibility Settings.
                    if (LockAccessibilityService.isConnected()) {
                        lockScreen(ctx)
                    } else {
                        showLockDisclosure = true
                    }
                },
            )

            if (showLockDisclosure) {
                AccessibilityDisclosureDialog(
                    onConfirm = {
                        showLockDisclosure = false
                        lockScreen(ctx)
                    },
                    onDismiss = { showLockDisclosure = false },
                )
            }
            if (showRecentsDisclosure) {
                AccessibilityDisclosureDialog(
                    onConfirm = {
                        showRecentsDisclosure = false
                        openAccessibilitySettings(ctx)
                    },
                    onDismiss = { showRecentsDisclosure = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startViewModel.goHome()
        // Dismiss the keyboard when returning to Start via the Home button.
        // The search field in the app list / feed retains IME focus after
        // goHome() snaps the pager back, leaving the keyboard open on Start.
        currentFocus?.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }
}

/**
 * Prominent disclosure dialog shown before directing the user to enable
 * TileShell's Accessibility Service. Required by Google Play policy for apps
 * that declare an accessibility service.
 *
 * Used for both screen-lock (gear long-press) and recent-apps (edge strip)
 * since both rely on the same single Accessibility Service.
 */
@Composable
private fun AccessibilityDisclosureDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable accessibility service") },
        text = {
            Text(
                "TileShell uses an Accessibility Service to lock your screen " +
                "(long-press the settings icon) and to show the recent apps screen " +
                "(edge strip).\n\n" +
                "This service does not read, collect, or transmit any data from your " +
                "screen, apps, or keystrokes. It performs these two actions only.\n\n" +
                "Tap \"Go to Settings\" to enable the TileShell Accessibility Service, " +
                "then return here.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Go to Settings") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}

private fun openAccessibilitySettings(context: Context) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
private fun DefaultLauncherPromptOnFirstRun() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* outcome read via DefaultLauncher.isDefault when needed */ }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("tileshell.prefs", Context.MODE_PRIVATE)
        if (DefaultLauncher.isDefault(context)) return@LaunchedEffect
        if (prefs.getBoolean("asked_default_launcher", false)) return@LaunchedEffect
        val intent = DefaultLauncher.createPromptIntent(context) ?: return@LaunchedEffect
        prefs.edit().putBoolean("asked_default_launcher", true).apply()
        runCatching { launcher.launch(intent) }
    }
}

@Composable
private fun RequestRuntimePermissionsOnStart() {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            WeatherRefreshWorker.refreshNow(context)
        }
    }

    LaunchedEffect(Unit) {
        if (!asked) {
            asked = true
            launcher.launch(
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }
}
