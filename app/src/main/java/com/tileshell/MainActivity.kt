package com.tileshell

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.tileshell.feature.system.DefaultLauncher

// Prototype theme tokens (design/.../launcher/styles.css): --bg / --fg, dark.
private val ScreenBg = Color(0xFF0A0A0D)
private val ScreenFg = Color(0xFFF6F6F8)

/**
 * TileShell home-screen host. Declared in the manifest as a HOME/DEFAULT
 * launcher activity (singleTask), drawn edge-to-edge with transparent system
 * bars. For S1 it shows a placeholder dark screen and, on first run, prompts
 * the user to make TileShell the default launcher.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status and navigation bars with transparent scrims.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            DefaultLauncherPromptOnFirstRun()
            PlaceholderHome()
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

@Composable
private fun PlaceholderHome() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg),
        contentAlignment = Alignment.Center,
    ) {
        // WP-style thin lowercase wordmark, per the prototype's heading style
        // (font-weight 200, letter-spacing -1px).
        Text(
            text = "tileshell",
            color = ScreenFg,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-1).sp,
        )
    }
}

@Preview(widthDp = 393, heightDp = 856)
@Composable
private fun PlaceholderHomePreview() {
    PlaceholderHome()
}
