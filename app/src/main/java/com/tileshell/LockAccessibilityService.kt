package com.tileshell

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RequiresApi

class LockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}

    companion object {
        @Volatile private var instance: LockAccessibilityService? = null

        /**
         * True only while this exact process holds a live, bound instance —
         * the fast path for actually invoking a global action. Deliberately
         * NOT the signal for "has the user enabled this in Settings": any
         * process restart (a crash, or an OEM battery/RAM manager killing the
         * background process — confirmed active on real hardware alongside
         * this app) resets this to null even though the user never touched
         * the setting, and the OS hasn't necessarily rebound it yet by the
         * time the next gesture fires. See [isEnabledInSettings] for that.
         */
        fun isConnected(): Boolean = instance != null

        /**
         * The real, system-level answer to "did the user turn this on" —
         * checked the same way [com.tileshell.feature.livetiles
         * .NotificationAccess.isEnabled] checks notification-listener access,
         * rather than trusting the in-process [instance] flag above. Used to
         * tell "genuinely not enabled yet" (show the Play-required disclosure
         * before sending the user to Settings) apart from "enabled, just not
         * bound in this fresh process yet" (rebinds on its own momentarily —
         * showing the same disclosure here would be a false nag, exactly what
         * users reported as "accessibility setting asked frequently").
         */
        fun isEnabledInSettings(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as? AccessibilityManager ?: return false
            return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any {
                    it.resolveInfo.serviceInfo.packageName == context.packageName &&
                        it.resolveInfo.serviceInfo.name == LockAccessibilityService::class.java.name
                }
        }

        @RequiresApi(Build.VERSION_CODES.P)
        fun lockScreen(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true

        fun showRecents(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) == true

        fun expandNotifications(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true
    }
}
