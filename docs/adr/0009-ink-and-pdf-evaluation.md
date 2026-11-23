# ADR-0009: Ink engine (accepted) and PDF evaluation (proposed)

## Part A — Ink: Accepted · 2026-10-21 (Phase 3)

### Spike findings (Jetpack Ink 1.0.0, stable since Dec 2025)
Checked against the published API signature files:
- `InProgressStrokesView` (View): `startStroke / addToStroke / finishStroke / cancelStroke` per pointer,
  `motionEventToWorldTransform` for zoom/pan, finished strokes via `InProgressStrokesFinishedListener`.
  Front-buffered low-latency rendering through `androidx.graphics`.
- `Brush.createWithColorIntArgb(StockBrushes.pressurePen() | marker() | highlighter(), colour, size, epsilon)`.
- `Stroke(brush, MutableStrokeInputBatch)` rebuilds a stroke from stored points (x, y, time, pressure, tilt,
  orientation); `CanvasStrokeRenderer.create().draw(canvas, stroke, matrix)` draws it at any zoom.
- An `InProgressStrokes` composable exists but consumes pointer input itself, so it can't support
  draw-and-hold. Partial-stroke erasing (`Stroke.subtract/split`) is only in 1.1 alpha.
- Ink uses native code, so it can't run in JVM/Robolectric tests.

### Decision
1. **Live stroke and finished-stroke rendering: Jetpack Ink 1.0.0.** We drive `InProgressStrokesView` from
   our own touch handler, which lets us see every point and detect a *hold* while the pen is still down.
2. **Data model: our own** (`:core:ink`, pure Kotlin): points in page coordinates, brush, shapes as vector
   primitives, a compact versioned binary codec. Nothing stored depends on Ink's formats (SRS NFR-11).
3. **Shape recognition, erasing (whole and partial), lasso and undo: our own pure-Kotlin code**, unit-tested
   on a generated corpus of shapes and handwriting.
4. **Fallback engine**: a simple path renderer used when Ink's native library can't load (JVM tests,
   unusual devices). The app degrades instead of crashing (SRS 1.3).

### Shape recognizer: how it was built and tested
Prototyped in plain Python against a generated corpus, then ported line-for-line to Kotlin (`:core:ink`).
- Corpus: lines with end curls and a hold tail, circles, ellipses, rectangles/squares (sharp and rounded
  corners, tilted, closing overshoot/undershoot), triangles, single-stroke arrows; and handwriting that must
  stay ink: cursive loops, signatures, zigzags, figure-eights, spirals, words.
- Results (400 strokes per type, two independent seeds): every shape ≥ 97 % recognised, **0 handwriting
  conversions**. With 1 dp extra sensor noise: every shape ≥ 95 %, still 0 conversions.
- Rejected during tuning: smoothing the line test made noisy lines pass but turned ~2 % of cursive into
  lines. Handwriting safety wins; instead the line test samples at ≥ 10 dp spacing, which fixes noise on
  short lines without that side effect.
- Cleanup after recognition: lines and arrows snap to 45° steps within 4°; rectangles square up within 7°;
  squares get equal sides; circles the mean radius; ellipses snap to horizontal/vertical within 8°; corners
  come from line fits of each side (accurate to < 3 dp on clean drawings).

### Consequences
+ Low latency and pressure-sensitive pens from a Google-maintained library; portable data.
− Two code paths (Ink and fallback) to keep visually close; Ink rendering is verified on the emulator only.
− Real latency numbers need the owner's stylus device (NFR-03).

## Part B — PDF: Accepted · 2026-10-22 (Phase 4)

### Options checked
| Candidate | Licence | Fit |
|---|---|---|
| Platform `PdfRenderer` (PDFium) | Part of Android | Fast, memory-friendly rendering on every supported version (8+). Read-only |
| `androidx.pdf` 1.0.0-beta01 | Apache-2.0 | Viewer + experimental editing; needs Android 9+; its own pen tools and writes edits into the PDF — would replace our ink (shapes, lasso, undo) and modify the document |
| PdfBox-Android 2.0.27.0 | Apache-2.0 | Reads page geometry, writes content into a copy; Android 4.4+; last release Jan 2023 (port of Apache PDFBox 2.0) |
| MuPDF | AGPL / commercial | AGPL would force open-sourcing the app |
| Nutrient (PSPDFKit) | Commercial | Paid |

### Decision
1. **Display: platform `PdfRenderer`**, one render thread, memory-bounded bitmap cache, detail tiles when zoomed.
2. **Annotations: our ink model** (Phase 3) — every PDF page is an ink page with the PDF page as background, so
   shape correction, lasso, erasers and undo work unchanged (INK-SH-07). Stored separately; the imported
   file is copied once into app storage and never modified (SRS 7.1).
3. **Export: PdfBox-Android** writes a copy with ink appended as vector content ("flattened": visible in every
   viewer, not editable as annotations elsewhere — SRS 7.3 allows this when documented).
4. Coordinates: `PdfPageMapping` (crop box, `/Rotate` 0/90/180/270, 1 unit = 0.75 pt) in `:core:ink`.

### Spike evidence (emulator, judged by PDFium)
`PdfMappingDeviceTest`: for A4, US Letter landscape, rotations 0/90/180/270 and an offset crop box, markers
drawn by PdfBox appear where our inverse mapping predicts; ink exported at a point appears at that pixel;
highlighter stays translucent; the source file's SHA-256 is unchanged.

### Consequences
+ No licence risk, works on all supported devices, one ink system everywhere.
− PdfBox-Android is not actively released; it's isolated behind `PdfAnnotationWriter` so it can be swapped.
− Export flattens ink; editable native annotations would need another writer later.
