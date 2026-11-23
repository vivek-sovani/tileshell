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
}
