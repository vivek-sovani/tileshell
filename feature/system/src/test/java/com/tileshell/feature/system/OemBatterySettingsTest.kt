package com.tileshell.feature.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OemBatterySettingsTest {

    @Test
    fun `known manufacturers resolve to their documented battery screen`() {
        assertEquals(
            OemBatteryTarget("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            oemBatteryTarget("samsung"),
        )
        assertEquals(
            OemBatteryTarget("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            oemBatteryTarget("Xiaomi"),
        )
        assertEquals(
            OemBatteryTarget(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            ),
            oemBatteryTarget("HUAWEI"),
        )
    }

    @Test
    fun `matching is case and whitespace insensitive`() {
        assertEquals(oemBatteryTarget("samsung"), oemBatteryTarget("  Samsung  "))
        assertEquals(oemBatteryTarget("samsung"), oemBatteryTarget("SAMSUNG"))
    }

    @Test
    fun `unknown manufacturer resolves to null`() {
        assertNull(oemBatteryTarget("google"))
        assertNull(oemBatteryTarget("motorola"))
        assertNull(oemBatteryTarget(""))
    }
}
