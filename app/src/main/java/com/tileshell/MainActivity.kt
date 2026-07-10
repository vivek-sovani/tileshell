package com.tileshell

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tileshell.feature.livetiles.WeatherRefreshWorker
import com.tileshell.feature.start.StartScreen
import com.tileshell.feature.start.StartViewModel
import com.tileshell.feature.system.DefaultLauncher

private const val PRIVACY_POLICY_URL = "https://vivek-sovani.github.io/tileshell/"

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
 * that declare an accessibility service — and, per a Play Console rejection
 * ("Accessibility API policy: Insufficient data use declaration in the
 * prominent disclosure"), the disclosure must spell out *all* data the app
 * collects anywhere, not just what the accessibility service itself touches
 * (the service only ever calls `performGlobalAction`; it never reads screen
 * content). The itemized list below mirrors `docs/PRIVACY_POLICY.md` /
 * [PRIVACY_POLICY_URL], condensed to the data types Play's reviewer actually
 * flagged: location, calendar, contacts, the installed-apps list, and the
 * locally-tracked "recent apps" tap history ("page views and taps in app").
 *
 * Used for both screen-lock (gear long-press) and recent-apps (edge strip)
 * since both rely on the same single Accessibility Service.
 */
@Composable
private fun AccessibilityDisclosureDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Before you enable accessibility") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "TileShell uses Android's Accessibility Service for one narrow purpose: " +
                    "locking the screen (long-press the settings icon) and opening recent apps " +
                    "(edge strip). The service itself never reads your screen content, other " +
                    "apps, or keystrokes — it only issues those two system actions.\n\n" +
                    "Separately from Accessibility, and only if you grant each permission, " +
                    "TileShell also accesses:\n\n" +
                    "• Approximate location — to show local weather on the Weather tile. Sent " +
                    "to Open-Meteo (a weather API) as coordinates only; never precise/GPS-level " +
                    "location.\n\n" +
                    "• Calendar events — to show your next events on the Calendar tile. Stays " +
                    "on this device.\n\n" +
                    "• Contacts (names and photos) — for the People tile and Quick Search. " +
                    "Stays on this device.\n\n" +
                    "• Notification content — to show badges and message previews on live " +
                    "tiles, if you enable notification access. Stays on this device.\n\n" +
                    "• Installed apps — TileShell reads your app list to display and launch " +
                    "them, as any home-screen launcher must. Stays on this device.\n\n" +
                    "• Which apps you tap — remembered locally to power the \"recent\" section " +
                    "of the App List and Quick Search. Never leaves this device.\n\n" +
                    "TileShell has no analytics or ad SDKs, no account system, and never sells " +
                    "or shares this data. Full privacy policy: $PRIVACY_POLICY_URL\n\n" +
                    "Tap \"Go to Settings\" to enable the TileShell Accessibility Service, " +
                    "then return here.",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Go to Settings") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text("Privacy policy") }
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
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
