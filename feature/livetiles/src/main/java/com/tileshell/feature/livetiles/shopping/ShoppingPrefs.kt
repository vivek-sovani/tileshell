package com.tileshell.feature.livetiles.shopping

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ShoppingSettings(
    /** Read order updates from new notifications. */
    val readOrderMessages: Boolean = true,
    /** Delivery OTPs stay hidden until the fingerprint, face or screen lock is used. */
    val lockOtps: Boolean = true,
)

/** The shopping hub's own settings, in the shared prefs file so the notification listener can read them synchronously. */
object ShoppingPrefs {
    private const val PREFS = "tileshell.prefs"
    const val KEY_READ = "shopping_read"
    const val KEY_LOCK = "shopping_lock_otp"

    private val _settings = MutableStateFlow<ShoppingSettings?>(null)

    fun settings(context: Context): StateFlow<ShoppingSettings?> {
        if (_settings.value == null) _settings.value = read(context)
        return _settings.asStateFlow()
    }

    /** The reading switch from the cache, true until it has been read (the default). */
    fun readOrderMessagesCached(): Boolean = _settings.value?.readOrderMessages ?: true

    fun current(context: Context): ShoppingSettings = _settings.value ?: read(context).also { _settings.value = it }

    /** Re-reads the stored values (after a backup restore wrote them). */
    fun reload(context: Context) {
        _settings.value = read(context)
    }

    fun update(context: Context, transform: (ShoppingSettings) -> ShoppingSettings) {
        val next = transform(current(context))
        prefs(context).edit().putBoolean(KEY_READ, next.readOrderMessages).putBoolean(KEY_LOCK, next.lockOtps).apply()
        _settings.value = next
    }

    private fun read(context: Context): ShoppingSettings {
        val p = prefs(context)
        return ShoppingSettings(readOrderMessages = p.getBoolean(KEY_READ, true), lockOtps = p.getBoolean(KEY_LOCK, true))
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
