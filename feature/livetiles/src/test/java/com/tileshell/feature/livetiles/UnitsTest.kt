package com.tileshell.feature.livetiles

import com.tileshell.core.data.settings.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test fun autoFollowsRegion() {
        assertEquals(ResolvedUnits.IMPERIAL, Units.resolve(UnitSystem.AUTO, "US"))
        assertEquals(ResolvedUnits.METRIC, Units.resolve(UnitSystem.AUTO, "IN"))
        assertEquals(ResolvedUnits.METRIC, Units.resolve(UnitSystem.AUTO, ""))
    }

    @Test fun explicitChoiceWinsOverRegion() {
        assertEquals(ResolvedUnits.METRIC, Units.resolve(UnitSystem.METRIC, "US"))
        assertEquals(ResolvedUnits.IMPERIAL, Units.resolve(UnitSystem.IMPERIAL, "IN"))
    }

    @Test fun conversions() {
        val i = ResolvedUnits.IMPERIAL
        val m = ResolvedUnits.METRIC
        assertEquals("73°", Units.temp(23, i)); assertEquals("23°", Units.temp(23, m))
        assertEquals("32°", Units.temp(0, i)); assertEquals("-4°", Units.temp(-20, i))
        assertEquals("7 mph", Units.wind(12, i)); assertEquals("12 km/h", Units.wind(12, m))
        assertEquals("3.1 mi", Units.distance(5.0, i)); assertEquals("5.0 km", Units.distance(5.0, m))
        assertEquals("88 °F", Units.tempWithScale(31.0, 0, i))
        assertEquals("5′7″", Units.height(170, i)); assertEquals("170 cm", Units.height(170, m))
    }
}
