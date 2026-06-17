package com.tileshell.feature.start.feed

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.design.ColorTokens
import kotlinx.coroutines.launch

private const val WIDGET_HOST_ID = 0x54_53 // "TS"

/**
 * Hosts a single Android app widget on the feed's glance tab. Self-contained: it
 * owns an [AppWidgetHost] (started while composed), runs the system widget picker +
 * optional configure activity via activity-result launchers (so no MainActivity
 * plumbing is needed — the composition is already activity-hosted), persists the
 * bound widget id in [WidgetStore], and renders the live [android.appwidget.AppWidgetHostView]
 * via [AndroidView]. Empty → an "add a widget" prompt. Everything is guarded so a
 * device that blocks third-party widget hosting degrades to the prompt.
 */
@Composable
fun WidgetSlot(accent: Color, tokens: ColorTokens) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val host = remember { AppWidgetHost(appContext, WIDGET_HOST_ID) }
    DisposableEffect(host) {
        runCatching { host.startListening() }
        onDispose { runCatching { host.stopListening() } }
    }
    val manager = remember { AppWidgetManager.getInstance(appContext) }
    val store = remember(context) { WidgetStore.create(context) }
    val widget by store.data.collectAsStateWithLifecycle(initialValue = WidgetData())
    val scope = rememberCoroutineScope()
    var pendingId by remember { mutableStateOf(-1) }

    val configureLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val id = pendingId
        pendingId = -1
        if (result.resultCode == Activity.RESULT_OK && id != -1) {
            scope.launch { store.setWidgetId(id) }
        } else if (id != -1) {
            runCatching { host.deleteAppWidgetId(id) }
        }
    }
    val pickLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        when {
            result.resultCode == Activity.RESULT_OK && id != -1 -> {
                val info = manager.getAppWidgetInfo(id)
                if (info?.configure != null) {
                    pendingId = id
                    val cfg = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                        .setComponent(info.configure)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    runCatching { configureLauncher.launch(cfg) }
                        .onFailure { scope.launch { store.setWidgetId(id) } }
                } else {
                    scope.launch { store.setWidgetId(id) }
                }
            }
            id != -1 -> runCatching { host.deleteAppWidgetId(id) }
        }
    }

    fun pick() {
        val id = runCatching { host.allocateAppWidgetId() }.getOrNull() ?: return
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            // Empty custom lists keep some OEM pickers from crashing.
            .putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, ArrayList())
            .putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, ArrayList())
        runCatching { pickLauncher.launch(intent) }
            .onFailure { runCatching { host.deleteAppWidgetId(id) } }
    }

    fun remove(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }
        scope.launch { store.clear() }
    }

    val id = widget.widgetId
    val info = if (id != -1) manager.getAppWidgetInfo(id) else null

    // A bound widget that was uninstalled returns null info — forget the stale id.
    LaunchedEffect(id, info) {
        if (id != -1 && info == null) store.clear()
    }

    if (id != -1 && info != null) {
        Column {
            key(id) {
                AndroidView(
                    factory = { ctx -> host.createView(ctx.applicationContext, id, info) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(tokens.sheet)
                        .heightIn(min = 96.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SlotAction("change", accent) { pick() }
                SlotAction("remove", tokens.fgDim) { remove(id) }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, tokens.tileLine, RoundedCornerShape(20.dp))
                .clickable { pick() }
                .padding(vertical = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("+ add a widget", color = accent, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SlotAction(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        color = color,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
