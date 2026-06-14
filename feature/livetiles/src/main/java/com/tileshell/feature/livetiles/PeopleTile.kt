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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
 * mosaic of contact avatars — 2×2 at medium, 4×2 at wide/large (prototype
 * `liveFace('people')`). While [active], one random cell cross-fades to a
 * different contact every ~2.1 s (the prototype `peopleStep`). The back face is a
 * single large avatar with "<name> posted". When the permission is denied or
 * there are no contacts it renders [fallback] (the static glyph).
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

    val big = size == TileSize.WIDE || size == TileSize.LARGE
    val cols = if (big) 4 else 2
    val rows = 2
    val cellCount = cols * rows

    val cells = remember(people, cellCount) {
        mutableStateListOf<Person>().apply { addAll(mosaicCells(people, cellCount)) }
    }
    LaunchedEffect(active, people, cellCount) {
        if (!active || people.size < 2) return@LaunchedEffect
        while (true) {
            delay(CELL_REFRESH_MS)
            val i = Random.nextInt(cells.size)
            cells[i] = people[Random.nextInt(people.size)]
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

/** One avatar cell: the contact photo cropped to fill, or initials on a tint. */
@Composable
private fun Avatar(person: Person, big: Boolean) {
    val photo = person.photoUri
    if (photo != null) {
        val bitmap = rememberTileBitmap(photo, targetPx = if (big) 300 else 120)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(colorFor(person.name)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials(person.name),
            color = FaceText,
            fontSize = if (big) 30.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
