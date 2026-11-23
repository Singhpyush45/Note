# Step 3 — LRU Bitmap Cache, Stylus Palm Rejection & ART Baseline Profile (`v36`)

## What's Included
1. `BitmapMemoryCache.kt`: Heap-aware LRU bitmap cache (`16–64 MB`) with power-of-two downsampling (`loadSampledFromFile`, `loadSampledFromUri`) and `onTrimMemory(level)` eviction.
2. `PalmRejectionFilter.kt`: `AUTO_STYLUS` / `STYLUS_ONLY` / `OFF` palm-rejection engine with 2-finger pinch/zoom passthrough, SQLite persistence (`PalmRejectionStore`), and `PalmRejectionSettingsCard()`.
3. `app/src/main/baseline-prof.txt`: ART Baseline Profile rules covering cold start, Library grid, Cover renderers, and Ink canvas.
4. `PerformanceAndPalmRejectionTest.kt`: Unit tests for downsampling and palm-rejection state transitions.

## Quick Wiring (3 Lines)
1. In `app/build.gradle.kts` (`dependencies`), add:
   ```kotlin
   implementation("androidx.profileinstaller:profileinstaller:1.4.1")
   ```
2. In your `Application` class, forward `override fun onTrimMemory(level: Int)` to `BitmapMemoryCache.onTrimMemory(level)`.
3. In `SettingsScreen.kt`, add `com.appname.notes.feature.ink.PalmRejectionSettingsCard(modifier = Modifier.padding(vertical = 8.dp))`.
