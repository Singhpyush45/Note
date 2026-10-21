# ADR-0009: Ink and PDF engine evaluation criteria (PROPOSED)

Status: Proposed · 2026-10-21 — resolved by Phase 3 (ink) and Phase 4 (PDF) spikes.

## Ink candidates
1. **Jetpack Ink 1.0.0** (stable; 1.1 in alpha) — `ink-authoring` (low-latency in-progress strokes),
   `ink-brush`, `ink-geometry`, `ink-rendering`, `ink-strokes`, `ink-storage`.
2. Custom renderer on `MotionEvent` + `androidx.graphics` low-latency front buffer.

Shape correction (SRS 6.2) is our own geometry code in either case: pure Kotlin classifier
(line/circle/ellipse/rectangle/square/triangle/arrow) with fitting error + confidence, unit-tested on a stroke corpus.

## PDF candidates
| Candidate | Licence | Render | Annotate/export | Note |
|---|---|---|---|---|
| Platform `PdfRenderer` | Platform | Yes | No editing (export via our own drawing) | Baseline |
| `androidx.pdf` 1.0.0-beta01 | Apache-2.0 | Yes (viewer) | Limited | Not yet stable |
| PdfBox-Android | Apache-2.0 | Partial | Can write annotations/content to a copy | Check maintenance |
| MuPDF | **AGPL** / commercial | Excellent | Excellent | AGPL forces open-sourcing the app — avoid unless licensed |
| Nutrient (PSPDFKit) | Commercial | Excellent | Excellent | Paid |

## Exit criteria
Ink: INK-SH-01…08 + latency measured on a real stylus device. PDF: PDF-01…06, original file byte-identical
after export (checksum test).
