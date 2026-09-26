package com.tileshell.feature.livetiles

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.NoteItem
import com.tileshell.core.data.NoteRepository
import com.tileshell.core.data.TaskListSummary
import com.tileshell.core.data.TaskRepository
import com.tileshell.core.data.preview
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PRODUCTIVITY_PIVOTS = listOf("today", "notes", "tasks", "apps")

// Note-card colours, cycled by note id so a note keeps its colour.
private val NOTE_CARD_COLORS = listOf("amber", "lime", "cyan", "magenta", "orange", "purple")

/**
 * Full-screen productivity hub (user-requested, designed first with
 * mockups): "today" (next meeting with a join button when the event has a
 * Meet/Zoom/Teams/Webex link, open tasks across every list, the latest note,
 * quick actions), "notes" (every TileShell note, including sticky notes, as
 * coloured cards), "tasks" (every named task list), and "apps" (installed
 * office / notes and files / meetings / tools apps, most used first). Same WP
 * Panorama/Pivot shell as the people and music hubs.
 *
 * Editing notes and task lists stays in the existing sheets, which live in
 * `:feature:personalize` (this module can't depend on it), so the hub asks
 * Start to open them via [onOpenNote]/[onNewNote]/[onOpenTaskList]. Pinning
 * goes through Start too: [onPinNote] (a note as a sticky note tile),
 * [onPinTaskList] (a list as a Tasks tile), [onPinNotepad] (the notepad tile),
 * [onPinHub] (this hub's own tile). [pinnedNoteIds]/[pinnedListIds] mark
 * what's already on Start.
 */
@Composable
fun ProductivityHubScreen(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    onOpenNote: (noteId: Long) -> Unit,
    onNewNote: () -> Unit,
    onOpenTaskList: (listId: String) -> Unit,
    onPinNote: (noteId: Long) -> Unit,
    onPinTaskList: (listId: String) -> Unit,
    onPinNotepad: () -> Unit,
    onPinHub: () -> Unit,
    pinnedNoteIds: Set<Long>,
    pinnedListIds: Set<String>,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
    initialPage: String? = null,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "productivityHubProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tasksRepo = remember(context) { TaskRepository.create(context) }
    val notesRepo = remember(context) { NoteRepository.create(context) }
    val notes by notesRepo.notes.collectAsState(initial = emptyList())

    BackHandler(enabled = visible) { onDismiss() }

    val pagerState = rememberPagerState(pageCount = { PRODUCTIVITY_PIVOTS.size })
    val pagerScope = rememberCoroutineScope()
    LaunchedEffect(visible, initialPage) {
        if (visible && initialPage != null) {
            val index = PRODUCTIVITY_PIVOTS.indexOf(initialPage)
            if (index >= 0) pagerState.scrollToPage(index)
        }
    }

    val lists by remember { tasksRepo.lists() }.collectAsState(initial = emptyList())

    // The quick row, customisable (user-requested): the four built-ins by
    // default, plus any list / note / app pinned to it; long-press removes.
    var quickItems by remember { mutableStateOf(loadQuickItems(context)) }
    val setQuick: (List<QuickItem>) -> Unit = {
        quickItems = it
        saveQuickItems(context, it)
    }
    val addToQuick: (QuickItem) -> Unit = { item ->
        if (item in quickItems) {
            android.widget.Toast.makeText(context, "already in quick", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            setQuick(addQuickItem(quickItems, item))
            android.widget.Toast.makeText(context, "added to quick", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // "new task": straight into the only list, a new one when there are
    // none, or a picker when there are several (user-requested).
    var listPickerOpen by remember { mutableStateOf(false) }
    val newTask: () -> Unit = {
        when (lists.size) {
            0 -> scope.launch { onOpenTaskList(tasksRepo.createList("tasks")) }
            1 -> onOpenTaskList(lists.first().id)
            else -> listPickerOpen = true
        }
    }
    if (listPickerOpen) {
        TaskListPicker(
            lists = lists,
            onPick = { id ->
                listPickerOpen = false
                onOpenTaskList(id)
            },
            onNewList = {
                listPickerOpen = false
                scope.launch { onOpenTaskList(tasksRepo.createList("")) }
            },
            onDismiss = { listPickerOpen = false },
        )
    }

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.bg)
                // Swallows every tap so none falls through to a Start tile
                // underneath (see WeatherHubScreen's doc comment).
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(20.dp))
                Text(text = "tileshell", color = tokens.fgDim, fontSize = 14.sp)
                Text(
                    text = "productivity",
                    color = accent,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    PRODUCTIVITY_PIVOTS.forEachIndexed { index, label ->
                        Text(
                            text = label,
                            color = if (pagerState.currentPage == index) tokens.fg else tokens.fgDim,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { pagerScope.launch { pagerState.animateScrollToPage(index) } },
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> TodayPage(
                        tokens, accent, notes, lists, tasksRepo, quickItems,
                        onOpenNote = onOpenNote,
                        onOpenTaskList = onOpenTaskList,
                        onRunQuick = { item ->
                            when (item) {
                                QuickItem.NewNote -> onNewNote()
                                QuickItem.NewTask -> newTask()
                                QuickItem.Calculator -> if (!openCalculator(context)) {
                                    android.widget.Toast.makeText(context, "no calculator app found", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                QuickItem.Timer -> openTimers(context)
                                is QuickItem.TaskList -> onOpenTaskList(item.listId)
                                is QuickItem.Note -> onOpenNote(item.noteId)
                                is QuickItem.App -> openApp(context, item.packageName)
                            }
                        },
                        onRemoveQuick = { setQuick(removeQuickItem(quickItems, it)) },
                        onAddQuick = addToQuick,
                    )
                    1 -> NotesPage(tokens, notes, pinnedNoteIds, onOpenNote, onPinNote, { addToQuick(QuickItem.Note(it)) }) { id ->
                        scope.launch { notesRepo.delete(id) }
                    }
                    2 -> TasksPage(tokens, accent, lists, tasksRepo, pinnedListIds, onOpenTaskList, onPinTaskList) {
                        addToQuick(QuickItem.TaskList(it))
                    }
                    else -> ProductivityAppsPage(tokens, accent) { addToQuick(QuickItem.App(it)) }
                }
            }

            HubAppBar(
                tokens = tokens,
                actions = when (pagerState.currentPage) {
                    1 -> listOf(
                        HubAppBarAction("back", "back", onDismiss),
                        HubAppBarAction("plus", "new note", onNewNote),
                        HubAppBarAction("pin", "pin notes to start", onPinNotepad),
                    )
                    2 -> listOf(
                        HubAppBarAction("back", "back", onDismiss),
                        HubAppBarAction("plus", "new list") {
                            scope.launch { onOpenTaskList(tasksRepo.createList("")) }
                        },
                    )
                    else -> listOf(
                        HubAppBarAction("back", "back", onDismiss),
                        HubAppBarAction("pin", "pin productivity to start", onPinHub),
                    )
                },
            )
        }
    }
}

// ---- today -----------------------------------------------------------------

@Composable
private fun TodayPage(
    tokens: ColorTokens,
    accent: Color,
    notes: List<NoteItem>,
    lists: List<TaskListSummary>,
    tasksRepo: TaskRepository,
    quickItems: List<QuickItem>,
    onOpenNote: (Long) -> Unit,
    onOpenTaskList: (String) -> Unit,
    onRunQuick: (QuickItem) -> Unit,
    onRemoveQuick: (QuickItem) -> Unit,
    onAddQuick: (QuickItem) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val calendarGranted = rememberPermissionGranted(Manifest.permission.READ_CALENDAR)
    val meetings = rememberUpcomingMeetings(calendarGranted)
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val openTasks by remember { tasksRepo.openTasks(limit = 4) }.collectAsState(initial = emptyList())
    val openCount by remember { tasksRepo.openCount() }.collectAsState(initial = 0)
    val latestNote = notes.firstOrNull { it.text.isNotBlank() || it.title.isNotBlank() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
    ) {
        item { SectionLabel("next meeting", tokens) }
        item {
            when {
                !calendarGranted -> CalendarPermissionPrompt(tokens, accent)
                meetings.isEmpty() -> Text("nothing else on your calendar today", color = tokens.fgDim, fontSize = 14.sp)
                else -> {
                    MeetingCard(meetings.first(), now, tokens, accent)
                    meetings.getOrNull(1)?.let { next ->
                        Text(
                            text = "then ${meetingTimeLabel(next.startMillis, next.endMillis, now).substringBefore(" –")} · " +
                                next.title.lowercase() + (next.link?.let { " · ${it.provider}" } ?: ""),
                            color = tokens.fgDim,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(top = 8.dp, start = 2.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { openCalendarEvent(context, next.eventId) },
                                ),
                        )
                    }
                }
            }
        }

        item { SectionLabel(if (openCount > 0) "tasks · $openCount open" else "tasks", tokens) }
        if (openTasks.isEmpty()) {
            item { Text("no open tasks", color = tokens.fgDim, fontSize = 14.sp) }
        } else {
            items(openTasks, key = { "task-${it.id}" }) { task ->
                TaskRow(task.text, done = false, tokens = tokens, accent = accent) {
                    scope.launch { tasksRepo.setDone(task.id, true) }
                }
            }
        }

        item { SectionLabel("latest note", tokens) }
        item {
            if (latestNote == null) {
                Text("no notes yet", color = tokens.fgDim, fontSize = 14.sp)
            } else {
                val preview = latestNote.preview()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.fg.copy(alpha = 0.06f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onOpenNote(latestNote.id) },
                        )
                        .padding(12.dp),
                ) {
                    Text(preview.title, color = tokens.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (preview.snippet.isNotBlank()) {
                        Text(preview.snippet, color = tokens.fgDim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        item { SectionLabel("quick", tokens) }
        item { QuickRowSection(quickItems, notes, lists, tokens, onRunQuick, onRemoveQuick, onAddQuick) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MeetingCard(meeting: UpcomingMeeting, now: Long, tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.fg.copy(alpha = 0.06f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { openCalendarEvent(context, meeting.eventId) },
            )
            .padding(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(meeting.title.lowercase(), color = tokens.fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                meetingTimeLabel(meeting.startMillis, meeting.endMillis, now) + (meeting.link?.let { " · ${it.provider}" } ?: ""),
                color = tokens.fgDim,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        meeting.link?.let { link ->
            Spacer(Modifier.width(10.dp))
            Text(
                "join",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { openMeetingLink(context, link.url) },
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun CalendarPermissionPrompt(tokens: ColorTokens, accent: Color) {
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Text(
        text = "show your meetings · allow calendar access",
        color = accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { request.launch(Manifest.permission.READ_CALENDAR) },
        ),
    )
}

/**
 * The quick row: each shortcut runs on tap, long-press offers "remove from
 * quick", and a trailing "+" adds back any built-in that was removed. A pinned
 * list/note/app whose target is gone (deleted, uninstalled) is skipped.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickRowSection(
    items: List<QuickItem>,
    notes: List<NoteItem>,
    lists: List<TaskListSummary>,
    tokens: ColorTokens,
    onRun: (QuickItem) -> Unit,
    onRemove: (QuickItem) -> Unit,
    onAdd: (QuickItem) -> Unit,
) {
    val context = LocalContext.current
    val missingBuiltIns = QuickItem.BUILT_INS.filter { it !in items }
    // Fixed quarter-width cells, so a short last row lines up under the first
    // columns instead of stretching across the row.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val cellWidth = (maxWidth - 18.dp) / 4
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 4,
    ) {
        items.forEach { item ->
            val (iconKey, label, packageName) = when (item) {
                QuickItem.NewNote -> Triple("notepad", "note", null)
                QuickItem.NewTask -> Triple("tasks", "task", null)
                QuickItem.Calculator -> Triple("calc", "calculator", null)
                QuickItem.Timer -> Triple("alarm", "timer", null)
                is QuickItem.TaskList -> {
                    val list = lists.firstOrNull { it.id == item.listId } ?: return@forEach
                    Triple("tasks", list.name.lowercase(), null)
                }
                is QuickItem.Note -> {
                    val note = notes.firstOrNull { it.id == item.noteId }?.takeIf { it.text.isNotBlank() || it.title.isNotBlank() } ?: return@forEach
                    Triple("note", note.preview().title.lowercase(), null)
                }
                is QuickItem.App -> {
                    val appLabel = remember(item.packageName) { appLabelOrNull(context, item.packageName) } ?: return@forEach
                    Triple(null, appLabel.lowercase(), item.packageName)
                }
            }
            var menuOpen by remember(item) { mutableStateOf(false) }
            Box(modifier = Modifier.width(cellWidth)) {
                QuickCell(iconKey, packageName, label, tokens, onClick = { onRun(item) }, onLongClick = { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("remove from quick") }, onClick = {
                        menuOpen = false
                        onRemove(item)
                    })
                }
            }
        }
        if (missingBuiltIns.isNotEmpty()) {
            var addOpen by remember { mutableStateOf(false) }
            Box(modifier = Modifier.width(cellWidth)) {
                QuickCell("plus", null, "add", tokens, onClick = { addOpen = true }, onLongClick = { addOpen = true })
                DropdownMenu(expanded = addOpen, onDismissRequest = { addOpen = false }) {
                    missingBuiltIns.forEach { builtIn ->
                        val name = when (builtIn) {
                            QuickItem.NewNote -> "new note"
                            QuickItem.NewTask -> "new task"
                            QuickItem.Calculator -> "calculator"
                            else -> "timer"
                        }
                        DropdownMenuItem(text = { Text(name) }, onClick = {
                            addOpen = false
                            onAdd(builtIn)
                        })
                    }
                }
            }
        }
    }
    }
    Text(
        "long-press a note, list or app to add it here",
        color = tokens.fgDim,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickCell(
    iconKey: String?,
    packageName: String?,
    label: String,
    tokens: ColorTokens,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(tokens.fg.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (packageName != null) {
                val icon = rememberAppIconBitmap(packageName, sizePx = 96)
                if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(32.dp))
            } else if (iconKey != null) {
                Icon(TileIcons[iconKey], contentDescription = null, tint = tokens.fg, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = tokens.fgDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Which list "new task" goes into, when there are several. */
@Composable
private fun TaskListPicker(
    lists: List<TaskListSummary>,
    onPick: (String) -> Unit,
    onNewList: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("add a task to") },
        text = {
            Column {
                lists.forEach { list ->
                    Text(
                        "${list.name.lowercase()} · ${list.openCount} open",
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(list.id) }
                            .padding(vertical = 10.dp),
                    )
                }
                Text(
                    "+ new list",
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNewList)
                        .padding(vertical = 10.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("cancel") } },
    )
}

/**
 * Upcoming meetings, re-read every 5 minutes while shown. [active] is the
 * same live-tile gate every other Start face is paused by (edit mode,
 * off-screen, screen off, battery saver) — the hub screen itself has no such
 * gate and passes the default `true`, but the Start-pinned productivity tile
 * must thread its own `active` through so this calendar poll actually stops
 * when the tile isn't visible instead of querying the provider forever.
 */
@Composable
internal fun rememberUpcomingMeetings(granted: Boolean, active: Boolean = true): List<UpcomingMeeting> {
    val context = LocalContext.current
    val meetings by produceState(initialValue = emptyList<UpcomingMeeting>(), granted, active) {
        if (!granted) {
            value = emptyList()
            return@produceState
        }
        if (!active) return@produceState
        while (true) {
            value = withContext(Dispatchers.IO) { queryUpcomingMeetings(context) }
            delay(5 * 60_000L)
        }
    }
    return meetings
}

// ---- notes -----------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotesPage(
    tokens: ColorTokens,
    notes: List<NoteItem>,
    pinnedNoteIds: Set<Long>,
    onOpenNote: (Long) -> Unit,
    onPinNote: (Long) -> Unit,
    onAddToQuick: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val visibleNotes = notes.filter { it.text.isNotBlank() || it.title.isNotBlank() }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (visibleNotes.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("no notes yet · tap + to write one", color = tokens.fgDim, fontSize = 14.sp, modifier = Modifier.padding(4.dp))
            }
        }
        items(visibleNotes.size, key = { visibleNotes[it].id }) { index ->
            val note = visibleNotes[index]
            var menuOpen by remember { mutableStateOf(false) }
            val color = TileAccents.forId(NOTE_CARD_COLORS[(note.id % NOTE_CARD_COLORS.size).toInt()])
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onOpenNote(note.id) },
                            onLongClick = { menuOpen = true },
                        )
                        .padding(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (note.title.isNotBlank()) {
                            Text(note.title.trim(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            note.text.trim(),
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = if (note.title.isNotBlank()) 3 else 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (note.id in pinnedNoteIds) {
                        Icon(TileIcons["pin"], contentDescription = "pinned to start", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (note.id !in pinnedNoteIds) {
                        DropdownMenuItem(text = { Text("pin to start") }, onClick = {
                            menuOpen = false
                            onPinNote(note.id)
                        })
                    }
                    DropdownMenuItem(text = { Text("add to quick") }, onClick = {
                        menuOpen = false
                        onAddToQuick(note.id)
                    })
                    DropdownMenuItem(text = { Text("delete") }, onClick = {
                        menuOpen = false
                        onDelete(note.id)
                    })
                }
            }
        }
    }
}

// ---- tasks -----------------------------------------------------------------

@Composable
private fun TasksPage(
    tokens: ColorTokens,
    accent: Color,
    lists: List<TaskListSummary>,
    tasksRepo: TaskRepository,
    pinnedListIds: Set<String>,
    onOpenTaskList: (String) -> Unit,
    onPinTaskList: (String) -> Unit,
    onAddToQuick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (lists.isEmpty()) {
            item { Text("no task lists yet · tap + to start one", color = tokens.fgDim, fontSize = 14.sp) }
        }
        items(lists, key = { it.id }) { list ->
            TaskListCard(
                list, list.id in pinnedListIds, tasksRepo, tokens, accent,
                onOpen = { onOpenTaskList(list.id) },
                onPin = { onPinTaskList(list.id) },
                onAddToQuick = { onAddToQuick(list.id) },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskListCard(
    list: TaskListSummary,
    pinned: Boolean,
    tasksRepo: TaskRepository,
    tokens: ColorTokens,
    accent: Color,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onAddToQuick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val tasks by remember(list.id) { tasksRepo.tasks(list.id) }.collectAsState(initial = emptyList())
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("add to quick") }, onClick = {
                menuOpen = false
                onAddToQuick()
            })
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(tokens.fg.copy(alpha = 0.06f))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpen,
                    onLongClick = { menuOpen = true },
                )
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    list.name.lowercase() + " · ${list.openCount} open",
                    color = tokens.fg,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (pinned) "on start" else "pin",
                    color = if (pinned) tokens.fgDim else accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !pinned,
                        onClick = onPin,
                    ),
                )
            }
            Spacer(Modifier.height(4.dp))
            val shown = tasks.sortedBy { it.done }.take(4)
            if (shown.isEmpty()) {
                Text("empty · tap to add tasks", color = tokens.fgDim, fontSize = 13.sp)
            }
            shown.forEach { task ->
                TaskRow(task.text, task.done, tokens, accent) {
                    scope.launch { tasksRepo.setDone(task.id, !task.done) }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(text: String, done: Boolean, tokens: ColorTokens, accent: Color, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(vertical = 5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (done) accent else Color.Transparent)
                .border(1.5.dp, if (done) accent else tokens.fgDim, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(TileIcons["check"], contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = if (done) tokens.fgDim else tokens.fg,
            fontSize = 14.sp,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- apps ------------------------------------------------------------------

@Composable
private fun ProductivityAppsPage(tokens: ColorTokens, accent: Color, onAddToQuick: (String) -> Unit) {
    val context = LocalContext.current
    val apps = rememberProductivityApps() ?: return
    val usageGranted = rememberUsageAccess()
    val groups = remember(apps) { groupProductivityApps(apps) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        if (groups.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("no office, mail, meeting or tool apps found", color = tokens.fgDim, fontSize = 14.sp, modifier = Modifier.padding(6.dp))
            }
        }
        if (!usageGranted && groups.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "sort by most used · allow usage access",
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { UsageAccess.openSettings(context) },
                        )
                        .padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
                )
            }
        }
        groups.forEach { (category, list) ->
            item(key = "group-${category.name}", span = { GridItemSpan(maxLineSpan) }) {
                Text(category.label, color = tokens.fgDim, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
            }
            items(list.size, key = { "app-${list[it].packageName}" }) { index ->
                val app = list[index]
                ProductivityAppCell(app, tokens, onLongClick = { onAddToQuick(app.packageName) }) { openApp(context, app.packageName) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductivityAppCell(app: ProductivityApp, tokens: ColorTokens, onLongClick: () -> Unit, onClick: () -> Unit) {
    val icon = rememberAppIconBitmap(app.packageName, sizePx = 96)
    var menuOpen by remember { mutableStateOf(false) }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(text = { Text("add to quick") }, onClick = {
            menuOpen = false
            onLongClick()
        })
    }
    Column(
        modifier = Modifier
            .padding(4.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = { menuOpen = true },
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(40.dp)) {
            if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(3.dp))
        Text(app.label.lowercase(), color = tokens.fg, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The installed productivity apps, most used first; null while the lookup
 * runs. Shared by the apps page and the hub tile.
 */
@Composable
internal fun rememberProductivityApps(): List<ProductivityApp>? {
    val context = LocalContext.current
    val opens = rememberAppOpenCounts()
    val installed by produceState<Pair<Map<String, String>, Set<String>>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val tools = resolvedToolPackages(context)
            val found = (PRODUCTIVITY_APP_PACKAGES + tools).mapNotNull { packageName ->
                if (runCatching { pm.getLaunchIntentForPackage(packageName) }.getOrNull() == null) return@mapNotNull null
                packageName to (appLabelOrNull(context, packageName) ?: packageName)
            }.toMap()
            found to tools
        }
    }
    val (apps, tools) = installed ?: return null
    return remember(apps, tools, opens) { productivityApps(apps, opens, tools) }
}

@Composable
private fun SectionLabel(text: String, tokens: ColorTokens) {
    Text(
        text,
        color = tokens.fgDim,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}
