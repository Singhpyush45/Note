# Phase 8 (8a, 8b, 8c first parts) and 7d — the multimodal page: report

Status: **done on CI** — build 130 (run 40228511477): 463 JVM/Robolectric tests, 39 emulator tests, 0 failures, lint 0 errors, test APK published. Device checks by a person are pending (nothing was tried on a real
phone yet).

Source: owner request of 25 Oct (Samsung Notes screenshot): "Photo import, PDF import direct on handwritten notes;
fonts; drag and drop / copy-paste photos from anywhere (browser, gallery)".

## What was built
- **Insert button** (top bar of a handwritten note, picture-with-+ icon): Photo, Take photo, Text box, PDF pages,
  Paste.
- **Photos on the page**: placed in the middle of the shown page (or where they were dropped), at most 70 % of
  the page wide, keeping their shape. Several at once fan out. They are stored as the note's attachments (same
  storage, limits and clean-up as typed notes). The lasso selects, moves, resizes, turns, flips, duplicates and
  deletes them; the eraser leaves them alone. Drawn under the ink, so you can write on a photo.
- **PDF pages**: each page of a chosen PDF becomes a new page at the end of the note, showing that page as a
  picture (1800 px on its long side), ready to write on. Up to 100 pages per import.
- **Text boxes** (new **Text** tool, "T" in the tool bar): tap the page to write; tap a text box to edit it.
  Fonts: Sans, Serif, Mono, Handwriting, Casual, Condensed, Medium — the phone's own font families (no download),
  size 10–72, colour, bold, italic, left/centre/right. Saving empty text deletes the box. The lasso moves,
  resizes (the text scales), turns, recolours and deletes text boxes; the eraser leaves them alone.
- **Paste**: Insert › Paste, or Ctrl+V on a keyboard: a copied picture (e.g. "Copy image" in a browser, a photo
  copied from the gallery) is placed as a photo; copied text becomes a text box; a copied PDF adds its pages.
- **Drag and drop** from other apps (split screen, pop-up view, tablets, Samsung DeX): photos, PDFs and text,
  placed where they are dropped.
- Storage: **ink format 3** (photos and text boxes); written only for those items, so pages without them stay
  readable by older versions; an older version opens a page with them read-only instead of losing anything.

## Also in this round
- 7d: **rotate handle** above a lasso selection — drag to turn freely, snapping to every 15°.
- App icon zoomed out: the whole "N" sits inside the launcher's safe zone with room around it.
- Infinite scroll: a small gap (10 units) between pages, so each page still reads as one.
- Home heading "All notes" → **Notes**; **New folder** is in the + menu.

## Verification (CI)
- JVM: photos and text round-trip in format 3 (Unicode text included), older ink stays format 1, an unknown
  font from a newer version opens read-only, truncated data is caught; boxes move/scale/turn (text size scales),
  flip (photo mirrors, text stays readable); eraser ignores them, lasso selects them; selection rotate, flip,
  front/back.
- Robolectric through the real app: Insert › Paste puts copied text on the page as a text box, and a copied
  picture link as a photo (2:1 shape kept), stored as the note's attachment.
- Screenshot: a handwritten page with a text box in the Handwriting font and a photo.

## Known limits (stated, not hidden)
- Fonts are the phone's built-in families; how "Handwriting" and "Casual" look depends on the phone maker.
- PDF pages become pictures on the pages (not selectable PDF text, not a linked PDF); exporting such a note to
  PDF exports the pictures.
- Removing a photo from the page keeps its file as an attachment of the note (until the note is deleted).
- Drag and drop needs the other app to share the item (most galleries, browsers and file apps do).
- Photo export / thumbnails decode the picture at their own size; very large photos are scaled down.
- Text is edited in a dialog (not typed directly on the page).
- Still to come: ruler, gradients (7d); PDF pages inside typed notes, blank pages between PDF pages (8b); tables,
  stickers, equations (8c).

## Second round (26 Oct)

### 7d — finished parts
- **Ruler** (ruler button at the end of the tool row): a straight edge on screen. Drag its body to move it, its
  blue knob to turn it (snaps to every 15°, angle shown). Start a pen/pencil/brush/highlighter stroke next to
  either long edge and it comes out perfectly straight along that edge.
- **Box selection**: the lasso has Free / Box; Box selects everything inside a dragged rectangle.
- **Gradient fill** for shapes: Fill chip cycles none → one colour → gradient (into a neighbouring colour).
- Lasso: rotate handle (free turn), rotate 90°, flip, bring to front / send to back (first round).

### 8a — recordings on the page
- Insert › **Record audio** (microphone permission asked once) or **Audio file**: a player chip appears on the page
  (name and length). Tap the chip with any tool except the lasso to play / pause; a player bar shows position,
  seek, speed and close. The lasso moves the chip. Recordings are the note's audio attachments.

### 8b — PDF notes
- **Blank pages between a PDF's pages**: ⋮ › "Add blank page after this one"; such pages can be deleted again (the
  document's own pages can't).
- Photos, text boxes, stickers, tables and recordings work on PDF notes too.
- **Export** of a PDF note keeps all of it: added pages are inserted at their place (the size of the page before
  them), photos are embedded, text boxes and recording chips are drawn as pictures (so every font and script
  shows exactly), ink stays vector. Checked on the emulator by rendering the exported file with Android's own PDF
  renderer (page count, page size, text pixels, photo colour and position).

### 8c — first part
- **Stickers**: Insert › Sticker: 48 emoji stickers (study marks, feelings, arrows, colours), placed large; the lasso
  moves and resizes them.
- **Table grid**: Insert › Table: rows × columns (1–12 each) as ruled lines to write in; one undo removes it.

### Verification (CI)
- JVM: ruler geometry (edge snapping, projection at any angle, knob, 15° snap), box lasso, gradient fill and
  recordings stored and read back; everything from the first round.
- Emulator: exported PDF note with an added page, a text box and a photo, read back with PdfRenderer.
- A flaky race was fixed on the way: right after "Stylus detected" a finger could still draw for a moment.

### Known limits (second round)
- The ruler stays where it is on screen when the page scrolls (like a real ruler on a moving sheet).
- Shape fills aren't in the exported annotated PDF of a PDF note (outlines are); they are in handwritten-note export.
- Recording chips in an exported PDF are pictures (PDFs can't play the sound).
- Table grids are lines to write in, not a spreadsheet; typed-note tables, equations, match-style paste and the
  advanced settings (toolbar, scroll bar, links/web previews) are still to come (8c).
- Text is still typed in a dialog, not directly on the page.
