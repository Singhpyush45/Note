# ADR-0003: Hilt for dependency injection

Status: Accepted · 2026-10-21

## Context
SRS 10.2 requires DI and explicit interfaces for PDF, ink, OCR and AI providers so implementations can be swapped.

## Options
- **Hilt (Dagger)** — compile-time graph, errors caught at build time, first-class ViewModel/WorkManager support, Google-maintained.
- Koin — runtime graph; simpler, but wiring errors appear only when the app runs.
- Manual DI — zero dependencies, but grows boilerplate quickly across 8 phases.

## Decision
Hilt 2.60.1 with KSP. Bindings live in `app/.../di`. Tests replace bindings with fakes.

## Consequences
+ Missing bindings fail the build, not the user's session.
− Slightly longer builds (annotation processing via KSP).
