package com.tileshell.feature.applist

import android.content.ComponentName
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Jump-grid cells: "#" bucket then a–z, matching the prototype's order. */
private val JUMP_LETTERS: List<String> = listOf("#") + ('a'..'z').map(Char::toString)

/**
 * The alphabetical app list (FR-5): a lazy list of every launchable app, with
 * lowercase letter section headers, an accent-backed icon and the app name per
 * row. Fed by [AppListViewModel] / AppCatalogRepository, so it updates live on
 * install/uninstall. Tapping a row launches the app.
 *
 * Search (S10) filters live as you type; tapping a section header opens the
 * jump grid — a 4-column board of "#" + a–z where present letters are accent
 * tiles that scroll to their section (prototype `screens.js` buildJump/jumpTo).
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
    val apps by viewModel.filteredApps.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val accent = TileAccents.Blue // theme accent arrives in S17
    val context = LocalContext.current

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var jumpOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            SearchBar(query = query, onQueryChange = viewModel::setQuery)

            if (apps.isEmpty() && query.isNotBlank()) {
                Text(
                    "no apps found",
                    color = DarkColorTokens.fgDim,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 18.dp, top = 30.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    itemsIndexed(
                        items = apps,
                        key = { _, app -> "${app.packageName}/${app.activityName}" },
                    ) { index, app ->
                        val newSection = index == 0 || apps[index - 1].letter != app.letter
                        if (newSection) LetterHeader(app.letter, accent) { jumpOpen = true }
                        AppRow(app, accent) {
                            AppLauncher.launch(context, app.packageName, app.activityName)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = jumpOpen, enter = fadeIn(), exit = fadeOut()) {
            JumpGrid(
                present = remember(apps) { AppListFilter.availableLetters(apps) },
                accent = accent,
                onPick = { letter ->
                    jumpOpen = false
                    val target = apps.indexOfFirst { it.letter == letter }
                    if (target >= 0) scope.launch { listState.scrollToItem(target) }
                },
                onDismiss = { jumpOpen = false },
            )
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
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
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("search apps", color = DarkColorTokens.fgDim, fontSize = 14.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = DarkColorTokens.fg, fontSize = 14.sp),
                cursorBrush = SolidColor(DarkColorTokens.fg),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LetterHeader(letter: String, accent: Color, onClick: () -> Unit) {
    Text(
        text = letter.lowercase(),
        color = accent,
        fontSize = 30.sp,
        fontWeight = FontWeight.ExtraLight,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(start = 18.dp, top = 14.dp, bottom = 4.dp),
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

/**
 * Full-screen jump board: 4 columns of "#" + a–z. Letters present in the list
 * are accent tiles that scroll to their section; absent ones are dimmed and
 * dismiss the grid on tap (prototype `.jt.has` / `.jt.off`).
 */
@Composable
private fun JumpGrid(
    present: Set<String>,
    accent: Color,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkColorTokens.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        JUMP_LETTERS.chunked(4).forEach { rowLetters ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowLetters.forEach { letter ->
                    val key = letter.uppercase() // "#" stays "#"
                    val has = key in present
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(if (has) accent else Color.Transparent)
                            .clickable { if (has) onPick(key) else onDismiss() }
                            .padding(8.dp),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Text(
                            text = letter,
                            color = if (has) Color.White else DarkColorTokens.fgDim.copy(alpha = 0.3f),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraLight,
                        )
                    }
                }
                // Pad the final short row so cells keep their column width.
                repeat(4 - rowLetters.size) { Spacer(Modifier.weight(1f)) }
            }
        }
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
