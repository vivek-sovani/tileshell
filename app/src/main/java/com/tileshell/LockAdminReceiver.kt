package com.tileshell

import android.app.admin.DeviceAdminReceiver

/** Minimal device-admin receiver — no callbacks needed; the policy declaration is the feature. */
class LockAdminReceiver : DeviceAdminReceiver()
