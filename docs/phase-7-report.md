# Phase 7 — Pens, pencils, brushes, colours, laser pointer: report

Status: **done on CI** — build 114 (run 40208068325): 454 JVM/Robolectric tests, 38 emulator tests, 0 failures, lint 0 errors, test APK published. Device checks by a person:
`phase-7-device-checklist.md` (pending, like the Phase 3–6 checklists).

Source: the owner's master prompt §12–14 and §17, and the four reference pictures (dark tool bar with tools,
widths and colours; floating rounded bottom bar with a font/colour panel; "Sketch with powerful tools" tool grid).

## What was built

### 7a — Tool bar and variation panel (§13)
- Glass tool bar: Pen, Marker, Pencil, Brush, Highlighter, Eraser, Lasso, Laser. Each drawing tool shows a small
  bar in its current colour (rainbow for the RGB laser).
- Second row for the chosen tool: its variant name (tap → options), three sizes, recent + quick colours, and a
  palette button for the full colour picker. Eraser, lasso and laser keep their own options.
- **Variation panel** (tap the chosen tool again or its variant name): live preview of the set-up on paper,
  one tile per variant with its own preview in the current colour, size and opacity sliders, pressure switch,
  "Recently used" set-ups (newest first, 6). It floats over the page so the page never moves; touches on it
  never reach the page.

### 7b — Variants with real rendering differences (§12, ADR-0014)
- 32 variants: 7 pens (ballpoint, fine, gel, fountain, fineliner, technical, dot), 2 markers, 3 highlighters,
  7 pencils (natural, HB, 2B, 4B, mechanical, sketch, graphite), 13 brushes (basic, soft, calligraphy brush and
  pen, airbrush, oil, watercolour, crayon, chalk, charcoal, texture, artistic, marker brush).
- Each variant has its own recipe (width from pressure / constant / nib angle, taper, grain texture, soft, spray,
  wash or dot finish, opacity); a test checks no two share one. Ballpoint, felt marker and chisel highlighter
  stay on Jetpack Ink; the rest are drawn by the app's renderer, the same way live, finished, in thumbnails and
  in PDF export.
- Stored per stroke (ink format 2, written only when needed so older strokes and versions are unaffected);
  an unknown variant from a newer version opens the page read-only.

### 7c — Colours (§14)
- Colour picker: Recent, My colours (save; long-press removes), palettes Basic, Greys, Pastel, Deep, Neon;
  Custom tab with saturation/brightness square, hue bar, HEX (#RGB / #RRGGBB with error), red/green/blue sliders.
- Changing colour keeps the tool's opacity; opacity is set in the panel (highlighter max 70 %).
- Recent colours also lead the quick colour row in the tool bar.

### 7e — Laser pointer (§17)
- Laser tool: a glowing trail that follows the pen or finger and fades in about 0.65 s; never saved, no undo
  entry, nothing changes in the note. The stylus eraser end still erases.
- Looks: Solid, Neon (white core), Dot; **RGB** rainbow mode (colours flow along the trail) or one colour;
  three widths. Kept for next time.

### 7d — Shapes and arranging (§15, §16) — first part
- **Shapes tool**: Line, Arrow, Rectangle, Square, Ellipse, Circle, Triangle — drag from corner to corner.
  Outline colour (quick colours + full picker), three widths, **Fill** on/off (same colour, see-through).
  Filled shapes are stored with their fill (ink format 2 flag, written only for filled shapes).
- **Lasso selection**: Rotate 90°, Bring to front, Send to back (plus move, resize, duplicate, recolour, delete).
- Still to do in 7d: ruler, free rotation handle, flip, more selection modes, gradient colours.

### Fixes and requests (25 Oct)
- **Scan note without Google Play services**: if Google's scanner can't start (no Play services, or its module
  isn't installed), the phone's camera app opens instead and the photo becomes a one-page PDF note (no edge
  detection or clean-up — those need Google's scanner). The "needs Google Play services" error is gone.
- **Folders inside folders** (database v10, `notebooks.parentId`): new folder inside the open folder (⋮ › New
  folder, or the "New folder" card), folders shown above the notes, Back / "Back to …" goes up a level, folder
  ⋮: rename, move (never into itself), delete (subfolders too; notes are kept and move to No notebook). Folder
  pickers show the tree indented.
- **Folder icon**: a two-tone folder in the accent colour (chips, folder cards, pickers, Manage notebooks).
- **App icon**: the owner's folded-paper "N" (adaptive icon, beige background, themed-icon "N" for Android 13+).

## Verification (CI)
- JVM: variant names and unique recipes, format 1/2 round trip for every variant, unknown variant → read-only,
  pressure/nib/taper widths, outline and dots geometry; laser trail fade, lift, separate strokes, rainbow hue
  along the trail and with time, density; colour maths (HEX forms, HSV round trip), palettes, recent lists and
  their storage (unknown entries skipped).
- Robolectric through the real app: pencil → panel → 4B, pressure off → the stored stroke has that tool, variant,
  size and pressure, and becomes "recently used"; colour picker sets the brush colour, saves it, puts it in
  recent colours; laser with RGB/Neon leaves the note empty, then the pen draws normally; every tool bar control
  is labelled and ≥ 48 dp for all 8 tools.
- Screenshots reviewed (phone, light and dark): all 32 variants on one page, pencil/brush/pen panels, colour
  palettes and custom tab, laser options and a rainbow laser trail.

## Known limits (stated, not hidden)
- Variants other than the three classic ones are drawn by the app's renderer: live latency slightly higher than
  Jetpack Ink's (measure on device: checklist).
- Soft, airbrush and watercolour edges use blur: Android 9+ with hardware drawing; older devices draw hard edges.
- Textures (graphite, wax, chalk, charcoal, canvas, bristle) are generated approximations, not scanned media.
- Shapes from shape correction use their tool's plain line, not the variant's texture.
- Not in this phase: gradient colours, shape fill/stroke colours, eyedropper, text tools/boxes (Phase 8).
- The laser trail stays where it was drawn on screen if the page is scrolled during the fade.
- Nothing was tested on a real phone or tablet with a stylus yet.
