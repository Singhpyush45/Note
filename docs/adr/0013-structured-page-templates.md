# ADR-0013: Structured page templates

Status: **Accepted** · 2026-10-25 (Phase 6c)

## Context
The owner's brief (§4) asks for a separate Choose-template screen with Basic, Academic, Planners, Journals,
Creative and Custom categories (30 named templates), custom templates the user can create, preview, edit,
rename, duplicate and delete, and says templates must be **structured layouts, not flattened screenshots**:
page dimensions, paper background, spacing, colours, layout regions and reusable components. Selecting a
template must create an editable note. No reference assets were provided, so original designs are needed.

## Decision
- **Model (`core/ink/template`, pure Kotlin, JVM-tested).** A `TemplateLayout` is page size + paper colour +
  `PaperStyle` (ruling none/lines/grid/dots, spacing, line colour, margin, top) + a list of components:
  text label, box, line, ruled area, dot area, checklist, table (weighted columns, header, row labels),
  music staves and circle rows. Calendars, timetables, trackers and budgets are tables. All coordinates are
  page units (A4 = 794 × 1123), so designs scale sharply and `resized()` moves/stretches every part.
- **Built-ins** are built in code from those components (`BuiltInTemplates`), one original functional design
  for every template the owner listed; landscape where it suits (school timetable, month calendar).
- **Storage (schema v8, generated non-destructive migration).** `custom_templates(id, name, layoutJson, …)`
  holds the user's templates. Each handwriting page gets `ink_pages.layoutJson`: the page keeps **its own
  copy** of the design, so editing or deleting a template never changes notes made from it, duplicates keep it,
  and new pages in that note repeat it. The plain `template` code is still written (nearest simple background)
  for older code paths; choosing a plain background in the editor clears the design.
- **JSON, forgiving.** kotlinx-serialization (already used for documents). Unknown fields are ignored and
  components of unknown kinds are skipped, so a newer app's layout still opens; a damaged layout opens as a
  plain page (never a crash).
- **One renderer** (`TemplateRenderer`, android.graphics) draws a layout in page units for the handwriting
  editor, note-card thumbnails, template tiles and previews, so a template looks identical everywhere. Lines and
  dots never get thinner than one screen pixel, so small previews still show the ruling.
- **Custom editor** edits a `TemplateDraft`: page size (A4 portrait/landscape, long 20:9), header (none, title,
  title & date), ruling, spacing, line colour, margin, paper colour (pastels). "Customise" on a built-in makes
  a custom copy; its printed parts are kept and resize with the page. Opening any template in the editor and
  saving gives the same layout back (tested for every built-in).

## Consequences
+ Every listed template exists and makes a real, writable handwriting note; custom templates have full CRUD,
  reachable from + › Choose template and Settings › Templates.
+ Designs are data: future phases (6d page settings, 8a text boxes on pages) can read and extend them.
− Template text (e.g. "Monday") is printed in English and is part of the page background, not editable text;
  writing goes on top with the pen. Editable text boxes on pages come with the multimodal note (Phase 8a).
− The custom editor offers whole-page options; moving individual boxes/tables freely is not offered.
− Paper colours are fixed light tones; adjusting paper for dark mode is Phase 6d (page settings).
