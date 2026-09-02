package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.HINDU_PANCHANG_ID
import com.tileshell.core.data.HinduPanchang
import com.tileshell.core.data.Paksha
import com.tileshell.core.data.PanchangDevanagari
import com.tileshell.core.data.PanchangInfo
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.calendarSystemFor
import com.tileshell.core.data.formatRomanDate
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileAccents
import kotlinx.coroutines.delay
import java.util.Locale

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

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
        FlipTile(
            flipped = flipped,
            modifier = modifier.fillMaxSize(),
            front = { PanchangFace(panchang = panchang, size = size, romanDate = romanDate, devanagari = true) },
            back = { PanchangFace(panchang = panchang, size = size, romanDate = romanDate, devanagari = false) },
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
 * The Hindu Panchang face — a typographic hierarchy (mirrors [ClockFront]'s
 * big-time/weekday/date grouping) instead of one flat block of text, per
 * explicit request: vara (weekday) leads at the largest size since it's the
 * single most glanceable fact, tithi+month follow at medium size as the
 * day's defining pair, then nakshatra and the two calendar years trail at
 * the smallest, dimmed size as supplementary detail — and [romanDate] trails
 * everything as its own bottom line, on both the [devanagari] and the
 * English face (the two faces this tile flips between; see
 * [CalendarSystemTileFace]). A big [MoonPhaseVisual] sits beside the text on
 * both faces (user-requested) — mirrors [MoonPhaseTile]'s own front-face
 * layout (visual beside text when there's room, above it when [narrow]).
 */
@Composable
private fun PanchangFace(panchang: PanchangInfo, size: TileSize, romanDate: String, devanagari: Boolean) {
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
            Text(
                text = "$pakshaName · $tithiName · $month",
                // A fixed highlight tint (not the tile's own accent fill, which
                // this text sits on top of and would risk blending into) so
                // tithi+month reads as the day's defining pair at a glance,
                // distinct from the plain face-text vara/nakshatra/year lines.
                color = TileAccents.Amber,
                fontSize = if (short) 11.sp else if (narrow) 12.sp else if (big) 15.sp else 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = if (narrow) 3 else 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
            if (!short) {
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
fun CalendarSystemSmallFace(modifier: Modifier = Modifier) {
    val cal = remember { java.util.Calendar.getInstance() }
    Column(
        modifier = modifier.fillMaxSize().padding(4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = cal.get(java.util.Calendar.DAY_OF_MONTH).toString(),
            color = FaceText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
