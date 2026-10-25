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

## Third round (26 Oct, owner list) — 8c finished

CI build 142 (run 40239791588): 472 JVM/Robolectric + 39 emulator tests, 0 failures, lint 0 errors, APK published.
Not yet tried on a real phone (see docs/phase-8-device-checklist.md).

### Owner's list (handwritten notes)
- **Full screen**: button at the left of the tool options. The title bar and the phone's status/navigation bars go
  (swipe from the edge shows them for a moment); a slim **floating tool bar** stands upright on the side, in
  portrait and landscape. Drag its handle to put it anywhere; one button turns it across/upright. It has: leave full
  screen, undo, redo, all tools, ruler, tool options, Insert. Tapping the chosen tool again shows its options (sizes,
  colours, eraser mode …) in a pill at the bottom. Back leaves full screen.
- **Undo / redo in the tool bar** (always visible at its left; no longer in the title bar).
- **Type straight on the page**: Text tool, tap the page → a blue cursor right there, type with the keyboard; the
  text sits at the page's zoom. A slim bar at the top: Done ✓, font, size −/+, bold, italic, alignment, colours,
  maths symbols, delete. The blue four-arrow handle moves the box. Tapping elsewhere, drawing, or Back finishes; empty
  text removes the box. Tap a text box with the Text tool to edit it the same way. (The old text dialog is gone.)
- **Press and hold on the page** (with a finger when fingers scroll, or with the Text tool): a menu with Paste, Write
  text (or Edit text on a text box), Select, Copy / Copy text, Delete — what fits the spot.
- **Copy / paste ink**: Lasso › Copy, or the press-and-hold Copy; Paste brings it back (into this or another note;
  photos get their own copy). Copied text boxes also go to the phone's clipboard.
- **Photo resize right after import / paste / drop**: the new photo (or pasted text, sticker, equation) comes
  selected with its handles: drag to move, corner to resize, top handle to turn. Done (or a tap elsewhere) returns
  to the tool used before.
- **Home: note size** — Sort and view (⇅) › Note size: Small, Big, Extra big, List. Remembered.

### 8c — finished
- **Tables in typed notes**: toolbar › Insert table (3 × 3). Rows are drawn as an aligned grid (fixed-width font,
  first row bold). In a table the toolbar shows Next cell, Add row, Add column, Delete row, Delete column. Enter adds
  a row; Enter in an empty last row leaves the table. Markdown export/import as GitHub tables.
- **Equations** (handwritten notes): Insert › Equation, written LaTeX-style with buttons (x², xₙ, fraction, roots,
  ∑, ∫, lim, Greek letters, ≤ ≥ ≠ → ∞ …) and a live preview; placed as a picture in the pen colour, selected to
  move/resize. Own small typesetter, works offline.
- **Web clipping** (typed notes): on a link, toolbar › Clip page: the page's title and summary go under the line as
  a quote, its preview picture becomes an attachment. Only when tapped.
- Match-style paste: already the behaviour (pasted text is plain and takes the style where it lands).
- Settings from before: tool-bar tools, page scroll bar, ask before leaving with Back.

### Verification (CI)
- Robolectric: full screen → floating bar, Text tool tap → type on the page → saved text box → leave full screen;
  tool bar controls all labelled and ≥ 48 dp.
- JVM: press-and-hold hit-testing (photo / ink / empty paper) and selection; typed tables (insert, Enter adds a row,
  next cell, add column, delete row), grid drawing with every cursor position mapping back, Markdown round trip; web
  page title/summary/picture reading; equations laid out (fractions stack, powers raise) and drawn.

### Known limits (third round)
- Table rows that are wider than the screen wrap, which breaks the grid; columns line up by character count (very
  wide characters like emoji can shift a column).
- Equations are pictures once placed (to change one, insert it again); equations aren't in typed notes yet.
- Press-and-hold needs fingers set to scroll (a pen is used) or the Text tool; with finger drawing, holding still
  is kept for shape correction.
- Pasting a recording into another note isn't possible (it stays with its own note).
- Web clipping needs internet and a page that publishes a title; some sites block apps.
