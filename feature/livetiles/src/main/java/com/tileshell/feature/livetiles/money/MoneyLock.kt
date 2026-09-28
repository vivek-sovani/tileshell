package com.tileshell.feature.livetiles.money

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/**
 * Unlocks the transactions page with fingerprint, face or the screen lock.
 * Android 11+ uses the system biometric prompt with screen-lock fallback;
 * older versions show the screen-lock confirmation ([confirmIntent]). With no
 * screen lock set at all there is nothing to check against, so it opens.
 */
internal object MoneyLock {
    fun isDeviceSecure(context: Context): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true

    /** True when the system biometric prompt handles it; false means use [confirmIntent]. */
    fun usesPrompt(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun prompt(context: Context, onSuccess: () -> Unit, onFail: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return onFail()
        runCatching {
            BiometricPrompt.Builder(context)
                .setTitle("unlock transactions")
                .setSubtitle("use your fingerprint, face or screen lock")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
                .build()
                .authenticate(
                    CancellationSignal(),
                    context.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onSuccess()
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = onFail()
                    },
                )
        }.onFailure { onFail() }
    }

    @Suppress("DEPRECATION")
    fun confirmIntent(context: Context): Intent? =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
            ?.createConfirmDeviceCredentialIntent("unlock transactions", "use your screen lock")
}
