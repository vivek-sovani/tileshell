package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.StockQuote
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.marketsRefreshDelayMs
import com.tileshell.core.data.settings.LiveRefreshRate
import com.tileshell.core.data.settings.resolveMs
import com.tileshell.core.data.fetchStockQuote
import com.tileshell.core.data.formatStockChangePercent
import com.tileshell.core.data.formatStockPrice
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileIcons

private const val MARKETS_TILE_REFRESH_MS = 60_000L

/**
 * The markets hub's tile: the markets glyph, and — from medium up — the NIFTY
 * 50's level and day change. Refreshes like the stock tile: only while [active],
 * at the user's stock rate in market hours and through to the opening bell otherwise.
 * Tapping opens the hub.
 */
@Composable
fun MarketsTileFace(size: TileSize, active: Boolean, refreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT, modifier: Modifier = Modifier) {
    val color = LocalTileFaceColor.current
    var quote by remember { mutableStateOf<StockQuote?>(null) }
    val showsQuote = size != TileSize.SMALL
    LaunchedEffect(active, showsQuote, refreshRate) {
        if (!active || !showsQuote) return@LaunchedEffect
        while (true) {
            fetchStockQuote("^NSEI")?.let { quote = it }
            delayUntilNextRefresh(marketsRefreshDelayMs(listOf("^NSEI"), refreshRate.resolveMs(MARKETS_TILE_REFRESH_MS)))
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        val q = quote
        if (showsQuote && q != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("NIFTY 50", color = color, fontSize = 12.sp)
                Column {
                    Text(formatStockPrice(q.price, ""), color = color, fontSize = if (size.cols >= 2) 26.sp else 18.sp, fontWeight = FontWeight.Light)
                    Text(formatStockChangePercent(q.changePercent), color = color, fontSize = 14.sp)
                }
                Text("markets", color = color, fontSize = 12.sp)
            }
        } else {
            Icon(TileIcons["markets"], contentDescription = null, tint = color, modifier = Modifier.align(Alignment.Center).size(if (size == TileSize.SMALL) 28.dp else 44.dp))
            if (size != TileSize.SMALL) {
                Text("markets", color = color, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
        }
    }
}

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
