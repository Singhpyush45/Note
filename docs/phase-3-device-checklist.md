# Phase 3 — device checklist (owner, ~10 minutes)

CI proves everything on an emulator. The SRS gate for Phase 3 is **INK-SH-01…08 on a real device**, and
latency (NFR-03) can only be judged with a real stylus. Please run this on your phone or tablet and report
the device + stylus model and any ✗.

## Install
1. GitHub → *Actions* → latest green **CI** run on `main` → *Artifacts* → download **apks**.
2. Install `app-release.apk` (the minified build users would get). Android asks to allow installs from
   your browser/files app once.

## Checks
Create a handwritten note with the small pen button above **New note**.

| # | Do | Expect | ✓/✗ |
|---|---|---|---|
| 1 | Write a few words with the stylus, fast and slow | Ink follows the tip without visible lag; thin/thick with pressure | |
| 2 | Rest your palm on the screen while writing | No stray marks from the palm | |
| 3 | INK-SH-01: draw a wobbly line, keep the pen still at the end (~½ s) | Snaps to a straight line (short buzz); keep dragging to move its end | |
| 4 | INK-SH-02: rough circle, hold | Clean circle (or ellipse if clearly oval) | |
| 5 | INK-SH-03: rough rectangle, square, triangle, arrow — hold each | Clean shapes about the same size as drawn | |
| 6 | INK-SH-04: write a word or signature, hold at the end | Stays handwriting | |
| 7 | INK-SH-05: right after a shape snaps, tap Undo | Your original rough stroke comes back | |
| 8 | INK-SH-06: leave the note, reopen it | Shapes in the same place and colour | |
| 9 | Pinch to zoom, two-finger scroll, then draw | Ink lands under the pen at every zoom | |
| 10 | Eraser: *Strokes* and *Area* modes; flip the stylus to its eraser end if it has one | Strokes vanish whole / only the rubbed part goes | |
| 11 | Lasso around some ink → drag it, drag the corner circle, recolour, duplicate, delete | Each works; Undo reverses each step | |
| 12 | INK-SH-08: Settings → Handwriting → Shape correction off; draw and hold | No snapping | |
| 13 | Settings → Drawing with a finger → Automatic: after using the stylus, one finger scrolls | As described | |
| 14 | Menu → Add page, Page background (lined/grid/dotted), Delete page | Works; page indicator updates | |
| 15 | Rotate the device while a note is open | Same note, same place, ink intact | |

INK-SH-07 (shapes on PDF pages) is tested in Phase 4 with PDF support.

## Report back
Device model, Android version, stylus model, and the ✗ rows (a short screen recording helps a lot).
