package com.tileshell.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing for the **horizontal pager** swipe (Start ↔ app list), which
 * [ScrollBenchmark] does not cover — that one drags the grid vertically, and the
 * pager's own `progress` animation only changes during a horizontal drag.
 *
 * This is the journey that exercises `StartScreen`'s per-frame state reads: the
 * pager drives an `Animatable<Float>` whose value changes every frame, and
 * anything reading it during composition (rather than deferring the read into a
 * `graphicsLayer` block) re-runs the whole `StartScreen` scope at frame rate.
 * A regression there is invisible to a vertical scroll benchmark but very
 * visible to the user, since swiping between Start and the app list is one of
 * the two most common gestures in a launcher.
 *
 * Deliberately a slow, deliberate drag (many steps) rather than a fling, so each
 * swipe produces a long enough run of frames for [FrameTimingMetric] to
 * characterise, and so the measurement reflects the drag itself rather than the
 * settle animation.
 */
@RunWith(AndroidJUnit4::class)
class PagerSwipeBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun pagerSwipeJank() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 8,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            startActivityAndWait()
            // Matches ScrollBenchmark: dismiss the default-launcher role dialog
            // if it is up, so the swipes land on the pager and not on a dialog.
            device.pressBack()
            device.waitForIdle()
        },
    ) {
        val midY = device.displayHeight / 2
        val left = (device.displayWidth * 0.10f).toInt()
        val right = (device.displayWidth * 0.90f).toInt()
        repeat(4) {
            // right -> left opens the app list; left -> right returns to Start.
            device.swipe(right, midY, left, midY, 40)
            device.waitForIdle()
            device.swipe(left, midY, right, midY, 40)
            device.waitForIdle()
        }
    }
}
