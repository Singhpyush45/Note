# APP_NAME — Advanced Notes & Knowledge Workspace

Android-first, offline-first notes app: typed notes, handwriting, PDF annotation, shape correction, search and
optional AI. The final name is chosen after the app is complete; until then the code uses the `APP_NAME`
placeholder and the package `com.appname.notes`.

[![CI](https://github.com/Singhpyush45/Note/actions/workflows/ci.yml/badge.svg)](https://github.com/Singhpyush45/Note/actions/workflows/ci.yml)

## Status

| Phase | Scope | State |
|---|---|---|
| 0 | Discovery, plan, ADRs | Done — see [`docs/phase-0-plan.md`](docs/phase-0-plan.md) |
| 1 | Foundation: theme, navigation, DI, settings, logging, CI | Done — see [`docs/phase-1-report.md`](docs/phase-1-report.md) |
| 2 | Core notes: editor, notebooks, tags, autosave, search, export | Next (waiting for ADR-0007 confirmation) |

## Tech stack
Kotlin · Jetpack Compose (Material 3) · Navigation 3 · Hilt · DataStore · (Room from Phase 2) · GitHub Actions CI.
Exact versions: [`gradle/libs.versions.toml`](gradle/libs.versions.toml) and [`docs/dependency-review.md`](docs/dependency-review.md).

## Project layout
```
app/                 Android app: features, navigation, DI, data
core/common/         Pure Kotlin: results/errors, IDs, clock, logging + redaction
core/designsystem/   Theme, tokens and shared Compose components
docs/                Plan, ADRs (docs/adr), test strategy, dependency review
```

## Run it
**Android Studio:** File → Open → this folder → run the `app` configuration on a phone or emulator.

**Command line:**
```
./gradlew assembleDebug                  # build app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:common:test testDebugUnitTest   # unit + Robolectric tests
./gradlew lintDebug                      # Android Lint
./gradlew connectedDebugAndroidTest      # instrumented tests (device/emulator connected)
```

**Without building:** open the latest green run under the *Actions* tab and download the `apks` artifact.

## Requirements source
`APP_NAME_Advanced_Notes_App_Final_SRS.pdf` (v1.0, 21 Oct 2026) in the claude.ai project.
