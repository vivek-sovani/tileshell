package com.tileshell.feature.livetiles.money

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the money tile's front shows. */
enum class MoneyTileDetails { NOTHING, LAST_PAYMENT_AND_RECEIPT }

data class MoneySettings(
    val tileDetails: MoneyTileDetails = MoneyTileDetails.NOTHING,
    val lockTransactions: Boolean = true,
    val readBankMessages: Boolean = true,
)

/**
 * The money hub's own settings, in the shared prefs file so the notification
 * listener can read them synchronously, plus a flow for the UI.
 */
object MoneyPrefs {
    private const val PREFS = "tileshell.prefs"
    private const val KEY_DETAILS = "money_tile_details"
    private const val KEY_LOCK = "money_lock"
    private const val KEY_READ = "money_read_bank_messages"

    private val _settings = MutableStateFlow<MoneySettings?>(null)

    fun settings(context: Context): StateFlow<MoneySettings?> {
        if (_settings.value == null) _settings.value = read(context)
        return _settings.asStateFlow()
    }

    fun current(context: Context): MoneySettings = _settings.value ?: read(context).also { _settings.value = it }

    fun update(context: Context, transform: (MoneySettings) -> MoneySettings) {
        val next = transform(current(context))
        prefs(context).edit()
            .putString(KEY_DETAILS, next.tileDetails.name)
            .putBoolean(KEY_LOCK, next.lockTransactions)
            .putBoolean(KEY_READ, next.readBankMessages)
            .apply()
        _settings.value = next
    }

    private fun read(context: Context): MoneySettings {
        val p = prefs(context)
        return MoneySettings(
            tileDetails = MoneyTileDetails.entries.find { it.name == p.getString(KEY_DETAILS, null) } ?: MoneyTileDetails.NOTHING,
            lockTransactions = p.getBoolean(KEY_LOCK, true),
            readBankMessages = p.getBoolean(KEY_READ, true),
        )
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
