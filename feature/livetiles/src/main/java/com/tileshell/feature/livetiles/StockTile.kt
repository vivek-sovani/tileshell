package com.tileshell.feature.livetiles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.StockQuote
import com.tileshell.core.data.StockSymbolRef
import com.tileshell.core.data.StockTile
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.effectiveMarketRefreshMs
import com.tileshell.core.data.fetchStockQuote
import com.tileshell.core.data.fetchStockSparkline
import com.tileshell.core.data.formatStockChangePercent
import com.tileshell.core.data.formatStockPrice
import com.tileshell.core.data.indexDisplayNameFor
import com.tileshell.core.data.indexTickerFor
import com.tileshell.core.data.settings.LiveRefreshRate
import com.tileshell.core.data.settings.resolveMs
import com.tileshell.core.data.stockCategoryFor
import com.tileshell.core.design.LocalTileFaceColor

/** How often the tile re-polls Yahoo Finance while it's on screen, absent a Personalize override — see [LiveRefreshRate]. */
private const val STOCK_REFRESH_MS = 60_000L

private val PositiveGreen = Color(0xFF35C759)
private val NegativeRed = Color(0xFFFF453A)

private fun changeColor(change: Double): Color = if (change < 0) NegativeRed else PositiveGreen

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

/** The symbol/label this selection leads with — the first member, for a [StockTile.Selection.Category] or [StockTile.Selection.MultiStock]. */
fun primaryStockRef(selection: StockTile.Selection): StockSymbolRef? = when (selection) {
    is StockTile.Selection.Single -> StockSymbolRef(selection.symbol, selection.displayName)
    is StockTile.Selection.Category -> stockCategoryFor(selection.categoryId)?.symbols?.firstOrNull()
    is StockTile.Selection.MultiStock -> selection.symbols.firstOrNull()?.let { (symbol, name) -> StockSymbolRef(symbol, name) }
}

/** Every symbol a selection should fetch quotes for — one for [StockTile.Selection.Single], the whole set for a category/multi-stock list. */
fun stockRefsFor(selection: StockTile.Selection): List<StockSymbolRef> = when (selection) {
    is StockTile.Selection.Single -> listOf(StockSymbolRef(selection.symbol, selection.displayName))
    is StockTile.Selection.Category -> stockCategoryFor(selection.categoryId)?.symbols.orEmpty()
    is StockTile.Selection.MultiStock -> selection.symbols.map { (symbol, name) -> StockSymbolRef(symbol, name) }
}

/**
 * The ticker + display name of the index a group selection's front face
 * tracks — a category's own curated sector index (NIFTY Bank for banking,
 * etc.), or the generic broad-market index ([indexTickerFor]) for a
 * user-built [StockTile.Selection.MultiStock] list, which has no single
 * sector to point at. `null` for [StockTile.Selection.Single] — one stock is
 * already its own headline, it doesn't need a second, broader number.
 */
private fun indexInfoFor(selection: StockTile.Selection, primary: StockSymbolRef): Pair<String, String>? = when (selection) {
    is StockTile.Selection.Single -> null
    is StockTile.Selection.Category -> stockCategoryFor(selection.categoryId)?.let { it.indexTicker to it.indexDisplayName }
    is StockTile.Selection.MultiStock -> indexTickerFor(primary.symbol) to indexDisplayNameFor(primary.symbol)
}

/**
 * The live stock tile: one symbol ([StockTile.Selection.Single]), a curated
 * sector basket ([StockTile.Selection.Category]), or a user-built custom list
 * ([StockTile.Selection.MultiStock]). A single-stock tile shows that stock's
 * price up front and its own intraday sparkline on the back, throughout every
 * size. A category/multi-stock tile instead leads with its **index**'s trend
 * (no one member represents the whole group) and flips to a list of members'
 * own prices — one member (the lead) below [TileSize.LARGE], every member at
 * [TileSize.LARGE]. Re-polls every [STOCK_REFRESH_MS] while [active] (a
 * Personalize "live data refresh" [refreshRate] overrides that interval;
 * either way [com.tileshell.core.data.effectiveMarketRefreshMs] slows it
 * further outside 9am-4pm weekday market hours, and [delayUntilNextRefresh]
 * aligns every stock tile sharing the same interval to the same wall-clock
 * instants so they all re-poll together instead of at independent offsets),
 * the same on-screen-only gate every other opt-in live tile uses; list
 * members are fetched one at a time rather than concurrently, simplest way
 * to sidestep any burst-rate-limit risk from hitting Yahoo's unofficial
 * endpoint with several requests at once.
 */
@Composable
fun StockTileFace(
    size: TileSize,
    flipped: Boolean,
    active: Boolean,
    selection: StockTile.Selection?,
    refreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    modifier: Modifier = Modifier,
) {
    if (selection == null) {
        NoStockPickedFace(size, modifier)
        return
    }
    val refs = remember(selection) { stockRefsFor(selection) }
    val primary = remember(selection) { primaryStockRef(selection) }
    if (refs.isEmpty() || primary == null) {
        NoStockPickedFace(size, modifier)
        return
    }
    val big = size == TileSize.LARGE
    val indexInfo = remember(selection) { indexInfoFor(selection, primary) }
    val isGroup = indexInfo != null

    var listQuotes by remember(selection) { mutableStateOf<Map<String, StockQuote?>>(emptyMap()) }
    var indexQuote by remember(selection) { mutableStateOf<StockQuote?>(null) }
    var indexSparkline by remember(selection) { mutableStateOf<List<Double>>(emptyList()) }
    var singleSparkline by remember(selection) { mutableStateOf<List<Double>>(emptyList()) }

    LaunchedEffect(selection, big, active, refreshRate) {
        if (!active) return@LaunchedEffect
        while (true) {
            if (indexInfo != null) {
                indexQuote = fetchStockQuote(indexInfo.first)
                indexSparkline = fetchStockSparkline(indexInfo.first)
                val listTargets = if (big) refs else listOf(primary)
                val fetched = LinkedHashMap<String, StockQuote?>()
                listTargets.forEach { ref -> fetched[ref.symbol] = fetchStockQuote(ref.symbol) }
                listQuotes = fetched
            } else {
                listQuotes = mapOf(primary.symbol to fetchStockQuote(primary.symbol))
                singleSparkline = fetchStockSparkline(primary.symbol)
            }
            delayUntilNextRefresh(effectiveMarketRefreshMs(refreshRate.resolveMs(STOCK_REFRESH_MS)))
        }
    }

    val frontReady = if (isGroup) indexQuote != null else listQuotes[primary.symbol] != null
    if (!frontReady) {
        NoDataFace(size, if (isGroup) indexInfo?.second.orEmpty() else primary.displayName, modifier)
        return
    }

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = {
            if (indexInfo != null) {
                StockIndexFront(indexInfo.second, indexQuote, indexSparkline, size)
            } else {
                StockSingleFront(primary, listQuotes[primary.symbol], size)
            }
        },
        back = {
            if (isGroup) {
                StockListBack(if (big) refs else listOf(primary), listQuotes, size)
            } else {
                StockSparklineBack(primary.displayName, listQuotes[primary.symbol], singleSparkline, size)
            }
        },
    )
}

@Composable
private fun NoStockPickedFace(size: TileSize, modifier: Modifier) {
    val narrow = size.narrowLive
    val short = size.shortLive
    Column(
        modifier = modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = "tap to choose a stock or sector",
            color = FaceText.copy(alpha = 0.82f),
            fontSize = if (short) 12.sp else 14.sp,
            maxLines = if (narrow) 4 else 2,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}

@Composable
private fun NoDataFace(size: TileSize, label: String, modifier: Modifier) {
    val narrow = size.narrowLive
    val short = size.shortLive
    Column(
        modifier = modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = label.ifBlank { "stocks" },
            color = FaceText,
            fontSize = if (short) 15.sp else 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        Text(
            text = "no data yet",
            color = FaceText.copy(alpha = 0.7f),
            fontSize = if (short) 11.sp else 13.sp,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}

/** One stock's own price — [StockTile.Selection.Single]'s front face, at every size. */
@Composable
private fun StockSingleFront(ref: StockSymbolRef, quote: StockQuote?, size: TileSize) {
    val narrow = size.narrowLive
    val short = size.shortLive
    val big = size == TileSize.LARGE
    if (quote == null) {
        NoDataFace(size, ref.displayName, Modifier)
        return
    }
    val changeSign = if (quote.change >= 0) "+" else ""
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = if (narrow) Arrangement.SpaceEvenly else Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = ref.displayName,
            color = FaceText.copy(alpha = 0.82f),
            fontSize = if (short) 11.sp else 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        Text(
            text = formatStockPrice(quote.price, quote.currency),
            color = FaceText,
            fontSize = if (short) 20.sp else if (narrow) 24.sp else if (big) 44.sp else 32.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        Text(
            text = "$changeSign${formatStockPrice(quote.change, "")} (${formatStockChangePercent(quote.changePercent)})",
            color = changeColor(quote.change),
            fontSize = if (short) 11.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        if (big) {
            Spacer(Modifier.weight(1f))
            Text(
                text = if (quote.marketOpen) "market open" else "market closed",
                color = FaceText.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
    }
}

/** A category/multi-stock selection's own index/sector trend — the front face for a group selection, at every size. */
@Composable
private fun StockIndexFront(indexName: String, indexQuote: StockQuote?, sparkline: List<Double>, size: TileSize) {
    val narrow = size.narrowLive
    val short = size.shortLive
    val big = size == TileSize.LARGE
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = if (narrow) Arrangement.SpaceEvenly else Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = indexName,
            color = FaceText,
            fontSize = if (short) 13.sp else if (big) 17.sp else 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        if (indexQuote != null) {
            Text(
                text = "${formatStockPrice(indexQuote.price, "")} (${formatStockChangePercent(indexQuote.changePercent)})",
                color = changeColor(indexQuote.change),
                fontSize = if (short) 12.sp else 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
        }
        if (sparkline.size >= 2 && !narrow) {
            Spacer(Modifier.height(if (big) 12.dp else 8.dp))
            Sparkline(
                points = sparkline,
                lineColor = indexQuote?.let { changeColor(it.change) } ?: FaceText,
                modifier = Modifier.fillMaxWidth().height(if (big) 48.dp else if (short) 22.dp else 32.dp),
            )
        }
        if (big) {
            Spacer(Modifier.weight(1f))
            Text("sector trend", color = FaceText.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}

/** A group selection's members with their own prices — the back face for a category/multi-stock tile: the lead member below [TileSize.LARGE], every member at [TileSize.LARGE]. */
@Composable
private fun StockListBack(refs: List<StockSymbolRef>, quotes: Map<String, StockQuote?>, size: TileSize) {
    val narrow = size.narrowLive
    val short = size.shortLive
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = if (refs.size > 1) Arrangement.SpaceEvenly else Arrangement.Center,
    ) {
        refs.take(6).forEach { ref -> StockListRow(ref, quotes[ref.symbol]) }
    }
}

@Composable
private fun StockListRow(ref: StockSymbolRef, quote: StockQuote?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ref.displayName,
            color = FaceText,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        if (quote != null) {
            Text(
                text = formatStockPrice(quote.price, quote.currency),
                color = FaceText,
                fontSize = 13.sp,
                maxLines = 1,
            )
            Text(
                text = formatStockChangePercent(quote.changePercent),
                color = changeColor(quote.change),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
        } else {
            Text(text = "…", color = FaceText.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}

/** Back face for a [StockTile.Selection.Single] tile: that stock's own intraday sparkline + day range. */
@Composable
private fun StockSparklineBack(label: String, quote: StockQuote?, sparkline: List<Double>, size: TileSize) {
    val narrow = size.narrowLive
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow) 4.dp else 11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = label,
            color = FaceText,
            fontSize = if (narrow) 14.sp else 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        if (sparkline.size >= 2 && quote != null) {
            Spacer(Modifier.height(6.dp))
            Sparkline(
                points = sparkline,
                lineColor = changeColor(quote.change),
                modifier = Modifier.fillMaxWidth().height(if (narrow) 28.dp else 36.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "high ${formatStockPrice(quote.dayHigh, quote.currency)} · low ${formatStockPrice(quote.dayLow, quote.currency)}",
                color = FaceText.copy(alpha = 0.7f),
                fontSize = if (narrow) 10.sp else 12.sp,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
        } else {
            Text(
                text = "no chart data yet",
                color = FaceText.copy(alpha = 0.65f),
                fontSize = 12.sp,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
        }
    }
}

/** A minimal intraday line chart — normalizes [points] to the drawn height, no axes/labels (the tile has no room for them). */
@Composable
private fun Sparkline(points: List<Double>, lineColor: Color, modifier: Modifier = Modifier) {
    val min = points.min()
    val max = points.max()
    val range = (max - min).takeIf { it > 0.0 } ?: 1.0
    Canvas(modifier = modifier) {
        val stepX = size.width / (points.size - 1).coerceAtLeast(1)
        val path = Path()
        points.forEachIndexed { i, value ->
            val x = i * stepX
            val y = size.height - ((value - min) / range * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 3f))
    }
}

/** The compact 1×1 face (ICONS home style / SMALL tile): the lead symbol's price + change only, never flips. */
@Composable
fun StockSmallFace(
    selection: StockTile.Selection?,
    fallback: @Composable () -> Unit,
    refreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    modifier: Modifier = Modifier,
) {
    if (selection == null) return fallback()
    val primary = remember(selection) { primaryStockRef(selection) } ?: return fallback()

    var quote by remember(selection) { mutableStateOf<StockQuote?>(null) }
    LaunchedEffect(selection, refreshRate) {
        while (true) {
            quote = fetchStockQuote(primary.symbol)
            delayUntilNextRefresh(effectiveMarketRefreshMs(refreshRate.resolveMs(STOCK_REFRESH_MS)))
        }
    }
    val current = quote ?: return fallback()

    Column(
        modifier = modifier.fillMaxSize().padding(4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = formatStockPrice(current.price, ""),
            color = FaceText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = formatStockChangePercent(current.changePercent),
            color = changeColor(current.change),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
