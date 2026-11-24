package com.tileshell.feature.livetiles.health

import com.tileshell.feature.livetiles.localIsoToEpochMillis
import org.json.JSONObject

/**
 * Air quality and UV for the air page, from Open-Meteo's air-quality API (no key). Parsing, bands and the best walking
 * time are pure and unit-tested; [AirRepository] fetches and caches.
 */

data class AirHour(val timeMs: Long, val aqi: Int?, val uv: Double?, val isDay: Boolean)

data class AirSnapshot(
    val usAqi: Int?,
    val pm25: Double?,
    val pm10: Double?,
    val ozone: Double?,
    val no2: Double?,
    val uvNow: Double?,
    val hours: List<AirHour>,
    val fetchedAtMs: Long,
    val utcOffsetSeconds: Int,
)

fun airQualityUrl(lat: Double, lon: Double): String =
    "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon" +
        "&current=us_aqi,pm10,pm2_5,ozone,nitrogen_dioxide,uv_index" +
        "&hourly=us_aqi,uv_index,is_day&timezone=auto&forecast_days=1"

private fun JSONObject.dbl(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

/** Null when the response is not an air-quality answer. Missing values stay null. */
fun parseAirQuality(json: String, nowMs: Long): AirSnapshot? {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val current = root.optJSONObject("current") ?: return null
    val offset = root.optInt("utc_offset_seconds", 0)
    val hourly = root.optJSONObject("hourly")
    val times = hourly?.optJSONArray("time")
    val aqi = hourly?.optJSONArray("us_aqi")
    val uv = hourly?.optJSONArray("uv_index")
    val day = hourly?.optJSONArray("is_day")
    val hours = buildList {
        if (times != null) for (i in 0 until times.length()) {
            val t = localIsoToEpochMillis(times.optString(i), offset) ?: continue
            add(
                AirHour(
                    t,
                    if (aqi == null || aqi.isNull(i)) null else aqi.optDouble(i).takeIf { !it.isNaN() }?.toInt(),
                    if (uv == null || uv.isNull(i)) null else uv.optDouble(i).takeIf { !it.isNaN() },
                    day?.optInt(i, 1) == 1,
                ),
            )
        }
    }
    return AirSnapshot(
        usAqi = current.dbl("us_aqi")?.toInt(),
        pm25 = current.dbl("pm2_5"),
        pm10 = current.dbl("pm10"),
        ozone = current.dbl("ozone"),
        no2 = current.dbl("nitrogen_dioxide"),
        uvNow = current.dbl("uv_index"),
        hours = hours,
        fetchedAtMs = nowMs,
        utcOffsetSeconds = offset,
    )
}

enum class AqiBand(val label: String, val max: Int, val argb: Long) {
    GOOD("good", 50, 0xFF7CB518),
    MODERATE("moderate", 100, 0xFFE2A200),
    SENSITIVE("unhealthy for sensitive groups", 150, 0xFFE5641E),
    UNHEALTHY("unhealthy", 200, 0xFFD6262B),
    VERY_UNHEALTHY("very unhealthy", 300, 0xFF6B3FD4),
    HAZARDOUS("hazardous", Int.MAX_VALUE, 0xFF7B1E3A),
}

fun aqiBand(aqi: Int): AqiBand = AqiBand.entries.first { aqi <= it.max }

/** UV bands as the WHO names them. */
fun uvBand(uv: Double): String = when {
    uv < 3 -> "low"
    uv < 6 -> "moderate"
    uv < 8 -> "high"
    uv < 11 -> "very high"
    else -> "extreme"
}

/** The daylight hour from [nowMs] on with the lowest AQI among those with UV under [maxUv]; earlier on a tie. Null when none. */
fun bestWalkHour(hours: List<AirHour>, nowMs: Long, maxUv: Double = 6.0): AirHour? =
    hours.filter { it.isDay && it.timeMs + 3_600_000L > nowMs && it.aqi != null && (it.uv ?: 0.0) < maxUv }
        .minWithOrNull(compareBy<AirHour> { it.aqi!! }.thenBy { it.timeMs })

/** Today's highest UV and the hour it falls in. */
fun uvPeak(hours: List<AirHour>): AirHour? = hours.filter { it.uv != null }.maxByOrNull { it.uv!! }?.takeIf { (it.uv ?: 0.0) > 0.0 }

fun encodeAirCache(lat: Double, lon: Double, raw: String, fetchedAtMs: Long): String = "$lat\t$lon\t$fetchedAtMs\n$raw"

/** (lat, lon, fetchedAt, raw json) or null. */
fun decodeAirCache(text: String): List<String>? {
    val nl = text.indexOf('\n')
    if (nl < 0) return null
    val head = text.substring(0, nl).split('\t')
    if (head.size < 3 || head[0].toDoubleOrNull() == null || head[1].toDoubleOrNull() == null || head[2].toLongOrNull() == null) return null
    return head + text.substring(nl + 1)
}
