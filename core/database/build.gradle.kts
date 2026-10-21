// Room database + repository implementations (ADR-0008). Only this module knows about SQLite.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.appname.notes.core.database"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Exported schemas are committed so migrations can be tested against every past version (SRS 16.2).
// The Room plugin also exposes them to MigrationTestHelper in tests.
room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)
}

val java21Launcher = project.extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}
tasks.withType<Test>().configureEach {
    javaLauncher.set(java21Launcher)
    jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
}

dependencies {
    api(project(":core:domain"))
    api(libs.room.runtime) // NotesDatabase (a RoomDatabase) is part of this module's public API
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.room.testing)
}
