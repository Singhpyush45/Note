// Pure Kotlin: handwriting model, storage codec, shape recognition, erasers, lasso and undo.
// No Android imports, so all of it is unit-tested on the JVM (NFR-10). Rendering lives in :app.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.lint)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Template layouts are stored as JSON (owner brief §4).
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
