package com.appname.notes.feature.editor

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.EmptyState
import com.appname.notes.core.designsystem.theme.AppTheme
import com.appname.notes.core.domain.markdown.ExportResult
import com.appname.notes.core.domain.markdown.LostFeature
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.feature.library.ConfirmDialog
import com.appname.notes.ui.common.MessageEffect
import com.appname.notes.ui.common.MoveToNotebookSheet
import kotlinx.coroutines.launch

object EditorTestTags {
    const val SCREEN = "screen_editor"
    const val TITLE = "editor_title"
    const val BODY = "editor_body"
    const val STATUS = "editor_status"
    const val TOOLBAR = "editor_toolbar"
}

@Composable
fun NoteEditorRoute(
    viewModel: NoteEditorViewModel,
    onClose: () -> Unit,
    onOpenNote: (NoteId) -> Unit,
    showBack: Boolean = true,
) {
    val load by viewModel.load.collectAsStateWithLifecycle()
    val readOnly by viewModel.readOnly.collectAsStateWithLifecycle()
    val saveStatus by viewModel.saveStatus.collectAsStateWithLifecycle()
    val meta by viewModel.meta.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val notebooks by viewModel.notebooks.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(message, snackbar, viewModel::messageShown)

    // Leaving the app (home button, app switcher, screen off): save now — the process may be killed next.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }

    var showMove by remember { mutableStateOf(false) }
    var showTags by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ExportResult?>(null) }
    var exportWarning by remember { mutableStateOf<ExportResult?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val export = pendingExport
        if (uri != null && export != null) viewModel.writeExport(uri, export.text)
        pendingExport = null
    }

    fun startExport(export: ExportResult) {
        pendingExport = export
        exportLauncher.launch(viewModel.suggestedFileName())
    }

    val actions = EditorMenuActions(
        onPin = { meta?.let { viewModel.setPinned(!it.isPinned) } },
        onFavourite = { meta?.let { viewModel.setFavourite(!it.isFavourite) } },
        onArchive = { meta?.let { viewModel.setArchived(!it.isArchived) } },
        onTrash = { scope.launch { if (viewModel.moveToTrash()) onClose() } },
        onRestore = viewModel::restore,
        onDeleteForever = { confirmDelete = true },
        onDuplicate = { scope.launch { viewModel.duplicate()?.let(onOpenNote) } },
        onMove = { showMove = true },
        onTags = { showTags = true },
        onShare = {
            viewModel.shareText()?.let { text ->
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, null))
            }
        },
        onExport = {
            viewModel.markdownExport()?.let { export ->
                if (export.lost.isEmpty()) startExport(export) else exportWarning = export
            }
        },
    )

    NoteEditorScreen(
        load = load,
        readOnly = readOnly,
        saveStatus = saveStatus,
        meta = meta,
        snackbar = snackbar,
        showBack = showBack,
        onBack = onClose,
        actions = actions,
    )

    if (showMove) {
        MoveToNotebookSheet(notebooks, meta?.notebookId, onPick = viewModel::moveToNotebook, onDismiss = { showMove = false })
    }
    if (showTags) {
        TagsSheet(
            allTags = allTags,
            attached = meta?.tags.orEmpty().map { it.id }.toSet(),
            onToggle = viewModel::setTag,
            onCreate = viewModel::createAndAttachTag,
            onDismiss = { showTags = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_forever_title),
            body = stringResource(R.string.confirm_delete_forever_body),
            confirm = stringResource(R.string.action_delete),
            onConfirm = { scope.launch { if (viewModel.deleteForever()) onClose() } },
            onDismiss = { confirmDelete = false },
        )
    }
    exportWarning?.let { export ->
        AlertDialog(
            onDismissRequest = { exportWarning = null },
            title = { Text(stringResource(R.string.export_lost_title)) },
            text = {
                Text(
                    export.lost.joinToString("\n") {
                        when (it) {
                            LostFeature.UNDERLINE -> context.getString(R.string.export_lost_underline)
                            LostFeature.UNSUPPORTED_CONTENT -> context.getString(R.string.export_lost_unsupported)
                        }
                    },
                )
            },
            confirmButton = { TextButton(onClick = { exportWarning = null; startExport(export) }) { Text(stringResource(R.string.action_continue)) } },
            dismissButton = { TextButton(onClick = { exportWarning = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

data class EditorMenuActions(
    val onPin: () -> Unit,
    val onFavourite: () -> Unit,
    val onArchive: () -> Unit,
    val onTrash: () -> Unit,
    val onRestore: () -> Unit,
    val onDeleteForever: () -> Unit,
    val onDuplicate: () -> Unit,
    val onMove: () -> Unit,
    val onTags: () -> Unit,
    val onShare: () -> Unit,
    val onExport: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    load: EditorLoad,
    readOnly: ReadOnlyReason?,
    saveStatus: SaveStatus,
    meta: NoteMeta?,
    snackbar: SnackbarHostState,
    showBack: Boolean,
    onBack: () -> Unit,
    actions: EditorMenuActions,
) {
    val controller = (load as? EditorLoad.Ready)?.controller
    Scaffold(
        modifier = Modifier.testTag(EditorTestTags.SCREEN),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    }
                },
                title = { if (readOnly == null && controller != null) SaveStatusLabel(saveStatus) },
                actions = {
                    if (controller != null && readOnly == null) {
                        IconButton(onClick = controller::undo, enabled = controller.canUndo) {
                            Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.editor_undo))
                        }
                        IconButton(onClick = controller::redo, enabled = controller.canRedo) {
                            Icon(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.editor_redo))
                        }
                    }
                    if (meta != null && !meta.inTrash) {
                        IconButton(onClick = actions.onPin) {
                            Icon(
                                if (meta.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                stringResource(if (meta.isPinned) R.string.note_unpin else R.string.note_pin),
                            )
                        }
                    }
                    if (meta != null) EditorOverflowMenu(meta, actions)
                },
            )
        },
        bottomBar = {
            if (controller != null && readOnly == null) {
                FormatToolbar(controller, Modifier.navigationBarsPadding().imePadding())
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when (load) {
                EditorLoad.Loading -> Unit
                EditorLoad.NotFound -> EmptyState(
                    title = stringResource(R.string.editor_not_found),
                    body = "",
                    modifier = Modifier.align(Alignment.Center),
                )
                is EditorLoad.Ready -> EditorContent(load.controller, readOnly, actions)
            }
        }
    }
}

@Composable
private fun SaveStatusLabel(status: SaveStatus) {
    val text = stringResource(
        when (status) {
            SaveStatus.SAVED -> R.string.editor_saved
            SaveStatus.UNSAVED -> R.string.editor_unsaved
            SaveStatus.SAVING -> R.string.editor_saving
            SaveStatus.FAILED -> R.string.editor_save_failed
        },
    )
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (status == SaveStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        modifier = Modifier.testTag(EditorTestTags.STATUS),
    )
}

@Composable
private fun EditorContent(controller: EditorController, readOnly: ReadOnlyReason?, actions: EditorMenuActions) {
    val spacing = AppTheme.spacing
    val bodyFocus = remember { FocusRequester() }
    val styles = EditorStyles.fromTheme()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val state = controller.state
    val transformation = remember(state, styles) { EditorVisualTransformation(state, styles) }
    val bodyLabel = stringResource(R.string.editor_body_label)
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .widthIn(max = spacing.maxContentWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.xl, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        readOnly?.let { reason ->
            Surface(color = colors.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.padding(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (reason == ReadOnlyReason.IN_TRASH) R.string.editor_in_trash else R.string.editor_read_only_newer),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (reason == ReadOnlyReason.IN_TRASH) {
                        TextButton(onClick = actions.onRestore) { Text(stringResource(R.string.note_restore)) }
                    }
                }
            }
        }

        val titleHint = stringResource(R.string.editor_title_hint)
        BasicTextField(
            value = controller.title,
            onValueChange = controller::onTitleChange,
            readOnly = readOnly != null,
            textStyle = MaterialTheme.typography.headlineSmall.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { bodyFocus.requestFocus() }),
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EditorTestTags.TITLE)
                .semantics { contentDescription = titleHint },
            decorationBox = { inner ->
                Box {
                    if (controller.title.isEmpty()) {
                        Text(titleHint, style = MaterialTheme.typography.headlineSmall, color = colors.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    inner()
                }
            },
        )

        val bodyHint = stringResource(R.string.editor_body_hint)
        BasicTextField(
            value = controller.textFieldValue,
            onValueChange = controller::onValueChange,
            readOnly = readOnly != null,
            visualTransformation = transformation,
            onTextLayout = { layout = it },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp)
                .focusRequester(bodyFocus)
                .testTag(EditorTestTags.BODY)
                .semantics { contentDescription = bodyLabel }
                .checkboxTaps(
                    enabled = readOnly == null,
                    layout = { layout },
                    decorations = { transformation.decorations },
                    isChecklist = { line -> state.paragraphs.getOrNull(line)?.kind == com.appname.notes.core.domain.document.BlockKind.CHECKLIST },
                    onToggle = controller::toggleChecked,
                ),
            decorationBox = { inner ->
                Box {
                    if (state.text.isEmpty() && state.paragraphs.size == 1 &&
                        state.paragraphs[0].kind == com.appname.notes.core.domain.document.BlockKind.PARAGRAPH
                    ) {
                        Text(bodyHint, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    inner()
                }
            },
        )
    }
}

/**
 * Taps on a checklist box toggle it instead of moving the cursor. Runs before the text field sees the
 * tap (Initial pass), and only consumes taps that land on a checkbox prefix.
 */
private fun Modifier.checkboxTaps(
    enabled: Boolean,
    layout: () -> TextLayoutResult?,
    decorations: () -> com.appname.notes.core.domain.editor.LineDecorations,
    isChecklist: (Int) -> Boolean,
    onToggle: (Int) -> Unit,
): Modifier = if (!enabled) this else pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val result = layout() ?: return@awaitEachGesture
        val offset = result.getOffsetForPosition(down.position)
        val line = decorations().lineWithPrefixAt(offset)?.takeIf(isChecklist) ?: return@awaitEachGesture
        down.consume()
        val up = waitForUpOrCancellation(PointerEventPass.Initial)
        if (up != null) {
            up.consume()
            onToggle(line)
        }
    }
}


@Composable
private fun EditorOverflowMenu(meta: NoteMeta, actions: EditorMenuActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun item(label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) =
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { Icon(icon, null) },
                    onClick = { open = false; onClick() },
                )
            if (meta.inTrash) {
                item(R.string.note_restore, Icons.Outlined.RestoreFromTrash, actions.onRestore)
                item(R.string.note_delete_forever, Icons.Outlined.DeleteForever, actions.onDeleteForever)
            } else {
                item(
                    if (meta.isFavourite) R.string.note_unfavourite else R.string.note_favourite,
                    if (meta.isFavourite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    actions.onFavourite,
                )
                item(R.string.note_tags, Icons.AutoMirrored.Outlined.Label, actions.onTags)
                item(R.string.note_move, Icons.Outlined.DriveFileMove, actions.onMove)
                item(R.string.note_duplicate, Icons.Outlined.ContentCopy, actions.onDuplicate)
                item(R.string.note_share, Icons.Outlined.Share, actions.onShare)
                item(R.string.note_export_markdown, Icons.Outlined.FileDownload, actions.onExport)
                item(
                    if (meta.isArchived) R.string.note_unarchive else R.string.note_archive,
                    if (meta.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                    actions.onArchive,
                )
                item(R.string.note_trash, Icons.Outlined.DeleteOutline, actions.onTrash)
            }
        }
    }
}
