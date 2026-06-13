package com.tileshell

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.tileshell.feature.start.GridSpikeScreen
import com.tileshell.feature.system.DefaultLauncher

/**
 * TileShell home-screen host. Declared in the manifest as a HOME/DEFAULT
 * launcher activity (singleTask), drawn edge-to-edge with transparent system
 * bars. On first run it prompts the user to make TileShell the default
 * launcher. S3 shows the dense-packing grid spike (60 dummy tiles); the S2
 * catalogue debug screen and the real Start screen arrive/return in S6.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status and navigation bars with transparent scrims.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            DefaultLauncherPromptOnFirstRun()
            GridSpikeScreen()
        }
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
