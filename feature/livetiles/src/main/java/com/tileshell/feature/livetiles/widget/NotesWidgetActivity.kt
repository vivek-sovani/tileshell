package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.NoteItem
import com.tileshell.core.data.NoteRepository
import com.tileshell.core.data.notePreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Standalone "manage the shared notepad" screen — list, add, edit, delete —
 * for anything the home-screen widget's own RemoteViews preview can't do
 * inline (there's no per-row tap target there; see
 * `NotesWidgetRefreshWorker`'s doc comment). A plain [ComponentActivity], not
 * part of TileShell's own `MainActivity`/Start UI — see
 * [TaskListWidgetActivity]'s identical doc comment for why.
 */
class NotesWidgetActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repository = NoteRepository.create(this)
        setContent {
            NotesScreen(repository = repository, onChanged = { NotesWidgetRefreshWorker.refreshNow(this) })
        }
    }
}

private val NoteFg = Color(0xFFF6F6F8)
private val NoteFgDim = Color(0xFFF6F6F8).copy(alpha = 0.65f)
private val NoteAccent = Color(0xFFE2A200)

private sealed class NotesStage {
    object List : NotesStage()
    data class Edit(val id: Long) : NotesStage()
}

@Composable
private fun NotesScreen(repository: NoteRepository, onChanged: () -> Unit) {
    var notes by remember { mutableStateOf<List<NoteItem>>(emptyList()) }
    var stage by remember { mutableStateOf<NotesStage>(NotesStage.List) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { repository.notes.collect { notes = it } }

    when (val current = stage) {
        NotesStage.List -> NotesListScreen(
            notes = notes,
            onOpen = { id -> stage = NotesStage.Edit(id) },
            onDelete = { id -> scope.launch { repository.delete(id); onChanged() } },
            onNew = {
                scope.launch {
                    val id = repository.createNote()
                    onChanged()
                    stage = NotesStage.Edit(id)
                }
            },
        )
        is NotesStage.Edit -> {
            val note = notes.find { it.id == current.id }
            if (note == null) {
                stage = NotesStage.List
            } else {
                NoteEditScreen(
                    note = note,
                    onBack = { stage = NotesStage.List },
                    onTextChange = { text -> scope.launch { repository.updateText(note.id, text); onChanged() } },
                )
            }
        }
    }
}

@Composable
private fun NotesListScreen(
    notes: List<NoteItem>,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onNew: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0D)).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("notes", color = NoteFg, fontSize = 20.sp, fontWeight = FontWeight.Light)
            Text(
                text = "+ new note",
                color = NoteAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onNew),
            )
        }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (notes.isEmpty()) {
                Text(
                    text = "nothing here yet — tap \"+ new note\" above",
                    color = NoteFgDim,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            notes.forEach { note ->
                val preview = notePreview(note.text)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(note.id) }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(preview.title, color = NoteFg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (preview.snippet.isNotBlank()) {
                            Text(preview.snippet, color = NoteFgDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(
                        text = "×",
                        color = NoteFgDim,
                        fontSize = 18.sp,
                        modifier = Modifier.clickable { onDelete(note.id) }.padding(6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteEditScreen(note: NoteItem, onBack: () -> Unit, onTextChange: (String) -> Unit) {
    var text by remember(note.id) { mutableStateOf(note.text) }
    BackHandler { onTextChange(text); onBack() }
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0D)).statusBarsPadding()) {
        Text(
            text = "‹ back",
            color = NoteAccent,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onTextChange(text); onBack() }
                .padding(horizontal = 20.dp, vertical = 16.dp),
        )
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(color = NoteFg, fontSize = 16.sp, lineHeight = 22.sp),
            cursorBrush = SolidColor(NoteAccent),
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            decorationBox = { inner ->
                if (text.isEmpty()) Text("write your note…", color = NoteFgDim.copy(alpha = 0.6f), fontSize = 16.sp)
                inner()
            },
        )
    }
}
