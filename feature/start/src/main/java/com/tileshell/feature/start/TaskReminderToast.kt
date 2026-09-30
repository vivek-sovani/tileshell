package com.tileshell.feature.start

import android.media.AudioManager
import android.media.RingtoneManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TaskRepository
import com.tileshell.core.data.reminders.DueReminder
import com.tileshell.core.data.reminders.TaskReminders
import com.tileshell.core.design.Glass
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.isLightBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val TOAST_SHOW_MS = 10_000L

/**
 * Windows Phone–style toast for a task reminder that goes off while Start is
 * on screen: a full-width accent strip across the top with the task, done and
 * snooze. Tap opens the task's list; swipe up or sideways dismisses; it also
 * hides itself after [TOAST_SHOW_MS]. The system notification is posted
 * silently meanwhile (see [TaskReminders.fire]), so the toast plays the
 * notification sound itself.
 */
@Composable
internal fun TaskReminderToast(accent: Color, onOpen: (listId: String) -> Unit, modifier: Modifier = Modifier) {
    if (!TaskReminders.ENABLED) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<DueReminder?>(null) }
    var shown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        TaskReminders.toasts.collect { reminder ->
            current = reminder
            shown = true
            playReminderSound(context)
        }
    }
    LaunchedEffect(current, shown) {
        if (shown) {
            delay(TOAST_SHOW_MS)
            shown = false
        }
    }

    val fg = Glass.faceTextColor(useDarkText = isLightBackground(accent))
    AnimatedVisibility(
        visible = shown && current != null,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = modifier,
    ) {
        val reminder = current ?: return@AnimatedVisibility
        fun dismiss() {
            shown = false
            TaskReminders.cancelNotification(context, reminder.taskId)
        }
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .background(accent)
                .statusBarsPadding()
                .pointerInput(reminder.taskId) {
                    var total = androidx.compose.ui.geometry.Offset.Zero
                    detectDragGestures(
                        onDragStart = { total = androidx.compose.ui.geometry.Offset.Zero },
                        onDragEnd = { if (total.y < -40f || abs(total.x) > 80f) shown = false },
                    ) { change, amount ->
                        change.consume()
                        total += amount
                    }
                }
                .clickable {
                    dismiss()
                    onOpen(reminder.listId)
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(TileIcons["check"], contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        reminder.text,
                        color = fg,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${reminder.listName.lowercase()} · ${reminder.whenText}",
                        color = fg.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
                Row(modifier = Modifier.padding(top = 6.dp)) {
                    Text(
                        "done",
                        color = fg,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                dismiss()
                                scope.launch(Dispatchers.IO) { TaskRepository.create(context).setDone(reminder.taskId, true) }
                            }
                            .padding(end = 20.dp, top = 2.dp, bottom = 2.dp),
                    )
                    Text(
                        "snooze 10 min",
                        color = fg,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                shown = false
                                scope.launch(Dispatchers.IO) { TaskReminders.snooze(context, reminder.taskId) }
                            }
                            .padding(top = 2.dp, bottom = 2.dp),
                    )
                }
            }
        }
    }
}

/** The default notification sound, only when the ringer is on (never in silent/vibrate). */
private fun playReminderSound(context: android.content.Context) {
    runCatching {
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
    }
}
