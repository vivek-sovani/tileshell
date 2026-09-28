package com.tileshell.feature.start

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
fun WhatsNewSheet(visible: Boolean, accentId: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
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
                Text(
                    text = "what's new in $WHATS_NEW_VERSION_NAME",
                    color = Color(0xFFF6F6F8),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Thin,
                )
                WhatsNewSection(title = "new features", accent = accent, items = WHATS_NEW_FEATURES)
                WhatsNewSection(title = "bugs fixed", accent = accent, items = WHATS_NEW_FIXES)
                Text(
                    text = "got it",
                    color = accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                        .padding(top = 4.dp, bottom = 2.dp),
                )
            }
        }
    }
}

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
internal const val WHATS_NEW_VERSION_CODE = 500
private const val WHATS_NEW_VERSION_NAME = "5.0.0"

// Started word-for-word in sync with docs/PLAY_STORE.md's "Release notes (v4.5.0)"
// Play-facing blurb (no character limit here, so each line reads as a full
// sentence rather than a compressed bullet fragment). The two trailing fixes
// were added in a later same-versionCode re-cut; per this project's own
// convention the frozen Play blurb text isn't rewritten for a re-cut (see
// "Also folded into this same versionCode 450" in PLAY_STORE.md instead) —
// this in-app card reflects what's actually in the build, not that frozen text.
private val WHATS_NEW_FEATURES = listOf(
    "hubs — tap the weather, music, calendar, people, productivity or battery tile for its own full-screen hub",
    "music: your own library and playlists, podcasts and internet radio, all playing in the background",
    "people: chats, messages, mail and social notifications in one list — reply inline, swipe to dismiss",
    "productivity: your next meeting with a join button, notes, named task lists and office apps",
    "battery tile, hub and widget, from tileshell's own battery log",
    "money: bank and upi transactions from new bank sms and payment notifications, locked with your fingerprint, plus your payment and banking apps",
    "weather shows a moon at night, plus sunrise, sunset and uv index; panchang shows moonrise and moonset",
    "reset start layout now runs a quick setup — tiles or icons, theme, one colour or multicolour, and your apps",
    "every permission, and refresh rates for weather, news, stocks and sports, each in one place",
)

private val WHATS_NEW_FIXES = listOf(
    "less battery use: notification images are cached and background polling pauses with live tiles",
    "music pauses when bluetooth or headphones disconnect",
    "widget settings no longer cover the whole screen in landscape",
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
