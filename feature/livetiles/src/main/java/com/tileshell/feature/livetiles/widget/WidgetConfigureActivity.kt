package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.CALENDAR_SYSTEMS
import com.tileshell.core.data.COMMODITY_CATEGORY_ORDER
import com.tileshell.core.data.COMMODITY_ITEMS
import com.tileshell.core.data.CURRENCY_CODES
import com.tileshell.core.data.CurrencyCode
import com.tileshell.core.data.CRICKET_LEAGUE_SLUG
import com.tileshell.core.data.CRICKET_TEAMS
import com.tileshell.core.data.IPL_TEAMS
import com.tileshell.core.data.STOCK_CATEGORIES
import com.tileshell.core.data.STOCK_CATEGORY_REGION_ORDER
import com.tileshell.core.data.SPORTS_LEAGUES
import com.tileshell.core.data.SPORTS_LEAGUE_CATEGORY_ORDER
import com.tileshell.core.data.SportsLeague
import com.tileshell.core.data.SportsTeam
import com.tileshell.core.data.SportsTile
import com.tileshell.core.data.StockCategory
import com.tileshell.core.data.StockQuote
import com.tileshell.core.data.StockSearchResult
import com.tileshell.core.data.StockSymbolRef
import com.tileshell.core.data.StockTile
import com.tileshell.core.data.currencyPairLabel
import com.tileshell.core.data.currencyPairTicker
import com.tileshell.core.data.fetchCommodityQuote
import com.tileshell.core.data.fetchSportsTeams
import com.tileshell.core.data.fetchStockQuote
import com.tileshell.core.data.fetchStockSearch
import com.tileshell.core.data.formatStockChangePercent
import com.tileshell.core.data.formatStockPrice
import com.tileshell.core.data.sportsLeagueFor
import com.tileshell.core.data.stockCategoryFor
import com.tileshell.core.design.TileAccents
import kotlinx.coroutines.delay

/**
 * Shown right after a home-screen widget is placed (`android:configure` in
 * each kind's `res/xml` metadata) — a per-instance colour override, "just
 * like the colour picker on a launcher tile": the same 14-swatch palette
 * (`TileAccents`) plus a "match tileshell's accent" option that clears the
 * override, so a widget-only user (never opens Personalize) can still pick a
 * colour without ever seeing the rest of the app.
 *
 * One shared Activity for every widget kind, not one per kind — which kind's
 * refresh worker to call after saving is resolved from the *placed widget's
 * own provider component* ([AppWidgetManager.getAppWidgetInfo]), never a
 * custom intent extra: the OS controls the launch intent for a configure
 * activity, so there is no room to pass one in.
 *
 * The calendar-system, stock, and commodity widgets each have genuine
 * required per-instance data with no sensible default — which of the 8
 * calendar systems, or which ticker symbol — so for those kinds specifically
 * this shows an extra step *first*: pick a system (see [CALENDAR_SYSTEMS]),
 * search for a stock, or pick a commodity/currency pair (see
 * [COMMODITY_ITEMS]), then the same colour step every other kind gets. All
 * steps live in one Composable's own local state rather than separate
 * Activities, since only the final (colour) step needs to persist anything
 * that finishes the flow.
 *
 * Stock/commodity both show a live-quote preview before confirming — the
 * same "see the real number before pinning" pattern the in-app
 * `StockPickerSheet`/`CommodityPickerSheet` already use — rather than
 * committing on the first tap, since a mistyped symbol or a stale catalog
 * entry would otherwise only surface later, silently, as "no data" on the
 * placed widget.
 */
class WidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val requiredStep = when (AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className) {
            CalendarSystemAppWidgetProvider::class.java.name -> RequiredStep.CALENDAR_SYSTEM
            StockAppWidgetProvider::class.java.name -> RequiredStep.STOCK
            CommodityAppWidgetProvider::class.java.name -> RequiredStep.COMMODITY
            SportsAppWidgetProvider::class.java.name -> RequiredStep.SPORTS
            StickyNoteAppWidgetProvider::class.java.name -> RequiredStep.STICKY_NOTE_TEXT
            else -> RequiredStep.NONE
        }
        val existingStock = WidgetConfigStore.stockSelectionEncoded(this, appWidgetId)?.let { StockTile.decode(it) }
        val existingCommodity = WidgetConfigStore.commoditySymbol(this, appWidgetId)
        val existingSports = WidgetConfigStore.sportsSelectionEncoded(this, appWidgetId)?.let { SportsTile.decode(it) }
        val existingStickyNoteText = WidgetStickyNoteStore.text(this, appWidgetId)

        setContent {
            ConfigureScreen(
                requiredStep = requiredStep,
                existingStock = existingStock,
                existingCommodity = existingCommodity,
                existingSports = existingSports,
                existingStickyNoteText = existingStickyNoteText,
                onSystemPicked = { systemId -> WidgetConfigStore.setCalendarSystemId(this, appWidgetId, systemId) },
                onStockPicked = { encoded -> WidgetConfigStore.setStockSelectionEncoded(this, appWidgetId, encoded) },
                onCommodityPicked = { symbol, name -> WidgetConfigStore.setCommoditySymbol(this, appWidgetId, symbol, name) },
                onSportsPicked = { encoded -> WidgetConfigStore.setSportsSelectionEncoded(this, appWidgetId, encoded) },
                onStickyNoteTextPicked = { text -> WidgetStickyNoteStore.setText(this, appWidgetId, text) },
                onColorPicked = ::save,
            )
        }
    }

    private fun save(colorId: String?) {
        WidgetColorStore.setColorId(this, appWidgetId, colorId)
        refreshOwningWidget()
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    private fun refreshOwningWidget() {
        when (AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className) {
            WeatherAppWidgetProvider::class.java.name -> WeatherWidgetRefreshWorker.refreshNow(this)
            BatteryAppWidgetProvider::class.java.name -> BatteryWidgetRefreshWorker.refreshNow(this)
            AlarmAppWidgetProvider::class.java.name -> AlarmWidgetRefreshWorker.refreshNow(this)
            MoonPhaseAppWidgetProvider::class.java.name -> MoonPhaseWidgetRefreshWorker.refreshNow(this)
            StepsAppWidgetProvider::class.java.name -> StepsWidgetRefreshWorker.refreshNow(this)
            CalendarSystemAppWidgetProvider::class.java.name -> CalendarSystemWidgetRefreshWorker.refreshNow(this)
            FlashlightAppWidgetProvider::class.java.name -> FlashlightWidgetRefreshWorker.refreshNow(this)
            StockAppWidgetProvider::class.java.name -> StockWidgetRefreshWorker.refreshNow(this)
            CommodityAppWidgetProvider::class.java.name -> CommodityWidgetRefreshWorker.refreshNow(this)
            SportsAppWidgetProvider::class.java.name -> SportsWidgetRefreshWorker.refreshNow(this)
            TasksAppWidgetProvider::class.java.name -> TasksWidgetRefreshWorker.refreshNow(this)
            NotesAppWidgetProvider::class.java.name -> NotesWidgetRefreshWorker.refreshNow(this)
            StickyNoteAppWidgetProvider::class.java.name -> StickyNoteWidgetRefreshWorker.refreshNow(this)
        }
    }
}

private enum class RequiredStep { NONE, CALENDAR_SYSTEM, STOCK, COMMODITY, SPORTS, STICKY_NOTE_TEXT }
private enum class ConfigureStep { FIRST, COLOR }

@Composable
private fun ConfigureScreen(
    requiredStep: RequiredStep,
    existingStock: StockTile.Selection?,
    existingCommodity: Pair<String, String>?,
    existingSports: SportsTile.Selection?,
    existingStickyNoteText: String,
    onSystemPicked: (String) -> Unit,
    onStockPicked: (encoded: String) -> Unit,
    onCommodityPicked: (symbol: String, displayName: String) -> Unit,
    onSportsPicked: (encoded: String) -> Unit,
    onStickyNoteTextPicked: (String) -> Unit,
    onColorPicked: (String?) -> Unit,
) {
    var step by remember { mutableStateOf(if (requiredStep == RequiredStep.NONE) ConfigureStep.COLOR else ConfigureStep.FIRST) }
    // This app targets Android 15+ (API 35), where edge-to-edge is enforced —
    // the window draws behind the status bar whether or not the Activity
    // opted in, so every screen below needs its own inset padding or its
    // title renders straight under the status bar icons. Applied once here,
    // at the switch's outer boundary, rather than inside each of the ~10
    // screen composables below (every one of them starts with its own
    // `Modifier.fillMaxSize()` root) — this Box just reserves the status
    // bar's height up front, and each child's own `fillMaxSize()` then fills
    // whatever's left underneath it.
    Box(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        when (step) {
            ConfigureStep.FIRST -> when (requiredStep) {
                RequiredStep.CALENDAR_SYSTEM -> SystemPickerScreen(
                    onPick = { systemId ->
                        onSystemPicked(systemId)
                        step = ConfigureStep.COLOR
                    },
                )
                RequiredStep.STOCK -> StockPickerScreen(
                    initial = existingStock,
                    onPick = { encoded ->
                        onStockPicked(encoded)
                        step = ConfigureStep.COLOR
                    },
                )
                RequiredStep.COMMODITY -> CommodityPickerScreen(
                    initial = existingCommodity,
                    onPick = { symbol, name ->
                        onCommodityPicked(symbol, name)
                        step = ConfigureStep.COLOR
                    },
                )
                RequiredStep.SPORTS -> SportsPickerScreen(
                    initial = existingSports,
                    onPick = { encoded ->
                        onSportsPicked(encoded)
                        step = ConfigureStep.COLOR
                    },
                )
                RequiredStep.STICKY_NOTE_TEXT -> StickyNoteTextScreen(
                    initial = existingStickyNoteText,
                    onPick = { text ->
                        onStickyNoteTextPicked(text)
                        step = ConfigureStep.COLOR
                    },
                )
                RequiredStep.NONE -> Unit
            }
            ConfigureStep.COLOR -> ColorPickerScreen(onPick = onColorPicked)
        }
    }
}

@Composable
private fun SystemPickerScreen(onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0D))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            text = "choose a calendar system",
            color = Color(0xFFF6F6F8),
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "front shows today in this system, roman date at the bottom. hindu panchang's " +
                "back repeats it in english; every other system's back is the plain roman date.",
            color = Color(0xFFF6F6F8).copy(alpha = 0.65f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))
        CALENDAR_SYSTEMS.forEach { system ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF6F6F8).copy(alpha = 0.08f))
                    .clickable { onPick(system.id) }
                    .padding(16.dp),
            ) {
                Text(system.displayName, color = Color(0xFFF6F6F8), fontSize = 16.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

private val ConfigFg = Color(0xFFF6F6F8)
private val ConfigFgDim = Color(0xFFF6F6F8).copy(alpha = 0.65f)
private val ConfigDivider = Color(0xFFF6F6F8).copy(alpha = 0.12f)
private val ConfigAccent = Color(0xFF2B78E4)
private val PreviewPositiveGreen = Color(0xFF35C759)
private val PreviewNegativeRed = Color(0xFFFF453A)

/**
 * The sticky-note widget's own required first step — write the note's text
 * before the shared colour picker. Reopening the gear later (see
 * [StickyNoteWidgetRefreshWorker]) lands back here too, seeded with
 * [initial], so this doubles as the widget's only way to *edit* its text
 * after the first placement.
 */
@Composable
private fun StickyNoteTextScreen(initial: String, onPick: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0D)).padding(20.dp)) {
        Text("write your note", color = ConfigFg, fontSize = 20.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "shown right on the widget — one note per widget",
            color = ConfigFgDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(ConfigFg.copy(alpha = 0.06f))
                .padding(16.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = ConfigFg, fontSize = 15.sp, lineHeight = 21.sp),
                cursorBrush = SolidColor(ConfigAccent),
                modifier = Modifier.fillMaxSize(),
                decorationBox = { inner ->
                    if (text.isEmpty()) Text("tap to start writing…", color = ConfigFgDim.copy(alpha = 0.6f), fontSize = 15.sp)
                    inner()
                },
            )
        }
        Spacer(Modifier.height(20.dp))
        ConfirmButton("save", enabled = true, onClick = { onPick(text) })
    }
}

/**
 * Full parity with the in-app [com.tileshell.core.data.StockTile.Selection]
 * model — search for a single stock, follow a curated sector basket, or
 * build a custom multi-stock list, each with a live-quote preview before
 * confirming — mirroring `StockPickerSheet`'s own
 * Browse/PreviewSingle/PreviewCategory/MultiSelect/PreviewMulti stages
 * (`:feature:personalize`) rather than the narrower single-symbol-only shape
 * this widget shipped with initially.
 */
private sealed class StockConfigureStage {
    object Browse : StockConfigureStage()
    data class PreviewSingle(val symbol: String, val displayName: String) : StockConfigureStage()
    data class PreviewCategory(val category: StockCategory) : StockConfigureStage()
    data class MultiSelect(val selected: List<StockSearchResult>) : StockConfigureStage()
    data class PreviewMulti(val picks: List<StockSearchResult>) : StockConfigureStage()
}

/**
 * Seeds the picker at the stage matching whatever this widget already has
 * saved, so reopening the gear on an existing multi-stock/category widget
 * lands on that same list/basket — with the search box right there to add
 * more or (for a list) tap an existing checkmark to remove one — instead of
 * discarding the current pick and starting over at a blank Browse every
 * time. A single/category pick has no "list" to edit, so it seeds straight
 * into that item's own preview (tap `‹ back` to choose something else via
 * Browse instead).
 */
private fun initialStockStage(selection: StockTile.Selection?): StockConfigureStage = when (selection) {
    null -> StockConfigureStage.Browse
    is StockTile.Selection.Single -> StockConfigureStage.PreviewSingle(selection.symbol, selection.displayName)
    is StockTile.Selection.Category -> stockCategoryFor(selection.categoryId)
        ?.let { StockConfigureStage.PreviewCategory(it) }
        ?: StockConfigureStage.Browse
    is StockTile.Selection.MultiStock -> StockConfigureStage.MultiSelect(
        selection.symbols.map { (symbol, name) -> StockSearchResult(symbol, name, "") },
    )
}

@Composable
private fun StockPickerScreen(initial: StockTile.Selection?, onPick: (encoded: String) -> Unit) {
    var stage by remember { mutableStateOf(initialStockStage(initial)) }
    when (val current = stage) {
        StockConfigureStage.Browse -> StockBrowseScreen(
            onPickSearchResult = { result -> stage = StockConfigureStage.PreviewSingle(result.symbol, result.displayName) },
            onPickCategory = { category -> stage = StockConfigureStage.PreviewCategory(category) },
            onBuildCustomList = { stage = StockConfigureStage.MultiSelect(emptyList()) },
        )
        is StockConfigureStage.PreviewSingle -> QuotePreviewScreen(
            displayName = current.displayName,
            symbol = current.symbol,
            fetch = ::fetchStockQuote,
            onBack = { stage = StockConfigureStage.Browse },
            onConfirm = { onPick(StockTile.encodeSingle(current.symbol, current.displayName)) },
        )
        is StockConfigureStage.PreviewCategory -> StockPreviewCategoryScreen(
            category = current.category,
            onBack = { stage = StockConfigureStage.Browse },
            onConfirm = { onPick(StockTile.encodeCategory(current.category.id, current.category.displayName)) },
        )
        is StockConfigureStage.MultiSelect -> StockMultiSelectScreen(
            selected = current.selected,
            onBack = { stage = StockConfigureStage.Browse },
            onToggle = { result ->
                val already = current.selected.any { it.symbol == result.symbol }
                stage = StockConfigureStage.MultiSelect(
                    if (already) current.selected.filterNot { it.symbol == result.symbol } else current.selected + result,
                )
            },
            onNext = { stage = StockConfigureStage.PreviewMulti(current.selected) },
        )
        is StockConfigureStage.PreviewMulti -> StockPreviewMultiScreen(
            picks = current.picks,
            onBack = { stage = StockConfigureStage.MultiSelect(current.picks) },
            onConfirm = {
                val symbols = current.picks.map { it.symbol to it.displayName }
                // A generic label, not a name-based one — every member is already listed
                // individually on the widget's own back face, so a title trying to also
                // cram every name in (consolidated or not) is redundant.
                onPick(StockTile.encodeMultiStock(symbols, "custom list"))
            },
        )
    }
}

@Composable
private fun StockBrowseScreen(
    onPickSearchResult: (StockSearchResult) -> Unit,
    onPickCategory: (StockCategory) -> Unit,
    onBuildCustomList: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<StockSearchResult>>(emptyList()) }
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        results = fetchStockSearch(trimmed)
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0D)).verticalScroll(rememberScrollState()).padding(vertical = 20.dp)) {
        Text("choose a stock", color = ConfigFg, fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = "search a stock, follow a whole sector, or build a custom list",
            color = ConfigFgDim,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(16.dp))
        Box20 { SearchField(query = query, onQueryChange = { query = it }) }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = ConfigDivider, modifier = Modifier.padding(horizontal = 20.dp))

        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            if (results.isEmpty()) {
                Text("no matches yet", color = ConfigFgDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
            }
            results.forEach { result -> StockSearchRow(result, onClick = { onPickSearchResult(result) }) }
        } else {
            Text(
                text = "+ build a custom list of stocks",
                color = ConfigAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onBuildCustomList).padding(horizontal = 20.dp, vertical = 14.dp),
            )
            HorizontalDivider(color = ConfigDivider, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            STOCK_CATEGORY_REGION_ORDER.forEach { region ->
                val categories = STOCK_CATEGORIES.filter { it.region == region }
                if (categories.isEmpty()) return@forEach
                Text(
                    text = region,
                    color = ConfigFgDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                categories.forEach { category ->
                    Text(
                        text = category.displayName,
                        color = ConfigFg,
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = { onPickCategory(category) }).padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

/** `"SYMBOL · EXCHANGE"`, or just `"SYMBOL"` when the exchange is unknown — e.g. a member restored from a saved selection, which never stored its exchange. */
private fun stockSubtitle(result: StockSearchResult): String =
    if (result.exchange.isBlank()) result.symbol else "${result.symbol} · ${result.exchange}"

@Composable
private fun StockSearchRow(result: StockSearchResult, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(result.displayName, color = ConfigFg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stockSubtitle(result), color = ConfigFgDim, fontSize = 12.sp)
    }
}

@Composable
private fun StockPreviewCategoryScreen(category: StockCategory, onBack: () -> Unit, onConfirm: () -> Unit) {
    var quotes by remember(category) { mutableStateOf<Map<String, StockQuote?>>(emptyMap()) }
    var loading by remember(category) { mutableStateOf(true) }
    LaunchedEffect(category) {
        loading = true
        quotes = category.symbols.associate { it.symbol to fetchStockQuote(it.symbol) }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0D)).verticalScroll(rememberScrollState())) {
        BackRow(onBack)
        Text(category.displayName, color = ConfigFg, fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            text = "${category.symbols.size} stocks — the widget's front tracks ${category.indexDisplayName}",
            color = ConfigFgDim,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = ConfigDivider, modifier = Modifier.padding(horizontal = 20.dp))

        if (loading) {
            Text("loading…", color = ConfigFgDim, fontSize = 13.sp, modifier = Modifier.padding(20.dp))
        } else {
            category.symbols.forEach { ref -> MemberRow(ref.displayName, quotes[ref.symbol]) }
        }
        Spacer(Modifier.height(20.dp))
        Box20 { ConfirmButton("confirm", enabled = true, onClick = onConfirm) }
    }
}

@Composable
private fun MemberRow(displayName: String, quote: StockQuote?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = displayName,
            color = ConfigFg,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        if (quote != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatStockPrice(quote.price, quote.currency), color = ConfigFg, fontSize = 13.sp, maxLines = 1)
                Text(
                    text = formatStockChangePercent(quote.changePercent),
                    color = if (quote.change < 0) PreviewNegativeRed else PreviewPositiveGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Text("…", color = ConfigFgDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StockMultiSelectScreen(
    selected: List<StockSearchResult>,
    onBack: () -> Unit,
    onToggle: (StockSearchResult) -> Unit,
    onNext: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<StockSearchResult>>(emptyList()) }
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        results = fetchStockSearch(trimmed)
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0D)).verticalScroll(rememberScrollState())) {
        BackRow(onBack)
        Text("custom list", color = ConfigFg, fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            text = if (selected.isEmpty()) "search and tap to add stocks" else "${selected.size} selected",
            color = ConfigFgDim,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Box20 { SearchField(query = query, onQueryChange = { query = it }) }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = ConfigDivider, modifier = Modifier.padding(horizontal = 20.dp))

        val trimmed = query.trim()
        if (selected.isNotEmpty() && trimmed.length < 2) {
            Text(
                text = "selected",
                color = ConfigFgDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            selected.forEach { result -> MultiSelectRow(result, checked = true, onToggle = onToggle) }
        }
        if (trimmed.length >= 2) {
            results.forEach { result -> MultiSelectRow(result, checked = selected.any { it.symbol == result.symbol }, onToggle = onToggle) }
        }
        Spacer(Modifier.height(20.dp))
        Box20 {
            ConfirmButton(
                text = if (selected.isEmpty()) "add at least one stock" else "next (${selected.size} selected)",
                enabled = selected.isNotEmpty(),
                onClick = onNext,
            )
        }
    }
}

@Composable
private fun MultiSelectRow(result: StockSearchResult, checked: Boolean, onToggle: (StockSearchResult) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = { onToggle(result) }).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(result.displayName, color = ConfigFg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stockSubtitle(result), color = ConfigFgDim, fontSize = 12.sp)
        }
        Text(
            text = if (checked) "✓" else "+",
            color = if (checked) PreviewPositiveGreen else ConfigFgDim,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun StockPreviewMultiScreen(picks: List<StockSearchResult>, onBack: () -> Unit, onConfirm: () -> Unit) {
    var quotes by remember(picks) { mutableStateOf<Map<String, StockQuote?>>(emptyMap()) }
    var loading by remember(picks) { mutableStateOf(true) }
    LaunchedEffect(picks) {
        loading = true
        quotes = picks.associate { it.symbol to fetchStockQuote(it.symbol) }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0D)).verticalScroll(rememberScrollState())) {
        BackRow(onBack)
        Text(
            text = "custom list",
            color = ConfigFg,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            text = "${picks.size} stocks — each one listed below",
            color = ConfigFgDim,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = ConfigDivider, modifier = Modifier.padding(horizontal = 20.dp))

        if (loading) {
            Text("loading…", color = ConfigFgDim, fontSize = 13.sp, modifier = Modifier.padding(20.dp))
        } else {
            picks.forEach { pick -> MemberRow(pick.displayName, quotes[pick.symbol]) }
        }
        Spacer(Modifier.height(20.dp))
        Box20 { ConfirmButton("confirm", enabled = true, onClick = onConfirm) }
    }
}

/**
 * The commodity/currency widget's own picker — a flat curated list (no
 * search, mirroring `CommodityPickerSheet`, since there are only a handful
 * of items) plus a "build your own pair" custom base/target flow, each with
 * the same live-quote preview before confirming.
 */
private sealed class CommodityConfigureStage {
    object Browse : CommodityConfigureStage()
    data class Preview(val symbol: String, val displayName: String) : CommodityConfigureStage()
}

@Composable
private fun CommodityPickerScreen(initial: Pair<String, String>?, onPick: (symbol: String, displayName: String) -> Unit) {
    var stage by remember {
        mutableStateOf(initial?.let { (symbol, name) -> CommodityConfigureStage.Preview(symbol, name) } ?: CommodityConfigureStage.Browse)
    }
    var customBase by remember { mutableStateOf<CurrencyCode?>(null) }
    var customTarget by remember { mutableStateOf<CurrencyCode?>(null) }

    when (val current = stage) {
        CommodityConfigureStage.Browse -> CommodityBrowseScreen(
            customBase = customBase,
            customTarget = customTarget,
            onSelectCustomBase = { base -> customBase = base; if (customTarget?.code == base.code) customTarget = null },
            onSelectCustomTarget = { target -> customTarget = target; if (customBase?.code == target.code) customBase = null },
            onPick = { symbol, name -> stage = CommodityConfigureStage.Preview(symbol, name) },
            onContinueCustomPair = {
                val base = customBase
                val target = customTarget
                if (base != null && target != null) {
                    stage = CommodityConfigureStage.Preview(currencyPairTicker(base.code, target.code), currencyPairLabel(base.code, target.code))
                }
            },
        )
        is CommodityConfigureStage.Preview -> QuotePreviewScreen(
            displayName = current.displayName,
            symbol = current.symbol,
            fetch = ::fetchCommodityQuote,
            onBack = { stage = CommodityConfigureStage.Browse },
            onConfirm = { onPick(current.symbol, current.displayName) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommodityBrowseScreen(
    customBase: CurrencyCode?,
    customTarget: CurrencyCode?,
    onSelectCustomBase: (CurrencyCode) -> Unit,
    onSelectCustomTarget: (CurrencyCode) -> Unit,
    onPick: (symbol: String, displayName: String) -> Unit,
    onContinueCustomPair: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0D)).verticalScroll(rememberScrollState()).padding(vertical = 20.dp)) {
        Text("choose a commodity", color = ConfigFg, fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = "gold, silver, oil, or a currency pair — one per widget",
            color = ConfigFgDim,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = ConfigDivider, modifier = Modifier.padding(horizontal = 20.dp))

        COMMODITY_CATEGORY_ORDER.forEach { category ->
            val items = COMMODITY_ITEMS.filter { it.category == category }
            if (items.isEmpty()) return@forEach
            Text(
                text = category,
                color = ConfigFgDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            items.forEach { item ->
                Text(
                    text = item.displayName,
                    color = ConfigFg,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = { onPick(item.symbol, item.displayName) }).padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
            if (category == "currencies") {
                Text(
                    text = "or build your own pair",
                    color = ConfigAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 10.dp),
                )
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(
                        text = "convert from",
                        color = ConfigFgDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CURRENCY_CODES.filter { it.code != customTarget?.code }.forEach { currency ->
                            CurrencyPill(currency.code, selected = currency.code == customBase?.code, onClick = { onSelectCustomBase(currency) })
                        }
                    }
                }
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(
                        text = "convert to",
                        color = ConfigFgDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CURRENCY_CODES.filter { it.code != customBase?.code }.forEach { currency ->
                            CurrencyPill(currency.code, selected = currency.code == customTarget?.code, onClick = { onSelectCustomTarget(currency) })
                        }
                    }
                }
                Box20(top = 20.dp) {
                    ConfirmButton(
                        text = if (customBase != null && customTarget != null) "see live rate (${customBase.code} / ${customTarget.code})" else "pick both currencies",
                        enabled = customBase != null && customTarget != null,
                        onClick = onContinueCustomPair,
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyPill(code: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .then(if (selected) Modifier.background(ConfigAccent) else Modifier.border(1.dp, ConfigDivider, RoundedCornerShape(16.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(code, color = if (selected) Color.White else ConfigFg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * The sports widget's own picker — a flat two-step flow (league, then team),
 * mirroring the in-app `SportsPickerSheet`'s own League→Team navigation
 * exactly, including its lack of a live-quote preview stage (unlike Stock/
 * Commodity): a team is followed for its ongoing/upcoming schedule, not a
 * single live number, so there's nothing meaningful to preview before
 * confirming — tapping a team commits immediately, straight to the colour
 * step. The in-app sheet's "ongoing now" shortcut (surfacing live matches at
 * the top of the team list) is deliberately not replicated here — it needs
 * its own extra network call at Browse time for comparatively little benefit
 * over just picking your team from the list, the same kind of scope-
 * narrowing call already made for this widget's stock/commodity siblings.
 */
private sealed class SportsConfigureStage {
    object LeagueList : SportsConfigureStage()
    data class TeamList(val league: SportsLeague) : SportsConfigureStage()
}

private fun initialSportsStage(selection: SportsTile.Selection?): SportsConfigureStage {
    val league = selection?.let { sportsLeagueFor(it.leagueSlug) }
    return if (league != null) SportsConfigureStage.TeamList(league) else SportsConfigureStage.LeagueList
}

@Composable
private fun SportsPickerScreen(initial: SportsTile.Selection?, onPick: (encoded: String) -> Unit) {
    var stage by remember { mutableStateOf(initialSportsStage(initial)) }
    when (val current = stage) {
        SportsConfigureStage.LeagueList -> SportsLeagueListScreen(
            onPickLeague = { league -> stage = SportsConfigureStage.TeamList(league) },
        )
        is SportsConfigureStage.TeamList -> SportsTeamListScreen(
            league = current.league,
            onBack = { stage = SportsConfigureStage.LeagueList },
            onPickTeam = { team -> onPick(SportsTile.encode(current.league.slug, team.id, team.displayName)) },
        )
    }
}

@Composable
private fun SportsLeagueListScreen(onPickLeague: (SportsLeague) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0D)).verticalScroll(rememberScrollState()).padding(vertical = 20.dp)) {
        Text("choose a league", color = ConfigFg, fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(4.dp))
        Text("then pick the team you follow", color = ConfigFgDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = ConfigDivider, modifier = Modifier.padding(horizontal = 20.dp))
        SPORTS_LEAGUE_CATEGORY_ORDER.forEach { category ->
            val leagues = SPORTS_LEAGUES.filter { it.category == category }
            if (leagues.isEmpty()) return@forEach
            Text(
                text = category,
                color = ConfigFgDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            leagues.forEach { league ->
                Text(
                    text = league.displayName,
                    color = ConfigFg,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = { onPickLeague(league) }).padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun SportsTeamListScreen(league: SportsLeague, onBack: () -> Unit, onPickTeam: (SportsTeam) -> Unit) {
    var teams by remember(league) { mutableStateOf<List<SportsTeam>>(emptyList()) }
    var loading by remember(league) { mutableStateOf(league.slug != CRICKET_LEAGUE_SLUG) }
    LaunchedEffect(league) {
        if (league.slug != CRICKET_LEAGUE_SLUG) {
            loading = true
            teams = fetchSportsTeams(league.slug)
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0D)).verticalScroll(rememberScrollState())) {
        BackRow(onBack)
        Text(league.displayName, color = ConfigFg, fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            text = "pick the team you follow",
            color = ConfigFgDim,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = ConfigDivider, modifier = Modifier.padding(horizontal = 20.dp))

        when {
            league.slug == CRICKET_LEAGUE_SLUG -> {
                Text(
                    text = "national teams",
                    color = ConfigFgDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                CRICKET_TEAMS.forEach { team -> SportsTeamRow(team, onClick = { onPickTeam(team) }) }
                Text(
                    text = "ipl",
                    color = ConfigFgDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                IPL_TEAMS.forEach { team -> SportsTeamRow(team, onClick = { onPickTeam(team) }) }
            }
            loading -> Text("loading…", color = ConfigFgDim, fontSize = 13.sp, modifier = Modifier.padding(20.dp))
            teams.isEmpty() -> Text(
                text = "couldn't load teams — check your connection",
                color = ConfigFgDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(20.dp),
            )
            else -> teams.forEach { team -> SportsTeamRow(team, onClick = { onPickTeam(team) }) }
        }
    }
}

@Composable
private fun SportsTeamRow(team: SportsTeam, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(team.displayName, color = ConfigFg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (team.abbreviation.isNotBlank()) {
            Text(team.abbreviation, color = ConfigFgDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ConfigFg.copy(alpha = 0.06f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = ConfigFg, fontSize = 15.sp),
            cursorBrush = SolidColor(ConfigAccent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("search by symbol or company name", color = ConfigFgDim.copy(alpha = 0.6f), fontSize = 15.sp)
                }
                inner()
            },
        )
    }
}

/** The `‹ back` link every non-Browse stage shows, same shape as the in-app pickers' own `BackRow`. */
@Composable
private fun BackRow(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹ back",
            color = ConfigAccent,
            fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onBack).padding(horizontal = 6.dp, vertical = 8.dp),
        )
    }
}

/** Shared live-quote preview for a single symbol (stock or commodity) — [fetch] is [fetchStockQuote] or [fetchCommodityQuote]. */
@Composable
private fun QuotePreviewScreen(
    displayName: String,
    symbol: String,
    fetch: suspend (String) -> StockQuote?,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    var quote by remember(symbol) { mutableStateOf<StockQuote?>(null) }
    var loading by remember(symbol) { mutableStateOf(true) }
    LaunchedEffect(symbol) {
        loading = true
        quote = fetch(symbol)
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0D)).verticalScroll(rememberScrollState())) {
        BackRow(onBack)
        Text(displayName, color = ConfigFg, fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 20.dp))
        Text(symbol, color = ConfigFgDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Spacer(Modifier.height(16.dp))
        Box20 { QuoteBlock(quote, loading) }
        Spacer(Modifier.height(24.dp))
        Box20 { ConfirmButton("confirm", enabled = true, onClick = onConfirm) }
    }
}

@Composable
private fun QuoteBlock(quote: StockQuote?, loading: Boolean) {
    when {
        loading -> Text("loading live price…", color = ConfigFgDim, fontSize = 14.sp)
        quote == null -> Text("couldn't load a live price — you can still confirm", color = ConfigFgDim, fontSize = 14.sp)
        else -> Column {
            Text(formatStockPrice(quote.price, quote.currency), color = ConfigFg, fontSize = 28.sp, fontWeight = FontWeight.ExtraLight)
            Text(
                text = formatStockChangePercent(quote.changePercent),
                color = if (quote.change < 0) PreviewNegativeRed else PreviewPositiveGreen,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(if (quote.marketOpen) "market open" else "market closed", color = ConfigFgDim, fontSize = 12.sp)
        }
    }
}

/** `Modifier.padding(horizontal = 20.dp)` wrapper — avoids repeating the same padding block on every full-width row in this file. */
@Composable
private fun Box20(top: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 0.dp).padding(top = top)) { content() }
}

@Composable
private fun ConfirmButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) ConfigAccent else ConfigAccent.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ColorPickerScreen(onPick: (String?) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0D))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            text = "choose a colour",
            color = Color(0xFFF6F6F8),
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "just for this widget — leave it on tileshell's own accent, or pick one below",
            color = Color(0xFFF6F6F8).copy(alpha = 0.65f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))
        MatchAccentRow(onClick = { onPick(null) })
        Spacer(Modifier.height(16.dp))
        TileAccents.swatches.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { (id, color) -> Swatch(color, onClick = { onPick(id) }) }
            }
        }
    }
}

@Composable
private fun MatchAccentRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF6F6F8).copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFF2B78E4)),
        ) {}
        Text("match tileshell's accent", color = Color(0xFFF6F6F8), fontSize = 15.sp)
    }
}

@Composable
private fun Swatch(color: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
    ) {}
}
