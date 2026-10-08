package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.isMarketInSession
import com.tileshell.core.data.MARKET_INDICES
import com.tileshell.core.data.MarketsTile
import com.tileshell.core.data.StockQuote
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.WatchSymbol
import com.tileshell.core.data.formatStockChangePercent
import com.tileshell.core.data.formatStockPrice
import com.tileshell.core.data.marketsRefreshDelayMs
import com.tileshell.core.data.priceCurrency
import com.tileshell.core.data.settings.LiveRefreshRate
import com.tileshell.core.data.settings.resolveMs
import com.tileshell.core.data.tileIndices
import com.tileshell.core.data.tileLabel
import com.tileshell.core.data.tileSymbols
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileIcons

private const val MARKETS_TILE_REFRESH_MS = 60_000L

/**
 * A markets tile, one of five by [kind]: the hub's own tile ("indices": the
 * indices marked on tile, the NIFTY 50 by default), or a stocks, commodities,
 * currencies or crypto tile showing the watchlist entries of that kind marked
 * on tile in the hub (the first few when none is). One entry reads big, several
 * as rows that grow with the tile's height. Refreshes like the stock tile: only
 * while [active], at the user's stock rate in market hours and through to the
 * opening bell otherwise. Tapping opens the hub.
 */
@Composable
fun MarketsTileFace(
    size: TileSize,
    active: Boolean,
    kind: String,
    refreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    modifier: Modifier = Modifier,
) {
    val color = LocalTileFaceColor.current
    val context = LocalContext.current
    val watch by MarketsWatchlist.flow(context).collectAsState()
    val markedSymbols by MarketsTileMarks.symbolsFlow(context).collectAsState()
    val markedIndices by MarketsTileMarks.indicesFlow(context).collectAsState()
    val isIndices = kind == MarketsTile.INDICES
    val items = remember(kind, watch, markedSymbols, markedIndices) {
        if (isIndices) tileIndices(markedIndices).map { WatchSymbol(it.symbol, it.displayName) } else tileSymbols(kind, watch, markedSymbols)
    }
    val symbols = remember(items) { items.map { it.symbol } }
    var quotes by remember { mutableStateOf<Map<String, StockQuote>>(emptyMap()) }
    val showsQuotes = size != TileSize.SMALL
    LaunchedEffect(symbols, active, showsQuotes, refreshRate) {
        if (!active || !showsQuotes || symbols.isEmpty()) return@LaunchedEffect
        var lastFull = 0L
        var failures = 0
        while (true) {
            val now = System.currentTimeMillis()
            val full = now - lastFull >= CLOSED_REFRESH_MS
            val fetched = fetchQuotes(symbols, held = quotes.takeUnless { full })
            if (full) lastFull = now
            if (fetched.isNotEmpty()) quotes = quotes + fetched
            failures = if (fetched.isEmpty() && symbols.any { isMarketInSession(it) }) failures + 1 else 0
            val backoff = if (failures > 0) (1L shl failures.coerceAtMost(4)) else 1L
            delayUntilNextRefresh(marketsRefreshDelayMs(symbols, refreshRate.resolveMs(MARKETS_TILE_REFRESH_MS)) * backoff)
        }
    }
    val title = if (isIndices) "markets" else kind
    val shown = items.filter { quotes[it.symbol] != null }.take(rowsFor(size))
    Box(modifier = modifier.fillMaxSize()) {
        when {
            !showsQuotes || shown.isEmpty() -> {
                Icon(TileIcons["markets"], contentDescription = null, tint = color, modifier = Modifier.align(Alignment.Center).size(if (size == TileSize.SMALL) 28.dp else 44.dp))
                if (size != TileSize.SMALL) Text(title, color = color, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
            shown.size == 1 && size.cols >= 2 -> {
                val item = shown.first()
                val q = quotes.getValue(item.symbol)
                Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(tileLabel(item, 18), color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Column {
                        Text(formatStockPrice(q.price, if (isIndices) "" else priceCurrency(item.symbol, q.currency)), color = color, fontSize = if (size.cols >= 2) 26.sp else 18.sp, fontWeight = FontWeight.Light, maxLines = 1)
                        Text(arrowed(q), color = color, fontSize = 14.sp)
                    }
                    Text(title, color = color, fontSize = 12.sp)
                }
            }
            else -> {
                // One column wide, or three rows tall: each entry takes two lines (name and change, then the price
                // when there is room), so neither the name nor a figure is squeezed out. Otherwise one line each,
                // with the price from 3 columns.
                val twoLine = size.cols <= 1 || size.rows >= 3
                val showPrice = size.cols >= 3
                val maxName = when {
                    size.cols <= 1 -> 6
                    size.cols == 2 -> 10
                    else -> 14
                }
                Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                    Text(title, color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Column(
                        verticalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        shown.forEach { item ->
                            val q = quotes.getValue(item.symbol)
                            val price = formatStockPrice(q.price, if (isIndices) "" else priceCurrency(item.symbol, q.currency))
                            if (twoLine) {
                                Column(Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text(tileLabel(item, if (size.cols <= 1) maxName else 18), color = color, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        if (size.cols >= 2) Text(arrowed(q), color = color, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.padding(start = 6.dp))
                                    }
                                    Text(
                                        if (size.cols <= 1) arrowed(q) else price,
                                        color = color.copy(alpha = if (size.cols <= 1) 1f else 0.85f), fontSize = if (size.cols <= 1) 11.sp else 12.sp,
                                        fontWeight = FontWeight.Light, maxLines = 1, softWrap = false,
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text(tileLabel(item, maxName), color = color, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    if (showPrice) {
                                        Text(price, color = color, fontSize = 13.sp, fontWeight = FontWeight.Light, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                                    }
                                    Text(arrowed(q), color = color, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun rowsFor(size: TileSize): Int = when {
    size.rows <= 1 -> 1
    size.rows == 2 -> 3
    size.rows == 3 -> 3
    else -> 5
}

/** `▲ +0.37%` / `▼ -1.20%`, so up and down read without colour on a coloured tile. */
private fun arrowed(q: StockQuote): String = (if (q.changePercent >= 0) "▲ " else "▼ ") + formatStockChangePercent(q.changePercent).removePrefix("+")

/** The sports hub's tile: the sports glyph and its name; tapping opens the hub. */
@Composable
fun SportsHubTileFace(size: TileSize, modifier: Modifier = Modifier) {
    val color = LocalTileFaceColor.current
    Box(modifier = modifier.fillMaxSize()) {
        Icon(TileIcons["sportshub"], contentDescription = null, tint = color, modifier = Modifier.align(Alignment.Center).size(if (size == TileSize.SMALL) 28.dp else 44.dp))
        if (size != TileSize.SMALL) {
            Text("sports", color = color, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
    }
}
