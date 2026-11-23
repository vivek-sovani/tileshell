package com.tileshell.core.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * The music + videos hub as its own Android task (experiment, switch
 * `tileshell.hubTasks` in gradle.properties).
 *
 * With the switch on, `MusicHubActivity` (`:app`, declared in
 * `app/src/hubTasks/AndroidManifest.xml`) hosts the hub in a task of its own,
 * so it has its own card in Android's recents like a music app. Every way into
 * the hub goes through [openMusic] / [musicIntent]; both answer "not available"
 * when the switch is off or the activity is not in the manifest, and the caller
 * then falls back to the in-app layer inside MainActivity, as before. The
 * activity is found by class name so the feature modules need no dependency on
 * `:app`.
 */
object HubTasks {
    /** Build switch, from gradle.properties → `tileshell.hubTasks`. */
    val ENABLED: Boolean = BuildConfig.HUB_TASKS

    /** Class name of the activity in `:app`. */
    const val MUSIC_ACTIVITY = "com.tileshell.MusicHubActivity"

    /** A hub page label (see `HUB_PIVOTS`) to open on, e.g. "podcasts" or "now playing". */
    const val EXTRA_PAGE = "com.tileshell.extra.MUSIC_HUB_PAGE"

    /** The intent that opens the music hub on [page], or null when the hub is not a task of its own. */
    fun musicIntent(context: Context, page: String? = null): Intent? {
        if (!ENABLED) return null
        val component = ComponentName(context.packageName, MUSIC_ACTIVITY)
        val declared = runCatching { context.packageManager.getActivityInfo(component, PackageManager.MATCH_DEFAULT_ONLY) }.isSuccess
        if (!declared) return null
        return Intent(Intent.ACTION_MAIN)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { if (page != null) putExtra(EXTRA_PAGE, page) }
    }

    /**
     * Opens the music hub in its own task. The tapped tile, when there is one,
     * is handed to the launch animation exactly as for an app ([LaunchOrigin]).
     * Returns false when the hub is not a task of its own (the caller then
     * opens the layer) or the start failed.
     */
    fun openMusic(context: Context, page: String? = null): Boolean {
        val intent = musicIntent(context, page) ?: return false
        val origin = LaunchOrigin.take()
        val started = runCatching { context.startActivity(intent, LaunchOrigin.optionsFor(context, origin?.bounds)) }.isSuccess
        if (started) LaunchOrigin.notifyLaunched(origin, context.packageName)
        return started
    }

    /** Class name of the activity in `:app` that hosts every other hub, one task (recents card) per hub. */
    const val HUB_ACTIVITY = "com.tileshell.HubTaskActivity"

    const val EXTRA_HUB_ID = "com.tileshell.extra.HUB_ID"
    const val EXTRA_HUB_PAGE = "com.tileshell.extra.HUB_PAGE"
    const val EXTRA_WEATHER_LAT = "com.tileshell.extra.WEATHER_LAT"
    const val EXTRA_WEATHER_LON = "com.tileshell.extra.WEATHER_LON"
    const val EXTRA_WEATHER_NAME = "com.tileshell.extra.WEATHER_NAME"

    /**
     * The intent that opens hub [id] (a [HubEntries] id, not music) in its own task, or null when hub tasks are
     * off. Every hub has its own data URI and the activity is a "document" (`documentLaunchMode=intoExisting`),
     * so each hub gets its own recents card and opening the same hub again brings that card back.
     */
    fun hubIntent(context: Context, id: String, page: String? = null, location: WeatherTile.Location? = null): Intent? {
        if (!ENABLED || HubEntries.ALL.none { it.id == id }) return null
        // Each hub has its own manifest alias (its own icon and label in recents) onto HubTaskActivity.
        val component = ComponentName(context.packageName, "com.tileshell.HubTask_$id")
        val declared = runCatching { context.packageManager.getActivityInfo(component, PackageManager.MATCH_DEFAULT_ONLY) }.isSuccess
        if (!declared) return null
        return Intent(Intent.ACTION_MAIN)
            .setComponent(component)
            .setData(android.net.Uri.parse("tileshell-hub://$id"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            .putExtra(EXTRA_HUB_ID, id)
            .apply {
                if (page != null) putExtra(EXTRA_HUB_PAGE, page)
                if (location is WeatherTile.Location.Fixed) {
                    putExtra(EXTRA_WEATHER_LAT, location.lat)
                    putExtra(EXTRA_WEATHER_LON, location.lon)
                    putExtra(EXTRA_WEATHER_NAME, location.name)
                }
            }
    }

    /** Opens hub [id] in its own task (the music hub in its own activity). False = not available, open the layer. */
    fun openHub(context: Context, id: String, page: String? = null, location: WeatherTile.Location? = null): Boolean {
        if (id == "music") return openMusic(context, page)
        val intent = hubIntent(context, id, page, location) ?: return false
        // The hub's task shows a copy of Start and grows the hub out of the tile itself (HubOrigin), so there is
        // no launch plate here and no system transition between the two identical screens.
        LaunchOrigin.take()
        val options = android.app.ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
        return runCatching { context.startActivity(intent, options) }.isSuccess
    }
}
