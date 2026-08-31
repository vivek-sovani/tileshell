package com.tileshell.feature.personalize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.STOCK_CATEGORIES
import com.tileshell.core.data.STOCK_CATEGORY_REGION_ORDER
import com.tileshell.core.data.StockCategory
import com.tileshell.core.data.StockQuote
import com.tileshell.core.data.StockSearchResult
import com.tileshell.core.data.StockSymbolRef
import com.tileshell.core.data.fetchStockQuote
import com.tileshell.core.data.fetchStockSearch
import com.tileshell.core.data.formatStockChangePercent
import com.tileshell.core.data.formatStockPrice
import com.tileshell.core.data.multiStockLabel
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens
import kotlinx.coroutines.delay

private const val SEARCH_DEBOUNCE_MS = 250L

private sealed class StockPickerStage {
    object Browse : StockPickerStage()
    data class PreviewSingle(val symbol: String, val displayName: String) : StockPickerStage()
    data class PreviewCategory(val category: StockCategory) : StockPickerStage()
    data class MultiSelect(val selected: List<StockSearchResult>) : StockPickerStage()
    data class PreviewMulti(val picks: List<StockSearchResult>) : StockPickerStage()
}

/**
 * The stock tile's picker — search for a single stock, or browse a curated
 * sector basket (see [STOCK_CATEGORIES]), and, before pinning anything, see
 * real live numbers for what you're about to follow: [StockPickerStage
 * .PreviewSingle]/[StockPickerStage.PreviewCategory] fetch and show the
 * actual current price(s) right in the sheet, with a separate "pin to start"
 * action — unlike [SportsPickerSheet]'s immediate pin-on-tap, since a stock
 * pick benefits from confirming the number looks right first.
 */
@Composable
fun StockPickerSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    tileId: String?,
    onSinglePicked: (id: String, symbol: String, displayName: String) -> Unit,
    onCategoryPicked: (id: String, categoryId: String, displayName: String) -> Unit,
    onMultiPicked: (id: String, symbols: List<Pair<String, String>>, displayName: String) -> Unit,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "stockPickerProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)

    var stage by remember { mutableStateOf<StockPickerStage>(StockPickerStage.Browse) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(visible) {
        if (visible) {
            stage = StockPickerStage.Browse
            query = ""
        }
    }

    BackHandler(enabled = visible) {
        if (stage != StockPickerStage.Browse) stage = StockPickerStage.Browse else onDismiss()
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
                    is StockPickerStage.Browse -> BrowseContent(
                        tokens = tokens,
                        accent = accent,
                        query = query,
                        onQueryChange = { query = it },
                        onPickSearchResult = { result -> stage = StockPickerStage.PreviewSingle(result.symbol, result.displayName) },
                        onPickCategory = { category -> stage = StockPickerStage.PreviewCategory(category) },
                        onBuildCustomList = { stage = StockPickerStage.MultiSelect(emptyList()) },
                    )
                    is StockPickerStage.PreviewSingle -> PreviewSingleContent(
                        symbol = current.symbol,
                        displayName = current.displayName,
                        tokens = tokens,
                        accent = accent,
                        onBack = { stage = StockPickerStage.Browse },
                        onConfirm = {
                            tileId?.let { id -> onSinglePicked(id, current.symbol, current.displayName) }
                            onDismiss()
                        },
                    )
                    is StockPickerStage.PreviewCategory -> PreviewCategoryContent(
                        category = current.category,
                        tokens = tokens,
                        accent = accent,
                        onBack = { stage = StockPickerStage.Browse },
                        onConfirm = {
                            tileId?.let { id -> onCategoryPicked(id, current.category.id, current.category.displayName) }
                            onDismiss()
                        },
                    )
                    is StockPickerStage.MultiSelect -> MultiSelectContent(
                        selected = current.selected,
                        tokens = tokens,
                        accent = accent,
                        onBack = { stage = StockPickerStage.Browse },
                        onToggle = { result ->
                            val already = current.selected.any { it.symbol == result.symbol }
                            stage = StockPickerStage.MultiSelect(
                                if (already) current.selected.filterNot { it.symbol == result.symbol } else current.selected + result,
                            )
                        },
                        onNext = { stage = StockPickerStage.PreviewMulti(current.selected) },
                    )
                    is StockPickerStage.PreviewMulti -> PreviewMultiContent(
                        picks = current.picks,
                        tokens = tokens,
                        accent = accent,
                        onBack = { stage = StockPickerStage.MultiSelect(current.picks) },
                        onConfirm = {
                            tileId?.let { id ->
                                val symbols = current.picks.map { it.symbol to it.displayName }
                                onMultiPicked(id, symbols, multiStockLabel(current.picks.map { it.displayName }))
                            }
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseContent(
    tokens: ColorTokens,
    accent: Color,
    query: String,
    onQueryChange: (String) -> Unit,
    onPickSearchResult: (StockSearchResult) -> Unit,
    onPickCategory: (StockCategory) -> Unit,
    onBuildCustomList: () -> Unit,
) {
    Text(
        text = "stocks",
        color = tokens.fg,
        fontSize = 20.sp,
        fontWeight = FontWeight.W300,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
    )
    Text(
        text = "search a stock, or follow a whole sector",
        color = tokens.fgDim,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
    )
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.fg.copy(alpha = 0.06f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = tokens.fg, fontSize = 15.sp),
            cursorBrush = SolidColor(accent),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("e.g. apple, reliance, TCS", color = tokens.fgDim.copy(alpha = 0.6f), fontSize = 15.sp)
                }
                inner()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val trimmed = query.trim()
    val results by produceState(emptyList<StockSearchResult>(), trimmed) {
        if (trimmed.length < 2) {
            value = emptyList()
        } else {
            delay(SEARCH_DEBOUNCE_MS)
            value = fetchStockSearch(trimmed)
        }
    }

    HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))

    LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        if (trimmed.length >= 2) {
            if (results.isEmpty()) {
                item {
                    Text(
                        text = "no matches yet",
                        color = tokens.fgDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
            items(results, key = { "search-${it.symbol}" }) { result ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onPickSearchResult(result) })
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(result.displayName, color = tokens.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${result.symbol} · ${result.exchange}", color = tokens.fgDim, fontSize = 12.sp)
                }
            }
        } else {
            item(key = "build-custom-list") {
                Text(
                    text = "+ build a custom list of stocks",
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onBuildCustomList)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
            item(key = "custom-list-divider") {
                HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            STOCK_CATEGORY_REGION_ORDER.forEach { region ->
                val categories = STOCK_CATEGORIES.filter { it.region == region }
                if (categories.isEmpty()) return@forEach
                item(key = "region-$region") {
                    Text(
                        text = region,
                        color = tokens.fgDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                items(categories, key = { it.id }) { category ->
                    Text(
                        text = category.displayName,
                        color = tokens.fg,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = { onPickCategory(category) })
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewSingleContent(
    symbol: String,
    displayName: String,
    tokens: ColorTokens,
    accent: Color,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    var quote by remember(symbol) { mutableStateOf<StockQuote?>(null) }
    var loading by remember(symbol) { mutableStateOf(true) }
    LaunchedEffect(symbol) {
        loading = true
        quote = fetchStockQuote(symbol)
        loading = false
    }

    BackRow(accent = accent, onBack = onBack)
    Text(
        text = displayName,
        color = tokens.fg,
        fontSize = 20.sp,
        fontWeight = FontWeight.W300,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
    )
    Text(symbol, color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp))

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
private fun PreviewCategoryContent(
    category: StockCategory,
    tokens: ColorTokens,
    accent: Color,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    var quotes by remember(category) { mutableStateOf<Map<String, StockQuote?>>(emptyMap()) }
    var loading by remember(category) { mutableStateOf(true) }
    LaunchedEffect(category) {
        loading = true
        quotes = category.symbols.associate { it.symbol to fetchStockQuote(it.symbol) }
        loading = false
    }

    BackRow(accent = accent, onBack = onBack)
    Text(
        text = category.displayName,
        color = tokens.fg,
        fontSize = 20.sp,
        fontWeight = FontWeight.W300,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
    )
    Text(
        text = "${category.symbols.size} stocks — shows every one at large size",
        color = tokens.fgDim,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
    )
    HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp))

    if (loading) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp))
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(category.symbols, key = { it.symbol }) { ref -> CategoryMemberRow(ref, quotes[ref.symbol], tokens) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
        ConfirmButton(text = "pin to start", accent = accent, onClick = onConfirm)
    }
}

@Composable
private fun CategoryMemberRow(ref: StockSymbolRef, quote: StockQuote?, tokens: ColorTokens) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(ref.displayName, color = tokens.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (quote != null) {
            Text(
                text = formatStockChangePercent(quote.changePercent),
                color = if (quote.change < 0) NegativeQuoteColor else PositiveQuoteColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            Text("…", color = tokens.fgDim, fontSize = 13.sp)
        }
    }
}

/**
 * Building a [StockTile.Selection.MultiStock] list: the same search box as
 * [BrowseContent], but a tap toggles a result into/out of [selected] instead
 * of navigating away — so picking several stocks is one continuous scroll of
 * search-and-tap rather than repeating the whole picker flow per stock.
 */
@Composable
private fun MultiSelectContent(
    selected: List<StockSearchResult>,
    tokens: ColorTokens,
    accent: Color,
    onBack: () -> Unit,
    onToggle: (StockSearchResult) -> Unit,
    onNext: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    BackRow(accent = accent, onBack = onBack)
    Text(
        text = "custom list",
        color = tokens.fg,
        fontSize = 20.sp,
        fontWeight = FontWeight.W300,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
    )
    Text(
        text = if (selected.isEmpty()) "search and tap to add stocks" else "${selected.size} selected",
        color = tokens.fgDim,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
    )
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.fg.copy(alpha = 0.06f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = tokens.fg, fontSize = 15.sp),
            cursorBrush = SolidColor(accent),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("e.g. apple, reliance, TCS", color = tokens.fgDim.copy(alpha = 0.6f), fontSize = 15.sp)
                }
                inner()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val trimmed = query.trim()
    val results by produceState(emptyList<StockSearchResult>(), trimmed) {
        if (trimmed.length < 2) {
            value = emptyList()
        } else {
            delay(SEARCH_DEBOUNCE_MS)
            value = fetchStockSearch(trimmed)
        }
    }

    HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        if (selected.isNotEmpty() && trimmed.length < 2) {
            item(key = "selected-header") {
                Text(
                    text = "selected",
                    color = tokens.fgDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(selected, key = { "picked-${it.symbol}" }) { result -> MultiSelectRow(result, checked = true, tokens = tokens, onToggle = onToggle) }
        }
        if (trimmed.length >= 2) {
            items(results, key = { "search-${it.symbol}" }) { result ->
                MultiSelectRow(result, checked = selected.any { it.symbol == result.symbol }, tokens = tokens, onToggle = onToggle)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
        ConfirmButton(
            text = if (selected.isEmpty()) "add at least one stock" else "next (${selected.size} selected)",
            accent = accent,
            enabled = selected.isNotEmpty(),
            onClick = onNext,
        )
    }
}

@Composable
private fun MultiSelectRow(result: StockSearchResult, checked: Boolean, tokens: ColorTokens, onToggle: (StockSearchResult) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onToggle(result) })
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(result.displayName, color = tokens.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${result.symbol} · ${result.exchange}", color = tokens.fgDim, fontSize = 12.sp)
        }
        Text(
            text = if (checked) "✓" else "+",
            color = if (checked) PositiveQuoteColor else tokens.fgDim,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** Live preview + confirm for a [StockTile.Selection.MultiStock] list, before pinning — same shape as [PreviewCategoryContent]. */
@Composable
private fun PreviewMultiContent(
    picks: List<StockSearchResult>,
    tokens: ColorTokens,
    accent: Color,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    var quotes by remember(picks) { mutableStateOf<Map<String, StockQuote?>>(emptyMap()) }
    var loading by remember(picks) { mutableStateOf(true) }
    LaunchedEffect(picks) {
        loading = true
        quotes = picks.associate { it.symbol to fetchStockQuote(it.symbol) }
        loading = false
    }

    BackRow(accent = accent, onBack = onBack)
    Text(
        text = multiStockLabel(picks.map { it.displayName }),
        color = tokens.fg,
        fontSize = 20.sp,
        fontWeight = FontWeight.W300,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
    )
    Text(
        text = "${picks.size} stocks — shows one below large size, every one at large size",
        color = tokens.fgDim,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
    )
    HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp))

    if (loading) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp))
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(picks, key = { it.symbol }) { pick ->
                CategoryMemberRow(StockSymbolRef(pick.symbol, pick.displayName), quotes[pick.symbol], tokens)
            }
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
private fun BackRow(accent: Color, onBack: () -> Unit) {
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
}

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
