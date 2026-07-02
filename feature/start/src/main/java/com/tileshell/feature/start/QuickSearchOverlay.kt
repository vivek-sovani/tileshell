package com.tileshell.feature.start

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.data.AppEntry
import com.tileshell.core.data.AppLauncher
import com.tileshell.core.data.RecentApps
import com.tileshell.core.data.RecentSearches
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens
import com.tileshell.feature.applist.AppListFilter
import com.tileshell.feature.livetiles.ContactMatch
import com.tileshell.feature.livetiles.colorFor
import com.tileshell.feature.livetiles.contactLookupUri
import com.tileshell.feature.livetiles.primaryPhoneNumber
import com.tileshell.feature.livetiles.rememberAppIconBitmap
import com.tileshell.feature.livetiles.rememberTileBitmap
import com.tileshell.feature.livetiles.searchContacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val RESULT_LIMIT = 5
private const val QUERY_DEBOUNCE_MS = 150L

/**
 * Quick search: a two-finger swipe-down on Start opens this, showing apps,
 * contacts, and a web-search fallback as the user types — the launcher-native
 * equivalent of a search button, reachable without a dedicated tile. Slides
 * down from the top edge (matching the gesture that opens it) rather than the
 * bottom-up sheets used elsewhere. Before anything is typed it instead shows
 * recent searches and frequently-launched apps.
 */
@Composable
fun QuickSearchOverlay(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    apps: List<AppEntry>,
    contactsGranted: Boolean,
    onRequestContacts: () -> Unit,
    onPinContact: (contactId: Long, lookupKey: String, name: String) -> Unit,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "quickSearchProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var query by remember { mutableStateOf("") }

    // The overlay is never disposed while translating off-screen (it's a plain
    // `visible` gate, not an AnimatedVisibility), so reset for the next open and
    // grab focus/keyboard for this one — same convention as the app list's search
    // box (AppListScreen `LaunchedEffect(visible)`).
    LaunchedEffect(visible) {
        if (visible) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            query = ""
        }
    }

    fun dismiss() {
        query = ""
        onDismiss()
    }

    // Any action taken on a typed query is worth remembering as a suggestion for
    // next time; a plain scrim-tap/back-press cancel (calling [dismiss] directly)
    // is not.
    fun act(action: () -> Unit) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) RecentSearches.record(context, trimmed)
        action()
        dismiss()
    }

    val trimmed = query.trim()
    val appMatches = remember(apps, trimmed) {
        if (trimmed.isEmpty()) emptyList() else AppListFilter.filter(apps, trimmed).take(RESULT_LIMIT)
    }
    val contactMatches by produceState<List<ContactMatch>>(emptyList(), trimmed, contactsGranted) {
        if (trimmed.isEmpty() || !contactsGranted) {
            value = emptyList()
        } else {
            delay(QUERY_DEBOUNCE_MS)
            value = withContext(Dispatchers.IO) { searchContacts(context, trimmed, RESULT_LIMIT) }
        }
    }
    BackHandler(enabled = visible) { dismiss() }

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dismiss() },
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = -size.height * (1f - progress) }
                .background(tokens.sheet)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .statusBarsPadding()
                .padding(top = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(tokens.chip, shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(TileIcons["search"], null, tint = tokens.fgDim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("search apps, contacts & web", color = tokens.fgDim, fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = tokens.fg, fontSize = 15.sp),
                        cursorBrush = SolidColor(tokens.fg),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { act { launchWebSearch(context, trimmed) } },
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        TileIcons["close"], null, tint = tokens.fgDim,
                        modifier = Modifier.size(18.dp).clickable { query = "" },
                    )
                }
            }

            if (trimmed.isEmpty()) {
                val recentSearches by RecentSearches.recent(context).collectAsStateWithLifecycle(initialValue = emptyList())
                val recentAppKeys by RecentApps.recent(context).collectAsStateWithLifecycle(initialValue = emptyList())
                val suggestedApps = remember(apps, recentAppKeys) {
                    AppListFilter.topApps(apps, recentAppKeys, System.currentTimeMillis()).take(RESULT_LIMIT)
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (recentSearches.isNotEmpty()) {
                        item { SearchSectionHeader("recent searches", accent) }
                        items(recentSearches, key = { "recent-search/$it" }) { q ->
                            RecentSearchRow(
                                query = q,
                                tokens = tokens,
                                onTap = { query = q },
                                onRemove = { RecentSearches.remove(context, q) },
                            )
                        }
                    }
                    if (suggestedApps.isNotEmpty()) {
                        item { SearchSectionHeader("suggested", accent) }
                        items(suggestedApps, key = { "suggested/${it.key}" }) { app ->
                            AppResultRow(app, tokens) {
                                AppLauncher.launch(context, app.packageName, app.activityName)
                                dismiss()
                            }
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (appMatches.isNotEmpty()) {
                        item { SearchSectionHeader("apps", accent) }
                        items(appMatches, key = { "app/${it.key}" }) { app ->
                            AppResultRow(app, tokens) {
                                act { AppLauncher.launch(context, app.packageName, app.activityName) }
                            }
                        }
                    }
                    if (contactsGranted) {
                        if (contactMatches.isNotEmpty()) {
                            item { SearchSectionHeader("contacts", accent) }
                            items(contactMatches, key = { "contact/${it.contactId}" }) { person ->
                                ContactResultRow(
                                    person = person,
                                    tokens = tokens,
                                    onOpenCard = {
                                        act {
                                            val uri = contactLookupUri(person.contactId, person.lookupKey)
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                        }
                                    },
                                    onCall = { number -> act { callContact(context, number) } },
                                    onMessage = { number -> act { messageContact(context, number) } },
                                    onPin = { act { onPinContact(person.contactId, person.lookupKey, person.name) } },
                                )
                            }
                        }
                    } else {
                        item { RequestRow(tokens, "allow contacts access to search contacts", onRequestContacts) }
                    }
                    item { SearchSectionHeader("web", accent) }
                    item {
                        WebSearchRow(trimmed, tokens) {
                            act { launchWebSearch(context, trimmed) }
                        }
                    }
                }
            }
        }
    }
}

private fun callContact(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
    runCatching { context.startActivity(intent) }
}

private fun messageContact(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}"))
    runCatching { context.startActivity(intent) }
}

@Composable
private fun SearchSectionHeader(text: String, accent: Color) {
    Text(
        text = text,
        color = accent,
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraLight,
        modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun AppResultRow(app: AppEntry, tokens: ColorTokens, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = rememberAppIconBitmap(app.packageName)
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(32.dp),
            )
        } else {
            Icon(TileIcons["app"], null, tint = tokens.fg, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(app.label, color = tokens.fg, fontSize = 16.sp)
    }
}

@Composable
private fun RecentSearchRow(query: String, tokens: ColorTokens, onTap: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(TileIcons["search"], null, tint = tokens.fgDim, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(14.dp))
            Text(query, color = tokens.fg, fontSize = 15.sp)
        }
        Icon(
            TileIcons["close"], null, tint = tokens.fgDim,
            modifier = Modifier.size(16.dp).clickable(onClick = onRemove),
        )
    }
}

/**
 * A contact match: tap opens the contact card; long-press opens a menu with
 * call/message (only when a number resolves) and "pin to start" — mirrors
 * `AppListScreen`'s `AppRow` long-press menu convention.
 */
@Composable
private fun ContactResultRow(
    person: ContactMatch,
    tokens: ColorTokens,
    onOpenCard: () -> Unit,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onPin: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var phone by remember(person.contactId) { mutableStateOf<String?>(null) }
    LaunchedEffect(menuOpen, person.contactId) {
        if (menuOpen) phone = withContext(Dispatchers.IO) { primaryPhoneNumber(context, person.contactId) }
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tapOrLongPress(onTap = onOpenCard, onLongPress = { menuOpen = true })
                .padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val photo = person.photoUri?.let { rememberTileBitmap(it, targetPx = 96) }
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (photo != null) {
                    Image(
                        bitmap = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(colorFor(person.name)))
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(person.name, color = tokens.fg, fontSize = 16.sp)
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            val number = phone
            if (number != null) {
                DropdownMenuItem(text = { Text("call") }, onClick = { menuOpen = false; onCall(number) })
                DropdownMenuItem(text = { Text("message") }, onClick = { menuOpen = false; onMessage(number) })
            }
            DropdownMenuItem(text = { Text("pin to start") }, onClick = { menuOpen = false; onPin() })
        }
    }
}

@Composable
private fun WebSearchRow(query: String, tokens: ColorTokens, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(TileIcons["search"], null, tint = tokens.fg, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Text("search the web for “$query”", color = tokens.fg, fontSize = 16.sp)
    }
}

@Composable
private fun RequestRow(tokens: ColorTokens, text: String, onRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRequest)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

/**
 * Tap vs. long-press (450 ms, matches the app list's pin gesture). Mirrors
 * `AppListScreen`'s private helper of the same name (a different module, so
 * duplicated rather than shared).
 */
private fun Modifier.tapOrLongPress(onTap: () -> Unit, onLongPress: () -> Unit): Modifier =
    pointerInput(onTap, onLongPress) {
        val slop = 7.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // true = released early (tap), false = moved past slop (cancel),
            // null = 450 ms elapsed still pressed (long-press fired).
            val outcome = withTimeoutOrNull(450L) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) return@withTimeoutOrNull true
                    if ((change.position - down.position).getDistance() > slop) {
                        return@withTimeoutOrNull false
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                true
            }
            when (outcome) {
                true -> onTap()
                false -> Unit
                null -> onLongPress()
            }
        }
    }
