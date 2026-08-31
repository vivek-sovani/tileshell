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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.suggestedNoteFileName
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens

/**
 * The Sticky Note tile's own dedicated editor — opened by tapping that tile.
 * Unlike [NotesSheet] this has no list to navigate: one tile is one note, so
 * tapping goes straight to editing it, and there's no in-sheet delete —
 * removing a sticky note means unpinning its tile, the same as any other
 * tile, via the existing corner-handle gesture.
 */
@Composable
fun StickyNoteEditorSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    tileId: String?,
    initialText: String,
    onTextChange: (id: String, text: String) -> Unit,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "stickyNoteEditorProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)

    // Reseeded whenever a different tile is opened; never re-read after that
    // (each keystroke fires onTextChange itself — re-syncing from the prop on
    // every recomposition would fight the user's own typing).
    var text by remember(tileId) { mutableStateOf(initialText) }
    LaunchedEffect(tileId) { text = initialText }

    // A text area with no focus shows no cursor at all — claim focus and raise
    // the keyboard whenever this sheet opens (or a different tile's note opens
    // in it), same as [NotesSheet]'s own editor.
    val focusRequester = remember(tileId) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(tileId, visible) {
        if (visible) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    val context = LocalContext.current
    // Same SAF "save a copy" mechanism as NotesSheet's editor — the system
    // picker itself offers Google Drive alongside on-device storage.
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

    BackHandler(enabled = visible) { onDismiss() }

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

                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "‹ done",
                        color = accent,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onDismiss)
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
                }
                Text(
                    text = "sticky note",
                    color = tokens.fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W300,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                )
                BasicTextField(
                    value = text,
                    onValueChange = { newText ->
                        text = newText
                        tileId?.let { id -> onTextChange(id, newText) }
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
        }
    }
}
