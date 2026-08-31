package com.tileshell.feature.personalize

import android.widget.Toast
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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.NoteItem
import com.tileshell.core.data.NoteRepository
import com.tileshell.core.data.notePreview
import com.tileshell.core.data.suggestedNoteFileName
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The Notes sub-sheet, opened by tapping the Notes tile. One sheet, two
 * internal views — a list and an editor — navigated the same way
 * `CategoryFolderSheet` switches between its category list and review mode
 * (a nullable "which one is open" id, reset to the list every time the sheet
 * opens fresh, Android back unwinds one level before dismissing).
 */
@Composable
fun NotesSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "notesSheetProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current
    val repository = remember(context) { NoteRepository.create(context) }
    val notes by repository.notes.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    // null = list; non-null = editing that note.
    var editingId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(visible) { if (visible) editingId = null }

    // Shared by both delete entry points (a list row's "×" and the editor's
    // own delete icon) — set the id to confirm, never delete directly.
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(visible) { if (visible) confirmDeleteId = null }

    BackHandler(enabled = visible) {
        if (editingId != null) editingId = null else onDismiss()
    }

    confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("delete this note?") },
            text = { Text("this can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.delete(id) }
                    if (editingId == id) editingId = null
                    confirmDeleteId = null
                }) { Text("delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = null }) { Text("cancel") }
            },
        )
    }

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

                val editing = editingId
                if (editing == null) {
                    NotesListContent(
                        notes = notes,
                        accent = accent,
                        tokens = tokens,
                        onCreate = { scope.launch { editingId = repository.createNote() } },
                        onOpen = { id -> editingId = id },
                        onDelete = { id -> confirmDeleteId = id },
                    )
                } else {
                    NoteEditorContent(
                        noteId = editing,
                        repository = repository,
                        scope = scope,
                        tokens = tokens,
                        accent = accent,
                        onBack = { editingId = null },
                        onDelete = { confirmDeleteId = editing },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesListContent(
    notes: List<NoteItem>,
    accent: Color,
    tokens: ColorTokens,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "notes",
            color = tokens.fg,
            fontSize = 20.sp,
            fontWeight = FontWeight.W300,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = TileIcons["plus"],
            contentDescription = "new note",
            tint = accent,
            modifier = Modifier.size(28.dp).clickable(onClick = onCreate),
        )
    }
    HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

    if (notes.isEmpty()) {
        Text(
            text = "no notes yet — tap + to write one",
            color = tokens.fgDim,
            fontSize = 14.sp,
            modifier = Modifier.padding(20.dp),
        )
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            items(notes, key = { it.id }) { note ->
                NoteRow(note = note, tokens = tokens, onOpen = { onOpen(note.id) }, onDelete = { onDelete(note.id) })
            }
        }
    }
}

@Composable
private fun NoteRow(note: NoteItem, tokens: ColorTokens, onOpen: () -> Unit, onDelete: () -> Unit) {
    val preview = remember(note.text) { notePreview(note.text) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(preview.title, color = tokens.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (preview.snippet.isNotEmpty()) {
                Text(
                    text = preview.snippet,
                    color = tokens.fgDim,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = TileIcons["close"],
            contentDescription = "delete note",
            tint = tokens.fgDim,
            modifier = Modifier.size(18.dp).clickable(onClick = onDelete),
        )
    }
}

@Composable
private fun ColumnScope.NoteEditorContent(
    noteId: Long,
    repository: NoteRepository,
    scope: CoroutineScope,
    tokens: ColorTokens,
    accent: Color,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(noteId) { mutableStateOf("") }
    LaunchedEffect(noteId) { text = repository.get(noteId)?.text.orEmpty() }

    // Opening a note should drop the user straight into typing — a text area
    // with no focus shows no cursor at all (easy to mistake for "the cursor
    // doesn't blink"), so claim focus and raise the keyboard the moment this
    // note's editor appears, same as tapping the field by hand would.
    val focusRequester = remember(noteId) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(noteId) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    val context = LocalContext.current
    // SAF document picker — the same permission-free "supports Google Drive"
    // mechanism the layout backup export uses (StartViewModel.exportBackup):
    // the system picker itself offers Drive (or any other cloud folder) as a
    // save destination alongside on-device storage, so no separate Drive
    // integration is needed here.
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.openOutputStream(it)?.use { stream -> stream.write(text.toByteArray()) }
            }.onSuccess {
                Toast.makeText(context, "note saved", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "save failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹ back",
            color = accent,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 6.dp, vertical = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = TileIcons["download"],
            contentDescription = "save a copy to device or drive",
            tint = tokens.fgDim,
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = { saveLauncher.launch(suggestedNoteFileName(text)) }),
        )
        Spacer(Modifier.width(16.dp))
        Icon(
            imageVector = TileIcons["close"],
            contentDescription = "delete note",
            tint = tokens.fgDim,
            modifier = Modifier.size(20.dp).clickable(onClick = onDelete),
        )
    }
    BasicTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            // Written on every change (not debounced): the note's id is
            // captured by this call's own closure, and this coroutine runs on
            // the sheet's own scope (not this editor view's), so it isn't
            // cancelled by navigating back to the list before it lands.
            scope.launch { repository.updateText(noteId, newText) }
        },
        textStyle = TextStyle(color = tokens.fg, fontSize = 16.sp, lineHeight = 22.sp),
        cursorBrush = SolidColor(accent),
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .focusRequester(focusRequester),
    )
}
