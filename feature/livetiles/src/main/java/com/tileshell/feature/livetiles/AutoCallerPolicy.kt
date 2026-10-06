package com.tileshell.feature.livetiles

/**
 * Who may browse and control the music hub through the (exported)
 * [AutoMediaBrowserService]: Android Auto and the assistant that answers
 * "play … on TileShell", the system, and TileShell itself. The claimed
 * package must really belong to the calling uid, so another app can't just
 * say it is Android Auto. Pure so it can be unit-tested.
 */
internal object AutoCallerPolicy {
    const val SYSTEM_UID = 1000

    private val allowedPackages = setOf(
        "com.google.android.projection.gearhead", // Android Auto
        "com.google.android.googlequicksearchbox", // Assistant (voice)
        "com.google.android.carassistant", // Assistant on the car screen
        "com.google.android.autosimulator", // Desktop Head Unit tooling
    )

    fun isAllowed(
        claimedPackage: String,
        callerUid: Int,
        packagesForUid: List<String>,
        ownPackage: String,
    ): Boolean {
        if (callerUid == SYSTEM_UID) return true
        if (claimedPackage !in packagesForUid) return false
        return claimedPackage == ownPackage || claimedPackage in allowedPackages
    }
}
