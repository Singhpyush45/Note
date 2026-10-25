plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    // Placeholder package until the final name is chosen (SRS 1.3). Renaming later is a mechanical refactor.
    namespace = "com.appname.notes"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.appname.notes"
        minSdk = 26
        targetSdk = 37
        // CI numbers every build, so each test APK is a newer version that installs over the previous one.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "0.4.0-build$build" // 0.<last finished phase>
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // One fixed key for test builds, so updates install over each other and keep your notes.
        // Not a secret and not the Play Store key: see signing/README.md.
        create("testBuilds") {
            storeFile = rootProject.file("signing/test-builds.jks")
            storePassword = "test-builds-only"
            keyAlias = "test"
            keyPassword = "test-builds-only"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("testBuilds")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // TEMPORARY: signed with the test-build key so it can be installed for testing (CI smoke test, your
            // phone). A real upload key is configured in Phase 8 (release) and never committed (SRS 13.1).
            signingConfig = signingConfigs.getByName("testBuilds")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        checkDependencies = true
        // Version-bump hints are reviewed manually in docs/dependency-review.md, not by lint.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

kotlin {
    jvmToolchain(17)
}

// Robolectric's Android 16 (API 36) sandbox needs Java 21 to *run* tests; bytecode still targets 17.
val java21Launcher = project.extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}
tasks.withType<Test>().configureEach {
    javaLauncher.set(java21Launcher)
    // Screenshot tests write here; declaring it lets the build cache restore the images too.
    outputs.dir(layout.buildDirectory.dir("screenshots"))
    // Robolectric's API 36 sandbox reflects into JDK internals.
    jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:ink"))
    implementation(project(":core:database"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.exifinterface) // photo orientation for attachments (fixed parser on all API levels)
    // On-device text recognition, models bundled so it works offline (FR-011, ADR-0011).
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.document.scanner) // Google's scanner screen, runs in Play services (no camera permission)
    implementation(libs.mlkit.text.recognition.devanagari)
    implementation(libs.androidx.work.runtime) // background document indexing (NFR-08)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // Jetpack Ink (ADR-0009): low-latency live strokes and stroke rendering. Our data model stays our own.
    implementation(libs.androidx.ink.authoring)
    implementation(libs.androidx.ink.brush)
    implementation(libs.androidx.ink.geometry)
    implementation(libs.androidx.ink.rendering)
    implementation(libs.androidx.ink.strokes)
    // PDF export (ADR-0009 Part B): writes annotations into a copy. Apache-2.0. Display uses Android's PdfRenderer.
    implementation(libs.pdfbox.android)
    implementation(libs.play.services.auth) // Google sign-in for Drive backup (Phase 9), only the drive.appdata scope

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
