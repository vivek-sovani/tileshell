package com.tileshell.feature.livetiles

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tileshell.core.data.HINDU_PANCHANG_ID
import com.tileshell.core.data.HinduPanchang
import com.tileshell.core.data.Paksha
import com.tileshell.core.data.PanchangDevanagari
import com.tileshell.core.data.PanchangInfo
import com.tileshell.core.data.SunTimes
import com.tileshell.core.data.SunTimesInfo
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.calendarSystemFor
import com.tileshell.core.data.formatRomanDate
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

// India's rough geographic centre — used only as a last-resort fallback when
// no real location is available at all (see [lastCoarseLocationOrDefault]),
// so the Panchang's sunrise/sunset line always shows a plausible time
// instead of degrading to blank. It's a coarse country-wide average, not a
// specific city — real sunrise/sunset for a location like Pune can be off by
// ~15-20 minutes from what this centre would compute, so it's only ever used
// once both a cached fix and a fresh fix attempt (below) come up empty.
private const val DEFAULT_LATITUDE = 20.5937
private const val DEFAULT_LONGITUDE = 78.9629

/** Bound on the one-time fresh-fix request in [lastCoarseLocationOrDefault], so a cold-start caller is never blocked long. */
private const val LOCATION_FIX_TIMEOUT_MS = 8_000L

/**
 * Best-effort device latitude/longitude for [SunTimes], strongly preferring
 * the user's real location over [DEFAULT_LATITUDE]/[DEFAULT_LONGITUDE].
 * First tries whatever's already cached by the OS (the same granted
 * coarse-location permission + last-known-fix technique the weather tile
 * already uses — [WeatherRefreshWorker]'s own `lastCoarseLocation`); when
 * nothing is cached yet — the common case right after a fresh install, since
 * nothing else may have ever asked the network-location provider for a fix —
 * this makes one bounded, single-shot request for a fresh network-location
 * fix (no new permission: `NETWORK_PROVIDER` only needs the coarse grant
 * already checked below) rather than silently falling back to a country-wide
 * average immediately. Falls back to the default only when both come up
 * empty (permission denied, no provider enabled, or no fix within the
 * timeout). Internal (not private) — the home-screen calendar-system
 * widget's own refresh worker reuses this exact function rather than
 * duplicating it.
 */
internal suspend fun lastCoarseLocationOrDefault(context: Context): Pair<Double, Double> {
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!granted) return DEFAULT_LATITUDE to DEFAULT_LONGITUDE

    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return DEFAULT_LATITUDE to DEFAULT_LONGITUDE

    val cached = runCatching {
        lm.getProviders(true).asSequence()
            .mapNotNull { lm.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
    }.getOrNull()
    if (cached != null) return cached.latitude to cached.longitude

    val fresh = withTimeoutOrNull(LOCATION_FIX_TIMEOUT_MS) { requestSingleNetworkFix(lm) }
    if (fresh != null) return fresh.latitude to fresh.longitude

    return DEFAULT_LATITUDE to DEFAULT_LONGITUDE
}

/** One bounded `NETWORK_PROVIDER` fix, resumed at most once; cancellation/timeout unregisters the listener. */
private suspend fun requestSingleNetworkFix(lm: LocationManager): Location? = suspendCancellableCoroutine { cont ->
    if (!runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
        cont.resume(null)
        return@suspendCancellableCoroutine
    }
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (cont.isActive) cont.resume(location)
        }
        @Deprecated("Deprecated in Java", ReplaceWith(""))
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {
            if (cont.isActive) cont.resume(null)
        }
    }
    runCatching {
        lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
    }.onFailure { if (cont.isActive) cont.resume(null) }
    cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
}

/**
 * The selected system's date, as text — every system but [HINDU_PANCHANG_ID]
 * (which renders its own richer [PanchangFace] instead) via `android.icu`,
 * whose date formatter renders straight into an alternate calendar when the
 * locale carries a `ca` (calendar) keyword (e.g. `ca=islamic`) — the same
 * mechanism used for every other supported system, so no per-system
 * formatting code beyond the keyword lookup.
 */
internal fun formatSelectedSystemDate(systemId: String, epochMillis: Long): String {
    val keyword = calendarSystemFor(systemId)?.icuCalendarKeyword ?: return ""
    return runCatching {
        val locale = Locale.Builder().setLanguage("en").setUnicodeLocaleKeyword("ca", keyword).build()
        val format = android.icu.text.DateFormat.getDateInstance(android.icu.text.DateFormat.FULL, locale)
        format.format(java.util.Date(epochMillis)).lowercase(Locale.ENGLISH)
    }.getOrDefault("")
}

/**
 * The "calendar systems" tile: front shows today's date in whichever single
 * system the user picked (Hindu Panchang, Islamic, Hebrew, ...), back always
 * shows the Roman (Gregorian) date — both in full text, not just numbers, so
 * a system like Hindu Panchang reads as "krishna paksha · ekadashi" rather
 * than a bare day count. No network, no permission — pure on-device date
 * math — so unlike the weather/stock/sports tiles there's nothing to poll;
 * it just re-renders once a minute so the date rolls over at midnight while
 * the tile is on screen.
 *
 * Hindu Panchang is the one exception to "front = system, back = Roman":
 * per explicit request it flips between the *same* Panchang in Devanagari
 * script (front) and in English/transliterated (back), with the Roman date
 * appended as a bottom line on *both* faces instead of getting a dedicated
 * all-Roman back face — the other systems have no script duality, so they
 * keep the original front/back split.
 */
@Composable
fun CalendarSystemTileFace(
    size: TileSize,
    flipped: Boolean,
    active: Boolean,
    systemId: String?,
    modifier: Modifier = Modifier,
) {
    if (systemId == null || calendarSystemFor(systemId) == null) {
        NoCalendarSystemPickedFace(size, modifier)
        return
    }

    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    val romanDate = formatRomanDate(nowMillis)

    if (systemId == HINDU_PANCHANG_ID) {
        val panchang = HinduPanchang.panchangFor(nowMillis)
        // Resolved once per tile instance (location doesn't meaningfully
        // change minute to minute) — cheap if a fix is already cached by the
        // OS, else a bounded on-device location request (see
        // lastCoarseLocationOrDefault). sunTimes is recomputed every minute
        // (keyed on nowMillis, which the ticker above already updates) —
        // needed because nextSunriseSunset rolls sunrise/sunset forward to
        // tomorrow the instant each one passes, not just at the date
        // rollover; still cheap, pure local trig, no network.
        val context = LocalContext.current
        val location by produceState(initialValue = DEFAULT_LATITUDE to DEFAULT_LONGITUDE, context) {
            value = lastCoarseLocationOrDefault(context)
        }
        val sunTimes = remember(nowMillis, location) {
            SunTimes.nextSunriseSunset(nowMillis, location.first, location.second)
        }
        FlipTile(
            flipped = flipped,
            modifier = modifier.fillMaxSize(),
            front = { PanchangFace(panchang = panchang, size = size, romanDate = romanDate, devanagari = true, sunTimes = sunTimes) },
            back = { PanchangFace(panchang = panchang, size = size, romanDate = romanDate, devanagari = false, sunTimes = sunTimes) },
        )
        return
    }

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = {
            val systemName = calendarSystemFor(systemId)?.displayName.orEmpty()
            CalendarSystemFace(label = systemName, dateText = formatSelectedSystemDate(systemId, nowMillis), size = size)
        },
        back = { CalendarSystemFace(label = "roman calendar", dateText = romanDate, size = size) },
    )
}

@Composable
private fun NoCalendarSystemPickedFace(size: TileSize, modifier: Modifier) {
    val narrow = size.narrowLive
    val short = size.shortLive
    Column(
        modifier = modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = "tap to choose a calendar system…",
            color = FaceText.copy(alpha = 0.82f),
            fontSize = if (short) 12.sp else 14.sp,
            maxLines = if (narrow) 4 else 2,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}

@Composable
private fun CalendarSystemFace(label: String, dateText: String, size: TileSize) {
    val narrow = size.narrowLive
    val short = size.shortLive
    val big = size == TileSize.LARGE
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = if (narrow) Arrangement.SpaceEvenly else Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = label,
            color = FaceText.copy(alpha = 0.7f),
            fontSize = if (short) 10.sp else 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        Text(
            text = dateText.ifBlank { "no data" },
            color = FaceText,
            fontSize = if (short) 13.sp else if (narrow) 14.sp else if (big) 20.sp else 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = if (big) 4 else if (narrow) 3 else 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}

/**
 * The [MoonPhaseVisual] fraction (0 = new moon, 0.5 = full moon) implied by a
 * specific tithi, rather than [moonPhaseFraction]'s generic epoch-day
 * approximation — the two would occasionally disagree by up to half a tithi
 * near a cycle boundary, drawing a crescent that doesn't quite match the tithi
 * name next to it. Exact by construction: each of the 30 tithis is defined as
 * a fixed 12° band of the Moon-Sun elongation ([HinduPanchang.tithiFromElongation]),
 * the same angle a phase fraction is measured from (`elongation / 360`), so
 * this just inverts that mapping using the tithi's own band midpoint.
 */
internal fun tithiMoonFraction(paksha: Paksha, tithiInPaksha: Int): Double {
    val tithiIndex = if (paksha == Paksha.SHUKLA) tithiInPaksha - 1 else tithiInPaksha - 1 + 15
    return (tithiIndex + 0.5) / 30.0
}

/**
 * "6:12 am"-style 12-hour clock formatting, matching [clockFace]'s own
 * convention. Internal (not private) — the home-screen calendar-system
 * widget's own refresh worker reuses this for the exact same formatting.
 */
internal fun formatClockTime12(epochMillis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    val hour24 = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val minute = cal.get(java.util.Calendar.MINUTE)
    val hour12 = (hour24 % 12).let { if (it == 0) 12 else it }
    val suffix = if (hour24 < 12) "am" else "pm"
    return "$hour12:${minute.toString().padStart(2, '0')} $suffix"
}

private val DEVANAGARI_DIGITS = charArrayOf('०', '१', '२', '३', '४', '५', '६', '७', '८', '९')

/** Maps each ASCII digit in [s] to its Devanagari numeral; anything else passes through. */
private fun toDevanagariDigits(s: String): String = s.map { c ->
    if (c in '0'..'9') DEVANAGARI_DIGITS[c - '0'] else c
}.joinToString("")

/**
 * "६:२३ पूर्वाह्न"-style 12-hour clock in Devanagari numerals + the
 * traditional Sanskrit forenoon/afternoon words — for the Panchang back
 * face, which is Devanagari-only (user-requested).
 */
internal fun formatClockTime12Devanagari(epochMillis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    val hour24 = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val minute = cal.get(java.util.Calendar.MINUTE)
    val hour12 = (hour24 % 12).let { if (it == 0) 12 else it }
    val suffix = if (hour24 < 12) "पूर्वाह्न" else "अपराह्न"
    return "${toDevanagariDigits(hour12.toString())}:${toDevanagariDigits(minute.toString().padStart(2, '0'))} $suffix"
}

/**
 * The Hindu Panchang face — a typographic hierarchy (mirrors [ClockFront]'s
 * big-time/weekday/date grouping) instead of one flat block of text, per
 * explicit request: vara (weekday) leads at the largest size since it's the
 * single most glanceable fact, tithi+month follow at medium size as the
 * day's defining pair (Devanagari face only — see below), then a
 * supplementary detail line trails at the smallest, dimmed size — and
 * [romanDate] trails everything as its own bottom line, on both the
 * [devanagari] and the English face (the two faces this tile flips between;
 * see [CalendarSystemTileFace]). A big [MoonPhaseVisual] sits beside the
 * text on both faces (user-requested) — mirrors [MoonPhaseTile]'s own
 * front-face layout (visual beside text when there's room, above it when
 * [narrow]).
 *
 * The two faces show different content, per explicit request: the front
 * face has vara (weekday) + paksha/tithi/month + nakshatra + the two
 * calendar years, all in Devanagari; the back face drops vara entirely (no
 * day-name line at all) and shows only [sunTimes]' sunrise/sunset (with
 * matching glyphs) and [PanchangInfo.ayana] — the ayana word itself in
 * Devanagari too ([PanchangDevanagari.ayana]), since a clock time and the
 * sun glyphs need no script of their own.
 */
@Composable
private fun PanchangFace(
    panchang: PanchangInfo,
    size: TileSize,
    romanDate: String,
    devanagari: Boolean,
    sunTimes: SunTimesInfo?,
) {
    val narrow = size.narrowLive
    val short = size.shortLive
    val big = size == TileSize.LARGE
    val pakshaName = if (devanagari) {
        PanchangDevanagari.paksha(panchang.tithi.paksha)
    } else if (panchang.tithi.paksha == Paksha.SHUKLA) {
        "shukla paksha"
    } else {
        "krishna paksha"
    }
    val vara = if (devanagari) PanchangDevanagari.vara(panchang.vara) else panchang.vara
    val tithiName = if (devanagari) PanchangDevanagari.tithiName(panchang.tithi.name) else panchang.tithi.name
    val month = if (devanagari) PanchangDevanagari.month(panchang.month) else panchang.month
    val nakshatra = if (devanagari) PanchangDevanagari.nakshatra(panchang.nakshatra) else panchang.nakshatra
    val nakshatraLabel = if (devanagari) "नक्षत्र" else "nakshatra"
    val yearLabel = if (devanagari) "शक ${panchang.shakaSamvat} · विक्रम ${panchang.vikramSamvat}" else "shaka ${panchang.shakaSamvat} · vikram ${panchang.vikramSamvat}"
    val moonFraction = tithiMoonFraction(panchang.tithi.paksha, panchang.tithi.tithiInPaksha)
    val visualSize = if (short) 34.dp else if (narrow) 40.dp else if (big) 72.dp else 52.dp

    val textColumn = @Composable {
        Column(horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start) {
            // Vara (weekday) only on the Devanagari face — the back face
            // shows sunrise/sunset/ayana only, with no day-name line at all
            // (user-requested).
            if (devanagari) {
                Text(
                    text = vara,
                    color = FaceText,
                    fontSize = if (short) 16.sp else if (narrow) 18.sp else if (big) 26.sp else 20.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
                )
            }
            // Tithi/paksha/month text only on the Devanagari face — the
            // back face shows sunrise/sunset/ayana instead of repeating the
            // same facts (user-requested).
            if (devanagari) {
                Text(
                    text = "$pakshaName · $tithiName · $month",
                    // A fixed highlight tint (not the tile's own accent fill,
                    // which this text sits on top of and would risk blending
                    // into) so tithi+month reads as the day's defining pair
                    // at a glance, distinct from the plain face-text vara/
                    // nakshatra/year lines.
                    color = TileAccents.Amber,
                    fontSize = if (short) 11.sp else if (narrow) 12.sp else if (big) 15.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (narrow) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
                )
            }
            if (!short) {
                if (devanagari) {
                    Text(
                        text = "$nakshatraLabel: $nakshatra",
                        color = FaceText.copy(alpha = 0.75f),
                        fontSize = if (narrow) 10.sp else if (big) 12.sp else 11.sp,
                        maxLines = if (narrow) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
                    )
                    Text(
                        text = yearLabel,
                        color = FaceText.copy(alpha = 0.6f),
                        fontSize = if (narrow) 9.sp else if (big) 12.sp else 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
                    )
                } else {
                    // Sunrise/sunset + ayana — back-face-only detail (user-
                    // requested), in place of what the Devanagari face still
                    // shows here (nakshatra + the two calendar years). The
                    // ayana word itself renders in Devanagari even here
                    // (user-requested) — a clock time and the sun glyphs
                    // above need no script of their own.
                    val detailFontSize = if (narrow) 11.sp else if (big) 13.sp else 12.sp
                    val detailIconSize = if (narrow) 11.dp else if (big) 13.dp else 12.dp
                    if (sunTimes != null) {
                        // sunTimes' two times can each independently belong
                        // to today or tomorrow (see SunTimes.nextSunriseSunset)
                        // — a short day label in front of each disambiguates
                        // which, instead of both silently reading as "today."
                        val sunriseVara = PanchangDevanagari.shortVara(HinduPanchang.varaFor(sunTimes.sunriseMillis))
                        val sunsetVara = PanchangDevanagari.shortVara(HinduPanchang.varaFor(sunTimes.sunsetMillis))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = TileIcons["sunrise"],
                                contentDescription = "sunrise",
                                tint = FaceText.copy(alpha = 0.75f),
                                modifier = Modifier.size(detailIconSize),
                            )
                            Text(
                                text = "$sunriseVara ${formatClockTime12Devanagari(sunTimes.sunriseMillis)}",
                                color = FaceText.copy(alpha = 0.75f),
                                fontSize = detailFontSize,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = TileIcons["sunset"],
                                contentDescription = "sunset",
                                tint = FaceText.copy(alpha = 0.75f),
                                modifier = Modifier.size(detailIconSize),
                            )
                            Text(
                                text = "$sunsetVara ${formatClockTime12Devanagari(sunTimes.sunsetMillis)}",
                                color = FaceText.copy(alpha = 0.75f),
                                fontSize = detailFontSize,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        text = PanchangDevanagari.ayana(panchang.ayana),
                        color = FaceText.copy(alpha = 0.6f),
                        fontSize = if (narrow) 10.sp else if (big) 13.sp else 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
                    )
                }
            }
            Text(
                text = romanDate,
                color = FaceText.copy(alpha = 0.55f),
                fontSize = if (short) 9.sp else if (narrow) 9.sp else if (big) 11.sp else 10.sp,
                maxLines = if (narrow) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
        }
    }

    if (narrow) {
        // Only one column wide — stack the visual above the text instead of
        // beside it, same as MoonPhaseTile's own narrow branch.
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MoonPhaseVisual(fraction = moonFraction, modifier = Modifier.size(visualSize))
            textColumn()
        }
    } else {
        Row(
            modifier = Modifier.fillMaxSize().padding(if (short) 4.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (short) 8.dp else 12.dp),
        ) {
            MoonPhaseVisual(fraction = moonFraction, modifier = Modifier.size(visualSize))
            Box(modifier = Modifier.weight(1f, fill = false)) { textColumn() }
        }
    }
}

/** The compact 1×1 face (ICONS home style / SMALL tile): just today's Roman day number, never flips. */
@Composable
fun CalendarSystemSmallFace(active: Boolean, modifier: Modifier = Modifier) {
    var dayOfMonth by remember { mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            dayOfMonth = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    Column(
        modifier = modifier.fillMaxSize().padding(4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = dayOfMonth.toString(),
            color = FaceText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
