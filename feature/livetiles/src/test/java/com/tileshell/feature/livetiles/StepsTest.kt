package com.tileshell.feature.livetiles

import com.tileshell.core.data.StepsPrefs
import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveStepsTest {

    @Test
    fun `no baseline yet starts today's count at zero and baselines to the current reading`() {
        val result = resolveSteps(currentCounter = 5432f, baseline = null, todayEpochDay = 100)
        assertEquals(0, result.stepsToday)
        assertEquals(StepsPrefs.Baseline(5432f, 100), result.newBaseline)
    }

    @Test
    fun `same day, counter grown — steps is the delta`() {
        val baseline = StepsPrefs.Baseline(counter = 1000f, epochDay = 100)
        val result = resolveSteps(currentCounter = 3500f, baseline = baseline, todayEpochDay = 100)
        assertEquals(2500, result.stepsToday)
        assertEquals(baseline, result.newBaseline)
    }

    @Test
    fun `a new day resets the baseline to today's reading, zero steps`() {
        val baseline = StepsPrefs.Baseline(counter = 9000f, epochDay = 100)
        val result = resolveSteps(currentCounter = 9050f, baseline = baseline, todayEpochDay = 101)
        assertEquals(0, result.stepsToday)
        assertEquals(StepsPrefs.Baseline(9050f, 101), result.newBaseline)
    }

    @Test
    fun `a reboot (counter now lower than baseline) resets the same way a new day does`() {
        val baseline = StepsPrefs.Baseline(counter = 9000f, epochDay = 100)
        val result = resolveSteps(currentCounter = 40f, baseline = baseline, todayEpochDay = 100)
        assertEquals(0, result.stepsToday)
        assertEquals(StepsPrefs.Baseline(40f, 100), result.newBaseline)
    }
}

class StepsGoalProgressTest {

    @Test
    fun `progress is the fraction of the goal reached`() {
        assertEquals(0.5f, stepsGoalProgress(5000, goal = 10_000), 0.001f)
    }

    @Test
    fun `progress clamps at 1 past the goal`() {
        assertEquals(1f, stepsGoalProgress(15_000, goal = 10_000), 0.001f)
    }

    @Test
    fun `zero steps is zero progress`() {
        assertEquals(0f, stepsGoalProgress(0), 0.001f)
    }
}

class StepsIconMappingTest {

    @Test
    fun `steps icon key maps to the steps face at medium and up`() {
        assertEquals(LiveFace.STEPS, LiveFace.forIconKey("steps", TileSize.MEDIUM))
        assertEquals(LiveFace.STEPS, LiveFace.forIconKey("steps", TileSize.WIDE))
    }

    @Test
    fun `steps stays out of forIconKey at small — handled by its own small-face branch instead`() {
        assertNull(LiveFace.forIconKey("steps", TileSize.SMALL))
    }

    @Test
    fun `steps never flips`() {
        assertTrue(!LiveFace.STEPS.flips)
    }
}
