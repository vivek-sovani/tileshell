package com.tileshell.feature.livetiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.ColorTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * The line a hub shows above its app bar for a couple of seconds after "pin to
 * start" ("pinned stocks to start" / "already on start"), so the result is seen
 * while the hub still covers Start; a system toast alone is easy to miss there.
 */
@Composable
fun HubPinNote(messages: Flow<String>, tokens: ColorTokens, accent: Color) {
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(messages) {
        messages.collect {
            note = it
            delay(2_800)
            if (note == it) note = null
        }
    }
    note?.let { text ->
        Box(modifier = Modifier.fillMaxWidth().background(accent).padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Light)
        }
    }
}
