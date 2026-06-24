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
        versionCode = 13
        versionName = "1.0.3"
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
