package com.tileshell.feature.livetiles

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val HUB_PIVOTS = listOf("all", "what's new", "recent")
private val JUMP_LETTERS = listOf("#") + ('A'..'Z').map { it.toString() }

/**
 * Full-screen people hub — matches the three approved mockups: an "all" page
 * (a "frequent" strip of avatars, then the full contact list grouped
 * alphabetically with a letter jump grid), a "what's new" page (recent
 * messaging/social notifications for people), and a "recent" page (most
 * recently contacted). Same WP Panorama/Pivot shell as
 * [CalendarHubScreen]/`MusicHubScreen` — captioned title, pivot row,
 * `HubAppBar`.
 *
 * Per direct user instruction, tapping any contact never opens a custom
 * profile page in here, and add/edit are left to the device's own Contacts
 * app too — this hub hands off to it ([openContactCard]/[openAddContact]),
 * which already owns that data and its own call/message/email/address
 * actions.
 *
 * "what's new" and "recent" can each be pinned to Start as their own tile
 * (user-requested: "pin facility for whats new and recents... this will pin
 * recent and whats new as tile" / "i want to pin the full page not a
 * particular line" / "the full tab") — [onPinPage] creates that tile;
 * [initialPage] is how such a tile reopens straight to its own page, same
 * mechanism as `MusicHubScreen`'s own `initialPage`.
 */
@Composable
fun PeopleHubScreen(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
    initialPage: String? = null,
    onPinPage: (page: String, label: String) -> Unit = { _, _ -> },
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "peopleHubProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current
    val granted = rememberPermissionGranted(Manifest.permission.READ_CONTACTS)

    BackHandler(enabled = visible) { onDismiss() }

    val pagerState = rememberPagerState(pageCount = { HUB_PIVOTS.size })
    val pagerScope = rememberCoroutineScope()
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var whatsNewFilter by remember { mutableStateOf(loadWhatsNewFilter(context)) }
    val snapshot by NotificationCenter.snapshot.collectAsStateWithLifecycle()

    LaunchedEffect(visible, initialPage) {
        if (visible && initialPage != null) {
            val index = HUB_PIVOTS.indexOf(initialPage)
            if (index >= 0) pagerState.scrollToPage(index)
        }
    }

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.bg)
                // Swallows every tap on this screen — see WeatherHubScreen's
                // own doc comment for the real bug this fixes elsewhere in
                // the hub family (a tap falling through to a Start tile
                // underneath).
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(20.dp))
                Text(text = "tileshell", color = tokens.fgDim, fontSize = 14.sp)
                Text(
                    text = "people",
                    color = accent,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    HUB_PIVOTS.forEachIndexed { index, label ->
                        val selected = pagerState.currentPage == index
                        Text(
                            text = label,
                            color = if (selected) tokens.fg else tokens.fgDim,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { pagerScope.launch { pagerState.animateScrollToPage(index) } },
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (searchOpen) {
                    PeopleSearchField(tokens, accent, query, { query = it }) {
                        searchOpen = false
                        query = ""
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (!granted) {
                PeoplePermissionGate(tokens, accent)
            } else {
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    when (page) {
                        0 -> AllPeoplePage(context, tokens, accent, query)
                        1 -> WhatsNewPage(context, tokens, accent, whatsNewFilter) {
                            whatsNewFilter = it
                            saveWhatsNewFilter(context, it)
                        }
                        else -> RecentPeoplePage(context, tokens, accent)
                    }
                }
            }

            HubAppBar(
                tokens = tokens,
                actions = when (pagerState.currentPage) {
                    0 -> listOf(
                        HubAppBarAction("back", "back", onDismiss),
                        HubAppBarAction("plus", "add contact") { openAddContact(context) },
                        HubAppBarAction("search", "search") { searchOpen = !searchOpen },
                        HubAppBarAction("people", "open contacts app") { openContactsApp(context) },
                    )
                    1 -> buildList {
                        add(HubAppBarAction("back", "back", onDismiss))
                        add(HubAppBarAction("pin", "pin this page to start") { onPinPage("what's new", "what's new") })
                        // Clears just the notifications listed under the current filter.
                        add(
                            HubAppBarAction("check", "clear these") {
                                NotificationCenter.clearKeys(
                                    recentActivity(snapshot, category = whatsNewFilter)
                                        .map { it.notificationKey }
                                        .filter { it.isNotEmpty() },
                                )
                            },
                        )
                        if (whatsNewFilter == PeopleCategory.MAIL) {
                            add(HubAppBarAction("edit", "compose") { openMailCompose(context) })
                        } else {
                            add(HubAppBarAction("people", "open contacts app") { openContactsApp(context) })
                        }
                    }
                    else -> {
                        val page = HUB_PIVOTS[pagerState.currentPage]
                        listOf(
                            HubAppBarAction("back", "back", onDismiss),
                            HubAppBarAction("pin", "pin this page to start") { onPinPage(page, page) },
                            HubAppBarAction("people", "open contacts app") { openContactsApp(context) },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun PeopleSearchField(
    tokens: ColorTokens,
    accent: Color,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.fg.copy(alpha = 0.08f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(color = tokens.fg, fontSize = 15.sp),
            cursorBrush = SolidColor(accent),
            decorationBox = { inner ->
                if (query.isEmpty()) Text("search people", color = tokens.fgDim, fontSize = 15.sp)
                inner()
            },
        )
        Text(
            "cancel",
            color = accent,
            fontSize = 13.sp,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        )
    }
}

@Composable
private fun AllPeoplePage(context: android.content.Context, tokens: ColorTokens, accent: Color, query: String) {
    val frequent by produceState<List<PersonSummary>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { queryFrequentContacts(context) }
    }
    val all by produceState<List<PersonSummary>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { queryAllContacts(context) }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var jumpOpen by remember { mutableStateOf(false) }
    // Only one row's call/message/whatsapp/pin line is ever open at a time —
    // user-requested: "when i tap another first should collapse".
    var expandedId by remember { mutableStateOf<Long?>(null) }

    val trimmedQuery = query.trim()
    if (trimmedQuery.isNotEmpty()) {
        val matches = all?.filter { it.name.contains(trimmedQuery, ignoreCase = true) }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
            if (matches == null) {
                item { Spacer(Modifier.height(1.dp)) }
            } else if (matches.isEmpty()) {
                item {
                    Text("no matches", color = tokens.fgDim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 24.dp))
                }
            } else {
                items(matches, key = { "match-${it.contactId}" }) { person ->
                    ContactRow(
                        context, person, tokens, accent,
                        expanded = expandedId == person.contactId,
                        onToggleExpand = { expandedId = if (expandedId == person.contactId) null else person.contactId },
                    )
                }
            }
        }
        return
    }

    val sections = all?.let { groupContactsByLetter(it) }
    // One lazy item per header + one per contact row, in order; a leading
    // "frequent" block (header + its own row) counts as a single item when
    // present. Used to scroll straight to a section from the jump grid.
    val hasFrequentBlock = !frequent.isNullOrEmpty()
    val sectionItemStart = remember(sections, hasFrequentBlock) {
        val starts = mutableMapOf<String, Int>()
        var index = if (hasFrequentBlock) 1 else 0
        sections?.forEach { (letter, people) ->
            starts[letter] = index
            index += 1 + people.size
        }
        starts
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        ) {
            val freq = frequent
            if (!freq.isNullOrEmpty()) {
                item {
                    Text(
                        "frequent",
                        color = tokens.fgDim,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(freq, key = { "freq-${it.contactId}" }) { person ->
                            FrequentAvatar(person, tokens) { openContactCard(context, person.contactId, person.lookupKey) }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                }
            }

            if (sections == null) {
                item { Spacer(Modifier.height(1.dp)) }
            } else if (sections.isEmpty()) {
                item {
                    Text("no contacts to show", color = tokens.fgDim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 24.dp))
                }
            } else {
                sections.forEach { (letter, people) ->
                    item(key = "header-$letter") {
                        Text(
                            text = letter.lowercase(),
                            color = accent,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(top = 10.dp, bottom = 6.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { jumpOpen = true },
                                ),
                        )
                    }
                    items(people, key = { "row-${it.contactId}" }) { person ->
                        ContactRow(
                            context, person, tokens, accent,
                            expanded = expandedId == person.contactId,
                            onToggleExpand = { expandedId = if (expandedId == person.contactId) null else person.contactId },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        AnimatedVisibility(visible = jumpOpen, enter = fadeIn(), exit = fadeOut()) {
            PeopleJumpGrid(
                present = sectionItemStart.keys,
                accent = accent,
                tokens = tokens,
                onPick = { letter ->
                    jumpOpen = false
                    sectionItemStart[letter]?.let { target ->
                        scope.launch { listState.scrollToItem(target) }
                    }
                },
                onDismiss = { jumpOpen = false },
            )
        }
    }
}

/** Full-screen A-Z picker — same interaction as the app list's own jump
 * grid (tap a section header to open it, tap a letter to scroll there),
 * reimplemented here since `:feature:livetiles` can't depend on
 * `:feature:applist` (the dependency graph runs the other way). */
@Composable
private fun PeopleJumpGrid(
    present: Set<String>,
    accent: Color,
    tokens: ColorTokens,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val cols = 4
    val rows = (JUMP_LETTERS.size + cols - 1) / cols
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val gap = 8.dp
        val inset = 18.dp
        val availW = maxWidth - inset * 2
        val availH = maxHeight - inset * 2
        val cellW = (availW - gap * (cols - 1)) / cols
        val cellH = (availH - gap * (rows - 1)) / rows
        val cell = minOf(cellW, cellH).coerceAtLeast(0.dp)
        val fontSize = (cell.value * 0.42f).coerceIn(8f, 26f).sp
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            JUMP_LETTERS.chunked(cols).forEach { rowLetters ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowLetters.forEach { letter ->
                        val available = letter in present
                        Box(
                            modifier = Modifier
                                .size(cell)
                                .background(
                                    if (available) accent.copy(alpha = 0.18f) else Color.Transparent,
                                    androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                )
                                .clickable(
                                    enabled = available,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onPick(letter) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                letter.lowercase(),
                                color = if (available) accent else tokens.fgDim.copy(alpha = 0.4f),
                                fontSize = fontSize,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A contact's avatar: the real profile photo, cropped to a circle, or a
 * circular initials plate tinted by [colorFor] when there is none. Also
 * reused by [PeopleHubPageTileFace]'s "recent" live tile face. */
@Composable
internal fun ContactAvatar(person: PersonSummary, size: androidx.compose.ui.unit.Dp, fontSize: androidx.compose.ui.unit.TextUnit) {
    val bitmap = person.photoUri?.let { rememberTileBitmap(it, targetPx = (size.value * 2).toInt()) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (bitmap == null) colorFor(person.name) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(initialsFor(person.name), color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FrequentAvatar(person: PersonSummary, tokens: ColorTokens, onClick: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(64.dp)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                ),
        ) {
            ContactAvatar(person, size = 56.dp, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                person.name.lowercase(),
                color = tokens.fg,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PinContactMenu(menuOpen, person) { menuOpen = false }
    }
}

/**
 * A row in the "all"/"recent"/search-results list. Tapping the name reveals a
 * quick-action line below it — user-requested: "when i click on name.. then
 * show three options call, message and pin on the line below" — instead of
 * navigating away to the device's Contacts app (that hand-off is still what
 * [openContactCard] is for, just no longer this row's own tap target).
 * Call/message need the contact's phone number, resolved lazily off the main
 * thread only once a row is actually expanded. WhatsApp joins the row
 * whenever it's installed (also user-requested); Facebook Messenger does not
 * — see [whatsAppContact]'s own doc for why no equivalent deep link exists.
 */
@Composable
private fun ContactRow(
    context: android.content.Context,
    person: PersonSummary,
    tokens: ColorTokens,
    accent: Color,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    var phone by remember(person.contactId) { mutableStateOf<String?>(null) }
    LaunchedEffect(expanded, person.contactId) {
        if (expanded && phone == null) {
            phone = withContext(Dispatchers.IO) { primaryPhoneNumber(context, person.contactId) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleExpand,
                )
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactAvatar(person, size = 40.dp, fontSize = 13.sp)
            Spacer(Modifier.width(14.dp))
            Text(
                person.name.lowercase(),
                color = tokens.fg,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (expanded) {
            // Spread evenly across the row's full width — user-reported "not
            // proportionally placed and sized" when this was a fixed-gap row
            // indented to sit under the avatar, which left the 3-5 buttons
            // clumped on the left with a large empty gap on the right.
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                val number = phone
                if (number != null) {
                    ContactActionButton("phone", "call", accent, tokens) { callContact(context, number) }
                    ContactActionButton("messages", "message", accent, tokens) { messageContact(context, number) }
                    if (isWhatsAppInstalled(context)) {
                        ContactActionButton("whatsapp", "whatsapp", Color(0xFF25D366), tokens) { whatsAppContact(context, number) }
                    }
                }
                ContactActionButton("pin", "pin", accent, tokens) { PeopleHubNavigation.requestPin(person) }
                ContactActionButton("contacts", "view", accent, tokens) { openContactCard(context, person.contactId, person.lookupKey) }
            }
        }
    }
}

@Composable
private fun ContactActionButton(iconKey: String, label: String, tint: Color, tokens: ColorTokens, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                // Solid tonal fill (user picked this over a bordered
                // translucent-circle option and a bare-icon-no-plate option,
                // shown side by side after "icon display should be improved"
                // → "looks dated") — a bolder, more modern filled square
                // reads as a clearer button than a faint tinted outline ever
                // did, in both themes.
                .background(tint),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(TileIcons[iconKey], null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(4.dp))
        // The label's own colour is deliberately NOT the icon's tint (accent
        // or WhatsApp green) — user-reported: "second line of action
        // visibiity should be improved in light as well as in dark mode".
        // Some accents read poorly as small 11sp text against either theme's
        // background; the icon above still carries the colour identity, so
        // the label uses the guaranteed-readable theme foreground instead.
        Text(label, color = tokens.fg, fontSize = 11.sp)
    }
}

/** The long-press "pin to start" menu shared by every contact row/avatar —
 * mirrors `QuickSearchOverlay`'s own contact long-press menu exactly, minus
 * call/message (this hub hands those off to the device's Contacts app, per
 * direct user instruction, so only pinning needs its own affordance here). */
@Composable
private fun PinContactMenu(expanded: Boolean, person: PersonSummary, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("pin to start") },
            onClick = {
                onDismiss()
                PeopleHubNavigation.requestPin(person)
            },
        )
    }
}

@Composable
private fun RecentPeoplePage(context: android.content.Context, tokens: ColorTokens, accent: Color) {
    val recent by produceState<List<PersonSummary>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { queryRecentContacts(context) }
    }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
    ) {
        val list = recent
        if (list == null) {
            item { Spacer(Modifier.height(1.dp)) }
        } else if (list.isEmpty()) {
            item {
                Column(modifier = Modifier.padding(vertical = 24.dp)) {
                    Text("no recently contacted people", color = tokens.fgDim, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    // User-reported: made real calls, "recent" still stayed
                    // empty. Root cause: this reads Android's own
                    // LAST_TIME_CONTACTED field, which most phones (Samsung's
                    // own dialer included) stopped updating automatically
                    // since Android 9 — not something this app can detect or
                    // work around without the Call Log permission, which
                    // carries real Play Store rejection risk for an app
                    // that isn't the default phone app (declined per direct
                    // discussion, kept as a known limitation instead).
                    Text(
                        "some phones don't keep track of this for calls made through their own dialer",
                        color = tokens.fgDim,
                        fontSize = 12.sp,
                    )
                }
            }
        } else {
            items(list, key = { "recent-${it.contactId}" }) { person ->
                ContactRow(
                    context, person, tokens, accent,
                    expanded = expandedId == person.contactId,
                    onToggleExpand = { expandedId = if (expandedId == person.contactId) null else person.contactId },
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * "what's new" — recent notifications from people, reusing [NotificationCenter]
 * (already tracked for badges/mail-and-messages tile faces) rather than a
 * parallel data source. Gated on notification-listener access, a separate
 * opt-in from READ_CONTACTS (the same permission the "notifications" row in
 * Personalize already asks for).
 *
 * Filter chips narrow it to chat / messages / mail / social ([filter], hoisted
 * so the app bar's "compose" can follow it). Tapping a row expands it in place
 * (only one at a time) to reply inline, mark read or archive — whichever
 * buttons the posting app put on its notification — or open the app; tapping
 * the row again collapses it.
 */
@Composable
private fun WhatsNewPage(
    context: android.content.Context,
    tokens: ColorTokens,
    accent: Color,
    filter: PeopleCategory?,
    onFilterChange: (PeopleCategory?) -> Unit,
) {
    val granted = rememberNotificationAccess()
    if (!granted) {
        NotificationAccessGate(tokens, accent)
        return
    }
    val snapshot by NotificationCenter.snapshot.collectAsStateWithLifecycle()
    val entries = remember(snapshot, filter) { recentActivity(snapshot, category = filter) }
    val counts = remember(snapshot) { whatsNewCounts(snapshot) }
    val hidden = remember(snapshot, filter) {
        if (filter == PeopleCategory.MAIL) hiddenActivityCounts(snapshot, PeopleCategory.MAIL) else emptyMap()
    }
    var expandedKey by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        WhatsNewFilterChips(tokens, accent, filter, counts) {
            expandedKey = null
            onFilterChange(it)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        ) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        if (filter == null) "nothing new right now" else "nothing new in ${filter.label}",
                        color = tokens.fgDim,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(entries, key = { "activity-${it.notificationKey.ifBlank { it.packageName + it.postTime }}" }) { entry ->
                    val key = entry.notificationKey
                    ActivityRow(
                        entry = entry,
                        tokens = tokens,
                        accent = accent,
                        expanded = key.isNotEmpty() && expandedKey == key,
                        onToggle = { expandedKey = if (expandedKey == key || key.isEmpty()) null else key },
                        onOpenApp = {
                            expandedKey = null
                            NotificationCenter.reportDisplayedKey(entry.packageName, key)
                            if (!NotificationCenter.openAndClear(context, entry.packageName)) {
                                openApp(context, entry.packageName)
                            }
                        },
                        onDone = { expandedKey = null },
                    )
                }
            }
            hidden.forEach { (packageName, count) ->
                item(key = "more-$packageName") {
                    val label = remember(packageName) { appLabelOrNull(context, packageName) ?: "the app" }.lowercase()
                    Text(
                        text = "$count more in $label · open $label ›",
                        color = tokens.fgDim,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { openApp(context, packageName) },
                            )
                            .padding(start = 58.dp, top = 10.dp, bottom = 10.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** The all / chat / messages / mail / social chip row, each with its pending count. */
@Composable
private fun WhatsNewFilterChips(
    tokens: ColorTokens,
    accent: Color,
    selected: PeopleCategory?,
    counts: Map<PeopleCategory?, Int>,
    onSelect: (PeopleCategory?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        items(WHATS_NEW_FILTERS, key = { it?.name ?: "all" }) { category ->
            val on = category == selected
            val count = counts[category] ?: 0
            val label = (category?.label ?: "all") + if (count > 0) " $count" else ""
            Text(
                text = label,
                color = if (on) Color.White else tokens.fg,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) accent else tokens.fg.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(category) },
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ActivityRow(
    entry: ActivityEntry,
    tokens: ColorTokens,
    accent: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenApp: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    // Real sender/message photo when the notification carried one (user-
    // requested: "in hub also the same thing photo of sender") — same
    // per-notification-key lookup, falling back to the per-package image,
    // that the pinned Start tile's own face uses.
    val itemImages by NotificationCenter.itemImages.collectAsStateWithLifecycle()
    val fallbackImages by NotificationCenter.images.collectAsStateWithLifecycle()
    val avatarBitmap = (itemImages[entry.notificationKey] ?: fallbackImages[entry.packageName])
        ?.avatar?.asImageBitmap()
    val appLabel = remember(entry.packageName) {
        appLabelOrNull(context, entry.packageName)?.lowercase() ?: "app"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (expanded) tokens.fg.copy(alpha = 0.06f) else Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                )
                .padding(vertical = 9.dp, horizontal = if (expanded) 8.dp else 0.dp),
            verticalAlignment = if (expanded) Alignment.Top else Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(44.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (avatarBitmap == null) colorFor(entry.sender) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(initialsFor(entry.sender), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                val appIcon = rememberAppIconBitmap(entry.packageName, sizePx = 64)
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.sender.lowercase(), color = tokens.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    entry.snippet,
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                    maxLines = if (expanded) 4 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(activityAgo(entry.postTime), color = tokens.fgDim, fontSize = 11.sp)
        }

        if (expanded) {
            Column(modifier = Modifier.padding(start = 66.dp, end = 10.dp, bottom = 10.dp)) {
                if (QuickAction.REPLY in entry.quickActions) {
                    InlineReplyField(tokens, accent, entry.sender) { text ->
                        val sent = NotificationCenter.performQuickAction(context, entry.notificationKey, QuickAction.REPLY, text)
                        if (sent) {
                            showToast(context, "sent")
                            onDone()
                        } else {
                            showToast(context, "couldn't reply here, opening $appLabel")
                            onOpenApp()
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (QuickAction.MARK_READ in entry.quickActions) {
                        RowAction("mark read", accent) {
                            pressOrFallBack(context, entry, QuickAction.MARK_READ, appLabel, onDone, onOpenApp)
                        }
                    }
                    if (QuickAction.ARCHIVE in entry.quickActions) {
                        RowAction("archive", accent) {
                            pressOrFallBack(context, entry, QuickAction.ARCHIVE, appLabel, onDone, onOpenApp)
                        }
                    }
                    RowAction("open $appLabel", accent, onOpenApp)
                }
            }
        }
    }
}

/** Presses [action] on [entry]'s notification; if the app no longer offers it,
 * opens the app instead so the tap still does something. */
private fun pressOrFallBack(
    context: android.content.Context,
    entry: ActivityEntry,
    action: QuickAction,
    appLabel: String,
    onDone: () -> Unit,
    onOpenApp: () -> Unit,
) {
    if (NotificationCenter.performQuickAction(context, entry.notificationKey, action)) {
        onDone()
    } else {
        showToast(context, "couldn't do that here, opening $appLabel")
        onOpenApp()
    }
}

@Composable
private fun InlineReplyField(tokens: ColorTokens, accent: Color, sender: String, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(color = tokens.fg, fontSize = 14.sp),
            cursorBrush = SolidColor(accent),
            maxLines = 4,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(tokens.fg.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text("reply to ${sender.lowercase()}", color = tokens.fgDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                inner()
            },
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "send",
            color = if (text.isBlank()) tokens.fgDim else accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = text.isNotBlank(),
                onClick = { onSend(text.trim()) },
            ),
        )
    }
}

@Composable
private fun RowAction(label: String, accent: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
    )
}

private fun showToast(context: android.content.Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}

private const val PEOPLE_PREFS = "tileshell.prefs"
private const val WHATS_NEW_FILTER_KEY = "people_whats_new_filter"

/** The filter chip last picked on "what's new", remembered across hub opens. */
private fun loadWhatsNewFilter(context: android.content.Context): PeopleCategory? = runCatching {
    context.getSharedPreferences(PEOPLE_PREFS, android.content.Context.MODE_PRIVATE)
        .getString(WHATS_NEW_FILTER_KEY, null)
        ?.let { PeopleCategory.valueOf(it) }
        ?.takeIf { it in WHATS_NEW_FILTERS }
}.getOrNull()

private fun saveWhatsNewFilter(context: android.content.Context, filter: PeopleCategory?) {
    runCatching {
        context.getSharedPreferences(PEOPLE_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(WHATS_NEW_FILTER_KEY, filter?.name).apply()
    }
}

/** Opens the default mail app's compose screen (the mail filter's "compose"). */
private fun openMailCompose(context: android.content.Context) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:"))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { showToast(context, "no mail app found") }
}

@Composable
private fun NotificationAccessGate(tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("see what's new", color = tokens.fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            "shows recent chats, messages, mail and social notifications from people. stays on your device — nothing is sent anywhere.",
            color = tokens.fgDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "allow notification access",
            color = accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { runCatching { context.startActivity(NotificationAccess.settingsIntent()) } },
            ),
        )
    }
}

@Composable
private fun PeoplePermissionGate(tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    var blocked by remember { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (!isGranted && !canShowSystemPermissionDialog(context, Manifest.permission.READ_CONTACTS, asked = true)) {
            blocked = true
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("see your people", color = tokens.fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            "shows your contacts, grouped and searchable. stays on your device — nothing is sent anywhere.",
            color = tokens.fgDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (blocked) "open settings to allow" else "allow contacts access",
            color = accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (blocked) openAppPermissionSettings(context) else requestPermission.launch(Manifest.permission.READ_CONTACTS)
                },
            ),
        )
    }
}
