package com.tileshell.feature.livetiles

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import java.util.Calendar
import java.util.Locale

/** Which group a productivity app sits in on the hub's apps page. */
enum class ProductivityCategory(val label: String) {
    OFFICE("office"),
    NOTES_FILES("notes and files"),
    MEETINGS("meetings"),
    TOOLS("tools"),
}

/**
 * Known productivity apps by package. Not exhaustive (OEM and regional apps
 * vary); extend when a specific gap is reported, same convention as the
 * People Hub's `PEOPLE_APP_CATEGORIES`. The device's own calculator and clock
 * are also added at runtime from their Android roles (see
 * [resolvedToolPackages]), so an OEM's version shows up even if it isn't here.
 */
private val PRODUCTIVITY_APP_CATEGORIES: Map<String, ProductivityCategory> = buildMap {
    listOf(
        "com.microsoft.office.officehubrow", // Microsoft 365
        "com.microsoft.office.word",
        "com.microsoft.office.excel",
        "com.microsoft.office.powerpoint",
        "com.google.android.apps.docs.editors.docs",
        "com.google.android.apps.docs.editors.sheets",
        "com.google.android.apps.docs.editors.slides",
        "cn.wps.moffice_eng",
        "com.infraware.office.link", // Polaris Office
        "com.zoho.writer",
    ).forEach { put(it, ProductivityCategory.OFFICE) }
    listOf(
        "com.google.android.keep",
        "com.microsoft.office.onenote",
        "com.evernote",
        "notion.id",
        "com.samsung.android.app.notes",
        "com.google.android.apps.docs", // Drive
        "com.microsoft.skydrive", // OneDrive
        "com.dropbox.android",
        "com.adobe.reader",
        "com.adobe.scan.android",
        "com.microsoft.todos",
        "com.google.android.apps.tasks",
        "com.todoist",
        "com.ticktick.task",
    ).forEach { put(it, ProductivityCategory.NOTES_FILES) }
    listOf(
        "com.google.android.apps.tachyon", // Google Meet
        "com.google.android.apps.meetings",
        "us.zoom.videomeetings",
        "com.microsoft.teams",
        "com.cisco.webex.meetings",
        "com.gotomeeting",
    ).forEach { put(it, ProductivityCategory.MEETINGS) }
    listOf(
        "com.google.android.calculator",
        "com.sec.android.app.popupcalculator",
        "com.miui.calculator",
        "com.coloros.calculator",
        "com.oneplus.calculator",
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.google.android.apps.nbu.files",
        "com.sec.android.app.myfiles",
        "com.google.android.apps.recorder",
        "com.sec.android.app.voicenote",
    ).forEach { put(it, ProductivityCategory.TOOLS) }
}

val PRODUCTIVITY_APP_PACKAGES: Set<String> get() = PRODUCTIVITY_APP_CATEGORIES.keys

/** An installed productivity app for the hub's apps page and tile. */
data class ProductivityApp(
    val packageName: String,
    val label: String,
    val category: ProductivityCategory,
)

/**
 * The installed productivity apps ([installed], package to label; [tools] =
 * extra role-resolved calculator/clock packages), sorted most opened first
 * ([opens], last 30 days, empty without usage access), then by name. Pure.
 */
fun productivityApps(
    installed: Map<String, String>,
    opens: Map<String, Int> = emptyMap(),
    tools: Set<String> = emptySet(),
): List<ProductivityApp> =
    installed.mapNotNull { (packageName, label) ->
        val category = PRODUCTIVITY_APP_CATEGORIES[packageName]
            ?: if (packageName in tools) ProductivityCategory.TOOLS else return@mapNotNull null
        ProductivityApp(packageName, label, category)
    }.sortedWith(compareByDescending<ProductivityApp> { opens[it.packageName] ?: 0 }.thenBy { it.label.lowercase() })

/** [apps] grouped in [ProductivityCategory] order, empty groups dropped. Pure. */
fun groupProductivityApps(apps: List<ProductivityApp>): List<Pair<ProductivityCategory, List<ProductivityApp>>> =
    ProductivityCategory.entries.mapNotNull { category ->
        apps.filter { it.category == category }.takeIf { it.isNotEmpty() }?.let { category to it }
    }

/** The device's own calculator and clock apps, from their Android roles. */
fun resolvedToolPackages(context: Context): Set<String> {
    val pm = context.packageManager
    val intents = listOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR),
        Intent(AlarmClock.ACTION_SHOW_ALARMS),
    )
    return intents.mapNotNull { intent ->
        runCatching { pm.resolveActivity(intent, 0)?.activityInfo?.packageName }.getOrNull()
            ?.takeIf { it != "android" }
    }.toSet()
}

/** A video-meeting link found in a calendar event, and which service it's for. */
data class MeetingLink(val url: String, val provider: String)

private val URL_PATTERN = Regex("""https?://[^\s<>"'()\[\]]+""")

private val MEETING_PROVIDERS = listOf(
    "meet.google.com" to "google meet",
    "zoom.us" to "zoom",
    "zoom.com" to "zoom",
    "teams.microsoft.com" to "teams",
    "teams.live.com" to "teams",
    "webex.com" to "webex",
    "gotomeeting.com" to "gotomeeting",
    "gotomeet.me" to "gotomeeting",
    "whereby.com" to "whereby",
)

/**
 * The first video-meeting link in an event's location or description (Meet,
 * Zoom, Teams, Webex, GoToMeeting, Whereby), or null. Other links (a map, an
 * agenda doc) are skipped. Pure.
 */
fun meetingLinkFrom(vararg texts: String?): MeetingLink? {
    texts.forEach { text ->
        if (text.isNullOrBlank()) return@forEach
        URL_PATTERN.findAll(text).forEach { match ->
            val url = match.value.trimEnd('.', ',', ';', ':', '!', '?', '>')
            val host = hostOf(url)
            MEETING_PROVIDERS.firstOrNull { (domain, _) -> host == domain || host.endsWith(".$domain") }
                ?.let { (_, provider) -> return MeetingLink(url, provider) }
        }
    }
    return null
}

// Plain string parsing rather than Uri.parse, which is a stub under JVM unit tests.
private fun hostOf(url: String): String =
    url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':').lowercase(Locale.ROOT)

/** An upcoming timed calendar event, with its meeting link if it has one. */
data class UpcomingMeeting(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val eventId: Long,
    val link: MeetingLink?,
)

/**
 * Timed (not all-day) events still to come or under way within the next
 * [hours], soonest first. Requires READ_CALENDAR; run off the main thread.
 */
fun queryUpcomingMeetings(context: Context, nowMillis: Long = System.currentTimeMillis(), hours: Int = 24): List<UpcomingMeeting> {
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .appendPath(nowMillis.toString())
        .appendPath((nowMillis + hours * 3_600_000L).toString())
        .build()
    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.DESCRIPTION,
    )
    val meetings = mutableListOf<UpcomingMeeting>()
    runCatching {
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(3) != 0) continue
                val end = c.getLong(2)
                if (end <= nowMillis) continue
                meetings += UpcomingMeeting(
                    title = c.getString(0)?.ifBlank { null } ?: "(untitled)",
                    startMillis = c.getLong(1),
                    endMillis = end,
                    eventId = c.getLong(4),
                    link = meetingLinkFrom(c.getString(5), c.getString(6)),
                )
            }
        }
    }
    return meetings
}

/**
 * "11:30 – 12:00 · in 25 min" / "· now" / "· tomorrow" — the hub's meeting
 * time line, in device-local time. Pure given [nowMillis].
 */
fun meetingTimeLabel(startMillis: Long, endMillis: Long, nowMillis: Long): String {
    val range = "${formatClock12(startMillis)} – ${formatClock12(endMillis)}"
    val relative = when {
        startMillis <= nowMillis -> "now"
        !sameDay(startMillis, nowMillis) -> "tomorrow"
        else -> {
            val minutes = ((startMillis - nowMillis) / 60_000L).toInt()
            if (minutes < 60) "in $minutes min" else "in ${minutes / 60} hr${if (minutes / 60 == 1) "" else "s"}"
        }
    }
    return "$range · $relative"
}

private fun formatClock12(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val hour = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val minute = cal.get(Calendar.MINUTE)
    val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "am" else "pm"
    return "$hour:${minute.toString().padStart(2, '0')} $amPm"
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

/** Opens a meeting link in its app (or the browser). */
fun openMeetingLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Opens a calendar event in the calendar app. */
fun openCalendarEvent(context: Context, eventId: Long) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/events/$eventId"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Calculator apps by package, tried first since several OEM calculators
 * (Samsung's among them) don't declare the calculator role. */
internal val KNOWN_CALCULATOR_PACKAGES = listOf(
    "com.sec.android.app.popupcalculator", // Samsung
    "com.google.android.calculator",
    "com.miui.calculator", // Xiaomi
    "com.coloros.calculator", // Oppo / Realme
    "com.oneplus.calculator",
    "com.vivo.calculator",
    "com.android.calculator2",
)

/**
 * Opens a calculator: a known calculator app if one is installed, else
 * whatever declares the calculator role, else any launchable app named
 * "calculator". Returns false (and the caller says so) when none exists.
 */
fun openCalculator(context: Context): Boolean {
    val pm = context.packageManager
    val launch = KNOWN_CALCULATOR_PACKAGES.firstNotNullOfOrNull { pkg ->
        runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
    } ?: runCatching {
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
            .takeIf { it.resolveActivity(pm) != null }
    }.getOrNull() ?: runCatching {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, 0)
            .firstOrNull { it.loadLabel(pm).toString().contains("calculator", ignoreCase = true) }
            ?.activityInfo?.packageName
            ?.let { pm.getLaunchIntentForPackage(it) }
    }.getOrNull()
    return launch != null && runCatching {
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

/** Opens the clock app's timers. */
fun openTimers(context: Context) {
    runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_TIMERS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .recoverCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
