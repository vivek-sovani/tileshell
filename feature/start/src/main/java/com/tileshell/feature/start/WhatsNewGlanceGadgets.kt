package com.tileshell.feature.start

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.TileAccents

/**
 * One-time "what's new" card announcing the v3.5.0 glance-gadgets catalog to
 * an *existing* install updating into it — a fresh install never sees this
 * (it gets [FirstRunHint]/[HomeStyleWizardScreen] instead, which already
 * cover onboarding); see [WhatsNewGlanceGadgetsPrefs]'s doc comment for how
 * the two are told apart. Visibility is owned by [StartViewModel.whatsNewOpen]
 * (mirrors [StartViewModel.homeStyleWizardOpen]'s shape) rather than local
 * `remember` state, so `goHome()` can mark it seen on a Home/back dismissal
 * the same "never nags twice" way every other one-shot overlay in this app
 * does. Same visual shape as [FirstRunHint] (bottom card over a scrim,
 * accent-tinted bold spans, tap-anywhere-or-"got it" to dismiss) so it reads
 * as part of the same family instead of a new kind of dialog.
 */
@Composable
fun WhatsNewGlanceGadgetsCard(accentId: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val accent = TileAccents.forId(accentId)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x99060608))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .fillMaxWidth()
                .background(Color(0xFF1B1B22), RoundedCornerShape(10.dp))
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "what's new · glance gadgets",
                color = Color(0xFFF6F6F8),
                fontSize = 17.sp,
                fontWeight = FontWeight.Thin,
            )
            Text(
                text = whatsNewText(accent),
                color = Color(0xFFB4B4C2),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Text(
                text = howToUseText(accent),
                color = Color(0xFF8A8A96),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
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

/** What was actually added — the full glance-gadget catalog, in the picker's own grouping order. */
private fun whatsNewText(accent: Color) = buildAnnotatedString {
    val bold = SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)
    withStyle(bold) { append("glance gadgets") }
    append(": tileshell's own live cards, pinnable to ")
    withStyle(bold) { append("start") }
    append(" or the ")
    withStyle(bold) { append("feed") }
    append(", alongside real android widgets — ")
    withStyle(bold) { append("stock market") }
    append(" (one stock, a sector/country basket, or your own list), ")
    withStyle(bold) { append("commodities") }
    append(", ")
    withStyle(bold) { append("sports") }
    append(" scores, extra ")
    withStyle(bold) { append("calendar systems") }
    append(" (hindu panchang & more), ")
    withStyle(bold) { append("countdown") }
    append(", ")
    withStyle(bold) { append("sticky note") }
    append(", ")
    withStyle(bold) { append("notes") }
    append(", ")
    withStyle(bold) { append("tasks") }
    append(", plus ")
    withStyle(bold) { append("battery") }
    append(", ")
    withStyle(bold) { append("alarm") }
    append(", ")
    withStyle(bold) { append("moon phase") }
    append(", ")
    withStyle(bold) { append("flashlight") }
    append(", and ")
    withStyle(bold) { append("steps") }
    append(" at-a-glance cards.")
}

/**
 * How to actually reach and use them — both entry points, since this same
 * gadget catalog is addable from either surface: Start's own "widgets" row in
 * the edit bar ([com.tileshell.feature.personalize.WidgetListSheet]'s
 * `WIDGET_CATALOG`) and the feed's "add" picker under its widgets section
 * ([com.tileshell.feature.start.feed.WidgetPicker]) — not the feed alone.
 */
private fun howToUseText(accent: Color) = buildAnnotatedString {
    val bold = SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)
    append("on ")
    withStyle(bold) { append("start") }
    append(": long-press empty space to edit, then tap ")
    withStyle(bold) { append("widgets") }
    append(" · on the ")
    withStyle(bold) { append("feed") }
    append(" (swipe right from start): under widgets, tap ")
    withStyle(bold) { append("add") }
    append(" — tileshell's own gadgets are listed first, ahead of real apps · ")
    withStyle(bold) { append("long-press") }
    append(" any card (start tile, glance gadget, widget, or weather/agenda/now-playing) to jump straight into edit mode — resize, reorder, or remove without tapping \"edit\" first.")
}

/**
 * One-shot "glance-gadgets what's-new notice seen" flag. Shown only when
 * [FirstRunHintPrefs.shown] is already `true` — that hint is dismissed once,
 * in whatever session first onboards a device, so seeing it already means
 * this is an *existing* install upgrading into the new feature, not a fresh
 * install still going through first-run onboarding (which shows
 * [FirstRunHint]/the home-style wizard instead — showing both in the same
 * session would be redundant). A fresh install therefore never marks this
 * shown either, but never needs to: [FirstRunHintPrefs.shown] gates it out
 * until the hint itself is dismissed, and by then a fresh install has
 * nothing new to be told about — it already saw the whole current feature
 * set once it reaches that point.
 */
internal object WhatsNewGlanceGadgetsPrefs {
    private const val PREFS = "tileshell.prefs"
    private const val KEY = "whats_new_glance_gadgets_shown"

    fun shown(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun markShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }
}
