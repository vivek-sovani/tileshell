package com.tileshell.feature.livetiles

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCallerPolicyTest {
    private val own = "com.tileshell"
    private val gearhead = "com.google.android.projection.gearhead"

    @Test fun androidAutoIsAllowed() =
        assertTrue(AutoCallerPolicy.isAllowed(gearhead, 10123, listOf(gearhead), own))

    @Test fun ownPackageIsAllowed() =
        assertTrue(AutoCallerPolicy.isAllowed(own, 10050, listOf(own), own))

    @Test fun systemUidIsAllowed() =
        assertTrue(AutoCallerPolicy.isAllowed("android", AutoCallerPolicy.SYSTEM_UID, emptyList(), own))

    @Test fun unknownAppIsRejected() =
        assertFalse(AutoCallerPolicy.isAllowed("com.evil.app", 10200, listOf("com.evil.app"), own))

    @Test fun spoofedAndroidAutoNameIsRejected() =
        assertFalse(AutoCallerPolicy.isAllowed(gearhead, 10200, listOf("com.evil.app"), own))

    @Test fun noPackagesForUidIsRejected() =
        assertFalse(AutoCallerPolicy.isAllowed(gearhead, 10200, emptyList(), own))
}
