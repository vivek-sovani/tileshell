package com.tileshell.feature.livetiles

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

/**
 * Tracks whether a single runtime [permission] is granted and requests it once,
 * the first time the live tile that needs it composes (the WP-style opt-in: the
 * tile asks for what it shows). [onGranted] fires after a grant — used to kick a
 * weather refresh the moment location is allowed.
 *
 * The one-shot ask is remembered across recomposition (and config change via
 * [rememberSaveable]) so the dialog is not re-raised every frame; a denial
 * leaves the tile degraded to static until the next process where it asks again.
 */
@Composable
fun rememberOptInPermission(
    permission: String,
    onGranted: () -> Unit = {},
): Boolean {
    val context = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(context, permission) ==
        PackageManager.PERMISSION_GRANTED

    var isGranted by remember { mutableStateOf(granted()) }
    var asked by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        isGranted = result
        if (result) onGranted()
    }

    LaunchedEffect(Unit) {
        if (!isGranted && !asked) {
            asked = true
            launcher.launch(permission)
        }
    }
    return isGranted
}
