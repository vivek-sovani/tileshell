package com.tileshell

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
import androidx.compose.ui.platform.LocalContext
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

        // Back on Start is a no-op (WP behaviour). When edit/overlays land
        // (S12/S16) they will be closed here before consuming the gesture.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )

        setContent {
            DefaultLauncherPromptOnFirstRun()
            StartScreen(viewModel = startViewModel)
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
        if (prefs.getBoolean("asked_default_launcher", false)) return@LaunchedEffect
        if (!DefaultLauncher.isDefault(context)) {
            DefaultLauncher.createPromptIntent(context)?.let { intent ->
                prefs.edit().putBoolean("asked_default_launcher", true).apply()
                launcher.launch(intent)
            }
        }
    }
}
