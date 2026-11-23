# ADR-0001: Build and verify in GitHub Actions

Status: Accepted · 2026-10-21

## Context
The coding agent's workspace has no Android SDK and cannot reach Google Maven or Maven Central.
SRS Appendix C requires clean builds and real test results after each meaningful change.

## Decision
- All builds/tests run in GitHub Actions (`.github/workflows/ci.yml`) on every push and PR.
- Jobs: `build-and-test` (assemble debug+release, JVM + Robolectric tests, lint) and
  `instrumented` (emulator API 35, `connectedDebugAndroidTest`).
- Because raw job logs are stored on a host the agent cannot reach, CI publishes results as
  **check-run annotations** (`.github/scripts/report.py`): test counts, each failing test with its
  message, lint errors, and the Gradle output tail on failure.
- The debug APK is uploaded as a workflow artifact so the owner can install it on a phone.
- The project also opens normally in Android Studio on the owner's PC.

## Consequences
+ Every claim of "tests pass" is backed by a CI run anyone can inspect.
− Feedback loop is minutes, not seconds. Keep changes small and focused.
