package com.appname.notes.screenshots

import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToNode
import com.appname.notes.core.ink.template.HeaderStyle
import com.appname.notes.core.ink.template.Ruling
import com.appname.notes.core.ink.template.TemplateCategory
import com.appname.notes.feature.templates.TemplateEditorTestTags
import com.appname.notes.feature.templates.TemplatesTestTags
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appname.notes.MainActivity
import com.appname.notes.R
import com.appname.notes.core.common.result.getOrNull
import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.NoteDocument
import com.appname.notes.core.domain.document.RichText
import com.appname.notes.core.domain.document.SpanType
import com.appname.notes.core.domain.document.StyleSpan
import com.appname.notes.core.domain.document.TextBlock
import com.appname.notes.di.AppDataEntryPoint
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.ink.codec.InkCodec
import com.appname.notes.core.ink.edit.InkEdit
import com.appname.notes.core.ink.input.EditorTool
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.core.ink.model.Pt
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.ShapeKind
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.core.ink.model.StrokePoints
import com.appname.notes.feature.editor.EditorTestTags
import com.appname.notes.feature.ink.InkTestTags
import com.appname.notes.feature.ink.InkToolTags
import com.appname.notes.feature.ink.ColorPickerTags
import com.appname.notes.core.ink.model.BrushStyle
import com.appname.notes.feature.library.LibraryTestTags
import com.appname.notes.feature.search.SearchTestTags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders real screens with sample notes to PNG files in app/build/screenshots (CI publishes them to the
 * `ci-output` branch) so the UI can be reviewed without a device. Also checks the adaptive layouts.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotTestBase(private val device: String) {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val data by lazy { EntryPointAccessors.fromApplication(composeRule.activity.application, AppDataEntryPoint::class.java) }

    protected fun string(id: Int) = composeRule.activity.getString(id)

    protected fun waitAdvancing(condition: () -> Boolean) = composeRule.waitUntil(15_000) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        condition()
    }

    protected fun exists(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    protected fun snap(name: String) {
        waitAdvancing { true }
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "${device}_$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun id() = UUID.randomUUID().toString()

    private fun block(kind: BlockKind, text: String, spans: List<StyleSpan> = emptyList(), checked: Boolean = false) =
        TextBlock(id(), kind, RichText(text, spans), checked)

    /** Realistic sample content (student persona, SRS 4.1). */
    protected fun seed() = runBlocking {
        val notes = data.notes()
        val calculus = data.notebooks().create("Calculus").getOrNull()!!
        data.notebooks().create("Personal")
        val exam = data.tags().createOrGet("exam").getOrNull()!!

        val lecture = notes.createNote(
            "Lecture 4 — Chain rule",
            NoteDocument(
                blocks = listOf(
                    block(BlockKind.HEADING2, "Key idea"),
                    block(
                        BlockKind.PARAGRAPH,
                        "Differentiate the outer function, then multiply by the derivative of the inner one.",
                        listOf(StyleSpan(0, 34, SpanType.BOLD)),
                    ),
                    block(BlockKind.BULLET, "d/dx sin(x²) = 2x·cos(x²)", listOf(StyleSpan(0, 25, SpanType.CODE))),
                    block(BlockKind.BULLET, "Works for any composition f(g(x))"),
                    block(BlockKind.HEADING2, "To revise"),
                    block(BlockKind.CHECKLIST, "Product rule examples", checked = true),
                    block(BlockKind.CHECKLIST, "Past paper Q3 and Q7"),
                    block(BlockKind.QUOTE, "Practice beats rereading."),
                ),
            ),
            calculus,
        ).getOrNull()!!
        notes.setTags(lecture, setOf(exam.id))
        notes.setPinned(setOf(lecture), true)

        notes.createNote(
            "Groceries",
            NoteDocument(blocks = listOf("Milk", "Eggs", "Rice").map { block(BlockKind.CHECKLIST, it) }),
            null,
        ).getOrNull()!!.also { notes.setFavourite(setOf(it), true) }
        notes.createNote(
            "",
            NoteDocument(blocks = listOf(block(BlockKind.PARAGRAPH, "Idea: a study timer that blocks distracting apps."))),
            null,
        )
        notes.createNote(
            "Project meeting",
            NoteDocument(
                blocks = listOf(
                    block(BlockKind.NUMBERED, "Finalise the SRS"),
                    block(BlockKind.NUMBERED, "Design system review"),
                    block(BlockKind.NUMBERED, "Phase 2 demo on Friday"),
                ),
            ),
            null,
        )
        lecture
    }

    /** A handwritten note with cursive-like writing, a highlight and corrected shapes. */
    protected fun seedInk(): com.appname.notes.core.domain.model.NoteId = runBlocking {
        val ink = data.ink()
        val noteId = ink.createInkNote(null, PageTemplate.LINED).getOrNull()!!
        data.notes().setTitle(noteId, "Physics — waves")
        val page = (ink.loadPages(noteId) as AppResult.Success).value.first().info.id
        val pen = InkBrush(InkTool.PEN, 0xFF1F1F1F.toInt(), 2.5f)
        val blue = InkBrush(InkTool.PEN, 0xFF1A73E8.toInt(), 2.5f)
        val items = mutableListOf<InkItem>()
        var z = 1L
        // "Words": loops along a baseline, like joined-up writing.
        fun word(x0: Float, baseline: Float, loops: Int, brush: InkBrush) {
            val b = StrokePoints.Builder(hasPressure = true, hasTilt = false, hasOrientation = false)
            val n = loops * 14
            for (i in 0..n) {
                val t = i / 14.0 * 2 * Math.PI
                val x = x0 + 9 * t - 13 * kotlin.math.sin(t) // loops: 13 > 9
                val y = baseline - 11 - 11 * kotlin.math.cos(t)
                b.add(x.toFloat(), y.toFloat(), i * 8, (0.45 + 0.35 * kotlin.math.sin(t / 3)).toFloat())
            }
            items += InkCodec.quantize(StrokeItem(id(), z++, brush, b.build()))
        }
        var x = 72f
        for (w in listOf(4, 3, 5)) { word(x, 120f, w, pen); x += w * 57f + 24f }
        x = 72f
        for (w in listOf(3, 6, 2, 4)) { word(x, 180f, w, blue); x += w * 57f + 24f }
        // Highlight under the second line.
        val hl = StrokePoints.Builder(false, false, false).apply { for (i in 0..20) add(70f + i * 17f, 172f, i * 8) }.build()
        items += StrokeItem(id(), z++, InkBrush(InkTool.HIGHLIGHTER, 0x66FFEB3B, 18f), hl)
        // Corrected shapes: a box with an arrow to a circle, and an underline.
        items += ShapeItem(id(), z++, pen, ShapeKind.RECTANGLE, ShapeGeometry.Polygon(listOf(Pt(80f, 260f), Pt(330f, 260f), Pt(330f, 400f), Pt(80f, 400f))), null)
        items += ShapeItem(id(), z++, blue, ShapeKind.ARROW, ShapeGeometry.Segment(Pt(340f, 330f), Pt(470f, 330f), 22f), null)
        items += ShapeItem(id(), z++, pen, ShapeKind.CIRCLE, ShapeGeometry.Ellipse(Pt(560f, 330f), 75f, 75f, 0f), null)
        items += ShapeItem(id(), z++, InkBrush(InkTool.MARKER, 0xFFD93025.toInt(), 4f), ShapeKind.LINE, ShapeGeometry.Segment(Pt(72f, 450f), Pt(420f, 450f)), null)
        items += ShapeItem(id(), z++, pen, ShapeKind.TRIANGLE, ShapeGeometry.Polygon(listOf(Pt(120f, 640f), Pt(220f, 480f), Pt(320f, 640f))), null)
        ink.commit(page, InkEdit(emptyList(), items))
        noteId
    }

    @Test
    fun captureHandwriting() {
        seed()
        seedInk()
        waitAdvancing { exists(hasText("Physics — waves")) }
        snap("library_with_ink_light")
        composeRule.onNodeWithText("Physics — waves").performClick()
        waitAdvancing { exists(hasTestTag(InkTestTags.CANVAS)) }
        snap("ink_light")
        composeRule.onNodeWithTag(InkTestTags.tool(EditorTool.ERASER)).performClick()
        snap("ink_eraser_tools_light")
        // Page settings (owner brief §5).
        composeRule.onNodeWithContentDescription(string(R.string.ink_more_options)).performClick()
        waitAdvancing { exists(hasTestTag(InkTestTags.PAGE_SETTINGS)) }
        composeRule.onNodeWithTag(InkTestTags.PAGE_SETTINGS).performClick()
        waitAdvancing { exists(hasTestTag(com.appname.notes.feature.ink.PageSettingsTags.DIALOG)) }
        snap("page_settings_light")
        composeRule.onNodeWithTag(com.appname.notes.feature.ink.PageSettingsTags.DONE).performScrollTo().performClick()
        waitAdvancing { !exists(hasTestTag(com.appname.notes.feature.ink.PageSettingsTags.DIALOG)) }
        pressBack()
        setTheme(R.string.settings_theme_dark)
        composeRule.onNodeWithText("Physics — waves").performClick()
        waitAdvancing { exists(hasTestTag(InkTestTags.CANVAS)) }
        snap("ink_dark")
    }

    /** Every pen, pencil and brush variant (owner brief §12), one wavy stroke each, in a grid of 4 columns. */
    private fun seedVariants(): com.appname.notes.core.domain.model.NoteId = runBlocking {
        val ink = data.ink()
        val noteId = ink.createInkNote(null, PageTemplate.BLANK).getOrNull()!!
        data.notes().setTitle(noteId, "All brushes")
        val page = (ink.loadPages(noteId) as AppResult.Success).value.first().info.id
        val colours = listOf(0xFF1F1F1F, 0xFF1A73E8, 0xFFD93025, 0xFF188038, 0xFF9334E6, 0xFFE8710A, 0xFF795548, 0xFF12B5CB).map { it.toInt() }
        val items = BrushStyle.entries.mapIndexed { i, style ->
            val col = i % 4
            val row = i / 4
            val b = StrokePoints.Builder(hasPressure = true, hasTilt = false, hasOrientation = false)
            for (k in 0..40) {
                val t = k / 40f
                val x = 40f + col * 190f + t * 150f
                val y = 70f + row * 130f + kotlin.math.sin(t * 2 * Math.PI).toFloat() * 28f
                b.add(x, y, k * 8, 0.25f + 0.7f * kotlin.math.sin(t * Math.PI).toFloat())
            }
            val colour = if (style.tool == InkTool.HIGHLIGHTER) (0x66 shl 24) or 0xFFEB3B else colours[row % colours.size]
            InkCodec.quantize(StrokeItem(id(), i.toLong() + 1, InkBrush(style.tool, colour, style.defaultSize, style), b.build()))
        }
        ink.commit(page, InkEdit(emptyList(), items))
        noteId
    }

    @Test
    fun captureTools() {
        seedVariants()
        waitAdvancing { exists(hasText("All brushes")) }
        composeRule.onNodeWithText("All brushes").performClick()
        waitAdvancing { exists(hasTestTag(InkTestTags.CANVAS)) }
        snap("brushes_light")
        composeRule.onNodeWithTag(InkTestTags.tool(EditorTool.PENCIL)).performClick()
        composeRule.onNodeWithTag(InkTestTags.tool(EditorTool.PENCIL)).performClick()
        waitAdvancing { exists(hasTestTag(InkToolTags.PANEL)) }
        snap("tool_panel_pencil_light")
        composeRule.onNodeWithTag(InkToolTags.PANEL_CLOSE).performClick()
        composeRule.onNodeWithTag(InkTestTags.tool(EditorTool.BRUSH)).performClick()
        composeRule.onNodeWithTag(InkToolTags.VARIANT_CHIP).performClick()
        waitAdvancing { exists(hasTestTag(InkToolTags.PANEL)) }
        snap("tool_panel_brush_light")
        composeRule.onNodeWithTag(InkToolTags.PANEL_CLOSE).performClick()
        composeRule.onNodeWithTag(InkToolTags.MORE_COLORS).performScrollTo().performClick()
        waitAdvancing { exists(hasTestTag(ColorPickerTags.DIALOG)) }
        snap("color_palettes_light")
        composeRule.onNodeWithTag(ColorPickerTags.TAB_CUSTOM).performClick()
        composeRule.waitForIdle()
        snap("color_custom_light")
        pressBackInDialog()
        composeRule.onNodeWithTag(InkTestTags.tool(EditorTool.LASER)).let { n -> runCatching { n.performScrollTo() }; n.performClick() }
        composeRule.waitForIdle()
        snap("laser_tools_light")
        // A rainbow trail, mid-gesture (the pointer is still down).
        composeRule.onNodeWithTag(InkToolTags.LASER_RAINBOW).performClick()
        composeRule.waitForIdle()
        laserGesture()
        snap("laser_rainbow_light")
        laserGesture(up = true)
        pressBack()
        setTheme(R.string.settings_theme_dark)
        composeRule.onNodeWithText("All brushes").performClick()
        waitAdvancing { exists(hasTestTag(InkTestTags.CANVAS)) }
        composeRule.onNodeWithTag(InkTestTags.tool(EditorTool.PEN)).performClick()
        composeRule.onNodeWithTag(InkToolTags.VARIANT_CHIP).performClick()
        waitAdvancing { exists(hasTestTag(InkToolTags.PANEL)) }
        snap("tool_panel_pen_dark")
    }

    private fun laserGesture(up: Boolean = false) {
        composeRule.runOnUiThread {
            fun find(v: android.view.View): com.appname.notes.feature.ink.InkCanvasView? =
                v as? com.appname.notes.feature.ink.InkCanvasView
                    ?: (v as? android.view.ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(g.getChildAt(it)) } }
            val view = find(composeRule.activity.window.decorView)!!
            val now = android.os.SystemClock.uptimeMillis()
            fun send(action: Int, x: Float, y: Float, t: Long) {
                val props = android.view.MotionEvent.PointerProperties().apply { id = 0; toolType = android.view.MotionEvent.TOOL_TYPE_STYLUS }
                val coords = android.view.MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 0.6f }
                val e = android.view.MotionEvent.obtain(now - 300, t, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_STYLUS, 0)
                view.dispatchTouchEvent(e)
                e.recycle()
            }
            val n = 40
            if (up) {
                send(android.view.MotionEvent.ACTION_UP, view.width * 0.85f, view.height * 0.45f, now)
                return@runOnUiThread
            }
            for (i in 0..n) {
                val f = i / n.toFloat()
                val x = view.width * (0.15f + 0.7f * f)
                val y = view.height * 0.45f + kotlin.math.sin(f * 2 * Math.PI).toFloat() * view.height * 0.12f
                send(if (i == 0) android.view.MotionEvent.ACTION_DOWN else android.view.MotionEvent.ACTION_MOVE, x, y, now - 300 + i * 300L / n)
            }
            view.invalidate()
        }
        composeRule.waitForIdle()
    }

    private fun pressBackInDialog() {
        composeRule.onNodeWithText(string(R.string.action_cancel)).performClick()
        waitAdvancing { !exists(hasTestTag(ColorPickerTags.DIALOG)) }
    }

    protected fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        waitAdvancing { exists(hasTestTag(LibraryTestTags.SCREEN)) }
    }

    protected fun setTheme(id: Int) {
        composeRule.onNodeWithTag("nav_settings").performClick()
        waitAdvancing { exists(hasText(string(id))) }
        composeRule.onNodeWithText(string(id)).performClick()
        waitAdvancing { exists(hasText(string(id)) and isSelected()) }
        composeRule.onNodeWithTag("nav_home").performClick()
        waitAdvancing { exists(hasTestTag(LibraryTestTags.SCREEN)) }
    }

    /** Settings in the Liquid Glass design (owner brief §19), light and dark, with the wallpaper picker. */
    @Test
    fun captureSettings() {
        composeRule.onNodeWithTag("nav_settings").performClick()
        waitAdvancing { exists(hasTestTag(com.appname.notes.feature.settings.SettingsTestTags.LIST)) }
        snap("settings_light")
        composeRule.onNodeWithTag(com.appname.notes.feature.settings.SettingsTestTags.WALLPAPER).performClick()
        waitAdvancing { exists(hasTestTag(com.appname.notes.feature.settings.SettingsTestTags.WALLPAPER_DIALOG)) }
        snap("settings_wallpaper_light")
        composeRule.onNodeWithText(string(R.string.action_close)).performClick()
        waitAdvancing { !exists(hasTestTag(com.appname.notes.feature.settings.SettingsTestTags.WALLPAPER_DIALOG)) }
        composeRule.onNodeWithText(string(R.string.settings_theme_dark)).performClick()
        waitAdvancing { exists(hasText(string(R.string.settings_theme_dark)) and isSelected()) }
        snap("settings_dark")
        // A different accent and the Ocean wallpaper, compact.
        composeRule.onNodeWithTag(com.appname.notes.feature.settings.SettingsTestTags.accent(com.appname.notes.core.designsystem.theme.Accent.VIOLET)).performClick()
        waitAdvancing { exists(hasTestTag(com.appname.notes.feature.settings.SettingsTestTags.accent(com.appname.notes.core.designsystem.theme.Accent.VIOLET)) and isSelected()) }
        snap("settings_violet_dark")
        // Note defaults (owner brief §5, §19).
        composeRule.onNodeWithTag(com.appname.notes.feature.settings.SettingsTestTags.LIST)
            .performScrollToNode(hasTestTag(com.appname.notes.feature.settings.SettingsTestTags.DARK_PAPER))
        snap("settings_note_defaults_dark")
    }

    /** The Choose-template screen, a preview, a note made from a planner, and the custom template editor (§4). */
    @Test
    fun captureTemplates() {
        composeRule.onNodeWithTag(LibraryTestTags.CREATE).performClick()
        waitAdvancing { exists(hasTestTag(LibraryTestTags.CHOOSE_TEMPLATE)) }
        composeRule.onNodeWithTag(LibraryTestTags.CHOOSE_TEMPLATE).performClick()
        waitAdvancing { exists(hasTestTag(TemplatesTestTags.tile("basic.lined"))) }
        snap("templates_light")
        composeRule.onNodeWithTag(TemplatesTestTags.chip(TemplateCategory.PLANNERS)).performClick()
        waitAdvancing { exists(hasTestTag(TemplatesTestTags.tile("planners.weekly"))) }
        snap("templates_planners_light")
        composeRule.onNodeWithTag(TemplatesTestTags.tile("planners.weekly")).performClick()
        waitAdvancing { exists(hasTestTag(TemplatesTestTags.USE)) }
        snap("template_preview_light")
        composeRule.onNodeWithTag(TemplatesTestTags.USE).performClick()
        waitAdvancing { exists(hasTestTag(InkTestTags.CANVAS)) }
        snap("ink_weekly_planner_light")
        pressBack()
        snap("library_template_note_light")

        // Settings › Templates › New template, with a few options changed.
        composeRule.onNodeWithTag("nav_settings").performClick()
        waitAdvancing { exists(hasTestTag(com.appname.notes.feature.settings.SettingsTestTags.LIST)) }
        composeRule.onNodeWithTag(com.appname.notes.feature.settings.SettingsTestTags.LIST)
            .performScrollToNode(hasTestTag(com.appname.notes.feature.settings.SettingsTestTags.TEMPLATES))
        composeRule.onNodeWithTag(com.appname.notes.feature.settings.SettingsTestTags.TEMPLATES).performClick()
        waitAdvancing { exists(hasTestTag(TemplatesTestTags.NEW)) }
        composeRule.onNodeWithTag(TemplatesTestTags.NEW).performClick()
        waitAdvancing { exists(hasTestTag(TemplateEditorTestTags.PREVIEW)) }
        snap("template_editor_light")
        val form = composeRule.onNodeWithTag(TemplateEditorTestTags.FORM)
        form.performScrollToNode(hasTestTag(TemplateEditorTestTags.header(HeaderStyle.TITLE_AND_DATE)))
        composeRule.onNodeWithTag(TemplateEditorTestTags.header(HeaderStyle.TITLE_AND_DATE)).performClick()
        form.performScrollToNode(hasTestTag(TemplateEditorTestTags.ruling(Ruling.DOTS)))
        composeRule.onNodeWithTag(TemplateEditorTestTags.ruling(Ruling.DOTS)).performClick()
        composeRule.onNodeWithTag(TemplateEditorTestTags.FORM).performScrollToNode(hasTestTag(TemplateEditorTestTags.paper(3)))
        composeRule.onNodeWithTag(TemplateEditorTestTags.paper(3)).performClick()
        snap("template_editor_changed_light")
    }

    @Test
    fun captureScreens() {
        seed()
        waitAdvancing { exists(hasText("Groceries")) }
        snap("library_light")

        // The single + and its menu (owner brief §3).
        composeRule.onNodeWithTag(LibraryTestTags.CREATE).performClick()
        waitAdvancing { exists(hasTestTag(LibraryTestTags.NEW_NOTE)) }
        snap("library_create_menu_light")
        composeRule.onNodeWithTag(LibraryTestTags.CREATE).performClick()
        waitAdvancing { !exists(hasTestTag(LibraryTestTags.NEW_NOTE)) }

        // Long-press selects; the header becomes the selection bar.
        composeRule.onNodeWithText("Groceries").performTouchInput { longClick() }
        waitAdvancing { exists(hasTestTag(LibraryTestTags.SELECTION_BAR)) }
        composeRule.onNodeWithText("Project meeting").performClick()
        snap("library_selecting_light")
        composeRule.onNodeWithTag(LibraryTestTags.SELECTION_CLEAR).performClick()
        waitAdvancing { !exists(hasTestTag(LibraryTestTags.SELECTION_BAR)) }

        composeRule.onNodeWithText("Lecture 4 — Chain rule").performClick()
        waitAdvancing { exists(hasTestTag(EditorTestTags.BODY)) }
        snap("editor_light")
        pressBack() // the bottom bar is hidden while editing on phones

        setTheme(R.string.settings_theme_dark)
        waitAdvancing { exists(hasText("Groceries")) }
        snap("library_dark")

        composeRule.onNodeWithTag(LibraryTestTags.SEARCH).performClick()
        waitAdvancing { exists(hasTestTag(SearchTestTags.FIELD)) }
        composeRule.onNodeWithTag(SearchTestTags.FIELD).performTextInput("deriv")
        waitAdvancing { exists(hasTestTag(SearchTestTags.RESULTS)) }
        snap("search_dark")
    }
}

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneScreenshotTest : ScreenshotTestBase("phone") {

    @Test
    fun phoneUsesBottomNavigationBar() {
        val item = composeRule.onNodeWithTag("nav_settings").fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertThat(item.top).isGreaterThan(root.height * 0.75f) // bottom of the screen
    }

    @Test
    fun editorIsFullScreenOnPhones() {
        composeRule.onNodeWithTag(LibraryTestTags.CREATE).performClick()
        waitAdvancing { exists(hasTestTag(LibraryTestTags.NEW_NOTE)) }
        composeRule.onNodeWithTag(LibraryTestTags.NEW_NOTE).performClick()
        waitAdvancing { exists(hasTestTag(EditorTestTags.BODY)) }
        assertThat(exists(hasTestTag(LibraryTestTags.SCREEN))).isFalse()
        assertThat(exists(hasTestTag("nav_settings"))).isFalse() // bottom bar hidden while editing
    }
}

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp-land-xhdpi")
class TabletScreenshotTest : ScreenshotTestBase("tablet") {

    @Test
    fun tabletUsesNavigationRail() {
        val item = composeRule.onNodeWithTag("nav_settings").fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertThat(item.left).isLessThan(root.width * 0.15f) // left edge, not a bottom bar
        assertThat(item.top).isLessThan(root.height * 0.75f)
    }

    @Test
    fun tabletShowsListAndNoteSideBySide() {
        waitAdvancing { exists(hasTestTag("detail_placeholder")) }
        composeRule.onNodeWithTag(LibraryTestTags.CREATE).performClick()
        waitAdvancing { exists(hasTestTag(LibraryTestTags.NEW_NOTE)) }
        composeRule.onNodeWithTag(LibraryTestTags.NEW_NOTE).performClick()
        waitAdvancing { exists(hasTestTag(EditorTestTags.BODY)) }
        assertThat(exists(hasTestTag(LibraryTestTags.SCREEN))).isTrue()
        val list = composeRule.onNodeWithTag(LibraryTestTags.SCREEN).fetchSemanticsNode().boundsInRoot
        val editor = composeRule.onNodeWithTag(EditorTestTags.SCREEN).fetchSemanticsNode().boundsInRoot
        assertThat(editor.left).isAtLeast(list.right - 1f)
    }

    @Test
    fun handwritingIsFullScreenOnTablets() {
        composeRule.onNodeWithTag(LibraryTestTags.CREATE).performClick()
        waitAdvancing { exists(hasTestTag(LibraryTestTags.CHOOSE_TEMPLATE)) }
        composeRule.onNodeWithTag(LibraryTestTags.CHOOSE_TEMPLATE).performClick()
        waitAdvancing { exists(hasTestTag(TemplatesTestTags.tile("basic.lined"))) }
        composeRule.onNodeWithTag(TemplatesTestTags.tile("basic.lined")).performClick()
        waitAdvancing { exists(hasTestTag(TemplatesTestTags.USE)) }
        composeRule.onNodeWithTag(TemplatesTestTags.USE).performClick()
        waitAdvancing { exists(hasTestTag(InkTestTags.CANVAS)) }
        assertThat(exists(hasTestTag(LibraryTestTags.SCREEN))).isFalse() // no list pane
        assertThat(exists(hasTestTag("nav_settings"))).isFalse() // no navigation rail
        val canvas = composeRule.onNodeWithTag(InkTestTags.CANVAS).fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertThat(canvas.width).isAtLeast(root.width - 1f)
    }
}
