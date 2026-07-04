import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Consumes the baseline profile produced by :macrobenchmark (S26).
    alias(libs.plugins.baselineprofile)
}

// Signing: reads from key.properties (NOT checked into git — see key.properties.template).
// When absent (dev machines / CI without credentials) release falls back to the
// debug keystore so local release builds and APK comparisons still work.
val keystoreFile = rootProject.file("key.properties")
val keystoreProps = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}

android {
    namespace = "com.tileshell"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tileshell"
        minSdk = 26
        targetSdk = 36
        // versionCode: 10 = v1.0; patches → 11, 12 …; v1.1 → 20, etc.
        // (13/1.0.3 was uploaded to Play then discarded; Play burns the code, so → 14.)
        // v1.1 = 20 (category folders + in-folder tile editing).
        // v1.1.1 = 21 (folder-merge size fixes, operable music controls, folder badges).
        // v1.2 = 30 (grid columns 4/5/6, refined editing/merge, folder fixes,
        //   small-weather temp, Outfit default, Bing daily wallpaper).
        // v1.3 = 40 (per-tile colour, tile colour from app icon, adjustable tile spacing,
        //   battery: network constraints on background workers, reduced media poll).
        // v1.4 = 50 (widget stacks, large tiles 3×3, landscape two-panel, notification
        //   tile content improvements, clock alarm priority, staggered stack rotation).
        // v1.4.1 = 51 (folder/stack X button opens overlay instead of deleting all tiles).
        // v1.5 = 60 (backup & restore: auto-save, visual layout history, export/import;
        //   personalize navigation shows one screen at a time; fresh installs default to
        //   no wallpaper + solid Nokia-blue tiles; app-list search-clear fix).
        // v1.5.1 = 61 (widget stack fixes: per-member tile colour during rotation, merging
        //   a liveOnly tile like weather into a stack no longer drops it; per-tile colour
        //   picker + full small/medium/wide/large resize cycle inside a folder overlay;
        //   folder overlay "make wide/large stack" shortcut; calendar/weather tile layout
        //   fixes at WIDE size).
        // v1.6.0 = 70 (quick search: two-finger swipe-down opens apps/contacts/web search,
        //   contact call/message/pin-to-start, recent searches + suggested apps; apps with a
        //   pending notification surface in the app list's recent section even when unpinned;
        //   hide/unhide apps from the app list; Samsung Gallery "story" notification fix).
        // v1.6.1 = 71 (wallpaper-behind-tiles / glass transparency fix: widget stack members
        //   and closed-folder mini-grid cells with an individually selected colour rendered
        //   fully opaque instead of showing the wallpaper/glass through them like other tiles).
        // v1.6.2 = 72 (in-app update check + flexible-update prompt banner on Start; wallpaper
        //   crop overlay gains pinch-to-zoom alongside drag-to-position; wallpaper slideshow
        //   rotates the background through multiple picked photos on a 15m/30m/1h/3h timer).
        // v1.7.0 = 80 (personalize wallpaper section reorganized into a none/photo/slideshow/
        //   bing/stock type selector, with blur/wallpaper-behind-tiles moved there as an
        //   "effects" subsection and tile style split into glass/colour & fill/shape & spacing
        //   subgroups; quick search can ask ChatGPT, Gemini, Claude, or Perplexity; clock tile
        //   date no longer clipped at 5/6 grid columns).
        versionCode = 80
        versionName = "1.7.0"
    }

    if (keystoreFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 full-mode shrinking + obfuscation. Rules in proguard-rules.pro
            // supplement the AGP defaults and each library's consumer rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    // The baseline-profile plugin auto-creates the `benchmarkRelease` (the
    // non-debuggable, profileable, debug-signed target the Macrobenchmark runs
    // against) and `nonMinifiedRelease` (profile generation) variants from
    // `release`, so no manual benchmark build type is needed here (S26).

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Every TileShell module is wired into the app so the whole graph
    // compiles as part of :app:assembleDebug.
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(project(":feature:start"))
    implementation(project(":feature:livetiles"))
    implementation(project(":feature:applist"))
    implementation(project(":feature:personalize"))
    implementation(project(":feature:system"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Installs the bundled baseline profile on first run (S26).
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)

    // The baseline profile artifact consumed at build time.
    baselineProfile(project(":macrobenchmark"))
}
