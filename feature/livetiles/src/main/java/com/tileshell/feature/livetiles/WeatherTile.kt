package com.tileshell.feature.livetiles

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.WeatherTile
import com.tileshell.core.design.LocalTileFaceColor

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

/**
 * Resolves whichever this tile/widget instance is actually configured for —
 * a stored `null` (never configured: a pre-multi-location install, or a
 * genuinely corrupt encoding) transparently means "follow the device", the
 * same behaviour this tile always had before several tiles could each follow
 * a different place (user-requested). Callers never need their own
 * null-handling branch — see [WeatherTileFace]/[WeatherSmallFace].
 */
private fun resolvedLocation(location: WeatherTile.Location?): WeatherTile.Location =
    location ?: WeatherTile.Location.Current

/** The cached snapshot for [location] — [WeatherCacheData.snapshot] for Current, [WeatherCacheData.places] for a Fixed place. */
internal fun WeatherCacheData.snapshotFor(location: WeatherTile.Location): WeatherSnapshot? = when (location) {
    WeatherTile.Location.Current -> snapshot
    is WeatherTile.Location.Fixed -> places[WeatherTile.key(location)]
}

/** How stale the cached forecast may be before waking the device refetches it. */
const val WEATHER_STALE_AFTER_MS: Long = 30 * 60 * 1000L

/**
 * Whether coming back to the launcher should refetch the forecast. Pure, so the
 * precedence is unit-testable.
 *
 * This is the companion to [WeatherRefreshWorker]'s screen-off gate, and exists
 * because that gate alone left a real hole: with the periodic tick suppressed
 * overnight, the next tick after the screen comes back on can be up to a full
 * interval away, so the tile kept showing whatever it had cached before the
 * screen went off. Measured on a real device: the cache was last written at
 * 22:24 and was still being displayed at 06:09 the next morning — a 7h45m-old
 * temperature — with no refresh due for up to another 30 minutes.
 *
 * A never-fetched cache ([fetchedAtMillis] 0) always refetches. A clock that
 * jumped backwards reads as fresh rather than stale, mirroring the feed's own
 * guard, so an NTP/timezone correction can't refetch on every single resume.
 */
fun shouldRefreshWeatherOnWake(
    nowMillis: Long,
    fetchedAtMillis: Long,
    staleAfterMillis: Long = WEATHER_STALE_AFTER_MS,
): Boolean {
    if (fetchedAtMillis <= 0L) return true
    if (fetchedAtMillis > nowMillis) return false
    return nowMillis - fetchedAtMillis >= staleAfterMillis
}

/**
 * Refetches on `ON_RESUME` when what's cached is stale — i.e. when the user comes
 * back to the launcher, which is the moment they can actually see the tile. Uses
 * the shared cache's own `fetchedAtMillis`, so no extra bookkeeping is needed.
 *
 * [rememberUpdatedState] matters here: the observer is registered once, but the
 * cached timestamp changes underneath it, and a plain capture would keep testing
 * the value from first composition forever — the same stale-closure trap this
 * codebase hit before with drag handles.
 */
@Composable
private fun WeatherWakeRefresh(fetchedAtMillis: Long) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(fetchedAtMillis)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                shouldRefreshWeatherOnWake(System.currentTimeMillis(), latest)
            ) {
                WeatherRefreshWorker.refreshNow(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

/**
 * Whether [snapshot]'s place is in night right now (moon instead of sun),
 * re-evaluated every minute so the icon flips at sunset/sunrise on its own —
 * the cached snapshot can be up to a refresh interval old, and nothing else
 * recomposes the face at that moment. False for a null snapshot.
 */
@Composable
fun rememberWeatherNight(snapshot: WeatherSnapshot?): Boolean =
    produceState(initialValue = snapshot?.isNightAt(System.currentTimeMillis()) ?: false, snapshot) {
        while (true) {
            value = snapshot?.isNightAt(System.currentTimeMillis()) ?: false
            delay(60_000L)
        }
    }.value

/**
 * The live weather tile (FR-2). Schedules the background refresh, asks for coarse
 * location once (opt-in) when following the device (a fixed picked place needs no
 * permission), and renders the cached [WeatherSnapshot] for [location] — several
 * weather tiles can each follow a different place at once (user-requested), each
 * reading its own slice of the shared [WeatherCache]. When there is no cached
 * snapshot yet for this instance's location it shows [fallback] (the static
 * glyph), so the tile degrades gracefully. [flipped] turns between current
 * conditions and today's range.
 */
@Composable
fun WeatherTileFace(
    size: TileSize,
    flipped: Boolean,
    location: WeatherTile.Location?,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resolved = resolvedLocation(location)
    LaunchedEffect(Unit) { WeatherRefreshWorker.ensureScheduled(context) }
    val locationGranted = rememberPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
    LaunchedEffect(locationGranted, resolved) {
        if (resolved is WeatherTile.Location.Current && locationGranted) WeatherRefreshWorker.refreshNow(context)
    }

    val cache = remember(context) { WeatherCache.create(context) }
    val snapshot = cache.data.collectAsState(initial = WeatherCacheData()).value.snapshotFor(resolved)
    // Before the fallback return: with nothing cached yet, fetchedAt 0 reads as
    // stale, so a resume is exactly when an empty tile should try to fill itself.
    WeatherWakeRefresh(snapshot?.fetchedAtMillis ?: 0L)
    if (snapshot == null) return fallback()

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { WeatherFront(snapshot, size) },
        back = { WeatherBack(snapshot, size) },
    )
}

/**
 * The compact weather face for a small (1×1) tile: just the current temperature,
 * centred (FR-2). Same data + opt-in as [WeatherTileFace] (shared [WeatherCache],
 * coarse-location ask, background refresh) so a standalone small weather tile
 * still fetches. Never flips; degrades to [fallback] when no snapshot is cached.
 */
@Composable
fun WeatherSmallFace(
    location: WeatherTile.Location?,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resolved = resolvedLocation(location)
    LaunchedEffect(Unit) { WeatherRefreshWorker.ensureScheduled(context) }
    val locationGranted = rememberPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
    LaunchedEffect(locationGranted, resolved) {
        if (resolved is WeatherTile.Location.Current && locationGranted) WeatherRefreshWorker.refreshNow(context)
    }

    val cache = remember(context) { WeatherCache.create(context) }
    val snapshot = cache.data.collectAsState(initial = WeatherCacheData()).value.snapshotFor(resolved)
    // Before the fallback return: with nothing cached yet, fetchedAt 0 reads as
    // stale, so a resume is exactly when an empty tile should try to fill itself.
    WeatherWakeRefresh(snapshot?.fetchedAtMillis ?: 0L)
    if (snapshot == null) return fallback()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = tempLabel(snapshot.tempC),
            color = FaceText,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-1).sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun WeatherFront(snapshot: WeatherSnapshot, size: TileSize) {
    // WIDE and MEDIUM share the same 2-row height (only LARGE's 3 rows have the
    // extra vertical room for the enlarged temperature) — sizing "big" off WIDE
    // clipped the condition line at the bottom of a WIDE stack member.
    val big = size == TileSize.LARGE
    // TALL/COLUMN are only 1 column wide (same as SMALL) — the place/condition
    // lines above clip at that width, so narrow tiles get a centred, width-safe
    // layout instead, spread across whatever row height the tile has.
    val narrow = size.narrowLive
    // WIDE_SMALL/BANNER are the row-axis squeeze instead: plenty of width, but
    // only one grid row tall, so the normal place/temp/condition stack clips
    // its last line unless every size in it shrinks to fit (unlike [narrow],
    // there's no width problem here, so the layout/alignment stays the same
    // left-aligned stack — just smaller text and tighter spacing).
    val short = size.shortLive
    val tempSize = if (narrow) 34.sp else if (short) 26.sp else if (big) 60.sp else 40.sp
    val labelSize = if (narrow) 11.sp else if (short) 10.sp else 13.sp
    val place = snapshot.place.ifBlank { "weather" }
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = if (narrow) Arrangement.SpaceEvenly else Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        // Location name (prototype shows none; the user asked for it).
        Text(
            text = place,
            color = FaceText,
            fontSize = labelSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        if (!narrow && !short) Spacer(Modifier.height(2.dp))
        if (narrow) {
            Text(
                text = tempLabel(snapshot.tempC),
                color = FaceText,
                fontSize = tempSize,
                lineHeight = tempSize * 0.9f,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        } else {
            // A richer multi-element illustration beside the temperature
            // (user-requested, matching the home-screen widget's own
            // WeatherConditionVisual) instead of no icon at all.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tempLabel(snapshot.tempC),
                    color = FaceText,
                    fontSize = tempSize,
                    lineHeight = tempSize * 0.9f,
                    fontWeight = FontWeight.ExtraLight,
                    letterSpacing = (-2).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                WeatherConditionVisual(
                    condition = snapshot.condition,
                    tint = FaceText,
                    night = rememberWeatherNight(snapshot),
                    modifier = Modifier.size(if (short) 22.dp else if (big) 40.dp else 30.dp),
                )
            }
        }
        if (!narrow && !short) Spacer(Modifier.height(4.dp))
        Text(
            text = snapshot.condition,
            color = FaceText,
            fontSize = labelSize,
            maxLines = if (narrow) 2 else 1,
            overflow = if (narrow) TextOverflow.Ellipsis else TextOverflow.Clip,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}

@Composable
private fun WeatherBack(snapshot: WeatherSnapshot, size: TileSize) {
    val narrow = size.narrowLive
    val short = size.shortLive
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = if (narrow) Arrangement.SpaceEvenly else Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = snapshot.place.ifBlank { "today" },
            color = FaceText.copy(alpha = 0.9f),
            fontSize = if (narrow) 11.sp else if (short) 10.sp else 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        if (!narrow && !short) Spacer(Modifier.height(6.dp))
        Text(
            text = highLowLabel(snapshot.highC, snapshot.lowC),
            color = FaceText,
            fontSize = if (narrow) 18.sp else if (short) 15.sp else 22.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            overflow = if (narrow) TextOverflow.Ellipsis else TextOverflow.Clip,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        // Pushes the detail line + sun times down to the tile's bottom edge.
        if (!narrow && !short) Spacer(Modifier.weight(1f))
        if (snapshot.detail.isNotEmpty()) {
            Text(
                text = snapshot.detail,
                color = FaceText.copy(alpha = 0.82f),
                fontSize = if (narrow) 11.sp else if (short) 10.sp else 12.sp,
                maxLines = if (narrow) 3 else 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
        }
        // The 7-day outlook (user-requested, matching the home-screen
        // widget's back face) — only at LARGE, the one size with room for it
        // beyond today's own high/low + detail.
        if (size == TileSize.LARGE && snapshot.forecast.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(snapshot.forecast) { day ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(day.dayLabel.take(3), color = FaceText.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 1)
                        Text(highLowLabel(day.highC, day.lowC), color = FaceText, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
        // Today's sunrise/sunset as the bottom line (user-requested) — skipped
        // on the 1-column and 1-row sizes, which have no room for it.
        if (!narrow && !short) {
            SunTimesRow(snapshot = snapshot, color = FaceText, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
