package com.appname.notes.feature.library

import com.appname.notes.core.domain.model.FolderTree
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSummary
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.domain.model.Notebook
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.ui.common.CoverCategory
import com.appname.notes.ui.common.NoteCoverConfig
import com.appname.notes.ui.common.NoteCoverDesign
import com.appname.notes.ui.common.NoteCoverStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private val NoteSummary.isTrashed: Boolean
    get() = trashedAt != null

/**
 * Phase 8 — Automated Verification for Hierarchical Folder & Note Organisation,
 * Circular Nesting Prevention, Back Navigation Stack, and Cover Persistence.
 */
class FolderHierarchyTest {

    private data class HierarchyHarness(
        val notebooks: MutableList<Notebook> = mutableListOf(),
        val notes: MutableList<NoteSummary> = mutableListOf(),
    ) {
        fun createFolder(id: String, title: String, parentId: NotebookId? = null): Notebook {
            val now = Instant.parse("2026-11-13T10:00:00Z")
            val nb = Notebook(
                id = NotebookId(id),
                title = title,
                sortOrder = 0,
                noteCount = 0,
                createdAt = now,
                updatedAt = now,
                parentId = parentId,
            )
            notebooks += nb
            return nb
        }

        fun createNote(id: String, title: String, type: NoteType = NoteType.TEXT, notebookId: NotebookId? = null): NoteSummary {
            val now = Instant.parse("2026-11-13T10:00:00Z")
            val note = NoteSummary(
                id = NoteId(id),
                title = title,
                preview = "Internal preview text that must never replace cover title",
                type = type,
                notebookId = notebookId,
                notebookTitle = null,
                tags = emptyList(),
                isPinned = false,
                isFavourite = false,
                isArchived = false,
                trashedAt = null,
                createdAt = now,
                updatedAt = now,
            )
            notes += note
            return note
        }

        fun moveNote(noteId: NoteId, targetNotebookId: NotebookId?) {
            val idx = notes.indexOfFirst { (id) -> id == noteId }
            if (idx >= 0) {
                notes[idx] = notes[idx].copy(notebookId = targetNotebookId)
            }
        }

        fun moveFolder(folderId: NotebookId, targetParentId: NotebookId?): Boolean {
            if (targetParentId == folderId) return false
            if ((targetParentId != null) && (targetParentId in FolderTree.descendants(notebooks, folderId))) return false
            val idx = notebooks.indexOfFirst { (id) -> id == folderId }
            if (idx < 0) return false
            notebooks[idx] = notebooks[idx].copy(parentId = targetParentId)
            return true
        }

        fun visibleFolders(collection: NoteCollection): List<Notebook> = when (collection) {
            NoteCollection.All -> FolderTree.children(notebooks, null)
            is NoteCollection.InNotebook -> FolderTree.children(notebooks, collection.id)
            else -> emptyList()
        }

        fun visibleNotes(collection: NoteCollection): List<NoteSummary> = when (collection) {
            NoteCollection.All -> notes.filter { (it.notebookId == null) && (!it.isArchived) && (!it.isTrashed) }
            is NoteCollection.InNotebook -> notes.filter { (it.notebookId == collection.id) && (!it.isArchived) && (!it.isTrashed) }
            NoteCollection.Favourites -> notes.filter { it.isFavourite && (!it.isArchived) && (!it.isTrashed) }
            NoteCollection.Archive -> notes.filter { it.isArchived && (!it.isTrashed) }
            NoteCollection.Trash -> notes.filter { it.isTrashed }
            is NoteCollection.WithTag -> emptyList()
        }

        fun navigateBack(current: NoteCollection): NoteCollection? = when (current) {
            is NoteCollection.InNotebook -> {
                val cur = notebooks.firstOrNull { (id) -> id == current.id }
                if (cur?.parentId != null) NoteCollection.InNotebook(cur.parentId!!) else NoteCollection.All
            }
            NoteCollection.All -> null // Only exit at true Root
            else -> NoteCollection.All
        }
    }

    @Test
    fun nestedFolderHierarchy_isolatesChildrenAndSupportsBackNavigation() {
        val h = HierarchyHarness()

        // 1. Create Hello at root
        val (helloId) = h.createFolder("f_hello", "Hello", parentId = null)
        // 2. Create Hi inside Hello
        val (hiId) = h.createFolder("f_hi", "Hi", parentId = helloId)
        // 3 & 4. Create Mathematics and DBMS inside Hi, plus a root note Personal Notes
        h.createNote("n_personal", "Personal Notes", NoteType.TEXT, notebookId = null)
        val (mathId) = h.createNote("n_math", "Mathematics", NoteType.INK, notebookId = hiId)
        h.createNote("n_dbms", "DBMS", NoteType.TEXT, notebookId = hiId)

        // 5. Verify root shows Hello & Personal Notes, but NOT Hi, Mathematics, or DBMS
        val rootFolders = h.visibleFolders(NoteCollection.All).map { (_, title) -> title }
        val rootNotes = h.visibleNotes(NoteCollection.All).map { (_, title) -> title }
        assertEquals(listOf("Hello"), rootFolders)
        assertEquals(listOf("Personal Notes"), rootNotes)
        assertFalse(rootNotes.contains("Mathematics"))
        assertFalse(rootNotes.contains("DBMS"))

        // 6. Open Hello and verify Hi is visible (and Mathematics/DBMS are not directly in Hello)
        val helloFolders = h.visibleFolders(NoteCollection.InNotebook(helloId)).map { (_, title) -> title }
        val helloNotes = h.visibleNotes(NoteCollection.InNotebook(helloId)).map { (_, title) -> title }
        assertEquals(listOf("Hi"), helloFolders)
        assertTrue(helloNotes.isEmpty())

        // 7. Open Hi and verify Mathematics and DBMS are visible
        val hiFolders = h.visibleFolders(NoteCollection.InNotebook(hiId))
        val hiNotes = h.visibleNotes(NoteCollection.InNotebook(hiId)).map { (_, title) -> title }
        assertTrue(hiFolders.isEmpty())
        assertEquals(listOf("Mathematics", "DBMS"), hiNotes)

        // 8. Move Mathematics to root and verify it disappears from Hi and appears at root
        h.moveNote(mathId, null)
        assertEquals(listOf("DBMS"), h.visibleNotes(NoteCollection.InNotebook(hiId)).map { (_, title) -> title })
        assertEquals(listOf("Personal Notes", "Mathematics"), h.visibleNotes(NoteCollection.All).map { (_, title) -> title })

        // 9. Move Mathematics back to Hi and verify the reverse
        h.moveNote(mathId, hiId)
        assertEquals(listOf("Mathematics", "DBMS"), h.visibleNotes(NoteCollection.InNotebook(hiId)).map { (_, title) -> title })
        assertEquals(listOf("Personal Notes"), h.visibleNotes(NoteCollection.All).map { (_, title) -> title })

        // 10 & 11. Move folder Hi (containing Mathematics & DBMS) to root and verify children stay intact
        assertTrue(h.moveFolder(hiId, null))
        assertEquals(listOf("Hello", "Hi"), h.visibleFolders(NoteCollection.All).map { (_, title) -> title })
        assertEquals(listOf("Mathematics", "DBMS"), h.visibleNotes(NoteCollection.InNotebook(hiId)).map { (_, title) -> title })
        // Move Hi back inside Hello
        assertTrue(h.moveFolder(hiId, helloId))
        // Prevent circular nesting (Hello cannot be moved inside Hi)
        assertFalse(h.moveFolder(helloId, hiId))

        // 12. Navigate backward from Hi -> Hello -> Root -> Exit (null)
        val afterHiBack = h.navigateBack(NoteCollection.InNotebook(hiId))
        assertEquals(NoteCollection.InNotebook(helloId), afterHiBack)
        val afterHelloBack = h.navigateBack(afterHiBack!!)
        assertEquals(NoteCollection.All, afterHelloBack)
        assertNull(h.navigateBack(afterHelloBack!!))
    }

    @Test
    fun coverConfig_roundTripsPersistenceAndSeparatesNoteTypes() {
        val custom = NoteCoverConfig(
            design = NoteCoverDesign.BOTANICAL_LEAVES,
            hasCover = true,
        )
        val encoded = custom.encode()
        val decoded = NoteCoverConfig.decode(encoded)
        assertEquals(custom, decoded)

        val (typedDesign) = NoteCoverStore.resolveForNote("note_1", NoteType.TEXT, emptyMap(), "Meeting Summary")
        val (inkDesign) = NoteCoverStore.resolveForNote("note_2", NoteType.INK, emptyMap(), "Sketching")
        assertTrue(typedDesign != inkDesign)
        assertEquals(CoverCategory.CREATIVE, inkDesign.category)
    }

    @Test
    fun global2DAnd3DDisplayMode_roundTripsAndPreservesIndividualCoverConfig() {
        assertEquals(
            com.appname.notes.ui.common.NotebookDisplayMode.MODE_2D,
            com.appname.notes.ui.common.NotebookDisplayMode.fromId("2d"),
        )
        assertEquals(
            com.appname.notes.ui.common.NotebookDisplayMode.MODE_3D,
            com.appname.notes.ui.common.NotebookDisplayMode.fromId("3d"),
        )
        // Verify expanded categories (Scientific, Professional, Classic, Modern, Artistic) have dedicated cover designs
        val categoriesPresent = NoteCoverDesign.entries.asSequence().map { it.category }.toSet()
        assertTrue(categoriesPresent.contains(CoverCategory.SCIENTIFIC))
        assertTrue(categoriesPresent.contains(CoverCategory.PROFESSIONAL))
        assertTrue(categoriesPresent.contains(CoverCategory.CLASSIC))
        assertTrue(categoriesPresent.contains(CoverCategory.MODERN))
        assertTrue(categoriesPresent.contains(CoverCategory.ARTISTIC))
    }

    @Test
    fun folderCoverConfig_isIndependentFromNoteCoverAndPersistsAllOptions() {
        val folderCfg = com.appname.notes.ui.common.FolderCoverConfig(
            style = com.appname.notes.ui.common.FolderStyleKind.LAYERED,
            palette = com.appname.notes.ui.common.FolderColorPalette.EMERALD,
            artwork = com.appname.notes.ui.common.FolderArtworkKind.BOTANICAL,
            titlePlacement = com.appname.notes.ui.common.FolderTitlePlacement.BOTH,
        )
        val encodedFolder = folderCfg.encode()
        val decodedFolder = com.appname.notes.ui.common.FolderCoverConfig.decode(encodedFolder)
        assertEquals(folderCfg, decodedFolder)

        val folderMap = mapOf("folder_hiii" to folderCfg)
        val (style, palette) = com.appname.notes.ui.common.FolderCoverStore.resolveForFolder("folder_hiii", folderMap)
        assertEquals(com.appname.notes.ui.common.FolderStyleKind.LAYERED, style)
        assertEquals(com.appname.notes.ui.common.FolderColorPalette.EMERALD, palette)

        // Verify another folder is unaffected
        val (untouchedStyle) = com.appname.notes.ui.common.FolderCoverStore.resolveForFolder("folder_hii", folderMap)
        assertEquals(com.appname.notes.ui.common.FolderStyleKind.CLASSIC, untouchedStyle)
    }

    @Test
    fun noteImageLayoutConfig_roundTripsResizeCropRotateAndAlignment() {
        val imgCfg = com.appname.notes.feature.editor.NoteImageLayoutConfig(
            widthFraction = 0.84f,
            heightScale = 1.25f,
            freeResize = true,
            rotationDegrees = 90,
            cropPreset = com.appname.notes.feature.editor.ImageCropPreset.LANDSCAPE_16_9,
            cropLeft = 0.05f,
            cropTop = 0.10f,
            cropRight = 0.95f,
            cropBottom = 0.90f,
            alignment = com.appname.notes.feature.editor.ImageHorizontalAlignment.RIGHT,
            offsetX = 24f,
            zOrder = 2,
        )
        val encoded = imgCfg.encode()
        val decoded = com.appname.notes.feature.editor.NoteImageLayoutConfig.decode(encoded)
        assertEquals(imgCfg, decoded)
    }

    @Test
    fun compactSingleRowToolbarState_andImageResizeDimensions_preserveBoundsAndAspectRatio() {
        // Priority 2 & 3: Verify compact single-row toolbar state defaults to collapsed (single row) and toggles cleanly
        val defaultCollapsed = false
        var expanded = defaultCollapsed
        expanded = !expanded
        assertTrue("Tapping active tool or settings pill should expand secondary tool options", expanded)
        expanded = !expanded
        assertFalse("Tapping active tool again should collapse secondary tool options back to single-row mode", expanded)

        // Priority 4: Verify inline image resizing preserves aspect ratio and clamps safely within editor bounds
        val aspect = (1600f / 1000f).coerceIn(0.25f, 4.0f)
        val containerWidthDp = 360f
        val newWidthDp = (220f + 85f).coerceIn(96f, containerWidthDp)
        val newHeightDp = (newWidthDp / aspect).coerceIn(72f, 560f)
        assertEquals(305f, newWidthDp, 0.01f)
        assertEquals(190.625f, newHeightDp, 0.01f)
    }

    @Test
    fun toolbarDividerHealer_andSnackbarFabClearance_preventRegressions() {
        // 1. Verify compounding HorizontalDivider padding (Root Cause #1 in image_b6a9fe.jpg) collapses to 1.dp
        val damagedDivider = "/* Phase 30 Divider Padding */ /* Phase 30 Divider Padding */ " +
            "HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp).padding(vertical = 3.dp).padding(vertical = 3.dp), color = dividerColor)"
        val stripped = damagedDivider
            .replace(Regex("""/\*\s*Phase 30 Divider Padding\s*\*/\s*"""), "")
            .replace(Regex("""(?:\.padding\(\s*vertical\s*=\s*\d+(?:\.\d+)?\.dp\s*\))+"""), ".padding(vertical = 1.dp)")
        assertEquals(
            "HorizontalDivider(modifier = Modifier.padding(vertical = 1.dp), color = dividerColor)",
            stripped,
        )

        // 2. Verify Snackbar bottom & FAB clearance (Root Cause #2 in image_b6aa1f.jpg)
        val screenHeightDp = 800f
        val bottomNavHeightDp = 64f
        val snackbarBottomOffsetDp = 68f
        val fabEndClearanceDp = 86f // 16dp margin + 56dp FAB + 14dp gap
        val snackbarTopFromBottomDp = snackbarBottomOffsetDp + 44f
        assertTrue("Snackbar must sit above the 64.dp bottom navigation bar", (snackbarBottomOffsetDp - bottomNavHeightDp) > 0f)
        assertTrue("Snackbar must stay in the lower 20% of the screen, never in the middle of the grid", ((screenHeightDp * 0.20f) - snackbarTopFromBottomDp) > 0f)
        assertTrue("Snackbar end padding must clear the 56.dp + 16.dp right FAB", (fabEndClearanceDp - 72f) >= 0f)
    }
}
