package com.tileshell.feature.start

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.AppEntry
import com.tileshell.core.data.AppLauncher
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens
import com.tileshell.feature.applist.AppListFilter
import com.tileshell.feature.livetiles.ContactMatch
import com.tileshell.feature.livetiles.colorFor
import com.tileshell.feature.livetiles.contactLookupUri
import com.tileshell.feature.livetiles.rememberAppIconBitmap
import com.tileshell.feature.livetiles.rememberTileBitmap
import com.tileshell.feature.livetiles.searchContacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val RESULT_LIMIT = 5
private const val CONTACTS_DEBOUNCE_MS = 150L

/**
 * Quick search: a two-finger swipe-down on Start opens this, showing apps,
 * contacts, and a web-search fallback as the user types — the launcher-native
 * equivalent of a search button, reachable without a dedicated tile. Slides
 * down from the top edge (matching the gesture that opens it) rather than the
 * bottom-up sheets used elsewhere.
 */
@Composable
fun QuickSearchOverlay(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    apps: List<AppEntry>,
    contactsGranted: Boolean,
    onRequestContacts: () -> Unit,
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

    val trimmed = query.trim()
    val appMatches = remember(apps, trimmed) {
        if (trimmed.isEmpty()) emptyList() else AppListFilter.filter(apps, trimmed).take(RESULT_LIMIT)
    }
    val contactMatches by produceState<List<ContactMatch>>(emptyList(), trimmed, contactsGranted) {
        if (trimmed.isEmpty() || !contactsGranted) {
            value = emptyList()
        } else {
            delay(CONTACTS_DEBOUNCE_MS)
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
                            onSearch = { launchWebSearch(context, trimmed); dismiss() },
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

            if (trimmed.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (appMatches.isNotEmpty()) {
                        item { SearchSectionHeader("apps", accent) }
                        items(appMatches, key = { "app/${it.key}" }) { app ->
                            AppResultRow(app, tokens) {
                                AppLauncher.launch(context, app.packageName, app.activityName)
                                dismiss()
                            }
                        }
                    }
                    if (contactsGranted) {
                        if (contactMatches.isNotEmpty()) {
                            item { SearchSectionHeader("contacts", accent) }
                            items(contactMatches, key = { "contact/${it.contactId}" }) { person ->
                                ContactResultRow(person, tokens) {
                                    val uri = contactLookupUri(person.contactId, person.lookupKey)
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                    dismiss()
                                }
                            }
                        }
                    } else {
                        item { RequestContactsRow(tokens, onRequestContacts) }
                    }
                    item { SearchSectionHeader("web", accent) }
                    item {
                        WebSearchRow(trimmed, tokens) {
                            launchWebSearch(context, trimmed)
                            dismiss()
                        }
                    }
                }
            }
        }
    }
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
private fun ContactResultRow(person: ContactMatch, tokens: ColorTokens, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
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
private fun RequestContactsRow(tokens: ColorTokens, onRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRequest)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "allow contacts access to search contacts",
            color = tokens.fgDim,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
