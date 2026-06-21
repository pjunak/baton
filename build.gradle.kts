// Root build script. Module plugins are declared here with `apply false` so
// each module opts in via its own `plugins {}` block (versions resolved from
// gradle/libs.versions.toml).
//
// NOTE: Spotless + detekt are temporarily NOT applied here while we settle the
// AGP 9 / Gradle 9 upgrade. Spotless returns on its 8.x line and detekt once it
// ships a Gradle-9-compatible stable. They are formatting/analysis only — no
// effect on the app itself.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
