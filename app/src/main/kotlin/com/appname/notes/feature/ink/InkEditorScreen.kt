package com.appname.notes.feature.ink

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.R
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.DeleteOutline
import com.appname.notes.core.designsystem.component.GlassSurface
import com.appname.notes.core.designsystem.component.liquidGlassPill
import com.appname.notes.core.designsystem.component.EmptyState
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import com.appname.notes.core.ink.edit.InkSession
import com.appname.notes.core.ink.geometry.Vec
import com.appname.notes.core.ink.input.EditorTool
import com.appname.notes.core.ink.input.InkInputController
import com.appname.notes.core.ink.model.AudioItem
import com.appname.notes.core.ink.model.PageTemplate
import com.appname.notes.core.ink.model.TextItem
import com.appname.notes.feature.audio.AudioControls
import com.appname.notes.feature.audio.AudioPlayerRow
import com.appname.notes.feature.audio.RecordingBar
import com.appname.notes.feature.editor.ReadOnlyReason
import com.appname.notes.feature.editor.SaveStatusLabel
import com.appname.notes.feature.library.ConfirmDialog
import com.appname.notes.feature.pdf.PdfPageRenderer
import com.appname.notes.feature.reminders.ReminderMenuButton
import com.appname.notes.feature.reminders.rememberReminderUi
import com.appname.notes.ui.common.MessageEffect
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.appname.notes.feature.editor.TagsSheet
import com.appname.notes.feature.pro.FocusTimerChip
import com.appname.notes.feature.pro.FocusTimerDialog
import com.appname.notes.feature.pro.InDocumentFindBar
import com.appname.notes.ui.common.MoveToNotebookSheet
import java.io.File
import kotlinx.coroutines.launch

object InkTestTags {
    const val SCREEN = "ink_screen"
    const val CANVAS = "ink_canvas"
    const val TOOLBAR = "ink_toolbar"
    const val PRESS_MENU = "ink_press_menu"
    const val PRESS_PASTE = "ink_press_paste"
    const val PRESS_WRITE = "ink_press_write"
    const val PRESS_SELECT = "ink_press_select"
    const val PRESS_COPY = "ink_press_copy"
    const val PRESS_DELETE = "ink_press_delete"
    const val PAGE_INDICATOR = "ink_page_indicator"
    const val PAGE_PREV = "ink_page_prev"
    const val PAGE_NEXT = "ink_page_next"
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
    const val SCROLL_BAR = "ink_scroll_bar"
}

@Composable
fun InkEditorRoute(viewModel: InkEditorViewModel, onClose: () -> Unit) {
    val load by viewModel.load.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val notebooks by viewModel.notebooks.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    MessageEffect(message, snackbar, viewModel::onMessageShown)

    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDeletePage by remember { mutableStateOf(false) }
    var showPages by rememberSaveable { mutableStateOf(false) }
    var pageSettings by rememberSaveable { mutableStateOf(false) }
    var showMoveSheet by rememberSaveable { mutableStateOf(false) }
    var showTagsSheet by rememberSaveable { mutableStateOf(false) }
    var showFocusTimer by rememberSaveable { mutableStateOf(false) }
    var findOpen by rememberSaveable { mutableStateOf(false) }
    var findQuery by rememberSaveable { mutableStateOf("") }
    var findMatchIdx by rememberSaveable { mutableIntStateOf(0) }
    // Tool variation panel and colour picker (owner brief §13–14).
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var colorPicker by rememberSaveable { mutableStateOf(false) }
    // Distraction-free full-screen mode is controlled explicitly by the user via the Full screen toggle.
    var fullScreen by rememberSaveable { mutableStateOf(false) }
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
            onShape = viewModel::setShape,
            onFront = viewModel::bringSelectionToFront,
            onBack = viewModel::sendSelectionToBack,
            onRotate = viewModel::rotateSelection,
            onFlip = viewModel::flipSelection,
            onRuler = viewModel::toggleRuler,
            onLassoBox = viewModel::setLassoBox,
            onCopySelection = viewModel::copySelection,
            onRemoveBackground = viewModel::removeBackground,
            onRestoreBackground = viewModel::restoreBackground,
            onClearPage = viewModel::clearCurrentPage,
            onCreateTaskFromSelection = viewModel::createTaskFromNote,
            onConvertSelectionToText = viewModel::convertSelectionToText,
            onToggleLockSelection = viewModel::toggleSelectionLock,
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
    // Photos, camera, PDF pages, text and paste on the page (owner request 8a).
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_INSERT)) { viewModel.insertFiles(it) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.insertFiles(listOf(it)) } }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val path = cameraPath
        cameraPath = null
        if (path != null && taken) viewModel.insertFiles(listOf(android.net.Uri.fromFile(File(path))))
    }
    val hasCamera = remember { context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY) }
    val paste = { viewModel.pasteFrom(clipContent(context)) }
    // Settings › Handwriting › Ask before leaving with Back.
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = ui.backGuard && load is InkLoad.Ready && !confirmLeave) { confirmLeave = true }
    if (confirmLeave) {
        ConfirmDialog(
            title = stringResource(R.string.ink_leave_title),
            body = stringResource(R.string.ink_leave_body),
            confirm = stringResource(R.string.ink_leave),
            onConfirm = { confirmLeave = false; onClose() },
            onDismiss = { confirmLeave = false },
        )
    }
    BackHandler(enabled = fullScreen) { fullScreen = false }
    LaunchedEffect(ui.readOnly) { if (ui.readOnly != null) fullScreen = false }
    val window = LocalActivity.current?.window
    DisposableEffect(fullScreen, window) {
        val bars = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (fullScreen && bars != null) {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { if (fullScreen) bars?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    var stickerPicker by rememberSaveable { mutableStateOf(false) }
    var tablePicker by rememberSaveable { mutableStateOf(false) }
    var equationDialog by rememberSaveable { mutableStateOf(false) }
    if (equationDialog) EquationDialog(onInsert = { viewModel.insertEquation(it); equationDialog = false }, onDismiss = { equationDialog = false })
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.insertFiles(it) }
    val recordPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[android.Manifest.permission.RECORD_AUDIO] == true) viewModel.startRecording() else if (granted.containsKey(android.Manifest.permission.RECORD_AUDIO)) viewModel.microphoneDenied()
    }
    val insertActions = InsertActions(
        photo = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        camera = if (hasCamera) {
            {
                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                val file = File(dir, "Photo ${System.currentTimeMillis()}.jpg")
                runCatching {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    cameraPath = file.path
                    camera.launch(uri)
                }
            }
        } else {
            null
        },
        text = { viewModel.selectTool(EditorTool.TEXT); viewModel.showTextHint() },
        pdf = { pdfPicker.launch(arrayOf("application/pdf")) },
        paste = paste,
        record = {
            val micGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val needsNotify = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            if (micGranted) {
                viewModel.startRecording()
                if (needsNotify) recordPermissions.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
            } else {
                recordPermissions.launch(listOfNotNull(android.Manifest.permission.RECORD_AUDIO, if (needsNotify) android.Manifest.permission.POST_NOTIFICATIONS else null).toTypedArray())
            }
        },
        audioFile = { audioPicker.launch(arrayOf("audio/*")) },
        sticker = { stickerPicker = true },
        table = { tablePicker = true },
        equation = { equationDialog = true },
        dateStamp = viewModel::insertDateStamp,
    )
    if (stickerPicker) StickerDialog(onPick = { viewModel.insertSticker(it); stickerPicker = false }, onDismiss = { stickerPicker = false })
    if (tablePicker) TableDialog(onInsert = { r, c -> viewModel.insertTable(r, c); tablePicker = false }, onDismiss = { tablePicker = false })
    val recordingState by viewModel.recording.collectAsStateWithLifecycle()
    val playbackState by viewModel.playback.state.collectAsStateWithLifecycle()
    val textEdit by viewModel.textEdit.collectAsStateWithLifecycle()
    // Typing on the page: Back finishes the text box (declared last, so it goes first).
    BackHandler(enabled = textEdit != null) { viewModel.commitText() }
    val pressMenu by viewModel.pressMenu.collectAsStateWithLifecycle()
    var canvasView by remember { mutableStateOf<InkCanvasView?>(null) }
    var transformTick by remember { mutableIntStateOf(0) }
    var scrollFraction by remember { mutableFloatStateOf(0f) }
    var zoomPercent by remember { mutableIntStateOf(100) }
    var fitToggleWidth by remember { mutableStateOf(true) }
    SideEffect {
        canvasView?.hiddenItemId = textEdit?.existing?.id
        canvasView?.onTransform = if (textEdit != null) { { transformTick++ } } else null
        canvasView?.onScrollChanged = { frac -> scrollFraction = frac }
        canvasView?.onZoomChanged = { zp -> zoomPercent = zp }
    }
    val inserting by viewModel.inserting.collectAsStateWithLifecycle()
    // Reminders (FR-010).
    val reminders by viewModel.reminders.reminders.collectAsStateWithLifecycle()
    val reminderUi = rememberReminderUi(viewModel.reminders, editable = load is InkLoad.Ready && ui.readOnly == null)

    Scaffold(
        modifier = Modifier.testTag(InkTestTags.SCREEN),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (!fullScreen) InkTopBar(
                ui = ui,
                ready = load is InkLoad.Ready,
                onBack = onClose,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onRename = { renaming = true },
                onAddPage = viewModel::addPage,
                onDuplicatePage = viewModel::duplicateCurrentPage,
                onDeletePage = { confirmDeletePage = true },
                onPageSettings = { pageSettings = true },
                onTrash = { scope.launch { if (viewModel.moveToTrash()) onClose() } },
                onExportPdf = { exportLauncher.launch(viewModel.exportFileName()) },
                onSharePdf = viewModel::sharePdf,
                onGoToPage = { showPages = true },
                onFit = viewModel::fit,
                onAddReminder = { reminderUi.add() },
                onCreateTask = viewModel::createTaskFromNote,
                onTogglePin = viewModel::togglePinned,
                onToggleFavourite = viewModel::toggleFavourite,
                onToggleArchive = viewModel::toggleArchived,
                onOpenTags = { showTagsSheet = true },
                onOpenMove = { showMoveSheet = true },
                onToggleBookmark = viewModel::toggleCurrentPageBookmark,
                onOpenFind = { findOpen = !findOpen },
                onOpenFocusTimer = { showFocusTimer = true },
                reminderButton = { ReminderMenuButton(reminders.orEmpty(), reminderUi, editable = load is InkLoad.Ready && ui.readOnly == null) },
                insert = insertActions,
            )
        },
        bottomBar = {
            if (load is InkLoad.Ready && ui.readOnly == null && !fullScreen) {
                InkToolbar(
                    ui = ui,
                    actions = toolActions,
                    panelOpen = panelOpen,
                    onPanel = { panelOpen = it },
                    modifier = Modifier.navigationBarsPadding(),
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onFullScreen = { panelOpen = false; fullScreen = true },
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
                    InkCanvas(l.session, l.controller, l.pdf, ui, viewModel, onPaste = paste, onView = { canvasView = it })
                    // Find Bar, Recording into this note, and the player for a chip that was tapped.
                    Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 12.dp)) {
                        if (findOpen) {
                            val matchingPages = remember(findQuery, ui.version, ui.pageCount) { viewModel.findMatchingPages(findQuery) }
                            val safeIdx = if (matchingPages.isEmpty()) 0 else findMatchIdx.coerceIn(0, matchingPages.lastIndex)
                            InDocumentFindBar(
                                query = findQuery,
                                onQueryChange = { q ->
                                    findQuery = q
                                    findMatchIdx = 0
                                    viewModel.findMatchingPages(q).firstOrNull()?.let { viewModel.goToPage(it) }
                                },
                                matchCount = matchingPages.size,
                                currentMatchIndex = safeIdx,
                                onPrevious = {
                                    if (matchingPages.isNotEmpty()) {
                                        val prev = (safeIdx - 1 + matchingPages.size) % matchingPages.size
                                        findMatchIdx = prev
                                        viewModel.goToPage(matchingPages[prev])
                                    }
                                },
                                onNext = {
                                    if (matchingPages.isNotEmpty()) {
                                        val next = (safeIdx + 1) % matchingPages.size
                                        findMatchIdx = next
                                        viewModel.goToPage(matchingPages[next])
                                    }
                                },
                                onClose = { findOpen = false; findQuery = "" },
                            )
                        }
                        RecordingBar(recordingState, viewModel.noteId, onAddMarker = {}, onStop = viewModel::stopRecording)
                        val playing = playbackState.attachmentId?.let { viewModel.playingAttachment() }
                        if (playing != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    AudioPlayerRow(
                                        playing,
                                        AudioControls(
                                            playback = playbackState,
                                            play = { viewModel.playback.play(it) },
                                            pause = viewModel.playback::pause,
                                            seek = viewModel.playback::seekTo,
                                            markers = { emptyList() },
                                            playMarker = {},
                                            setSpeed = viewModel.playback::setSpeed,
                                        ),
                                        onMore = null,
                                    )
                                }
                                IconButton(onClick = viewModel.playback::release) { Icon(Icons.Outlined.Close, stringResource(R.string.audio_player_close)) }
                            }
                        }
                    }
                    if (ui.exporting || inserting) {
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
                    if (fullScreen && ui.readOnly == null) {
                        FloatingInkToolbar(
                            ui = ui,
                            actions = toolActions,
                            panelOpen = panelOpen,
                            onPanel = { panelOpen = it },
                            onUndo = viewModel::undo,
                            onRedo = viewModel::redo,
                            onExit = { fullScreen = false },
                            insert = insertActions,
                            onAddPage = viewModel::addPage,
                        )
                    }
                    if (panelOpen && ui.readOnly == null && ui.tools.tool.inkTool != null) {
                        // Over the page, just above the tool bar, so opening it never moves the page.
                        VariationPanel(
                            ui = ui,
                            actions = toolActions,
                            onClose = { panelOpen = false },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (fullScreen) 76.dp else 0.dp).widthIn(max = 560.dp),
                        )
                    }
                    val editing = textEdit
                    val view = canvasView
                    if (editing != null && view != null) {
                        InlineTextEditor(
                            request = editing,
                            view = view,
                            transformTick = transformTick,
                            onChange = viewModel::updateTextDraft,
                            onMove = viewModel::moveTextDraft,
                            onDone = viewModel::commitText,
                            onDelete = viewModel::deleteTextDraft,
                        )
                    }
                    pressMenu?.let { m ->
                        PressAndHoldMenu(
                            menu = m,
                            onPaste = { viewModel.pasteFrom(clipContent(context), m.pageId, Vec(m.x.toDouble(), m.y.toDouble())) },
                            onWriteText = viewModel::menuWriteText,
                            onSelect = viewModel::menuSelect,
                            onCopy = viewModel::menuCopy,
                            onDelete = viewModel::menuDelete,
                            onDismiss = viewModel::dismissPressMenu,
                        )
                    }
                    if (ui.scrollBar && ui.pageCount > 1) {
                        PageScrollBar(
                            current = ui.currentPage,
                            count = ui.pageCount,
                            fraction = scrollFraction,
                            onFraction = { f -> canvasView?.scrollToFraction(f) },
                            onPage = viewModel::goToPage,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp, top = 72.dp, bottom = 72.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Interactive Liquid Glass Zoom Percentage Pill (tap to cycle Fit Width / Fit Page)
                        GlassSurface(
                            shape = CircleShape,
                            elevation = 3.dp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    val mode = if (fitToggleWidth) FitMode.WIDTH else FitMode.PAGE
                                    fitToggleWidth = !fitToggleWidth
                                    viewModel.fit(mode)
                                }
                                .testTag("ink_zoom_pill"),
                        ) {
                            Text(
                                text = "$zoomPercent%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                        if (ui.pageCount > 1) {
                            // Smooth Liquid Glass page navigation capsule + page indicator (SRS 7.1).
                            GlassSurface(
                                shape = CircleShape,
                                elevation = 3.dp,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                ) {
                                    IconButton(
                                        onClick = { if (ui.currentPage > 0) viewModel.goToPage(ui.currentPage - 1) },
                                        enabled = ui.currentPage > 0,
                                        modifier = Modifier.size(32.dp).testTag(InkTestTags.PAGE_PREV),
                                    ) {
                                        Icon(
                                            Icons.Filled.KeyboardArrowUp,
                                            contentDescription = stringResource(R.string.ink_page_prev),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable { showPages = true }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag(InkTestTags.PAGE_INDICATOR),
                                    ) {
                                        Text(
                                            stringResource(R.string.ink_page_indicator, ui.currentPage + 1, ui.pageCount),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    IconButton(
                                        onClick = { if (ui.currentPage + 1 < ui.pageCount) viewModel.goToPage(ui.currentPage + 1) },
                                        enabled = ui.currentPage + 1 < ui.pageCount,
                                        modifier = Modifier.size(32.dp).testTag(InkTestTags.PAGE_NEXT),
                                    ) {
                                        Icon(
                                            Icons.Filled.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.ink_page_next),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
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
            bookmarkedIds = ui.bookmarkedPages,
            onPick = { viewModel.goToPage(it); showPages = false },
            onDismiss = { showPages = false },
        )
    }
    if (showMoveSheet) {
        MoveToNotebookSheet(
            notebooks = notebooks,
            current = ui.notebookId,
            onPick = viewModel::moveToNotebook,
            onDismiss = { showMoveSheet = false },
        )
    }
    if (showTagsSheet) {
        TagsSheet(
            allTags = allTags,
            attached = ui.tags.map { it.id }.toSet(),
            onToggle = viewModel::setTag,
            onCreate = viewModel::createAndAttachTag,
            onDismiss = { showTagsSheet = false },
        )
    }
    if (showFocusTimer) {
        FocusTimerDialog(onDismiss = { showFocusTimer = false })
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
        val shapeColor = ui.tools.tool == EditorTool.SHAPE
        val current = when {
            laserColor -> ui.laser.colorArgb
            shapeColor -> ui.tools.shapeColor
            else -> ui.tools.tool.inkTool?.let { ui.tools.brushes[it]?.colorArgb } ?: 0xFF000000.toInt()
        }
        ColorPickerDialog(
            initial = current,
            recent = ui.recentColors,
            saved = ui.savedColors,
            onPick = { c ->
                when {
                    laserColor -> viewModel.setLaser(ui.laser.copy(colorArgb = c, rainbow = false))
                    shapeColor -> viewModel.setShape(ui.shapeSettings.withColor(c))
                    else -> viewModel.setColor(c)
                }
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
    onPaste: () -> Unit = {},
    onView: (InkCanvasView) -> Unit = {},
) {
    val isDark = LocalDarkTheme.current
    val background = if (isDark) DarkPaper.WORKSPACE_DARK else DarkPaper.WORKSPACE_LIGHT
    // Dark paper only in dark theme, and only if the user wants it (Settings / page settings).
    val darkPaper = ui.darkPaper && isDark
    val description = stringResource(R.string.ink_canvas_description, ui.currentPage + 1, ui.pageCount.coerceAtLeast(1))
    // Drag and drop from other apps (gallery, browser, files — side by side or on a tablet): photos, PDFs, text.
    var canvasView by remember { mutableStateOf<InkCanvasView?>(null) }
    var canvasOrigin by remember { mutableStateOf(Offset.Zero) }
    val activity = LocalActivity.current
    val dropTarget = remember(viewModel) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val e = event.toAndroidDragEvent()
                activity?.requestDragAndDropPermissions(e)
                val clip = clipContent(activity ?: return false, e.clipData)
                val at = canvasView?.pagePointAt(e.x - canvasOrigin.x, e.y - canvasOrigin.y)
                when {
                    clip.uris.isNotEmpty() -> viewModel.insertFiles(clip.uris, at?.first, at?.second)
                    clip.text != null -> viewModel.insertPlainText(clip.text, at?.first, at?.second)
                    else -> return false
                }
                return true
            }
        }
    }
    AndroidView(
        factory = { context ->
            InkCanvasView(context).also { canvasView = it; onView(it) }.apply {
                viewState = viewModel.viewState
                listener = object : InkCanvasView.Listener {
                    override fun onInteraction() = viewModel.refresh()
                    override fun onStylusDetected() = viewModel.onStylusDetected()
                    override fun onPageChanged(index: Int) = viewModel.onPageChanged(index)
                    override fun onUndoRequested() = viewModel.undo()
                    override fun onRedoRequested() = viewModel.redo()
                    override fun onTextTap(pageId: String, x: Double, y: Double, existing: TextItem?) =
                        viewModel.onTextTap(pageId, x, y, existing)
                    override fun onPasteRequested() = onPaste()
                    override fun onAudioTap(item: AudioItem) = viewModel.onAudioTap(item)
                    override fun onLongPress(pageId: String, p: Vec, x: Float, y: Float, unitsPerDp: Double) =
                        viewModel.onLongPress(pageId, p, x, y, unitsPerDp)
                    override fun onDrawStart() = viewModel.onDrawStart()
                }
            }
        },
        onRelease = { view ->
            viewModel.viewState = view.viewState
            view.listener = null
            view.onTransform = null
            view.onScrollChanged = null
            view.onZoomChanged = null
            canvasView = null
        },
        update = { view ->
            view.fingerDraws = ui.fingerDraws
            view.backgroundArgb = background
            view.contentDescription = description
            view.pdf = pdf
            view.arrangement = ui.arrangement
            view.darkPaper = darkPaper
            view.laser = ui.laser
            view.rulerVisible = ui.ruler && ui.readOnly == null
            view.bind(session, controller)
            ui.scrollRequest?.let {
                view.scrollToPage(it.page, animate = true)
                viewModel.consumeScrollRequest()
            }
            ui.fitRequest?.let {
                view.fit(it.mode)
                viewModel.consumeFitRequest()
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { canvasOrigin = it.positionInRoot() }
            .dragAndDropTarget(
                shouldStartDragAndDrop = { ev -> ui.readOnly == null && acceptsDrop(ev.toAndroidDragEvent().clipDescription) },
                target = dropTarget,
            )
            .testTag(InkTestTags.CANVAS),
    )
}

/**
 * Minimal, distraction-free top control bar inspired by the dark-mode reference editor:
 * two compact floating pill clusters (left navigation/title pill and right actions pill) with subtle borders.
 */
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
    onDuplicatePage: () -> Unit = {},
    onDeletePage: () -> Unit,
    onPageSettings: () -> Unit,
    onTrash: () -> Unit,
    onExportPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onGoToPage: () -> Unit,
    onFit: (FitMode) -> Unit,
    onAddReminder: () -> Unit = {},
    onCreateTask: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onToggleFavourite: () -> Unit = {},
    onToggleArchive: () -> Unit = {},
    onOpenTags: () -> Unit = {},
    onOpenMove: () -> Unit = {},
    onToggleBookmark: () -> Unit = {},
    onOpenFind: () -> Unit = {},
    onOpenFocusTimer: () -> Unit = {},
    reminderButton: @Composable () -> Unit = {},
    insert: InsertActions? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val editable = ready && ui.readOnly == null
    val isDark = LocalDarkTheme.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(if (isDark) DarkPaper.WORKSPACE_DARK else DarkPaper.WORKSPACE_LIGHT))
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Unified Liquid Glass Top Bar: guarantees generous width for the Note Title on the left
        // and only essential minimal actions (Insert, Add Page, More ⋮) on the right.
        GlassSurface(
            shape = RoundedCornerShape(26.dp),
            tint = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
            elevation = 4.dp,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }

                // Title & Save status gets Modifier.weight(1f) so it never squishes or wraps vertically
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = editable, onClick = onRename)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = ui.title.ifBlank { stringResource(R.string.ink_untitled) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (editable) SaveStatusLabel(ui.saveStatus)
                }

                // Minimal Right Action Cluster: Insert (+), Add Page, Active Reminder indicator, and More (⋮)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    if (editable && insert != null) {
                        InsertButton(insert)
                    }
                    if (editable) {
                        IconButton(
                            onClick = onAddPage,
                            modifier = Modifier.size(40.dp).testTag("ink_add_page_direct"),
                        ) {
                            Icon(
                                Icons.Outlined.NoteAdd,
                                stringResource(if (ui.isPdf) R.string.pdf_add_blank_page else R.string.ink_page_add),
                            )
                        }
                    }
                    reminderButton()
                    if (ready) {
                        Box {
                            IconButton(
                                onClick = { menu = true },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Filled.MoreVert, stringResource(R.string.ink_more_options))
                            }
                            DropdownMenu(
                                expanded = menu,
                                onDismissRequest = { menu = false },
                                shape = RoundedCornerShape(22.dp),
                                containerColor = if (isDark) Color(0xFF1A202C) else Color(0xFFF8FAFC),
                                tonalElevation = 4.dp,
                                shadowElevation = 10.dp,
                                modifier = Modifier.width(276.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    // 1. Top Quick-Action Icon Strip (Find, Pin, Favourite, Bookmark, Timer, Paste)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        IconButton(
                                            onClick = { menu = false; onOpenFind() },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(Icons.Outlined.Search, stringResource(R.string.find_in_note), modifier = Modifier.size(19.dp))
                                        }
                                        if (editable) {
                                            IconButton(
                                                onClick = { menu = false; onTogglePin() },
                                                modifier = Modifier.size(36.dp),
                                            ) {
                                                Icon(
                                                    if (ui.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                    stringResource(if (ui.isPinned) R.string.note_unpin else R.string.note_pin),
                                                    tint = if (ui.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(19.dp),
                                                )
                                            }
                                            IconButton(
                                                onClick = { menu = false; onToggleFavourite() },
                                                modifier = Modifier.size(36.dp),
                                            ) {
                                                Icon(
                                                    if (ui.isFavourite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                                    stringResource(if (ui.isFavourite) R.string.note_unfavourite else R.string.note_favourite),
                                                    tint = if (ui.isFavourite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(19.dp),
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { menu = false; onToggleBookmark() },
                                            modifier = Modifier.size(36.dp).testTag("ink_bookmark_page_btn"),
                                        ) {
                                            Icon(
                                                if (ui.currentPageBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                                stringResource(if (ui.currentPageBookmarked) R.string.ink_unbookmark_page else R.string.ink_bookmark_page),
                                                tint = if (ui.currentPageBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(19.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = { menu = false; onOpenFocusTimer() },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(Icons.Outlined.Schedule, stringResource(R.string.focus_timer_title), modifier = Modifier.size(19.dp))
                                        }
                                        if (editable && insert != null) {
                                            IconButton(
                                                onClick = { menu = false; insert.paste() },
                                                modifier = Modifier.size(36.dp).testTag("ink_paste_direct"),
                                            ) {
                                                Icon(Icons.Outlined.ContentPaste, stringResource(R.string.insert_paste), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }

                                    // 2. Compact 2-Column Page Actions (Add Page, Duplicate, Delete, Page Settings)
                                    if (editable) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            CompactMenuTile(
                                                label = stringResource(if (ui.isPdf) R.string.pdf_add_blank_page else R.string.ink_page_add),
                                                onClick = { menu = false; onAddPage() },
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (!ui.shownPageFromPdf) {
                                                CompactMenuTile(
                                                    label = stringResource(R.string.ink_page_duplicate),
                                                    onClick = { menu = false; onDuplicatePage() },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            CompactMenuTile(
                                                label = stringResource(R.string.page_settings_title),
                                                onClick = { menu = false; onPageSettings() },
                                                modifier = Modifier.weight(1f).testTag(InkTestTags.PAGE_SETTINGS),
                                            )
                                            if (ui.pageCount > 1 && !ui.shownPageFromPdf) {
                                                CompactMenuTile(
                                                    label = stringResource(R.string.ink_page_delete),
                                                    onClick = { menu = false; onDeletePage() },
                                                    modifier = Modifier.weight(1f),
                                                    destructive = true,
                                                )
                                            }
                                        }
                                    }

                                    // 3. Compact View & Zoom Row (Fit Width, Fit Page, Go to Page)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        CompactMenuTile(
                                            label = stringResource(R.string.ink_fit_width),
                                            onClick = { menu = false; onFit(FitMode.WIDTH) },
                                            modifier = Modifier.weight(1f),
                                        )
                                        CompactMenuTile(
                                            label = stringResource(R.string.ink_fit_page),
                                            onClick = { menu = false; onFit(FitMode.PAGE) },
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (ui.pageCount > 1) {
                                            CompactMenuTile(
                                                label = stringResource(R.string.ink_go_to_page),
                                                onClick = { menu = false; onGoToPage() },
                                                modifier = Modifier.weight(1f).testTag(InkTestTags.GO_TO_PAGE),
                                            )
                                        }
                                    }

                                    // 4. Compact Organize & Reminders (Tags, Move, Remind me, To-do)
                                    if (editable) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            CompactMenuTile(
                                                label = stringResource(R.string.note_tags),
                                                onClick = { menu = false; onOpenTags() },
                                                modifier = Modifier.weight(1f),
                                            )
                                            CompactMenuTile(
                                                label = stringResource(R.string.note_move),
                                                onClick = { menu = false; onOpenMove() },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            CompactMenuTile(
                                                label = stringResource(R.string.reminder_add),
                                                onClick = { menu = false; onAddReminder() },
                                                modifier = Modifier.weight(1f),
                                            )
                                            CompactMenuTile(
                                                label = stringResource(R.string.todo_create_from_note),
                                                onClick = { menu = false; onCreateTask() },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }

                                    // 5. Compact PDF Export & Share Row
                                    val pdfReady = !ui.exporting && !ui.pdfFailed
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        CompactMenuTile(
                                            label = stringResource(if (ui.isPdf) R.string.pdf_export else R.string.ink_export_pdf),
                                            enabled = pdfReady,
                                            onClick = { menu = false; onExportPdf() },
                                            modifier = Modifier.weight(1f).testTag(InkTestTags.EXPORT_PDF),
                                        )
                                        CompactMenuTile(
                                            label = stringResource(if (ui.isPdf) R.string.pdf_share else R.string.ink_share_pdf),
                                            enabled = pdfReady,
                                            onClick = { menu = false; onSharePdf() },
                                            modifier = Modifier.weight(1f).testTag(InkTestTags.SHARE_PDF),
                                        )
                                    }

                                    // 6. Compact Footer Row (Rename, Archive, Trash)
                                    if (editable || ui.readOnly != ReadOnlyReason.IN_TRASH) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            if (editable) {
                                                CompactMenuTile(
                                                    label = stringResource(R.string.action_rename),
                                                    onClick = { menu = false; onRename() },
                                                    modifier = Modifier.weight(1f),
                                                )
                                                CompactMenuTile(
                                                    label = stringResource(if (ui.isArchived) R.string.note_unarchive else R.string.note_archive),
                                                    onClick = { menu = false; onToggleArchive() },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                            if (ui.readOnly != ReadOnlyReason.IN_TRASH) {
                                                CompactMenuTile(
                                                    label = stringResource(R.string.note_trash),
                                                    onClick = { menu = false; onTrash() },
                                                    modifier = Modifier.weight(1f),
                                                    destructive = true,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMenuTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val isDark = LocalDarkTheme.current
    val textColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(12.dp))
            .liquidGlassPill(
                selected = false,
                dark = isDark,
                accentColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                cornerRadius = 12.dp,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
private fun PagesDialog(
    pages: List<InkSession.Page>,
    current: Int,
    bookmarkedIds: Set<String> = emptySet(),
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var bookmarksOnly by rememberSaveable { mutableStateOf(false) }
    val indexedPages = remember(pages, bookmarkedIds, bookmarksOnly) {
        pages.mapIndexed { idx, p -> idx to p }.filter { (_, p) -> !bookmarksOnly || p.id in bookmarkedIds }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ink_pages_title, pages.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (bookmarkedIds.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
                            .padding(3.dp)
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(
                            false to stringResource(R.string.ink_all_pages_tab, pages.size),
                            true to stringResource(R.string.ink_bookmarks_only, pages.count { it.id in bookmarkedIds }),
                        ).forEach { (only, label) ->
                            val active = bookmarksOnly == only
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 32.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .selectable(selected = active, role = Role.Tab, onClick = { bookmarksOnly = only })
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(76.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 420.dp).testTag(InkTestTags.PAGES_DIALOG),
                ) {
                    itemsIndexed(indexedPages, key = { _, pair -> pair.second.id }) { _, (i, page) ->
                        val selected = i == current
                        val hasInk = page.items.items.isNotEmpty()
                        val isBookmarked = page.id in bookmarkedIds
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
                                Box(Modifier.fillMaxSize()) {
                                    if (isBookmarked) {
                                        Icon(
                                            Icons.Filled.Bookmark,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(15.dp),
                                        )
                                    }
                                    if (hasInk) {
                                        // Not by colour alone: the label says "has handwriting" too.
                                        Icon(
                                            InkIcons.Handwriting,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(14.dp),
                                        )
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
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_INSERT = 10

/**
 * Continuous vertical page scroll bar on the right edge: dragging or tapping scrolls smoothly and continuously
 * through the document without stepwise page jumps, while TalkBack can also step page-by-page.
 */
@Composable
private fun PageScrollBar(
    current: Int,
    count: Int,
    fraction: Float,
    onFraction: (Float) -> Unit,
    onPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.ink_scroll_bar)
    val pageText = stringResource(R.string.ink_page_indicator, current + 1, count)
    var trackHeight by remember { mutableIntStateOf(1) }
    val displayFraction = if (count <= 1) 0f else fraction.coerceIn(0f, 1f)
    fun fractionAt(y: Float) = (y / trackHeight.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxHeight()
            .width(24.dp)
            .androidx_onSize { trackHeight = it }
            .pointerInput(count) {
                detectTapGestures { offset -> onFraction(fractionAt(offset.y)) }
            }
            .pointerInput(count) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    onFraction(fractionAt(change.position.y))
                }
            }
            .semantics {
                contentDescription = label
                stateDescription = pageText
                progressBarRangeInfo = ProgressBarRangeInfo(current.toFloat(), 0f..(count - 1).toFloat(), steps = (count - 2).coerceAtLeast(0))
                setProgress { v -> onPage(v.toInt().coerceIn(0, count - 1)); true }
            }
            .testTag(InkTestTags.SCROLL_BAR),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.fillMaxHeight().width(2.5.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), CircleShape))
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val p = measurable.measure(constraints)
                    layout(p.width, p.height) { p.place(0, ((trackHeight - p.height) * displayFraction).toInt()) }
                }
                .size(width = 8.dp, height = 38.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), CircleShape),
        )
    }
}

private fun Modifier.androidx_onSize(onHeight: (Int) -> Unit) = this.then(Modifier.onSizeChanged { onHeight(it.height) })

/** Press and hold on the page (owner request): paste, write text, select, copy, delete — what fits the spot. */
@Composable
private fun PressAndHoldMenu(
    menu: PressMenu,
    onPaste: () -> Unit,
    onWriteText: () -> Unit,
    onSelect: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isText = menu.item is TextItem
    val onSomething = menu.item != null || menu.inSelection
    Box(Modifier.offset { IntOffset(menu.screenX.toInt(), menu.screenY.toInt()) }.size(1.dp)) {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss, modifier = Modifier.testTag(InkTestTags.PRESS_MENU)) {
            if (menu.editable) {
                DropdownMenuItem(text = { Text(stringResource(R.string.ink_menu_paste)) }, onClick = onPaste, modifier = Modifier.testTag(InkTestTags.PRESS_PASTE))
                if (isText || !onSomething) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (isText) R.string.ink_menu_edit_text else R.string.ink_menu_write_text)) },
                        onClick = onWriteText,
                        modifier = Modifier.testTag(InkTestTags.PRESS_WRITE),
                    )
                }
            }
            if (!menu.inSelection) {
                DropdownMenuItem(text = { Text(stringResource(R.string.ink_menu_select)) }, onClick = onSelect, modifier = Modifier.testTag(InkTestTags.PRESS_SELECT))
            }
            if (onSomething) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (isText) R.string.ink_menu_copy_text else R.string.ink_menu_copy)) },
                    onClick = onCopy,
                    modifier = Modifier.testTag(InkTestTags.PRESS_COPY),
                )
                if (menu.editable) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.ink_menu_delete)) }, onClick = onDelete, modifier = Modifier.testTag(InkTestTags.PRESS_DELETE))
                }
            }
        }
    }
}
