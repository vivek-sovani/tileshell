package com.tileshell.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start timing for the Start screen (S26 / spec §3 — target ≤ 800 ms
 * timeToInitialDisplay). Runs twice so the win from the baseline profile is
 * visible: [none] is the worst case (JIT only), [baselineProfile] is what ships.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun coldStartupBaselineProfile() =
        startup(CompilationMode.Partial(warmupIterations = 0))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        compilationMode = mode,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }
}

internal const val TARGET_PACKAGE = "com.tileshell"
