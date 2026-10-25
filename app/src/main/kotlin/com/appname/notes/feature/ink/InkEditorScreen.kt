package com.appname.notes.feature.ink

import com.appname.notes.feature.reminders.ReminderMenuButton
import com.appname.notes.feature.reminders.rememberReminderUi
import android.content.ClipData
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.appname.notes.feature.pdf.PdfPageRenderer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.EmptyState
import com.appname.notes.core.ink.edit.InkSession
import com.appname.notes.core.ink.input.EditorTool
import com.appname.notes.core.ink.input.EraserMode
import com.appname.notes.core.ink.input.InkInputController
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.feature.editor.ReadOnlyReason
import com.appname.notes.feature.editor.SaveStatusLabel
import com.appname.notes.feature.library.ConfirmDialog
import com.appname.notes.ui.common.MessageEffect
import kotlinx.coroutines.launch

object InkTestTags {
    const val SCREEN = "ink_screen"
    const val CANVAS = "ink_canvas"
    const val TOOLBAR = "ink_toolbar"
    const val PAGE_INDICATOR = "ink_page_indicator"
    const val EXPORT_PDF = "ink_export_pdf"
    const val SHARE_PDF = "ink_share_pdf"
    const val GO_TO_PAGE = "ink_go_to_page"
    const val PAGES_DIALOG = "ink_pages_dialog"
    fun pageCell(index: Int) = "ink_page_cell_$index"
    const val EXPORT_PROGRESS = "ink_export_progress"
    const val PDF_FAILED = "ink_pdf_failed"
    const val PAGE_SETTINGS = "ink_page_settings"
    const val EXPORT_NOTE_PDF = "ink_export_note_pdf"
    fun tool(tool: EditorTool) = "ink_tool_${tool.name.lowercase()}"
}

@Composable
fun InkEditorRoute(viewModel: InkEditorViewModel, onClose: () -> Unit) {
    val load by viewModel.load.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    MessageEffect(message, snackbar, viewModel::onMessageShown)

    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDeletePage by remember { mutableStateOf(false) }
    var showPages by rememberSaveable { mutableStateOf(false) }
    var pageSettings by rememberSaveable { mutableStateOf(false) }
    // Tool variation panel and colour picker (owner brief §13–14).
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var colorPicker by rememberSaveable { mutableStateOf(false) }
    val toolActions = remember(viewModel) {
        InkToolActions(
            onTool = { tool -> viewModel.selectTool(tool); panelOpen = false },
            onStyle = viewModel::setStyle,
            onColor = viewModel::setColor,
            onSize = viewModel::setSize,
            onOpacity = viewModel::setOpacity,
            onPressure = viewModel::setPressure,
            onRecent = viewModel::applyRecent,
            onMoreColors = { colorPicker = true },
            onEraserMode = viewModel::setEraserMode,
            onEraserSize = viewModel::setEraserSize,
            onDeleteSelection = viewModel::deleteSelection,
            onDuplicateSelection = viewModel::duplicateSelection,
            onRecolorSelection = viewModel::recolorSelection,
            onDoneSelection = viewModel::clearSelection,
            onLaser = viewModel::setLaser,
        )
    }
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.pdf_share_title)
    LaunchedEffect(ui.shareRequest) {
        val request = ui.shareRequest ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, request.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, request.uri) // carries the read grant through the chooser
        context.startActivity(Intent.createChooser(send, shareTitle))
        viewModel.consumeShareRequest()
    }
    // "Export annotated PDF": the system "save as" screen picks where the copy goes (no storage permission needed).
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) viewModel.exportPdf(uri)
    }
    // Reminders (FR-010).
    val reminders by viewModel.reminders.reminders.collectAsStateWithLifecycle()
    val reminderUi = rememberReminderUi(viewModel.reminders, editable = load is InkLoad.Ready && ui.readOnly == null)

    Scaffold(
        modifier = Modifier.testTag(InkTestTags.SCREEN),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            InkTopBar(
                ui = ui,
                ready = load is InkLoad.Ready,
                onBack = onClose,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onRename = { renaming = true },
                onAddPage = viewModel::addPage,
                onDeletePage = { confirmDeletePage = true },
                onPageSettings = { pageSettings = true },
                onTrash = { scope.launch { if (viewModel.moveToTrash()) onClose() } },
                onExportPdf = { exportLauncher.launch(viewModel.exportFileName()) },
                onSharePdf = viewModel::sharePdf,
                onGoToPage = { showPages = true },
                onFit = viewModel::fit,
                onAddReminder = { reminderUi.add() },
                reminderButton = { ReminderMenuButton(reminders.orEmpty(), reminderUi, editable = load is InkLoad.Ready && ui.readOnly == null) },
            )
        },
        bottomBar = {
            if (load is InkLoad.Ready && ui.readOnly == null) {
                InkToolbar(
                    ui = ui,
                    actions = toolActions,
                    panelOpen = panelOpen,
                    onPanel = { panelOpen = it },
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val l = load) {
                InkLoad.Loading -> Unit
                InkLoad.NotFound -> EmptyState(
                    title = stringResource(R.string.editor_not_found),
                    body = "",
                    modifier = Modifier.align(Alignment.Center),
                )
                is InkLoad.Ready -> {
                    InkCanvas(l.session, l.controller, l.pdf, ui, viewModel)
                    if (ui.exporting) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter).testTag(InkTestTags.EXPORT_PROGRESS))
                    }
                    if (ui.pdfFailed) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).testTag(InkTestTags.PDF_FAILED),
                        ) {
                            Text(
                                stringResource(R.string.pdf_cant_display),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    ui.readOnly?.let { reason ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                        ) {
                            Text(
                                stringResource(if (reason == ReadOnlyReason.IN_TRASH) R.string.editor_in_trash else R.string.editor_read_only_newer),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    if (panelOpen && ui.readOnly == null && ui.tools.tool.inkTool != null) {
                        // Over the page, just above the tool bar, so opening it never moves the page.
                        VariationPanel(
                            ui = ui,
                            actions = toolActions,
                            onClose = { panelOpen = false },
                            modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp),
                        )
                    }
                    if (ui.pageCount > 1) {
                        // Page count (SRS 7.1); tapping it opens the page grid to jump to a page.
                        Surface(
                            onClick = { showPages = true },
                            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                            shape = CircleShape,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).testTag(InkTestTags.PAGE_INDICATOR),
                        ) {
                            Text(
                                stringResource(R.string.ink_page_indicator, ui.currentPage + 1, ui.pageCount),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    val ready = load as? InkLoad.Ready
    if (showPages && ready != null) {
        PagesDialog(
            pages = ready.session.pages,
            current = ui.currentPage,
            onPick = { viewModel.goToPage(it); showPages = false },
            onDismiss = { showPages = false },
        )
    }
    if (renaming) {
        RenameDialog(
            initial = ui.title,
            onConfirm = { viewModel.rename(it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDeletePage) {
        ConfirmDialog(
            title = stringResource(R.string.ink_delete_page_title),
            body = stringResource(R.string.ink_delete_page_body),
            confirm = stringResource(R.string.action_delete),
            onConfirm = { viewModel.deleteCurrentPage(); confirmDeletePage = false },
            onDismiss = { confirmDeletePage = false },
        )
    }
    if (colorPicker && ready != null) {
        val laserColor = ui.tools.tool == EditorTool.LASER
        val current = if (laserColor) ui.laser.colorArgb else ui.tools.tool.inkTool?.let { ui.tools.brushes[it]?.colorArgb } ?: 0xFF000000.toInt()
        ColorPickerDialog(
            initial = current,
            recent = ui.recentColors,
            saved = ui.savedColors,
            onPick = { c ->
                if (laserColor) viewModel.setLaser(ui.laser.copy(colorArgb = c, rainbow = false)) else viewModel.setColor(c)
                colorPicker = false
            },
            onSave = viewModel::saveColor,
            onRemoveSaved = viewModel::removeSavedColor,
            onDismiss = { colorPicker = false },
        )
    }
    if (pageSettings && ready != null) {
        PageSettingsDialog(
            arrangement = ui.arrangement,
            format = ui.pageFormat,
            design = ui.pageDesign,
            darkPaper = ui.darkPaper,
            isPdf = ui.isPdf,
            actions = PageSettingsActions(
                onArrangement = viewModel::setArrangement,
                onFormat = viewModel::setPageFormat,
                onBackground = viewModel::setBackground,
                onPaperColor = viewModel::setPaperColor,
                onDarkPaper = viewModel::setDarkPaper,
            ),
            onDismiss = { pageSettings = false },
        )
    }
}

@Composable
private fun InkCanvas(
    session: InkSession,
    controller: InkInputController,
    pdf: PdfPageRenderer?,
    ui: InkEditorUi,
    viewModel: InkEditorViewModel,
) {
    val background = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
    // Dark paper only in dark theme, and only if the user wants it (Settings / page settings).
    val darkPaper = ui.darkPaper && com.appname.notes.core.designsystem.theme.LocalDarkTheme.current
    val description = stringResource(R.string.ink_canvas_description, ui.currentPage + 1, ui.pageCount.coerceAtLeast(1))
    AndroidView(
        factory = { context ->
            InkCanvasView(context).apply {
                viewState = viewModel.viewState
                listener = object : InkCanvasView.Listener {
                    override fun onInteraction() = viewModel.refresh()
                    override fun onStylusDetected() = viewModel.onStylusDetected()
                    override fun onPageChanged(index: Int) = viewModel.onPageChanged(index)
                    override fun onUndoRequested() = viewModel.undo()
                    override fun onRedoRequested() = viewModel.redo()
                }
            }
        },
        onRelease = { view -> viewModel.viewState = view.viewState },
        update = { view ->
            view.fingerDraws = ui.fingerDraws
            view.backgroundArgb = background
            view.contentDescription = description
            view.pdf = pdf
            view.arrangement = ui.arrangement
            view.darkPaper = darkPaper
            view.laser = ui.laser
            view.bind(session, controller)
            ui.scrollRequest?.let {
                view.scrollToPage(it.page)
                viewModel.consumeScrollRequest()
            }
            ui.fitRequest?.let {
                view.fit(it.mode)
                viewModel.consumeFitRequest()
            }
        },
        modifier = Modifier.fillMaxSize().testTag(InkTestTags.CANVAS),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InkTopBar(
    ui: InkEditorUi,
    ready: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRename: () -> Unit,
    onAddPage: () -> Unit,
    onDeletePage: () -> Unit,
    onPageSettings: () -> Unit,
    onTrash: () -> Unit,
    onExportPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onGoToPage: () -> Unit,
    onFit: (FitMode) -> Unit,
    onAddReminder: () -> Unit = {},
    reminderButton: @Composable () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    val editable = ready && ui.readOnly == null
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
        },
        title = {
            Column(Modifier.clickable(enabled = editable, onClick = onRename)) {
                Text(
                    ui.title.ifBlank { stringResource(R.string.ink_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                if (editable) SaveStatusLabel(ui.saveStatus)
            }
        },
        actions = {
            if (editable) {
                IconButton(onClick = onUndo, enabled = ui.canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.editor_undo)) }
                IconButton(onClick = onRedo, enabled = ui.canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.editor_redo)) }
            }
            reminderButton()
            if (ready) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.ink_more_options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (editable) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menu = false; onRename() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.reminder_add)) }, onClick = { menu = false; onAddReminder() })
                        }
                        // Viewing (works read-only too): page navigation and zoom presets (SRS 7.1).
                        if (ui.pageCount > 1) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ink_go_to_page)) },
                                onClick = { menu = false; onGoToPage() },
                                modifier = Modifier.testTag(InkTestTags.GO_TO_PAGE),
                            )
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.ink_fit_width)) }, onClick = { menu = false; onFit(FitMode.WIDTH) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.ink_fit_page)) }, onClick = { menu = false; onFit(FitMode.PAGE) })
                        HorizontalDivider()
                        // Exporting only reads the note, so it works for read-only notes too. A PDF note exports an
                        // annotated copy of the document; a handwritten note becomes a PDF with its pages as set.
                        run {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (ui.isPdf) R.string.pdf_export else R.string.ink_export_pdf)) },
                                enabled = !ui.exporting && !ui.pdfFailed,
                                onClick = { menu = false; onExportPdf() },
                                modifier = Modifier.testTag(InkTestTags.EXPORT_PDF),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(if (ui.isPdf) R.string.pdf_share else R.string.ink_share_pdf)) },
                                enabled = !ui.exporting && !ui.pdfFailed,
                                onClick = { menu = false; onSharePdf() },
                                modifier = Modifier.testTag(InkTestTags.SHARE_PDF),
                            )
                        }
                        if (editable && !ui.isPdf) {
                            // A PDF's pages come from the document, so they can't be added, deleted or re-templated.
                            DropdownMenuItem(text = { Text(stringResource(R.string.ink_page_add)) }, onClick = { menu = false; onAddPage() })
                            if (ui.pageCount > 1) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.ink_page_delete)) }, onClick = { menu = false; onDeletePage() })
                            }
                        }
                        if (editable) {
                            // Layout and scroll direction apply to PDF notes too; size and paper only to handwriting.
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.page_settings_title)) },
                                onClick = { menu = false; onPageSettings() },
                                modifier = Modifier.testTag(InkTestTags.PAGE_SETTINGS),
                            )
                        }
                        if (editable || ui.isPdf) HorizontalDivider()
                        if (ui.readOnly != ReadOnlyReason.IN_TRASH) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.note_trash)) }, onClick = { menu = false; onTrash() })
                        }
                    }
                }
            }
        },
    )
}

// ---------------------------------------------------------------- dialogs

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.editor_title_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_rename)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@StringRes
internal fun PageTemplate.label(): Int = when (this) {
    PageTemplate.BLANK -> R.string.ink_template_blank
    PageTemplate.LINED -> R.string.ink_template_lined
    PageTemplate.GRID -> R.string.ink_template_grid
    PageTemplate.DOTTED -> R.string.ink_template_dotted
}

/** Page navigation (SRS 7.1 "page rail"): every page at its shape, the current one marked, tap to go there. */
@Composable
private fun PagesDialog(pages: List<InkSession.Page>, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ink_pages_title, pages.size)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(76.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 420.dp).testTag(InkTestTags.PAGES_DIALOG),
            ) {
                itemsIndexed(pages, key = { _, p -> p.id }) { i, page ->
                    val selected = i == current
                    val hasInk = page.items.items.isNotEmpty()
                    val label = stringResource(if (hasInk) R.string.ink_page_cell_ink else R.string.ink_page_cell, i + 1)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = { onPick(i) },
                            shape = MaterialTheme.shapes.extraSmall,
                            color = Color(InkPaints.PAPER),
                            border = BorderStroke(
                                if (selected) 3.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio((page.width / page.height).coerceIn(0.4f, 2.5f))
                                .testTag(InkTestTags.pageCell(i))
                                .semantics {
                                    contentDescription = label
                                    this.selected = selected
                                },
                        ) {
                            if (hasInk) {
                                // Not by colour alone: the label says "has handwriting" too.
                                Box(Modifier.padding(6.dp), contentAlignment = Alignment.TopEnd) {
                                    Icon(InkIcons.Handwriting, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp).clearAndSetSemantics { },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
