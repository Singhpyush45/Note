# Phase 2 — Core notes: completion report

Status: **Complete** (21 Oct 2026). Evidence: GitHub Actions on `main` — build job and emulator job green.
Screenshots: `ci-output` branch → `screenshots/` (regenerated on every push to `main`).

## Requirement coverage

| ID | Requirement | Delivered | Verified by |
|---|---|---|---|
| FR-001 | Create, read, update, delete, duplicate, archive, restore; recoverable trash | ✅ | RepositoriesTest, NotesFlowRobolectricTest |
| FR-002 | Autosave without a Save button; visible save state | ✅ saves 0.7 s after typing stops, on app background, and on leaving the note; "Saved / Saving… / Unsaved / Retrying" label | NotesFlow tests, e2e |
| FR-003 | Notebooks: create, rename, reorder, delete without destroying notes | ✅ delete asks: keep notes (No notebook) or move to Trash | RepositoriesTest |
| FR-004 | Tags (many per note), pin, favourite, predictable filters | ✅ case-insensitive tags, `#exam` = `exam` | RepositoriesTest |
| FR-005 | Rich text: headings, paragraphs, lists, checklists, emphasis, links, quotes | ✅ H1–H3, bullet, numbered, checklist, quote, bold/italic/underline/strike/code/link. Inline media → Phase 5 (attachments) | EditorOpsTest (28 cases incl. 2 000 random edits), EditorScreenTest |
| FR-006 | Offline search over titles + content; filters by notebook, tag, type, date | ✅ FTS4 prefix search, highlighted snippets, Hindi supported | RepositoriesTest, SearchQueryTest, flow test |
| FR-007 | Text/Markdown export and share; say what can't be preserved | ✅ share as text, export `.md`, import `.md`/`.txt`; warns that underline is lost | MarkdownTest (CommonMark emphasis rules) |
| FR-008 | Survive rotation, process death, backgrounding | ✅ | **LifecycleE2ETest** (real `am kill` and `force-stop` on emulator), recreation tests |
| SRS 8.1 | Phones + tablets | ✅ phone: full-screen editor, bottom bar; tablet: rail + list-detail (list left, note right) | Phone/Tablet screenshot tests |
| SRS 8.2 | Accessibility | ✅ TalkBack actions for every long-press action and for ticking checkboxes; Ctrl+Enter ticks with a keyboard; headings marked; 48 dp targets | EditorScreenTest, SettingsRowsTest |
| SRS 12.1 | Index rebuild path | ✅ Settings → Maintenance → Rebuild search index | RepositoriesTest, SettingsViewModelTest |

## Design decisions made in this phase
- **ADR-0007 accepted**: block document JSON; editor edits all text blocks as one field (native selection/copy/paste) with structure kept by pure functions in `:core:domain`.
- **ADR-0008 accepted**: Room 2.8.5 + FTS4 (proven migration tooling); Room 3 revisited in Phase 6.
- Notes in a newer format or with unreadable content open **read-only**; nothing is ever overwritten.
- Empty notes the user never typed into are discarded automatically.
- Permanent delete keeps a content-free tombstone row for future sync.
- Card previews keep list markers ("☐ Milk") via a stored `preview` column.

## Modules after Phase 2
`:app`, `:core:common`, `:core:designsystem`, `:core:domain` (pure Kotlin), `:core:database` (Room), `:e2e` (UiAutomator, own process).

## Tests (CI, clean checkout)
| Suite | Tests |
|---|---|
| JVM + Robolectric (all modules) | 163, 0 failures |
| Emulator API 35 — in-app instrumented | 2 |
| Emulator API 35 — e2e lifecycle (process death, force-stop) | 2 |
| Release (R8) smoke: install, launch, alive, no crash | 1 |
| Lint | 0 errors, 0 warnings from our code |

Stability: a collision between the in-app instrumented tests and the e2e tests (Gradle ran both in parallel
on one emulator) was found and fixed; they now run sequentially. Two consecutive identical CI runs were
fully green afterwards.

## Known limitations (deliberate, with owner of follow-up)
- Images/attachments inside notes → Phase 5 (FR-009).
- List indentation (nested bullets) not yet supported; the data model reserves room for it.
- Markdown can't express *crossing* styles (bold overlapping italic mid-word); they export balanced but may
  re-import slightly differently. Nested styles round-trip exactly.
- FTS4 orders results by pinned + recency, not relevance (ADR-0008).
- Schema v1 changed during Phase 2 (added `preview`). Pre-release only; from now on every schema change ships
  with a migration test (CI fails if a changed schema isn't committed).
- Migration test harness lands with the first real migration (expected in Phase 3: ink tables).
- `actions/upload-artifact` Node 20 warning; one Gradle deprecation from a third-party plugin.

## Security & privacy
All data local; no network permission. Cloud backup disabled on all Android versions until FR-025.
Imports limited to 2 MB text, binary files rejected. Link dialog accepts only http(s)/mailto/tel.

## Next phase prerequisites (Phase 3 — Ink spike → ink editor)
- Owner's stylus device model (for latency measurement and pressure/tilt checks).
- Spike: Jetpack Ink 1.0 vs custom renderer (ADR-0009), then draw-and-hold shape correction (SRS 6.2).
