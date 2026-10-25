package com.appname.notes.feature.ink

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appname.notes.R
import com.appname.notes.core.common.id.IdGenerator
import com.appname.notes.core.common.result.AppResult
import com.appname.notes.core.common.result.DomainError
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.repository.InkPageInfo
import com.appname.notes.core.domain.repository.InkRepository
import com.appname.notes.core.domain.repository.NoteRepository
import com.appname.notes.core.domain.repository.PdfDocumentInfo
import com.appname.notes.core.domain.repository.PdfRepository
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.common.coroutines.DispatcherProvider
import com.appname.notes.core.common.logging.AppLogger
import com.appname.notes.feature.pdf.PdfAnnotationWriter
import com.appname.notes.feature.pdf.PdfFileStore
import com.appname.notes.feature.pdf.PdfPageRenderer
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.appname.notes.core.ink.model.InkItem
import java.io.File
import java.io.IOException
import java.io.OutputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import com.appname.notes.core.ink.edit.InkSession
import com.appname.notes.core.ink.edit.PageEdit
import com.appname.notes.core.ink.edit.PageItems
import com.appname.notes.core.ink.input.EditorTool
import com.appname.notes.core.ink.input.EraserMode
import com.appname.notes.core.ink.input.InkInputController
import com.appname.notes.core.ink.input.InkToolState
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.layout.PageArrangement
import com.appname.notes.core.ink.layout.PageMode
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.core.ink.template.PageDesign
import com.appname.notes.core.ink.template.PageFormat
import com.appname.notes.core.ink.template.Ruling
import com.appname.notes.core.ink.template.SpacingPreset
import com.appname.notes.core.ink.template.TemplateLayout
import com.appname.notes.core.settings.FingerMode
import com.appname.notes.core.settings.InkSettings
import com.appname.notes.core.settings.SettingsRepository
import com.appname.notes.core.ink.edit.InkEdit
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.core.ink.model.BrushStyle
import com.appname.notes.core.settings.Recents
import com.appname.notes.core.settings.LaserSettings
import com.appname.notes.core.settings.ShapeSettings
import com.appname.notes.di.ApplicationScope
import com.appname.notes.feature.editor.ReadOnlyReason
import com.appname.notes.feature.editor.SaveStatus
import com.appname.notes.ui.common.UserMessage
import com.appname.notes.ui.common.toUserMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** Everything the handwriting screen shows around the canvas. */
data class InkEditorUi(
    val title: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val currentTemplate: PageTemplate = PageTemplate.BLANK,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val tools: InkToolState = InkToolState(),
    val hasSelection: Boolean = false,
    val readOnly: ReadOnlyReason? = null,
    val saveStatus: SaveStatus = SaveStatus.SAVED,
    val fingerDraws: Boolean = true,
    /** Bumps on every ink change so the canvas knows to redraw. */
    val version: Long = 0,
    /** One-shot request for the canvas to show a page (after adding one). */
    val scrollRequest: ScrollRequest? = null,
    val fitRequest: FitRequest? = null,
    /** A written annotated copy waiting for the share sheet. */
    val shareRequest: ShareRequest? = null,
    /** A PDF note: pages come from the document (no adding/deleting), export is available. */
    val isPdf: Boolean = false,
    /** The PDF file can't be displayed (missing or damaged). The ink is still there. */
    val pdfFailed: Boolean = false,
    val exporting: Boolean = false,
    /** Page settings (owner brief §5): how pages are arranged, their size, and the shown page's design. */
    val arrangement: PageArrangement = PageArrangement.DEFAULT,
    /** The note's page size; null for PDF notes (their pages keep the document's size). */
    val pageFormat: PageFormat? = null,
    val pageDesign: TemplateLayout? = null,
    /** Dark paper in dark theme (a global setting, shown in page settings too). */
    val darkPaper: Boolean = true,
    /** Recently used tool set-ups and colours, newest first, and the user's saved colours (owner brief §13–14). */
    val recentBrushes: List<InkBrush> = emptyList(),
    val recentColors: List<Int> = emptyList(),
    val savedColors: List<Int> = emptyList(),
    /** The laser pointer's look (owner brief §17). */
    val laser: LaserSettings = LaserSettings(),
) {
    /** The shape tool's current choices. */
    val shapeSettings: ShapeSettings get() = ShapeSettings(tools.shapeKind, tools.shapeColor, tools.shapeSize, tools.shapeFill)
}

data class ScrollRequest(val page: Int, val id: Long = System.nanoTime())

enum class FitMode { WIDTH, PAGE }

data class ShareRequest(val uri: Uri, val id: Long = System.nanoTime())

data class FitRequest(val mode: FitMode, val id: Long = System.nanoTime())

sealed interface InkLoad {
    data object Loading : InkLoad
    data object NotFound : InkLoad

    /** [session] and [controller] live as long as this ViewModel (they survive rotation). */
    data class Ready(val session: InkSession, val controller: InkInputController, val pdf: PdfPageRenderer? = null) : InkLoad
}

/**
 * One open handwritten note.
 *
 * The [InkSession] is the source of truth while open. Each change is queued and saved in order by one
 * worker running in the application scope, so saves finish even after the screen closes (FR-002/FR-008).
 * Failed saves are retried with growing pauses; a deleted note stops the queue and makes the screen
 * read-only.
 */
@HiltViewModel(assistedFactory = InkEditorViewModel.Factory::class)
class InkEditorViewModel @AssistedInject constructor(
    // A plain String: Dagger can't use Kotlin value classes in assisted factories (name mangling).
    @Assisted noteIdValue: String,
    @Assisted private val initialPdfPage: Int,
    private val ink: InkRepository,
    private val notes: NoteRepository,
    private val settings: SettingsRepository,
    private val ids: IdGenerator,
    @param:ApplicationScope private val appScope: CoroutineScope,
    private val pdfRepository: PdfRepository,
    private val pdfFiles: PdfFileStore,
    private val pdfWriter: PdfAnnotationWriter,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger,
    @param:ApplicationContext private val appContext: Context,
    reminderFactory: com.appname.notes.feature.reminders.NoteReminders.Factory,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        /** [initialPdfPage]: PDF page to show first, or -1 (Dagger's assisted factories can't take nullable Ints well). */
        fun create(noteId: String, initialPdfPage: Int): InkEditorViewModel
    }

    /** Declared first: other properties below use it while initialising. */
    val noteId = NoteId(noteIdValue)

    private val _load = MutableStateFlow<InkLoad>(InkLoad.Loading)
    val load: StateFlow<InkLoad> = _load.asStateFlow()

    private val _ui = MutableStateFlow(InkEditorUi())
    val ui: StateFlow<InkEditorUi> = _ui.asStateFlow()

    private val _message = MutableStateFlow<UserMessage?>(null)
    val message: StateFlow<UserMessage?> = _message.asStateFlow()

    /** This note's reminders (FR-010). */
    val reminders = reminderFactory.create(noteId, viewModelScope) { _message.value = it }

    /** Where the canvas was looking (kept across rotation). */
    var viewState: InkViewState? = null

    private var inkSettings = InkSettings()
    private var stylusNoticeShown = false

    /** Until the user picks a tool here, the remembered one from settings applies (it may load after the note). */
    private var toolPickedHere = false

    // ---------------------------------------------------------------- save queue

    private sealed interface Op {
        data class Commit(val edit: PageEdit) : Op
        data class AddPage(
            val index: Int,
            val template: PageTemplate,
            val layout: com.appname.notes.core.ink.template.TemplateLayout?,
            val result: CompletableDeferred<AppResult<InkPageInfo>>,
        ) : Op
        data class DeletePage(val pageId: String, val result: CompletableDeferred<AppResult<Unit>>) : Op
        data class SetTemplate(val pageId: String, val template: PageTemplate) : Op

        /** A page-settings change; failures are reported like any other save. */
        data class Run(val what: suspend () -> AppResult<*>) : Op
        data class Resize(val width: Float, val height: Float, val result: CompletableDeferred<AppResult<List<InkPageInfo>>>) : Op
    }

    private val queue = Channel<Op>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            settings.ink.collect { s ->
                inkSettings = s
                applySettings(s)
            }
        }
        viewModelScope.launch {
            notes.observeNote(noteId).collect { note ->
                if (note == null) return@collect
                _ui.update {
                    it.copy(
                        title = note.title,
                        readOnly = when {
                            it.readOnly == ReadOnlyReason.NEWER_VERSION -> ReadOnlyReason.NEWER_VERSION
                            note.trashedAt != null -> ReadOnlyReason.IN_TRASH
                            else -> null
                        },
                    )
                }
            }
        }
        // Read-only can come from the save worker (note deleted elsewhere): apply it on the main thread.
        viewModelScope.launch {
            ui.map { it.readOnly }.distinctUntilChanged().collect { reason ->
                controller()?.let { c ->
                    c.readOnly = reason != null
                    if (reason != null) c.cancel()
                }
                refresh()
            }
        }
        viewModelScope.launch {
            settings.noteDefaults.collect { d -> _ui.update { it.copy(darkPaper = d.darkPaper) } }
        }
        appScope.launch { drainQueue() }
    }

    private suspend fun load() {
        val note = notes.getNote(noteId)
        val pages = (ink.loadPages(noteId) as? AppResult.Success)?.value
        if (note == null || pages.isNullOrEmpty()) {
            _load.value = InkLoad.NotFound
            return
        }
        val session = InkSession(
            pages.map {
                InkSession.Page(it.info.id, it.info.template, it.info.width, it.info.height, PageItems.of(it.items), it.info.pdfPageIndex, it.info.layout)
            },
        )
        val arrangement = (ink.arrangement(noteId) as? AppResult.Success)?.value ?: PageArrangement.DEFAULT
        val pdfDocument = if (note.type == NoteType.PDF) (pdfRepository.document(noteId) as? AppResult.Success)?.value else null
        val pdfRenderer = pdfDocument?.let { doc ->
            PdfPageRenderer(appContext, pdfFiles.file(doc.fileName), logger).also { r ->
                r.onFailed = { _ui.update { ui -> ui.copy(pdfFailed = true) } }
            }
        }
        this.pdfDocument = pdfDocument
        val readOnly = when {
            pages.any { it.readOnly } -> ReadOnlyReason.NEWER_VERSION
            note.trashedAt != null -> ReadOnlyReason.IN_TRASH
            else -> null
        }
        val controller = InkInputController(session, ids::newId).also {
            it.readOnly = readOnly != null
            it.tools = toolsFrom(inkSettings)
        }
        session.onEdit = { edit ->
            enqueue(Op.Commit(edit))
            extendIfInfinite(edit.pageId)
            rememberUsedBrush(edit.edit)
        }
        session.onChanged = { refresh() }
        _ui.update {
            it.copy(
                title = note.title,
                readOnly = readOnly,
                isPdf = note.type == NoteType.PDF,
                // The renderer may already have reported a missing/damaged file: never undo that.
                pdfFailed = it.pdfFailed || (note.type == NoteType.PDF && pdfRenderer == null),
                arrangement = arrangement,
            )
        }
        _load.value = InkLoad.Ready(session, controller, pdfRenderer)
        // Opened from a search hit: show that PDF page first.
        if (initialPdfPage >= 0) {
            val index = session.pages.indexOfFirst { it.pdfPageIndex == initialPdfPage }
            if (index >= 0) _ui.update { it.copy(scrollRequest = ScrollRequest(index)) }
        }
        refresh()
    }

    private fun session(): InkSession? = (_load.value as? InkLoad.Ready)?.session
    private fun controller(): InkInputController? = (_load.value as? InkLoad.Ready)?.controller

    /** Recomputes the screen state from the session and controller. Main thread. */
    fun refresh() {
        val s = session() ?: return
        val c = controller() ?: return
        _ui.update { ui ->
            val page = ui.currentPage.coerceIn(0, (s.pages.size - 1).coerceAtLeast(0))
            val shown = s.pages.getOrNull(page)
            val firstInk = s.pages.firstOrNull { it.pdfPageIndex == null }
            ui.copy(
                pageCount = s.pages.size,
                currentPage = page,
                currentTemplate = shown?.template ?: PageTemplate.BLANK,
                pageDesign = shown?.takeIf { it.pdfPageIndex == null }?.let { PageDesign.current(it.layout, it.template, it.width, it.height) },
                pageFormat = if (ui.isPdf) null else firstInk?.let { PageFormat.closest(it.width, it.height) },
                canUndo = s.canUndo && ui.readOnly == null,
                canRedo = s.canRedo && ui.readOnly == null,
                tools = c.tools,
                hasSelection = c.selection != null,
                fingerDraws = inkSettings.fingerDraws,
                version = s.version,
            )
        }
    }

    private fun enqueue(op: Op) {
        pending.incrementAndGet()
        _ui.update { it.copy(saveStatus = SaveStatus.SAVING) }
        queue.trySend(op)
    }

    /** One worker, in order: the database always receives changes in the order they were made. */
    private suspend fun drainQueue() {
        for (op in queue) {
            process(op)
            if (pending.decrementAndGet() == 0) {
                _ui.update { if (it.saveStatus != SaveStatus.FAILED) it.copy(saveStatus = SaveStatus.SAVED) else it }
            }
        }
        // The screen is gone and every change is saved: an untouched new note isn't kept (like text notes).
        notes.discardIfBlank(noteId)
    }

    private suspend fun process(op: Op) {
        when (op) {
            is Op.Commit -> retrying { ink.commit(op.edit.pageId, op.edit.edit) }
            is Op.AddPage -> op.result.complete(retrying { ink.addPage(noteId, op.index, op.template, op.layout) })
            is Op.DeletePage -> op.result.complete(retrying { ink.deletePage(op.pageId) })
            is Op.SetTemplate -> retrying { ink.setTemplate(op.pageId, op.template) }
            is Op.Run -> retrying { op.what() }
            is Op.Resize -> op.result.complete(retrying { ink.resizePages(noteId, op.width, op.height) })
        }
    }

    private suspend fun <T> retrying(block: suspend () -> AppResult<T>): AppResult<T> {
        var wait = FIRST_RETRY_MS
        var attempt = 0
        while (true) {
            val r = block()
            if (r is AppResult.Success) {
                if (_ui.value.saveStatus == SaveStatus.FAILED) _ui.update { it.copy(saveStatus = SaveStatus.SAVING) }
                return r
            }
            val error = (r as AppResult.Failure).error
            if (error is DomainError.NotFound || error is DomainError.Validation) {
                // The note or page is gone (deleted elsewhere): stop editing, don't loop.
                _ui.update { it.copy(readOnly = it.readOnly ?: ReadOnlyReason.IN_TRASH) }
                _message.value = UserMessage(R.string.editor_not_found)
                return r
            }
            _ui.update { it.copy(saveStatus = SaveStatus.FAILED) }
            if (attempt == 0) _message.value = error.toUserMessage()
            attempt++
            if (attempt >= MAX_ATTEMPTS) return r // give up on this change; later ones still get their turn
            delay(wait)
            wait = (wait * 2).coerceAtMost(MAX_RETRY_MS)
        }
    }

    override fun onCleared() {
        // Let the worker finish what's queued, then stop.
        queue.close()
        (_load.value as? InkLoad.Ready)?.pdf?.close()
    }

    private var pdfDocument: PdfDocumentInfo? = null

    /** Suggested file name: "… (annotated).pdf" for a PDF note, the note's title for a handwritten one. */
    fun exportFileName(): String =
        if (pdfDocument == null) {
            "${_ui.value.title.ifBlank { appContext.getString(R.string.ink_untitled) }}.pdf"
        } else {
            "${_ui.value.title.ifBlank { pdfDocument?.displayName?.removeSuffix(".pdf") ?: "PDF" }} (annotated).pdf"
        }

    /** Every page of a handwritten note as exported, snapshotted on the main thread. */
    private fun exportPages(s: InkSession): List<ExportPage> =
        s.pages.map { ExportPage(it.width, it.height, it.template, it.layout, it.items.items) }

    /** What an export writes, taken on the main thread (page lists are immutable, so this is cheap and safe). */
    private sealed interface ExportData {
        data class Annotated(val doc: PdfDocumentInfo, val ink: Map<Int, List<InkItem>>) : ExportData
        data class Handwriting(val pages: List<ExportPage>) : ExportData
    }

    private fun exportData(s: InkSession): ExportData =
        pdfDocument?.let { ExportData.Annotated(it, inkByPdfPage(s)) } ?: ExportData.Handwriting(exportPages(s))

    /** Writes the export for this note: an annotated copy of the PDF, or the handwriting as a new PDF. */
    private suspend fun writeExport(data: ExportData, open: () -> OutputStream?): Boolean = when (data) {
        is ExportData.Annotated -> writeAnnotated(data.doc, data.ink, open)
        is ExportData.Handwriting -> withContext(dispatchers.io) {
            try {
                val out = open() ?: return@withContext false
                out.use { InkPdfWriter.write(data.pages, it) }
                true
            } catch (e: IOException) {
                logger.warn(TAG, "Note PDF export failed", e)
                false
            } catch (e: SecurityException) {
                logger.warn(TAG, "Note PDF export not permitted", e)
                false
            }
        }
    }

    /** Ink per PDF page, snapshotted on the main thread (page lists are immutable, so this is cheap and safe). */
    private fun inkByPdfPage(s: InkSession): Map<Int, List<InkItem>> =
        s.pages.mapNotNull { p -> p.pdfPageIndex?.let { it to p.items.items } }.toMap()

    /**
     * Writes a copy of the PDF with the ink drawn in (SRS 7.3) with [open]; the stored original is only read
     * (PDF-05). Runs in the app scope so it finishes even if the screen closes. Returns false on failure.
     */
    private suspend fun writeAnnotated(doc: PdfDocumentInfo, ink: Map<Int, List<InkItem>>, open: () -> OutputStream?): Boolean =
        withContext(dispatchers.io) {
            try {
                val out = open() ?: return@withContext false
                out.use { pdfWriter.writeAnnotatedCopy(pdfFiles.file(doc.fileName), ink, it) }
                true
            } catch (e: IOException) {
                logger.warn(TAG, "PDF export failed", e)
                false
            } catch (e: SecurityException) {
                logger.warn(TAG, "PDF export not permitted", e)
                false
            } catch (e: RuntimeException) { // a PDF PdfBox can't rewrite
                logger.warn(TAG, "PDF export failed", e)
                false
            }
        }

    /** "Export annotated PDF": [target] comes from the system "save as" screen. */
    fun exportPdf(target: Uri) {
        val data = exportData(session() ?: return)
        _ui.update { it.copy(exporting = true) }
        appScope.launch {
            val ok = writeExport(data) { appContext.contentResolver.openOutputStream(target, "wt") }
            _ui.update { it.copy(exporting = false) }
            val done = if (data is ExportData.Annotated) R.string.pdf_exported else R.string.ink_pdf_exported
            _message.value = UserMessage(if (ok) done else R.string.pdf_export_failed)
        }
    }

    /**
     * "Share annotated PDF": writes the copy to a private cache folder and offers it through the app's
     * FileProvider, so only the app the user picks can read it, and only that file.
     */
    fun sharePdf() {
        val data = exportData(session() ?: return)
        val name = exportFileName().replace(UNSAFE_FILE_CHARS, "_").take(MAX_FILE_NAME)
        _ui.update { it.copy(exporting = true) }
        appScope.launch {
            val dir = File(appContext.cacheDir, SHARE_DIR)
            val file = File(dir, name)
            val ok = writeExport(data) {
                dir.mkdirs()
                // Earlier shares are removed once the receiving app has had plenty of time to read them.
                val old = System.currentTimeMillis() - SHARE_KEEP_MS
                dir.listFiles()?.forEach { if (it.lastModified() < old) it.delete() }
                file.outputStream()
            }
            val uri = if (ok) FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", file) else null
            _ui.update { it.copy(exporting = false, shareRequest = uri?.let { u -> ShareRequest(u) }) }
            if (uri == null) _message.value = UserMessage(R.string.pdf_export_failed)
        }
    }

    fun consumeShareRequest() {
        _ui.update { it.copy(shareRequest = null) }
    }

    // ---------------------------------------------------------------- canvas callbacks

    fun onPageChanged(index: Int) {
        _ui.update { ui ->
            val p = session()?.pages?.getOrNull(index)
            ui.copy(
                currentPage = index,
                currentTemplate = p?.template ?: ui.currentTemplate,
                pageDesign = p?.takeIf { it.pdfPageIndex == null }?.let { PageDesign.current(it.layout, it.template, it.width, it.height) },
            )
        }
    }

    fun onStylusDetected() {
        if (inkSettings.stylusSeen || stylusNoticeShown) return
        stylusNoticeShown = true
        val auto = inkSettings.fingerMode == FingerMode.AUTO
        viewModelScope.launch { settings.updateInk { it.copy(stylusSeen = true) } }
        if (auto) _message.value = UserMessage(R.string.ink_stylus_detected)
    }

    fun onMessageShown() {
        _message.value = null
    }

    fun consumeScrollRequest() {
        _ui.update { it.copy(scrollRequest = null) }
    }

    /** Page navigation (SRS 7.1): jump to page [index] (0-based). */
    fun goToPage(index: Int) {
        val count = session()?.pages?.size ?: return
        if (index in 0 until count) _ui.update { it.copy(scrollRequest = ScrollRequest(index)) }
    }

    fun fit(mode: FitMode) {
        _ui.update { it.copy(fitRequest = FitRequest(mode)) }
    }

    fun consumeFitRequest() {
        _ui.update { it.copy(fitRequest = null) }
    }

    // ---------------------------------------------------------------- toolbar

    fun undo() {
        if (session()?.undo() == true) controller()?.refreshSelection()
        refresh()
    }

    fun redo() {
        if (session()?.redo() == true) controller()?.refreshSelection()
        refresh()
    }

    fun selectTool(tool: EditorTool) {
        val c = controller() ?: return
        toolPickedHere = true
        c.tools = c.tools.copy(tool = tool)
        refresh()
        persistInk { it.copy(lastTool = tool) }
    }

    fun setColor(colorArgb: Int) {
        val c = controller() ?: return
        val inkTool = c.tools.tool.inkTool() ?: return
        val brush = c.tools.brushes[inkTool] ?: return
        // The colour changes, the opacity stays (highlighters stay translucent whatever colour is picked).
        val color = (brush.colorArgb and ALPHA_MASK) or (colorArgb and RGB_MASK)
        updateBrush(inkTool, brush.copy(colorArgb = color))
        val opaque = colorArgb or ALPHA_MASK
        persistInk { it.copy(recentColors = Recents.push(it.recentColors, opaque, Recents.MAX_COLORS)) }
    }

    fun setSize(size: Float) {
        val c = controller() ?: return
        val inkTool = c.tools.tool.inkTool() ?: return
        val brush = c.tools.brushes[inkTool] ?: return
        if (!size.isFinite() || size <= 0f) return
        updateBrush(inkTool, brush.copy(size = size))
    }

    /** Picks a variant of the current tool (owner brief §12). Size goes to the variant's natural size; colour stays. */
    fun setStyle(style: BrushStyle) {
        val c = controller() ?: return
        val inkTool = c.tools.tool.inkTool() ?: return
        if (style.tool != inkTool) return
        val brush = c.tools.brushes[inkTool] ?: return
        if (brush.effectiveStyle == style) return
        updateBrush(inkTool, brush.copy(style = style, size = style.defaultSize).normalized())
    }

    /** Opacity 0..1 of the current tool's colour (highlighters at most [HIGHLIGHTER_MAX_OPACITY]). */
    fun setOpacity(opacity: Float) {
        val c = controller() ?: return
        val inkTool = c.tools.tool.inkTool() ?: return
        val brush = c.tools.brushes[inkTool] ?: return
        if (!opacity.isFinite()) return
        val max = if (inkTool == InkTool.HIGHLIGHTER) HIGHLIGHTER_MAX_OPACITY else 1f
        val alpha = (opacity.coerceIn(MIN_OPACITY, max) * 255).toInt()
        updateBrush(inkTool, brush.copy(colorArgb = (alpha shl 24) or (brush.colorArgb and RGB_MASK)))
    }

    fun setPressure(enabled: Boolean) {
        val c = controller() ?: return
        val inkTool = c.tools.tool.inkTool() ?: return
        val brush = c.tools.brushes[inkTool] ?: return
        updateBrush(inkTool, brush.copy(pressure = enabled).normalized())
    }

    /** Uses a recently used set-up again: its tool, variant, colour and size. */
    fun applyRecent(brush: InkBrush) {
        val c = controller() ?: return
        val tool = EditorTool.of(brush.tool)
        toolPickedHere = true
        c.tools = c.tools.copy(tool = tool)
        updateBrush(brush.tool, brush.normalized())
        persistInk { it.copy(lastTool = tool) }
    }

    // ------------------------------------------------------------ shape tool and arranging (7d)

    /** Changes the shape tool's shape, outline colour, width or fill; kept for next time. */
    fun setShape(shape: ShapeSettings) {
        val c = controller() ?: return
        val safe = shape.copy(size = shape.size.takeIf { it.isFinite() }?.coerceIn(0.5f, 40f) ?: 2.5f)
        c.tools = c.tools.copy(shapeKind = safe.kind, shapeColor = safe.colorArgb, shapeSize = safe.size, shapeFill = safe.fillArgb)
        refresh()
        persistInk { it.copy(shape = safe) }
    }

    fun bringSelectionToFront() {
        controller()?.bringSelectionToFront()
        refresh()
    }

    fun sendSelectionToBack() {
        controller()?.sendSelectionToBack()
        refresh()
    }

    fun rotateSelection() {
        controller()?.rotateSelection()
        refresh()
    }

    /** Changes how the laser pointer looks; kept for next time. */
    fun setLaser(laser: LaserSettings) {
        val safe = laser.copy(widthDp = laser.widthDp.takeIf { it.isFinite() }?.coerceIn(2f, 24f) ?: LaserSettings().widthDp, colorArgb = laser.colorArgb or ALPHA_MASK)
        _ui.update { it.copy(laser = safe) }
        persistInk { it.copy(laser = safe) }
    }

    fun saveColor(colorArgb: Int) {
        val opaque = colorArgb or ALPHA_MASK
        viewModelScope.launch {
            settings.updateInk { s -> if (opaque in s.savedColors) s else s.copy(savedColors = (s.savedColors + opaque).takeLast(Recents.MAX_SAVED)) }
        }
    }

    fun removeSavedColor(colorArgb: Int) {
        persistInk { s -> s.copy(savedColors = s.savedColors - colorArgb) }
    }

    /** A stroke was just drawn with the current tool: its set-up goes to the front of the recent list. */
    private fun rememberUsedBrush(edit: InkEdit) {
        if (edit.removed.isNotEmpty()) return
        val stroke = edit.added.singleOrNull() as? StrokeItem ?: return
        val c = controller() ?: return
        val current = c.tools.tool.inkTool()?.let { c.tools.brushes[it] } ?: return
        val used = stroke.brush.normalized()
        if (used != current.normalized()) return // an undo/redo or a pasted stroke, not a new one
        if (inkSettings.recentBrushes.firstOrNull() == used) return
        persistInk { it.copy(recentBrushes = Recents.push(it.recentBrushes, used, Recents.MAX_BRUSHES)) }
    }

    private fun updateBrush(tool: InkTool, brush: InkBrush) {
        val c = controller() ?: return
        c.tools = c.tools.copy(brushes = c.tools.brushes + (tool to brush))
        refresh()
        persistInk { it.copy(brushes = it.brushes + (tool to brush)) }
    }

    fun setEraserMode(mode: EraserMode) {
        val c = controller() ?: return
        c.tools = c.tools.copy(eraserMode = mode)
        refresh()
        persistInk { it.copy(eraserMode = mode) }
    }

    fun setEraserSize(sizeDp: Double) {
        val c = controller() ?: return
        c.tools = c.tools.copy(eraserSizeDp = sizeDp)
        refresh()
    }

    fun deleteSelection() {
        controller()?.deleteSelection()
        refresh()
    }

    fun duplicateSelection() {
        controller()?.duplicateSelection()
        refresh()
    }

    fun recolorSelection(colorArgb: Int) {
        controller()?.recolorSelection(colorArgb)
        refresh()
    }

    fun clearSelection() {
        controller()?.clearSelection()
        refresh()
    }

    // ---------------------------------------------------------------- pages and note

    fun addPage() {
        val s = session() ?: return
        if (_ui.value.readOnly != null || _ui.value.isPdf) return
        val index = (_ui.value.currentPage + 1).coerceAtMost(s.pages.size)
        appendPage(index, scroll = true)
    }

    /** Adds a page after [index]: same size and design as the page before it (a template repeats). */
    private fun appendPage(index: Int, scroll: Boolean): kotlinx.coroutines.Job? {
        val s = session() ?: return null
        val before = s.pages.getOrNull(index - 1) ?: s.pages.lastOrNull() ?: return null
        val template = before.template
        val layout = PageDesign.current(before.layout, before.template, before.width, before.height)
        val result = CompletableDeferred<AppResult<InkPageInfo>>()
        enqueue(Op.AddPage(index, template, layout, result))
        return viewModelScope.launch {
            when (val r = result.await()) {
                is AppResult.Success -> {
                    val info = r.value
                    s.insertPage(info.index, InkSession.Page(info.id, info.template, info.width, info.height, PageItems.EMPTY, layout = info.layout))
                    if (scroll) _ui.update { it.copy(scrollRequest = ScrollRequest(info.index)) }
                    refresh()
                }
                is AppResult.Failure -> Unit // message already shown by the queue
            }
        }
    }

    fun deleteCurrentPage() {
        val s = session() ?: return
        if (_ui.value.readOnly != null || _ui.value.isPdf || s.pages.size <= 1) return
        val page = s.pages.getOrNull(_ui.value.currentPage) ?: return
        val result = CompletableDeferred<AppResult<Unit>>()
        enqueue(Op.DeletePage(page.id, result))
        viewModelScope.launch {
            if (result.await() is AppResult.Success) {
                controller()?.clearSelection()
                s.removePage(page.id)
                refresh()
            }
        }
    }

    fun setTemplate(template: PageTemplate) {
        val s = session() ?: return
        if (_ui.value.readOnly != null || _ui.value.isPdf) return
        val page = s.pages.getOrNull(_ui.value.currentPage) ?: return
        s.setTemplate(page.id, template)
        enqueue(Op.SetTemplate(page.id, template))
        _ui.update { it.copy(currentTemplate = template) }
    }

    // ---------------------------------------------------------------- page settings (owner brief §5)

    private val canChangePages: Boolean get() = _ui.value.readOnly == null

    fun setArrangement(arrangement: PageArrangement) {
        if (!canChangePages || arrangement == _ui.value.arrangement) return
        _ui.update { it.copy(arrangement = arrangement) }
        enqueue(Op.Run { ink.setArrangement(noteId, arrangement) })
        if (arrangement.mode == PageMode.INFINITE) session()?.pages?.lastOrNull()?.let { extendIfInfinite(it.id) }
    }

    /** Every handwriting page gets [format]'s size (never smaller than its ink). Not for PDF notes. */
    fun setPageFormat(format: PageFormat) {
        val s = session() ?: return
        if (!canChangePages || _ui.value.isPdf || format == _ui.value.pageFormat) return
        controller()?.clearSelection()
        val result = CompletableDeferred<AppResult<List<InkPageInfo>>>()
        enqueue(Op.Resize(format.width, format.height, result))
        viewModelScope.launch {
            val r = result.await() as? AppResult.Success ?: return@launch
            val byId = r.value.associateBy { it.id }
            // Only the size comes from the result: a design chosen meanwhile (already queued after this) is kept.
            s.updatePages { p -> byId[p.id]?.let { p.copy(width = it.width, height = it.height, layout = p.layout?.resized(it.width, it.height)) } ?: p }
            refresh()
        }
    }

    /** A plain background with [preset] spacing on the shown page or all pages. Replaces template designs. */
    fun setBackground(ruling: Ruling, preset: SpacingPreset, allPages: Boolean) =
        changeDesigns(allPages) { PageDesign.withBackground(it, ruling, preset) }

    fun setPaperColor(color: Int, allPages: Boolean) = changeDesigns(allPages) { PageDesign.withPaperColor(it, color) }

    private fun changeDesigns(allPages: Boolean, change: (TemplateLayout) -> TemplateLayout) {
        val s = session() ?: return
        if (!canChangePages || _ui.value.isPdf) return
        val shownId = s.pages.getOrNull(_ui.value.currentPage)?.id ?: return
        val targets = s.pages.filter { it.pdfPageIndex == null && (allPages || it.id == shownId) }
        val layouts = targets.associate { p -> p.id to change(PageDesign.current(p.layout, p.template, p.width, p.height)) }
        s.updatePages { p -> layouts[p.id]?.let { p.copy(layout = it, template = it.basicTemplate) } ?: p }
        enqueue(Op.Run { ink.setPageLayouts(noteId, layouts) })
        refresh()
    }

    fun setDarkPaper(enabled: Boolean) {
        viewModelScope.launch { settings.updateNoteDefaults { it.copy(darkPaper = enabled) } }
    }

    /** Infinite mode: writing on the last page adds an empty one after it, so the surface keeps growing. */
    private var extending = false

    private fun extendIfInfinite(pageId: String) {
        val s = session() ?: return
        if (_ui.value.arrangement.mode != PageMode.INFINITE || _ui.value.isPdf || extending) return
        val last = s.pages.lastOrNull() ?: return
        if (last.id != pageId || last.items.items.isEmpty()) return
        extending = true
        val job = appendPage(s.pages.size, scroll = false)
        // One page at a time: the next can be added once this one is in (or failed).
        viewModelScope.launch {
            job?.join()
            extending = false
        }
    }

    fun rename(title: String) {
        viewModelScope.launch {
            val r = notes.setTitle(noteId, title)
            if (r is AppResult.Failure) _message.value = r.error.toUserMessage()
        }
    }

    /** Moves the note to the trash; returns true when done so the screen can close. */
    suspend fun moveToTrash(): Boolean = notes.moveToTrash(setOf(noteId)) is AppResult.Success

    // ---------------------------------------------------------------- settings

    /**
     * Saves a tool change. While our own saves are still on their way, the settings flow can echo an older value;
     * tool state isn't taken from the flow then, so a quick second tap is never undone by the first one's echo.
     */
    private var localInkWrites = 0

    private fun persistInk(change: (InkSettings) -> InkSettings) {
        localInkWrites++
        viewModelScope.launch {
            try {
                settings.updateInk(change)
            } finally {
                localInkWrites--
            }
        }
    }

    private fun applySettings(s: InkSettings) {
        val c = controller()
        if (c != null && localInkWrites == 0) {
            val tool = if (toolPickedHere) c.tools.tool else s.lastTool
            val next = toolsFrom(s).copy(tool = tool, eraserSizeDp = c.tools.eraserSizeDp)
            if (next != c.tools) c.tools = next
        }
        _ui.update { it.copy(recentBrushes = s.recentBrushes, recentColors = s.recentColors, savedColors = s.savedColors, laser = if (localInkWrites == 0) s.laser else it.laser) }
        refresh()
    }

    private fun toolsFrom(s: InkSettings) = InkToolState(
        tool = s.lastTool,
        brushes = s.brushes,
        eraserMode = s.eraserMode,
        shapeCorrection = s.shapeCorrection,
        holdMillis = s.holdDuration.millis,
        shapeKind = s.shape.kind,
        shapeColor = s.shape.colorArgb,
        shapeSize = s.shape.size,
        shapeFill = s.shape.fillArgb,
    )

    private companion object {
        const val TAG = "InkEditor"
        const val SHARE_DIR = "shared"
        const val SHARE_KEEP_MS = 60 * 60 * 1000L
        const val MAX_FILE_NAME = 120
        val UNSAFE_FILE_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
        const val MAX_ATTEMPTS = 6
        const val FIRST_RETRY_MS = 500L
        const val MAX_RETRY_MS = 5_000L
        const val ALPHA_MASK = 0xFF shl 24
        const val RGB_MASK = 0xFFFFFF
        const val MIN_OPACITY = 0.1f
    }
}

internal fun EditorTool.inkTool(): InkTool? = inkTool
