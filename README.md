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
| 2 | Core notes: editor, notebooks, tags, autosave, search, Markdown, phone + tablet layouts | Done — see [`docs/phase-2-report.md`](docs/phase-2-report.md) |
| 3 | Handwriting (ink) + draw-and-hold shape correction | Done on CI; real-device check pending — see [`docs/phase-3-report.md`](docs/phase-3-report.md) |
| 4 | PDF import and annotation | Next |

## Tech stack
Kotlin · Jetpack Compose (Material 3, adaptive list-detail) · Navigation 3 · Hilt · Room (FTS4) · DataStore · Jetpack Ink · GitHub Actions CI.
Exact versions: [`gradle/libs.versions.toml`](gradle/libs.versions.toml) and [`docs/dependency-review.md`](docs/dependency-review.md).

## Project layout
```
app/                 Android app: features, navigation, DI, data
core/common/         Pure Kotlin: results/errors, IDs, clock, logging + redaction
core/designsystem/   Theme, tokens and shared Compose components
core/domain/         Pure Kotlin: note model, block document, editor engine, Markdown, repository contracts
core/ink/            Pure Kotlin: handwriting model, storage format, shape recogniser, erasers, lasso, undo, input logic
core/database/       Room database, full-text search, repository implementations (+ committed schemas)
e2e/                 UiAutomator lifecycle tests (process death, force-stop) in their own process
docs/                Plan, ADRs (docs/adr), test strategy, dependency review
```

## Run it
**Android Studio:** File → Open → this folder → run the `app` configuration on a phone or emulator.

**Command line:**
```
./gradlew assembleDebug                  # build app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:common:test :core:domain:test :core:ink:test testDebugUnitTest   # unit + Robolectric tests
./gradlew lintDebug                      # Android Lint
./gradlew connectedDebugAndroidTest      # instrumented tests (device/emulator connected)
./gradlew :e2e:connectedCheck            # process-death / force-stop tests
```

**Without building:** open the latest green run under the *Actions* tab and download the `apks` artifact.
Screenshots of every build: the `ci-output` branch, folder `screenshots/`.

## Requirements source
`APP_NAME_Advanced_Notes_App_Final_SRS.pdf` (v1.0, 21 Oct 2026) in the claude.ai project.
