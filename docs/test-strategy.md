# Test strategy

Maps SRS §16 to concrete tools. All layers run in CI on every push (ADR-0001).

| Layer | Tool | Runs where | Covers (grows per phase) |
|---|---|---|---|
| Unit (pure Kotlin) | JUnit4, Truth, Turbine, coroutines-test | JVM | Result types, IDs, redaction, navigation logic, ViewModels with fakes, later: shape geometry, conflict rules, export manifests |
| Data | JUnit4 + real DataStore/Room on temp files | JVM / Robolectric | Settings persistence; later DAOs, FTS, migrations, tombstones, transactions |
| UI (fast) | Compose UI test on Robolectric | JVM, simulated API 26 + 36 | Navigation, screens, accessibility semantics, state restoration |
| Instrumented | Compose UI test + AndroidJUnitRunner | Emulator API 35 in CI; owner's device | Launch smoke, recreation, theme |
| End-to-end lifecycle | UiAutomator in `:e2e` (separate process) | Emulator API 35 in CI | Real process death (`am kill`) and force-stop; later: stylus input, PDF, permissions |
| Screenshots | Robolectric native graphics | JVM | Phone + tablet, light + dark, published to `ci-output` branch |
| Static | Android Lint | CI | Accessibility, API-level misuse, resource issues |
| Performance | Macrobenchmark / baseline profiles | Phase 8 (earlier for ink/PDF spikes) | Startup, scrolling, search at 10k notes, ink latency |
| Security | Supabase RLS negative tests | Phase 6 | Cross-account access must be denied |

## Rules
- Business logic gets unit tests before UI tests.
- Every bug fix adds a regression test.
- In unit and UI tests a sleep is a bug — use idling, `waitUntil` or virtual time (Robolectric: advance the main looper).
  Exception: black-box `:e2e` tests, which can't see inside the app, wait a bounded time for it to save before killing it.
- SRS 16.2 mandatory regression scenarios are added as soon as their phase starts.

## Commands (local, Android Studio terminal)
```
./gradlew :core:common:test :core:domain:test :core:ink:test testDebugUnitTest   # unit + Robolectric
./gradlew lintDebug                   # lint
./gradlew connectedDebugAndroidTest   # needs emulator or phone connected
./gradlew :e2e:connectedCheck         # process death / force-stop (UiAutomator, own process)
```
