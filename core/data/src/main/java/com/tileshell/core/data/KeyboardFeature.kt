package com.tileshell.core.data

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/**
 * The TileShell keyboard (`:feature:keyboard`) seen from the rest of the app,
 * without depending on that module — it is only in the build while the
 * `tileshell.keyboard` switch is on (a release decision, see gradle.properties).
 */
object KeyboardFeature {
    /** Kill switch, from gradle.properties → `tileshell.keyboard`. */
    val ENABLED: Boolean = BuildConfig.KEYBOARD

    /** Class name of the input-method service in `:feature:keyboard`. */
    const val SERVICE_CLASS = "com.tileshell.feature.keyboard.TileShellImeService"

    /** Turned on in Android's keyboard settings (not necessarily the one in use). */
    fun isEnabled(context: Context): Boolean = runCatching {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return false
        imm.enabledInputMethodList.any {
            it.packageName == context.packageName && it.serviceName == SERVICE_CLASS
        }
    }.getOrDefault(false)

    /** The keyboard currently chosen for typing. */
    fun isSelected(context: Context): Boolean = runCatching {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?: return false
        // Stored short-form ("com.tileshell/.feature…") — compare the parsed component.
        val component = ComponentName.unflattenFromString(id) ?: return false
        component.packageName == context.packageName && component.className == SERVICE_CLASS
    }.getOrDefault(false)
}
