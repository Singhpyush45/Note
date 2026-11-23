# Phase 6 — UI shell: Liquid Glass, home, templates, page settings: report

Status: **done on CI** — build 102 (run 40192639220): 428 JVM/Robolectric tests, 38 emulator tests (incl. the
process-death e2e tests), 0 failures, lint 0 errors, test APK published.
Device checks by a person: `phase-6-device-checklist.md` (pending, like the Phase 3–5 checklists).

Source of the work: the owner's master prompt (§1–5, §19) and its Liquid Glass mock-up, mapped in
`claude/roadmap-v2.md` (project doc).

## What was built

### 6a — Liquid Glass design system and appearance settings (§1, §19, ADR-0012)
- One accent hue builds the whole colour scheme, every text/background pair adjusted to WCAG AA (any custom hue).
- Glass surfaces: translucent tint, top highlight, hairline border, soft outside shadow; real backdrop blur on
  Android 12+ (Compose GraphicsLayer + RenderEffect), frosted-but-solid on older phones and in tests.
- Settings › Appearance: theme (Light/Dark/System), accent (6 presets + custom hue), glass intensity, blur,
  corner radius, wallpaper (5 drawn presets or a photo, blurred and veiled), UI density, reset to defaults.

### 6b — Notes-first home (§2, §3)
- Glass header: "All notes ▾" (all collections), search, sort, ⋮; quick filter chips (All, Favourites, notebooks).
- One expandable **+**: Blank note, Choose template, Scan note, Import file (PDF → PDF note, Markdown/text →
  text note, anything else attached to a new note). Closes with ×, a tap outside, or Back.
- Card ⋮: Rename, Pin, Favourite, Move, Duplicate, Archive, Trash, Select. Long-press selects several notes:
  Select all, Pin, Move, Archive, Trash (Restore / Delete forever in Trash); Back cancels.

### 6c — Templates (§4, ADR-0013)
- Separate Choose-template screen: back, search, category chips, sections with page thumbnails, large preview
  with **Use template** (a handwriting note with that design).
- All 30 templates the owner listed, as structured layouts (page size, paper, ruling, labels, boxes, ruled
  areas, checklists, tables, calendars, music staves) — original designs, not images.
- Custom templates: create, preview, edit, rename, duplicate, delete (also from Settings › Templates);
  "Customise" copies a built-in. Editor: page size, header, ruling, spacing, line colour, margin, paper colour.
- A page keeps its own copy of its design: editing or deleting a template never changes existing notes.
- Database v8 (`custom_templates`, `ink_pages.layoutJson`), generated migration.

### 6d — Page settings and note defaults (§5, §19)
- **Page settings** (handwriting editor ⋮): separate choices for
  - layout: **Split pages** or **Infinite scroll** (pages join without gaps or edges, and writing on the last
    page adds an empty one after it, so the surface keeps growing);
  - scroll direction: **Vertical** or **Horizontal** (pages side by side, a whole page fits the screen);
  - page size: **A4 portrait**, **A4 landscape**, **Long 20:9** — all pages, never smaller than their writing;
  - background **Blank / Lined / Grid / Dot** with **Narrow / Normal / Wide** spacing, for this page or all pages;
  - paper colour: four palettes (pastel, warm, cool, neutral) and a custom colour (always light enough to write on);
  - **Dark paper in dark theme**: display-only mapping (white paper → dark grey, black ink → light, hues kept),
    also on note cards; the live stroke uses the same mapping, so ink looks the same while and after drawing.
  PDF notes keep the document's page size and background; layout and direction still apply.
- Settings are stored **per note** (database v9, `ink_note_settings`; size and design per page), copied with
  duplicates, and used by the editor and by **export**: handwritten notes now export/share as a vector PDF with
  each page's size, paper, template and ink (real colours).
- **Settings › Note defaults**: Blank note opens as Typed or Handwriting; default page style (Basic templates),
  size, layout, scroll direction, paper colour; dark paper on/off. New blank handwritten notes and notes from
  templates start with them.

## Verification (CI, build 102)
- JVM/Robolectric (428): colour contrast for all hues; glass/theme; home actions, selection, + menu, rename;
  template model (every built-in round-trips, stays on the page, newer/damaged layouts), draft editor, page-design
  helpers, page layout engine (vertical/horizontal, infinite gaps, zoom/scroll/current page); repositories
  (templates CRUD, per-note arrangement, resize never hides ink, per-page designs, copies, deleted notes);
  settings (note defaults round trip, unknown values); flows through the real app (choose/use/search/customise
  templates, make/delete a custom one, page settings change the note, infinite scroll adds a page, blank note as
  handwriting with defaults); screenshots of every new screen in light and dark.
- Emulator (38): handwriting and lifecycle tests now go + › Choose template › Lined › Use template; process
  death and force-stop e2e; **PDF export** of a handwritten note checked by reading it back (page sizes 595×842
  and 842×595 points, ink and paper colour pixels).
- Real bugs found by these tests and fixed: a PDF failure notice could be reset by a slower page-settings read;
  a page-size change finishing late overwrote a background chosen meanwhile.

## Known limits (stated, not hidden)
- Backdrop blur only on Android 12+; note cards are frosted without live blur to keep scrolling smooth.
- Template labels are printed English text on the page background, not editable text (text boxes: Phase 8a).
- The custom template editor offers whole-page options; boxes/tables can't be moved freely.
- Infinite scroll is built from pages joined without gaps: a single stroke can't cross from one page into the
  next (it is clipped at the join); the next page is added as soon as the last one is written on.
- Dark paper changes how pages look only; highlighter colours become darker tones on dark paper.
- Exported PDFs draw strokes with the app's fallback renderer: same paths, colours and widths, and pen pressure
  as varying segment widths; Jetpack Ink's on-screen brush tips may look slightly smoother than the PDF.
