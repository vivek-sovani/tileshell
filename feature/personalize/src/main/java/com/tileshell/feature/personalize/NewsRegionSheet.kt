package com.tileshell.feature.personalize

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens

/**
 * One selectable news region for [RegionChipGrid] — framework-free (no
 * dependency on `:feature:livetiles`'s region/country types) so this module
 * doesn't need a new cross-module dependency; the caller resolves region
 * codes to display names and builds this list.
 */
data class RegionOption(val code: String, val label: String, val enabled: Boolean)

/**
 * The region multi-select chip grid — shared by [NewsRegionSheet] (reached from
 * Personalize) and the feed page's own gear-icon settings sheet, so both
 * surfaces edit the exact same subscribed-region set with one visual.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegionChipGrid(
    regions: List<RegionOption>,
    accent: Color,
    tokens: ColorTokens,
    onToggle: (code: String, enabled: Boolean) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        regions.forEach { region ->
            RegionChip(
                label = region.label,
                on = region.enabled,
                accent = accent,
                tokens = tokens,
                onClick = { onToggle(region.code, !region.enabled) },
            )
        }
    }
}

/** Selectable chip for one region (mirrors the feed page's `FeedSourceChip` look). */
@Composable
private fun RegionChip(
    label: String,
    on: Boolean,
    accent: Color,
    tokens: ColorTokens,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (on) Modifier.background(accent)
                else Modifier.border(1.dp, tokens.tileLine, RoundedCornerShape(16.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = if (on) Color.White else tokens.fgDim, fontSize = 13.sp)
    }
}

/**
 * The "news region" sub-sheet (personalize → choose news regions): the same
 * region multi-select the feed page's gear icon already exposes, surfaced
 * directly from Personalize too — both edit the same subscribed-region set.
 */
@Composable
fun NewsRegionSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    regions: List<RegionOption>,
    onToggleRegion: (code: String, enabled: Boolean) -> Unit,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "newsRegionSheetProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)

    BackHandler(enabled = visible) { onDismiss() }

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f)
                    .graphicsLayer { translationY = size.height * (1f - progress) }
                    .background(tokens.sheet, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                // drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tokens.fgDim.copy(alpha = 0.4f)),
                )

                Text(
                    text = "news region",
                    color = tokens.fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Text(
                    text = "select any number",
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )

                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    RegionChipGrid(
                        regions = regions,
                        accent = accent,
                        tokens = tokens,
                        onToggle = onToggleRegion,
                    )
                }
            }
        }
    }
}
