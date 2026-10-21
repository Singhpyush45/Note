# Phase 4 — device checklist (owner, ~10 minutes)

CI proves Phase 4 on an emulator (API 35) with generated PDFs. Real PDFs (scans, textbooks, forms) and a
real tablet + stylus can still surprise us, so please run this once and report the device, stylus and
any ✗. Use 2–3 of your own PDFs, ideally: one long (100+ pages), one scanned, one with landscape pages.

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

| # | Do | Expect | ✓/✗ |
|---|---|---|---|
| 1 | PDF-01: Library → ⋮ → **Import PDF**, pick a multi-page PDF | Opens as a note named after the file; all pages scroll and render; page counter `1 / N` | |
| 2 | From the Files app (or Gmail): tap a PDF → **Open with** this app; also try **Share** → this app | Same: imported and opened. Reopening the app later doesn't import it twice | |
| 3 | Tap the page counter → pick a page | Jumps to that page; pages with ink show a small pen icon | |
| 4 | ⋮ → **Fit width**, then **Fit page** | Page fills the width / the whole page is visible | |
| 5 | PDF-02: zoom in a lot, write next to a word; zoom out, write again | Ink stays next to the same word at every zoom; text gets sharp after a moment when zoomed in | |
| 6 | INK-SH-07: draw a rough rectangle on the PDF and hold | Clean rectangle; zoom/scroll — it stays on the same spot of the page | |
| 7 | Highlighter over a line of text | Text still readable through it | |
| 8 | PDF-03: swipe the app away from recents, reopen the note | All annotations are back | |
| 9 | PDF-04: ⋮ → **Export annotated PDF** → save to Downloads; open it in another viewer (Drive, Chrome, Adobe) | Ink and shapes on the right pages at the right spots, highlighter translucent | |
| 10 | PDF-05: open the original file you imported in that viewer | No ink on it — unchanged | |
| 11 | ⋮ → **Share annotated PDF** → e.g. send to yourself | The shared file has the ink | |
| 12 | PDF-06: open your largest PDF, fling quickly from first to last page and back | No crash; pages fill in as you go (blank paper for a moment is OK) | |
| 13 | Try a password-protected PDF and a non-PDF file renamed to `.pdf` | A clear message, no crash, no empty note created | |
| 14 | Library: the PDF note's card | Shows the first page with your ink on it | |

Report: device + Android version, stylus, which PDFs, and the numbers of any ✗ (a screenshot helps).
