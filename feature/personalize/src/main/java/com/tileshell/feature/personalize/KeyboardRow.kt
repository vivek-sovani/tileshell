package com.tileshell.feature.personalize

import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tileshell.core.data.KeyboardFeature
import com.tileshell.core.design.ColorTokens

/**
 * "tileshell keyboard" in personalize → system, only in builds with the
 * keyboard switched on ([KeyboardFeature.ENABLED]). Walks through the two steps
 * Android requires: turn it on in keyboard settings, then choose it. Re-checked
 * on every resume, so coming back from Settings updates the row.
 */
@Composable
internal fun KeyboardRow(accent: Color, tokens: ColorTokens) {
    if (!KeyboardFeature.ENABLED) return
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(KeyboardFeature.isEnabled(context)) }
    var selected by remember { mutableStateOf(KeyboardFeature.isSelected(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = KeyboardFeature.isEnabled(context)
                selected = KeyboardFeature.isSelected(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val (subtitle, action) = when {
        !enabled -> "metro-style keyboard in your accent colour — turn it on first" to "turn on ›"
        !selected -> "turned on — choose it as the keyboard you type with" to "choose ›"
        else -> "in use · uses your accent colour and theme" to "change ›"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    if (!enabled) {
                        context.startActivity(
                            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } else {
                        context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
                    }
                }
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "tileshell keyboard", color = tokens.fg, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                // Still maturing (Marathi/Hindi typing especially), so it's labelled beta.
                Text(
                    text = "BETA",
                    color = accent,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .border(1.dp, accent)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Text(text = subtitle, color = tokens.fgDim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(text = action, color = accent, fontSize = 13.sp)
    }
}
