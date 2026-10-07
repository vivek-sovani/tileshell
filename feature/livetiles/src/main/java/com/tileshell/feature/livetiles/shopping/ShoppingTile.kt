package com.tileshell.feature.livetiles.shopping

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileIcons
import com.tileshell.feature.livetiles.FlipTile
import com.tileshell.feature.livetiles.iconPx
import com.tileshell.feature.livetiles.openApp
import com.tileshell.feature.livetiles.rememberMonochromeAppIcon
import kotlinx.coroutines.delay

private const val SHOPPING_FLIP_MS = 6_000L

/**
 * The shopping tile. Front: what is arriving (each order's item, store, and when it arrives), or just the bag glyph
 * when nothing is. Back: all your shopping apps as icons; tapping one opens it (a tap elsewhere opens the hub). With
 * more apps than fit, each turn to the back shows the next page. Flips on its own while [active], only when there are
 * apps to show.
 */
@Composable
fun ShoppingTileFace(size: TileSize, active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ShoppingStore.ensureLoaded(context) }
    val orders by ShoppingStore.orders.collectAsStateWithLifecycle()
    val marks by ShoppingTileMarks.marks(context).collectAsStateWithLifecycle()
    val apps = appsOnTile(rememberShoppingApps().orEmpty().distinctBy { it.packageName }, marks.orEmpty())
    val arriving = remember(orders) { arrivingOrders(orders) }

    var flipped by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    val canFlip = apps.isNotEmpty() && size != TileSize.SMALL
    LaunchedEffect(active, canFlip) {
        if (!active || !canFlip) {
            flipped = false
            return@LaunchedEffect
        }
        while (true) {
            delay(SHOPPING_FLIP_MS)
            flipped = !flipped
            // Each turn to the back shows the next page of apps.
            if (flipped) page++
        }
    }
    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { if (size == TileSize.SMALL || arriving.isEmpty()) ShoppingGlyphFront(size, arriving.size) else ShoppingArrivingFront(arriving, size) },
        back = { ShoppingAppsBack(apps, size, page) },
    )
}

@Composable
private fun ShoppingGlyphFront(size: TileSize, arriving: Int) {
    val color = LocalTileFaceColor.current
    Box(modifier = Modifier.fillMaxSize()) {
        Icon(TileIcons["shophub"], contentDescription = null, tint = color, modifier = Modifier.align(Alignment.Center).size(if (size == TileSize.SMALL) 28.dp else 44.dp))
        if (size != TileSize.SMALL) {
            Text("shopping", color = color, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
    }
}

@Composable
private fun ShoppingArrivingFront(orders: List<Order>, size: TileSize) {
    val color = LocalTileFaceColor.current
    val big = size.rows >= 2
    // One order per row of tile height, two on a short wide tile.
    val fits = size.rows.coerceAtLeast(1).coerceAtMost(4)
    Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            orders.take(fits).forEach { o ->
                Column {
                    Text(o.title, color = color, fontSize = if (big) 15.sp else 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(o.merchant, o.eta ?: o.status.label).joinToString(" · "),
                        color = color.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            if (orders.size > fits) "shopping · +${orders.size - fits} more" else "shopping · ${orders.size} arriving",
            color = color, fontSize = 12.sp, maxLines = 1,
        )
    }
}

@Composable
private fun ShoppingAppsBack(apps: List<ShoppingApp>, size: TileSize, page: Int) {
    val context = LocalContext.current
    val color = LocalTileFaceColor.current
    val columns = size.cols.coerceAtLeast(1).let { if (it == 2) 3 else it }
    val rows = size.rows.coerceAtLeast(1)
    val perPage = columns * rows
    val pages = ((apps.size + perPage - 1) / perPage).coerceAtLeast(1)
    val shown = apps.drop((page % pages) * perPage).take(perPage)
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 18.dp)) {
            shown.chunked(columns).forEach { rowApps ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    rowApps.forEach { app ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { openApp(context, app.packageName) },
                            contentAlignment = Alignment.Center,
                        ) {
                            val icon = rememberMonochromeAppIcon(app.packageName, sizePx = iconPx(26.dp))
                            if (icon != null) {
                                Image(icon, app.label, colorFilter = ColorFilter.tint(color), modifier = Modifier.size(26.dp))
                            }
                        }
                    }
                    repeat(columns - rowApps.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Text(
            if (pages > 1) "shop · ${(page % pages) + 1}/$pages" else "shop",
            color = color, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 4.dp),
        )
    }
}
