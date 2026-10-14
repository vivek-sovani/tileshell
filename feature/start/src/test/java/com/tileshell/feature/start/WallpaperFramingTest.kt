package com.tileshell.feature.start

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperFramingTest {
    @Test fun narrowWindowKeepsSavedFraming() {
        assertEquals(WallpaperFraming(0.2f, 0.7f, 1.6f), wallpaperFramingFor(4000f, 2250f, false, 0.2f, 0.7f, 1.6f))
    }

    @Test fun wideWindowShowsLandscapePhotoWholeAndCentred() {
        assertEquals(WallpaperFraming(0.5f, 0.5f, 1f), wallpaperFramingFor(4000f, 2250f, true, 0.2f, 0.7f, 1.6f))
    }

    @Test fun widePortraitPhotoKeepsFocalPointButDropsZoom() {
        assertEquals(WallpaperFraming(0.2f, 0.7f, 1f), wallpaperFramingFor(2250f, 4000f, true, 0.2f, 0.7f, 1.6f))
    }
}
