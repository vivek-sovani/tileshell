package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
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
import com.tileshell.core.data.Paksha
import com.tileshell.core.data.PanchangDevanagari
import com.tileshell.core.data.PanchangInfo
import com.tileshell.core.data.calendarSystemFor
import com.tileshell.core.data.formatRomanDate
import com.tileshell.feature.livetiles.R
import com.tileshell.feature.livetiles.formatSelectedSystemDate
import com.tileshell.feature.livetiles.tithiMoonFraction
import java.util.concurrent.TimeUnit

/**
 * Builds + pushes the calendar-system widget's [RemoteViews]. Pure local
 * date math (same as [HinduPanchang]/[com.tileshell.core.data
 * .calendarSystemFor]'s `android.icu` formatting) — no permission, no
 * network, no cache, so — like moon phase — nothing to force-fetch, just a
 * ~30-min periodic re-render so the date rolls over at midnight.
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
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CalendarSystemWidgetRefreshWorker>(30, TimeUnit.MINUTES).build(),
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
            ids.forEach { id ->
                val systemId = WidgetConfigStore.calendarSystemId(context, id)
                val minWidthDp = manager.getAppWidgetOptions(id)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                val (accent, onAccent) = resolveWidgetAccent(context, id)
                val views = buildRemoteViews(context, id, systemId, nowMillis, accent, onAccent, isCompactWidget(minWidthDp))
                manager.updateAppWidget(id, views)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            appWidgetId: Int,
            systemId: String?,
            nowMillis: Long,
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
                setPanchangFace(views, panchang, romanDate, onAccent, devanagari = true, back = false)
                setPanchangFace(views, panchang, romanDate, onAccent, devanagari = false, back = true)
            } else {
                views.setTextColor(R.id.widget_label, onAccent)
                views.setTextColor(R.id.widget_date, onAccent)
                views.setTextColor(R.id.widget_back_label, onAccent)
                views.setTextColor(R.id.widget_back_date, onAccent)
                if (!compact) views.setInt(R.id.widget_icon, "setColorFilter", onAccent)
                views.setTextViewText(R.id.widget_label, displayName)
                views.setTextViewText(
                    R.id.widget_date,
                    formatSelectedSystemDate(systemId, nowMillis).ifBlank { "no data" },
                )
                views.setTextViewText(R.id.widget_back_date, formatRomanDate(nowMillis))
            }
            return views
        }

        private fun setPanchangFace(
            views: RemoteViews,
            panchang: PanchangInfo,
            romanDate: String,
            onAccent: Int,
            devanagari: Boolean,
            back: Boolean,
        ) {
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

            val varaId = if (back) R.id.widget_back_vara else R.id.widget_vara
            val pakshaId = if (back) R.id.widget_back_paksha else R.id.widget_paksha
            val nakshatraId = if (back) R.id.widget_back_nakshatra else R.id.widget_nakshatra
            val romanId = if (back) R.id.widget_back_roman else R.id.widget_roman

            views.setTextColor(varaId, onAccent)
            views.setTextColor(nakshatraId, onAccent)
            views.setTextColor(romanId, onAccent)
            views.setTextViewText(varaId, vara)
            views.setTextViewText(pakshaId, "$pakshaName · $tithiName · $month")
            views.setTextViewText(nakshatraId, "$nakshatraLabel: $nakshatra")
            views.setTextViewText(romanId, romanDate)
        }
    }
}
