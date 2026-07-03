package com.tileshell.feature.start

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.feature.system.AppUpdateState

/**
 * Thin, non-blocking Play Store update prompt for Start. Unlike [FirstRunHint]
 * this never scrims the whole screen — a launcher is Home, and an update can
 * recur every session until the user acts, so it reads as a dismissible strip
 * rather than a takeover dialog. Dismissing only hides it until [state] changes
 * again (e.g. NONE→AVAILABLE on the next check, or AVAILABLE→READY_TO_INSTALL
 * once the background download finishes).
 */
@Composable
fun UpdateAvailableBanner(
    state: AppUpdateState,
    accent: Color,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(state) { dismissed = false }
    val visible = state != AppUpdateState.NONE && state != AppUpdateState.DOWNLOADING && !dismissed

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .background(Color(0xE61B1B22), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (state == AppUpdateState.READY_TO_INSTALL) {
                    "update downloaded — restart to finish"
                } else {
                    "a new version of tileshell is available"
                },
                color = Color(0xFFF6F6F8),
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (state == AppUpdateState.READY_TO_INSTALL) "restart" else "update",
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(start = 10.dp, end = 4.dp),
            )
            Text(
                text = "×",
                color = Color(0xFF8A8A96),
                fontSize = 16.sp,
                modifier = Modifier
                    .clickable { dismissed = true }
                    .padding(start = 8.dp),
            )
        }
    }
}
