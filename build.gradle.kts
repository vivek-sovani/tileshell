// Top-level build file. Plugin versions live in the version catalog;
// each module opts in with the alias.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}

// Apply the shared Compose stability config (S26) to every module that runs the
// Compose compiler, so the data-layer models read by the tile grid are treated
// as stable and tiles don't over-recompose on scroll/flip.
subprojects {
    plugins.withId("org.jetbrains.kotlin.plugin.compose") {
        extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFile.set(
                rootProject.layout.projectDirectory.file("compose_stability.conf"),
            )
        }
    }
}
