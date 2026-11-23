# ADR-0008: Local database — Room 2.8

Status: **Accepted** · 2026-10-21 (start of Phase 2)

| | Room 2.8.5 | Room 3.0.3 (`androidx.room3`) |
|---|---|---|
| Maturity | Years in production, huge ecosystem | Stable since 1 Jul 2026 (3.5 months) |
| API | Android SQLite (+ optional driver) | Driver-only, coroutine-first, KMP |
| Full-text search | `@Fts4` with external content + `rebuild` | `@Fts5` (better ranking) |
| Migration testing | `MigrationTestHelper` proven | Not documented on the release page |

## Decision
Use **Room 2.8.5** with framework SQLite:
- Proven migration tooling matters more than FTS5 ranking for a single-user notes app — SRS 9.2 forbids
  destructive migrations, so migration tests must be rock solid.
- FTS4 external-content table (`notes_fts`) is kept in sync by Room-generated triggers and can be rebuilt
  with `INSERT INTO notes_fts(notes_fts) VALUES('rebuild')` (SRS 12.1 "index rebuild path").
- Results are ordered by pinned, then recency (FTS4 has no built-in relevance ranking).

Caveat recorded in code: `notes` has a TEXT primary key, so FTS rows map to SQLite's implicit `rowid`.
Never run `VACUUM` without rebuilding the FTS index afterwards.

## Revisit
Phase 6 (sync) or when FTS5 ranking is needed. Repository interfaces in `:core:domain` hide the choice,
so a switch only touches `:core:database`.
