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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.tileshell.core.data.WeatherPlaceResult
import com.tileshell.core.data.fetchWeatherPlaceSearch
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens
import kotlinx.coroutines.delay

private const val SEARCH_DEBOUNCE_MS = 250L

/**
 * The weather tile/widget's own location choice — shown once, right when a
 * new instance is added (user-requested: "when weather tile and widget is
 * added, ask for current location or select location and establish widget/
 * tile based on user choice… multiple weather tile/widgets are allowed").
 * Two outcomes: [onUseCurrentLocation] (follows the device's coarse location,
 * the pre-existing behaviour, now an explicit choice instead of the only
 * option) or [onPickPlace] (a fixed, geocoded place — search-as-you-type via
 * [fetchWeatherPlaceSearch], same shape as [StockPickerSheet]'s stock search).
 * The caller is responsible for actually creating the tile/writing the widget
 * config once one of the two callbacks fires — this sheet only decides which.
 */
@Composable
fun WeatherLocationSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onUseCurrentLocation: () -> Unit,
    onPickPlace: (lat: Double, lon: Double, name: String) -> Unit,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "weatherLocationSheetProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)

    BackHandler(enabled = visible) { onDismiss() }

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
                    .fillMaxHeight(0.75f)
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
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tokens.fgDim.copy(alpha = 0.4f)),
                )

                Text(
                    text = "weather location",
                    color = tokens.fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W300,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                )
                Text(
                    text = "follow this device's own location, or pick a fixed place — you can add another " +
                        "weather tile or widget for a different place any time.",
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                )

                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.14f))
                        .clickable(onClick = onUseCurrentLocation)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("use current location", color = tokens.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("follows this device wherever it goes", color = tokens.fgDim, fontSize = 12.sp)
                    }
                    Text("›", color = accent, fontSize = 18.sp)
                }

                HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))

                Text(
                    text = "or search a place",
                    color = tokens.fg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                )

                var query by remember { mutableStateOf("") }
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
                                Text("e.g. london, mumbai, tokyo", color = tokens.fgDim.copy(alpha = 0.6f), fontSize = 15.sp)
                            }
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                val trimmed = query.trim()
                val results by produceState(emptyList<WeatherPlaceResult>(), trimmed) {
                    if (trimmed.length < 2) {
                        value = emptyList()
                    } else {
                        delay(SEARCH_DEBOUNCE_MS)
                        value = fetchWeatherPlaceSearch(trimmed)
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                    if (trimmed.length >= 2 && results.isEmpty()) {
                        item {
                            Text(
                                text = "no matches yet",
                                color = tokens.fgDim,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                    }
                    items(results, key = { "${it.lat}-${it.lon}" }) { result ->
                        WeatherPlaceRow(result, tokens) { onPickPlace(result.lat, result.lon, result.displayName) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherPlaceRow(result: WeatherPlaceResult, tokens: ColorTokens, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(result.displayName, color = tokens.fg, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
