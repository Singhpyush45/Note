# ADR-0002: Modules and dependency direction

Status: Accepted · 2026-10-21

## Decision
Start with three Gradle modules and split more only when build time, ownership or isolation justifies it
(SRS Appendix A note).

| Module | Type | Contents | May depend on |
|---|---|---|---|
| `:core:common` | Pure Kotlin/JVM | `AppResult`/`DomainError`, `AppClock`, `IdGenerator` (UUIDv7), `AppLogger` + `LogRedactor`, `DispatcherProvider` | coroutines only |
| `:core:designsystem` | Android library | Theme, colour/type/spacing tokens, shared components | Compose |
| `:app` | Android app | Features (`feature/*`), data (`core/*` packages), DI, navigation | both above |

Inside `:app`, packages mirror SRS Appendix A (`core/settings`, `core/logging`, `feature/home`, `feature/settings`, `navigation`, `di`).

Rules (SRS 10.2):
- Composables never call repositories or remote APIs directly — only ViewModels do.
- ViewModels depend on repository **interfaces**, never on Room/DataStore/Supabase types.
- `:core:common` must stay free of Android imports so domain logic is testable on the JVM.

Planned splits: `:core:database` (Phase 2), `:core:ink` (Phase 3), `:core:pdf` (Phase 4), `:core:sync` (Phase 6).
