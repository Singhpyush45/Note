# Phase 4 — PDF import, annotation and export: report

Status: **done on CI** (build, JVM tests, lint, emulator and process-death tests green from a clean
checkout). The SRS gate also asks for PDF-01…06 by a person on a real device: see
`phase-4-device-checklist.md` (pending, like the Phase 3 checklist).

## What was built

- **Import** (SRS 7.1): Library ⋮ → *Import PDF* (system file picker), plus *Open with* and *Share* from
  other apps. The file is copied into app-private storage with its SHA-256; one ink page is created per
  PDF page, sized as the page is displayed (rotation and crop box applied). All or nothing: a failed import
  leaves no note and no file. Clear messages for: not a PDF, password-protected, over 300 MB, empty, no space.
- **Workspace** (SRS 7.1, 8.3): PDF pages are drawn under the ink. Rendering is lazy, on one background
  thread, in a few bitmap sizes, within a memory budget (1/6 of the app heap, max 128 MB); when zoomed in
  past the largest size, a sharp tile of the visible area is rendered after the gesture settles. Under
  memory pressure the cache is emptied; out of memory halves the bitmap size instead of crashing.
  Missing/damaged file → "This PDF couldn't be displayed" banner, ink still editable.
- **Navigation**: page counter (tap it) and ⋮ → *Go to page…* open a page grid; ⋮ → *Fit width* /
  *Fit page*; pinch zoom and pan as in Phase 3. (Also available for handwritten notes.)
- **Annotation**: every Phase 3 tool works on PDF pages — pen, highlighter, eraser, lasso move/resize,
  draw-and-hold shapes, undo/redo. Ink is stored in page units (1/96 in), never screen pixels.
- **Export / share** (SRS 7.3): *Export annotated PDF* (system "save as") and *Share annotated PDF*
  (share sheet via a FileProvider that exposes only `cache/shared`). The copy is written with
  PdfBox-Android: ink is appended as vector content, highlighter with real transparency. The stored
  original is only ever read (PDF-05).
- **Library**: PDF cards show the first PDF page with the ink on it.
- **Storage hygiene**: stored PDFs no note uses (after permanent delete / empty trash, or an interrupted
  import) are deleted at startup and after deletes. Files from the last 10 minutes are kept, so an import
  that is still finishing is never touched. Duplicated notes share one file.
- **Database v3** (AutoMigration 2→3, nothing dropped): `pdf_documents` table and `ink_pages.pdfPageIndex`.

## Design decisions (ADR-0009 Part B)

- **Display with Android's `PdfRenderer`** (PDFium, part of the OS: no APK size, Apache-2.0 platform code),
  **write with PdfBox-Android** (Apache-2.0). Commercial PDF SDKs were rejected for licence cost; MuPDF for
  AGPL; `androidx.pdf` is still beta.
- **One coordinate system**: a PDF page is an ink page whose size is the PDF page's displayed size. The
  mapping between page units and PDF user space (rotation 0/90/180/270, offset crop boxes) is pure Kotlin
  (`PdfPageMapping`) and was proven against PDFium on the emulator before any UI was built (the spike).
- **Export flattens** ink into page content (visible in every viewer). Editable native PDF annotations
  would need a different writer; SRS 7.3 allows flattening when documented — it is, here and in the ADR.

## Requirement coverage

| SRS | Requirement | Verified by |
|---|---|---|
| 7.1 | Import via document picker and share intents | PdfDeviceTest (real import); **e2e: ACTION_VIEW from another app's FileProvider**; Robolectric: menu entry |
| 7.1 | Lazy render, release bitmaps under pressure | PdfDeviceTest: 150-page PDF at 2560 px, cache ≤ budget, native heap growth bounded; `trim()` on memory callbacks |
| 7.1 | Page navigation, pinch zoom, pan, fit-width/fit-page, page count | PageStackTest (fit width / fit page); PdfFlowRobolectricTest (page grid jump, counter) |
| 7.1 | Original preserved; annotations stored separately | PdfDeviceTest: stored copy and user's file byte-identical (SHA-256) after import, annotate and export |
| 7.1 | Loading / corrupt / out-of-memory states without crashing | Robolectric + PdfDeviceTest: missing file → banner, no crash; bad files → reasons, no leftovers; OOM path halves size |
| 7.2 | Stable IDs, page reference, style, page-coordinate geometry | Ink model + codec (Phase 3); `pdfPageIndex` per page |
| 7.3 | Export copy with annotations in the right place; never overwrite the original | PdfMappingDeviceTest (all rotations, offset crop), PdfDeviceTest (landscape page, shapes) |
| PDF-01 | Multi-page import; all pages navigate and render | PdfDeviceTest (3 and 150 pages); Robolectric page grid |
| PDF-02 | Annotate at multiple zoom levels; ink anchored | Robolectric: shape drawn at 2× is half the page units; PdfMappingTest; flow tests |
| PDF-03 | Force-close and reopen; annotations reappear | **e2e: real `am kill`** on a PDF note opened from another app, ink visible after relaunch |
| PDF-04 | Export, reopen in an independent viewer, aligned | Exported files re-rendered with PDFium (independent of PdfBox, which wrote them): ink pixels at the drawn spot |
| PDF-05 | Export a copy; original unchanged | SHA-256 before/after in PdfMappingDeviceTest and PdfDeviceTest |
| PDF-06 | Large PDF on constrained memory | PdfDeviceTest (150 pages, memory budget); OOM fallback |
| INK-SH-07 | Zoom/pan a PDF page with corrected shapes; shape stays anchored | Robolectric: rectangle corrected at 2× zoom stored on its page in page units, unchanged after reopen; export alignment of shapes on the emulator |

## Tests (CI, clean checkout)

- JVM (unit + Robolectric): **all green**, see the CI summary for the run that adds this report.
- Emulator API 35: **17/17** — AppSmoke 2, InkDevice 2, PdfMapping 3, **PdfDevice 6**, e2e lifecycle 4
  (including the new PDF process-death test).
- Release build: minified with R8 and launched on the emulator without crashes.
- Lint: no errors; new warnings fixed. One warning inside a dependency jar is ignored with a reason
  (Bouncy Castle's unused trust-all TLS helper; the app makes no network connections).

## Known limitations (deliberate, with follow-up)

1. **Export flattens** annotations (not editable in other PDF apps). Documented (SRS 7.3).
2. **Text boxes and other markups** from SRS 7.2's planned list are not built; ink, highlighter, lines and
   recognised shapes are. Text boxes fit Phase 5 (capture) or a later PDF pass.
3. **Password-protected PDFs** are refused with a message; there is no password prompt yet.
4. **No text search inside PDFs** yet (needs text extraction/OCR — Phase 5).
5. The page grid shows page shapes and an ink marker, **not page thumbnails** (to keep memory low on long
   PDFs).
6. The **release build's export** is protected by keeping PdfBox whole in R8 (it uses reflection); no
   automated test runs an export in the minified build yet.
7. PDF rendering can't run on the JVM, so Robolectric covers the workspace around it; **rendering is tested
   on the emulator only**, with generated PDFs. Real-world PDFs → device checklist.
8. Large-PDF memory was tested on the emulator's heap, **not on a low-RAM phone**.
9. Shared annotated copies stay in the app cache for up to an hour (then deleted on the next share; Android
   may also clear the cache).
10. Imported PDFs are app-private and **not backed up** (backup is off until FR-025 / Phase 6).

## Owner actions

- Run `phase-4-device-checklist.md` (and the pending `phase-3-device-checklist.md`) on your phone/tablet
  with your stylus; report device, stylus and any ✗.

## Next phase (Phase 5 — Capture)

Attachments, scanner/OCR, audio, reminders. OCR would also unlock search inside PDFs (limitation 4).
