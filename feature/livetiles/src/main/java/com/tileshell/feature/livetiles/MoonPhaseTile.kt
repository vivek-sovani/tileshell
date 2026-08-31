package com.tileshell.feature.livetiles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.LocalTileFaceColor
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A synodic month, in days — the average time from one new moon to the next.
 * Internal (not private) so [MoonPhaseTest] can build exact round-trip cases.
 */
internal const val SYNODIC_MONTH_DAYS = 29.530588853

/**
 * Days from the Unix epoch (1970-01-01 00:00 UTC) to a known new moon
 * (2000-01-06 18:14 UTC) — the reference point every phase below is measured
 * against. Off by at most a few hours from the true instant, which is well
 * inside the ~1 day resolution this tile actually displays.
 */
internal const val REFERENCE_NEW_MOON_EPOCH_DAY = 10962.759722

/** The 8 canonical phases, matching the standard 8-phase moon chart. */
enum class MoonPhaseName(val label: String) {
    NEW("new moon"),
    WAXING_CRESCENT("waxing crescent"),
    FIRST_QUARTER("first quarter"),
    WAXING_GIBBOUS("waxing gibbous"),
    FULL("full moon"),
    WANING_GIBBOUS("waning gibbous"),
    LAST_QUARTER("last quarter"),
    WANING_CRESCENT("waning crescent"),
}

/**
 * Position in the current synodic cycle, 0f = new moon, 0.5f = full moon,
 * wrapping back to 1f = the next new moon. Pure date math — no location, no
 * network, no permission needed at all.
 */
fun moonPhaseFraction(epochDay: Double): Double {
    val cycles = (epochDay - REFERENCE_NEW_MOON_EPOCH_DAY) / SYNODIC_MONTH_DAYS
    val frac = cycles - floor(cycles)
    return frac
}

/**
 * Buckets [fraction] into one of the 8 named phases, each spanning 1/8 of the
 * cycle centred on its exact point (e.g. "full moon" covers the 1/16 either
 * side of fraction 0.5) — the same convention a printed 8-phase moon chart uses.
 */
fun moonPhaseName(fraction: Double): MoonPhaseName {
    val segment = floor(fraction * 8 + 0.5).toInt().mod(8)
    return MoonPhaseName.entries[segment]
}

/** Percent of the disc illuminated, via the standard cosine approximation. */
fun moonIllumination(fraction: Double): Int {
    val illum = (1 - cos(2 * PI * fraction)) / 2
    return (illum * 100).roundToInt()
}

private fun daysUntilFraction(fraction: Double, target: Double): Int {
    val remaining = ((target - fraction) + 1.0).mod(1.0)
    val days = remaining * SYNODIC_MONTH_DAYS
    // Floating-point noise around an exact target (fraction landing a few
    // billionths short of it) would otherwise ceil up to 1 instead of reading
    // as "today" — a fraction of a day either side of the target still counts
    // as today.
    if (days < 0.01) return 0
    return ceil(days).toInt()
}

/**
 * The text (and [fraction], for drawing [MoonPhaseVisual]) shown on the
 * moon-phase tile's two faces.
 */
data class MoonPhaseFace(
    val name: String,
    val illuminationPercent: Int,
    val nextEventLabel: String,
    val fraction: Double,
)

/** Pure — takes the day number directly, so this is fully unit-testable. */
fun moonPhaseFace(epochDay: Double): MoonPhaseFace {
    val fraction = moonPhaseFraction(epochDay)
    val toFull = daysUntilFraction(fraction, 0.5)
    val toNew = daysUntilFraction(fraction, 0.0)
    val nextEventLabel = when {
        toFull == 0 -> "full moon today"
        toNew == 0 -> "new moon today"
        toFull <= toNew -> "full moon in ${toFull}d"
        else -> "new moon in ${toNew}d"
    }
    return MoonPhaseFace(
        name = moonPhaseName(fraction).label,
        illuminationPercent = moonIllumination(fraction),
        nextEventLabel = nextEventLabel,
        fraction = fraction,
    )
}

private fun currentMoonPhaseFace(): MoonPhaseFace =
    moonPhaseFace(System.currentTimeMillis() / 86_400_000.0)

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

/**
 * The live moon-phase tile: front shows tonight's phase name + illumination,
 * back shows the next full/new moon countdown. Entirely local date math — no
 * location, no permission, no network — so unlike every other flippable face
 * in this file there's nothing to gate on a granted permission; it only ticks
 * on the minute boundary while [active] to stay cheap and consistent with the
 * rest of the flip scheduler, even though the phase itself only meaningfully
 * changes about once a day.
 */
@Composable
fun MoonPhaseTileFace(
    size: TileSize,
    flipped: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    var face by remember { mutableStateOf(currentMoonPhaseFace()) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            face = currentMoonPhaseFace()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { MoonPhaseFront(face, size) },
        back = { MoonPhaseBack(face, size) },
    )
}

@Composable
private fun MoonPhaseFront(face: MoonPhaseFace, size: TileSize) {
    val narrow = size.narrowLive
    val short = size.shortLive
    val big = size == TileSize.LARGE
    val visualSize = if (short) 26.dp else if (narrow) 32.dp else if (big) 60.dp else 42.dp

    val textColumn = @Composable {
        Column(horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start) {
            Text(
                text = face.name,
                color = FaceText,
                fontSize = if (short) 14.sp else if (narrow) 16.sp else if (big) 26.sp else 18.sp,
                lineHeight = if (short) 16.sp else if (narrow) 18.sp else if (big) 28.sp else 20.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.5).sp,
                maxLines = 2,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
            Text(
                text = "${face.illuminationPercent}% lit",
                color = FaceText.copy(alpha = 0.82f),
                fontSize = if (short) 10.sp else if (narrow) 12.sp else 14.sp,
                maxLines = 1,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
        }
    }

    if (narrow) {
        // Only one column wide — stack the visual above the text instead of
        // beside it, spread across whatever row height the tile has.
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MoonPhaseVisual(fraction = face.fraction, modifier = Modifier.size(visualSize))
            textColumn()
        }
    } else {
        Row(
            modifier = Modifier.fillMaxSize().padding(if (short) 4.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (short) 8.dp else 12.dp),
        ) {
            MoonPhaseVisual(fraction = face.fraction, modifier = Modifier.size(visualSize))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                textColumn()
                if (big) {
                    Spacer(Modifier.height(8.dp))
                    Text("moon phase", color = FaceText.copy(alpha = 0.82f), fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Draws tonight's moon disc: a lit crescent/gibbous/half/full shape against a
 * dim shadow, built from two half-ellipses (the classic 2-arc moon-phase
 * render) rather than a bitmap — matches [FaceText] so it reads correctly on
 * any tile accent, light or dark theme.
 *
 * The lit side is the right half for the waxing half of the cycle ([fraction]
 * < 0.5) and the left half for the waning half; a second half-ellipse of
 * horizontal radius `|cos(2π·fraction)|` either adds to it (gibbous) or cuts
 * into it (crescent) on the appropriate side, so the two half-ellipses alone
 * reproduce new / crescent / quarter / gibbous / full exactly at their
 * canonical fractions (0, ~0.15, 0.25, ~0.35, 0.5, …).
 */
/** Widened to internal so [CalendarSystemTile]'s Hindu Panchang face can reuse the same crescent — tithi is fundamentally a lunar-phase measure, so the two are drawn identically. */
@Composable
internal fun MoonPhaseVisual(fraction: Double, modifier: Modifier = Modifier) {
    val lit = FaceText
    val shadow = FaceText.copy(alpha = 0.18f)
    val rim = FaceText.copy(alpha = 0.4f)
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)

        fun halfEllipsePath(rx: Float, rightSide: Boolean): Path = Path().apply {
            moveTo(cx, cy - r)
            arcTo(
                rect = Rect(cx - rx, cy - r, cx + rx, cy + r),
                startAngleDegrees = -90f,
                sweepAngleDegrees = if (rightSide) 180f else -180f,
                forceMoveTo = false,
            )
            close()
        }

        val litRight = fraction < 0.5
        val cosVal = cos(2 * PI * fraction).toFloat()
        val rx = kotlin.math.abs(cosVal) * r
        val isGibbous = cosVal < 0f

        drawCircle(color = shadow, radius = r, center = center)
        drawPath(halfEllipsePath(r, litRight), color = lit)
        drawPath(halfEllipsePath(rx, if (isGibbous) !litRight else litRight), color = if (isGibbous) lit else shadow)
        drawCircle(color = rim, radius = r, center = center, style = Stroke(width = 1.dp.toPx()))
    }
}

@Composable
private fun MoonPhaseBack(face: MoonPhaseFace, size: TileSize) {
    val narrow = size.narrowLive
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow) 4.dp else 11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = "moon phase",
            color = FaceText,
            fontSize = if (narrow) 18.sp else 22.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.5).sp,
            maxLines = if (narrow) 2 else 1,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = face.nextEventLabel,
            color = FaceText.copy(alpha = 0.65f),
            fontSize = if (narrow) 11.sp else 13.sp,
            maxLines = 1,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}
