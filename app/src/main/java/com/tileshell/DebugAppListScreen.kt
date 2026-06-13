package com.tileshell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.data.AppCatalogRepository
import com.tileshell.core.data.AppEntry
import kotlinx.coroutines.flow.flowOf

// Dark prototype tokens (design/.../launcher/styles.css).
private val ScreenBg = Color(0xFF0A0A0D)
private val ScreenFg = Color(0xFFF6F6F8)
private val ScreenFgDim = Color(0x9EF6F6F8)

/**
 * Temporary S2 debug screen: proves [AppCatalogRepository] enumerates real
 * installed apps and updates live on install / uninstall. Replaced by the real
 * Start screen in S6.
 */
@Composable
fun DebugAppListScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { AppCatalogRepository(context) }
    val apps by repo.apps.collectAsStateWithLifecycle(initialValue = emptyList())

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Column(modifier = Modifier.padding(20.dp, 48.dp, 20.dp, 12.dp)) {
                    Text("app catalogue (debug)", ScreenFg, 24.sp, FontWeight.ExtraLight)
                    Text("${apps.size} launchable apps", ScreenFgDim, 13.sp, FontWeight.Normal)
                }
            }
            items(apps, key = { "${it.packageName}/${it.activityName}" }) { app ->
                AppRow(app)
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(app.label, ScreenFg, 16.sp, FontWeight.Normal)
        Text("[${app.letter}]  ${app.packageName}", ScreenFgDim, 12.sp, FontWeight.Normal)
    }
}

@Composable
private fun Text(
    text: String,
    color: Color,
    size: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
) = androidx.compose.material3.Text(
    text = text,
    color = color,
    fontSize = size,
    fontWeight = weight,
)
