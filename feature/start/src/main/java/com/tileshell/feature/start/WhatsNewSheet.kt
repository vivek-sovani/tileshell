package com.tileshell.feature.start

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextAlign
import com.tileshell.core.data.KeyboardFeature
import com.tileshell.core.design.TileIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.TileAccents

/**
 * One-time "what's new in this version" card, shown on the first launch after
 * an update (never on a genuinely fresh install — see [WhatsNewPrefs] and its
 * call site in `StartViewModel.init`). Mirrors [FirstRunHint]'s own scrim +
 * bottom-card shape and dismiss-by-tap-anywhere convention rather than
 * introducing a new overlay style, since both are one-shot informational
 * cards over Start.
 *
 * Content is the same "New features" / "Bugs fixed" text already committed to
 * `docs/PLAY_STORE.md`'s "Release notes (v4.5.0)" entry, hardcoded here since
 * that doc isn't shipped in the APK — kept in sync by hand each release (see
 * [WHATS_NEW_VERSION_CODE]'s own doc comment).
 */
@Composable
fun WhatsNewSheet(
    visible: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Non-null for the hubs release (5.0.0): the card introduces each hub and
     * offers to reset Start with the new layout, via the setup wizard. */
    onSetUpWithHubs: (() -> Unit)? = null,
) {
    val accent = TileAccents.forId(accentId)

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99060608))
                // Deliberately not a tap-to-dismiss scrim (unlike FirstRunHint's) —
                // user-requested: only "got it" should dismiss this card, so it
                // reads as a real acknowledgement rather than something a stray
                // tap could brush past. Still consumes the tap (no-op click, no
                // ripple) so it doesn't fall through to a Start tile underneath.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // One screen of a split or wide window: the buttons are not stretched across a hinge.
            com.tileshell.core.design.SingleScreenHost {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Column(
                        modifier = Modifier
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 24.dp)
                            // Longer releases can be taller than a small screen.
                            .verticalScroll(rememberScrollState())
                            .fillMaxWidth()
                            .background(Color(0xFF1B1B22), RoundedCornerShape(10.dp))
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (onSetUpWithHubs != null) {
                            HubsIntro(accent = accent, onSetUp = onSetUpWithHubs, onKeep = onDismiss)
                            return@Column
                        }
                        Text(
                            text = "what's new in $WHATS_NEW_VERSION_NAME",
                            color = Color(0xFFF6F6F8),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Thin,
                        )
                        if (KeyboardFeature.ENABLED) KeyboardIntro(accent = accent)
                        WhatsNewSection(title = "new features", accent = accent, items = WHATS_NEW_FEATURES)
                        WhatsNewSection(title = "bugs fixed", accent = accent, items = WHATS_NEW_FIXES)
                        Text(
                            text = "got it",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(accent, RoundedCornerShape(4.dp))
                                .clickable(onClick = onDismiss)
                                .padding(vertical = 11.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The hubs release's update card: one line per hub, then "set up start
 * with hubs" (saves the layout to history, runs the setup wizard) or "keep my
 * layout". */
@Composable
private fun HubsIntro(accent: Color, onSetUp: () -> Unit, onKeep: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("tileshell $WHATS_NEW_VERSION_NAME — hubs", color = Color(0xFFF6F6F8), fontSize = 17.sp, fontWeight = FontWeight.Thin)
        Text(
            "tiles now open full-screen hubs that bring one part of your phone together.",
            color = Color(0xFFB4B4C2),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(10.dp),
    ) {
        Icon(TileIcons["app"], contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Text(
            "apps in one place: the music, people, productivity and money hubs each have an apps tab that gathers " +
                "the related apps on your phone — music players, chat and mail, office and meeting apps, " +
                "payment and banking apps.",
            color = Color(0xFFB4B4C2),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HUB_INTROS.forEach { hub ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(32.dp).background(TileAccents.forId(hub.colorId)),
                ) {
                    Icon(TileIcons[hub.icon], contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(hub.name, color = Color(0xFFF6F6F8), fontSize = 14.sp)
                    Text(hub.description, color = Color(0xFF9A9AA8), fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
    }
    if (KeyboardFeature.ENABLED) {
        Row(verticalAlignment = Alignment.Top) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp).background(accent)) {
                Text("अ", color = Color.White, fontSize = 16.sp)
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text("windows phone-style keyboard · beta", color = Color(0xFFF6F6F8), fontSize = 14.sp)
                Text(
                    "a metro keyboard in your accent colour, with swipe typing, emoji and clipboard. type मराठी " +
                        "and हिन्दी in english letters or on devanagari keys. turn it on in personalize › system › " +
                        "tileshell keyboard.",
                    color = Color(0xFF9A9AA8),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
    Text(
        "set up start with the new hubs layout? your current layout is saved to layout history first, so you can restore it.",
        color = Color(0xFF9A9AA8),
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "set up start with hubs",
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(accent, RoundedCornerShape(4.dp))
                .clickable(onClick = onSetUp)
                .padding(vertical = 11.dp),
        )
        Text(
            "keep my layout",
            color = accent,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, accent, RoundedCornerShape(4.dp))
                .clickable(onClick = onKeep)
                .padding(vertical = 10.dp),
        )
    }
    Text(
        "you can add any hub later from personalize › add live tiles.",
        color = Color(0xFF8A8A96),
        fontSize = 12.sp,
    )
}

/** 5.6.0: introduces the Windows Phone-style keyboard (beta) and opens Android's keyboard list to turn it on. */
@Composable
private fun KeyboardIntro(accent: Color) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val enabled = remember { KeyboardFeature.isEnabled(context) }
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp).background(accent)) {
            Text("अ", color = Color.White, fontSize = 16.sp)
        }
        Column(modifier = Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("windows phone-style keyboard", color = Color(0xFFF6F6F8), fontSize = 14.sp)
                Text(
                    "BETA",
                    color = accent,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 8.dp).border(1.dp, accent).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Text(
                "a metro keyboard in your accent colour with wordflow (swipe across letters to type), suggestions, " +
                    "emoji and clipboard. type मराठी and हिन्दी in english letters or on devanagari keys. " +
                    "it never connects to the internet, and it is still in beta.",
                color = Color(0xFF9A9AA8),
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Text(
                if (enabled) "turned on — choose it when you type, or in personalize › system" else "turn it on in android's keyboard settings  ›",
                color = accent,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable {
                        runCatching {
                            if (enabled) {
                                context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.showInputMethodPicker()
                            } else {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    },
            )
        }
    }
}

private data class HubIntro(val name: String, val icon: String, val colorId: String, val description: String)

private val HUB_INTROS = listOf(
    HubIntro("weather", "weather", "cyan", "hourly and 7-day forecast, sunrise, sunset, uv index and a moon at night"),
    HubIntro("music", "music", "orange", "your own library and playlists, podcasts and internet radio, with gapless playback"),
    HubIntro("calendar", "calendar", "cobalt", "this week, what's next and a month view of your events"),
    HubIntro("people", "people", "teal", "chats, messages and mail in one list — reply inline, swipe to dismiss"),
    HubIntro("productivity", "productivity", "purple", "your next meeting with a join button, notes, and task lists with reminders"),
    HubIntro("battery", "battery", "lime", "drain rate, time left, today's curve and screen time per app"),
    HubIntro("money", "money", "green", "bank, upi and card transactions from new bank sms, and card bills by due date, locked with your fingerprint — add it from add live tiles"),
)

/** True only for the hubs release (5.0.0), whose card offers the hubs setup; 5.6.0's card is the plain list. */
internal const val WHATS_NEW_OFFERS_HUB_SETUP = false

@Composable
private fun WhatsNewSection(title: String, accent: Color, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        items.forEach { item ->
            Row {
                Text(
                    text = "•",
                    color = Color(0xFF8A8A96),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = item,
                    color = Color(0xFFB4B4C2),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

/**
 * The versionCode this content describes — must be bumped by hand alongside
 * `app/build.gradle.kts`'s own `versionCode` every time [WHATS_NEW_FEATURES]/
 * [WHATS_NEW_FIXES] are updated for a new release, or the card will show the
 * previous release's content under the new version's own version-code gate.
 */
internal const val WHATS_NEW_VERSION_CODE = 560
private const val WHATS_NEW_VERSION_NAME = "5.6.0"

// Started word-for-word in sync with docs/PLAY_STORE.md's "Release notes (v4.5.0)"
// Play-facing blurb (no character limit here, so each line reads as a full
// sentence rather than a compressed bullet fragment). The two trailing fixes
// were added in a later same-versionCode re-cut; per this project's own
// convention the frozen Play blurb text isn't rewritten for a re-cut (see
// "Also folded into this same versionCode 450" in PLAY_STORE.md instead) —
// this in-app card reflects what's actually in the build, not that frozen text.
private val WHATS_NEW_FEATURES = listOf(
    "music + videos hub: videos from folders you choose, a video and ott apps section, podcasts and your last song carry on where you left them",
    "hubs and launched apps grow out of the tile you tap and shrink back when you return",
    "dual-screen and foldable layouts: hubs, personalize, the guide and about use both screens of a surface duo or galaxy z fold",
    "android auto: your music, podcasts and radio in the car, with voice search",
    "new hubs: markets, sports, news, clock, shopping and health",
    "long-press a tile for quick actions: its shortcuts, size and colour, move, app info and the notification it is showing",
    "live tiles flip like a windows phone tile, notification tiles are laid out like one, and the size picker is a grid with a live preview",
    "panchang in eight languages with each region's festivals, and news in 13 languages plus china, hong kong and taiwan",
    "hubs and sheets can follow your wallpaper's colour, with a hub colour choice in personalize",
)

private val WHATS_NEW_FIXES = listOf(
    "less battery use from background work and animations",
    "returning from an app lands on the page you left, not the first start page",
    "a tile no longer stays part-way through a flip; dragging, backups and apps launched from a tile are smoother",
)

/**
 * Tracks the last versionCode this device has actually seen the "what's new"
 * card for — deliberately distinct from [FirstRunHintPrefs]'s plain boolean,
 * since this needs to re-trigger on every future version bump, not just once
 * ever. No prior record defaults to `0` (never a real versionCode), so an
 * *existing* user's very first check against this feature correctly shows
 * once — the "don't show on a genuinely fresh install" guard lives entirely
 * at the call site in `StartViewModel.init`, which only ever consults this at
 * all once `HomeStyleWizardPrefs.shown()` is already true (the same "has this
 * device run TileShell before" signal the wizard itself uses) — a fresh
 * install sees the wizard instead and never reaches this check.
 */
object WhatsNewPrefs {
    private const val PREFS = "tileshell.prefs"
    private const val KEY_LAST_SEEN = "whats_new_last_seen_version_code"

    fun shouldShow(context: Context, currentVersionCode: Int): Boolean =
        prefs(context).getInt(KEY_LAST_SEEN, 0) < currentVersionCode

    fun markSeen(context: Context, versionCode: Int) {
        prefs(context).edit().putInt(KEY_LAST_SEEN, versionCode).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
