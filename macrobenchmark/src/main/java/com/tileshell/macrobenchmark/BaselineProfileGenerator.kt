package com.tileshell.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile :app ships (S26). The critical user journey is
 * launching the Start screen and scrolling the grid — the classes/methods touched
 * here are AOT-compiled on install, cutting cold start and first-scroll jank.
 *
 * Produced by `./gradlew :app:generateBaselineProfile` (writes
 * app/src/<variant>/generated/baselineProfiles/). Run on a rooted/userdebug
 * device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = TARGET_PACKAGE,
        // Also emit a startup profile (dexlayout ordering) — extra cold-start win.
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        // Exercise the dense-grid scroll path so its composables land in the
        // profile alongside the startup classes.
        device.waitForIdle()
        val midX = device.displayWidth / 2
        val top = (device.displayHeight * 0.30f).toInt()
        val bottom = (device.displayHeight * 0.75f).toInt()
        repeat(2) {
            device.swipe(midX, bottom, midX, top, 10)
            device.waitForIdle()
            device.swipe(midX, top, midX, bottom, 10)
            device.waitForIdle()
        }
    }
}
