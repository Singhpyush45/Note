# NOTEflow / Papriva — Master Liquid Glass & Brand Identity Patch

## Included Files (Drop-in Repository Paths)
1. **Brand & Launcher Icon Assets (Phase 20)**:
   - `assets/brand/papriva_icon_light.svg` — Editable SVG source (Pure `#FFFFFF` background + `#141518` dark graphite folded-paper `P`)
   - `assets/brand/papriva_icon_dark.svg` — Editable SVG source (Pure `#000000` background + `#F4F4F6` soft-white folded-paper `P`)
   - `assets/brand/papriva_icon_monochrome.svg` — Monochrome SVG silhouette
   - `app/src/main/res/drawable/ic_launcher_background.xml` (`#FFFFFF`)
   - `app/src/main/res/drawable-night/ic_launcher_background.xml` (`#000000`)
   - `app/src/main/res/drawable/ic_launcher_foreground.xml` (Light adaptive foreground vector)
   - `app/src/main/res/drawable-night/ic_launcher_foreground.xml` (Dark adaptive foreground vector)
   - `app/src/main/res/drawable/ic_launcher_monochrome.xml` (Android 13+ themed icon vector)
   - `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` & `ic_launcher_round.xml`

2. **Centralized Neutral Liquid Glass Design System & Reusable Menus (Phases 2 & 6)**:
   - `core/designsystem/src/main/kotlin/com/appname/notes/core/designsystem/theme/LiquidGlassTokens.kt`
   - `core/designsystem/src/main/kotlin/com/appname/notes/core/designsystem/component/GlassMenu.kt`

3. **Home Screen, Category Tabs, Compact Folder-Up Icon, 1-Tap Bookmark & Creation Studios (Phases 4, 5, 8, 9, 11)**:
   - `app/src/main/kotlin/com/appname/notes/feature/library/GoodnotesCreationDialogs.kt` (Full-width settings card fixing `image_5ddc78.jpg` text wrapping + neutral Liquid Glass palette)
   - `app/src/main/kotlin/com/appname/notes/feature/library/HomeParts.kt` (Non-overflowing `Notes · Documents · Folders · Favourites` tabs fixing `image_5ddc54.jpg`)
   - `app/src/main/kotlin/com/appname/notes/feature/library/LibraryScreen.kt` (Compact circular Liquid Glass back icon `FolderTestTags.UP`, standalone `New folder` removed from grid, neutral Liquid Glass `SortMenu` & `OverflowMenu`, and 1-tap bookmark toggle)
   - `app/src/main/kotlin/com/appname/notes/ui/common/NoteCard.kt` (Direct 1-tap Bookmark/Star button + uniform grid preview heights fixing `image_5ddbf9.jpg`)
   - `app/src/main/kotlin/com/appname/notes/navigation/NotesApp.kt` (Neutral Liquid Glass sliding pill bottom navigation bar)
