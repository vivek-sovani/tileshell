package com.tileshell.feature.personalize

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TaskItem
import com.tileshell.core.data.TaskRepository
import com.tileshell.core.data.reminders.TaskReminders
import com.tileshell.core.data.reminders.TaskRepeat
import com.tileshell.core.data.reminders.reminderLine
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Tasks sub-sheet, opened by tapping a Tasks tile — the tile's own
 * entry point into managing its checklist, the same way tapping a folder
 * opens [com.tileshell.feature.start.FolderOverlay]. Follows the same
 * slide-up shape as [HiddenAppsSheet]/[WidgetListSheet]. Owns its own
 * [TaskRepository] read/write (like the tile face itself does) — the task
 * list isn't part of `StartViewModel`'s state, only the auto-clear setting is.
 *
 * [listId] scopes every read/write to one specific pinned Tasks tile or
 * glance gadget's own list (its own stable tile/widget id) — each pinned
 * instance keeps an independent checklist now, not one list shared by every
 * instance (user-reported: adding a new Tasks tile/gadget just showed the
 * same existing list again).
 */
@Composable
fun TaskListSheet(
    visible: Boolean,
    listId: String,
    dark: Boolean,
    accentId: String,
    autoClearDaily: Boolean,
    onAutoClearDailyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "taskListSheetProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current
    val repository = remember(context) { TaskRepository.create(context) }
    val tasks by remember(listId) { repository.tasks(listId) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf("") }
    var confirmClearAll by remember { mutableStateOf(false) }

    // Task reminders (hidden entirely when TaskReminders.ENABLED is off).
    var reminderFor by remember { mutableStateOf<TaskItem?>(null) }
    // A reminder picked for the task still being typed, set when it's added.
    var draftRemindAt by remember { mutableStateOf<Long?>(null) }
    var draftRepeat by remember { mutableStateOf<TaskRepeat>(TaskRepeat.Once) }
    var askExactAccess by remember { mutableStateOf(false) }
    var exactAllowed by remember { mutableStateOf(TaskReminders.canScheduleExact(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAllowed = TaskReminders.canScheduleExact(context)
                // Access granted in Settings → re-arm alarms as exact ones.
                scope.launch { TaskReminders.sync(context) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val now = rememberMinuteNow(visible)

    // The list's name ("work", "home"), editable in place. Every shown list
    // gets a row (and a default name) the moment it's opened.
    val savedName by remember(listId) { repository.listName(listId) }.collectAsState(initial = null)
    var nameDraft by remember(listId) { mutableStateOf<String?>(null) }
    LaunchedEffect(listId, visible) {
        if (visible) repository.ensureList(listId)
    }

    // A text area with no focus shows no cursor at all — claim focus and raise
    // the keyboard whenever this sheet opens (or opens for a different list),
    // same as NotesSheet/StickyNoteEditorSheet's own editors, so typing a task
    // needs no extra tap first.
    val draftFocusRequester = remember(listId) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(listId, visible) {
        if (visible) {
            draftFocusRequester.requestFocus()
            keyboard?.show()
        }
    }

    // Capture the current text and clear the field *before* launching — the
    // coroutine body runs after this function returns, so reading `draft`
    // inside it (rather than a captured local) would always see the
    // already-cleared "" the very next line resets it to, silently dropping
    // every task.
    val submitDraft = {
        val text = draft
        val at = draftRemindAt
        val repeat = draftRepeat
        draft = ""
        if (text.isNotBlank()) {
            draftRemindAt = null
            draftRepeat = TaskRepeat.Once
        }
        scope.launch { repository.addTask(listId, text, at, repeat) }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("clear all tasks?") },
            text = { Text("this removes every task, including ones you haven't finished. this can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.clearAll(listId) }
                    confirmClearAll = false
                }) { Text("clear all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("cancel") }
            },
        )
    }

    if (askExactAccess) {
        AlertDialog(
            onDismissRequest = { askExactAccess = false },
            title = { Text("turn on on-time reminders") },
            text = {
                Text("android needs \"alarms & reminders\" access to alert you at the exact minute. without it, reminders are off.")
            },
            confirmButton = {
                TextButton(onClick = {
                    askExactAccess = false
                    runCatching { context.startActivity(TaskReminders.exactAccessIntent(context)) }
                }) { Text("open settings") }
            },
            dismissButton = {
                TextButton(onClick = { askExactAccess = false }) { Text("not now") }
            },
        )
    }

    BackHandler(enabled = visible && reminderFor == null) { onDismiss() }

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .graphicsLayer { translationY = size.height * (1f - progress) }
                    .background(tokens.sheet, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .imePadding(),
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tokens.fgDim.copy(alpha = 0.5f)),
                )

                BasicTextField(
                    value = nameDraft ?: savedName ?: "tasks",
                    onValueChange = { value ->
                        nameDraft = value
                        if (value.isNotBlank()) scope.launch { repository.renameList(listId, value) }
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = tokens.fg, fontSize = 20.sp, fontWeight = FontWeight.W300),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                )

                // Auto-clear-completed-daily toggle.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("auto-clear completed daily", color = tokens.fg, fontSize = 14.sp)
                        Text(
                            text = "checked-off tasks clear themselves each day — active tasks are never touched",
                            color = tokens.fgDim,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = autoClearDaily,
                        onCheckedChange = onAutoClearDailyChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accent),
                    )
                }

                HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

                // Add-a-task row.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (draft.isEmpty()) {
                            Text("add a task", color = tokens.fgDim, fontSize = 15.sp)
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            singleLine = true,
                            textStyle = TextStyle(color = tokens.fg, fontSize = 15.sp),
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitDraft() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(draftFocusRequester),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (TaskReminders.ENABLED) {
                        Icon(
                            imageVector = TileIcons["bell"],
                            contentDescription = if (draftRemindAt != null) "change reminder" else "add a reminder",
                            tint = if (draftRemindAt != null) accent else tokens.fgDim,
                            modifier = Modifier
                                .padding(end = 14.dp)
                                .size(22.dp)
                                .clickable {
                                    reminderFor = TaskItem(
                                        id = NEW_TASK_ID,
                                        text = draft.ifBlank { "new task" },
                                        done = false,
                                        remindAt = draftRemindAt,
                                        repeat = draftRepeat,
                                    )
                                },
                        )
                    }
                    Icon(
                        imageVector = TileIcons["plus"],
                        contentDescription = "add task",
                        tint = accent,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { submitDraft() },
                    )
                }

                draftRemindAt?.let { at ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                    ) {
                        Icon(TileIcons["bell"], contentDescription = null, tint = accent, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(reminderLine(at, draftRepeat, now), color = accent, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(
                            "remove",
                            color = tokens.fgDim,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { draftRemindAt = null; draftRepeat = TaskRepeat.Once },
                        )
                    }
                }

                if (tasks.isEmpty()) {
                    Text(
                        text = "no tasks yet — add one above",
                        color = tokens.fgDim,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(tasks, key = { it.id }) { task ->
                            TaskRow(
                                task = task,
                                accent = accent,
                                tokens = tokens,
                                onToggle = { scope.launch { repository.setDone(task.id, !task.done) } },
                                onDelete = { scope.launch { repository.delete(task.id) } },
                                now = now,
                                exactAllowed = exactAllowed,
                                onReminder = if (TaskReminders.ENABLED) ({ reminderFor = task }) else null,
                                onFixAccess = { askExactAccess = true },
                            )
                        }
                    }
                }

                HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "clear completed",
                        color = accent,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { scope.launch { repository.clearCompleted(listId) } }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "clear all",
                        color = tokens.fgDim,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { confirmClearAll = true }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                    )
                }
            }
        }
        reminderFor?.let { editing ->
            TaskReminderSheet(
                task = editing,
                tokens = tokens,
                accent = accent,
                onSet = { at, repeat ->
                    reminderFor = null
                    if (editing.id == NEW_TASK_ID) {
                        draftRemindAt = at
                        draftRepeat = repeat
                    } else {
                        scope.launch { repository.setReminder(editing.id, at, repeat) }
                    }
                    if (!TaskReminders.notificationsAllowed(context) && Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (!TaskReminders.canScheduleExact(context)) askExactAccess = true
                },
                onRemove = {
                    reminderFor = null
                    if (editing.id == NEW_TASK_ID) {
                        draftRemindAt = null
                        draftRepeat = TaskRepeat.Once
                    } else {
                        scope.launch { repository.setReminder(editing.id, null, TaskRepeat.Once) }
                    }
                },
                onDismiss = { reminderFor = null },
            )
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskItem,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    now: Long,
    exactAllowed: Boolean,
    onReminder: (() -> Unit)?,
    onFixAccess: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (task.done) accent else tokens.chip)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (task.done) {
                Icon(TileIcons["check"], null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.text,
                color = if (task.done) tokens.fgDim else tokens.fg,
                fontSize = 15.sp,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
            )
            val remindAt = task.remindAt
            if (remindAt != null && !task.done) {
                val overdue = remindAt <= now
                when {
                    !exactAllowed && !overdue -> Text(
                        text = "reminder off · needs alarms & reminders access",
                        color = Color(0xFFE8B04A),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp).clickable(onClick = onFixAccess),
                    )
                    else -> Text(
                        text = reminderLine(remindAt, task.repeat, now),
                        color = if (overdue) Color(0xFFF07A7A) else accent,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        if (onReminder != null && !task.done) {
            Icon(
                imageVector = TileIcons["bell"],
                contentDescription = if (task.remindAt != null) "edit reminder" else "add reminder",
                tint = if (task.remindAt != null) accent else tokens.fgDim,
                modifier = Modifier.padding(end = 14.dp).size(20.dp).clickable(onClick = onReminder),
            )
        }
        Icon(
            imageVector = TileIcons["close"],
            contentDescription = "delete task",
            tint = tokens.fgDim,
            modifier = Modifier.size(18.dp).clickable(onClick = onDelete),
        )
    }
}

/** The current time, refreshed on each minute boundary while [active] (for "overdue"). */
@Composable
private fun rememberMinuteNow(active: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        while (active) {
            now = System.currentTimeMillis()
            delay(60_000L - (now % 60_000L))
        }
    }
    return now
}

/** Stands in for the not-yet-added task when its reminder is picked while typing it. */
private const val NEW_TASK_ID = -1L
