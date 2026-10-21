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

## Part B — PDF: Proposed (resolved in Phase 4)

| Candidate | Licence | Render | Annotate/export | Note |
|---|---|---|---|---|
| Platform `PdfRenderer` | Platform | Yes | No editing (export via our own drawing) | Baseline |
| `androidx.pdf` 1.0.0-beta01 | Apache-2.0 | Yes (viewer) | Limited | Not yet stable |
| PdfBox-Android | Apache-2.0 | Partial | Can write annotations/content to a copy | Check maintenance |
| MuPDF | **AGPL** / commercial | Excellent | Excellent | AGPL forces open-sourcing the app — avoid unless licensed |
| Nutrient (PSPDFKit) | Commercial | Excellent | Excellent | Paid |

Exit criteria: PDF-01…06 pass, original file byte-identical after export (checksum test).
Ink annotations on PDF pages reuse the Phase 3 page/ink model with a PDF page as background.
