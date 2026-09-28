package com.tileshell.feature.livetiles.money

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.data.TileSize
import com.tileshell.feature.livetiles.FlipTile
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.feature.livetiles.openApp
import com.tileshell.feature.livetiles.rememberMonochromeAppIcon
import kotlinx.coroutines.delay

private const val MONEY_FLIP_MS = 6_000L

/**
 * The money tile. Front: just the ₹ glyph, or — when "show on tile" is set to
 * it — the last payment and the last money received. Back: installed payment
 * apps; tapping one opens it (a tap elsewhere opens the hub). Flips on its own
 * while [active], only when there are payment apps to show.
 */
@Composable
fun MoneyTileFace(size: TileSize, active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { MoneyStore.ensureLoaded(context) }
    val settings by MoneyPrefs.settings(context).collectAsStateWithLifecycle()
    val txns by MoneyStore.transactions.collectAsStateWithLifecycle()
    val payment = rememberMoneyApps()?.filter { it.kind == MoneyAppKind.PAYMENT }.orEmpty()

    var flipped by remember { mutableStateOf(false) }
    val canFlip = payment.isNotEmpty() && size != TileSize.SMALL
    LaunchedEffect(active, canFlip) {
        if (!active || !canFlip) {
            flipped = false
            return@LaunchedEffect
        }
        while (true) {
            delay(MONEY_FLIP_MS)
            flipped = !flipped
        }
    }
    val showDetails = settings?.tileDetails == MoneyTileDetails.LAST_PAYMENT_AND_RECEIPT
    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = {
            if (showDetails && size != TileSize.SMALL) {
                MoneyDetailsFront(txns.firstOrNull { !it.credit }, txns.firstOrNull { it.credit }, size)
            } else {
                MoneyGlyphFront(size)
            }
        },
        back = { MoneyAppsBack(payment, size) },
    )
}

@Composable
private fun MoneyGlyphFront(size: TileSize) {
    val color = LocalTileFaceColor.current
    Box(modifier = Modifier.fillMaxSize()) {
        Text("₹", color = color, fontSize = if (size == TileSize.SMALL) 28.sp else 44.sp, modifier = Modifier.align(Alignment.Center))
        if (size != TileSize.SMALL) {
            Text("money", color = color, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
    }
}

@Composable
private fun MoneyDetailsFront(paid: MoneyTxn?, received: MoneyTxn?, size: TileSize) {
    val color = LocalTileFaceColor.current
    val big = size.rows >= 2
    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        MoneyLine("last paid", paid, "−", color, big)
        MoneyLine("last received", received, "+", color, big)
        Text("money", color = color, fontSize = 12.sp)
    }
}

@Composable
private fun MoneyLine(label: String, txn: MoneyTxn?, sign: String, color: androidx.compose.ui.graphics.Color, big: Boolean) {
    Column {
        Text(label, color = color.copy(alpha = 0.8f), fontSize = 10.sp)
        Text(
            if (txn == null) "—" else "$sign${formatRupees(txn.amountPaise)} ${txn.counterparty}",
            color = color,
            fontSize = if (big) 15.sp else 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (txn != null && big) Text(timeAgo(txn.time), color = color.copy(alpha = 0.8f), fontSize = 10.sp)
    }
}

@Composable
private fun MoneyAppsBack(apps: List<MoneyApp>, size: TileSize) {
    val context = LocalContext.current
    val color = LocalTileFaceColor.current
    val columns = size.cols.coerceAtLeast(1).let { if (it == 2) 3 else it }
    val rows = size.rows.coerceAtLeast(1)
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 18.dp)) {
            apps.take(columns * rows).chunked(columns).forEach { rowApps ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    rowApps.forEach { app ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { openApp(context, app.packageName) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            val icon = rememberMonochromeAppIcon(app.packageName, sizePx = 96)
                            if (icon != null) {
                                Image(icon, app.label, colorFilter = ColorFilter.tint(color), modifier = Modifier.size(26.dp))
                            }
                        }
                    }
                    repeat(columns - rowApps.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Text("pay", color = color, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 4.dp))
    }
}

internal fun timeAgo(time: Long, now: Long = System.currentTimeMillis()): String {
    val m = (now - time) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 24 * 60 -> "${m / 60}h ago"
        else -> "${m / (24 * 60)}d ago"
    }
}
