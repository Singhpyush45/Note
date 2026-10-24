# ADR-0011: Text search inside documents (PDF text, OCR)

Status: **Accepted** · 2026-10-24 (Phase 5b)

## Context
FR-011 (OCR text searchable) and SRS Journey 4 (find text in OCR-indexed documents and open the page). Phase 4
left PDFs unsearchable. Constraints: offline-first (NFR-05), no OCR on the main thread (NFR-01), bounded
background work (NFR-08), user control over processing (NFR-12), no lock-in to one vendor (NFR-11).

## Decision
- **Text layer first.** PDFs are read with PdfBox (already used for export): exact text for slides, books,
  exported documents, and fast. Only pages without a real text layer are OCR'd (≤ 40 per document).
- **OCR: Google ML Kit, on the device, models bundled** (`text-recognition` Latin + `text-recognition-devanagari`,
  16.0.1, free to use). Works offline from the first launch and on phones where Play services can't download
  models; nothing leaves the phone. Cost: a larger APK. Behind `TextRecognizerEngine`, so Tesseract or another
  engine can replace it.
- **Storage:** one row per page per source (`extracted_text`, schema v5) with an FTS4 index, plus a job table
  so each source is processed once; failures/unavailable are retried after 6 h, at most 3 times. Rows go with
  their attachment/note on delete.
- **Background:** WorkManager unique work, runs queued one after another, not when the battery is low; small
  batches so stopping loses at most one document. Triggered at startup, after attaching and after importing.
- **Search:** note-text matches first, then up to 3 document matches per note with their location
  ("Page 3", "In Board.jpg"); a PDF-note hit opens at that page.
- **Control:** Settings → Privacy → "Find text in photos and PDFs" (on by default; off deletes all extracted text).

## Consequences
+ PDF search works offline, exactly for text PDFs; scans and photos become searchable.
− Larger APK (bundled models and native code for every CPU type in the test APK; Play Store splits per device).
− OCR quality depends on the photo; handwriting is not recognised (ML Kit "text" models read print).
− Long scanned PDFs are only OCR'd up to 40 pages (bounded battery use); the text layer is read for all pages.
