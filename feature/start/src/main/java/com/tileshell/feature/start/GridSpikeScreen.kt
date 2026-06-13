package com.tileshell.feature.start

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The 14 accent colours (CLAUDE.md / design/.../launcher/data.js).
private val ACCENTS = listOf(
    0xFF2B78E4, 0xFF1452CC, 0xFF6B3FD4, 0xFFC4287E, 0xFFD6262B, 0xFFE5641E, 0xFFE2A200,
    0xFF7CB518, 0xFF1F9E57, 0xFF0F9B9B, 0xFF1399C6, 0xFF5A6B7B, 0xFF9B6A8F, 0xFF3A4554,
).map { Color(it) }

private val ScreenBg = Color(0xFF0A0A0D)

/**
 * Deterministic mixed-size tile set for the spike: a steady stream of small
 * tiles broken up by mediums, with the occasional wide and large to exercise
 * row spans and dense back-fill.
 */
fun demoTiles(count: Int): List<TileSpec> = List(count) { i ->
    val size = when {
        i % 17 == 5 -> TileSize.LARGE
        i % 7 == 3 -> TileSize.WIDE
        i % 3 == 0 -> TileSize.MEDIUM
        else -> TileSize.SMALL
    }
    TileSpec(id = "t$i", size = size)
}

/**
 * S3 spike screen: 60 dummy colored tiles packed by [GridPacker] and rendered
 * via [DenseTileGrid] in a vertical scroller, to validate dense packing and
 * scroll smoothness on device. Throwaway scaffolding — replaced by the real
 * Start screen in S6.
 */
@Composable
fun GridSpikeScreen(modifier: Modifier = Modifier) {
    val tiles = remember { demoTiles(60) }
    Box(modifier = modifier.fillMaxSize().background(ScreenBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding(),
        ) {
            DenseTileGrid(tiles = tiles) { spec -> DemoTile(spec) }
        }
    }
}

@Composable
private fun DemoTile(spec: TileSpec) {
    val index = spec.id.removePrefix("t").toIntOrNull() ?: 0
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ACCENTS[index % ACCENTS.size]),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = spec.id,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(8.dp),
        )
    }
}
