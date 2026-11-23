# ADR-0010: Attachments

Status: **Accepted** · 2026-10-24 (Phase 5a)

## Context
FR-009: attach images, files, scans and PDFs; show previews, metadata and storage errors. The text editor
(ADR-0007) edits a note as one text field, which can't hold pictures between lines.

## Decision
- **Attachments live beside the text, not inside it.** A note shows its attachments in a strip between the
  title and the body: photos as previews, other files as rows (name, type, size). The block document is
  unchanged, so older app versions and Markdown export keep working; a future "inline image" block can
  refer to an attachment by id without moving files.
- **Storage:** one row per attachment (`attachments` table, schema v4) pointing to a file copied into
  app-private storage (`files/attachments`). Files are written once and never modified. Copies of a note
  share files; a file is deleted only when no row refers to it (cleanup at startup and after permanent
  deletes, with a 10-minute grace so an attach in progress is never touched).
- **Sources without permissions:** system photo picker, document picker and the camera app (via a
  FileProvider link), plus "Share" from other apps. The app requests no storage, photo or camera permission.
- **Opening/sharing:** one file at a time through the FileProvider with a read-only grant.
- **Previews:** decoded at display size with EXIF orientation applied, PDFs show page 1; memory cache 1/16 of heap.
- **Limits and errors:** 250 MB per file; 50 MB must stay free; clear messages for unreadable, too large,
  storage full. Removing an attachment is undoable (the file stays until cleanup).

## Consequences
+ No editor rewrite; no new permissions; works offline; duplicates are cheap.
− Pictures can't be placed between paragraphs yet.
− Markdown export can't carry files; the user is told before exporting (FR-007).
− Handwritten and PDF notes can store attachments, but only text notes show the attach UI for now.
