package com.tileshell.feature.applist

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tileshell.core.data.AppEntry
import com.tileshell.core.data.AppLauncher
import com.tileshell.core.data.PinResult
import com.tileshell.core.design.LocalAccent
import com.tileshell.core.design.LocalColorTokens
import com.tileshell.core.design.TileIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
 * Rows show the app's real icon (no backing square) rather than the prototype's
 * monoline glyph, since arbitrary installed apps have no TileShell glyph (see
 * docs/DECISIONS.md S9); apps with no resolvable icon fall back to the monoline
 * "app" glyph on the list background.
 */
@Composable
fun AppListScreen(
    modifier: Modifier = Modifier,
    onPinned: () -> Unit = {},
    viewModel: AppListViewModel = viewModel(),
) {
    val apps by viewModel.filteredApps.collectAsStateWithLifecycle()
    val topApps by viewModel.topApps.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val accent = LocalAccent.current // global accent (FR-7, S17)
    val context = LocalContext.current

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var jumpOpen by remember { mutableStateOf(false) }

    // Pinning a row toasts and, on success, returns to Start (FR-5).
    val onPinnedState = rememberUpdatedState(onPinned)
    LaunchedEffect(viewModel) {
        viewModel.pinned.collect { (result, label) ->
            val message = when (result) {
                PinResult.PINNED -> "pinned $label"
                PinResult.ALREADY_ON_START -> "already on start"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            if (result == PinResult.PINNED) onPinnedState.value()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().displayCutoutPadding(),
        ) {
            SearchBar(query = query, onQueryChange = viewModel::setQuery)

            if (apps.isEmpty() && query.isNotBlank()) {
                Text(
                    "no apps found",
                    color = LocalColorTokens.current.fgDim,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 18.dp, top = 30.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    // "recent" section (recently-launched + newly-installed), only
                    // when not searching; sits above the alphabetical list.
                    if (topApps.isNotEmpty()) {
                        item(key = "recent-header") { SectionHeader("recent", accent) }
                        items(topApps, key = { "recent/${it.key}" }) { app ->
                            AppRow(
                                app = app,
                                onTap = { AppLauncher.launch(context, app.packageName, app.activityName) },
                                onPin = { viewModel.pin(app) },
                                onUninstall = { uninstallApp(context, app.packageName) },
                            )
                        }
                    }
                    itemsIndexed(
                        items = apps,
                        key = { _, app -> app.key },
                    ) { index, app ->
                        val newSection = index == 0 || apps[index - 1].letter != app.letter
                        if (newSection) LetterHeader(app.letter, accent) { jumpOpen = true }
                        AppRow(
                            app = app,
                            onTap = { AppLauncher.launch(context, app.packageName, app.activityName) },
                            onPin = { viewModel.pin(app) },
                            onUninstall = { uninstallApp(context, app.packageName) },
                        )
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
                    // Leading lazy items before the alphabetical list: the recent
                    // header + its rows (present only when not searching).
                    val lead = if (topApps.isNotEmpty()) 1 + topApps.size else 0
                    if (target >= 0) scope.launch { listState.scrollToItem(lead + target) }
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
            .background(LocalColorTokens.current.chip)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(TileIcons["search"], null, tint = LocalColorTokens.current.fgDim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(9.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("search apps", color = LocalColorTokens.current.fgDim, fontSize = 14.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = LocalColorTokens.current.fg, fontSize = 14.sp),
                cursorBrush = SolidColor(LocalColorTokens.current.fg),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A non-clickable section label (e.g. "recent"), styled like a letter header. */
@Composable
private fun SectionHeader(text: String, accent: Color) {
    Text(
        text = text.lowercase(),
        color = accent,
        fontSize = 30.sp,
        fontWeight = FontWeight.ExtraLight,
        modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp),
    )
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
private fun AppRow(
    app: AppEntry,
    onTap: () -> Unit,
    onPin: () -> Unit,
    onUninstall: () -> Unit,
) {
    // Long-press opens a WP-style context menu: pin the app to Start, or uninstall
    // it (the system uninstall dialog). A quick tap still launches.
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tapOrLongPress(onTap = onTap, onLongPress = { menuOpen = true })
                // TalkBack: launch on activate, with pin / uninstall as custom
                // actions (the sighted long-press menu isn't reachable otherwise).
                .clearAndSetSemantics {
                    contentDescription = app.label
                    role = Role.Button
                    onClick(label = "launch") { onTap(); true }
                    customActions = listOf(
                        CustomAccessibilityAction("pin to start") { onPin(); true },
                        CustomAccessibilityAction("uninstall") { onUninstall(); true },
                    )
                }
                .padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                val icon = rememberAppIcon(app.packageName, app.activityName)
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(40.dp),
                    )
                } else {
                    // No real icon: the monoline glyph on the list background (no square).
                    Icon(
                        TileIcons["app"], null,
                        tint = LocalColorTokens.current.fg,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(app.label, color = LocalColorTokens.current.fg, fontSize = 16.sp)
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("pin to start") },
                onClick = { menuOpen = false; onPin() },
            )
            DropdownMenuItem(
                text = { Text("uninstall") },
                onClick = { menuOpen = false; onUninstall() },
            )
        }
    }
}

/**
 * Launches the system uninstall dialog for [packageName] (the user confirms in the
 * platform UI). Uses `ACTION_DELETE` with a `package:` URI — the canonical, reliably
 * supported way to ask the system PackageInstaller to uninstall; the installer is
 * declared visible in the manifest `<queries>` so it resolves under Android 11+
 * package-visibility. Only on an outright failure does it fall back to the
 * deprecated `ACTION_UNINSTALL_PACKAGE`. The catalog updates live on removal via the
 * existing package-change observer.
 */
private fun uninstallApp(context: Context, packageName: String) {
    val uri = Uri.fromParts("package", packageName, null)
    val delete = Intent(Intent.ACTION_DELETE, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(delete) }.isSuccess) return

    @Suppress("DEPRECATION")
    val uninstall = Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(uninstall) }.isSuccess) return

    Toast.makeText(context, "couldn't open uninstall", Toast.LENGTH_SHORT).show()
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
            .background(LocalColorTokens.current.bg)
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
                            color = if (has) Color.White else LocalColorTokens.current.fgDim.copy(alpha = 0.3f),
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

/**
 * Tap-to-launch plus a 450 ms long-press that cancels if the finger moves more
 * than 7 px (CLAUDE.md normative; prototype `screens.js` app-list pin). Does not
 * consume the down, so vertical list scrolling still wins on a drag.
 */
private fun Modifier.tapOrLongPress(onTap: () -> Unit, onLongPress: () -> Unit): Modifier =
    pointerInput(onTap, onLongPress) {
        val slop = 7.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // true = released early (tap), false = moved past slop (cancel),
            // null = 450 ms elapsed still pressed (long-press fired).
            val outcome = withTimeoutOrNull(450L) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) return@withTimeoutOrNull true
                    if ((change.position - down.position).getDistance() > slop) {
                        return@withTimeoutOrNull false
                    }
                }
                @Suppress("UNREACHABLE_CODE") false
            }
            when (outcome) {
                null -> {
                    onLongPress()
                    waitForUpOrCancellation() // swallow the rest of the press
                }
                true -> onTap()
                false -> Unit // dragged → leave it to the scroll container
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
