package com.tileshell.feature.livetiles

import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.tileshell.core.data.TileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

private const val BUBBLE_REFRESH_MS = 4_500L

/** A bubble's fixed size and starting position, as a fraction of the tile — physics takes over from there. */
private data class CircleSlot(val cx: Float, val cy: Float, val d: Float)

/** Five varied-size starting points for the wide (~2:1) tile. */
private val WIDE_SLOTS = listOf(
    CircleSlot(0.16f, 0.32f, 0.60f),
    CircleSlot(0.40f, 0.80f, 0.38f),
    CircleSlot(0.62f, 0.26f, 0.44f),
    CircleSlot(0.85f, 0.66f, 0.54f),
    CircleSlot(0.58f, 0.88f, 0.26f),
)

/** Four varied-size starting points for the square-ish (~1:1) medium/large tile. */
private val COMPACT_SLOTS = listOf(
    CircleSlot(0.30f, 0.30f, 0.60f),
    CircleSlot(0.80f, 0.22f, 0.34f),
    CircleSlot(0.22f, 0.80f, 0.30f),
    CircleSlot(0.76f, 0.76f, 0.44f),
)

/** A bubble's live physics state — position is observable so only that bubble's own offset recomposes each frame. */
private class BubbleSim(x0: Float, y0: Float, var vx: Float, var vy: Float, val radius: Float) {
    var x by mutableFloatStateOf(x0)
    var y by mutableFloatStateOf(y0)
}

/**
 * The live people tile (FR-2). Asks for READ_CONTACTS once (opt-in), then shows a
 * cluster of circular *profile photos* of the user's **favourite + frequently-
 * contacted** contacts only — five bubbles at wide, four at medium/large, each a
 * different size, drifting and bouncing off each other and the tile edges (a
 * small elastic physics sim, not a fixed grid or fixed slots — user-requested,
 * see DECISIONS.md). Only contacts that have a photo are used (no initials
 * avatars). While [active], each bubble independently swaps to a different
 * contact every ~4.5 s (staggered per bubble, instant cut — no fade/crossfade;
 * the animation here is the bubbles' own motion, not the photo transition). No
 * flip — this tile never turns to a back face. When the permission is denied or
 * no favourite contact has a photo it renders [fallback] (the static glyph).
 */
@Composable
fun PeopleTileFace(
    size: TileSize,
    active: Boolean,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val granted = rememberPermissionGranted(Manifest.permission.READ_CONTACTS)

    var people by remember { mutableStateOf<List<Person>>(emptyList()) }
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        people = runCatching { withContext(Dispatchers.IO) { queryContacts(context) } }
            .getOrElse { emptyList() }
    }
    if (!granted || people.isEmpty()) return fallback()

    val slots = if (size == TileSize.WIDE) WIDE_SLOTS else COMPACT_SLOTS
    // Seed each bubble in queryContacts' order — favourites + frequently-contacted
    // only — same distinct-coverage cycling mosaicCells already provided for the
    // old grid; each bubble's own timer takes over swapping from here.
    val initial = remember(people, slots.size) { mosaicCells(people, slots.size) }

    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val minDimPx = minOf(widthPx, heightPx)
        val speed = minDimPx * 0.18f // px/sec — gentle drift, not a pinball

        val bubbles = remember(widthPx, heightPx, slots) {
            slots.map { slot ->
                val angle = Random.nextFloat() * (2f * Math.PI).toFloat()
                BubbleSim(
                    x0 = widthPx * slot.cx,
                    y0 = heightPx * slot.cy,
                    vx = kotlin.math.cos(angle) * speed,
                    vy = kotlin.math.sin(angle) * speed,
                    radius = minDimPx * slot.d / 2f,
                )
            }
        }

        LaunchedEffect(bubbles, active) {
            if (!active) return@LaunchedEffect
            var lastNanos = 0L
            while (true) {
                val nanos = withFrameNanos { it }
                if (lastNanos == 0L) {
                    lastNanos = nanos
                } else {
                    val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    lastNanos = nanos
                    stepBubbleSimulation(bubbles, widthPx, heightPx, dt)
                }
            }
        }

        slots.forEachIndexed { index, _ ->
            val bubble = bubbles[index]
            val diameterDp = with(density) { (bubble.radius * 2f).toDp() }
            PeopleBubble(
                people = people,
                initial = initial[index],
                active = active,
                seed = index,
                modifier = Modifier
                    .size(diameterDp)
                    .offset {
                        IntOffset(
                            (bubble.x - bubble.radius).roundToInt(),
                            (bubble.y - bubble.radius).roundToInt(),
                        )
                    },
            )
        }
    }
}

/**
 * Advances every bubble's position by [dt] seconds, bounces them off the tile
 * edges, and resolves bubble-bubble overlaps with a simple equal-mass elastic
 * collision (exchange the velocity component along the collision normal). A
 * touch of damping (0.94x) on every bounce keeps the cluster settling into a
 * gentle drift rather than accelerating forever.
 */
private fun stepBubbleSimulation(bubbles: List<BubbleSim>, widthPx: Float, heightPx: Float, dt: Float) {
    for (b in bubbles) {
        b.x += b.vx * dt
        b.y += b.vy * dt

        if (b.x - b.radius < 0f) {
            b.x = b.radius
            b.vx = -b.vx * 0.94f
        } else if (b.x + b.radius > widthPx) {
            b.x = widthPx - b.radius
            b.vx = -b.vx * 0.94f
        }
        if (b.y - b.radius < 0f) {
            b.y = b.radius
            b.vy = -b.vy * 0.94f
        } else if (b.y + b.radius > heightPx) {
            b.y = heightPx - b.radius
            b.vy = -b.vy * 0.94f
        }
    }

    for (i in bubbles.indices) {
        for (j in (i + 1) until bubbles.size) {
            val a = bubbles[i]
            val c = bubbles[j]
            val dx = c.x - a.x
            val dy = c.y - a.y
            val dist = sqrt(dx * dx + dy * dy)
            val minDist = a.radius + c.radius
            if (dist > 0f && dist < minDist) {
                val nx = dx / dist
                val ny = dy / dist
                // Separate so they no longer overlap.
                val overlap = minDist - dist
                a.x -= nx * overlap / 2f
                a.y -= ny * overlap / 2f
                c.x += nx * overlap / 2f
                c.y += ny * overlap / 2f
                // Exchange the velocity component along the collision normal.
                val avn = a.vx * nx + a.vy * ny
                val cvn = c.vx * nx + c.vy * ny
                a.vx += (cvn - avn) * nx * 0.94f
                a.vy += (cvn - avn) * ny * 0.94f
                c.vx += (avn - cvn) * nx * 0.94f
                c.vy += (avn - cvn) * ny * 0.94f
            }
        }
    }
}

@Composable
private fun PeopleBubble(
    people: List<Person>,
    initial: Person,
    active: Boolean,
    seed: Int,
    modifier: Modifier,
) {
    var person by remember(initial) { mutableStateOf(initial) }
    // Staggered per-bubble timer (offset by [seed]) so bubbles swap photos at
    // different moments — an instant cut, not a fade, so the only animation is
    // the bubble's own motion.
    LaunchedEffect(active, people) {
        if (!active || people.size <= 1) return@LaunchedEffect
        delay(300L + seed * 260L)
        while (true) {
            delay(BUBBLE_REFRESH_MS)
            val candidates = people.filter { it != person }
            if (candidates.isEmpty()) continue
            person = candidates.random()
        }
    }

    Box(modifier = modifier.clip(CircleShape)) {
        Avatar(person)
    }
}

/**
 * One bubble's photo. Always a full-bleed circular crop — the parent already
 * clips to [CircleShape], so this just fills it. While the photo decodes (or if
 * its URI is briefly unreadable) it shows a plain colour tint — never initials,
 * per the photos-only requirement.
 */
@Composable
private fun Avatar(person: Person) {
    val bitmap = rememberTileBitmap(person.photoUri, targetPx = 160)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = person.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().background(colorFor(person.name)))
    }
}
