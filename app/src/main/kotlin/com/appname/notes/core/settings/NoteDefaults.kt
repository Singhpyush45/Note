package com.appname.notes.core.settings

import com.appname.notes.core.ink.layout.PageArrangement
import com.appname.notes.core.ink.layout.PageMode
import com.appname.notes.core.ink.layout.ScrollAxis
import com.appname.notes.core.ink.template.BuiltInTemplates
import com.appname.notes.core.ink.template.PageFormat
import com.appname.notes.core.ink.template.TemplateCategory
import com.appname.notes.core.ink.template.TemplateColors
import com.appname.notes.core.ink.template.TemplateLayout

/** What "Blank note" in the + menu makes (owner brief §3, §19). */
enum class BlankNoteKind { TYPED, HANDWRITING }

/**
 * Settings › Note defaults (owner brief §5, §19): how new notes start. Page size, arrangement (split/infinite),
 * scroll direction and page style are separate choices. Each note then keeps its own settings.
 */
data class NoteDefaults(
    val blankNote: BlankNoteKind = BlankNoteKind.TYPED,
    /** Id of a Basic template (Blank page, Lined, Grid (narrow) …). */
    val pageStyle: String = DEFAULT_STYLE,
    val pageFormat: PageFormat = PageFormat.A4_PORTRAIT,
    val pageMode: PageMode = PageMode.SPLIT,
    val scrollAxis: ScrollAxis = ScrollAxis.VERTICAL,
    val paperColor: Int = TemplateColors.PAPER,
    /** In dark theme, show pages as dark paper (display only). */
    val darkPaper: Boolean = true,
) {
    val arrangement: PageArrangement get() = PageArrangement(pageMode, scrollAxis)

    /** The first page of a new blank handwritten note: the default style, size and paper colour. */
    fun newPageLayout(): TemplateLayout {
        val style = BuiltInTemplates.byId(pageStyle)?.takeIf { it.category == TemplateCategory.BASIC } ?: BuiltInTemplates.byId(DEFAULT_STYLE)!!
        return style.layout.resized(pageFormat.width, pageFormat.height).copy(paperColor = paperColor)
    }

    companion object {
        const val DEFAULT_STYLE = "basic.lined"

        /** The page styles offered as defaults: the Basic templates. */
        val STYLES: List<String> get() = BuiltInTemplates.inCategory(TemplateCategory.BASIC).map { it.id }
    }
}
