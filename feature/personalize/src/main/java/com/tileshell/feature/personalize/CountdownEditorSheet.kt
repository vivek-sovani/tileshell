package com.tileshell.feature.personalize

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_LABEL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

/**
 * The Countdown tile's own dedicated editor — opened by tapping that tile.
 * Like [StickyNoteEditorSheet] there's no list to navigate (one tile is one
 * countdown) and no in-sheet delete — removing one means unpinning its tile.
 * Unlike a sticky note, a countdown carries two fields (a target date and a
 * label), both re-sent together on every change since they're persisted as
 * one encoded string ([com.tileshell.core.data.CountdownTile]).
 */
@Composable
fun CountdownEditorSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    tileId: String?,
    initialTargetIsoDate: String,
    initialLabel: String,
    onDataChange: (id: String, targetIsoDate: String, label: String) -> Unit,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "countdownEditorProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current

    // Reseeded whenever a different tile is opened; never re-read after that,
    // same reasoning as StickyNoteEditorSheet's own `text` state — re-syncing
    // from the prop on every recomposition would fight the user's own typing.
    var label by remember(tileId) { mutableStateOf(initialLabel) }
    var targetIsoDate by remember(tileId) { mutableStateOf(initialTargetIsoDate) }
    LaunchedEffect(tileId) {
        label = initialLabel
        targetIsoDate = initialTargetIsoDate
    }

    fun commit(newLabel: String = label, newTargetIsoDate: String = targetIsoDate) {
        label = newLabel
        targetIsoDate = newTargetIsoDate
        tileId?.let { id -> onDataChange(id, newTargetIsoDate, newLabel) }
    }

    val pickedDate = remember(targetIsoDate) { runCatching { LocalDate.parse(targetIsoDate) }.getOrNull() }

    fun openDatePicker() {
        val base = pickedDate ?: LocalDate.now()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth -> commit(newTargetIsoDate = LocalDate.of(year, month + 1, dayOfMonth).toString()) },
            base.year,
            base.monthValue - 1,
            base.dayOfMonth,
        ).show()
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
                    .fillMaxHeight(0.6f)
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

                Text(
                    text = "‹ done",
                    color = accent,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onDismiss)
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
                )
                Text(
                    text = "countdown",
                    color = tokens.fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W300,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                )
                Text(
                    text = "label",
                    color = tokens.fgDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                )
                BasicTextField(
                    value = label,
                    onValueChange = { commit(newLabel = it) },
                    singleLine = true,
                    textStyle = TextStyle(color = tokens.fg, fontSize = 16.sp),
                    cursorBrush = SolidColor(accent),
                    decorationBox = { inner ->
                        if (label.isEmpty()) {
                            Text("e.g. birthday", color = tokens.fgDim.copy(alpha = 0.6f), fontSize = 16.sp)
                        }
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
                Text(
                    text = "target date",
                    color = tokens.fgDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.tileLine.copy(alpha = 0.4f))
                        .clickable(onClick = ::openDatePicker)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = TileIcons["countdown"],
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(
                        text = pickedDate?.format(DATE_LABEL_FORMAT)?.lowercase(Locale.ENGLISH) ?: "tap to pick a date",
                        color = if (pickedDate != null) tokens.fg else tokens.fgDim,
                        fontSize = 15.sp,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
