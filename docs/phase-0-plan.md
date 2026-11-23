# Phase 0 — Discovery & Implementation Plan

Status: **Done** (21 Oct 2026). Source of truth for requirements: `APP_NAME_Advanced_Notes_App_Final_SRS.pdf` (v1.0).
The product name stays **APP_NAME** until the app is complete.

## 1. Environment findings

| Item | Finding | Consequence |
|---|---|---|
| Repo | `Singhpyush45/Note`, empty at start | No existing work to protect; fresh project. |
| Claude's workspace | Linux, JDK 21, Gradle 8.14 — but **no Android SDK** and Google Maven / Maven Central are blocked | Code cannot be compiled here. |
| GitHub Actions | Ubuntu runner has Android SDK platforms up to **API 37.x**, build-tools 37.0.0, full network | **All builds and tests run in CI** (ADR-0001). |
| Owner's PC | Windows, Android Studio installed | Owner can open/run the same project locally. |

Dependency versions were read directly from Maven metadata in CI on 21 Oct 2026 (see `dependency-review.md`).

## 2. How each phase is verified

Every push runs `.github/workflows/ci.yml`:
1. Clean build of debug **and** release (R8) APKs.
2. JVM unit tests (pure Kotlin logic) + Robolectric tests (Compose UI on a simulated Android, API 26 and 36).
3. Android Lint (fails on errors).
4. Instrumented tests on a real Android emulator (API 35).
5. Debug APK uploaded as a downloadable artifact.

A phase is only marked complete when CI is green on a clean checkout (SRS 17.2).

## 3. Architecture summary

```
Compose UI  →  ViewModel  →  Use case / domain  →  Repository interface  →  Room / files / remote adapters
```
- MVVM + unidirectional data flow: UI renders a `UiState`, sends events up; ViewModel owns logic.
- Business rules live in plain Kotlin (testable without a phone) — NFR-10.
- Modules (ADR-0002): `:app`, `:core:common` (pure Kotlin), `:core:designsystem` (UI kit). More modules are split out only when a phase justifies it.

## 4. Key decisions (ADRs in `docs/adr/`)

| ADR | Decision | Status |
|---|---|---|
| 0001 | Build/verify in GitHub Actions; results surfaced as annotations | Accepted |
| 0002 | Module layout + dependency direction | Accepted |
| 0003 | Hilt for dependency injection | Accepted |
| 0004 | Navigation 3 with type-safe `@Serializable` keys | Accepted |
| 0005 | minSdk 26, compile/target SDK 37 | Accepted |
| 0006 | Logging through an interface with mandatory redaction | Accepted |
| 0007 | Note content format: versioned block document (JSON) + plain-text projection for search | **Proposed** — confirm before Phase 2 |
| 0008 | Local DB: Room 3 (stable 3.0.x) vs Room 2.8 — decide with a Phase 2 spike | **Proposed** |
| 0009 | Ink & PDF evaluation criteria (Jetpack Ink 1.0 stable is first candidate) | **Proposed** — Phase 3/4 spikes |

## 5. Phase plan and gates

| Phase | Scope | Gate (must pass before next phase) |
|---|---|---|
| 0 Discovery | Environment, plan, ADRs, test strategy | Plan reviewed by owner |
| 1 Foundation | Gradle setup, design system, theme (light/dark/dynamic), adaptive navigation shell, Hilt, DataStore settings, logging + redaction, StrictMode, CI | Clean build; unit, Robolectric, emulator and lint all green |
| 2 Core notes | Room DB, note editor (rich text blocks), notebooks, tags, pin/favourite, archive/trash, autosave, local FTS search, Markdown/text export | Offline/restart/process-death scenarios pass |
| 3 Ink spike → ink editor | Stroke capture, rendering, pressure, eraser, lasso, zoom, undo/redo, draw-and-hold shape correction | INK-SH-01…08 pass on a real device |
| 4 PDF | Import, lazy render, annotation overlays, export copy | PDF-01…06 pass; original file untouched |
| 5 Capture | Attachments, scanner/OCR, audio, reminders | Permission-denied and failure cases pass |
| 6 Sync | Supabase auth, outbox, conflicts, tombstones, backup/restore | Two-device convergence + RLS negative tests pass |
| 7 Intelligence | AI actions via server proxy, semantic search, flashcards, graph | Privacy/cost gates; all features disableable |
| 8 Release | Accessibility audit, profiling, hardening, store assets | Release checklist approved |

## 6. Phase 1 acceptance checklist

All items passed — see `phase-1-report.md`.

- [x] `./gradlew assembleDebug assembleRelease` succeeds from a clean checkout in CI.
- [x] App launches with splash screen, edge-to-edge, no theme flash.
- [x] Adaptive navigation: bottom bar on phones, navigation rail on wide screens.
- [x] Home ⇄ Settings navigation works; system Back behaves correctly; back stack survives rotation and process death.
- [x] Theme mode (System/Light/Dark) and dynamic colour (Android 12+) persist across restarts.
- [x] No disk/network I/O on the main thread (StrictMode logs in debug builds).
- [x] Logger redacts emails, tokens, secrets and long digit runs (unit-tested).
- [x] All interactive elements have accessibility labels/roles and ≥48dp touch targets.
- [x] Unit, Robolectric, instrumented and lint checks green.

## 7. Open decisions for the owner

1. ADR-0007 note format (affects Phase 2) — recommendation inside the ADR.
2. Test device(s): which Android phone/tablet and whether a stylus is available (needed by Phase 3).
3. Cloud sync opt-in vs onboarding prompt (Phase 6; no rush).
