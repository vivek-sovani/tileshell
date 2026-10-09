package com.tileshell.feature.livetiles.shopping

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.HubAppBar
import com.tileshell.core.design.HubAppBarAction
import com.tileshell.core.design.HubFilter
import com.tileshell.core.design.HubPanorama
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens
import com.tileshell.feature.livetiles.HubAppsPage
import com.tileshell.feature.livetiles.RowAction
import com.tileshell.feature.livetiles.SwipeToDismissRow
import com.tileshell.feature.livetiles.HubKind
import com.tileshell.feature.livetiles.HubPageApp
import com.tileshell.feature.livetiles.HubPinNote
import com.tileshell.feature.livetiles.NotificationAccess
import com.tileshell.feature.livetiles.money.MoneyLock
import com.tileshell.feature.livetiles.openApp
import com.tileshell.feature.livetiles.rememberNotificationAccess
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val SHOPPING_PIVOTS = listOf("arriving", "deals", "past", "apps")
private const val APPS_PAGE = 3
private val ShopBlue = Color(0xFF2B78E4)
private val FoodOrange = Color(0xFFE5641E)
private val Closed = Color(0xFFE5645A)

/**
 * The shopping hub: "arriving" (orders on their way, one card each with its progress line, arrival time and delivery
 * OTP), "past" (delivered, cancelled and returned orders, 90 days) and "apps" (your shopping, food and courier apps;
 * edit to add, move or take off). Orders are read from new notifications as they arrive; delivery OTPs stay hidden
 * until the fingerprint, face or screen lock is used (when that is set). The app bar holds back, settings (or edit
 * apps on the apps page) and pin.
 */
@Composable
fun ShoppingHubScreen(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    onPinHub: () -> Unit,
    pinMessages: kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.emptyFlow(),
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "shoppingHubProgress",
    )
    // Shown OTPs hide again once the hub has fully closed.
    var revealed by remember { mutableStateOf(setOf<String>()) }
    if (!visible && progress == 0f) {
        LaunchedEffect(Unit) { revealed = emptySet() }
        return
    }

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current
    LaunchedEffect(Unit) { ShoppingStore.ensureLoaded(context) }
    val settings by ShoppingPrefs.settings(context).collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = visible) { if (settingsOpen) settingsOpen = false else onDismiss() }

    val pagerState = rememberPagerState(pageCount = { SHOPPING_PIVOTS.size })
    var appsEditing by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) { if (pagerState.currentPage != APPS_PAGE) appsEditing = false }

    // Showing a delivery OTP asks for the fingerprint, face or screen lock first (when that is set).
    var pendingKey by remember { mutableStateOf<String?>(null) }
    val credentialLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) pendingKey?.let { revealed = revealed + it }
        pendingKey = null
    }
    val reveal: (String) -> Unit = { key ->
        val locked = (settings?.lockOtps ?: true) && MoneyLock.isDeviceSecure(context)
        when {
            !locked -> revealed = revealed + key
            MoneyLock.usesPrompt() -> MoneyLock.prompt(context, onSuccess = { revealed = revealed + key }, onFail = {})
            else -> {
                pendingKey = key
                MoneyLock.confirmIntent(context)?.let { credentialLauncher.launch(it) } ?: run { revealed = revealed + key; pendingKey = null }
            }
        }
    }

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            if (settingsOpen) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "SHOPPING",
                        color = tokens.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 18.dp, top = 18.dp),
                    )
                    Text("settings", color = tokens.fg, fontSize = 56.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                    ShoppingSettingsPage(settings ?: ShoppingSettings(), tokens, accent)
                }
            } else {
                HubPanorama(
                    title = "shopping",
                    sections = SHOPPING_PIVOTS,
                    pagerState = pagerState,
                    tokens = tokens,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    when (page) {
                        0 -> ArrivingPage(tokens, accent, settings?.readOrderMessages ?: true, revealed, reveal)
                        1 -> DealsPage(tokens, accent, settings?.readOrderMessages ?: true)
                        2 -> PastPage(tokens, accent)
                        else -> ShoppingAppsPage(tokens, accent, appsEditing)
                    }
                }
            }
            HubPinNote(pinMessages, tokens, accent)
            HubAppBar(
                tokens = tokens,
                actions = buildList {
                    add(HubAppBarAction("back", "back") { if (settingsOpen) settingsOpen = false else onDismiss() })
                    if (!settingsOpen && pagerState.currentPage == APPS_PAGE) {
                        add(HubAppBarAction(if (appsEditing) "check" else "edit", "edit apps", if (appsEditing) "done" else "edit apps") { appsEditing = !appsEditing })
                    } else {
                        add(HubAppBarAction("settings", "shopping settings", "settings") { settingsOpen = !settingsOpen })
                    }
                    add(HubAppBarAction("pin", "pin shopping to start", "pin to start", onPinHub))
                },
            )
        }
    }
}

// --- arriving ---------------------------------------------------------------------

@Composable
private fun NoticeLine(text: String, accent: Color, onClick: (() -> Unit)? = null) {
    Text(
        text, color = accent, fontSize = 14.sp,
        modifier = Modifier.padding(vertical = 8.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

@Composable
private fun ArrivingPage(tokens: ColorTokens, accent: Color, reading: Boolean, revealed: Set<String>, onReveal: (String) -> Unit) {
    val context = LocalContext.current
    val orders by ShoppingStore.orders.collectAsStateWithLifecycle()
    val access = rememberNotificationAccess()
    val apps = com.tileshell.feature.livetiles.shopping.rememberShoppingApps().orEmpty()
    var filter by remember { mutableStateOf<String?>(null) }
    var expandedKey by remember { mutableStateOf<String?>(null) }
    val arriving = remember(orders) { arrivingOrders(orders) }
    val shown = remember(arriving, filter) {
        when (filter) {
            "shopping" -> arriving.filter { !it.food }
            "food" -> arriving.filter { it.food }
            else -> arriving
        }
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        if (!access) {
            item(key = "access") {
                NoticeLine("turn on notification access so tileshell can see order updates as they arrive ›", accent) {
                    runCatching { context.startActivity(NotificationAccess.settingsIntent()) }
                }
            }
        } else if (!reading) {
            item(key = "off") { NoticeLine("reading order messages is off. turn it on in settings.", accent) }
        }
        item(key = "filters") {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                listOf<Pair<String?, String>>(null to "all", "shopping" to "shopping", "food" to "food").forEach { (value, label) ->
                    HubFilter(label, filter == value, tokens, accent) { filter = value }
                }
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    "nothing on its way. orders appear here as their notifications arrive, from the moment this is on.",
                    color = tokens.fgDim, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        items(shown, key = { it.key }) { o ->
            OrderCard(
                o, apps, o.key in revealed, { onReveal(o.key) },
                expanded = expandedKey == o.key,
                onToggle = { expandedKey = if (expandedKey == o.key) null else o.key },
                onRemove = {
                    if (expandedKey == o.key) expandedKey = null
                    ShoppingStore.remove(context, o.key)
                },
                tokens = tokens,
                accent = accent,
            )
        }
    }
}

@Composable
private fun OrderCard(
    o: Order,
    apps: List<ShoppingApp>,
    otpShown: Boolean,
    onReveal: () -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    tokens: ColorTokens,
    accent: Color,
) {
    val context = LocalContext.current
    val bar = if (o.food) FoodOrange else ShopBlue
    val app = remember(apps, o.merchant) { appForMerchant(apps, o.merchant) }
    val open = { app?.let { openApp(context, it.packageName) }; Unit }
    // The message itself, when it says more than the item's name.
    val message = o.message.takeUnless { it.isBlank() || it.equals(o.title, ignoreCase = true) }.orEmpty()
    // Like a deal or a "what's new" row: tap opens the store's app, the arrow expands the whole message, a sideways
    // swipe (or "dismiss") removes the order.
    SwipeToDismissRow(tokens = tokens, onDismiss = onRemove) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(tokens.sheetLine))
            Column(modifier = Modifier.fillMaxWidth().background(if (expanded) tokens.fg.copy(alpha = 0.06f) else Color.Transparent)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .clickable(enabled = app != null, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { open() })
                        .padding(vertical = 10.dp, horizontal = if (expanded) 8.dp else 0.dp),
                ) {
                    Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(bar))
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(o.title, color = tokens.fg, fontSize = 17.sp, fontWeight = FontWeight.Light, maxLines = if (expanded) 8 else 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(o.merchant, o.status.label, o.eta).joinToString(" · "),
                            color = tokens.fgDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (expanded && message.isNotEmpty()) {
                            Text(message, color = tokens.fgDim, fontSize = 13.sp, maxLines = 30, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (step in 0..3) {
                                Box(modifier = Modifier.weight(1f).height(3.dp).background(if (o.status.step >= step) bar else tokens.sheetLine))
                            }
                        }
                        if (o.otp != null) {
                            Text(
                                if (otpShown) "delivery otp ${o.otp}" else "delivery otp ●●●●  tap to show",
                                color = tokens.fg, fontSize = 12.sp,
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .border(0.5.dp, tokens.fg)
                                    .clickable(enabled = !otpShown, onClick = onReveal)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            com.tileshell.core.design.TileIcons["chevron"],
                            contentDescription = if (expanded) "collapse" else "expand",
                            tint = tokens.fgDim,
                            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = if (expanded) -90f else 90f },
                        )
                    }
                }
                if (expanded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp)) {
                        if (app != null) RowAction("open in ${app.label.lowercase()}", accent, { open() })
                        RowAction("dismiss", accent, onRemove)
                    }
                }
            }
        }
    }
}

// --- deals ------------------------------------------------------------------------

@Composable
private fun DealsPage(tokens: ColorTokens, accent: Color, reading: Boolean) {
    val context = LocalContext.current
    val deals by ShoppingStore.deals.collectAsStateWithLifecycle()
    val apps = com.tileshell.feature.livetiles.shopping.rememberShoppingApps().orEmpty()
    var expandedKey by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "note") {
            Text(
                if (reading) "offers from your shopping apps, store texts and business chats. last 2 weeks, kept on this phone."
                else "reading order messages is off. turn it on in settings to collect deals.",
                color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (deals.isEmpty()) {
            item(key = "empty") {
                Text("no deals yet. offers appear here as their notifications arrive.", color = tokens.fgDim, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
        items(deals, key = { it.key }) { d ->
            val app = appForMerchant(apps, d.merchant)
            MessageRow(
                title = d.title,
                body = d.text,
                meta = "${d.merchant} · ${dayLabel(d.time)} · " + SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(d.time)).lowercase(),
                metaColor = if (d.food) FoodOrange else ShopBlue,
                appLabel = app?.label?.lowercase(),
                expanded = expandedKey == d.key,
                onToggle = { expandedKey = if (expandedKey == d.key) null else d.key },
                onOpen = { app?.let { openApp(context, it.packageName) } },
                onDismiss = {
                    if (expandedKey == d.key) expandedKey = null
                    ShoppingStore.removeDeal(context, d.key)
                },
                tokens = tokens,
                accent = accent,
            )
        }
        if (deals.isNotEmpty()) {
            item(key = "clear") {
                Text("clear all deals", color = accent, fontSize = 14.sp, modifier = Modifier.clickable { ShoppingStore.clearDeals(context) }.padding(vertical = 12.dp))
            }
        }
    }
}

// --- a message row, as in people's what's new ------------------------------------------

/**
 * A deal or a finished order, the way a "what's new" row works in the people hub: tap it to open the store's app,
 * the arrow expands it to the whole message, and swiping it sideways (or "dismiss" when expanded) removes it.
 */
@Composable
private fun MessageRow(
    title: String,
    body: String,
    meta: String,
    metaColor: Color,
    appLabel: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    tokens: ColorTokens,
    accent: Color,
) {
    SwipeToDismissRow(tokens = tokens, onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(tokens.sheetLine))
            Column(modifier = Modifier.fillMaxWidth().background(if (expanded) tokens.fg.copy(alpha = 0.06f) else Color.Transparent)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen)
                        .padding(vertical = 10.dp, horizontal = if (expanded) 8.dp else 0.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = tokens.fg, fontSize = 17.sp, fontWeight = FontWeight.Light, maxLines = if (expanded) 8 else 2, overflow = TextOverflow.Ellipsis)
                        if (body.isNotEmpty()) {
                            Text(body, color = tokens.fgDim, fontSize = 13.sp, maxLines = if (expanded) 30 else 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(meta, color = metaColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            com.tileshell.core.design.TileIcons["chevron"],
                            contentDescription = if (expanded) "collapse" else "expand",
                            tint = tokens.fgDim,
                            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = if (expanded) -90f else 90f },
                        )
                    }
                }
                if (expanded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp)) {
                        if (appLabel != null) RowAction("open in $appLabel", accent, onOpen)
                        RowAction("dismiss", accent, onDismiss)
                    }
                }
            }
        }
    }
}

// --- past -------------------------------------------------------------------------

@Composable
private fun PastPage(tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    val orders by ShoppingStore.orders.collectAsStateWithLifecycle()
    val past = remember(orders) { pastOrders(orders) }
    val apps = com.tileshell.feature.livetiles.shopping.rememberShoppingApps().orEmpty()
    var expandedKey by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "note") {
            Text("last 90 days. only what arrived after this was turned on is here.", color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (past.isEmpty()) item(key = "empty") { Text("no finished orders yet", color = tokens.fgDim, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp)) }
        items(past, key = { it.key }) { o ->
            val app = appForMerchant(apps, o.merchant)
            MessageRow(
                title = o.title,
                // The message itself, when it says more than the item's name.
                body = o.message.takeUnless { it.isBlank() || it.equals(o.title, ignoreCase = true) }.orEmpty(),
                meta = "${o.merchant} · ${o.status.label} · ${dayLabel(o.updated)}",
                metaColor = if (o.status == OrderStatus.DELIVERED) tokens.fgDim else Closed,
                appLabel = app?.label?.lowercase(),
                expanded = expandedKey == o.key,
                onToggle = { expandedKey = if (expandedKey == o.key) null else o.key },
                onOpen = { app?.let { openApp(context, it.packageName) } },
                onDismiss = {
                    if (expandedKey == o.key) expandedKey = null
                    ShoppingStore.remove(context, o.key)
                },
                tokens = tokens,
                accent = accent,
            )
        }
    }
}

private fun dayLabel(time: Long, now: Long = System.currentTimeMillis()): String {
    val start = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return when {
        time >= start -> "today"
        time >= start - 86_400_000L -> "yesterday"
        else -> SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(Date(time)).lowercase()
    }
}

// --- apps -------------------------------------------------------------------------

@Composable
private fun ShoppingAppsPage(tokens: ColorTokens, accent: Color, editing: Boolean) {
    val context = LocalContext.current
    val apps = rememberShoppingApps() ?: return
    val marks by ShoppingTileMarks.marks(context).collectAsStateWithLifecycle()
    HubAppsPage(
        kind = HubKind.SHOPPING,
        sectionDefs = listOf(
            ShoppingAppKind.SHOPPING.name to "shopping",
            ShoppingAppKind.FOOD.name to "food & groceries",
            ShoppingAppKind.COURIER.name to "couriers",
        ),
        apps = apps.map { HubPageApp(it.packageName, it.label, it.kind.name) },
        editing = editing,
        tokens = tokens,
        accent = accent,
        emptyText = "no shopping, food or courier apps found",
        onOpen = { openApp(context, it) },
        header = {
            Text(
                if (marks.isNullOrEmpty()) "▢ marks the apps on the shopping tile. none marked shows them all." else "▣ is on the shopping tile. tap to add or remove.",
                color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 2.dp),
            )
        },
        subEntries = true,
        tileMarks = marks ?: emptySet(),
        onToggleTileMark = { ShoppingTileMarks.toggle(context, it) },
    )
}

// --- settings ---------------------------------------------------------------------

@Composable
private fun ShoppingSettingsPage(settings: ShoppingSettings, tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("clear order history?") },
            text = { Text("this deletes every order tileshell has recorded on this phone.") },
            confirmButton = { TextButton(onClick = { ShoppingStore.clear(context); confirmClear = false }) { Text("clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("cancel") } },
        )
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingRow("read order messages", "from new notifications only, kept on this phone", settings.readOrderMessages, accent, tokens) { on ->
            ShoppingPrefs.update(context) { it.copy(readOrderMessages = on) }
        }
        SettingRow("lock delivery otps", "fingerprint, face or your screen lock to show one", settings.lockOtps, accent, tokens) { on ->
            ShoppingPrefs.update(context) { it.copy(lockOtps = on) }
        }
        Text("clear order history", color = Closed, fontSize = 15.sp, modifier = Modifier.clickable { confirmClear = true }.padding(vertical = 4.dp))
        Text(
            "orders are read from the notifications of your shopping, food and courier apps, and from sms and email that name a store, as they arrive. " +
                "nothing is uploaded, and orders are not in backups.",
            color = tokens.fgDim, fontSize = 12.sp,
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, accent: Color, tokens: ColorTokens, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = tokens.fg, fontSize = 15.sp)
            Text(subtitle, color = tokens.fgDim, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = accent))
    }
}
