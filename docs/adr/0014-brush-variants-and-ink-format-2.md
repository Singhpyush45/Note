# ADR-0014: Pen, pencil and brush variants; ink format 2

Status: accepted (Phase 7)

## Context
The owner brief (§12) asks for many pen, pencil and brush variants, and says each variant name must match a real
difference in how it draws. Jetpack Ink (ADR-0009) has three stock brush families (pressure pen, marker,
highlighter); it can't draw grain textures, nib angles, soft/sprayed edges, watercolour or dots.

## Decision
- A variant is a `BrushStyle` with a fixed `StrokeSpec` recipe: width mode (constant / pressure / nib angle),
  minimum width, nib angle, taper, texture (graphite fine/medium/coarse, wax, chalk, charcoal, canvas, bristle),
  finish (solid, soft, spray, wash, dots), opacity and softness. A unit test checks no two variants share a recipe.
- The three original set-ups (ballpoint, felt marker, chisel highlighter with pressure on) stay on Jetpack Ink
  (lowest latency). Every other variant is drawn by our own renderer (`StyledInk`), live and finished, on screen,
  in thumbnails and in PDF export, from geometry in `core/ink` (`StrokeOutline`) that is unit-tested.
- New tools PENCIL and BRUSH join PEN, MARKER, HIGHLIGHTER.
- Storage: ink item format 2 adds one byte for the variant and one for flags (pressure off). It is written only
  when needed, so strokes made with the original set-ups stay in format 1 and older app versions read them.
  A variant code this version doesn't know makes the page open read-only (like any newer format), never lost.
- Recently used set-ups, recent and saved colours, and laser settings are stored in DataStore preferences
  (short text lists; unknown entries skipped).

## Consequences
- Variant strokes are drawn by the CPU path renderer: live latency is a little higher than Jetpack Ink's.
- Blurred finishes (soft, airbrush, watercolour) need Android 9+ with hardware drawing; older devices draw a
  hard edge. Textures are generated tiles that approximate real media.
- Shapes from shape correction keep the plain outline of their tool (variant style not applied to shapes yet).
