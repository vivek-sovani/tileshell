package com.tileshell.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Preview-only tile footprints (cols × rows on the 4-column grid), kept local
 * so :core:design stays independent of the packer in :feature:start.
 */
private enum class PreviewSize(val cols: Int, val rows: Int) {
    SMALL(1, 1), MEDIUM(2, 2), WIDE(4, 2), LARGE(4, 4)
}

private val UNIT = 90.dp
private val GAP = 3.dp

/**
 * A faithful static tile: accent fill, monoline icon top-left (centred on
 * small), lowercase label lower-left (hidden on small) — per the prototype
 * `.tile` rules in styles.css.
 */
@Composable
private fun SampleTile(size: PreviewSize, accent: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    val w = UNIT * size.cols + GAP * (size.cols - 1)
    val h = UNIT * size.rows + GAP * (size.rows - 1)
    Box(modifier = Modifier.size(w, h).background(accent)) {
        if (size == PreviewSize.SMALL) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp).align(Alignment.Center))
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(11.dp)) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(34.dp))
                Spacer(Modifier.weight(1f)) // margin-top:auto → label drops to the bottom
                Text(text = label.lowercase(), color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

@Preview(name = "tile · small", showBackground = true, backgroundColor = 0xFF0A0A0D)
@Composable
private fun TileSmallPreview() {
    Box(Modifier.padding(8.dp)) { SampleTile(PreviewSize.SMALL, TileAccents.Cobalt, TileIcons["clock"], "clock") }
}

@Preview(name = "tile · medium", showBackground = true, backgroundColor = 0xFF0A0A0D)
@Composable
private fun TileMediumPreview() {
    Box(Modifier.padding(8.dp)) { SampleTile(PreviewSize.MEDIUM, TileAccents.Green, TileIcons["phone"], "phone") }
}

@Preview(name = "tile · wide", showBackground = true, backgroundColor = 0xFF0A0A0D)
@Composable
private fun TileWidePreview() {
    Box(Modifier.padding(8.dp)) { SampleTile(PreviewSize.WIDE, TileAccents.Magenta, TileIcons["calendar"], "calendar") }
}

@Preview(name = "tile · large", showBackground = true, backgroundColor = 0xFF0A0A0D)
@Composable
private fun TileLargePreview() {
    Box(Modifier.padding(8.dp)) { SampleTile(PreviewSize.LARGE, TileAccents.Cyan, TileIcons["photos"], "photos") }
}

@Preview(name = "all sizes", showBackground = true, backgroundColor = 0xFF0A0A0D, widthDp = 420, heightDp = 760)
@Composable
private fun AllSizesPreview() {
    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(GAP)) {
        Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
            SampleTile(PreviewSize.SMALL, TileAccents.Cobalt, TileIcons["clock"], "clock")
            SampleTile(PreviewSize.MEDIUM, TileAccents.Green, TileIcons["phone"], "phone")
        }
        SampleTile(PreviewSize.WIDE, TileAccents.Magenta, TileIcons["calendar"], "calendar")
        SampleTile(PreviewSize.LARGE, TileAccents.Cyan, TileIcons["photos"], "photos")
    }
}

@Preview(name = "icon set", showBackground = true, backgroundColor = 0xFF0A0A0D, widthDp = 360, heightDp = 760)
@Composable
private fun IconSetPreview() {
    val names = TileIcons.byName.keys.sorted()
    LazyVerticalGrid(columns = GridCells.Adaptive(64.dp), modifier = Modifier.background(DarkColorTokens.bg)) {
        items(names) { name ->
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(TileIcons[name], null, tint = DarkColorTokens.fg, modifier = Modifier.size(28.dp))
                Text(name, color = DarkColorTokens.fgDim, fontSize = 9.sp, fontWeight = FontWeight.Light)
            }
        }
    }
}

@Preview(name = "accents", showBackground = true, backgroundColor = 0xFF0A0A0D, widthDp = 320)
@Composable
private fun AccentsPreview() {
    LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.width(320.dp).padding(10.dp)) {
        items(TileAccents.all) { c -> Box(Modifier.padding(4.dp).size(34.dp).background(c)) }
    }
}

@Preview(name = "wallpapers", showBackground = true, widthDp = 360, heightDp = 220)
@Composable
private fun WallpapersPreview() {
    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Wallpapers.all.take(3).forEach { wp ->
            Box(Modifier.size(96.dp, 180.dp).wallpaperBackground(wp), contentAlignment = Alignment.BottomStart) {
                Text(wp.label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            }
        }
    }
}
