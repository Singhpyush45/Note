# Production Build Hygiene & R8 Setup (`v34`)

## Before You Extract (Critical Precondition)
Make sure you have already run **at least one build with `v33`** and committed your working Kotlin files in Git (`git status` clean) so that every patch (`InkTools.kt`, `Theme.kt`, `SettingsScreen.kt`, `NoteEditorScreen.kt`, `PlannerScreen.kt`, `InkEditorScreen.kt`, `LibraryViewModel.kt`, `Wallpaper.kt`) is permanently saved on disk before removing the build-time source rewriter.

## Step 1 — Extract `NOTEflow-Production-Build-Hygiene-v34.zip`
Extract into your project root (`D:\Note-Argon4-Updated-Source`), overwriting:
- `build.gradle.kts` (removes the 1,671-line source rewriter)
- `gradle.properties` (enables Gradle Configuration Cache, Build Cache, Parallel Builds, and R8 Full Mode)
- `app/proguard-rules.pro` (production R8 rules for Room, Hilt, Kotlinx Serialization, Compose, Coroutines, and release log stripping)

## Step 2 — Enable R8 & Resource Shrinking in `app/build.gradle.kts`
Either run `.\Enable-Production-R8.ps1` (which backs up `app/build.gradle.kts` first), or ensure your `buildTypes` block inside `app/build.gradle.kts` looks like this:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
    }
}
```

## Step 3 — Verify Configuration Cache, Unit Tests & Release APK
Run from `D:\Note-Argon4-Updated-Source`:

```powershell
.\gradlew.bat clean testDebugUnitTest assembleRelease --configuration-cache
```

Then run it a **second time** — you should see `Reusing configuration cache.` in Gradle's output and significantly faster build times.
