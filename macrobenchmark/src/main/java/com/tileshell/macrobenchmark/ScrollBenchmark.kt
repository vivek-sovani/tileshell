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
 * Scroll/drag jank on the dense Start grid (S26 / spec §3 — frame durations under
 * budget). Flings the grid up and down a few times and records [FrameTimingMetric]
 * (frameDurationCpuMs / frameOverrunMs) with the shipped baseline profile applied.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollJank() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 8,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            startActivityAndWait()
            // First run pops the "set default launcher" role dialog over Start;
            // dismiss it before the measured journey so the swipes hit the grid.
            device.pressBack()
            device.waitForIdle()
        },
    ) {
        val midX = device.displayWidth / 2
        val top = (device.displayHeight * 0.25f).toInt()
        val bottom = (device.displayHeight * 0.80f).toInt()
        repeat(4) {
            // ~40 steps = a deliberate drag (not a quick fling), so each scroll
            // reliably produces a run of frames for FrameTimingMetric.
            device.swipe(midX, bottom, midX, top, 40)
            device.waitForIdle()
            device.swipe(midX, top, midX, bottom, 40)
            device.waitForIdle()
        }
    }
}
