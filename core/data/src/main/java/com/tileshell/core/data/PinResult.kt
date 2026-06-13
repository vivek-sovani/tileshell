package com.tileshell.core.data

/** Outcome of pinning an app from the app list (FR-5), driving the user toast. */
enum class PinResult {
    /** A new tile was added to Start. */
    PINNED,

    /** The app was already pinned; nothing changed. */
    ALREADY_ON_START,
}
