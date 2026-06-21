// Pure-Kotlin domain module: PlayerState, Track, and the Action hierarchy,
// mirrored from the music backend's protocol.py. No Android dependencies, so
// it stays portable (and could be lifted into a KMP module later).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // Exposed as `api`: downstream modules serialize these types directly.
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
