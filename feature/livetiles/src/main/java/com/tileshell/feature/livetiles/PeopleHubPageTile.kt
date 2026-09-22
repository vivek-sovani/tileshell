package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Matches ConversationTileFace's own back-face cycle cadence.
private const val WHATS_NEW_CYCLE_MS = 2_600L

/**
 * The live face for a People Hub page pinned to Start ([PeopleHubTile] in
 * `:core:data`) — user-requested: a "what's new"/"recent" tile must **not**
 * look like the main "people" tile's photo mosaic ("what new and rcent
 * should not show contact photos on live tile. instead if possible show few
 * lines"), and needs "a proper tile title" of its own. "what's new" then
 * became "built like email showing rotating clickable messages on flip
 * side. and no.of new on front face" — so it now shares the exact same
 * front/back shape as the mail/messages tile ([ConversationCountFace] /
 * [NotificationFaceContent]), just aggregated across every people-related
 * app instead of pinned to one. It isn't part of the shared random-flip
 * scheduler (that's keyed off `LiveFace.forIconKey("people")`, which the
 * plain photo-mosaic people tile needs to stay `flips = false`), so it
 * drives its own flip/cycle timer instead, the same way `MusicTileFace`/
 * `PhotosTileFace` self-drive theirs. "recent" stays a simple few-lines
 * face — degrades to [fallback] when there's nothing to show.
 */
@Composable
fun PeopleHubPageTileFace(
    page: String,
    size: TileSize,
    active: Boolean,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (page) {
        "recent" -> RecentPeopleTileFace(size, fallback, modifier)
        "what's new" -> WhatsNewTileFace(size, active, fallback, modifier)
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
    Box(modifier = modifier.fillMaxSize()) {
        PeopleHubPageLines("recent", list.map { it.name.lowercase() }, Modifier.fillMaxSize())
        PageIconCorner("clock")
    }
}

/** A small corner glyph identifying which People Hub page a pinned tile
 * shows — user-requested ("can we have icons for recent and what new"), so
 * the two page tiles aren't just distinguished by their title text. Mirrors
 * `AppIconCorner`'s own top-start position/size on the mail/messages tiles. */
@Composable
private fun BoxScope.PageIconCorner(iconKey: String) {
    Icon(
        imageVector = TileIcons[iconKey],
        contentDescription = null,
        tint = LocalTileFaceColor.current,
        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(18.dp),
    )
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

@Composable
private fun WhatsNewTileFace(size: TileSize, active: Boolean, fallback: @Composable () -> Unit, modifier: Modifier) {
    val snapshot by NotificationCenter.snapshot.collectAsStateWithLifecycle()
    val entries = remember(snapshot) { recentActivity(snapshot) }
    if (entries.isEmpty()) {
        // Nothing pending — no back face to show, so this tile never claims
        // to have "displayed" anything for a tap to open.
        SideEffect { NotificationCenter.reportWhatsNewDisplayed(null, null) }
        return fallback()
    }

    // Flip the whole tile between the front count face and the back
    // cycling-message face, same cadence as the shared scheduler.
    var flipped by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active) {
            flipped = false
            return@LaunchedEffect
        }
        while (true) {
            delay(WHATS_NEW_CYCLE_MS)
            flipped = !flipped
        }
    }

    // While flipped (back face showing), also cycle through each pending
    // entry in turn, same as ConversationTileFace's own item cycling.
    val itemIndex = remember { mutableIntStateOf(0) }
    LaunchedEffect(active, entries.size) {
        itemIndex.intValue = 0
        if (!active || entries.size <= 1) return@LaunchedEffect
        while (true) {
            delay(WHATS_NEW_CYCLE_MS)
            itemIndex.intValue = (itemIndex.intValue + 1) % entries.size
        }
    }
    val current = entries.getOrElse(itemIndex.intValue) { entries.first() }

    // Only claim a tap while the back face (a specific message) is actually
    // showing — the front/count face's tap should still open the hub.
    SideEffect {
        NotificationCenter.reportWhatsNewDisplayed(
            if (flipped) current.packageName else null,
            if (flipped) current.notificationKey.ifEmpty { null } else null,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        FlipTile(
            flipped = flipped,
            modifier = Modifier.fillMaxSize(),
            front = { ConversationCountFace(entries.size, "new", size) },
            back = {
                NotificationFaceContent(
                    item = ConversationItem(
                        sender = current.sender,
                        snippet = current.snippet,
                        notificationKey = current.notificationKey,
                    ),
                    avatar = null,
                    picture = null,
                    size = size,
                )
            },
        )
        PageIconCorner("bell")
    }
}
