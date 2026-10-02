package com.tileshell.feature.livetiles

import com.tileshell.core.design.HubPanorama
import com.tileshell.core.design.HubAppBarAction
import com.tileshell.core.design.HubAppBar
import com.tileshell.core.design.HubFilter
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.tileshell.core.data.SunTimes
import com.tileshell.core.data.MoonTimes
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Festival names: bright gold on dark, a deeper gold that reads on light. */
private fun gold(dark: Boolean) = if (dark) Color(0xFFFFD66B) else Color(0xFF9A6A00)

private val PIVOTS = listOf("आज", "हा महिना", "हे वर्ष")

/** Gregorian month names in Devanagari (Marathi). */
private val DEV_MONTHS = listOf(
    "जानेवारी", "फेब्रुवारी", "मार्च", "एप्रिल", "मे", "जून",
    "जुलै", "ऑगस्ट", "सप्टेंबर", "ऑक्टोबर", "नोव्हेंबर", "डिसेंबर",
)
private val VARA_KEYS = listOf("ravivara", "somavara", "mangalavara", "budhavara", "guruvara", "shukravara", "shanivara")

/** "मंगल ६" — short weekday and date, in Devanagari. */
internal fun devanagariDay(day: Long, withMonth: Boolean = false): String {
    val c = Calendar.getInstance().apply { timeInMillis = day }
    val vara = PanchangDevanagari.shortVara(VARA_KEYS[c.get(Calendar.DAY_OF_WEEK) - 1])
    val date = PanchangDevanagari.digits(c.get(Calendar.DAY_OF_MONTH))
    return if (withMonth) "$vara $date ${DEV_MONTHS[c.get(Calendar.MONTH)]}" else "$vara $date"
}

private fun monthTitle(year: Int, month: Int) = "${DEV_MONTHS[month]} ${PanchangDevanagari.digits(year)}"

private fun monthStart(year: Int, month: Int): Long = Calendar.getInstance().apply {
    clear(); set(year, month, 1)
}.timeInMillis

private fun daysIn(year: Int, month: Int): Int = Calendar.getInstance().apply {
    clear(); set(year, month, 1)
}.getActualMaximum(Calendar.DAY_OF_MONTH)

/**
 * The Panchang sheet (tile and widget tap), all in Devanagari: today, a month
 * and the year — each with important days and festivals kept apart — and a
 * separate highlight settings page.
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
    var settingsOpen by remember { mutableStateOf(false) }
    if (!visible && progress == 0f) {
        settingsOpen = false
        return
    }

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current
    BackHandler(enabled = visible) { if (settingsOpen) settingsOpen = false else onDismiss() }

    val settings by PanchangPrefs.settings(context).collectAsState()
    val location by produceState(initialValue = DEFAULT_LATITUDE to DEFAULT_LONGITUDE, context) {
        value = lastCoarseLocationOrDefault(context)
    }
    val now = remember(visible) { System.currentTimeMillis() }
    val nowCal = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val year = nowCal.get(Calendar.YEAR)
    // The whole calendar year, computed once in the background; the month
    // and year pages and today's "next" list all read it.
    val yearDays by produceState<List<Pair<Long, List<Observance>>>?>(initialValue = null, settings, location, year) {
        val s = settings ?: return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching {
                val start = monthStart(year, 0)
                val days = Calendar.getInstance().apply { timeInMillis = start }.getActualMaximum(Calendar.DAY_OF_YEAR)
                // Plus January of next year, so "next" still has something in late December.
                PanchangObservances.upcoming(start, days + 31, s, moonriseAfter = moonriseAfter(location.first, location.second), location = location)
            }.getOrDefault(emptyList())
        }
    }
    val pagerState = rememberPagerState(pageCount = { PIVOTS.size })
    val scope = rememberCoroutineScope()

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
            if (settingsOpen) {
                // The highlight choices as their own page, the way Lumia apps
                // showed settings: the app's name small, over a large light title.
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "पंचांग",
                        color = tokens.fg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 18.dp, top = 18.dp),
                    )
                    Text(
                        "महत्त्वाचे दिवस",
                        color = tokens.fg,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                    )
                    HighlightSettingsPage(settings ?: ObservanceSettings(), accent, tokens)
                }
            } else {
                HubPanorama(
                    title = "पंचांग",
                    sections = PIVOTS,
                    pagerState = pagerState,
                    tokens = tokens,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    when (page) {
                        0 -> TodayPage(now, location, yearDays, tokens, dark, accent) { settingsOpen = true }
                        1 -> MonthPage(nowCal, yearDays, settings, location, tokens, dark, accent)
                        else -> YearPage(now, year, yearDays, tokens, dark, accent)
                    }
                }
            }
            HubAppBar(
                tokens = tokens,
                actions = listOf(
                    HubAppBarAction("back", "back", "मागे") { if (settingsOpen) settingsOpen = false else onDismiss() },
                    HubAppBarAction("settings", "choose important days", "महत्त्वाचे दिवस") { settingsOpen = !settingsOpen },
                ),
            )
        }
    }
}

@Composable
private fun TodayPage(
    now: Long,
    location: Pair<Double, Double>,
    yearDays: List<Pair<Long, List<Observance>>>?,
    tokens: ColorTokens,
    dark: Boolean,
    accent: Color,
    onSettings: () -> Unit,
) {
    val panchang = remember(now) { HinduPanchang.panchangFor(now) }
    val today = PanchangObservances.startOfDay(now, TimeZone.getDefault())
    val todays = yearDays?.firstOrNull { it.first == today }?.second.orEmpty()
    val sun = remember(location) { SunTimes.nextSunriseSunset(today + 3 * 3_600_000L, location.first, location.second) }
    val moonrise = remember(location) { eveningMoonrise(now, location.first, location.second) }
    val moon = remember(location) { MoonTimes.nextMoonriseMoonset(today, location.first, location.second) }
    val next = yearDays.orEmpty().filter { it.first > today }.take(3)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
        item {
            // Today's tithi large, as on the tile (user-requested): the
            // Devanagari tithi number with the paksha stacked beside it, then
            // the tithi's name and month, then the day.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    PanchangDevanagari.tithiNumber(panchang.tithi),
                    color = tokens.fg,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light,
                    lineHeight = 80.sp,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    PanchangDevanagari.paksha(panchang.tithi.paksha).split(" ").forEach {
                        Text(it, color = tokens.fgDim, fontSize = 17.sp, lineHeight = 22.sp)
                    }
                }
            }
            Text(
                "${PanchangDevanagari.tithiName(panchang.tithi.name)} · ${PanchangDevanagari.month(panchang.month)}",
                color = TileAccents.Amber,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
                lineHeight = 38.sp,
            )
            Text(
                PanchangDevanagari.vara(panchang.vara),
                color = tokens.fg,
                fontSize = 17.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
            )
            Text(
                "नक्षत्र: ${PanchangDevanagari.nakshatra(panchang.nakshatra)} · शक ${PanchangDevanagari.digits(panchang.shakaSamvat)} · " +
                    "विक्रम ${PanchangDevanagari.digits(panchang.vikramSamvat)}",
                color = tokens.fgDim,
                fontSize = 13.sp,
            )
            Text(PanchangDevanagari.ayana(panchang.ayana), color = tokens.fgDim, fontSize = 13.sp)
            todays.forEach { o ->
                Column(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .background(TileAccents.Amber.copy(alpha = 0.16f))
                        .padding(10.dp),
                ) {
                    Text(o.name, color = if (o.festival) gold(dark) else tokens.fg, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    grahanDetail(o)?.let { Text(it, color = tokens.fgDim, fontSize = 13.sp) }
                    if (o.id == "sankashti" && moonrise != null) {
                        Text("चंद्रोदय ${formatClockTime12Devanagari(moonrise)}", color = tokens.fgDim, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            sun?.let {
                Text(
                    "सूर्योदय ${formatClockTime12Devanagari(it.sunriseMillis)} · सूर्यास्त ${formatClockTime12Devanagari(it.sunsetMillis)}",
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                )
            }
            Text(
                listOfNotNull(
                    moon.moonriseMillis?.let { "चंद्रोदय ${formatClockTime12Devanagari(it)}" },
                    moon.moonsetMillis?.let { "चंद्रास्त ${formatClockTime12Devanagari(it)}" },
                ).joinToString(" · "),
                color = tokens.fgDim,
                fontSize = 13.sp,
            )
        }
        item { SectionTitle("पुढील") }
        if (yearDays != null && next.isEmpty()) item { Empty("पुढे काही नाही", tokens) }
        next.forEach { (day, list) -> item(key = "n$day") { DayRow(devanagariDay(day, withMonth = true), list, tokens, dark, false) } }
        item {
            Text(
                "महत्त्वाचे दिवस निवडा ›",
                color = accent,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 18.dp).clickable(onClick = onSettings),
            )
            Note(tokens)
        }
    }
}

@Composable
private fun MonthPage(
    nowCal: Calendar,
    yearDays: List<Pair<Long, List<Observance>>>?,
    settings: ObservanceSettings?,
    location: Pair<Double, Double>,
    tokens: ColorTokens,
    dark: Boolean,
    accent: Color,
) {
    var offset by remember { mutableStateOf(0) }
    val shown = Calendar.getInstance().apply {
        timeInMillis = monthStart(nowCal.get(Calendar.YEAR), nowCal.get(Calendar.MONTH))
        add(Calendar.MONTH, offset)
    }
    val y = shown.get(Calendar.YEAR)
    val m = shown.get(Calendar.MONTH)
    val start = monthStart(y, m)
    val end = start + daysIn(y, m) * 86_400_000L
    // In the precomputed year when possible; other months are worked out here.
    val inYear = yearDays != null && y == nowCal.get(Calendar.YEAR)
    val computed by produceState<List<Pair<Long, List<Observance>>>?>(initialValue = null, y, m, settings, location, inYear) {
        if (inYear) return@produceState
        val s = settings ?: return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching {
                PanchangObservances.upcoming(start, daysIn(y, m), s, moonriseAfter = moonriseAfter(location.first, location.second), location = location)
            }.getOrDefault(emptyList())
        }
    }
    val days = if (inYear) yearDays.orEmpty().filter { it.first in start until end } else computed
    val today = PanchangObservances.startOfDay(System.currentTimeMillis(), TimeZone.getDefault())
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
        item {
            val prev = DEV_MONTHS[(m + 11) % 12]
            val nextName = DEV_MONTHS[(m + 1) % 12]
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("‹ $prev", color = accent, fontSize = 14.sp, modifier = Modifier.clickable { offset-- }.padding(vertical = 6.dp))
                Text(
                    monthTitle(y, m),
                    color = tokens.fg,
                    fontSize = 17.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Text("$nextName ›", color = accent, fontSize = 14.sp, modifier = Modifier.clickable { offset++ }.padding(vertical = 6.dp))
            }
        }
        observanceSections(days, tokens, dark, today) { devanagariDay(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YearPage(
    now: Long,
    year: Int,
    yearDays: List<Pair<Long, List<Observance>>>?,
    tokens: ColorTokens,
    dark: Boolean,
    accent: Color,
) {
    var group by remember { mutableStateOf(ObservanceGroup.FESTIVALS) }
    val today = PanchangObservances.startOfDay(now, TimeZone.getDefault())
    val end = monthStart(year + 1, 0)
    val inYear = yearDays.orEmpty().filter { it.first < end }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(PanchangDevanagari.digits(year), color = tokens.fg, fontSize = 17.sp, modifier = Modifier.padding(end = 8.dp))
                ObservanceGroup.entries.forEach { g -> Pill(g.title, group == g, accent, tokens) { group = g } }
            }
        }
        if (yearDays == null) item { Empty("मोजत आहे…", tokens) }
        val rows = inYear.mapNotNull { (day, list) ->
            list.filter { group.has(it) }.takeIf { it.isNotEmpty() }?.let { day to it }
        }
        rows.groupBy { Calendar.getInstance().apply { timeInMillis = it.first }.get(Calendar.MONTH) }
            .toSortedMap()
            .forEach { (month, entries) ->
                item(key = "m$month") { SectionTitle(DEV_MONTHS[month]) }
                entries.forEach { (day, list) ->
                    item(key = "y$day") { DayRow(devanagariDay(day), list, tokens, dark, past = day < today) }
                }
            }
        if (yearDays != null && rows.isEmpty()) item { Empty("काही नाही", tokens) }
    }
}

/** The sheet's three kinds of day, each its own section. */
private enum class ObservanceGroup(val title: String) {
    IMPORTANT("महत्त्वाचे दिवस"), FESTIVALS("सण"), GRAHAN("ग्रहण");

    fun has(o: Observance) = when (this) {
        IMPORTANT -> !o.festival && !o.grahan
        FESTIVALS -> o.festival
        GRAHAN -> o.grahan
    }
}

/** Important days, festivals and any grahan, for the days in [days]. */
private fun LazyListScope.observanceSections(
    days: List<Pair<Long, List<Observance>>>?,
    tokens: ColorTokens,
    dark: Boolean,
    today: Long,
    label: (Long) -> String,
) {
    ObservanceGroup.entries.forEach { group ->
        val rows = days.orEmpty().mapNotNull { (day, list) ->
            list.filter { group.has(it) }.takeIf { it.isNotEmpty() }?.let { day to it }
        }
        // No grahan this month is the usual case: leave that section out.
        if (group == ObservanceGroup.GRAHAN && rows.isEmpty()) return@forEach
        item(key = "s$group") { SectionTitle(group.title) }
        if (days == null) item(key = "l$group") { Empty("मोजत आहे…", tokens) }
        else if (rows.isEmpty()) item(key = "e$group") { Empty("काही नाही", tokens) }
        rows.forEach { (day, list) ->
            item(key = "r$group$day") { DayRow(label(day), list, tokens, dark, past = day < today) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HighlightSettingsPage(settings: ObservanceSettings, accent: Color, tokens: ColorTokens) {
    val context = LocalContext.current
    var paksha by remember { mutableStateOf<Paksha?>(Paksha.SHUKLA) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
        item {
            Text("टाइल, विजेट आणि पंचांगावर दाखवायचे दिवस · चालू/बंद करण्यासाठी टॅप करा", color = tokens.fgDim, fontSize = 13.sp)
            SectionTitle("महत्त्वाचे दिवस")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PanchangObservances.HIGHLIGHTS.forEach { h ->
                    val on = h.id in settings.highlights
                    Pill(h.name, on, accent, tokens) {
                        PanchangPrefs.update(context) { it.copy(highlights = if (on) it.highlights - h.id else it.highlights + h.id) }
                    }
                }
            }
            SectionTitle("सण")
            Pill("हिंदू सण दाखवा", settings.festivals, accent, tokens) {
                PanchangPrefs.update(context) { it.copy(festivals = !settings.festivals) }
            }
            SectionTitle("ग्रहण")
            Pill("सूर्य व चंद्र ग्रहण दाखवा", settings.grahan, accent, tokens) {
                PanchangPrefs.update(context) { it.copy(grahan = !settings.grahan) }
            }
            SectionTitle("तुमची तिथी")
            Text("दर महिन्याला, सूर्योदयाच्या तिथीनुसार", color = tokens.fgDim, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Paksha.SHUKLA to "शुक्ल", Paksha.KRISHNA to "कृष्ण", null to "दोन्ही").forEach { (value, label) ->
                    Pill(label, paksha == value, accent, tokens) { paksha = value }
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..15).forEach { n ->
                    val key = PanchangObservances.customKey(paksha, n)
                    val on = key in settings.customTithis
                    Pill(PanchangDevanagari.digits(n), on, accent, tokens) {
                        PanchangPrefs.update(context) { it.copy(customTithis = if (on) it.customTithis - key else it.customTithis + key) }
                    }
                }
            }
            if (settings.customTithis.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    settings.customTithis.sorted().forEach { key ->
                        val (p, t) = PanchangObservances.parseCustomKey(key) ?: return@forEach
                        Pill("${PanchangObservances.customName(p, t).first}  ×", true, accent, tokens) {
                            PanchangPrefs.update(context) { it.copy(customTithis = it.customTithis - key) }
                        }
                    }
                }
            }
            Note(tokens)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = TileAccents.Amber, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
}

@Composable
private fun Empty(text: String, tokens: ColorTokens) {
    Text(text, color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun Note(tokens: ColorTokens) {
    Text(
        "तिथी सूर्योदयाला, संकष्टी चंद्रोदयाला आणि सण त्यांच्या नेहमीच्या वेळी पाहिले जातात. अधिक मासात सण दाखवले जात नाहीत. " +
            "ग्रहणाच्या वेळा तुमच्या ठिकाणानुसार आहेत (मांद्य चंद्रग्रहण दाखवले जात नाही). " +
            "तुमच्या स्थानिक पंचांगापेक्षा एखाद्या दिवसाचा फरक असू शकतो.",
        color = tokens.fgDim,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
private fun DayRow(date: String, list: List<Observance>, tokens: ColorTokens, dark: Boolean, past: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp).graphicsLayer { alpha = if (past) 0.45f else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            list.forEach { o ->
                Text(o.name, color = if (o.festival) gold(dark) else tokens.fg, fontSize = 15.sp)
                grahanDetail(o)?.let { Text(it, color = tokens.fgDim, fontSize = 12.sp) }
            }
        }
        Text(date, color = tokens.fgDim, fontSize = 13.sp)
    }
}

/** "स्पर्श ७:१२ · मोक्ष ९:३४" when seen here, else "येथे दिसणार नाही". */
private fun grahanDetail(o: Observance): String? {
    val e = o.eclipse ?: return null
    val start = e.visibleStart
    val end = e.visibleEnd
    if (start == null || end == null) return "येथे दिसणार नाही"
    return "स्पर्श ${formatClockTime12Devanagari(start)} · मोक्ष ${formatClockTime12Devanagari(end)}"
}

@Composable
private fun Pill(label: String, on: Boolean, accent: Color, tokens: ColorTokens, onClick: () -> Unit) {
    HubFilter(label, on, tokens, accent, onClick = onClick)
}
