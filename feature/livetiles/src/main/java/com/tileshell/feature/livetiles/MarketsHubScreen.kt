package com.tileshell.feature.livetiles

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.MARKET_INDICES
import com.tileshell.core.data.MarketsTile
import com.tileshell.core.data.POPULAR_WATCH_SYMBOLS
import com.tileshell.core.data.tidyName
import com.tileshell.core.data.watchKinds
import com.tileshell.core.data.StockQuote
import com.tileshell.core.data.MarketSearchResult
import com.tileshell.core.data.fetchMarketSearch
import com.tileshell.core.data.kindLabel
import com.tileshell.core.data.popularMatches
import com.tileshell.core.data.priceCurrency
import com.tileshell.core.data.WatchSymbol
import com.tileshell.core.data.fetchStockQuote
import com.tileshell.core.data.fetchStockSparkline
import com.tileshell.core.data.formatStockChangePercent
import com.tileshell.core.data.formatStockPrice
import com.tileshell.core.data.isMarketInSession
import com.tileshell.core.data.marketsRefreshDelayMs
import com.tileshell.core.data.settings.LiveRefreshRate
import com.tileshell.core.data.settings.resolveMs
import com.tileshell.core.data.moverSymbols
import com.tileshell.core.data.topMovers
import com.tileshell.core.data.watchKind
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.HubAppBar
import com.tileshell.core.design.HubAppBarAction
import com.tileshell.core.design.HubFilter
import com.tileshell.core.design.HubPanorama
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val MARKETS_PIVOTS = listOf("watchlist", "indices", "movers")

private val MarketUp = Color(0xFF35C759)
private val MarketDown = Color(0xFFFF453A)

private const val MARKETS_REFRESH_MS = 60_000L

/** Quotes are fetched a handful at a time so a long list doesn't trip Yahoo's rate limit. */
private val quoteGate = Semaphore(6)

/**
 * The markets hub (user-approved mockup). "watchlist": the user's own stocks,
 * commodities, currencies and crypto with price and day change; add by search,
 * remove in edit. "indices": the big indices with the selected one's day line.
 * "movers": the biggest risers and fallers among the sector baskets tileshell
 * already follows (Yahoo's own screener needs a login). Quotes come from the
 * same Yahoo Finance endpoint as the stock tile and refresh every minute
 * while the hub is open; nothing is sent but the symbols.
 */
@Composable
fun MarketsHubScreen(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    refreshRate: LiveRefreshRate,
    onDismiss: () -> Unit,
    onPinTile: (String) -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "marketsHubProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current
    val watch by MarketsWatchlist.flow(context).collectAsState()
    var adding by remember { mutableStateOf(false) }
    // What the add page adds: the section it was opened from, so each section has its own add.
    var addKind by remember { mutableStateOf("stocks") }
    var editing by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val markedSymbols by MarketsTileMarks.symbolsFlow(context).collectAsState()
    val markedIndices by MarketsTileMarks.indicesFlow(context).collectAsState()
    // The watchlist is filtered by kind (no "all"); the first kind it has is the default.
    val kinds = remember(watch) { watchKinds(watch) }
    var kindChoice by remember { mutableStateOf<String?>(null) }
    val kind = kindChoice?.takeIf { it in kinds } ?: kinds.firstOrNull()

    BackHandler(enabled = visible) {
        if (adding) adding = false else onDismiss()
    }
    val pagerState = rememberPagerState(pageCount = { MARKETS_PIVOTS.size })
    val scope = rememberCoroutineScope()

    // Same gate as the stock tile: Start resumed, no battery saver, animations on.
    // Opening the hub outside it still loads once.
    val active = rememberLiveTilesActive(suspended = !visible)
    val watchQuotes by rememberQuotes(watch.map { it.symbol }, visible, active, refreshTick, refreshRate)
    val indexQuotes by rememberQuotes(MARKET_INDICES.map { it.symbol }, visible && pagerState.currentPage == 1, active, refreshTick, refreshRate)

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.bg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            HubPanorama(
                title = "markets",
                sections = MARKETS_PIVOTS,
                pagerState = pagerState,
                tokens = tokens,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> if (adding) {
                        AddSymbolPage(addKind, tokens, accent, watch) { picked ->
                            MarketsWatchlist.add(context, picked)
                            adding = false
                        }
                    } else {
                        WatchlistPage(
                            watch, watchQuotes, editing, kinds, kind, { kindChoice = it }, markedSymbols, tokens, accent,
                            onRemove = { MarketsWatchlist.remove(context, it) },
                            onToggleMark = { MarketsTileMarks.toggleSymbol(context, it) },
                            onAdd = { k -> addKind = k; adding = true },
                        )
                    }
                    1 -> IndicesPage(indexQuotes, visible && pagerState.currentPage == 1, markedIndices ?: listOf("^NSEI"), { MarketsTileMarks.toggleIndex(context, it) }, tokens, accent)
                    else -> MoversPage(visible && pagerState.currentPage == 2, active, refreshTick, refreshRate, tokens, accent)
                }
            }
            HubAppBar(
                tokens = tokens,
                actions = buildList {
                    add(HubAppBarAction("back", "back") { if (adding) adding = false else onDismiss() })
                    // Adds to the section showing (a stock, commodity, currency pair or crypto), stocks otherwise.
                    val addFor = if (pagerState.currentPage == 0 && !adding) kind ?: "stocks" else "stocks"
                    add(HubAppBarAction("plus", "add a ${kindLabel(addFor)}", "add ${kindLabel(addFor)}") {
                        editing = false
                        addKind = addFor
                        adding = true
                        scope.launch { pagerState.animateScrollToPage(0) }
                    })
                    if (pagerState.currentPage == 0 && !adding && watch.isNotEmpty()) {
                        add(HubAppBarAction(if (editing) "check" else "edit", "edit watchlist", if (editing) "done" else "edit") { editing = !editing })
                    }
                    add(HubAppBarAction("refresh", "refresh prices", "refresh") { refreshTick++ })
                    // Pins the tile for what is showing: the kind chosen on the watchlist, else the markets (indices) tile.
                    val pinKind = if (pagerState.currentPage == 0 && !adding) kind else null
                    add(
                        HubAppBarAction("pin", "pin ${pinKind ?: "markets"} tile to start", "pin ${pinKind ?: "markets"}") {
                            onPinTile(pinKind ?: MarketsTile.INDICES)
                        },
                    )
                },
                menuItems = buildList {
                    val current = if (pagerState.currentPage == 0 && !adding) kind else null
                    kinds.filter { it != current }.forEach { k -> add(HubAppBarAction("pin", "pin $k tile", "pin $k tile") { onPinTile(k) }) }
                    if (current != null) add(HubAppBarAction("pin", "pin markets tile", "pin indices tile") { onPinTile(MarketsTile.INDICES) })
                    add(HubAppBarAction("refresh", "reset watchlist", "reset watchlist") { MarketsWatchlist.reset(context); editing = false })
                },
            )
        }
    }
}

/**
 * Latest quote per symbol while [enabled] (the hub is open and this page is the
 * one showing): one fetch on opening, then, only while [active], again after
 * [marketsRefreshDelayMs] — the user's stock rate while any of the symbols'
 * markets is trading, through to the opening bell otherwise, as the stock
 * tile does. [tick] forces an immediate refetch.
 */
@Composable
private fun rememberQuotes(
    symbols: List<String>,
    enabled: Boolean,
    active: Boolean,
    tick: Int,
    refreshRate: LiveRefreshRate,
): State<Map<String, StockQuote>> =
    produceState(emptyMap(), symbols, enabled, active, tick, refreshRate) {
        if (!enabled || symbols.isEmpty()) return@produceState
        while (true) {
            val fetched = fetchQuotes(symbols)
            if (fetched.isNotEmpty()) value = value + fetched
            if (!active) break
            delayUntilNextRefresh(marketsRefreshDelayMs(symbols, refreshRate.resolveMs(MARKETS_REFRESH_MS)))
        }
    }

internal suspend fun fetchQuotes(symbols: List<String>): Map<String, StockQuote> = coroutineScope {
    symbols.distinct().map { s -> async { quoteGate.withPermit { s to fetchStockQuote(s) } } }
        .awaitAll()
        .mapNotNull { (s, q) -> q?.let { s to it } }
        .toMap()
}

private fun openQuotePage(context: Context, symbol: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://finance.yahoo.com/quote/${Uri.encode(symbol)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchlistPage(
    watch: List<WatchSymbol>,
    quotes: Map<String, StockQuote>,
    editing: Boolean,
    kinds: List<String>,
    kind: String?,
    onKind: (String) -> Unit,
    marked: List<String>,
    tokens: ColorTokens,
    accent: Color,
    onRemove: (String) -> Unit,
    onToggleMark: (String) -> Unit,
    onAdd: (String) -> Unit,
) {
    val context = LocalContext.current
    val shown = remember(watch, kind) { watch.filter { watchKind(it.symbol) == kind } }
    // Marked ones are what the tile shows; with none marked it shows the first few of the kind.
    val noneMarked = remember(shown, marked) { shown.none { it.symbol in marked } }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        if (watch.isEmpty()) {
            item {
                Text(
                    "your watchlist is empty. add a stock ›",
                    color = accent,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 10.dp).clickable { onAdd("stocks") },
                )
            }
            return@LazyColumn
        }
        if (kinds.size > 1) {
            item(key = "kinds") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    kinds.forEach { k -> HubFilter(k, kind == k, tokens, accent) { onKind(k) } }
                }
            }
        }
        item(key = "tile-hint") {
            Text(
                if (noneMarked) "the $kind tile shows the first few. tap ▢ to choose which."
                else "▣ is on the $kind tile. tap to add or remove.",
                color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )
        }
        items(shown, key = { it.symbol }) { item ->
            val q = quotes[item.symbol]
            val on = item.symbol in marked
            MarketRow(
                title = item.displayName,
                subtitle = marketSubtitle(item.symbol, q),
                quote = q,
                currency = priceCurrency(item.symbol, q?.currency.orEmpty()),
                tokens = tokens,
                trailing = {
                    TileMark(on, accent, tokens) { onToggleMark(item.symbol) }
                    if (editing) {
                        Text(
                            "✕",
                            color = tokens.fgDim,
                            fontSize = 18.sp,
                            modifier = Modifier.clickable { onRemove(item.symbol) }.padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
                        )
                    }
                },
                onClick = { if (!editing) openQuotePage(context, item.symbol) },
            )
        }
        // One add per section: a stock, a commodity, a currency pair or a crypto.
        item(key = "add-kind") {
            val k = kind ?: "stocks"
            Text("+ add a ${kindLabel(k)}", color = accent, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp).clickable { onAdd(k) })
        }
    }
}

/** "On tile": the shared mark, filled when the tile shows this one. */
@Composable
private fun TileMark(on: Boolean, accent: Color, tokens: ColorTokens, onClick: () -> Unit) {
    OnTileMark(on, tokens, accent, "show on the tile", onToggle = onClick)
}

private fun marketSubtitle(symbol: String, quote: StockQuote?): String {
    val state = if (isMarketInSession(symbol)) "open" else "closed"
    return "${symbol.removeSuffix(".NS").removeSuffix(".BO")} · $state"
}

@Composable
private fun MarketRow(
    title: String,
    subtitle: String,
    quote: StockQuote?,
    currency: String,
    tokens: ColorTokens,
    trailing: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    selected: Boolean = false,
    accent: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (selected) accent else tokens.fg,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(subtitle, color = tokens.fgDim, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (quote != null) {
                    Text(formatStockPrice(quote.price, currency), color = tokens.fg, fontSize = 17.sp, fontWeight = FontWeight.Light)
                    Text(
                        formatStockChangePercent(quote.changePercent),
                        color = if (quote.changePercent >= 0) MarketUp else MarketDown,
                        fontSize = 13.sp,
                    )
                } else {
                    Text("···", color = tokens.fgDim, fontSize = 17.sp)
                }
            }
            trailing?.invoke(this)
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(tokens.sheetLine))
    }
}

@Composable
private fun AddSymbolPage(kind: String, tokens: ColorTokens, accent: Color, watch: List<WatchSymbol>, onPick: (WatchSymbol) -> Unit) {
    var query by remember { mutableStateOf("") }
    val label = kindLabel(kind)
    val results by produceState(emptyList<MarketSearchResult>(), query) {
        value = emptyList()
        if (query.isBlank()) return@produceState
        delay(350)
        value = fetchMarketSearch(query.trim())
    }
    val have = remember(watch) { watch.map { it.symbol }.toSet() }
    val placeholder = when (kind) {
        "commodities" -> "search a commodity (gold, crude oil…)"
        "currencies" -> "search a currency pair (eur usd)"
        "crypto" -> "search a crypto (bitcoin)"
        else -> "search a stock or etf"
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "box") {
            Column {
                Text("add a $label", color = accent, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
                Box(modifier = Modifier.fillMaxWidth().background(tokens.chip).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (query.isEmpty()) Text(placeholder, color = tokens.fgDim, fontSize = 16.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = tokens.fg, fontSize = 16.sp),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (query.isBlank()) {
            val popular = POPULAR_WATCH_SYMBOLS.filter { watchKind(it.symbol) == kind && it.symbol !in have }
            if (popular.isNotEmpty()) {
                item(key = "popular-h") {
                    Text("popular", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
                }
            }
            items(popular, key = { "p-" + it.symbol }) { item ->
                PickRow(item.displayName, "${item.symbol} · $label", tokens) { onPick(item) }
            }
        } else {
            // Only this section's kind: a stock search never offers a currency pair, and the other way round.
            val merged = (popularMatches(query).map { MarketSearchResult(it.symbol, it.displayName, "", kindLabel(watchKind(it.symbol))) } + results)
                .filter { watchKind(it.symbol) == kind }
                .distinctBy { it.symbol }
            items(merged.filter { it.symbol !in have }, key = { "r-" + it.symbol }) { r ->
                PickRow(tidyName(r.displayName), "${r.symbol} · ${r.kind}", tokens) { onPick(WatchSymbol(r.symbol, tidyName(r.displayName))) }
            }
        }
    }
}

@Composable
private fun PickRow(title: String, subtitle: String, tokens: ColorTokens, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp)) {
        Text(title, color = tokens.fg, fontSize = 17.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = tokens.fgDim, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun IndicesPage(quotes: Map<String, StockQuote>, enabled: Boolean, marked: List<String>, onToggleMark: (String) -> Unit, tokens: ColorTokens, accent: Color) {
    var selected by remember { mutableStateOf(MARKET_INDICES.first().symbol) }
    val spark by produceState(emptyList<Double>(), selected, enabled) {
        value = emptyList()
        if (enabled) value = fetchStockSparkline(selected)
    }
    val chosen = MARKET_INDICES.first { it.symbol == selected }
    val q = quotes[selected]
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "hero") {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Text(chosen.displayName, color = accent, fontSize = 15.sp)
                if (q != null) {
                    Text(formatStockPrice(q.price, ""), color = tokens.fg, fontSize = 40.sp, fontWeight = FontWeight.ExtraLight)
                    Text(
                        "${formatStockChangePercent(q.changePercent)}  ·  ${if (isMarketInSession(selected)) "open" else "closed"}",
                        color = if (q.changePercent >= 0) MarketUp else MarketDown,
                        fontSize = 14.sp,
                    )
                } else {
                    Text("···", color = tokens.fgDim, fontSize = 40.sp, fontWeight = FontWeight.ExtraLight)
                }
                HubSparkline(
                    spark,
                    color = if ((q?.changePercent ?: 0.0) >= 0) MarketUp else MarketDown,
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp),
                )
            }
        }
        item(key = "tile-hint") {
            Text(
                "▣ is on the markets tile. tap to add or remove.",
                color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        items(MARKET_INDICES, key = { it.symbol }) { index ->
            val iq = quotes[index.symbol]
            MarketRow(
                title = index.displayName,
                subtitle = if (isMarketInSession(index.symbol)) "open" else "closed",
                quote = iq,
                currency = "",
                tokens = tokens,
                selected = index.symbol == selected,
                accent = accent,
                trailing = { TileMark(index.symbol in marked, accent, tokens) { onToggleMark(index.symbol) } },
                onClick = { selected = index.symbol },
            )
        }
    }
}

@Composable
private fun MoversPage(enabled: Boolean, active: Boolean, tick: Int, refreshRate: LiveRefreshRate, tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    var region by remember { mutableStateOf("india") }
    var gainers by remember { mutableStateOf(true) }
    val basket = remember(region) { moverSymbols(region) }
    val quotes by rememberQuotes(basket.map { it.symbol }, enabled, active, tick, refreshRate)
    val names = remember(basket) { basket.associate { it.symbol to it.displayName } }
    val top = remember(quotes, gainers, region) {
        topMovers(quotes.filterKeys { it in names }.values.toList(), gainers)
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "filters") {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    listOf(true to "gainers", false to "losers").forEach { (value, label) ->
                        HubFilter(label, gainers == value, tokens, accent) { gainers = value }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    listOf("india" to "india", "us" to "us").forEach { (value, label) ->
                        HubFilter(label, region == value, tokens, accent) { region = value }
                    }
                }
            }
        }
        if (top.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (quotes.isEmpty()) "loading prices…" else if (gainers) "nothing is up right now" else "nothing is down right now",
                    color = tokens.fgDim,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        items(top, key = { it.symbol }) { q ->
            MarketRow(
                title = names[q.symbol] ?: q.displayName,
                subtitle = q.symbol.removeSuffix(".NS"),
                quote = q,
                currency = q.currency,
                tokens = tokens,
                onClick = { openQuotePage(context, q.symbol) },
            )
        }
        item(key = "note") {
            Text(
                "ranked among the sector baskets tileshell follows, not the whole market",
                color = tokens.fgDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** A plain day line: no axes, just the shape of the session. */
@Composable
private fun HubSparkline(points: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val lo = points.min()
        val hi = points.max()
        val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = size.width * i / (points.size - 1)
            val y = size.height - (((p - lo) / span).toFloat() * size.height)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}
