# ADR-0007: Note content format

Status: **Accepted** by owner · 2026-10-21

## Decision
A note body is a **versioned block document** stored as JSON:

```json
{ "schemaVersion": 1,
  "blocks": [
    { "type": "text", "id": "…", "kind": "heading1", "text": { "text": "Lecture 4", "spans": [] } },
    { "type": "text", "id": "…", "kind": "checklist", "checked": true,
      "text": { "text": "Revise chain rule", "spans": [ { "start": 8, "end": 18, "style": "bold" } ] } }
  ] }
```

- `type` = block family. Phase 2 has only `text`; later phases add `image`, `ink`, `pdfPage`.
- `kind` (text blocks) = `paragraph | heading1 | heading2 | heading3 | bullet | numbered | checklist | quote`.
  An unknown kind from a newer app version degrades to `paragraph` (text is kept).
- `spans` = inline styles over character ranges: `bold, italic, underline, strikethrough, code, link(url)`.
- An unknown block **type** is preserved byte-for-byte; the editor opens such a note read-only rather
  than risk dropping content.
- Every block has a stable ID so future sync can merge per block (SRS 11.3).
- A plain-text projection is stored alongside for search and previews.

## Editor model
The editor edits all text blocks as **one text field** (one block per line). This gives native selection,
copy/paste and keyboard behaviour across paragraphs. Block structure is kept in parallel metadata and updated
by pure, unit-tested functions in `:core:domain` (`EditorState`).

## Markdown
Export/import supports headings, lists, checklists, quotes, bold, italic, strikethrough, code and links.
Underline has no Markdown equivalent; export warns the user when a note uses it (FR-007).
