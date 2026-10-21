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
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.core.settings.FingerMode
import com.appname.notes.core.settings.InkSettings
import com.appname.notes.core.settings.SettingsRepository
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
    /** A PDF note: pages come from the document (no adding/deleting), export is available. */
    val isPdf: Boolean = false,
    /** The PDF file can't be displayed (missing or damaged). The ink is still there. */
    val pdfFailed: Boolean = false,
    val exporting: Boolean = false,
)

data class ScrollRequest(val page: Int, val id: Long = System.nanoTime())

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
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(noteId: String): InkEditorViewModel
    }

    /** Declared first: other properties below use it while initialising. */
    val noteId = NoteId(noteIdValue)

    private val _load = MutableStateFlow<InkLoad>(InkLoad.Loading)
    val load: StateFlow<InkLoad> = _load.asStateFlow()

    private val _ui = MutableStateFlow(InkEditorUi())
    val ui: StateFlow<InkEditorUi> = _ui.asStateFlow()

    private val _message = MutableStateFlow<UserMessage?>(null)
    val message: StateFlow<UserMessage?> = _message.asStateFlow()

    /** Where the canvas was looking (kept across rotation). */
    var viewState: InkViewState? = null

    private var inkSettings = InkSettings()
    private var stylusNoticeShown = false

    /** Until the user picks a tool here, the remembered one from settings applies (it may load after the note). */
    private var toolPickedHere = false

    // ---------------------------------------------------------------- save queue

    private sealed interface Op {
        data class Commit(val edit: PageEdit) : Op
        data class AddPage(val index: Int, val template: PageTemplate, val result: CompletableDeferred<AppResult<InkPageInfo>>) : Op
        data class DeletePage(val pageId: String, val result: CompletableDeferred<AppResult<Unit>>) : Op
        data class SetTemplate(val pageId: String, val template: PageTemplate) : Op
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
                InkSession.Page(it.info.id, it.info.template, it.info.width, it.info.height, PageItems.of(it.items), it.info.pdfPageIndex)
            },
        )
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
        session.onEdit = { edit -> enqueue(Op.Commit(edit)) }
        session.onChanged = { refresh() }
        _ui.update { it.copy(title = note.title, readOnly = readOnly, isPdf = note.type == NoteType.PDF, pdfFailed = note.type == NoteType.PDF && pdfRenderer == null) }
        _load.value = InkLoad.Ready(session, controller, pdfRenderer)
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
            ui.copy(
                pageCount = s.pages.size,
                currentPage = page,
                currentTemplate = s.pages.getOrNull(page)?.template ?: PageTemplate.BLANK,
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
            is Op.AddPage -> op.result.complete(retrying { ink.addPage(noteId, op.index, op.template) })
            is Op.DeletePage -> op.result.complete(retrying { ink.deletePage(op.pageId) })
            is Op.SetTemplate -> retrying { ink.setTemplate(op.pageId, op.template) }
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

    /** Suggested file name for "Export annotated PDF". */
    fun exportFileName(): String = "${_ui.value.title.ifBlank { pdfDocument?.displayName?.removeSuffix(".pdf") ?: "PDF" }} (annotated).pdf"

    /**
     * Writes a copy of the PDF with the ink drawn in (SRS 7.3) to [target]. The stored original is only read.
     * Runs in the app scope so it finishes even if the screen closes.
     */
    fun exportPdf(target: Uri) {
        val doc = pdfDocument ?: return
        val s = session() ?: return
        // Snapshot on the main thread: page lists are immutable.
        val ink = s.pages.mapNotNull { p -> p.pdfPageIndex?.let { it to p.items.items } }.toMap()
        _ui.update { it.copy(exporting = true) }
        appScope.launch {
            val ok = withContext(dispatchers.io) {
                try {
                    val out = appContext.contentResolver.openOutputStream(target, "wt") ?: return@withContext false
                    out.use { pdfWriter.writeAnnotatedCopy(pdfFiles.file(doc.fileName), ink, it) }
                    true
                } catch (e: java.io.IOException) {
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
            _ui.update { it.copy(exporting = false) }
            _message.value = UserMessage(if (ok) R.string.pdf_exported else R.string.pdf_export_failed)
        }
    }

    // ---------------------------------------------------------------- canvas callbacks

    fun onPageChanged(index: Int) {
        _ui.update { ui ->
            val template = session()?.pages?.getOrNull(index)?.template ?: ui.currentTemplate
            ui.copy(currentPage = index, currentTemplate = template)
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
        viewModelScope.launch { settings.updateInk { it.copy(lastTool = tool) } }
    }

    fun setColor(colorArgb: Int) {
        val c = controller() ?: return
        val inkTool = c.tools.tool.inkTool() ?: return
        val brush = c.tools.brushes.getValue(inkTool)
        // Highlighters stay translucent whatever colour is picked.
        val color = if (inkTool == InkTool.HIGHLIGHTER) (HIGHLIGHTER_ALPHA shl 24) or (colorArgb and 0xFFFFFF) else colorArgb
        updateBrush(inkTool, brush.copy(colorArgb = color))
    }

    fun setSize(size: Float) {
        val c = controller() ?: return
        val inkTool = c.tools.tool.inkTool() ?: return
        updateBrush(inkTool, c.tools.brushes.getValue(inkTool).copy(size = size))
    }

    private fun updateBrush(tool: InkTool, brush: InkBrush) {
        val c = controller() ?: return
        c.tools = c.tools.copy(brushes = c.tools.brushes + (tool to brush))
        refresh()
        viewModelScope.launch { settings.updateInk { it.copy(brushes = it.brushes + (tool to brush)) } }
    }

    fun setEraserMode(mode: EraserMode) {
        val c = controller() ?: return
        c.tools = c.tools.copy(eraserMode = mode)
        refresh()
        viewModelScope.launch { settings.updateInk { it.copy(eraserMode = mode) } }
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
        val template = _ui.value.currentTemplate
        val result = CompletableDeferred<AppResult<InkPageInfo>>()
        enqueue(Op.AddPage(index, template, result))
        viewModelScope.launch {
            when (val r = result.await()) {
                is AppResult.Success -> {
                    val info = r.value
                    s.insertPage(info.index, InkSession.Page(info.id, info.template, info.width, info.height, PageItems.EMPTY))
                    _ui.update { it.copy(scrollRequest = ScrollRequest(info.index)) }
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

    fun rename(title: String) {
        viewModelScope.launch {
            val r = notes.setTitle(noteId, title)
            if (r is AppResult.Failure) _message.value = r.error.toUserMessage()
        }
    }

    /** Moves the note to the trash; returns true when done so the screen can close. */
    suspend fun moveToTrash(): Boolean = notes.moveToTrash(setOf(noteId)) is AppResult.Success

    // ---------------------------------------------------------------- settings

    private fun applySettings(s: InkSettings) {
        val c = controller()
        if (c != null) {
            val tool = if (toolPickedHere) c.tools.tool else s.lastTool
            val next = toolsFrom(s).copy(tool = tool, eraserSizeDp = c.tools.eraserSizeDp)
            if (next != c.tools) c.tools = next
        }
        refresh()
    }

    private fun toolsFrom(s: InkSettings) = InkToolState(
        tool = s.lastTool,
        brushes = s.brushes,
        eraserMode = s.eraserMode,
        shapeCorrection = s.shapeCorrection,
        holdMillis = s.holdDuration.millis,
    )

    private companion object {
        const val TAG = "InkEditor"
        const val MAX_ATTEMPTS = 6
        const val FIRST_RETRY_MS = 500L
        const val MAX_RETRY_MS = 5_000L
        const val HIGHLIGHTER_ALPHA = 0x66
    }
}

internal fun EditorTool.inkTool(): InkTool? = when (this) {
    EditorTool.PEN -> InkTool.PEN
    EditorTool.MARKER -> InkTool.MARKER
    EditorTool.HIGHLIGHTER -> InkTool.HIGHLIGHTER
    EditorTool.ERASER, EditorTool.LASSO -> null
}
