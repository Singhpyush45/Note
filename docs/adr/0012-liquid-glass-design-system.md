# ADR-0012: Liquid Glass design system

Status: **Accepted** · 2026-10-25 (Phase 6a)

## Context
The owner's brief (§1, §19) asks for a restrained, authentic "Liquid Glass" look: translucent surfaces with natural
background blur, fine borders, soft shadows, rounded corners; Light/Dark/System; and real, persistent settings for
accent colour, glass intensity, blur, corner radius, wallpaper and density. Constraints: readability (WCAG AA,
SRS 8.2), battery and smooth scrolling (NFR-02/08), Android 8+ (min SDK 26), and no unverified dependencies.

## Decision
- **Colour from one hue.** `Palette.scheme(hue, dark)` builds the whole Material scheme: near-neutral surfaces
  with a faint tint of the accent, the accent in primary roles. Every text/background pair is adjusted until it
  meets 4.5:1, so any custom hue is readable (tested for all presets and every 15° of the wheel, both themes).
  Wallpaper-based dynamic colour (Android 12+) stays available and overrides the accent.
- **Glass = tint + border + highlight + optional backdrop blur.** `GlassSurface` draws a translucent tint
  (opacity from "Glass intensity", never below a readable floor), a soft top highlight, a hairline border and a
  shadow drawn only outside the shape (a platform shadow would show through translucent glass).
- **Backdrop blur without a library.** On Android 12+ with hardware rendering, content marked
  `Modifier.backdropSource(...)` is recorded into a Compose `GraphicsLayer`; each glass surface draws the part
  under itself from that layer through a `BlurEffect` (RenderEffect). Layers are references, so scrolling updates
  the blur without re-recording the glass. Elsewhere (Android 8–11, software rendering, tests) glass is simply more
  opaque. The Haze library does the same; it wasn't added because no version could be verified from this build
  environment, and the needed part is small.
- **Wallpapers** are drawn (soft radial colour fields, adapted for dark theme) — no image assets. A photo from the
  photo picker is copied in at ≤1600 px, blurred by "Background blur" and veiled for readability.
- **Shapes and spacing follow settings**: one corner radius scales Material's shape scale; Compact density
  tightens paddings while touch targets stay 48 dp.
- **Where glass goes**: chrome and grouped content (settings cards, bars, menus, panels). Note text and the drawing
  canvas stay on opaque paper for readability.

## Consequences
+ Every appearance setting has a visible effect and is persisted (DataStore), with reset to defaults.
+ No new dependency; blur cost is limited to the area of glass surfaces, and only on capable devices.
− True backdrop blur only on Android 12+; older phones see frosted-but-solid glass.
− A glass surface must not be placed inside the content it blurs (documented on `Backdrop`).
