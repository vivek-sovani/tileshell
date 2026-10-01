package com.tileshell.feature.livetiles

import android.provider.Telephony
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The quick actions for a person tapped on the favourites tile: call (opens the
 * dialer with the number, never dials outright), message (the SMS app), the chat
 * app they last messaged you on (a WhatsApp chat opens directly; any other app
 * just opens), and their contact card. Call/message hide without a number.
 * [person] null closes it; the last person stays drawn while it slides away.
 */
@Composable
fun FavouriteQuickSheet(
    person: PersonSummary?,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    onOpenFavourites: () -> Unit,
    rightHalf: Boolean = false,
) {
    val progress by animateFloatAsState(
        targetValue = if (person != null) 1f else 0f,
        animationSpec = tween(260, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "favouriteQuickSheet",
    )
    var shown by remember { mutableStateOf<PersonSummary?>(null) }
    if (person != null) shown = person
    val p = shown
    if (p == null || (person == null && progress == 0f)) return

    BackHandler(enabled = person != null) { onDismiss() }
    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current

    val phone by produceState<String?>(initialValue = null, p.contactId) {
        value = withContext(Dispatchers.IO) { primaryPhoneNumber(context, p.contactId) }
    }
    LaunchedEffect(Unit) { MessagedLog.ensureLoaded(context) }
    val log by MessagedLog.entries.collectAsStateWithLifecycle()
    val lastMessaged = remember(log, p) { matchMessaged(log, listOf(p)).firstOrNull()?.second }
    val smsPackage = remember { runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull() }
    val number = phone
    // The SMS app is already "message"; otherwise the app they last wrote on,
    // else WhatsApp when installed and there's a number for it.
    val chatPackage = remember(lastMessaged, smsPackage, number) {
        lastMessaged?.packageName?.takeIf { it != smsPackage }
            ?: "com.whatsapp".takeIf { number != null && isWhatsAppInstalled(context) }
    }

    fun act(block: () -> Unit) {
        block()
        onDismiss()
    }

    SheetStage(rightHalf = rightHalf) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress }
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = (1f - progress) * size.height }
                .background(tokens.sheet)
                // Taps inside the panel must not fall through to the scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ContactAvatar(p, size = 56.dp, fontSize = 18.sp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        p.name.lowercase(),
                        color = tokens.fg,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = lastMessaged?.let {
                        "${appLabel(context, it.packageName)} · ${messagedAgo(it.time, System.currentTimeMillis())}"
                    } ?: number
                    if (subtitle != null) {
                        Text(subtitle, color = tokens.fgDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                if (number != null) {
                    QuickActionTile("call", accent, Modifier.weight(1f), icon = { TileGlyph("phone") }) {
                        act { callContact(context, number) }
                    }
                    QuickActionTile("message", accent, Modifier.weight(1f), icon = { TileGlyph("messages") }) {
                        act { messageContact(context, number) }
                    }
                }
                if (chatPackage != null) {
                    val label = remember(chatPackage) { appLabel(context, chatPackage) }
                    QuickActionTile(label, accent, Modifier.weight(1f), icon = { AppGlyph(chatPackage) }) {
                        act {
                            if (chatPackage == "com.whatsapp" && number != null) whatsAppContact(context, number)
                            else openApp(context, chatPackage)
                        }
                    }
                }
                QuickActionTile("contact", TileAccents.forId("slate"), Modifier.weight(1f), icon = { TileGlyph("contacts") }) {
                    act { openContactCard(context, p.contactId, p.lookupKey) }
                }
                // Same tile size however many show (2-4), left-aligned.
                val shownTiles = (if (number != null) 2 else 0) + (if (chatPackage != null) 1 else 0) + 1
                repeat(4 - shownTiles) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (lastMessaged != null) number.orEmpty() else "",
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "open favourites ›",
                    color = accent,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            act(onOpenFavourites)
                        }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    color: Color,
    modifier: Modifier,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .aspectRatio(1f)
            .background(color)
            .clickable(onClick = onClick)
            .padding(9.dp),
    ) {
        icon()
        Text(label, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TileGlyph(key: String) {
    Icon(TileIcons[key], contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
}

/** The chat app's own icon, so "whatsapp" or "telegram" reads at a glance. */
@Composable
private fun AppGlyph(packageName: String) {
    val bitmap = rememberAppIconBitmap(packageName)
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(26.dp))
    } else {
        TileGlyph("messages")
    }
}

private fun appLabel(context: android.content.Context, packageName: String): String =
    runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString().lowercase()
    }.getOrDefault(packageName.substringAfterLast('.'))
