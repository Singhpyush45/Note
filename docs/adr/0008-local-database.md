# ADR-0008: Local database — Room 2.8 vs Room 3 (PROPOSED)

Status: Proposed · 2026-10-21 — decided by a short spike at the start of Phase 2.

| | Room 2.8.5 | Room 3.0.3 (`androidx.room3`) |
|---|---|---|
| Maturity | Very mature, huge ecosystem | Stable since 2026, new package names |
| API | Android SQLite + optional driver | Driver-based (`SQLiteDriver`), coroutine-first, multiplatform |
| FTS | `@Fts4` entities | To verify in spike |
| Migrations/tests | `MigrationTestHelper` proven | To verify in spike |

Spike exit criteria: FTS4 external-content table works, migration test helper works, transactions across
note + FTS + tags work, and Robolectric/JVM tests run. Pick Room 3 if all pass; otherwise Room 2.8.
Either way the repository interfaces hide the choice (SRS 10.2).
