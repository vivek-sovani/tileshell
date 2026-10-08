package com.tileshell.feature.livetiles

import com.tileshell.core.data.reminders.nextScheduled
import com.tileshell.core.data.reminders.reminderShortWhen
import androidx.compose.foundation.layout.width
import com.tileshell.core.data.reminders.isReminderDue
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TaskItem
import com.tileshell.core.data.TaskRepository
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileIcons
import kotlinx.coroutines.launch

/** One row shown in the tile's checklist preview. */
data class TaskPreviewItem(val id: Long, val text: String, val done: Boolean, val due: Boolean = false)

/** What the tile actually renders — counts plus a short preview. */
data class TasksSummary(
    val doneCount: Int,
    val totalCount: Int,
    val preview: List<TaskPreviewItem>,
    /** Open tasks whose reminder has gone off (see isReminderDue) — listed first. */
    val dueCount: Int = 0,
)

/**
 * Pure — no repository/Flow involved, so the "which tasks show in the
 * preview" choice is unit-testable. Active tasks come first (what you'd want
 * to glance at); once every task is done, the preview falls back to showing
 * completed ones rather than going blank. Tasks arrive oldest-first (DB
 * position order), so once there are more active tasks than fit, the newest
 * ones are kept — otherwise a task just added would never appear until
 * enough older ones were checked off or deleted.
 */
fun tasksSummary(tasks: List<TaskItem>, maxPreview: Int = 3, now: Long = 0L): TasksSummary {
    fun TaskItem.isDue() = isReminderDue(remindAt, snoozeAt, done, now)
    // Due reminders come first, like a WP live tile surfacing what needs you now.
    val due = tasks.filter { it.isDue() }
    val active = tasks.filter { !it.done && !it.isDue() }
    val room = (maxPreview - due.size).coerceAtLeast(0)
    val activePreview = if (active.size > room) active.takeLast(room) else active
    val ordered = due + activePreview + tasks.filter { it.done }
    return TasksSummary(
        doneCount = tasks.count { it.done },
        totalCount = tasks.size,
        preview = ordered.take(maxPreview).map { TaskPreviewItem(it.id, it.text, it.done, due = it.isDue()) },
        dueCount = due.size,
    )
}

/**
 * How many checklist rows a tile of this size has room for, so a bigger tile
 * actually shows more of the list instead of leaving the rest of the space
 * blank. Pure so it's unit-testable. Narrow sizes (TALL/COLUMN) render via
 * [TasksFront]'s own `narrow` branch and never call this.
 */
fun maxPreviewFor(size: TileSize): Int = when (size) {
    TileSize.MEDIUM -> 4
    TileSize.WIDE, TileSize.WIDE_MEDIUM -> 4
    TileSize.LARGE -> 6
    TileSize.TALL_MEDIUM -> 6
    TileSize.XLARGE -> 10
    else -> 3
}

/**
 * Wide-enough tiles (3+ grid columns) lay the preview out as two columns
 * instead of one long left-aligned list, so the extra horizontal room a WIDE/
 * LARGE/XLARGE tile has isn't left blank next to short task text.
 */
fun previewColumnsFor(size: TileSize): Int = if (size.cols >= 3) 2 else 1

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

/**
 * The live tasks tile: a short checklist preview + "x of y done." This tile
 * is restricted to 2+ row sizes (see
 * [com.tileshell.core.data.AppCategories.requiresTallTile]) — a 1-row tile has
 * no room to show a checklist at all — so unlike every other face in this
 * package there's no `short`-size branch to handle. Never flips (see
 * [LiveFace.TASKS]'s own `flips = false`, matching [PeopleTileFace]/
 * [StepsTileFace]) — the checklist itself is the only thing worth a glance,
 * so there's no separate back face to hold anything else.
 *
 * [interactive] gates the preview's checkboxes — tapping one toggles that task
 * done right from Start, the same "consume the tap so the tile doesn't also
 * launch" pattern [MusicTileFace]'s transport buttons already use. Off in edit
 * mode (the grid owns all touch there).
 *
 * [listId] scopes the checklist to this one pinned tile/gadget instance — see
 * [TaskRepository]'s own doc comment.
 */
@Composable
fun TasksTileFace(size: TileSize, listId: String, modifier: Modifier = Modifier, interactive: Boolean = false) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { TaskDailyResetWorker.ensureScheduled(context) }
    val repository = remember(context) { TaskRepository.create(context) }
    val tasks by remember(listId) { repository.tasks(listId) }.collectAsState(initial = emptyList())
    val now = rememberMinuteClock()
    val summary = remember(tasks, size, now) { tasksSummary(tasks, maxPreviewFor(size), now) }
    val next = remember(tasks, now) {
        nextScheduled(tasks.filter { !it.done }, { it.remindAt }, { it.snoozeAt }, now)
            ?.let { "${reminderShortWhen(it.snoozeAt ?: it.remindAt!!, now)} · ${it.text}" }
    }
    val scope = rememberCoroutineScope()
    // The list's name ("work", "home") labels the tile; every shown list gets one.
    val name by remember(listId) { repository.listName(listId) }.collectAsState(initial = null)
    LaunchedEffect(listId) { repository.ensureList(listId) }

    TasksFront(
        summary = summary,
        name = name ?: "tasks",
        next = next,
        size = size,
        interactive = interactive,
        onToggle = { id, done -> scope.launch { repository.setDone(id, done) } },
        modifier = modifier,
    )
}

@Composable
private fun TasksFront(
    summary: TasksSummary,
    name: String,
    next: String?,
    size: TileSize,
    interactive: Boolean,
    onToggle: (id: Long, done: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val narrow = size.narrowLive
    val big = size.cols >= 3 && size.rows >= 3

    if (narrow) {
        // Only one column wide (TALL/COLUMN) — still shows the real checklist
        // (user-reported: a narrow tile only ever showed the bare "x/y" count,
        // not the tasks themselves), just left-aligned and compact instead of
        // the wider tile's two-line "x of y done" + progress bar header.
        Column(modifier = modifier.fillMaxSize().padding(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${summary.doneCount}/${summary.totalCount}",
                    color = FaceText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (summary.dueCount > 0) DueBadge(summary.dueCount)
            }
            Spacer(Modifier.height(6.dp))
            if (summary.preview.isEmpty()) {
                Text(
                    text = "no tasks yet",
                    color = FaceText.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    summary.preview.forEach { item ->
                        TaskPreviewRow(item = item, interactive = interactive, onToggle = onToggle, maxLines = 2)
                    }
                }
            }
            // A row of checkboxes can fill the entire narrow tile, leaving no
            // gap to tap that isn't already claimed by a checkbox's own toggle
            // (user-reported: no way to reach "add a task" once the list filled
            // the tile) — this trailing label is the same reserved, always-
            // untouched tap area the wider layout already keeps below its own
            // list, so the tile's own tap-to-open-the-sheet handler always has
            // somewhere to land regardless of how many tasks are shown.
            Spacer(Modifier.weight(1f))
            if (next != null) NextLine(next, fontSize = 10)
            Text(name.lowercase(), color = FaceText.copy(alpha = 0.82f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }

    Column(modifier = modifier.fillMaxSize().padding(11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (summary.dueCount > 0) "due now" else "${summary.doneCount} of ${summary.totalCount} done",
                color = FaceText,
                fontSize = if (big) 20.sp else 16.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (summary.dueCount > 0) DueBadge(summary.dueCount)
        }
        if (summary.totalCount > 0) {
            Spacer(Modifier.height(6.dp))
            TaskProgressBar(
                fraction = summary.doneCount.toFloat() / summary.totalCount,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        if (summary.preview.isEmpty()) {
            Text(
                text = "no tasks yet",
                color = FaceText.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
        } else if (previewColumnsFor(size) == 2) {
            // Wide enough for two columns — reads left-to-right, top-to-bottom,
            // so the extra width isn't left blank next to short task text.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                summary.preview.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        rowItems.forEach { item ->
                            Box(modifier = Modifier.weight(1f)) {
                                TaskPreviewRow(item = item, interactive = interactive, onToggle = onToggle, maxLines = 2)
                            }
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                summary.preview.forEach { item ->
                    TaskPreviewRow(item = item, interactive = interactive, onToggle = onToggle)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // A one-row tile has no room for the footer under the summary line.
        if (size.rows >= 2) {
            if (next != null) NextLine(next, fontSize = 12)
            Text(name.lowercase(), color = FaceText.copy(alpha = 0.82f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TaskProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(FaceText.copy(alpha = 0.25f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(2.dp))
                .background(FaceText),
        )
    }
}

@Composable
private fun TaskPreviewRow(
    item: TaskPreviewItem,
    interactive: Boolean,
    onToggle: (id: Long, done: Boolean) -> Unit,
    // 2 in the two-column layout — a narrower column truncates a merely
    // average-length task to a fragment at 1 line (user-reported: "two column
    // tasks doesnt show full task text"); letting it wrap instead shows the
    // whole thing in the common case, only ellipsizing a genuinely long one.
    maxLines: Int = 1,
) {
    // Centered for the common single-line row (checkbox and text read as one
    // unit); top-aligned once text can wrap to 2 lines, so the checkbox sits
    // beside the first line instead of drifting toward the wrapped text's
    // vertical middle.
    val checkboxAlignment = if (maxLines > 1) Alignment.Top else Alignment.CenterVertically
    Row(verticalAlignment = checkboxAlignment, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            // A bigger, round tap target than the visible 14dp box, centred on
            // it — same "enlarge the hit area beyond the glyph" idea used for
            // every other on-tile touch control in this app (resize/unpin
            // corner handles, the music transport buttons at 26-34dp).
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .clickable(enabled = interactive, onClick = { onToggle(item.id, !item.done) }),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .border(1.4.dp, FaceText.copy(alpha = 0.7f), RoundedCornerShape(3.dp)),
            ) {
                if (item.done) {
                    Icon(
                        imageVector = TileIcons["check"],
                        contentDescription = null,
                        tint = FaceText,
                        modifier = Modifier.fillMaxSize().padding(1.dp),
                    )
                }
            }
        }
        if (item.due) {
            Icon(TileIcons["bell"], contentDescription = "due", tint = FaceText, modifier = Modifier.size(13.dp).padding(top = 1.dp))
        }
        Text(
            text = item.text,
            color = if (item.done) FaceText.copy(alpha = 0.55f) else FaceText,
            fontSize = 14.sp,
            fontWeight = if (item.due) FontWeight.Medium else null,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
        )
    }
}

/** Count of due reminders, the tile's own badge (the tasks tile has no app package to badge). */
@Composable
private fun DueBadge(count: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(CircleShape)
            .background(FaceText)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            color = FaceText.let { if (it.luminance() > 0.5f) Color(0xFF111111) else Color.White },
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Now, refreshed on each minute boundary, so a reminder becomes "due" on time. */
@Composable
internal fun rememberMinuteClock(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - (now % 60_000L))
        }
    }
    return now
}

/** "🔔 6:00 pm · water the plants" — the next reminder still to come on this list. */
@Composable
private fun NextLine(text: String, fontSize: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
        Icon(TileIcons["bell"], contentDescription = "next reminder", tint = FaceText, modifier = Modifier.size((fontSize + 1).dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = FaceText, fontSize = fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
