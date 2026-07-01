package com.tileshell.feature.start

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.TileAccents

/**
 * First-run hint overlay (S19): a one-time card leading with a brief feature
 * summary ([featureText]) followed by the prototype's gesture hint text
 * (`<div class="hint">` in the prototype), shown over Start on the very first
 * launch and dismissed by a tap. A `SharedPreferences` flag keeps it from ever
 * returning. The card sits at the bottom — clear of the grid and the chevron
 * affordance it describes — so the user can glance between the two. The bold
 * highlights and "got it" action tie into [accentId] so the card reads as part
 * of the same theme as the tiles behind the scrim, not a generic dark dialog.
 */
@Composable
fun FirstRunHint(accentId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(!FirstRunHintPrefs.shown(context)) }
    val accent = TileAccents.forId(accentId)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        fun dismiss() {
            FirstRunHintPrefs.markShown(context)
            visible = false
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Scrim (prototype overlay tone); tap anywhere to dismiss.
                .background(Color(0x99060608))
                .clickable(onClick = ::dismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 24.dp)
                    .fillMaxWidth()
                    .background(Color(0xFF1B1B22), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "welcome to tileshell",
                    color = Color(0xFFF6F6F8),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Thin,
                )
                Text(
                    text = featureText(accent),
                    color = Color(0xFFB4B4C2),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Text(
                    text = hintText(accent),
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
                        .clickable(onClick = ::dismiss)
                        .padding(top = 4.dp, bottom = 2.dp),
                )
            }
        }
    }
}

/** A brief, plain-language summary of what the launcher can do, shown once on first run. */
private fun featureText(accent: Color) = buildAnnotatedString {
    val bold = SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)
    withStyle(bold) { append("live tiles") }
    append(" for clock, weather, calendar, mail, music & more · a ")
    withStyle(bold) { append("feed") }
    append(" page for news, widgets & now-playing · ")
    withStyle(bold) { append("folders") }
    append(" and widget stacks to group apps · deep ")
    withStyle(bold) { append("personalization") }
    append(" — colours, wallpapers, fonts, grid density.")
}

/**
 * The prototype hint string with the same bolded spans
 * (`Windows Mobile Launcher.html` → `.hint`). `<b>` highlights map to an
 * accent-tinted weight emphasis (was a fixed lavender-gray in the prototype).
 */
private fun hintText(accent: Color) = buildAnnotatedString {
    val bold = SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)
    withStyle(bold) { append("tap") }
    append(" a tile to open · ")
    withStyle(bold) { append("long-press") }
    append(" to edit (then drag to move, drop a tile onto another to make a folder, ")
    append("use the corner handles to resize/unpin) · ")
    withStyle(bold) { append("chevron") }
    append(" → app list · in the app list ")
    withStyle(bold) { append("tap a letter") }
    append(" for the jump grid, ")
    withStyle(bold) { append("long-press") }
    append(" an app to pin.")
}

/** One-shot "first-run hint seen" flag, kept in the shared launcher prefs. */
private object FirstRunHintPrefs {
    private const val PREFS = "tileshell.prefs"
    private const val KEY = "first_run_hint_shown"

    fun shown(context: Context): Boolean =
        prefs(context).getBoolean(KEY, false)

    fun markShown(context: Context) {
        prefs(context).edit().putBoolean(KEY, true).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
