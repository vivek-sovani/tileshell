plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

// Macrobenchmark + Baseline Profile harness (S26). A `com.android.test` module
// that instruments :app to measure cold start / scroll jank and to generate the
// baseline profile :app consumes. Self-instrumenting so it runs the target app
// in its own process.
android {
    namespace = "com.tileshell.macrobenchmark"
    compileSdk = 36

    defaultConfig {
        // Macrobenchmark + profileable trace capture needs API 28+.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    // The baseline-profile plugin manages the matching variants
    // (`benchmarkRelease` for measurement, `nonMinifiedRelease` for generation),
    // so no manual build types are declared here (S26).
}

// Generate the profile against the connected device/emulator (no managed
// device is provisioned in this repo). Run on a clean app each iteration.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
