package com.appname.notes.feature.editor

import android.Manifest
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.outlined.Mic
import com.appname.notes.core.domain.model.AudioMarker
import com.appname.notes.feature.audio.AudioControls
import com.appname.notes.feature.audio.AudioTestTags
import com.appname.notes.feature.audio.RecordingBar
import androidx.compose.material.icons.outlined.DocumentScanner
import com.appname.notes.feature.attachments.rememberDocumentScanner
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.InsertDriveFile
import com.appname.notes.core.domain.model.Attachment
import com.appname.notes.core.domain.model.AttachmentKind
import com.appname.notes.feature.attachments.AttachmentActions
import com.appname.notes.feature.attachments.AttachmentStrip
import com.appname.notes.feature.attachments.AttachmentTestTags
import com.appname.notes.feature.attachments.ImageViewer
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
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.material.icons.outlined.AlarmAdd
import androidx.compose.material.icons.outlined.AudioFile
import com.appname.notes.feature.reminders.RemindersRow
import com.appname.notes.feature.voice.VoiceTypingActions
import com.appname.notes.feature.voice.VoiceTypingBar
import com.appname.notes.feature.reminders.rememberReminderUi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.EmptyState
import com.appname.notes.core.designsystem.theme.AppTheme
import com.appname.notes.core.domain.document.BlockKind
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
    const val TABLE_INSERT = "editor_table_insert"
    const val CLIP = "editor_clip_page"
    const val TABLE_NEXT = "editor_table_next"
    const val TABLE_ADD_ROW = "editor_table_add_row"
    const val TABLE_ADD_COLUMN = "editor_table_add_column"
    const val STATUS = "editor_status"
    const val TOOLBAR = "editor_toolbar"
    const val REMIND_TASK = "editor_remind_task"
    const val VOICE = "editor_voice"
}

@Composable
fun NoteEditorRoute(
    viewModel: NoteEditorViewModel,
    onClose: () -> Unit,
    onOpenNote: (NoteId) -> Unit,
    showBack: Boolean = true,
    /** Opens a PDF note (an attached PDF the user chose to write on). */
    onOpenPdfNote: (NoteId) -> Unit = {},
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
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(message, snackbar, viewModel::messageShown)

    // Leaving the app (home button, app switcher, screen off): save now — the process may be killed next.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.flush()
        viewModel.voice.value?.pause() // never keep the microphone on in the background
    }

    var showMove by remember { mutableStateOf(false) }
    var showTags by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ExportResult?>(null) }
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val playback by viewModel.playback.state.collectAsStateWithLifecycle()
    // Microphone (and, on Android 13+, the "Recording" notification), asked only when recording starts.
    val recordPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) viewModel.startRecording() else viewModel.microphoneDenied()
    }
    val attaching by viewModel.attaching.collectAsStateWithLifecycle()
    var viewing by remember { mutableStateOf<Attachment?>(null) }
    // Where the camera app writes the photo; saved because the camera may outlive our process.
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    // System pickers: they grant access to exactly the chosen items, so no storage/photo permission is needed.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { viewModel.attach(it) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.attach(it) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.attach(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val path = cameraPath
        cameraPath = null
        if (path != null) viewModel.attachCameraPhoto(path, taken)
    }
    val intents = viewModel.attachmentIntents
    val shareTitle = stringResource(R.string.attach_share)
    val editable = readOnly == null && load is EditorLoad.Ready
    val attachmentActions = AttachmentActions(
        open = { a ->
            if (a.kind == AttachmentKind.IMAGE) {
                viewing = a
            } else if (!intents.start(context, intents.viewIntent(a))) {
                viewModel.noAppToOpen()
            }
        },
        openWith = { a -> if (!intents.start(context, Intent.createChooser(intents.viewIntent(a), null))) viewModel.noAppToOpen() },
        share = { a -> intents.start(context, intents.shareIntent(a, shareTitle)) },
        remove = if (editable) viewModel::removeAttachment else null,
        annotate = { a -> scope.launch { viewModel.annotatePdf(a)?.let(onOpenPdfNote) } },
        rename = if (editable) viewModel::renameAttachment else null,
    )
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    val scan = rememberDocumentScanner(onScanned = viewModel::attachScan, onUnavailable = viewModel::scannerUnavailable)
    val attachMenu = AttachMenu(
        photos = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        camera = if (hasCamera) {
            {
                val (file, uri) = intents.newCameraFile()
                cameraPath = file.path
                try {
                    camera.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    cameraPath = null
                    viewModel.cameraUnavailable()
                }
            }
        } else {
            null
        },
        files = { filePicker.launch(arrayOf("*/*")) },
        audioFile = { audioPicker.launch(arrayOf("audio/*")) },
        scan = scan,
        record = {
            val wanted = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                viewModel.startRecording()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    recordPermissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            } else {
                recordPermissions.launch(wanted.toTypedArray())
            }
        },
    )
    val audioControls = AudioControls(
        playback = playback,
        play = { viewModel.playback.play(it) },
        pause = viewModel.playback::pause,
        seek = viewModel.playback::seekTo,
        markers = viewModel::markersFor,
        playMarker = viewModel::playMarker,
        setSpeed = viewModel.playback::setSpeed,
    )
    var exportWarning by remember { mutableStateOf<ExportResult?>(null) }

    // Voice typing (§11): the microphone is asked for when the user taps Start.
    val voiceSession by viewModel.voice.collectAsStateWithLifecycle()
    val voicePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoiceTyping() else viewModel.voicePermissionDenied()
    }
    val startVoice = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startVoiceTyping()
        } else {
            viewModel.openVoiceTyping()
            voicePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Reminders (FR-010): the dialog, and asking for notifications when one is set.
    val reminders by viewModel.reminders.reminders.collectAsStateWithLifecycle()
    val reminderUi = rememberReminderUi(viewModel.reminders, editable, taskText = viewModel::taskText)

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
        onRemind = { reminderUi.add() },
    )

    val session = voiceSession
    val voicePanel: (@Composable (Modifier) -> Unit)? = if (session == null) {
        null
    } else {
        { panelModifier ->
            val voiceState by session.state.collectAsStateWithLifecycle()
            VoiceTypingBar(
                modifier = panelModifier,
                state = voiceState,
                actions = VoiceTypingActions(
                    start = startVoice,
                    pause = session::pause,
                    language = viewModel::setVoiceLanguage,
                    useOnline = { session.useOnlineService(); session.start() },
                    done = viewModel::closeVoiceTyping,
                ),
            )
        }
    }

    NoteEditorScreen(
        load = load,
        readOnly = readOnly,
        saveStatus = saveStatus,
        meta = meta,
        snackbar = snackbar,
        showBack = showBack,
        onBack = onClose,
        actions = actions,
        attachMenu = if (editable) attachMenu else null,
        attachments = {
            AttachmentStrip(attachments, attaching, viewModel.previews, attachmentActions, audio = audioControls)
        },
        recordingBar = {
            RecordingBar(recording, viewModel.noteId, onAddMarker = viewModel::addMarker, onStop = viewModel::stopRecording)
        },
        onAudioMarker = viewModel::playMarker,
        reminders = {
            RemindersRow(
                reminders = reminders.orEmpty(),
                taskText = viewModel::taskText,
                canAlert = reminderUi.canAlert,
                onEdit = reminderUi::edit,
                onFixAlerts = reminderUi::fixAlerts,
            )
        },
        onVoiceTyping = if (editable) startVoice else null,
        onClipLink = if (editable) viewModel::clipLink else null,
        voicePanel = voicePanel,
        onRemindTask = if (editable) {
            { viewModel.taskAtCursor()?.let(reminderUi::add) ?: Unit }
        } else {
            null
        },
    )

    viewing?.let { a ->
        // Closed if the photo is removed elsewhere (or by Undo racing): never show a stale item.
        if (attachments.none { it.id == a.id }) {
            viewing = null
        } else {
            ImageViewer(a, viewModel.previews, attachmentActions, onDismiss = { viewing = null })
        }
    }

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
                            LostFeature.UNDERLINE -> resources.getString(R.string.export_lost_underline)
                            LostFeature.UNSUPPORTED_CONTENT -> resources.getString(R.string.export_lost_unsupported)
                            LostFeature.ATTACHMENTS -> resources.getString(R.string.export_lost_attachments)
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
    val onRemind: () -> Unit = {},
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
    /** Null when attaching isn't possible (read-only note). */
    attachMenu: AttachMenu? = null,
    /** Shown between the title and the text. */
    attachments: @Composable () -> Unit = {},
    /** Shown under the top bar while recording. */
    recordingBar: @Composable () -> Unit = {},
    /** The cursor is on an audio marker and "Play from" was tapped. */
    onAudioMarker: (AudioMarker) -> Unit = {},
    /** The note's reminders, shown under the title. */
    reminders: @Composable () -> Unit = {},
    /** "Remind me about this task" on a checklist item; null when the note can't be edited. */
    onRemindTask: (() -> Unit)? = null,
    /** Opens voice typing; null when the note can't be edited. */
    onVoiceTyping: (() -> Unit)? = null,
    onClipLink: ((String) -> Unit)? = null,
    /** The open voice-typing panel, shown in place of the formatting toolbar. */
    voicePanel: (@Composable (Modifier) -> Unit)? = null,
) {
    val controller = (load as? EditorLoad.Ready)?.controller
    Scaffold(
        modifier = Modifier.testTag(EditorTestTags.SCREEN),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
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
                        attachMenu?.let { AttachButton(it) }
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
                recordingBar()
            }
        },
        bottomBar = {
            if (voicePanel != null) {
                voicePanel(Modifier.navigationBarsPadding().imePadding())
            } else if (controller != null && readOnly == null) {
                FormatToolbar(controller, Modifier.navigationBarsPadding().imePadding(), onAudioMarker = onAudioMarker, onRemindTask = onRemindTask, onVoiceTyping = onVoiceTyping, onClipLink = onClipLink)
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
                is EditorLoad.Ready -> EditorContent(load.controller, readOnly, actions, attachments, reminders)
            }
        }
    }
}

@Composable
internal fun SaveStatusLabel(status: SaveStatus) {
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
private fun EditorContent(
    controller: EditorController,
    readOnly: ReadOnlyReason?,
    actions: EditorMenuActions,
    attachments: @Composable () -> Unit,
    reminders: @Composable () -> Unit,
) {
    val spacing = AppTheme.spacing
    val bodyFocus = remember { FocusRequester() }
    val styles = EditorStyles.fromTheme()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val state = controller.state
    val transformation = remember(state, styles) { EditorVisualTransformation(state, styles) }
    // The tap handler is installed once; these always hold the latest values for it to read.
    val latestTransformation by rememberUpdatedState(transformation)
    val latestState by rememberUpdatedState(state)
    val bodyLabel = stringResource(R.string.editor_body_label)
    val toggleLabel = stringResource(R.string.checkbox_toggle)
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

        reminders()
        attachments()

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
                .semantics {
                    contentDescription = bodyLabel
                    // TalkBack users can't aim at the small box: offer "Toggle checkbox" in the actions menu.
                    if (readOnly == null && state.paragraphs[state.lineOf(state.selection.min)].kind == BlockKind.CHECKLIST) {
                        customActions = listOf(
                            CustomAccessibilityAction(toggleLabel) {
                                controller.toggleChecked(controller.state.lineOf(controller.state.selection.min))
                                true
                            },
                        )
                    }
                }
                // Keyboard users: Ctrl+Enter ticks the checklist item under the cursor.
                .onPreviewKeyEvent { event ->
                    val toggle = readOnly == null && event.type == KeyEventType.KeyDown && event.key == Key.Enter && event.isCtrlPressed
                    if (toggle) controller.toggleChecked(controller.state.lineOf(controller.state.selection.min))
                    toggle
                }
                .checkboxTaps(
                    enabled = readOnly == null,
                    layout = { layout },
                    decorations = { latestTransformation.decorations },
                    isChecklist = { line -> latestState.paragraphs.getOrNull(line)?.kind == BlockKind.CHECKLIST },
                    onToggle = controller::toggleChecked,
                ),
            decorationBox = { inner ->
                Box {
                    if (state.text.isEmpty() && state.paragraphs.size == 1 &&
                        state.paragraphs[0].kind == BlockKind.PARAGRAPH
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
                item(R.string.reminder_add, Icons.Outlined.AlarmAdd, actions.onRemind)
                item(
                    if (meta.isFavourite) R.string.note_unfavourite else R.string.note_favourite,
                    if (meta.isFavourite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    actions.onFavourite,
                )
                item(R.string.note_tags, Icons.AutoMirrored.Outlined.Label, actions.onTags)
                item(R.string.note_move, Icons.AutoMirrored.Outlined.DriveFileMove, actions.onMove)
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

/** The ways to add an attachment (FR-009, FR-011, FR-012). [camera] is null on devices without a camera. */
data class AttachMenu(
    val photos: () -> Unit,
    val camera: (() -> Unit)?,
    val files: () -> Unit,
    val scan: () -> Unit = {},
    val record: () -> Unit = {},
    val audioFile: () -> Unit = {},
)

@Composable
private fun AttachButton(menu: AttachMenu) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag(AttachmentTestTags.ATTACH_BUTTON)) {
            Icon(Icons.Outlined.AttachFile, stringResource(R.string.attach_menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.attach_photos)) },
                leadingIcon = { Icon(Icons.Outlined.Image, null) },
                onClick = { open = false; menu.photos() },
            )
            menu.camera?.let { camera ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.attach_camera)) },
                    leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                    onClick = { open = false; camera() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.audio_record)) },
                leadingIcon = { Icon(Icons.Outlined.Mic, null) },
                onClick = { open = false; menu.record() },
                modifier = Modifier.testTag(AudioTestTags.RECORD),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.attach_audio_file)) },
                leadingIcon = { Icon(Icons.Outlined.AudioFile, null) },
                onClick = { open = false; menu.audioFile() },
                modifier = Modifier.testTag(AttachmentTestTags.AUDIO_FILE),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.scan_document)) },
                leadingIcon = { Icon(Icons.Outlined.DocumentScanner, null) },
                onClick = { open = false; menu.scan() },
                modifier = Modifier.testTag(AttachmentTestTags.SCAN),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.attach_files)) },
                leadingIcon = { Icon(Icons.Outlined.InsertDriveFile, null) },
                onClick = { open = false; menu.files() },
            )
        }
    }
}

private const val MAX_PICK = 20
