package com.tileshell.feature.personalize

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens

/**
 * The personalize bottom sheet (FR-7): a slide-up panel over a fading scrim with
 * a grip, a lowercase "personalize" title, a dark/light segmented toggle and the
 * 14 accent swatches (prototype `buildSettings`). Stateless — it renders the
 * passed [dark]/[accentId] and reports changes via the callbacks, so the host
 * persists them and feeds the new values straight back, re-skinning live.
 *
 * Transparency, blur, wallpaper and layout-reset groups from the prototype land
 * in later sessions; this sheet covers theme + accent only.
 */
@Composable
fun PersonalizeSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onThemeChange: (dark: Boolean) -> Unit,
    onAccentChange: (id: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Slide/fade progress (prototype .sheet transition: .3s cubic-bezier).
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "sheetProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)

    Box(modifier = modifier.fillMaxSize()) {
        // Scrim (prototype rgba(0,0,0,.5)); tap to dismiss.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.sheet)
                // Swallow taps so they don't fall through to the scrim.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            // Grip.
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tokens.fgDim.copy(alpha = 0.5f)),
            )

            Text(
                text = "personalize",
                color = tokens.fg,
                fontSize = 30.sp,
                fontWeight = FontWeight.W200,
                letterSpacing = (-1).sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 14.dp),
            )

            // ---- theme ----
            SettingGroup(label = "theme", tokens.fgDim) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, tokens.tileLine),
                ) {
                    SegCell("dark", selected = dark, accent = accent, fg = tokens.fg) {
                        onThemeChange(true)
                    }
                    SegCell("light", selected = !dark, accent = accent, fg = tokens.fg) {
                        onThemeChange(false)
                    }
                }
            }

            // ---- accent colour ----
            SettingGroup(label = "accent colour", tokens.fgDim) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TileAccents.swatches.chunked(7).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { (id, color) ->
                                Swatch(
                                    color = color,
                                    selected = id == accentId,
                                    ring = tokens.fg,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onAccentChange(id) },
                                )
                            }
                            // Pad a short final row so swatches keep their column width.
                            repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** A labelled settings group (prototype .set-group / .set-label). */
@Composable
private fun SettingGroup(label: String, labelColor: Color, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 18.dp)) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        content()
    }
}

/** One cell of the segmented toggle (prototype .seg div / .seg div.on). */
@Composable
private fun RowScope.SegCell(
    label: String,
    selected: Boolean,
    accent: Color,
    fg: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .background(if (selected) accent else Color.Transparent)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else fg,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** One accent swatch (prototype .swatches i / .swatches i.sel). */
@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    ring: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .then(if (selected) Modifier.border(2.5.dp, ring).padding(4.dp) else Modifier)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
    )
}
