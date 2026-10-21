// Pure Kotlin: note models, the block document, editor logic, Markdown and repository contracts.
// No Android imports, so all of it is unit-tested on the JVM (NFR-10).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.lint)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:common"))
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
