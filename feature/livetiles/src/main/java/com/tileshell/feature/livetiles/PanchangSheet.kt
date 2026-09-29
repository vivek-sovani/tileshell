package com.tileshell.feature.livetiles

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.HinduPanchang
import com.tileshell.core.data.Observance
import com.tileshell.core.data.ObservanceSettings
import com.tileshell.core.data.Paksha
import com.tileshell.core.data.PanchangDevanagari
import com.tileshell.core.data.PanchangObservances
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Festival names: bright gold on dark, a deeper gold that reads on light. */
private fun gold(dark: Boolean) = if (dark) Color(0xFFFFD66B) else Color(0xFF9A6A00)
private const val UPCOMING_DAYS = 60

/**
 * The Panchang tile's sheet: today's tithi and any highlight, the highlighted
 * days and festivals in the next [UPCOMING_DAYS] days, and which tithis to
 * highlight.
 */
@Composable
fun PanchangSheet(
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
        label = "panchangSheetProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current
    BackHandler(enabled = visible, onBack = onDismiss)

    val settings by PanchangPrefs.settings(context).collectAsState()
    val location by produceState(initialValue = DEFAULT_LATITUDE to DEFAULT_LONGITUDE, context) {
        value = lastCoarseLocationOrDefault(context)
    }
    val now = remember { System.currentTimeMillis() }
    val panchang = remember(now) { HinduPanchang.panchangFor(now) }
    // Today and the coming days, with the same highlights as the tile.
    val days by produceState<List<Pair<Long, List<Observance>>>?>(initialValue = null, settings, location) {
        val s = settings ?: return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching {
                PanchangObservances.upcoming(now, UPCOMING_DAYS, s, moonriseAfter = moonriseAfter(location.first, location.second))
            }.getOrDefault(emptyList())
        }
    }
    val today = PanchangObservances.startOfDay(now, java.util.TimeZone.getDefault())
    val todays = days?.firstOrNull { it.first == today }?.second.orEmpty()
    val todayMoonrise = remember(location) { eveningMoonrise(now, location.first, location.second) }

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp)) {
                item {
                    Text("tileshell", color = tokens.fgDim, fontSize = 14.sp)
                    Text("panchang", color = accent, fontSize = 52.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${PanchangDevanagari.vara(panchang.vara)} · ${PanchangDevanagari.paksha(panchang.tithi.paksha)} " +
                            "${PanchangDevanagari.tithiName(panchang.tithi.name)} · ${PanchangDevanagari.month(panchang.month)}",
                        color = tokens.fg,
                        fontSize = 16.sp,
                    )
                    Text(
                        "नक्षत्र: ${PanchangDevanagari.nakshatra(panchang.nakshatra)}",
                        color = tokens.fgDim,
                        fontSize = 13.sp,
                    )
                    todays.forEach { o ->
                        Column(
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(TileAccents.Amber.copy(alpha = 0.16f))
                                .padding(10.dp),
                        ) {
                            Text(o.name, color = if (o.festival) gold(dark) else tokens.fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            val extra = if (o.id == "sankashti" && todayMoonrise != null) {
                                " · moonrise ${formatClockTime12(todayMoonrise)}"
                            } else {
                                ""
                            }
                            Text(o.english + extra, color = tokens.fgDim, fontSize = 12.sp)
                        }
                    }
                }
                item { SectionTitle("coming up", tokens) }
                val coming = days.orEmpty().filter { it.first != today }
                if (days != null && coming.isEmpty()) {
                    item { Text("nothing highlighted in the next $UPCOMING_DAYS days", color = tokens.fgDim, fontSize = 13.sp) }
                }
                coming.forEach { (day, list) ->
                    item(key = day) { UpcomingRow(day, list, tokens, dark) }
                }
                item { SectionTitle("highlight", tokens) }
                val current = settings ?: ObservanceSettings()
                PanchangObservances.HIGHLIGHTS.forEach { h ->
                    item(key = "h-${h.id}") {
                        SwitchRow(h.name, h.english, h.id in current.highlights, accent, tokens) { on ->
                            PanchangPrefs.update(context) {
                                it.copy(highlights = if (on) it.highlights + h.id else it.highlights - h.id)
                            }
                        }
                    }
                }
                item(key = "festivals") {
                    SwitchRow("सण", "hindu festivals", current.festivals, accent, tokens) { on ->
                        PanchangPrefs.update(context) { it.copy(festivals = on) }
                    }
                }
                item(key = "custom") { CustomTithiPicker(current, accent, tokens) }
                item {
                    Text(
                        "open in web search ›",
                        color = accent,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 20.dp).clickable {
                            runCatching {
                                val url = "https://www.google.com/search?q=" + Uri.encode("hindu panchang calendar today")
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                    )
                    Text(
                        "tithis are taken at sunrise, sankashti at moonrise, and festivals at their usual time of day. " +
                            "festivals are skipped in an adhik month. timings can differ from your local panchang by a day.",
                        color = tokens.fgDim,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, tokens: ColorTokens) {
    Text(text, color = TileAccents.Amber, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 22.dp, bottom = 4.dp))
}

@Composable
private fun UpcomingRow(day: Long, list: List<Observance>, tokens: ColorTokens, dark: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            list.forEach { o ->
                Text(o.name, color = if (o.festival) gold(dark) else tokens.fg, fontSize = 15.sp)
            }
        }
        Text(
            SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(Date(day)).lowercase(),
            color = tokens.fgDim,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, accent: Color, tokens: ColorTokens, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = tokens.fg, fontSize = 15.sp)
            Text(subtitle, color = tokens.fgDim, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White),
        )
    }
}

/** "your own tithi": pick a paksha, then tap a tithi number to add it; tap an added one to remove it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomTithiPicker(settings: ObservanceSettings, accent: Color, tokens: ColorTokens) {
    val context = LocalContext.current
    var paksha by remember { mutableStateOf<Paksha?>(Paksha.SHUKLA) }
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Text("your own tithi", color = tokens.fg, fontSize = 15.sp)
        Text("highlighted at sunrise, every month", color = tokens.fgDim, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Paksha.SHUKLA to "shukla", Paksha.KRISHNA to "krishna", null to "both").forEach { (value, label) ->
                Pill(label, paksha == value, accent, tokens) { paksha = value }
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..15).forEach { n ->
                val key = PanchangObservances.customKey(paksha, n)
                val on = key in settings.customTithis
                Pill(PanchangDevanagari.digits(n), on, accent, tokens) {
                    PanchangPrefs.update(context) {
                        it.copy(customTithis = if (on) it.customTithis - key else it.customTithis + key)
                    }
                }
            }
        }
        if (settings.customTithis.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                settings.customTithis.sorted().forEach { key ->
                    val (p, t) = PanchangObservances.parseCustomKey(key) ?: return@forEach
                    val (name, _) = PanchangObservances.customName(p, t)
                    Pill("$name  ×", true, accent, tokens) {
                        PanchangPrefs.update(context) { it.copy(customTithis = it.customTithis - key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(label: String, on: Boolean, accent: Color, tokens: ColorTokens, onClick: () -> Unit) {
    Text(
        label,
        color = if (on) Color.White else tokens.fg,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) accent else Color.Transparent)
            .border(1.dp, if (on) accent else tokens.tileLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}
