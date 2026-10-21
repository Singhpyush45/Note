# Dependency review

Versions read from Maven metadata in CI on 2026-10-21. Only stable releases are used unless noted.
All listed libraries are Apache-2.0 (commercial use allowed) and actively maintained by Google/JetBrains/Square
unless stated otherwise.

| Dependency | Version | Purpose | Notes |
|---|---|---|---|
| Android Gradle Plugin | 9.4.1 | Build | Uses built-in Kotlin support (AGP 9) |
| Gradle | 9.8.2 | Build tool | Wrapper committed |
| Kotlin | 2.4.21 | Language + Compose compiler plugin | |
| KSP | 2.3.12 | Annotation processing (Hilt) | |
| Compose BOM | 2026.09.00 | Aligns Compose UI / Material 3 versions | |
| material-icons-core | 1.7.8 | Basic icons | Frozen upstream; replace with Material Symbols drawables when needed |
| Hilt / androidx.hilt | 2.60.1 / 1.4.0 | DI + ViewModel injection | |
| Navigation 3 | 1.2.0 | Navigation | lifecycle-viewmodel-navigation3 2.11.0 for per-screen ViewModels |
| Activity Compose | 1.13.0 | Compose host, edge-to-edge | |
| Lifecycle | 2.11.0 | ViewModel, `collectAsStateWithLifecycle` | |
| Core KTX / SplashScreen | 1.19.1 / 1.2.0 | Platform helpers, consistent splash on API 26+ | |
| DataStore Preferences | 1.2.1 | Settings persistence | 1.3.0 is RC — wait for stable |
| kotlinx-coroutines | 1.11.0 | Async | |
| kotlinx-serialization | 1.12.0 | Nav keys; later note documents | |
| JUnit4 / Truth / Turbine | 4.13.2 / 1.4.5 / 1.2.1 | Tests | |
| Robolectric | 4.17 | JVM Android tests | Runs SDK ≤36; tests pinned to 26 and 36 |
| AndroidX Test | core 1.7.0, ext-junit 1.3.0, runner 1.7.0 | Instrumented tests | |
| Room (+ testing) | 2.8.5 | Local database, FTS4 search | Added Phase 2 (ADR-0008) |
| Material 3 Adaptive Navigation 3 | 1.3.0 | Tablet list-detail layout | Added Phase 2 |
| material-icons-extended | 1.7.8 | Editor/library icons | Frozen upstream; R8 strips unused icons from release |
| Lifecycle process | 2.11.0 | App-level lifecycle | Added Phase 2 |
| UiAutomator | 2.4.0 | Process-death / force-stop e2e tests | Added Phase 2 |

## Watch list (later phases)
- Jetpack Ink 1.0.0 stable / 1.1.0-alpha — Phase 3.
- Room 3 — revisit in Phase 6 (ADR-0008).
- WorkManager 2.12.0 — Phase 5/6.
- `androidx.pdf` 1.0.0-beta01 — Phase 4 (not stable yet).
- Supabase Kotlin client — Phase 6 (licence + maintenance review then).
