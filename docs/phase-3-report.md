# Phase 3 — Handwriting and draw-and-hold shape correction: report

Status: **Complete on CI (JVM, Robolectric, emulator with real Jetpack Ink). Real-device sign-off pending**
— the SRS gate is "INK-SH-01…08 pass on a real device"; the owner's checklist is in
[`phase-3-device-checklist.md`](phase-3-device-checklist.md).
Evidence: GitHub Actions on `main`; screenshots on the `ci-output` branch → `screenshots/*ink*`.

## What was built
- **Handwritten notes**: new note type with pages (A4, blank/lined/grid/dotted), stacked vertically,
  pinch-zoom and scroll; first-page thumbnails in the library; full screen on phones and tablets.
- **Tools**: pressure pen, marker, highlighter (drawn under writing), colours and three sizes per tool
  (remembered), stroke eraser and area eraser (also the stylus' eraser end), lasso select → move,
  resize (corner handle), recolour, duplicate, delete. Undo/redo for every change (200 steps).
- **Draw-and-hold shape correction** (SRS 6.2): line, arrow, circle, ellipse, rectangle, square,
  triangle. Hold ½ s (configurable 0.35/0.5/0.8 s) → the stroke snaps, with a short vibration; keep
  dragging to move a line's end or resize the shape; lift to keep it. Two undo steps: the first brings
  back your ink. Can be switched off.
- **Input**: stylus, eraser end, finger, mouse (wheel scroll, Ctrl + wheel zoom, Ctrl+Z / Ctrl+Y);
  palm rejection; "drawing with a finger": automatic (until a stylus is seen) / always / never.
- **Storage**: one database row per stroke/shape in a compact binary format (about 3 bytes per point,
  plus 2 for each of pressure, tilt and orientation when the stylus reports them), saved in the background
  in order as you draw; schema v2 migration from v1.

## Design decisions
- **ADR-0009 Part A accepted**: Jetpack Ink 1.0 for the live low-latency stroke and stroke rendering;
  our own pure-Kotlin data model, recogniser, erasers, lasso and undo (`:core:ink`, JVM-tested).
  If Ink's native code can't load, a built-in path renderer takes over instead of crashing.
- Brushes pinned to Ink's version-1 families, so saved ink looks the same after library updates.
- Every change is one `InkEdit` (removed items, added items). Undo = swap the lists (exact by
  construction); the database applies the same edit incrementally.
- Coordinates are page units (1/96 inch), never screen pixels (SRS 6.1); tolerances are in dp and
  converted at the current zoom, so gestures feel the same at any zoom.
- The recogniser was prototyped in Python, tuned on a generated corpus, then ported 1:1 (see ADR-0009).
  A smoothing change that helped noisy lines was **rejected** because it converted ~2 % of cursive
  writing — keeping handwriting safe beats recognising more shapes.

## Requirement coverage

| SRS | Requirement | Verified by |
|---|---|---|
| 6.1 | Position, time, pressure, tilt, orientation captured (stylus); finger without pressure | InkInputControllerTest, InkFlowRobolectricTest |
| 6.1 | Pen, highlighter, colour/width, stroke + area erase | Controller, eraser and flow tests; screenshots |
| 6.1 | Pan/zoom; stored in page coordinates | PageStackTest; flow test: stroke after a real pinch is stored at page scale |
| 6.1 | Incremental persistence; recover after process death | InkRepositoryTest (120 random edits, each saved and reloaded); **e2e: real `am kill`**, ink visible after relaunch |
| 6.1 | Versioned stroke schema | InkCodecTest (round trip, newer version → read-only, every truncation and random corruption never crashes) |
| 6.2.1 | Line, circle, ellipse, rectangle, square, triangle, arrow | Corpus: 400 per shape × 2 seeds, every shape ≥ 97 %; +1 dp sensor noise ≥ 95 % |
| 6.2.2 | Hold detection (time + tolerance), confidence, preserve low confidence, undoable, disableable | HoldDetectorTest, controller tests, settings |
| 6.2.3 | Vector geometry, style, page coordinates | ShapeRecognizerTest (snapping, corners < 3 dp), codec |
| INK-SH-01 | Crooked line + hold → straight | Corpus, controller test, recogniser geometry tests |
| INK-SH-02 | Rough circle → circle/ellipse | Corpus, geometry tests |
| INK-SH-03 | Rectangle/square/triangle regularised, scale kept | Corpus, geometry tests; **emulator with real Ink** (rectangle) |
| INK-SH-04 | Word/signature + hold → stays ink | Corpus: **0 conversions** of 4 800 handwriting strokes (cursive, signatures, zigzags, figure-8s, spirals, words) |
| INK-SH-05 | Undo after correction → original stroke | Controller, Robolectric and emulator tests |
| INK-SH-06 | Reopen → same geometry and style | Flow test (correct → undo → redo → reopen) |
| INK-SH-07 | Shapes on PDF pages under zoom | **Phase 4** (PDF). Same page-coordinate model; ink-page zoom mapping already tested |
| INK-SH-08 | Shape correction off → no snapping | Controller test; Settings switch |
| 6.3 | Lasso select, move, copy, delete, recolour, resize; explicit eraser modes | Controller tests; flow test (lasso move) |
| 6.3 | Undo/redo covers draw, correction, erase, move, resize, style; bounded | Random undo/redo sequences (InkEditTest), session tests; limit 200 |
| 16.2 | Ink → correct → undo → redo → restart → verify | Flow test (reopen); e2e process death (ink) |
| 8.1 | Phones and tablets | Screenshots; tablet test: ink editor uses the whole screen |
| 8.2 | Accessibility | All toolbar controls labelled and ≥ 48 dp (test); keyboard undo/redo; canvas has a description |

## Tests (CI, clean checkout)
| Suite | Tests |
|---|---|
| JVM + Robolectric (all modules) | 248, 0 failures (85 new handwriting tests in `:core:ink`, database, app) |
| Emulator API 35 — in-app instrumented (incl. real Jetpack Ink: pixel check, hold → shape) | 4 |
| Emulator API 35 — e2e lifecycle (incl. handwriting survives process death) | 3 |
| Release (R8) smoke | 1 |
| Lint | 0 errors, 0 warnings; 0 compiler warnings |

Mutation checks during development: deliberately breaking undo and the codec made the tests fail, so
they test what they claim.

## Bug found in Jetpack Ink 1.0.0 (worked around)
The handwriting process-death e2e test found a real crash: drawing a stroke and pressing Home at once
crashes the app on Ink's render thread (`IllegalStateException: Attempt to draw after renderer has been
closed` in `CanvasInProgressStrokesRenderHelperV33`). Workaround: `InkCrashGuard` absorbs only that
exception on Ink's render thread (any other crash still crashes), and the canvas creates a fresh Ink
layer when it becomes visible again. No ink is lost either way (strokes are saved on pen-up). The e2e test
now passes; remove the workaround once an Ink release fixes it.

**Follow-up (found during Phase 4/5, fixed):** the same race also has a native form that no Java guard can
catch. Ink 1.0.0's `discard()` closes the renderer on the main thread while Ink's render thread may still be
drawing; Android's renderer then aborts the process ("Cannot drawFrame with no CanvasContext"). It showed
up in 2 of 5 CI runs, read from the 1.0.0 sources. Fixed by moving to **Ink 1.1.0-alpha09**, whose source
closes the renderer on the render thread after its layers are hidden (a documented exception to the
stable-only rule; see `dependency-review.md`). `InkLifecycleStressTest` (write and leave via Back/Home,
repeatedly, under memory pressure) now guards this path on every CI run.

## Known limitations (deliberate, with follow-up)
- **Real-device latency and feel** (NFR-03) not measured yet — needs the owner's stylus device.
- No textured pencil brush yet (not among Ink 1.0's stock brushes); pen, marker and highlighter only.
- Area-eraser cuts follow the eraser edge to about ⅓ of its radius, not pixel-exact.
- Recogniser thresholds are tuned on a synthetic corpus; SRS 6.2.2 asks for usability tuning — the
  device checklist is the first round.
- Undo history lasts while the note is open (saved ink and shapes persist; the history doesn't).
- Very large pages (thousands of strokes) aren't benchmarked yet → Phase 8 macrobenchmarks.
- Handwriting isn't searchable yet (handwriting recognition → Phases 5/7). Titles are.
- Drawing in the R8 release build is covered by the owner checklist (CI's release test only launches it).

## Next phase prerequisites (Phase 4 — PDF)
- Owner's device checklist results (fixes go in before Phase 4 starts).
- PDF engine decision (ADR-0009 Part B), licence check.
