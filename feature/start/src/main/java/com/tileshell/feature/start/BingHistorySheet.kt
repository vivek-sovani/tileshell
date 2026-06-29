package com.tileshell.feature.start

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens
import com.tileshell.feature.livetiles.BingImage
import com.tileshell.feature.livetiles.fetchBingImages
import com.tileshell.feature.start.feed.rememberRemoteImage

/**
 * Slide-up viewer of the last several days of Microsoft Bing wallpapers (Bing keeps
 * roughly 8). Each thumbnail is loaded from Bing on demand; tapping one calls [onPick]
 * with its full-resolution URL — which pins that image as the wallpaper (daily mode off).
 * Same bottom-sheet animation/tokens as the personalize/about sheets.
 */
@Composable
fun BingHistorySheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onPick: (imageUrl: String) -> Unit,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "bingHistoryProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)

    var images by remember { mutableStateOf<List<BingImage>?>(null) }
    LaunchedEffect(visible) {
        if (visible && images == null) images = fetchBingImages()
    }

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
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.sheet)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
        ) {
            // Grip
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tokens.fgDim.copy(alpha = 0.5f)),
            )

            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                Text(
                    text = "recent bing wallpapers",
                    color = tokens.fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W300,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "tap one to set it as your wallpaper",
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(18.dp))

                val list = images
                when {
                    list == null -> StatusLine("loading…", tokens.fgDim)
                    list.isEmpty() -> StatusLine("couldn't load bing wallpapers — check your connection", tokens.fgDim)
                    else -> {
                        // Two-per-row grid.
                        list.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                rowItems.forEach { img ->
                                    BingHistoryCell(
                                        image = img,
                                        tokens = tokens,
                                        accent = accent,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onPick(img.fullUrl) },
                                    )
                                }
                                // Keep a lone trailing item half-width.
                                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Text(text = text, color = color, fontSize = 14.sp, modifier = Modifier.padding(vertical = 24.dp))
}

@Composable
private fun BingHistoryCell(
    image: BingImage,
    tokens: com.tileshell.core.design.ColorTokens,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(6.dp))
                .background(tokens.tileLine),
        ) {
            val bitmap = rememberRemoteImage(image.thumbUrl)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = image.title.ifBlank { "bing wallpaper ${image.date}" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Bottom scrim + date for legibility over any image.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f),
                        ),
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(text = image.date, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.W500)
            }
        }
        if (image.title.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = image.title,
                color = tokens.fgDim,
                fontSize = 11.sp,
                maxLines = 2,
            )
        }
    }
}
