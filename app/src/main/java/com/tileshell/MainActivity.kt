package com.tileshell

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tileshell.feature.livetiles.WeatherRefreshWorker
import com.tileshell.feature.start.StartScreen
import com.tileshell.feature.start.StartViewModel
import com.tileshell.feature.system.DefaultLauncher

/**
 * TileShell home-screen host. Declared in the manifest as a HOME/DEFAULT
 * launcher activity (singleTask), drawn edge-to-edge with transparent system
 * bars. On first run it prompts the user to make TileShell the default
 * launcher, then shows the real Start screen (persisted tiles over the aurora
 * wallpaper).
 */
class MainActivity : ComponentActivity() {

    // Activity-scoped; the same instance is used by StartScreen's composable.
    private val startViewModel: StartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status and navigation bars with transparent scrims.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Back closes the personalize sheet first, then the folder overlay, then
        // leaves edit mode, then returns from the App-list page to Start; on a
        // plain Start screen it is a no-op (WP behaviour).
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        startViewModel.personalizeOpen.value -> startViewModel.closePersonalize()
                        startViewModel.openFolderId.value != null -> startViewModel.closeFolder()
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
            StartScreen(viewModel = startViewModel, onLockScreen = { lockScreen(ctx) })
        }
    }

    /**
     * Home pressed while TileShell is already foreground (singleTask delivers a
     * fresh HOME intent here): close overlays/edit and scroll Start to the top.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startViewModel.goHome()
    }
}

/**
 * Fires the RoleManager-backed "set default launcher" prompt exactly once,
 * on first run, unless TileShell already holds the HOME role. Declining is
 * respected — we never re-prompt automatically.
 */
@Composable
private fun DefaultLauncherPromptOnFirstRun() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* outcome read via DefaultLauncher.isDefault when needed */ }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("tileshell.prefs", Context.MODE_PRIVATE)
        // Re-check the role every launch: already default → never prompt (even if
        // we never recorded asking, e.g. the user set us default from system
        // settings). Otherwise ask exactly once and respect a decline.
        if (DefaultLauncher.isDefault(context)) return@LaunchedEffect
        if (prefs.getBoolean("asked_default_launcher", false)) return@LaunchedEffect
        val intent = DefaultLauncher.createPromptIntent(context) ?: return@LaunchedEffect
        // Record the ask before launching so a process death mid-dialog can't
        // make us re-prompt; only flip it when a prompt actually fires.
        prefs.edit().putBoolean("asked_default_launcher", true).apply()
        runCatching { launcher.launch(intent) }
    }
}

/**
 * Requests all runtime permissions TileShell needs (calendar, location, contacts)
 * upfront on app start so the user is asked once in a single system dialog rather
 * than separately when each live tile first appears. The one-shot guard uses
 * [rememberSaveable] so it fires once per process; Android handles permanent
 * denial silently (no dialog shown). When location is granted the weather worker
 * is kicked immediately so the weather tile can show data on the first open.
 */
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
