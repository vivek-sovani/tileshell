package com.tileshell.feature.applist

import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tileshell.core.data.AppEntry
import com.tileshell.core.data.AppLauncher
import com.tileshell.core.design.DarkColorTokens
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The alphabetical app list (FR-5): a lazy list of every launchable app, with
 * lowercase letter section headers, an accent-backed icon and the app name per
 * row. Fed by [AppListViewModel] / AppCatalogRepository, so it updates live on
 * install/uninstall. Tapping a row launches the app.
 *
 * Rows show the app's real icon rather than the prototype's monoline glyph,
 * since arbitrary installed apps have no TileShell glyph (see docs/DECISIONS.md
 * S9); the accent square is kept as the backing.
 */
@Composable
fun AppListScreen(
    modifier: Modifier = Modifier,
    viewModel: AppListViewModel = viewModel(),
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val accent = TileAccents.Blue // theme accent arrives in S17

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        // Search bar (visual only; live filtering lands in S10).
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                .fillMaxWidth()
                .height(38.dp)
                .background(DarkColorTokens.chip)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(TileIcons["search"], null, tint = DarkColorTokens.fgDim, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(9.dp))
            Text("search apps", color = DarkColorTokens.fgDim, fontSize = 14.sp)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(
                items = apps,
                key = { _, app -> "${app.packageName}/${app.activityName}" },
            ) { index, app ->
                val newSection = index == 0 || apps[index - 1].letter != app.letter
                if (newSection) LetterHeader(app.letter, accent)
                AppRow(app, accent) {
                    AppLauncher.launch(context, app.packageName, app.activityName)
                }
            }
        }
    }
}

@Composable
private fun LetterHeader(letter: String, accent: Color) {
    Text(
        text = letter.lowercase(),
        color = accent,
        fontSize = 30.sp,
        fontWeight = FontWeight.ExtraLight,
        modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun AppRow(app: AppEntry, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(accent),
            contentAlignment = Alignment.Center,
        ) {
            val icon = rememberAppIcon(app.packageName, app.activityName)
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(30.dp),
                )
            } else {
                Icon(TileIcons["app"], null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(app.label, color = DarkColorTokens.fg, fontSize = 16.sp)
    }
}

/** Loads an app's launcher icon off the main thread, as an [ImageBitmap]. */
@Composable
private fun rememberAppIcon(packageName: String, activityName: String): ImageBitmap? {
    val context = LocalContext.current
    val state = produceState<ImageBitmap?>(null, packageName, activityName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getActivityIcon(ComponentName(packageName, activityName))
                    .toBitmap(width = 96, height = 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }
    return state.value
}
