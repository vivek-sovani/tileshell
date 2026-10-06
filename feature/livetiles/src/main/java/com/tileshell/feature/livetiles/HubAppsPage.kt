package com.tileshell.feature.livetiles

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tileshell.core.design.ColorTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** One app on a hub's apps page and the section it is in. */
internal data class HubPageApp(
    val packageName: String,
    val label: String,
    val section: String,
    val badge: Int = 0,
)

private data class HubGridSection(val key: String, val title: String, val apps: List<HubPageApp>)

private const val COLUMNS = 4

/**
 * A hub's apps page (People, Money, Productivity): sections of app icons in a
 * four-column grid. While [editing], hold an app and drag it to another
 * section, tap its ✕ to take it off the page (it stays installed), or add
 * apps from "+" at the end of a section. The choices are kept by
 * [HubAppChoices], so they also change what the hub treats as its apps
 * (notifications, the pinned apps tile).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HubAppsPage(
    kind: HubKind,
    sectionDefs: List<Pair<String, String>>,
    apps: List<HubPageApp>,
    editing: Boolean,
    tokens: ColorTokens,
    accent: Color,
    emptyText: String,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    longPressLabel: String? = null,
    onLongPress: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val choices by remember { HubAppChoices.state(context) }.collectAsState()
    val choice = choices[kind] ?: HubAppChoice()
    val sections = remember(sectionDefs, apps) {
        sectionDefs.map { (key, title) -> HubGridSection(key, title, apps.filter { it.section == key }) }
    }
    var addTarget by remember { mutableStateOf<String?>(null) }

    val scroll = rememberScrollState()
    var dragPackage by remember { mutableStateOf<String?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var containerBounds by remember { mutableStateOf(Rect.Zero) }
    val sectionBounds = remember { mutableStateMapOf<String, Rect>() }
    val density = LocalDensity.current

    fun sectionUnder(p: Offset): String? = sections.firstOrNull { sectionBounds[it.key]?.contains(p) == true }?.key

    fun finishDrag() {
        val pkg = dragPackage
        if (pkg != null) {
            val target = sectionUnder(dragPosition)
            val current = apps.firstOrNull { it.packageName == pkg }?.section
            if (target != null && target != current) {
                HubAppChoices.update(context, kind) { it.place(pkg, target) }
            }
        }
        dragPackage = null
    }

    // Scrolls the page while a drag is held near its top or bottom edge.
    LaunchedEffect(dragPackage) {
        val edge = with(density) { 90.dp.toPx() }
        while (dragPackage != null) {
            val y = dragPosition.y
            if (y < containerBounds.top + edge) scroll.scrollBy(-16f)
            else if (y > containerBounds.bottom - edge) scroll.scrollBy(16f)
            delay(16)
        }
    }

    Box(modifier = modifier.fillMaxSize().onGloballyPositioned { containerBounds = it.boundsInRoot() }) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 12.dp).padding(bottom = 32.dp),
        ) {
            header()
            if (editing) {
                Text(
                    "hold an app and drag it to another section · ✕ takes it off this page",
                    color = tokens.fgDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 2.dp),
                )
            } else if (apps.isEmpty()) {
                Text(emptyText, color = tokens.fgDim, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 24.dp))
            }
            sections.forEach { section ->
                if (section.apps.isEmpty() && !editing) return@forEach
                val target = dragPackage != null && sectionUnder(dragPosition) == section.key
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .onGloballyPositioned { sectionBounds[section.key] = it.boundsInRoot() }
                        .then(if (target) Modifier.border(1.dp, accent) else Modifier),
                ) {
                    Text(
                        section.title,
                        color = tokens.fgDim,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 6.dp, top = 8.dp),
                    )
                    val cells: List<HubPageApp?> = section.apps + if (editing) listOf(null) else emptyList()
                    cells.chunked(COLUMNS).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            row.forEach { app ->
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    if (app == null) {
                                        AddCell(tokens, accent) { addTarget = section.key }
                                    } else {
                                        AppCell(
                                            app = app,
                                            editing = editing,
                                            dragging = dragPackage == app.packageName,
                                            tokens = tokens,
                                            accent = accent,
                                            longPressLabel = longPressLabel,
                                            onOpen = { onOpen(app.packageName) },
                                            onLongPress = { onLongPress(app.packageName) },
                                            onDrop = { HubAppChoices.update(context, kind) { it.drop(app.packageName) } },
                                            onDragStart = { rootPosition -> dragPackage = app.packageName; dragPosition = rootPosition },
                                            onDragBy = { dragPosition += it },
                                            onDragEnd = ::finishDrag,
                                            onDragCancel = { dragPackage = null },
                                        )
                                    }
                                }
                            }
                            repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (editing) {
                if (choice.dropped.isNotEmpty()) {
                    val n = choice.dropped.size
                    Text(
                        "$n app${if (n == 1) "" else "s"} taken off this page · show again",
                        color = accent,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { HubAppChoices.update(context, kind) { it.restoreDropped() } }
                            .padding(start = 6.dp, top = 16.dp),
                    )
                }
                if (!choice.isEmpty) {
                    Text(
                        "reset this page to defaults",
                        color = accent,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { HubAppChoices.update(context, kind) { HubAppChoice() } }
                            .padding(start = 6.dp, top = 10.dp),
                    )
                }
            }
        }

        // The icon under the finger while dragging.
        dragPackage?.let { pkg ->
            val icon = rememberAppIconBitmap(pkg, sizePx = iconPx(48.dp))
            val half = with(density) { 24.dp.toPx() }
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (dragPosition.x - containerBounds.left - half).roundToInt(),
                                (dragPosition.y - containerBounds.top - half).roundToInt(),
                            )
                        }
                        .size(48.dp),
                )
            }
        }
    }

    addTarget?.let { start ->
        AddHubAppsDialog(
            sectionDefs = sectionDefs,
            initialSection = start,
            existing = apps.map { it.packageName }.toSet(),
            tokens = tokens,
            accent = accent,
            onDismiss = { addTarget = null },
            onAdd = { packages, section ->
                HubAppChoices.update(context, kind) { c -> packages.fold(c) { acc, pkg -> acc.place(pkg, section) } }
                addTarget = null
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(
    app: HubPageApp,
    editing: Boolean,
    dragging: Boolean,
    tokens: ColorTokens,
    accent: Color,
    longPressLabel: String?,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onDrop: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val icon = rememberAppIconBitmap(app.packageName, sizePx = iconPx(40.dp))
    var menuOpen by remember { mutableStateOf(false) }
    var topLeft by remember { mutableStateOf(Offset.Zero) }
    if (longPressLabel != null) {
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text(longPressLabel) }, onClick = {
                menuOpen = false
                onLongPress()
            })
        }
    }
    Column(
        modifier = Modifier
            .padding(4.dp)
            .alpha(if (dragging) 0.3f else 1f)
            .onGloballyPositioned { topLeft = it.boundsInRoot().topLeft }
            .then(
                if (editing) {
                    Modifier.pointerInput(app.packageName) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset -> onDragStart(topLeft + offset) },
                            onDrag = { change, amount ->
                                change.consume()
                                onDragBy(amount)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                        )
                    }
                } else {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpen,
                        onLongClick = if (longPressLabel != null) ({ menuOpen = true }) else null,
                    )
                },
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
            if (app.badge > 0 && !editing) {
                Text(
                    text = if (app.badge > 99) "99+" else app.badge.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent)
                        .padding(horizontal = 5.dp),
                )
            }
            if (editing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD6262B))
                        .clickable(onClick = onDrop),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", color = Color.White, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(app.label.lowercase(), color = tokens.fg, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AddCell(tokens: ColorTokens, accent: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.padding(4.dp).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(40.dp).border(1.dp, accent, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("+", color = accent, fontSize = 22.sp) }
        }
        Spacer(Modifier.height(3.dp))
        Text("add", color = accent, fontSize = 11.sp)
    }
}

/** Installed launcher apps (package to label), without TileShell itself, by name. */
internal fun installedLauncherApps(context: android.content.Context): List<Pair<String, String>> = runCatching {
    val pm = context.packageManager
    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) null else pkg to info.loadLabel(pm).toString()
        }
        .distinctBy { it.first }
        .sortedBy { it.second.lowercase() }
}.getOrDefault(emptyList())

/** Pick installed apps, choose the section, add them to the hub's apps page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddHubAppsDialog(
    sectionDefs: List<Pair<String, String>>,
    initialSection: String,
    existing: Set<String>,
    tokens: ColorTokens,
    accent: Color,
    onDismiss: () -> Unit,
    onAdd: (List<String>, String) -> Unit,
) {
    val context = LocalContext.current
    val installed by produceState<List<Pair<String, String>>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { installedLauncherApps(context) }
    }
    var query by remember { mutableStateOf("") }
    var section by remember { mutableStateOf(initialSection) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val shown = remember(installed, query, existing) {
        installed.orEmpty().filter { (pkg, label) -> pkg !in existing && label.contains(query.trim(), ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.82f)
                .background(tokens.sheet, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text(
                "add apps to ${sectionDefs.firstOrNull { it.first == section }?.second ?: ""}",
                color = tokens.fg,
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = tokens.fg, fontSize = 14.sp),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth().background(tokens.chip).padding(horizontal = 12.dp, vertical = 9.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("search your apps", color = tokens.fgDim, fontSize = 14.sp)
                    inner()
                },
            )
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (installed == null) {
                    Text("loading apps…", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                } else if (shown.isEmpty()) {
                    Text("no more apps to add", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(shown, key = { it.first }) { (pkg, label) ->
                            val on = pkg in picked
                            val icon = rememberAppIconBitmap(pkg, sizePx = iconPx(32.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { picked = if (on) picked - pkg else picked + pkg }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .border(1.dp, if (on) accent else tokens.fgDim)
                                        .background(if (on) accent else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) { if (on) Text("✓", color = Color.White, fontSize = 13.sp) }
                                Spacer(Modifier.width(12.dp))
                                if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(32.dp)) else Spacer(Modifier.size(32.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(label, color = tokens.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("section", color = tokens.fgDim, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                sectionDefs.forEach { (key, title) ->
                    val on = key == section
                    Text(
                        title,
                        color = if (on) Color.White else tokens.fg,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .background(if (on) accent else tokens.fg.copy(alpha = 0.1f))
                            .clickable { section = key }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("cancel", color = tokens.fgDim, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (picked.isEmpty()) "add" else "add ${picked.size} app${if (picked.size == 1) "" else "s"}",
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            if (picked.isEmpty()) {
                                Toast.makeText(context, "tick the apps to add", Toast.LENGTH_SHORT).show()
                            } else {
                                onAdd(picked.toList(), section)
                            }
                        }
                        .padding(8.dp),
                )
            }
        }
    }
}
