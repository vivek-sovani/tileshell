package com.tileshell.feature.livetiles.money

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens
import com.tileshell.feature.livetiles.HubAppBar
import com.tileshell.feature.livetiles.HubAppBarAction
import com.tileshell.feature.livetiles.NotificationAccess
import com.tileshell.feature.livetiles.openApp
import com.tileshell.feature.livetiles.rememberAppIconBitmap
import com.tileshell.feature.livetiles.rememberNotificationAccess
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val MONEY_PIVOTS = listOf("transactions", "apps")
private val DEBIT = Color(0xFFE5645A)
private val CREDIT = Color(0xFF3FB871)

/**
 * The money hub: "transactions" (read from bank SMS and payment-app
 * notifications, locked behind biometrics / screen lock when set) and "apps"
 * (installed payment and banking apps, most used first). The app bar holds
 * back, settings and pin.
 */
@Composable
fun MoneyHubScreen(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    onPinHub: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "moneyHubProgress",
    )
    // Locked again every time the hub closes.
    var unlocked by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (!visible) unlocked = false }
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current
    LaunchedEffect(Unit) { MoneyStore.ensureLoaded(context) }
    val settings by MoneyPrefs.settings(context).collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = visible) { if (settingsOpen) settingsOpen = false else onDismiss() }
    val pagerState = rememberPagerState(pageCount = { MONEY_PIVOTS.size })
    val pagerScope = rememberCoroutineScope()

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
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(20.dp))
                Text(text = "tileshell", color = tokens.fgDim, fontSize = 14.sp)
                Text("money", color = accent, fontSize = 52.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(12.dp))
                if (!settingsOpen) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        MONEY_PIVOTS.forEachIndexed { index, label ->
                            Text(
                                text = label,
                                color = if (pagerState.currentPage == index) tokens.fg else tokens.fgDim,
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
                } else {
                    Text("settings", color = tokens.fg, fontSize = 20.sp, fontWeight = FontWeight.Light)
                }
                Spacer(Modifier.height(8.dp))
            }
            Box(modifier = Modifier.weight(1f)) {
                if (settingsOpen) {
                    MoneySettingsPage(settings ?: MoneySettings(), tokens, accent)
                } else {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        when (page) {
                            0 -> {
                                val locked = (settings?.lockTransactions ?: true) && !unlocked && MoneyLock.isDeviceSecure(context)
                                if (locked) MoneyLockedPage(tokens, accent) { unlocked = true } else MoneyTransactionsPage(tokens, accent)
                            }
                            else -> MoneyAppsPage(tokens)
                        }
                    }
                }
            }
            HubAppBar(
                tokens = tokens,
                actions = listOf(
                    HubAppBarAction("back", "back") { if (settingsOpen) settingsOpen = false else onDismiss() },
                    HubAppBarAction("settings", "money settings") { settingsOpen = !settingsOpen },
                    HubAppBarAction("pin", "pin to start", onPinHub),
                ),
            )
        }
    }
}

@Composable
private fun MoneyLockedPage(tokens: ColorTokens, accent: Color, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val credentialLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) onUnlocked()
    }
    fun unlock() {
        if (MoneyLock.usesPrompt()) {
            MoneyLock.prompt(context, onSuccess = onUnlocked, onFail = {})
        } else {
            MoneyLock.confirmIntent(context)?.let { credentialLauncher.launch(it) } ?: onUnlocked()
        }
    }
    // Ask straight away; the button is there if they cancel.
    LaunchedEffect(Unit) { unlock() }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).border(2.dp, accent, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("🔒", fontSize = 26.sp) }
        Spacer(Modifier.height(16.dp))
        Text("unlock to see your transactions", color = tokens.fg, fontSize = 16.sp)
        Text("fingerprint, face or your screen lock", color = tokens.fgDim, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "unlock",
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(accent).clickable { unlock() }
                .padding(horizontal = 28.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text("the apps page stays open without unlocking", color = tokens.fgDim, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoneyTransactionsPage(tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    val txns by MoneyStore.transactions.collectAsStateWithLifecycle()
    val access = rememberNotificationAccess()
    var filter by remember { mutableStateOf<String?>(null) }
    val filters = remember(txns) { moneyFilters(txns) }
    val shown = remember(txns, filter) { txns.filter { filter == null || moneyFilterKey(it, filter!!) } }
    val month = remember(shown) { monthTotals(shown) }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        if (!access) {
            item {
                Text(
                    "turn on notification access so tileshell can read bank messages and payment notifications ›",
                    color = accent,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp).clickable {
                        runCatching { context.startActivity(NotificationAccess.settingsIntent()) }
                    },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                TotalCard("spent · ${month.label}", formatRupees(month.spent), DEBIT, tokens, Modifier.weight(1f))
                TotalCard("received · ${month.label}", formatRupees(month.received), CREDIT, tokens, Modifier.weight(1f))
            }
        }
        if (filters.size > 1) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    Chip("all", filter == null, accent, tokens) { filter = null }
                    filters.forEach { f -> Chip(f, filter == f, accent, tokens) { filter = if (filter == f) null else f } }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    "no transactions yet. new bank messages and payment notifications show up here as they arrive — older messages can't be read.",
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        var lastDay = ""
        shown.forEach { txn ->
            val day = dayLabel(txn.time)
            if (day != lastDay) {
                lastDay = day
                item(key = "d-$day-${txn.time}") { Text(day, color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
            }
            item(key = "t-${txn.time}-${txn.amountPaise}") { TxnRow(txn, tokens) }
        }
        shown.firstOrNull { it.balancePaise != null }?.let { withBal ->
            item {
                Text(
                    "balance ${listOfNotNull(withBal.bank, withBal.account?.let { "·$it" }).joinToString(" ")}: ${formatRupees(withBal.balancePaise!!)} (from the last message)",
                    color = tokens.fgDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TotalCard(label: String, value: String, color: Color, tokens: ColorTokens, modifier: Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(tokens.fg.copy(alpha = 0.06f)).padding(10.dp)) {
        Text(label, color = tokens.fgDim, fontSize = 12.sp)
        Text(value, color = color, fontSize = 20.sp)
    }
}

@Composable
private fun Chip(label: String, on: Boolean, accent: Color, tokens: ColorTokens, onClick: () -> Unit) {
    Text(
        label,
        color = if (on) Color.White else tokens.fg,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) accent else Color.Transparent)
            .border(1.dp, if (on) accent else tokens.tileLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun TxnRow(txn: MoneyTxn, tokens: ColorTokens) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(txn.counterparty, color = tokens.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(txn.method, listOfNotNull(txn.bank, txn.account?.let { "·$it" }).joinToString(" ").ifBlank { null }, clock(txn.time))
                    .joinToString(" · "),
                color = tokens.fgDim,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        Text(
            (if (txn.credit) "+" else "−") + formatRupees(txn.amountPaise),
            color = if (txn.credit) CREDIT else DEBIT,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun MoneyAppsPage(tokens: ColorTokens) {
    val apps = rememberMoneyApps()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        if (apps != null && apps.isEmpty()) {
            item { Text("no payment or banking apps found", color = tokens.fgDim, fontSize = 13.sp) }
        }
        listOf(MoneyAppKind.PAYMENT to "payment & wallets", MoneyAppKind.BANK to "banking").forEach { (kind, title) ->
            val list = apps.orEmpty().filter { it.kind == kind }
            if (list.isNotEmpty()) {
                item { Text(title, color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)) }
                list.chunked(4).forEach { row ->
                    item(key = "${kind.name}-${row.first().packageName}") { AppGridRow(row, tokens) }
                }
            }
        }
        item { Text("most used first · tap to open", color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 14.dp)) }
    }
}

@Composable
private fun AppGridRow(apps: List<MoneyApp>, tokens: ColorTokens) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        apps.forEach { app ->
            Column(
                modifier = Modifier.weight(1f).clickable { openApp(context, app.packageName) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val icon = rememberAppIconBitmap(app.packageName)
                if (icon != null) Image(icon, null, modifier = Modifier.size(48.dp)) else Box(Modifier.size(48.dp))
                Spacer(Modifier.height(4.dp))
                Text(app.label.lowercase(), color = tokens.fg, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        repeat(4 - apps.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun MoneySettingsPage(settings: MoneySettings, tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("clear transaction history?") },
            text = { Text("this deletes every transaction tileshell has recorded on this phone.") },
            confirmButton = { TextButton(onClick = { MoneyStore.clear(context); confirmClear = false }) { Text("clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("cancel") } },
        )
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Text("show on tile", color = tokens.fg, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth().border(1.dp, tokens.tileLine)) {
                listOf(MoneyTileDetails.NOTHING to "nothing", MoneyTileDetails.LAST_PAYMENT_AND_RECEIPT to "last payment & receipt").forEach { (value, label) ->
                    val on = settings.tileDetails == value
                    Text(
                        label,
                        color = if (on) Color.White else tokens.fg,
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).background(if (on) accent else Color.Transparent)
                            .clickable { MoneyPrefs.update(context) { it.copy(tileDetails = value) } }
                            .padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        SettingSwitch("lock transactions", "fingerprint, face or your screen lock", settings.lockTransactions, accent, tokens) { on ->
            MoneyPrefs.update(context) { it.copy(lockTransactions = on) }
        }
        SettingSwitch("read bank messages", "from new notifications only, kept on this phone", settings.readBankMessages, accent, tokens) { on ->
            MoneyPrefs.update(context) { it.copy(readBankMessages = on) }
        }
        Text("clear transaction history", color = DEBIT, fontSize = 15.sp, modifier = Modifier.clickable { confirmClear = true }.padding(vertical = 4.dp))
        Text(
            "transactions are read from your bank's sms and payment apps' notifications as they arrive, and kept for a year. nothing is uploaded.",
            color = tokens.fgDim,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, accent: Color, tokens: ColorTokens, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = tokens.fg, fontSize = 15.sp)
            Text(subtitle, color = tokens.fgDim, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = accent))
    }
}

internal data class MonthTotals(val label: String, val spent: Long, val received: Long)

/** This calendar month's spent/received totals. Pure, unit-tested. */
internal fun monthTotals(txns: List<MoneyTxn>, now: Long = System.currentTimeMillis()): MonthTotals {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH)
    val inMonth = txns.filter {
        val c = Calendar.getInstance().apply { timeInMillis = it.time }
        c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m
    }
    return MonthTotals(
        label = SimpleDateFormat("MMM", Locale.ENGLISH).format(Date(now)).lowercase(),
        spent = inMonth.filter { !it.credit }.sumOf { it.amountPaise },
        received = inMonth.filter { it.credit }.sumOf { it.amountPaise },
    )
}

/** Filter chips: each bank account ("hdfc ·1234"), then "upi" and "cards" when used. Pure, unit-tested. */
internal fun moneyFilters(txns: List<MoneyTxn>): List<String> {
    val accounts = txns.mapNotNull { accountLabel(it) }.distinct()
    val methods = buildList {
        if (txns.any { it.method == "upi" }) add("upi")
        if (txns.any { it.method == "card" }) add("cards")
    }
    return accounts + methods
}

internal fun moneyFilterKey(txn: MoneyTxn, filter: String): Boolean = when (filter) {
    "upi" -> txn.method == "upi"
    "cards" -> txn.method == "card"
    else -> accountLabel(txn) == filter
}

private fun accountLabel(t: MoneyTxn): String? =
    t.account?.let { acct -> listOfNotNull(t.bank, "·$acct").joinToString(" ") }

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

private fun clock(time: Long): String = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(time)).lowercase()
