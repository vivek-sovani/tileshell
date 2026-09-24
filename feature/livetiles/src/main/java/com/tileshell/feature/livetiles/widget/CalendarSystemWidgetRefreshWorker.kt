package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tileshell.core.data.HINDU_PANCHANG_ID
import com.tileshell.core.data.HinduPanchang
import com.tileshell.core.data.PanchangDevanagari
import com.tileshell.core.data.PanchangInfo
import com.tileshell.core.data.MoonTimes
import com.tileshell.core.data.MoonTimesInfo
import com.tileshell.core.data.SunTimes
import com.tileshell.core.data.SunTimesInfo
import com.tileshell.core.data.calendarSystemFor
import com.tileshell.core.data.formatRomanDate
import com.tileshell.feature.livetiles.R
import com.tileshell.feature.livetiles.formatClockTime12Devanagari
import com.tileshell.feature.livetiles.formatSelectedSystemDate
import com.tileshell.feature.livetiles.lastCoarseLocationOrDefault
import com.tileshell.feature.livetiles.tithiMoonFraction
import java.util.concurrent.TimeUnit

/**
 * Builds + pushes the calendar-system widget's [RemoteViews]. Pure local
 * date math (same as [HinduPanchang]/[com.tileshell.core.data
 * .calendarSystemFor]'s `android.icu` formatting) — no permission, no
 * network, no cache, so — like moon phase — nothing to force-fetch, just a
 * re-render when the date rolls over. That is now a **single daily run just
 * after midnight** ([WidgetWork.millisUntilNextMidnight]) rather than the
 * 30-minute poll it used to be: polling 48 times a day to catch one midnight
 * rollover woke the device 47 times for nothing.
 *
 * Reuses two pure helpers straight from the in-app tile rather than
 * duplicating them: [formatSelectedSystemDate] (widened to internal) for the
 * 7 ICU-backed systems, and [tithiMoonFraction] for the exact same real
 * moon-crescent bitmap the moon-phase widget uses ([moonPhaseBitmap]) when
 * the picked system is Hindu Panchang.
 */
class CalendarSystemWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        pushAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC = "tileshell_calsys_widget_refresh"
        private const val UNIQUE_NOW = "tileshell_calsys_widget_refresh_now"

        fun ensureScheduled(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                // CANCEL_AND_REENQUEUE, not KEEP (an install already on the old
                // 30-minute cadence would otherwise keep it forever) and not
                // UPDATE: UPDATE leaves work that has already started its
                // periodic cadence anchored wherever that cadence already runs
                // and ignores the new initial delay, so the midnight alignment
                // below never actually took effect after the first period.
                // Observed on a real device: this job's daily run had drifted to
                // 19:26, i.e. the date rolled over 19 hours before its "midnight"
                // refresh. Re-enqueueing re-anchors it to the next real midnight.
                // Safe to do on every onUpdate because that fires only on
                // placement, reboot and app update, and each re-enqueue still
                // targets the very next midnight.
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                PeriodicWorkRequestBuilder<CalendarSystemWidgetRefreshWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(WidgetWork.millisUntilNextMidnight(), TimeUnit.MILLISECONDS)
                    .setConstraints(WidgetWork.localConstraints())
                    .build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_PERIODIC)
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CalendarSystemWidgetRefreshWorker>().build(),
            )
        }

        suspend fun pushAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CalendarSystemAppWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val nowMillis = System.currentTimeMillis()
            // One location resolution per push (not per widget instance) —
            // reused by the Hindu Panchang face's sunrise/sunset line below.
            // Suspends briefly (bounded) on a cold start with no cached fix
            // yet — see lastCoarseLocationOrDefault; fine here since this
            // already runs inside a CoroutineWorker.
            val location = lastCoarseLocationOrDefault(context)
            val sunTimes = SunTimes.nextSunriseSunset(nowMillis, location.first, location.second)
            val moonTimes = MoonTimes.nextMoonriseMoonset(nowMillis, location.first, location.second)
            ids.forEach { id ->
                val systemId = WidgetConfigStore.calendarSystemId(context, id)
                val minWidthDp = manager.getAppWidgetOptions(id)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                val (accent, onAccent) = resolveWidgetAccent(context, id)
                val views = buildRemoteViews(context, id, systemId, nowMillis, sunTimes, moonTimes, accent, onAccent, isCompactWidget(minWidthDp))
                manager.updateAppWidget(id, views)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            appWidgetId: Int,
            systemId: String?,
            nowMillis: Long,
            sunTimes: SunTimesInfo?,
            moonTimes: MoonTimesInfo,
            accent: Int,
            onAccent: Int,
            compact: Boolean,
        ): RemoteViews {
            val isHindu = systemId == HINDU_PANCHANG_ID
            val layout = when {
                systemId == null -> if (compact) R.layout.widget_calsys_generic_compact else R.layout.widget_calsys_generic
                isHindu && compact -> R.layout.widget_calsys_hindu_compact
                isHindu -> R.layout.widget_calsys_hindu
                compact -> R.layout.widget_calsys_generic_compact
                else -> R.layout.widget_calsys_generic
            }
            val views = RemoteViews(context.packageName, layout)
            views.setImageViewBitmap(R.id.widget_bg, accentGradientBitmap(accent))
            // Rounds the widget's own corners (see widget_rounded_background.xml) —
            // the background drawable provides an Outline; clipToOutline clips
            // widget_bg's full-bleed gradient (and everything else) to it.
            views.setBoolean(R.id.widget_root, "setClipToOutline", true)
            views.setOnClickPendingIntent(R.id.widget_settings, reconfigurePendingIntent(context, appWidgetId))

            if (systemId == null) {
                views.setOnClickPendingIntent(R.id.widget_root, reconfigurePendingIntent(context, appWidgetId))
                views.setTextColor(R.id.widget_label, onAccent)
                views.setTextColor(R.id.widget_date, onAccent)
                views.setTextColor(R.id.widget_back_label, onAccent)
                views.setTextColor(R.id.widget_back_date, onAccent)
                views.setTextViewText(R.id.widget_label, "no system picked")
                views.setTextViewText(R.id.widget_date, "tap the gear to choose one")
                views.setTextViewText(R.id.widget_back_date, formatRomanDate(nowMillis))
                return views
            }

            val system = calendarSystemFor(systemId)
            val displayName = system?.displayName.orEmpty()
            views.setOnClickPendingIntent(
                R.id.widget_root,
                calendarSystemAppPendingIntent(context, appWidgetId, displayName.ifBlank { "calendar" }),
            )

            if (isHindu) {
                val panchang = HinduPanchang.panchangFor(nowMillis)
                val romanDate = formatRomanDate(nowMillis)
                val moonFraction = tithiMoonFraction(panchang.tithi.paksha, panchang.tithi.tithiInPaksha)
                val moon = moonPhaseBitmap(moonFraction, onAccent)
                views.setImageViewBitmap(R.id.widget_icon, moon)
                views.setImageViewBitmap(R.id.widget_icon_back, moon)
                setPanchangFace(views, panchang, romanDate, sunTimes, moonTimes, onAccent, devanagari = true, back = false, compact = compact)
                setPanchangFace(views, panchang, romanDate, sunTimes, moonTimes, onAccent, devanagari = false, back = true, compact = compact)
            } else {
                views.setTextColor(R.id.widget_label, onAccent)
                views.setTextColor(R.id.widget_date, onAccent)
                views.setTextColor(R.id.widget_back_label, onAccent)
                views.setTextColor(R.id.widget_back_date, onAccent)
                views.setInt(R.id.widget_icon, "setColorFilter", onAccent)
                views.setTextViewText(R.id.widget_label, displayName)
                views.setTextViewText(
                    R.id.widget_date,
                    formatSelectedSystemDate(systemId, nowMillis).ifBlank { "no data" },
                )
                views.setTextViewText(R.id.widget_back_date, formatRomanDate(nowMillis))
            }
            return views
        }

        /**
         * Mirrors the in-app tile's own [com.tileshell.feature.livetiles
         * .PanchangFace]: the front face has vara (weekday) + paksha/tithi/
         * month + nakshatra + the two calendar years, all in Devanagari; the
         * [back] face drops vara entirely (no day-name line) and shows only
         * sunrise, sunset and ayana — one below another, all in Devanagari
         * (user-requested, see [PanchangFace]'s own doc comment for the full
         * reasoning). Font sizes for the back face's three lines come from
         * the layout resource itself, which already has a distinct smaller
         * set of sizes for the [compact] (narrow) bucket vs. the full one —
         * same "size drives the XML variant" idiom the rest of this widget's
         * text already follows, rather than a third dynamic scale.
         */
        private fun setPanchangFace(
            views: RemoteViews,
            panchang: PanchangInfo,
            romanDate: String,
            sunTimes: SunTimesInfo?,
            moonTimes: MoonTimesInfo,
            onAccent: Int,
            devanagari: Boolean,
            back: Boolean,
            compact: Boolean,
        ) {
            val varaId = if (back) R.id.widget_back_vara else R.id.widget_vara
            val pakshaId = if (back) R.id.widget_back_paksha else R.id.widget_paksha
            if (back) {
                views.setViewVisibility(varaId, View.GONE)
            } else {
                views.setViewVisibility(varaId, View.VISIBLE)
                views.setTextColor(varaId, onAccent)
                views.setTextViewText(varaId, PanchangDevanagari.vara(panchang.vara))
            }

            if (devanagari) {
                val pakshaName = PanchangDevanagari.paksha(panchang.tithi.paksha)
                val tithiName = PanchangDevanagari.tithiName(panchang.tithi.name)
                val month = PanchangDevanagari.month(panchang.month)
                views.setViewVisibility(pakshaId, View.VISIBLE)
                views.setTextViewText(pakshaId, "$pakshaName · $tithiName · $month")
                // The tithi as a big number (user-requested), front face only.
                views.setTextColor(R.id.widget_tithi_number, onAccent)
                views.setTextViewText(R.id.widget_tithi_number, PanchangDevanagari.tithiNumber(panchang.tithi))
            } else {
                // Back face shows no tithi text at all — replaced by
                // sunrise/sunset/ayana below.
                views.setViewVisibility(pakshaId, View.GONE)
            }

            val sunriseId = if (back) R.id.widget_back_sunrise else null
            val sunsetId = if (back) R.id.widget_back_sunset else null
            val ayanaId = if (back) R.id.widget_back_ayana else null
            if (back && sunriseId != null && sunsetId != null && ayanaId != null) {
                if (sunTimes != null) {
                    views.setViewVisibility(sunriseId, View.VISIBLE)
                    views.setViewVisibility(sunsetId, View.VISIBLE)
                    views.setTextColor(sunriseId, onAccent)
                    views.setTextColor(sunsetId, onAccent)
                    // sunTimes' two times can each independently belong to
                    // today or tomorrow (see SunTimes.nextSunriseSunset) — a
                    // short day label in front of each disambiguates which.
                    val sunriseVara = PanchangDevanagari.shortVara(HinduPanchang.varaFor(sunTimes.sunriseMillis))
                    val sunsetVara = PanchangDevanagari.shortVara(HinduPanchang.varaFor(sunTimes.sunsetMillis))
                    views.setTextViewText(sunriseId, "🌅 $sunriseVara ${formatClockTime12Devanagari(sunTimes.sunriseMillis)}")
                    views.setTextViewText(sunsetId, "🌇 $sunsetVara ${formatClockTime12Devanagari(sunTimes.sunsetMillis)}")
                } else {
                    views.setViewVisibility(sunriseId, View.GONE)
                    views.setViewVisibility(sunsetId, View.GONE)
                }
                // Next moonrise/moonset (user-requested), same day-label +
                // time shape as the sun lines; a line with no event in the
                // search window is hidden rather than left blank.
                listOf(
                    Triple(R.id.widget_back_moonrise, moonTimes.moonriseMillis, "🌙↑"),
                    Triple(R.id.widget_back_moonset, moonTimes.moonsetMillis, "🌙↓"),
                ).forEach { (viewId, millis, glyph) ->
                    if (millis == null) {
                        views.setViewVisibility(viewId, View.GONE)
                    } else {
                        val vara = PanchangDevanagari.shortVara(HinduPanchang.varaFor(millis))
                        views.setViewVisibility(viewId, View.VISIBLE)
                        views.setTextColor(viewId, onAccent)
                        views.setTextViewText(viewId, "$glyph $vara ${formatClockTime12Devanagari(millis)}")
                    }
                }
                views.setTextColor(ayanaId, onAccent)
                views.setTextViewText(ayanaId, PanchangDevanagari.ayana(panchang.ayana))
            }

            if (compact) return

            val nakshatraId = if (back) R.id.widget_back_nakshatra else R.id.widget_nakshatra
            val romanId = if (back) R.id.widget_back_roman else R.id.widget_roman
            views.setTextColor(romanId, onAccent)
            if (devanagari) {
                val nakshatra = PanchangDevanagari.nakshatra(panchang.nakshatra)
                views.setViewVisibility(nakshatraId, View.VISIBLE)
                views.setTextColor(nakshatraId, onAccent)
                views.setTextViewText(nakshatraId, "नक्षत्र: $nakshatra")
            } else {
                // No longer used on the back face — sunrise/sunset/ayana
                // above have their own dedicated lines now.
                views.setViewVisibility(nakshatraId, View.GONE)
            }
            views.setTextViewText(romanId, romanDate)
        }
    }
}
