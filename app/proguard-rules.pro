# TileShell R8 rules — supplement the AGP defaults + library consumer rules.
#
# Libraries that ship their own consumer ProGuard (no extra rules needed here):
#   Room, DataStore, WorkManager base, Compose runtime, Kotlin coroutines,
#   Lifecycle, Activity-Compose, ProfileInstaller.
#
# What we DO need to add:

# ── WorkManager workers ────────────────────────────────────────────────────────
# WorkManager resolves Worker subclasses by their fully-qualified class name
# (stored in the work request). R8 would rename them, breaking deserialization.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Room entities and DAOs ─────────────────────────────────────────────────────
# KSP-generated _Impl classes derive SQL table names from @Entity class names.
# The AGP 8+ built-in rules already keep @Entity/@Dao/@Database, but belt-and-
# suspenders for any KSP-generated implementation classes under the db package.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# ── Kotlin coroutines ──────────────────────────────────────────────────────────
# Suppress warnings about the debug agent (not included in release builds).
-dontwarn kotlinx.coroutines.debug.*

# ── javax.xml DOM (RSS parser) ─────────────────────────────────────────────────
# RssFeed.kt uses javax.xml.parsers.DocumentBuilderFactory — an Android system
# class not in our APK. R8 may warn about missing JDK-only subclasses; silence.
-dontwarn javax.xml.transform.**
-dontwarn org.w3c.dom.bootstrap.**

# ── Accessibility & Device Admin (declared in manifest, kept by AGP) ──────────
# Belt-and-suspenders for the two custom components that must survive by name.
-keep class com.tileshell.LockAccessibilityService { *; }
-keep class com.tileshell.LockAdminReceiver { *; }
-keep class com.tileshell.feature.livetiles.TileNotificationListenerService { *; }

# ── Attributes needed for stack traces (optional but good for crash reporting) ─
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
