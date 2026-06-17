package com.tileshell.feature.start

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperSampleSizeTest {

    @Test
    fun `zero dimensions return 1`() {
        assertEquals(1, wallpaperSampleSize(0, 0))
        assertEquals(1, wallpaperSampleSize(0, 1080))
        assertEquals(1, wallpaperSampleSize(1080, 0))
    }

    @Test
    fun `image already at or below threshold returns 1`() {
        assertEquals(1, wallpaperSampleSize(1080, 1080))
        assertEquals(1, wallpaperSampleSize(800, 600))
        assertEquals(1, wallpaperSampleSize(1920, 1080))
    }

    @Test
    fun `12MP landscape photo downsamples to 2x`() {
        // 4032×3024 shorter=3024: 3024/(1*2)=1512>=1080 → s=2; 3024/(2*2)=756<1080 → stop
        assertEquals(2, wallpaperSampleSize(4032, 3024))
    }

    @Test
    fun `12MP portrait photo downsamples to 2x`() {
        assertEquals(2, wallpaperSampleSize(3024, 4032))
    }

    @Test
    fun `50MP photo downsamples to 4x`() {
        // 8064×6048 shorter=6048: /2=3024>=1080 → s=2; /4=1512>=1080 → s=4; /8=756<1080 → stop
        assertEquals(4, wallpaperSampleSize(8064, 6048))
    }

    @Test
    fun `just above threshold does not downsample`() {
        // shorter=2161: 2161/2=1080 >= 1080 → s=2; 2161/4=540 < 1080 → stop
        assertEquals(2, wallpaperSampleSize(2161, 3000))
    }
}
