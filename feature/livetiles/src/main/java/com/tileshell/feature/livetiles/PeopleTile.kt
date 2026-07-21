package com.tileshell.feature.livetiles

import android.Manifest
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.tileshell.core.data.TileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val BUBBLE_REFRESH_MS = 2_100L

/** One bubble's relative position/size within the tile — [cx]/[cy] = centre fraction of width/height, [d] = diameter as a fraction of the tile's shorter side. */
private data class CircleSlot(val cx: Float, val cy: Float, val d: Float)

/** Scattered cluster for the wide (~2:1) tile — five bubbles of varied size. */
private val WIDE_SLOTS = listOf(
    CircleSlot(0.16f, 0.32f, 0.60f),
    CircleSlot(0.40f, 0.80f, 0.38f),
    CircleSlot(0.62f, 0.26f, 0.44f),
    CircleSlot(0.85f, 0.66f, 0.54f),
    CircleSlot(0.58f, 0.88f, 0.26f),
)

/** Scattered cluster for the square-ish (~1:1) medium/large tile — four bubbles of varied size. */
private val COMPACT_SLOTS = listOf(
    CircleSlot(0.30f, 0.30f, 0.60f),
    CircleSlot(0.80f, 0.22f, 0.34f),
    CircleSlot(0.22f, 0.80f, 0.30f),
    CircleSlot(0.76f, 0.76f, 0.44f),
)

/**
 * The live people tile (FR-2). Asks for READ_CONTACTS once (opt-in), then shows a
 * scattered cluster of circular *profile photos* of the user's **favourite +
 * frequently-contacted** contacts only — five bubbles at wide, four at
 * medium/large, each a different size (deliberately not a uniform grid). Only
 * contacts that have a photo are used (no initials avatars). While [active],
 * each bubble independently cross-fades to a different contact every ~2.1 s
 * (staggered per bubble so they don't all swap in lockstep) with a small bounce
 * pop on every swap. No flip — this tile never turns to a back face (see
 * DECISIONS.md: user-requested removal of the flip in favour of the bubble
 * cluster). When the permission is denied or no favourite contact has a photo
 * it renders [fallback] (the static glyph).
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val minDim = minOf(maxWidth, maxHeight)
        slots.forEachIndexed { index, slot ->
            val diameter = minDim * slot.d
            PeopleBubble(
                people = people,
                initial = initial[index],
                active = active,
                seed = index,
                modifier = Modifier
                    .size(diameter)
                    .offset(
                        x = maxWidth * slot.cx - diameter / 2,
                        y = maxHeight * slot.cy - diameter / 2,
                    ),
            )
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
    // different moments instead of all flipping together like a uniform grid.
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

    // A small bounce pop on every swap (including the first paint) — the
    // "animations" half of the bubble-cluster look, independent of the photo
    // cross-fade itself.
    val scale = remember { Animatable(0.82f) }
    LaunchedEffect(person) {
        scale.snapTo(0.82f)
        scale.animateTo(
            1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        )
    }

    Box(modifier = modifier.scale(scale.value).clip(CircleShape)) {
        Crossfade(targetState = person, animationSpec = tween(320), label = "bubblePhoto") { p -> Avatar(p) }
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
