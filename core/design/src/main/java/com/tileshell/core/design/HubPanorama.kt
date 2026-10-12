package com.tileshell.core.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Section widths come from [panoramaGeometry]: a portrait phone's sections stay
 * screen-wide less a peek of the next; on a landscape screen (or a tablet)
 * several sections show side by side, each about a phone's width. On a window
 * split at a hinge (Surface Duo, a half-open Galaxy Z Fold) each screen holds
 * whole sections, nothing is drawn under the hinge, and the title and what sits
 * under it stay on the first screen.
 */
private val PANORAMA_MAX_SECTION = PANORAMA_MAX_SECTION_DP.dp

/** Side margin of the title and section headers, matching the pages' own. */
private val PANORAMA_MARGIN = 18.dp

/**
 * Windows Phone panorama shell for a hub (user-requested, following the
 * Lumia hubs): a giant thin title that runs off the right edge and slides
 * slower than the content as you swipe, then the sections side by side, each
 * a little narrower than the screen so the next one's header peeks in. A
 * section header can be tapped to jump to it. Lumia's sizes and colours:
 * title ~96sp extra-light, headers ~34sp light, both in the theme
 * foreground (white on black / black on white), not the accent.
 *
 * [belowTitle] sits between the title and the sections (e.g. a search field);
 * [section] draws section [index]'s content under its header.
 */
@Composable
fun HubPanorama(
    title: String,
    sections: List<String>,
    pagerState: PagerState,
    tokens: ColorTokens,
    modifier: Modifier = Modifier,
    belowTitle: @Composable ColumnScope.() -> Unit = {},
    // Always shown between the title and the sections, outside the pager and never folded with the title (a video
    // must not sit inside the sliding pages: their movement leaves it black).
    pinned: @Composable ColumnScope.() -> Unit = {},
    section: @Composable (index: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val titleStyle = TextStyle(
        color = tokens.fg,
        fontSize = 96.sp,
        fontWeight = FontWeight.ExtraLight,
        // Tightened for Latin titles only: on Devanagari ("पंचांग") the
        // negative spacing threw off the measured width and cut the last
        // letter off.
        letterSpacing = if (title.all { it.code < 128 }) (-3).sp else 0.sp,
        lineHeight = 96.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
    if (PanoramaCapture.enabled) {
        PanoramaCaptureLayout(
            title, sections, tokens, titleStyle, PANORAMA_MAX_SECTION, PANORAMA_MARGIN,
            modifier, belowTitle, section,
        )
        return
    }
    val measurer = rememberTextMeasurer()
    val titleWidthPx = remember(title, titleStyle) { measurer.measure(title, titleStyle).size.width }
    val density = LocalDensity.current
    // The width comes from onSizeChanged rather than BoxWithConstraints: with
    // BoxWithConstraints here, R8 merged this lambda with Start's own and the
    // release build failed Android's verifier at launch (VerifyError in
    // StartScreenKt).
    var widthPx by remember { mutableIntStateOf(0) }
    var heightPx by remember { mutableIntStateOf(0) }
    var originX by remember { mutableFloatStateOf(0f) }
    var originY by remember { mutableFloatStateOf(0f) }
    // The hinge, when this panorama spans one: screens side by side (vertical
    // hinge) or one above the other (horizontal).
    val hinge = LocalHinge.current
    val sideBySide = hinge?.splitOf(vertical = true, extent = widthPx.toFloat(), origin = originX)
    val stacked = hinge?.splitOf(vertical = false, extent = heightPx.toFloat(), origin = originY)
    // On a short screen (landscape) the title, with whatever sits under it
    // (people's search), folds away while a section's list is scrolled up and
    // comes back on a scroll down, so the list gets the room (user-requested).
    // Portrait keeps it, as Lumia did.
    val short = heightPx in 1 until widthPx && stacked == null
    var titleHidden by remember { mutableStateOf(false) }
    val titleScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -2f) titleHidden = true
                else if (available.y > 2f) titleHidden = false
                return Offset.Zero
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                widthPx = it.width
                heightPx = it.height
            }
            .onGloballyPositioned {
                val at = it.positionInWindow()
                originX = at.x
                originY = at.y
            }
            .nestedScroll(titleScroll),
    ) {
        val marginPx = with(density) { PANORAMA_MARGIN.toPx() }
        // A long title ("productivity") slides until most of it has shown; a
        // short one still drifts a quarter-screen, as Lumia's did.
        // Beside a hinge the title only has the first screen to run along.
        val titleAreaPx = sideBySide?.first ?: widthPx.toFloat()
        val travelPx = maxOf(titleWidthPx + marginPx - titleAreaPx * 0.7f, titleAreaPx * 0.25f)
        val geometry = panoramaGeometry(widthPx / density.density, sideBySide?.scaled(1f / density.density))
        val pageSize = if (widthPx == 0) PageSize.Fill else PageSize.Fixed(geometry.sectionDp.dp)
        // Title, search and pinned rows keep to the first screen of a side-by-side split.
        val topWidth = if (sideBySide != null) Modifier.width(with(density) { sideBySide.first.toDp() }) else Modifier.fillMaxWidth()
        // Above a horizontal hinge: the title block fills the top screen and the sections start below the hinge.
        val topScreenDp = stacked?.let { with(density) { it.first.toDp() } }
        val hingeDp = stacked?.let { with(density) { it.gap.toDp() } }
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = if (topScreenDp != null) Modifier.fillMaxWidth().heightIn(min = topScreenDp) else Modifier) {
                AnimatedVisibility(
                    visible = !(short && titleHidden),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(modifier = topWidth) {
                        Box(modifier = topWidth.clipToBounds()) {
                            Text(
                                text = title,
                                style = titleStyle,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .wrapContentWidth(Alignment.Start, unbounded = true)
                                    .padding(start = PANORAMA_MARGIN - 4.dp, top = 6.dp)
                                    .graphicsLayer {
                                        val last = (pagerState.pageCount - 1).coerceAtLeast(1)
                                        val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                                        translationX = -(position / last).coerceIn(0f, 1f) * travelPx
                                    },
                            )
                        }
                        belowTitle()
                    }
                }
                Column(modifier = topWidth) { pinned() }
            }
            if (hingeDp != null) Spacer(Modifier.height(hingeDp))
            HorizontalPager(
                state = pagerState,
                pageSize = pageSize,
                pageSpacing = geometry.spacingDp.dp,
                modifier = Modifier.weight(1f),
            ) { index ->
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = sections[index],
                        color = tokens.fg,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .padding(start = PANORAMA_MARGIN, top = 2.dp, bottom = 8.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClickLabel = "show ${sections[index]}",
                            ) { scope.launch { pagerState.animateScrollToPage(index) } },
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) { section(index) }
                }
            }
        }
        // Beside a hinge the title keeps to the first screen, which leaves the top of the second one bare:
        // the section names go there, the ones on show bright, so it is plain that more sections lie
        // beyond and a tap jumps to one.
        if (sideBySide != null && sections.size > 2 * geometry.perScreen) {
            val onShow = pagerState.currentPage until pagerState.currentPage + 2 * geometry.perScreen
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(with(density) { sideBySide.second.toDp() })
                    .horizontalScroll(rememberScrollState())
                    .padding(start = PANORAMA_MARGIN, end = 12.dp, top = 22.dp),
            ) {
                sections.forEachIndexed { i, name ->
                    Text(
                        text = name,
                        color = if (i in onShow) tokens.fg else tokens.fgDim,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "show $name",
                        ) { scope.launch { pagerState.animateScrollToPage(i) } },
                    )
                }
            }
        }
    }
}

/**
 * Beside a hinge, the section on the second screen while [pagerState] is on its
 * current page, or null when the window is whole or no section lies there. A hub
 * uses it to give the second screen its own bottom bar actions
 * ([HubAppBar]'s `rightActions`): the first screen's are for `currentPage`.
 */
@Composable
fun rememberRightScreenPage(pagerState: PagerState): Int? {
    val hinge = LocalHinge.current ?: return null
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val split = hinge.splitOf(vertical = true, extent = window.width.toFloat()) ?: return null
    val perScreen = panoramaGeometry(window.width / density.density, split.scaled(1f / density.density)).perScreen
    return (pagerState.currentPage + perScreen).takeIf { it < pagerState.pageCount }
}
