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
import com.tileshell.core.data.PanchangLanguage
import com.tileshell.core.data.PanchangNames
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

private val VARA_KEYS = listOf("ravivara", "somavara", "mangalavara", "budhavara", "guruvara", "shukravara", "shanivara")

/** "मंगल ६" — short weekday and date, in the Panchang language. */
internal fun devanagariDay(day: Long, withMonth: Boolean = false, language: PanchangLanguage = PanchangLanguage.MARATHI): String {
    val c = Calendar.getInstance().apply { timeInMillis = day }
    val vara = PanchangNames.shortVara(language, VARA_KEYS[c.get(Calendar.DAY_OF_WEEK) - 1])
    val date = PanchangNames.digits(language, c.get(Calendar.DAY_OF_MONTH))
    return if (withMonth) "$vara $date ${PanchangText.gregorianMonth(language, c.get(Calendar.MONTH))}" else "$vara $date"
}

private fun monthTitle(year: Int, month: Int, language: PanchangLanguage) =
    "${PanchangText.gregorianMonth(language, month)} ${PanchangNames.digits(language, year)}"

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
    val language = settings?.language ?: PanchangLanguage.DEFAULT
    val pivots = listOf(PanchangText.PIVOT_TODAY, PanchangText.PIVOT_MONTH, PanchangText.PIVOT_YEAR).map { it.of(language) }
    val pagerState = rememberPagerState(pageCount = { pivots.size })
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.CompositionLocalProvider(LocalPanchangLanguage provides language) {
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
                        PanchangText.TITLE.of(language),
                        color = tokens.fg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 18.dp, top = 18.dp),
                    )
                    Text(
                        PanchangText.IMPORTANT_DAYS.of(language),
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
                    title = PanchangText.TITLE.of(language),
                    sections = pivots,
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
                    HubAppBarAction("back", "back", PanchangText.BACK.of(language)) { if (settingsOpen) settingsOpen = false else onDismiss() },
                    HubAppBarAction("settings", "choose important days", PanchangText.IMPORTANT_DAYS.of(language)) { settingsOpen = !settingsOpen },
                ),
            )
        }
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
    val lang = LocalPanchangLanguage.current
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
                    PanchangNames.tithiNumber(lang, panchang.tithi),
                    color = tokens.fg,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light,
                    lineHeight = 80.sp,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    PanchangNames.paksha(lang, panchang.tithi.paksha).split(" ").forEach {
                        Text(it, color = tokens.fgDim, fontSize = 17.sp, lineHeight = 22.sp)
                    }
                }
            }
            Text(
                "${PanchangNames.tithiName(lang, panchang.tithi.name)} · ${PanchangNames.month(lang, panchang.month)}",
                color = TileAccents.Amber,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
                lineHeight = 38.sp,
            )
            Text(
                PanchangNames.vara(lang, panchang.vara),
                color = tokens.fg,
                fontSize = 17.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
            )
            Text(
                "${PanchangText.NAKSHATRA.of(lang)}: ${PanchangNames.nakshatra(lang, panchang.nakshatra)} · ${PanchangText.SHAKA.of(lang)} ${PanchangNames.digits(lang, panchang.shakaSamvat)} · " +
                    "${PanchangText.VIKRAM.of(lang)} ${PanchangNames.digits(lang, panchang.vikramSamvat)}",
                color = tokens.fgDim,
                fontSize = 13.sp,
            )
            Text(PanchangNames.ayana(lang, panchang.ayana), color = tokens.fgDim, fontSize = 13.sp)
            todays.forEach { o ->
                Column(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .background(TileAccents.Amber.copy(alpha = 0.16f))
                        .padding(10.dp),
                ) {
                    Text(o.name, color = if (o.festival) gold(dark) else tokens.fg, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    grahanDetail(o, lang)?.let { Text(it, color = tokens.fgDim, fontSize = 13.sp) }
                    if (o.id == "sankashti" && moonrise != null) {
                        Text("${PanchangText.MOONRISE.of(lang)} ${formatClockTime12Panchang(moonrise, lang)}", color = tokens.fgDim, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            sun?.let {
                Text(
                    "${PanchangText.SUNRISE.of(lang)} ${formatClockTime12Panchang(it.sunriseMillis, lang)} · ${PanchangText.SUNSET.of(lang)} ${formatClockTime12Panchang(it.sunsetMillis, lang)}",
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                )
            }
            Text(
                listOfNotNull(
                    moon.moonriseMillis?.let { "${PanchangText.MOONRISE.of(lang)} ${formatClockTime12Panchang(it, lang)}" },
                    moon.moonsetMillis?.let { "${PanchangText.MOONSET.of(lang)} ${formatClockTime12Panchang(it, lang)}" },
                ).joinToString(" · "),
                color = tokens.fgDim,
                fontSize = 13.sp,
            )
        }
        item { SectionTitle(PanchangText.NEXT.of(lang)) }
        if (yearDays != null && next.isEmpty()) item { Empty(PanchangText.NOTHING_AHEAD.of(lang), tokens) }
        next.forEach { (day, list) -> item(key = "n$day") { DayRow(devanagariDay(day, withMonth = true, language = lang), list, tokens, dark, false) } }
        item {
            Text(
                PanchangText.CHOOSE_DAYS.of(lang),
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
    val lang = LocalPanchangLanguage.current
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
            val prev = PanchangText.gregorianMonth(lang, (m + 11) % 12)
            val nextName = PanchangText.gregorianMonth(lang, (m + 1) % 12)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("‹ $prev", color = accent, fontSize = 14.sp, modifier = Modifier.clickable { offset-- }.padding(vertical = 6.dp))
                Text(
                    monthTitle(y, m, lang),
                    color = tokens.fg,
                    fontSize = 17.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Text("$nextName ›", color = accent, fontSize = 14.sp, modifier = Modifier.clickable { offset++ }.padding(vertical = 6.dp))
            }
        }
        observanceSections(days, tokens, dark, today) { devanagariDay(it, language = lang) }
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
    val lang = LocalPanchangLanguage.current
    var group by remember { mutableStateOf(ObservanceGroup.FESTIVALS) }
    val today = PanchangObservances.startOfDay(now, TimeZone.getDefault())
    val end = monthStart(year + 1, 0)
    val inYear = yearDays.orEmpty().filter { it.first < end }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(PanchangNames.digits(lang, year), color = tokens.fg, fontSize = 17.sp, modifier = Modifier.padding(end = 8.dp))
                ObservanceGroup.entries.forEach { g -> Pill(g.titleIn(lang), group == g, accent, tokens) { group = g } }
            }
        }
        if (yearDays == null) item { Empty(PanchangText.CALCULATING.of(lang), tokens) }
        val rows = inYear.mapNotNull { (day, list) ->
            list.filter { group.has(it) }.takeIf { it.isNotEmpty() }?.let { day to it }
        }
        rows.groupBy { Calendar.getInstance().apply { timeInMillis = it.first }.get(Calendar.MONTH) }
            .toSortedMap()
            .forEach { (month, entries) ->
                item(key = "m$month") { SectionTitle(PanchangText.gregorianMonth(lang, month)) }
                entries.forEach { (day, list) ->
                    item(key = "y$day") { DayRow(devanagariDay(day, language = lang), list, tokens, dark, past = day < today) }
                }
            }
        if (yearDays != null && rows.isEmpty()) item { Empty(PanchangText.NOTHING.of(lang), tokens) }
    }
}

/** The sheet's three kinds of day, each its own section. */
private enum class ObservanceGroup(private val text: PanchangText) {
    IMPORTANT(PanchangText.IMPORTANT_DAYS), FESTIVALS(PanchangText.GROUP_FESTIVALS), GRAHAN(PanchangText.GROUP_GRAHAN);

    fun titleIn(language: PanchangLanguage) = text.of(language)

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
        item(key = "s$group") { SectionTitle(group.titleIn(LocalPanchangLanguage.current)) }
        if (days == null) item(key = "l$group") { Empty(PanchangText.CALCULATING.of(LocalPanchangLanguage.current), tokens) }
        else if (rows.isEmpty()) item(key = "e$group") { Empty(PanchangText.NOTHING.of(LocalPanchangLanguage.current), tokens) }
        rows.forEach { (day, list) ->
            item(key = "r$group$day") { DayRow(label(day), list, tokens, dark, past = day < today) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HighlightSettingsPage(settings: ObservanceSettings, accent: Color, tokens: ColorTokens) {
    val lang = LocalPanchangLanguage.current
    val context = LocalContext.current
    var paksha by remember { mutableStateOf<Paksha?>(Paksha.SHUKLA) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
        item {
            Text(PanchangText.SETTINGS_NOTE.of(lang), color = tokens.fgDim, fontSize = 13.sp)
            SectionTitle(PanchangText.LANGUAGE.of(lang))
            Text(PanchangText.LANGUAGE_NOTE.of(lang), color = tokens.fgDim, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PanchangLanguage.entries.forEach { l ->
                    Pill(l.nativeName, settings.language == l, accent, tokens) { PanchangPrefs.setLanguage(context, l) }
                }
            }
            SectionTitle(PanchangText.IMPORTANT_DAYS.of(lang))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PanchangObservances.HIGHLIGHTS.forEach { h ->
                    val on = h.id in settings.highlights
                    Pill(h.nameIn(lang), on, accent, tokens) {
                        PanchangPrefs.update(context) { it.copy(highlights = if (on) it.highlights - h.id else it.highlights + h.id) }
                    }
                }
            }
            SectionTitle(PanchangText.GROUP_FESTIVALS.of(lang))
            Pill(PanchangText.SHOW_FESTIVALS.of(lang), settings.festivals, accent, tokens) {
                PanchangPrefs.update(context) { it.copy(festivals = !settings.festivals) }
            }
            SectionTitle(PanchangText.GROUP_GRAHAN.of(lang))
            Pill(PanchangText.SHOW_GRAHAN.of(lang), settings.grahan, accent, tokens) {
                PanchangPrefs.update(context) { it.copy(grahan = !settings.grahan) }
            }
            SectionTitle(PanchangText.YOUR_TITHI.of(lang))
            Text(PanchangText.YOUR_TITHI_NOTE.of(lang), color = tokens.fgDim, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Paksha.SHUKLA to PanchangNames.pakshaShort(lang, Paksha.SHUKLA), Paksha.KRISHNA to PanchangNames.pakshaShort(lang, Paksha.KRISHNA), null to PanchangText.BOTH.of(lang)).forEach { (value, label) ->
                    Pill(label, paksha == value, accent, tokens) { paksha = value }
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..15).forEach { n ->
                    val key = PanchangObservances.customKey(paksha, n)
                    val on = key in settings.customTithis
                    Pill(PanchangNames.digits(lang, n), on, accent, tokens) {
                        PanchangPrefs.update(context) { it.copy(customTithis = if (on) it.customTithis - key else it.customTithis + key) }
                    }
                }
            }
            if (settings.customTithis.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    settings.customTithis.sorted().forEach { key ->
                        val (p, t) = PanchangObservances.parseCustomKey(key) ?: return@forEach
                        Pill("${PanchangObservances.customName(p, t, lang).first}  ×", true, accent, tokens) {
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
        PanchangText.FOOTNOTE.of(LocalPanchangLanguage.current),
        color = tokens.fgDim,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
private fun DayRow(date: String, list: List<Observance>, tokens: ColorTokens, dark: Boolean, past: Boolean) {
    val lang = LocalPanchangLanguage.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp).graphicsLayer { alpha = if (past) 0.45f else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            list.forEach { o ->
                Text(o.name, color = if (o.festival) gold(dark) else tokens.fg, fontSize = 15.sp)
                grahanDetail(o, lang)?.let { Text(it, color = tokens.fgDim, fontSize = 12.sp) }
            }
        }
        Text(date, color = tokens.fgDim, fontSize = 13.sp)
    }
}

/** "स्पर्श ७:१२ · मोक्ष ९:३४" when seen here, else "येथे दिसणार नाही". */
private fun grahanDetail(o: Observance, lang: PanchangLanguage): String? {
    val e = o.eclipse ?: return null
    val start = e.visibleStart
    val end = e.visibleEnd
    if (start == null || end == null) return PanchangText.NOT_VISIBLE.of(lang)
    return "${PanchangText.CONTACT.of(lang)} ${formatClockTime12Panchang(start, lang)} · ${PanchangText.RELEASE.of(lang)} ${formatClockTime12Panchang(end, lang)}"
}

@Composable
private fun Pill(label: String, on: Boolean, accent: Color, tokens: ColorTokens, onClick: () -> Unit) {
    HubFilter(label, on, tokens, accent, onClick = onClick)
}
