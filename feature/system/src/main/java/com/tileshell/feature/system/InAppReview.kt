package com.tileshell.feature.system

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Launches Play's native in-app review screen — a small overlay Play draws
 * directly over the app, with no navigation away from it. This only ever
 * renders for a build installed through a Play-associated channel (internal
 * testing, closed/open testing, or production), and even then Google applies
 * its own silent per-app quota on top of that — neither condition is
 * something an app can detect or influence. A plain adb-sideloaded debug
 * build (this project's normal local test loop) will reliably request the
 * flow successfully but show nothing at all; that's expected Play behaviour,
 * not a bug, and can only be verified for real once uploaded to a Play
 * testing track and installed from the Play Store itself.
 *
 * Falls back to opening the Play Store listing page only when the request
 * itself genuinely fails (e.g. no Play Store on the device, or an exception)
 * — never merely because nothing appeared to happen, since Play deliberately
 * never reports whether its overlay actually displayed.
 */
object InAppReview {
    fun launch(activity: Activity) {
        runCatching {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { request ->
                if (request.isSuccessful) {
                    runCatching { manager.launchReviewFlow(activity, request.result) }
                } else {
                    openStoreListing(activity)
                }
            }
        }.onFailure { openStoreListing(activity) }
    }

    private fun openStoreListing(activity: Activity) {
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${activity.packageName}"))
            .setPackage("com.android.vending")
        val opened = runCatching { activity.startActivity(marketIntent) }.isSuccess
        if (!opened) {
            val webUri = Uri.parse("https://play.google.com/store/apps/details?id=${activity.packageName}")
            runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, webUri)) }
        }
    }
}
