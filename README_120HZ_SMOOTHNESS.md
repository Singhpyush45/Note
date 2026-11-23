# 120Hz Butter-Smooth Upgrade (`v39`)

## What Was Causing the Millisecond Tap/Slide Lag
1. **Slow `StiffnessMediumLow` (400f) springs + per-frame `.offset(x = ...)` relayouts** in `NotesApp.kt` and `AnimatedGlassSegmentedControl.kt` (forcing Compose to re-layout the whole bar on every frame while sliding).
2. **Display refresh rate not locked to 120Hz** on Samsung/Xiaomi/OnePlus devices when idle.
3. **Debug APK vs. Release APK**: Debug builds disable R8 and ART AOT compilation, making first taps 2x–4x slower than Release builds.

## What's Included in `v39`
- `HighRefreshRateHelper.kt`: Requests the highest native-resolution display mode (`90Hz` / `120Hz` / `144Hz`) on `MainActivity`.
- `AnimatedGlassSegmentedControl.kt` & `NotesApp.kt`: Upgraded sliding pills to GPU-only `graphicsLayer { translationX = ... }` (zero per-frame relayouts) + snappy `1050f` 120Hz springs (`~135ms` settle).

## 1-Line Wiring in `MainActivity.kt`
Inside `MainActivity.onCreate(...)`, right after `super.onCreate(savedInstanceState)`, add:
```kotlin
com.appname.notes.core.performance.HighRefreshRateHelper.enableMaxRefreshRate(this)
```
Then test with a **Release build** (`.\gradlew.bat assembleRelease --configuration-cache`) to feel true 120Hz speed!
