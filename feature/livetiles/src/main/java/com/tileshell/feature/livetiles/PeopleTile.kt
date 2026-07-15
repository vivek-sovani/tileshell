package com.tileshell.feature.livetiles

import android.Manifest
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

private val FaceText = Color.White
private const val CELL_REFRESH_MS = 2_100L

/**
 * The live people tile (FR-2). Asks for READ_CONTACTS once (opt-in), then shows a
 * mosaic of *profile photos* of the user's **favourite + frequently-contacted**
 * contacts only — 2×2 at medium, 4×2 at wide (prototype `liveFace('people')`).
 * Only contacts that have a photo are used (no initials avatars). While [active],
 * one random cell cross-fades to a different contact every ~2.1 s (the prototype
 * `peopleStep`). The back face is a single large photo with "<name> posted". When
 * the permission is denied or no favourite contact has a photo it renders
 * [fallback] (the static glyph).
 */
@Composable
fun PeopleTileFace(
    size: TileSize,
    flipped: Boolean,
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

    val big = size == TileSize.WIDE
    val cols = if (big) 4 else 2
    val rows = 2
    val cellCount = cols * rows

    // Seed the mosaic in queryContacts' order — favourites + frequently-contacted
    // only — so the tile shows just the user's important people; the rotation below
    // cycles among them for liveliness.
    val cells = remember(people, cellCount) {
        mutableStateListOf<Person>().apply { addAll(mosaicCells(people, cellCount)) }
    }
    // Rotate in a contact that is not already on screen, so the mosaic never shows
    // the same photo twice. Only meaningful when there are more contacts than cells
    // (otherwise every contact is already shown and there is nothing to swap in).
    LaunchedEffect(active, people, cellCount) {
        if (!active || people.size <= cellCount) return@LaunchedEffect
        while (true) {
            delay(CELL_REFRESH_MS)
            val offscreen = people.filter { it !in cells }
            if (offscreen.isEmpty()) continue
            cells[Random.nextInt(cells.size)] = offscreen[Random.nextInt(offscreen.size)]
        }
    }

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { MosaicGrid(cells, cols, rows) },
        back = { PeopleBack(people[2 % people.size]) },
    )
}

@Composable
private fun MosaicGrid(cells: List<Person>, cols: Int, rows: Int) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (r in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (c in 0 until cols) {
                    val person = cells.getOrNull(r * cols + c)
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        if (person != null) {
                            // Cross-fade the cell when the scheduler swaps its contact.
                            Crossfade(
                                targetState = person,
                                animationSpec = tween(300),
                                label = "cell",
                            ) { p -> Avatar(p, big = false) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleBack(person: Person) {
    Box(modifier = Modifier.fillMaxSize()) {
        Avatar(person, big = true)
        Text(
            text = "${person.name.substringBefore(' ')} posted",
            color = FaceText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 11.dp, bottom = 9.dp),
        )
    }
}

/**
 * One avatar cell. The back face ([big]) is a single full-bleed photo behind the
 * "posted" caption, like a photo post. Mosaic cells (front face, ![big]) instead
 * show the contact's photo as a circular avatar chip, inset within the square cell
 * so the tile's own fill shows through the corners — the familiar round contact-photo
 * look, rather than a grid of square crops (a deliberate deviation from the
 * prototype's square avatars — user-requested, see DECISIONS.md). While the photo
 * decodes (or if its URI is briefly unreadable) the shape shows a plain colour tint
 * — never initials, per the photos-only requirement.
 */
@Composable
private fun Avatar(person: Person, big: Boolean) {
    val bitmap = rememberTileBitmap(person.photoUri, targetPx = if (big) 300 else 120)
    val shape = if (big) RectangleShape else CircleShape
    val shapeModifier = if (big) {
        Modifier.fillMaxSize()
    } else {
        Modifier.fillMaxSize().padding(3.dp).clip(shape)
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = person.name,
            contentScale = ContentScale.Crop,
            modifier = shapeModifier,
        )
    } else {
        Box(modifier = shapeModifier.background(colorFor(person.name)))
    }
}
