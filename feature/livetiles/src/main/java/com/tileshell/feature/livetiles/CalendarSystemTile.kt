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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.tileshell.core.data.PanchangDayCell
import com.tileshell.core.data.PanchangDayKind
import com.tileshell.core.data.PanchangMonth
import com.tileshell.core.data.panchangMonth
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.background
import androidx.compose.runtime.collectAsState
import com.tileshell.core.data.PanchangObservances
import com.tileshell.core.data.HinduPanchang
import com.tileshell.core.data.Paksha
import com.tileshell.core.data.PanchangDevanagari
import com.tileshell.core.data.PanchangInfo
import com.tileshell.core.data.MoonTimes
import com.tileshell.core.data.MoonTimesInfo
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
internal const val DEFAULT_LATITUDE = 20.5937
internal const val DEFAULT_LONGITUDE = 78.9629

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
@Volatile private var recentFix: Pair<Long, Pair<Double, Double>>? = null

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

    // A fix asked for in the last six hours is reused: with no cached fix on the phone, every composition of the tile,
    // every sheet open and every widget push otherwise waited up to 8 s on a fresh network request again.
    recentFix?.let { (at, pos) -> if (System.currentTimeMillis() - at < 6 * 60 * 60_000L) return pos }
    val fresh = withTimeoutOrNull(LOCATION_FIX_TIMEOUT_MS) { requestSingleNetworkFix(lm) }
    if (fresh != null) {
        val pos = fresh.latitude to fresh.longitude
        recentFix = System.currentTimeMillis() to pos
        return pos
    }

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
        // The tithi, month and years move on a scale of hours: recomputed every five minutes, not on every recomposition.
        val panchang = remember(nowMillis / 300_000L) { HinduPanchang.panchangFor(nowMillis) }
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
        // The next sunrise and sunset only change when one of them passes: keep the last result until then.
        val sunHolder = remember(location) { arrayOfNulls<SunTimesInfo>(1) }
        val sunTimes = sunHolder[0]?.takeIf { nowMillis < minOf(it.sunriseMillis, it.sunsetMillis) }
            ?: SunTimes.nextSunriseSunset(nowMillis, location.first, location.second).also { sunHolder[0] = it }
        // Same "next" semantics as sunTimes — each rolls forward the minute it
        // passes. A ~300-sample altitude scan, still cheap once a minute.
        // Same for the moon (a 48 h scan): recomputed when its next rise or set passes, or every six hours at the latest.
        val moonHolder = remember(location) { arrayOfNulls<Pair<Long, MoonTimesInfo>>(1) }
        val moonTimes = moonHolder[0]?.takeIf { (at, info) ->
            nowMillis - at < 6 * 60 * 60_000L && nowMillis < (listOfNotNull(info.moonriseMillis, info.moonsetMillis).minOrNull() ?: Long.MAX_VALUE)
        }?.second ?: MoonTimes.nextMoonriseMoonset(nowMillis, location.first, location.second).also { moonHolder[0] = nowMillis to it }
        // Highlighted tithis and festivals: recomputed once a day, when the
        // settings change, or when the location resolves (Sankashti uses moonrise).
        val obsSettings by PanchangPrefs.settings(context).collectAsState()
        val dayKey = PanchangObservances.startOfDay(nowMillis, java.util.TimeZone.getDefault())
        val strip by produceState<String?>(initialValue = null, dayKey, obsSettings, location) {
            val settings = obsSettings ?: return@produceState
            value = withContext(Dispatchers.Default) {
                runCatching {
                    val moon = moonriseAfter(location.first, location.second)
                    observanceStripText(
                        PanchangObservances.on(dayKey, settings, moonriseAfter = moon, location = location),
                        eveningMoonrise(dayKey, location.first, location.second),
                    )
                }.getOrNull()
            }
        }
        FlipTile(
            flipped = flipped,
            modifier = modifier.fillMaxSize(),
            front = {
                ObservanceStripped(strip, size) {
                    PanchangFace(panchang = panchang, size = size, devanagari = true, hasStrip = strip != null)
                }
            },
            back = {
                // A 2×2 back face has no room for the strip as well as its four
                // times; the front already shows it, every other flip.
                val roomy = size.cols >= 4 || size.rows >= 3
                ObservanceStripped(if (roomy) strip else null, size) {
                    PanchangBackFace(panchang = panchang, size = size, sunTimes = sunTimes, moonTimes = moonTimes, nowMillis = nowMillis)
                }
            },
        )
        return
    }

    val systemName = calendarSystemFor(systemId)?.displayName.orEmpty()
    val details = rememberSystemDetails(systemId, nowMillis)
    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = {
            if (details != null) SystemDayFront(systemName, details, nowMillis, size)
            else CalendarSystemFace(label = systemName, dateText = formatSelectedSystemDate(systemId, nowMillis), size = size)
        },
        back = {
            if (details != null) SystemDayBack(systemId, details, romanDate, size)
            else CalendarSystemFace(label = "roman calendar", dateText = romanDate, size = size)
        },
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
 * supplementary detail line trails at the smallest, dimmed size. No Roman
 * (English) date on either face (user-requested, for room). A big [MoonPhaseVisual] sits beside the
 * text on both faces (user-requested) — mirrors [MoonPhaseTile]'s own
 * front-face layout (visual beside text when there's room, above it when
 * [narrow]).
 *
 * This is the front face: vara (weekday) + paksha/tithi/month + nakshatra +
 * the two calendar years, all in Devanagari. The back face is
 * [PanchangBackFace].
 */
@Composable
private fun PanchangFace(
    panchang: PanchangInfo,
    size: TileSize,
    devanagari: Boolean,
    hasStrip: Boolean = false,
) {
    val narrow = size.narrowLive
    val short = size.shortLive
    val big = size == TileSize.LARGE
    // A 2×2 tile keeps the widget's lines (vara, number, tithi · month,
    // nakshatra) at full size rather than squeezing in the year too; with a
    // highlight strip, the strip takes the nakshatra's place.
    val compact = !big && !narrow && !short && size.cols <= 2 && size.rows <= 2
    val pakshaName = if (devanagari) {
        PanchangDevanagari.paksha(panchang.tithi.paksha)
    } else if (panchang.tithi.paksha == Paksha.SHUKLA) {
        "shukla paksha"
    } else {
        "krishna paksha"
    }
    val vara = if (devanagari) PanchangDevanagari.vara(panchang.vara) else panchang.vara
    val tithiName = if (devanagari) PanchangDevanagari.tithiName(panchang.tithi.name) else panchang.tithi.name
    val tithiNumber = PanchangDevanagari.tithiNumber(panchang.tithi)
    val month = if (devanagari) PanchangDevanagari.month(panchang.month) else panchang.month
    val nakshatra = if (devanagari) PanchangDevanagari.nakshatra(panchang.nakshatra) else panchang.nakshatra
    val nakshatraLabel = if (devanagari) "नक्षत्र" else "nakshatra"
    val yearLabel = if (devanagari) {
        "शक ${toDevanagariDigits(panchang.shakaSamvat.toString())} · विक्रम ${toDevanagariDigits(panchang.vikramSamvat.toString())}"
    } else {
        "shaka ${panchang.shakaSamvat} · vikram ${panchang.vikramSamvat}"
    }
    val moonFraction = tithiMoonFraction(panchang.tithi.paksha, panchang.tithi.tithiInPaksha)
    val visualSize = if (short) 34.dp else if (narrow) 40.dp else if (big) 64.dp else if (size.cols >= 4) 44.dp else 34.dp

    val textColumn = @Composable {
        Column(horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start) {
            // Vara (weekday) only on the Devanagari face — the back face
            // shows sunrise/sunset/ayana only, with no day-name line at all
            // (user-requested).
            if (devanagari) {
                Text(
                    text = vara,
                    color = FaceText,
                    fontSize = if (short) 16.sp else if (narrow) 15.sp else if (big) 22.sp else 17.sp,
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
            // The tithi as a big number (user-requested), the way the
            // calendar tile shows the day of the month between weekday and
            // month. The 1-row size has no room for it, so there the number
            // rides along in the tithi line instead.
            // Paksha rides beside the number (user-requested) so the line
            // below only has to fit tithi + month.
            if (devanagari && !short) {
                val numberSize = if (narrow) 30.sp else if (big) 56.sp else 40.sp
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tithiNumber,
                        color = FaceText,
                        fontSize = numberSize,
                        lineHeight = numberSize,
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(if (narrow) 3.dp else 5.dp))
                    // Stacked one word per line ("शुक्ल" over "पक्ष") — a
                    // medium tile has no room for "शुक्ल पक्ष" on one line
                    // beside the number (verified on-device: it squeezed to
                    // nothing).
                    Column {
                        pakshaName.split(' ').forEach { word ->
                            Text(
                                text = word,
                                color = TileAccents.Amber,
                                fontSize = if (narrow) 10.sp else if (big) 14.sp else 11.sp,
                                lineHeight = if (narrow) 12.sp else if (big) 17.sp else 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
            if (devanagari) {
                Text(
                    text = if (short) "$pakshaName · $tithiName $tithiNumber · $month" else "$tithiName · $month",
                    lineHeight = if (short) 13.sp else if (big) 19.sp else 16.sp,
                    // A fixed highlight tint (not the tile's own accent fill,
                    // which this text sits on top of and would risk blending
                    // into) so tithi+month reads as the day's defining pair
                    // at a glance, distinct from the plain face-text vara/
                    // nakshatra/year lines.
                    color = TileAccents.Amber,
                    fontSize = if (short) 11.sp else if (narrow) 12.sp else if (big) 15.sp else 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (narrow) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
                )
            }
            if (!short) {
                // One column wide and two rows tall has room for the day, the number and the tithi, not the rest.
                if (devanagari && !(compact && hasStrip) && (!narrow || size.rows >= 3)) {
                    Text(
                        text = "$nakshatraLabel: $nakshatra",
                        color = FaceText.copy(alpha = 0.8f),
                        fontSize = if (narrow) 10.sp else if (big) 13.sp else 12.sp,
                        maxLines = if (narrow) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
                    )
                }
                if (devanagari && !compact && (!narrow || size.rows >= 4)) {
                    Text(
                        text = yearLabel,
                        color = FaceText.copy(alpha = 0.6f),
                        fontSize = if (narrow) 9.sp else if (big) 12.sp else 10.sp,
                        maxLines = if (narrow || size.cols <= 2) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
                    )
                }
            }
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
            if (size.rows >= 3) MoonPhaseVisual(fraction = moonFraction, modifier = Modifier.size(visualSize))
            ScaleDownToFit(Modifier.weight(1f, fill = false)) { textColumn() }
        }
    } else if (short) {
        // One row tall: the moon beside a condensed line or two.
        Row(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MoonPhaseVisual(fraction = moonFraction, modifier = Modifier.size(visualSize))
            Box(modifier = Modifier.weight(1f, fill = false)) { ScaleDownToFit { textColumn() } }
        }
    } else {
        // 2×2 and larger (user-requested: show the full data): the moon sits
        // in the top-right corner so the text gets the whole width. Beside
        // the text it squeezed the tithi/nakshatra/year lines into ellipses
        // on a 2×2 tile.
        Box(modifier = Modifier.fillMaxSize().padding(11.dp)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                ScaleDownToFit { textColumn() }
            }
            MoonPhaseVisual(fraction = moonFraction, modifier = Modifier.align(Alignment.TopEnd).size(visualSize))
        }
    }
}

/**
 * A face with the day's highlight (see [observanceStripText]) as an amber
 * strip along the bottom; unchanged when there's none.
 */
@Composable
private fun ObservanceStripped(strip: String?, size: TileSize, content: @Composable () -> Unit) {
    if (strip == null) {
        content()
        return
    }
    val tiny = size.shortLive || size.narrowLive
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
        Text(
            text = strip,
            color = Color(0xFF3A2600),
            fontSize = if (tiny) 10.sp else if (size == TileSize.LARGE) 14.sp else 12.sp,
            fontWeight = FontWeight.Medium,
            // Two lines on a 2-column tile: "अंगारकी संकष्टी चतुर्थी · चंद्रोदय ७:४७" doesn't fit on one.
            maxLines = if (size.cols <= 2) 2 else 1,
            lineHeight = if (tiny) 12.sp else 15.sp,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (size.narrowLive) TextAlign.Center else TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .background(TileAccents.Amber)
                .padding(horizontal = if (tiny) 4.dp else 10.dp, vertical = if (tiny) 2.dp else 4.dp),
        )
    }
}

/**
 * The Panchang back face (user-requested: no moon picture, the text laid out
 * better): ayana as the heading, then a sun column (sunrise, sunset) and a
 * moon column (moonrise, moonset) — side by side when the tile is wide
 * enough, one under the other otherwise. No Roman date (user-requested, for
 * room: the front face already has it).
 * Each time carries a short Devanagari weekday, since "next sunrise" can be
 * tomorrow's.
 */
@Composable
private fun PanchangBackFace(
    panchang: PanchangInfo,
    size: TileSize,
    sunTimes: SunTimesInfo?,
    moonTimes: MoonTimesInfo?,
    nowMillis: Long,
) {
    // A big tile has room for the whole month, with a tithi under every date.
    if (size.cols >= 3 && size.rows >= 3) {
        PanchangMonthBack(panchang, sunTimes, moonTimes, nowMillis)
        return
    }
    val narrow = size.narrowLive
    val short = size.shortLive
    val big = size == TileSize.LARGE || size == TileSize.XLARGE
    val sideBySide = size.cols >= 4
    val iconSize = if (narrow) 12.dp else if (big) 15.dp else 13.dp
    val fontSize = if (narrow) 11.sp else if (big) 14.sp else 12.sp
    // The "सूर्य"/"चंद्र" labels only where there's room — on a 2-column tile
    // they cost two lines, and the rise/set glyphs already tell sun from moon.
    val labels = !short && !narrow && size.cols >= 3
    val sun = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (labels) PanchangColumnLabel("सूर्य", big)
            sunTimes?.let {
                PanchangEventRow("sunrise", it.sunriseMillis, iconSize, fontSize, compact = narrow)
                PanchangEventRow("sunset", it.sunsetMillis, iconSize, fontSize, compact = narrow)
            }
        }
    }
    val moon = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (labels) PanchangColumnLabel("चंद्र", big)
            moonTimes?.moonriseMillis?.let { PanchangEventRow("moonrise", it, iconSize, fontSize, compact = narrow) }
            moonTimes?.moonsetMillis?.let { PanchangEventRow("moonset", it, iconSize, fontSize, compact = narrow) }
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        contentAlignment = if (narrow) Alignment.Center else Alignment.CenterStart,
    ) {
    ScaleDownToFit {
    Column(
        verticalArrangement = Arrangement.spacedBy(if (short) 2.dp else 8.dp),
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = PanchangDevanagari.ayana(panchang.ayana),
            color = FaceText,
            fontSize = if (narrow) 13.sp else if (big) 20.sp else 16.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (sideBySide && !short) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.weight(1f)) { sun() }
                Box(Modifier.weight(1f)) { moon() }
            }
        } else if (short) {
            // One row tall: just the next sunrise and sunset in a line.
            sunTimes?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PanchangEventRow("sunrise", it.sunriseMillis, iconSize, fontSize)
                    PanchangEventRow("sunset", it.sunsetMillis, iconSize, fontSize)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(if (narrow) 4.dp else 6.dp)) {
                sun()
                moon()
            }
        }
    }
    }
    }
}

@Composable
private fun PanchangColumnLabel(text: String, big: Boolean) {
    Text(
        text = text,
        color = TileAccents.Amber,
        fontSize = if (big) 13.sp else 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}

/** One "glyph · short weekday · time" line on the Panchang back face. */
@Composable
private fun PanchangEventRow(iconKey: String, epochMillis: Long, iconSize: androidx.compose.ui.unit.Dp, fontSize: androidx.compose.ui.unit.TextUnit, compact: Boolean = false) {
    val vara = PanchangDevanagari.shortVara(HinduPanchang.varaFor(epochMillis))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            imageVector = TileIcons[iconKey],
            contentDescription = iconKey,
            tint = FaceText.copy(alpha = 0.75f),
            modifier = Modifier.size(iconSize),
        )
        Text(
            // One column wide: no weekday, and "पूर्वाह्न / अपराह्न" shortened, so the time is not cut.
            text = if (compact) formatClockTime12Devanagari(epochMillis).replace("पूर्वाह्न", "पू").replace("अपराह्न", "अ") else "$vara ${formatClockTime12Devanagari(epochMillis)}",
            color = FaceText.copy(alpha = 0.75f),
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The compact 1×1 face (ICONS home style / SMALL tile), never flips. For the
 * Hindu Panchang it shows today's tithi number with its paksha (user-requested:
 * the data that fits a small tile); for any other system, today's Roman day.
 */
@Composable
fun CalendarSystemSmallFace(active: Boolean, modifier: Modifier = Modifier, systemId: String? = null) {
    if (systemId == HINDU_PANCHANG_ID) {
        PanchangSmallFace(active, modifier)
        return
    }
    RomanDaySmallFace(active, modifier)
}

@Composable
private fun PanchangSmallFace(active: Boolean, modifier: Modifier) {
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val panchang = remember(nowMillis / 60_000L) { HinduPanchang.panchangFor(nowMillis) }
    Column(
        modifier = modifier.fillMaxSize().padding(4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = PanchangDevanagari.tithiNumber(panchang.tithi),
            color = FaceText,
            fontSize = 26.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
        )
        Text(
            text = PanchangDevanagari.paksha(panchang.tithi.paksha).substringBefore(' '),
            color = TileAccents.Amber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun RomanDaySmallFace(active: Boolean, modifier: Modifier) {
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

/**
 * Lays [content] out at its natural size and, only if that's taller than the
 * space available, scales it down uniformly (from the top-left) until it fits
 * — so the Panchang faces show every line on any tile, whatever the grid's
 * column count, font scale or tile style (user-reported: the 2×2 face cut off
 * its year and date lines on a 5-column grid with a 1.08 font scale). Content
 * that already fits is untouched. The reported size is the scaled size, so a
 * parent can still center it.
 */
@Composable
internal fun ScaleDownToFit(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
        val placeable = measurables.first().measure(loose)
        val maxH = constraints.maxHeight
        val scale = if (constraints.hasBoundedHeight && placeable.height > maxH && placeable.height > 0) {
            maxH / placeable.height.toFloat()
        } else {
            1f
        }
        val w = (placeable.width * scale).toInt().coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = (placeable.height * scale).toInt().coerceIn(constraints.minHeight, if (constraints.hasBoundedHeight) maxH else Int.MAX_VALUE)
        layout(w, h) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
        }
    }
}

/**
 * The big-tile back: the month as a Monday-first grid, each date with its tithi number in Devanagari beneath it
 * (ekadashi in amber, the full moon as ○ and the new moon as ●, today a filled disc), the ayana as the heading, and
 * the sun and moon times in a line at the foot.
 */
@Composable
private fun PanchangMonthBack(panchang: PanchangInfo, sunTimes: SunTimesInfo?, moonTimes: MoonTimesInfo?, nowMillis: Long) {
    // The month is laid out in the tile's own size: the tile-wide enlargement would leave the cells too short for a tithi.
    val outer = androidx.compose.ui.platform.LocalDensity.current
    val k = LocalLiveFaceScale.current.coerceAtMost(1.3f)
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(outer.density / k, outer.fontScale),
    ) { PanchangMonthBackBody(panchang, sunTimes, moonTimes, nowMillis) }
}

@Composable
private fun PanchangMonthBackBody(panchang: PanchangInfo, sunTimes: SunTimesInfo?, moonTimes: MoonTimesInfo?, nowMillis: Long) {
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val month by produceState<PanchangMonth?>(initialValue = null, today.year, today.monthValue) {
        value = withContext(Dispatchers.Default) { panchangMonth(today.year, today.monthValue, zone) }
    }
    val title = today.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH).lowercase(Locale.ENGLISH) + " ${today.year}"
    // Monday..Sunday headings in Devanagari, read off a known week.
    val heads = remember {
        (0L..6L).map { PanchangDevanagari.shortVara(HinduPanchang.varaFor(java.time.LocalDate.of(2024, 1, 1).plusDays(it).atTime(12, 0).atZone(zone).toInstant().toEpochMilli())) }
    }
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = FaceText, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
            // The lunar months this month touches, the later one on the same tint as its dates below.
            month?.lunarMonths?.forEachIndexed { i, (name, adhika) ->
                val word = (if (adhika) "अधिक " else "") + PanchangDevanagari.month(name)
                Text(
                    word, color = if (i == 0) TileAccents.Amber else FaceText, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp).let { if (i > 0) it.background(FaceText.copy(alpha = 0.22f)).padding(horizontal = 4.dp) else it },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            heads.forEach {
                Text(it, color = FaceText.copy(alpha = 0.6f), fontSize = 9.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        val m = month
        Column(Modifier.weight(1f).fillMaxWidth()) {
            if (m != null) {
                val rows = (m.firstWeekdayOffset + m.cells.size + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val cell = m.cells.getOrNull(r * 7 + c - m.firstWeekdayOffset)
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                if (cell != null) PanchangGridCell(cell, isToday = cell.day == today.dayOfMonth, laterMonth = m.lunarMonthIndex(cell) > 0)
                            }
                        }
                    }
                }
            }
        }
        val times = listOfNotNull(
            sunTimes?.let { "sunrise" to it.sunriseMillis },
            sunTimes?.let { "sunset" to it.sunsetMillis },
            moonTimes?.moonriseMillis?.let { "moonrise" to it },
            moonTimes?.moonsetMillis?.let { "moonset" to it },
        )
        if (times.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                times.forEach { (icon, millis) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(TileIcons[icon], icon, tint = FaceText.copy(alpha = 0.75f), modifier = Modifier.size(11.dp))
                        Text(formatClockTime12Devanagari(millis), color = FaceText.copy(alpha = 0.75f), fontSize = 9.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun PanchangGridCell(cell: PanchangDayCell, isToday: Boolean, laterMonth: Boolean) {
    val tithi = when (cell.kind) {
        PanchangDayKind.PURNIMA -> "○"
        PanchangDayKind.AMAVASYA -> "●"
        else -> cell.label
    }
    val ink = if (isToday) Color.Black.copy(alpha = 0.85f) else FaceText
    val tithiInk = when {
        isToday -> ink
        cell.kind == PanchangDayKind.EKADASHI -> TileAccents.Amber
        else -> FaceText.copy(alpha = 0.7f)
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(1.dp).let { if (isToday) it.clip(CircleShape).background(FaceText) else if (laterMonth) it.background(FaceText.copy(alpha = 0.16f)) else it },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("${cell.day}", color = ink, fontSize = 11.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false)
        Text(tithi, color = tithiInk, fontSize = 9.sp, maxLines = 1, softWrap = false)
    }
}
