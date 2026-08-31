package com.tileshell.feature.personalize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.COMMODITY_CATEGORY_ORDER
import com.tileshell.core.data.COMMODITY_ITEMS
import com.tileshell.core.data.CURRENCY_CODES
import com.tileshell.core.data.CommodityItem
import com.tileshell.core.data.CurrencyCode
import com.tileshell.core.data.StockQuote
import com.tileshell.core.data.currencyPairLabel
import com.tileshell.core.data.currencyPairTicker
import com.tileshell.core.data.fetchCommodityQuote
import com.tileshell.core.data.formatStockChangePercent
import com.tileshell.core.data.formatStockPrice
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens

private sealed class CommodityPickerStage {
    object Browse : CommodityPickerStage()
    data class Preview(val item: CommodityItem, val backTo: CommodityPickerStage = Browse) : CommodityPickerStage()
}

/**
 * The commodity/currency tile's picker — a flat, curated, grouped list (see
 * [COMMODITY_ITEMS]; no search, unlike [StockPickerSheet], since there are
 * only a handful of items rather than thousands of tradable companies), with
 * the same "see the real live number before pinning" preview step
 * [StockPickerSheet] established.
 */
@Composable
fun CommodityPickerSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    tileId: String?,
    onPicked: (id: String, symbol: String, displayName: String) -> Unit,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "commodityPickerProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)

    var stage by remember { mutableStateOf<CommodityPickerStage>(CommodityPickerStage.Browse) }
    var customBase by remember { mutableStateOf<CurrencyCode?>(null) }
    var customTarget by remember { mutableStateOf<CurrencyCode?>(null) }
    LaunchedEffect(visible) {
        if (visible) {
            stage = CommodityPickerStage.Browse
            customBase = null
            customTarget = null
        }
    }

    BackHandler(enabled = visible) {
        if (stage != CommodityPickerStage.Browse) stage = CommodityPickerStage.Browse else onDismiss()
    }

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .graphicsLayer { translationY = size.height * (1f - progress) }
                    .background(tokens.sheet, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tokens.fgDim.copy(alpha = 0.5f)),
                )

                when (val current = stage) {
                    is CommodityPickerStage.Browse -> BrowseContent(
                        tokens = tokens,
                        accent = accent,
                        onPick = { item -> stage = CommodityPickerStage.Preview(item) },
                        customBase = customBase,
                        customTarget = customTarget,
                        onSelectCustomBase = { base ->
                            customBase = base
                            if (customTarget?.code == base.code) customTarget = null
                        },
                        onSelectCustomTarget = { target ->
                            customTarget = target
                            if (customBase?.code == target.code) customBase = null
                        },
                        onContinueCustomPair = {
                            val base = customBase
                            val target = customTarget
                            if (base != null && target != null) {
                                val item = CommodityItem(
                                    symbol = currencyPairTicker(base.code, target.code),
                                    displayName = currencyPairLabel(base.code, target.code),
                                    category = "currencies",
                                )
                                stage = CommodityPickerStage.Preview(item, backTo = CommodityPickerStage.Browse)
                            }
                        },
                    )
                    is CommodityPickerStage.Preview -> PreviewContent(
                        item = current.item,
                        tokens = tokens,
                        accent = accent,
                        onBack = { stage = current.backTo },
                        onConfirm = {
                            tileId?.let { id -> onPicked(id, current.item.symbol, current.item.displayName) }
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrowseContent(
    tokens: ColorTokens,
    accent: Color,
    onPick: (CommodityItem) -> Unit,
    customBase: CurrencyCode?,
    customTarget: CurrencyCode?,
    onSelectCustomBase: (CurrencyCode) -> Unit,
    onSelectCustomTarget: (CurrencyCode) -> Unit,
    onContinueCustomPair: () -> Unit,
) {
    Text(
        text = "commodities & currencies",
        color = tokens.fg,
        fontSize = 20.sp,
        fontWeight = FontWeight.W300,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
    )
    Text(
        text = "pick one to follow",
        color = tokens.fgDim,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
    )
    HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp))

    LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        COMMODITY_CATEGORY_ORDER.forEach { category ->
            val items = COMMODITY_ITEMS.filter { it.category == category }
            if (items.isEmpty()) return@forEach
            item(key = "category-$category") {
                Text(
                    text = category,
                    color = tokens.fgDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(items, key = { it.symbol }) { commodityItem ->
                Text(
                    text = commodityItem.displayName,
                    color = tokens.fg,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onPick(commodityItem) })
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
            if (category == "currencies") {
                item(key = "custom-pair-header") {
                    Text(
                        text = "or build your own pair",
                        color = accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 10.dp),
                    )
                }
                item(key = "custom-pair-from") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        Text(
                            text = "convert from",
                            color = tokens.fgDim,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CURRENCY_CODES.filter { it.code != customTarget?.code }.forEach { currency ->
                                CurrencyPill(
                                    code = currency.code,
                                    selected = currency.code == customBase?.code,
                                    accent = accent,
                                    tokens = tokens,
                                    onClick = { onSelectCustomBase(currency) },
                                )
                            }
                        }
                    }
                }
                item(key = "custom-pair-to") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        Text(
                            text = "convert to",
                            color = tokens.fgDim,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CURRENCY_CODES.filter { it.code != customBase?.code }.forEach { currency ->
                                CurrencyPill(
                                    code = currency.code,
                                    selected = currency.code == customTarget?.code,
                                    accent = accent,
                                    tokens = tokens,
                                    onClick = { onSelectCustomTarget(currency) },
                                )
                            }
                        }
                    }
                }
                item(key = "custom-pair-button") {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        ConfirmButton(
                            text = if (customBase != null && customTarget != null) {
                                "see live rate (${customBase.code} / ${customTarget.code})"
                            } else {
                                "pick both currencies"
                            },
                            accent = accent,
                            enabled = customBase != null && customTarget != null,
                            onClick = onContinueCustomPair,
                        )
                    }
                }
            }
        }
    }
}

/** A selectable currency-code pill — same shape/look as [NewsRegionSheet]'s own region chip. */
@Composable
private fun CurrencyPill(code: String, selected: Boolean, accent: Color, tokens: ColorTokens, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (selected) Modifier.background(accent)
                else Modifier.border(1.dp, tokens.tileLine, RoundedCornerShape(16.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(code, color = if (selected) Color.White else tokens.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PreviewContent(
    item: CommodityItem,
    tokens: ColorTokens,
    accent: Color,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    var quote by remember(item) { mutableStateOf<StockQuote?>(null) }
    var loading by remember(item) { mutableStateOf(true) }
    LaunchedEffect(item) {
        loading = true
        quote = fetchCommodityQuote(item.symbol)
        loading = false
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹ back",
            color = accent,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 6.dp, vertical = 8.dp),
        )
    }
    Text(
        text = item.displayName,
        color = tokens.fg,
        fontSize = 20.sp,
        fontWeight = FontWeight.W300,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
    )

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
        when {
            loading -> CircularProgressIndicator(color = accent, modifier = Modifier.size(24.dp))
            quote == null -> Text("couldn't load a live price — check your connection", color = tokens.fgDim, fontSize = 14.sp)
            else -> QuoteSummary(quote!!, tokens)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
        ConfirmButton(text = "pin to start", accent = accent, onClick = onConfirm)
    }
}

@Composable
private fun QuoteSummary(quote: StockQuote, tokens: ColorTokens) {
    Column {
        Text(
            text = formatStockPrice(quote.price, quote.currency),
            color = tokens.fg,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraLight,
        )
        Text(
            text = formatStockChangePercent(quote.changePercent),
            color = if (quote.change < 0) NegativeQuoteColor else PositiveQuoteColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = if (quote.marketOpen) "market open" else "market closed",
            color = tokens.fgDim,
            fontSize = 12.sp,
        )
    }
}

private val PositiveQuoteColor = Color(0xFF35C759)
private val NegativeQuoteColor = Color(0xFFFF453A)

@Composable
private fun ConfirmButton(text: String, accent: Color, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) accent else accent.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
