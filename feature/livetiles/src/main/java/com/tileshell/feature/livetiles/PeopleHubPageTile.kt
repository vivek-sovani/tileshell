package com.tileshell.feature.livetiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.input.pointer.pointerInput
import com.tileshell.core.data.settings.SettingsRepository
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.asImageBitmap
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

// Matches ConversationTileFace's own item-cycling cadence (which item shows
// while the back face is up) — that part really does tick every 2.6s.
private const val WHATS_NEW_ITEM_CYCLE_MS = 2_600L

// How long this tile dwells on each face before flipping. A real mail/
// messages tile does NOT flip itself every 2.6s — that interval belongs to
// the shared *scheduler* (rememberFlipState in FlipTile.kt), which ticks
// every 2.6s but only flips one RANDOMLY-CHOSEN tile among every flippable
// one on screen, so any single tile flips far less often than that in
// practice. This tile isn't part of that scheduler (see the class doc), so
// it drives its own dwell time instead — user-reported the original 2.6s
// flip felt "very fast" compared to a real mail tile; this longer dwell
// approximates the shared scheduler's real per-tile cadence.
private const val WHATS_NEW_FLIP_MS = 15_000L

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
    // False in edit mode: a tap on a favourite then selects the tile instead.
    interactive: Boolean = true,
) {
    when (page) {
        "favourites", "recent" -> FavouritesTileFace(size, interactive, fallback, modifier)
        "what's new" -> WhatsNewTileFace(size, active, fallback, modifier)
        "apps" -> PeopleAppsTileFace(size, active, fallback, modifier)
        else -> fallback()
    }
}

@Composable
private fun FavouritesTileFace(size: TileSize, interactive: Boolean, fallback: @Composable () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository.create(context) }
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings ?: return
    LaunchedEffect(Unit) { MessagedLog.ensureLoaded(context) }
    val log by MessagedLog.entries.collectAsStateWithLifecycle()
    // Starred contacts in the user's order, minus any taken off the tile;
    // with none starred, people who recently messaged, as before. Null while
    // loading; the flag says whether anyone is starred.
    val loaded by produceState<Pair<Boolean, List<PersonSummary>>?>(
        initialValue = null, s.favouritesOrder, s.favouritesOffTile, log,
    ) {
        value = withContext(Dispatchers.IO) {
            val starred = queryFavouriteContacts(context)
            if (starred.isEmpty()) {
                false to matchMessaged(log, queryAllContacts(context)).map { it.first }.take(MAX_FAVOURITE_ROWS)
            } else {
                true to tileFavourites(orderedFavourites(starred, s.favouritesOrder), s.favouritesOffTile)
            }
        }
    }
    val (hasStarred, list) = loaded ?: return
    if (list.isEmpty() && !hasStarred) return fallback()
    val color = LocalTileFaceColor.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // A fixed count per tile height (4 on a 2x2), and the rows share the
        // height evenly, so names and photos grow with the tile.
        val capacity = favouritesTileCapacity(size.rows)
        val rowHeight = (maxHeight - FAVOURITE_HEADER_HEIGHT) / capacity.coerceAtLeast(1)
        // Names as large as the row allows (user-reported: too small at 12sp).
        val nameSize = (rowHeight.value * 0.56f).coerceIn(12f, 20f).sp
        val avatarSize = (rowHeight * 0.8f).coerceIn(18.dp, 44.dp)
        LaunchedEffect(capacity) { FavouritesTileCapacity.report(context, capacity) }
        val (shown, more) = favouritesTileSplit(list, capacity)
        // A 2-column tile only has room for the count beside the title.
        val wide = maxWidth >= 200.dp
        // One column wide: no room for a name beside the photo, so the photos stand alone, centred.
        val iconsOnly = maxWidth < 100.dp
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
            // "+ N more" sits in the title row, so it never costs a person's
            // row; tapping the title or empty space opens favourites (the
            // tile's own tap).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(FAVOURITE_TITLE_HEIGHT).claimTouches(),
            ) {
                // On a 2-column tile the heart would squeeze "favourites"
                // once "+N" is showing; the title names the tile anyway.
                if (wide || more == 0 || iconsOnly) {
                    Icon(TileIcons["heart"], contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                }
                if (iconsOnly) {
                    Spacer(Modifier.weight(1f))
                } else {
                    Text(
                        "favourites",
                        color = color,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (more > 0) {
                    val label = if (wide) "+$more more ›" else "+$more"
                    Text(label, color = color, fontSize = 12.sp, maxLines = 1)
                }
            }
            if (list.isEmpty()) {
                Text("mark people \"on tile\" in favourites", color = color.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 3)
            }
            shown.forEach { person ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (iconsOnly) Arrangement.Center else Arrangement.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .personTap(enabled = interactive) { PeopleHubNavigation.showQuickActions(person) },
                ) {
                    ContactAvatar(person, size = avatarSize, fontSize = (avatarSize.value * 0.4f).sp)
                    if (!iconsOnly) Spacer(Modifier.width(6.dp))
                    if (!iconsOnly) Text(
                        person.name.lowercase(),
                        color = color,
                        fontSize = nameSize,
                        lineHeight = nameSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.fillMaxWidth().weight(1f).claimTouches())
        }
    }
}

private const val MAX_FAVOURITE_ROWS = 8

private val FAVOURITE_TITLE_HEIGHT = 20.dp

// Title row plus the column's vertical padding (2 x 4dp).
private val FAVOURITE_HEADER_HEIGHT = 28.dp

/**
 * A quick tap on a person's row. Lets the tile's own gestures win otherwise:
 * a hold (the long-press into edit mode) or a scroll never fires it, and only
 * the tap's own release is consumed, so the tile doesn't also open the hub.
 */
private fun Modifier.personTap(enabled: Boolean, onTap: () -> Unit): Modifier =
    if (!enabled) this else pointerInput(onTap) {
        val slop = 7.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val up = withTimeoutOrNull(PERSON_TAP_MAX_MS) { waitForUpOrCancellation() } ?: return@awaitEachGesture
            if ((up.position - down.position).getDistance() > slop) return@awaitEachGesture
            up.consume()
            onTap()
        }
    }

/**
 * Takes taps on the title row and the empty space below the list without
 * consuming them, so the tile's own tap opens favourites. Without it, Compose
 * stretches each 26dp person row's touch area toward 48dp, and a tap on the
 * title just above the first row opened that person instead.
 */
private fun Modifier.claimTouches(): Modifier = pointerInput(Unit) {
    awaitEachGesture { awaitFirstDown(requireUnconsumed = false) }
}

// Under the tile long-press (430ms+), so holding still enters edit mode.
private const val PERSON_TAP_MAX_MS = 400L

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

/** More rows/lines fit on a taller tile; a 1-row tile still gets its title
 * plus one entry. */
private fun linesFor(size: TileSize): Int = (size.rows * 2).coerceIn(1, 6)

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
    // cycling-message face — see WHATS_NEW_FLIP_MS's own doc for why this is
    // a much longer dwell than the item-cycling interval below.
    var flipped by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active) {
            flipped = false
            return@LaunchedEffect
        }
        while (true) {
            delay(WHATS_NEW_FLIP_MS)
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
            delay(WHATS_NEW_ITEM_CYCLE_MS)
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

    // Real sender/picture images (user-requested: shown "like as we show in
    // other live tiles") — same per-notification-key lookup, falling back to
    // the per-package image, that ConversationTileFace itself uses.
    val itemImages by NotificationCenter.itemImages.collectAsStateWithLifecycle()
    val fallbackImages by NotificationCenter.images.collectAsStateWithLifecycle()
    val imgs = itemImages[current.notificationKey] ?: fallbackImages[current.packageName]

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
                    avatar = imgs?.avatar?.asImageBitmap(),
                    picture = imgs?.picture?.asImageBitmap(),
                    size = size,
                )
            },
        )
        PageIconCorner("bell")
    }
}

/**
 * The "apps" page pinned to Start: the front face shows the chat, messages and
 * mail apps and the back face the social apps (user-requested), each sorted by
 * most used (with usage access), then most pending notifications, and badged
 * with its count. One icon per
 * grid cell of the tile (4 on medium, 8 on wide, 9 on large). Tapping an icon
 * opens that app's home screen, not the latest message (user-requested); a tap
 * anywhere else opens the People Hub's apps page. Flips on the same slow dwell
 * as the "what's new" tile, and doesn't flip when one face would be empty.
 */
@Composable
private fun PeopleAppsTileFace(size: TileSize, active: Boolean, fallback: @Composable () -> Unit, modifier: Modifier) {
    val installed = rememberInstalledPeopleApps() ?: return
    val snapshot by NotificationCenter.snapshot.collectAsStateWithLifecycle()
    val opens = rememberAppOpenCounts()
    val apps = remember(installed, snapshot, opens) { peopleApps(installed, snapshot.badges, opens).distinctBy { it.packageName } }
    val (inbox, social) = remember(apps) { apps.partition { it.category != PeopleCategory.SOCIAL } }
    if (apps.isEmpty()) return fallback()

    val canFlip = inbox.isNotEmpty() && social.isNotEmpty()
    var flipped by remember { mutableStateOf(false) }
    LaunchedEffect(active, canFlip) {
        if (!active || !canFlip) {
            flipped = false
            return@LaunchedEffect
        }
        while (true) {
            delay(WHATS_NEW_FLIP_MS)
            flipped = !flipped
        }
    }
    val frontApps = inbox.ifEmpty { social }
    val frontLabel = if (inbox.isEmpty()) "social" else "inbox"
    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { PeopleAppIconGrid(frontApps, frontLabel, size) },
        back = { PeopleAppIconGrid(social, "social", size) },
    )
}

@Composable
private fun PeopleAppIconGrid(apps: List<PeopleApp>, label: String, size: TileSize) {
    val context = LocalContext.current
    val color = LocalTileFaceColor.current
    val columns = size.cols.coerceAtLeast(1)
    val rows = size.rows.coerceAtLeast(1)
    val showLabel = rows >= 2
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = if (showLabel) 18.dp else 6.dp),
        ) {
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
                            PeopleAppIcon(app, color)
                        }
                    }
                    repeat(columns - rowApps.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (showLabel) {
            Text(
                label,
                color = color,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun PeopleAppIcon(app: PeopleApp, color: Color) {
    val icon = rememberMonochromeAppIcon(app.packageName, sizePx = iconPx(28.dp))
    Box(modifier = Modifier.size(34.dp)) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = app.label,
                colorFilter = ColorFilter.tint(color),
                modifier = Modifier.size(28.dp).align(Alignment.Center),
            )
        }
        if (app.badge > 0) {
            // The tile face is the glyph colour on the tile's own fill, so the badge
            // is a dark circle ringed in that colour (WP style, like Start's badge).
            OutlinedCountBadge(
                count = app.badge,
                ring = color,
                fill = Color(0xFF111111),
                modifier = Modifier.align(Alignment.TopEnd),
                diameter = 16.dp,
            )
        }
    }
}
