# Phase 3 — device checklist (owner, ~10 minutes)

CI proves everything on an emulator. The SRS gate for Phase 3 is **INK-SH-01…08 on a real device**, and
latency (NFR-03) can only be judged with a real stylus. Please run this on your phone or tablet and report
the device + stylus model and any ✗.

## Install
1. On your phone or tablet, open **https://github.com/Singhpyush45/Note/releases/tag/test-build** and tap
   **APP_NAME-test.apk** (no GitHub login needed). It is the newest build that passed every CI test.
   The same file is also sent in our chat when a phase is ready.
2. Open the downloaded file. The first time, Android asks to allow installs from your browser/Files app:
   allow it (Settings → *Install unknown apps*), then **Install**. If Play Protect warns about an unknown
   developer, choose *Install anyway* (it is a test build, not from the Play Store).
3. Later builds install **over** this one and keep your notes. Check the version in Settings → *About*
   (e.g. `0.4.0-build57`) when you report results.

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
