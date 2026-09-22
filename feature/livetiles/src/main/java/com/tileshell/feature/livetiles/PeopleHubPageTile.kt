package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.LocalTileFaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The live face for a People Hub page pinned to Start ([PeopleHubTile] in
 * `:core:data`) — user-requested: a "what's new"/"recent" tile must **not**
 * look like the main "people" tile's photo mosaic ("what new and rcent
 * should not show contact photos on live tile. instead if possible show few
 * lines"), and needs "a proper tile title" of its own. Shows a bold page
 * title plus a few lines of real content (recently-contacted names, or
 * notification sender+snippet lines) instead — degrades to [fallback] when
 * there's nothing to show (no permission, or genuinely empty).
 */
@Composable
fun PeopleHubPageTileFace(
    page: String,
    size: TileSize,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (page) {
        "recent" -> RecentPeopleTileFace(size, fallback, modifier)
        "what's new" -> WhatsNewTileFace(size, fallback, modifier)
        else -> fallback()
    }
}

@Composable
private fun RecentPeopleTileFace(size: TileSize, fallback: @Composable () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val maxLines = linesFor(size)
    val recent by produceState<List<PersonSummary>?>(initialValue = null, maxLines) {
        value = withContext(Dispatchers.IO) { queryRecentContacts(context, limit = maxLines) }
    }
    val list = recent
    if (list == null) return
    if (list.isEmpty()) return fallback()
    PeopleHubPageLines("recent", list.map { it.name.lowercase() }, modifier)
}

@Composable
private fun WhatsNewTileFace(size: TileSize, fallback: @Composable () -> Unit, modifier: Modifier) {
    val maxLines = linesFor(size)
    val snapshot by NotificationCenter.snapshot.collectAsStateWithLifecycle()
    val entries = remember(snapshot, maxLines) { recentActivity(snapshot, limit = maxLines) }
    if (entries.isEmpty()) return fallback()
    PeopleHubPageLines("what's new", entries.map { "${it.sender.lowercase()}: ${it.snippet}" }, modifier)
}

/** More lines fit on a taller tile; a 1-row tile still gets its title plus one line. */
private fun linesFor(size: TileSize): Int = (size.rows * 2).coerceIn(1, 6)

@Composable
private fun PeopleHubPageLines(title: String, lines: List<String>, modifier: Modifier) {
    val color = LocalTileFaceColor.current
    Column(
        modifier = modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(title, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        lines.forEach { line ->
            Text(line, color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
