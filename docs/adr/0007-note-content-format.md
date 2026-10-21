# ADR-0007: Note content format (PROPOSED — confirm before Phase 2)

Status: Proposed · 2026-10-21

## Problem
FR-005 needs headings, lists, checklists, emphasis, links, quotes and inline media. Later phases add ink pages,
PDF pages and audio markers to the same note. Search (FR-006) needs plain text. Sync (FR-016) must not merge
rich text naively (SRS 11.3).

## Options
1. **Markdown string** — simple, portable; but cursor/format editing in Compose is hard, and checklists/media
   become text parsing problems.
2. **HTML** — heavy, unsafe to render, poor fit for Compose.
3. **Versioned block document (JSON)** — a note is an ordered list of blocks, each with a stable ID:
   `Paragraph`, `Heading(level)`, `BulletItem`, `NumberedItem`, `Checklist(checked)`, `Quote`, `Image(attachmentId)`,
   later `InkPage(pageId)`, `PdfPage(...)`. Text blocks hold text + style spans (bold/italic/underline/strike/code/link).

## Recommendation: option 3
- Each block maps to one Compose text field → editing stays responsive in long notes.
- Stable block IDs make future sync conflict handling per-block instead of per-note.
- `schemaVersion` field allows migrations (SRS 6.1 asks the same for ink).
- A plain-text projection is stored alongside for FTS search and snippets.
- Markdown export/import is a converter, with a warning when a feature can't be represented (FR-007).

Serialization: `kotlinx.serialization` JSON, stored in a Room column; large media stays in files (SRS 9.2).
