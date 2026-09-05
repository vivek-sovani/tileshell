package com.tileshell.feature.livetiles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.nextMarketRefreshDelayMs
import com.tileshell.core.data.fetchCommodityQuote
import com.tileshell.core.data.fetchCommoditySparkline
import com.tileshell.core.data.formatStockChangePercent
import com.tileshell.core.data.formatStockPrice
import com.tileshell.core.data.settings.LiveRefreshRate
import com.tileshell.core.data.settings.resolveMs
import com.tileshell.core.design.LocalTileFaceColor

/** How often the tile re-polls Yahoo Finance while it's on screen, absent a Personalize override — same cadence as the stock tile; see [LiveRefreshRate]. */
private const val COMMODITY_REFRESH_MS = 60_000L

private val PositiveGreen = Color(0xFF35C759)
private val NegativeRed = Color(0xFFFF453A)

private fun changeColor(change: Double): Color = if (change < 0) NegativeRed else PositiveGreen

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

/**
 * The live commodity/currency tile: one symbol from [com.tileshell.core.data.COMMODITY_ITEMS]
 * (gold, silver, crude oil, a currency pair, ...) — always a single trackable
 * price, unlike the stock tile's category/multi-stock baskets, so the front
 * face is always price+change and the back is always that symbol's own
 * intraday sparkline, at every size. Re-polls every [COMMODITY_REFRESH_MS]
 * while [active] (a Personalize "live data refresh" [refreshRate] overrides
 * that interval; [delayUntilNextRefresh] then aligns every commodity tile
 * sharing the same interval to the same wall-clock instants), the same
 * on-screen-only gate every other opt-in live tile uses. Also folds in
 * [nextMarketRefreshDelayMs]'s market-hours gate, same as the stock tile —
 * but correctly, per instrument: a futures contract or currency pair
 * (`GC=F`, `EURUSD=X`) trades essentially around the clock on weekdays, so
 * unlike an equity it is *not* throttled overnight, only over the weekend.
 * The previous version applied a fixed 9am-4pm equity window to these too,
 * which throttled a currency pair through the very hours it was moving.
 */
@Composable
fun CommodityTileFace(
    size: TileSize,
    flipped: Boolean,
    active: Boolean,
    symbol: String?,
    displayName: String?,
    refreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    modifier: Modifier = Modifier,
) {
    if (symbol.isNullOrBlank() || displayName == null) {
        NoCommodityPickedFace(size, modifier)
        return
    }

    var quote by remember(symbol) { mutableStateOf<StockQuote?>(null) }
    var sparkline by remember(symbol) { mutableStateOf<List<Double>>(emptyList()) }

    LaunchedEffect(symbol, active, refreshRate) {
        if (!active) return@LaunchedEffect
        while (true) {
            quote = fetchCommodityQuote(symbol)
            sparkline = fetchCommoditySparkline(symbol)
            // Market-aware: a futures or FX symbol trades essentially around
            // the clock on weekdays, so unlike an equity it is NOT throttled
            // overnight — but it does sleep through the weekend.
            delayUntilNextRefresh(
                nextMarketRefreshDelayMs(symbol, refreshRate.resolveMs(COMMODITY_REFRESH_MS)),
            )
        }
    }

    val current = quote
    if (current == null) {
        NoDataFace(size, displayName, modifier)
        return
    }

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { CommodityFront(displayName, current, size) },
        back = { CommodityBack(displayName, current, sparkline, size) },
    )
}

@Composable
private fun NoCommodityPickedFace(size: TileSize, modifier: Modifier) {
    val narrow = size.narrowLive
    val short = size.shortLive
    Column(
        modifier = modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = "tap to choose gold, silver, a currency…",
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
            text = label,
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

@Composable
private fun CommodityFront(displayName: String, quote: StockQuote, size: TileSize) {
    val narrow = size.narrowLive
    val short = size.shortLive
    val big = size == TileSize.LARGE
    val changeSign = if (quote.change >= 0) "+" else ""
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = if (narrow) Arrangement.SpaceEvenly else Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = displayName,
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

@Composable
private fun CommodityBack(displayName: String, quote: StockQuote, sparkline: List<Double>, size: TileSize) {
    val narrow = size.narrowLive
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow) 4.dp else 11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = displayName,
            color = FaceText,
            fontSize = if (narrow) 14.sp else 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        if (sparkline.size >= 2) {
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

/** A minimal intraday line chart — normalizes [points] to the drawn height, same shape as the stock tile's own. */
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

/** The compact 1×1 face (ICONS home style / SMALL tile): price + change only, never flips. */
@Composable
fun CommoditySmallFace(
    symbol: String?,
    fallback: @Composable () -> Unit,
    active: Boolean = true,
    refreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    modifier: Modifier = Modifier,
) {
    if (symbol.isNullOrBlank()) return fallback()

    var quote by remember(symbol) { mutableStateOf<StockQuote?>(null) }
    // [active] gates this exactly as it does CommodityTileFace's loop above —
    // it was missing here, so a SMALL commodity tile kept fetching with the
    // screen off and battery saver on.
    LaunchedEffect(symbol, active, refreshRate) {
        if (!active) return@LaunchedEffect
        while (true) {
            quote = fetchCommodityQuote(symbol)
            delayUntilNextRefresh(
                nextMarketRefreshDelayMs(symbol, refreshRate.resolveMs(COMMODITY_REFRESH_MS)),
            )
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
