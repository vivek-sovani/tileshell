package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TaskItem
import com.tileshell.core.data.TaskRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Standalone "manage this widget's checklist" screen — add/check/delete —
 * for anything the home-screen widget's own RemoteViews checkbox rows can't
 * do inline. A plain [ComponentActivity], not part of TileShell's own
 * `MainActivity`/Start UI: launched via [TasksAppWidgetProvider
 * .managePendingIntent] from any host launcher, so it must never bring up
 * the full app (see `WidgetAppLaunch.kt`'s doc comment) — this finishes back
 * to whatever launcher you were on, the same as [WidgetConfigureActivity].
 */
class TaskListWidgetActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repository = TaskRepository.create(this)
        val listId = TasksAppWidgetProvider.listIdFor(appWidgetId)

        setContent {
            TaskListScreen(
                repository = repository,
                listId = listId,
                onChanged = { TasksWidgetRefreshWorker.refreshNow(this) },
            )
        }
    }
}

private val TaskFg = Color(0xFFF6F6F8)
private val TaskFgDim = Color(0xFFF6F6F8).copy(alpha = 0.65f)
private val TaskAccent = Color(0xFF2B78E4)

@Composable
private fun TaskListScreen(repository: TaskRepository, listId: String, onChanged: () -> Unit) {
    var tasks by remember { mutableStateOf<List<TaskItem>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(listId) {
        repository.tasks(listId).collect { tasks = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0D))
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Text(
            text = "tasks",
            color = TaskFg,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (tasks.isEmpty()) {
                Text(
                    text = "nothing here yet — add your first task below",
                    color = TaskFgDim,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            tasks.forEach { task ->
                TaskRow(
                    task = task,
                    onToggle = { scope.launch { repository.setDone(task.id, !task.done); onChanged() } },
                    onDelete = { scope.launch { repository.delete(task.id); onChanged() } },
                )
            }
        }
        AddTaskRow(
            onAdd = { text -> scope.launch { repository.addTask(listId, text); onChanged() } },
        )
    }
}

@Composable
private fun TaskRow(task: TaskItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (task.done) TaskAccent else Color(0xFFF6F6F8).copy(alpha = 0.12f))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (task.done) Text("✓", color = Color.White, fontSize = 13.sp)
        }
        Text(
            text = if (task.done) {
                buildAnnotatedString { withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(task.text) } }
            } else {
                buildAnnotatedString { append(task.text) }
            },
            color = if (task.done) TaskFgDim else TaskFg,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Text(
            text = "×",
            color = TaskFgDim,
            fontSize = 18.sp,
            modifier = Modifier.clickable(onClick = onDelete).padding(6.dp),
        )
    }
}

@Composable
private fun AddTaskRow(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(TaskFg.copy(alpha = 0.06f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = TaskFg, fontSize = 15.sp),
                cursorBrush = SolidColor(TaskAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (text.isNotBlank()) { onAdd(text); text = "" }
                }),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (text.isEmpty()) Text("add a task", color = TaskFgDim.copy(alpha = 0.7f), fontSize = 15.sp)
                    inner()
                },
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (text.isNotBlank()) TaskAccent else TaskAccent.copy(alpha = 0.4f))
                .clickable(enabled = text.isNotBlank()) { onAdd(text); text = "" }
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text("add", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}
