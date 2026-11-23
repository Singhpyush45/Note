# Step 5 — Adaptive Layouts, StrictMode Debug Guard, Predictive Back & Compose UI Tests (`v38`)

## What's Included
1. `AdaptiveLayoutSupport.kt`: Material 3 `600dp`/`840dp` responsive layout spec (`rememberAdaptiveLayoutSpec()`) scaling library grid columns (`2 -> 3 -> 4`), floating toolbar max width, and tablet two-pane eligibility.
2. `ProductionDiagnostics.kt`: Debug-only `StrictMode` guard (main-thread I/O & SQLite/Activity leak detection) and local crash breadcrumb logger (`filesDir/crash_reports/last_crash.txt`).
3. `AdaptiveLayoutTest.kt` & `ProductionRegressionUiTest.kt`: Fast JVM unit tests + Compose UI regression tests covering bottom-anchored `GlassSnackbarHost` Undo safety and compact `InkToolButtonBox` bounds.

## Quick Wiring (3 Lines)
1. In `app/src/main/AndroidManifest.xml`, add `android:enableOnBackInvokedCallback="true"` to `<application ...>` for Android 14+ predictive back gestures.
2. In your `Application.onCreate()`, call:
   ```kotlin
   ProductionDiagnostics.enableDebugStrictMode(BuildConfig.DEBUG)
   ProductionDiagnostics.installCrashBreadcrumbHandler(this)
   ```
3. Verify all unit and UI regression tests:
   ```powershell
   .\gradlew.bat testDebugUnitTest connectedDebugAndroidTest --configuration-cache
   ```
