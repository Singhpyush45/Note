package com.appname.notes.feature.library

import com.appname.notes.core.domain.model.FolderTree
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.EmptyState
import com.appname.notes.core.designsystem.component.GlassSurface
import com.appname.notes.core.designsystem.component.backdropSource
import com.appname.notes.core.designsystem.component.rememberBackdrop
import com.appname.notes.core.designsystem.theme.AppTheme
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.domain.model.NoteSummary
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.domain.model.Notebook
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.feature.attachments.rememberDocumentScanner
import com.appname.notes.ui.common.ActionSheet
import com.appname.notes.ui.common.CardAction
import com.appname.notes.ui.common.InkThumbnail
import com.appname.notes.ui.common.MessageEffect
import com.appname.notes.ui.common.MoveToNotebookSheet
import com.appname.notes.ui.common.NoteCard
import com.appname.notes.ui.common.SheetAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object LibraryTestTags {
    const val SCREEN = "screen_home"
    const val HEADER = "home_header"
    const val CREATE = "fab_create"
    const val CREATE_SCRIM = "create_scrim"
    const val NEW_NOTE = "fab_new_note"
    const val CHOOSE_TEMPLATE = "create_choose_template"
    const val SCAN = "action_scan"
    const val NEW_FOLDER = "action_new_folder"
    const val IMPORT_FILE = "action_import_file"
    const val COLLECTION_BUTTON = "collection_button"
    const val SEARCH = "action_search"
    const val GRID = "notes_grid"
    const val LOADING = "library_loading"
    const val IMPORTING_PDF = "importing_pdf"
    const val CHIPS = "collection_chips"
    const val CHIP_ALL = "chip_all"
    const val CHIP_FAVOURITES = "chip_favourites"
    const val SELECTION_BAR = "selection_bar"
    const val SELECTION_COUNT = "selection_count"
    const val SELECTION_CLEAR = "selection_clear"
    const val SELECT_ALL = "selection_select_all"
    const val SELECTION_PIN = "selection_pin"
    const val SELECTION_MOVE = "selection_move"
    const val SELECTION_ARCHIVE = "selection_archive"
    const val SELECTION_TRASH = "selection_trash"
    const val SELECTION_RESTORE = "selection_restore"
    const val SELECTION_DELETE = "selection_delete"
    const val RENAME_DIALOG = "rename_dialog"
    const val RENAME_FIELD = "rename_field"
    const val RENAME_SAVE = "rename_save"

    fun chip(nb: Notebook) = "chip_nb_${nb.id.value}"
    fun card(id: NoteId) = "note_card_${id.value}"
    fun more(id: NoteId) = "note_more_${id.value}"
}

@Composable
fun LibraryRoute(
    onOpenNote: (NoteId, NoteType) -> Unit,
    onOpenSearch: () -> Unit,
    onManageNotebooks: () -> Unit,
    onManageTags: () -> Unit,
    selectedNoteId: String?,
    /** Opens the Choose-template screen for the shown notebook/tag (null = none). */
    onChooseTemplate: (notebookId: String?, tagId: String?) -> Unit = { _, _ -> },
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // The system file picker grants read access to the one file picked; no storage permission needed.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { viewModel.importFile(uri)?.let { (id, type) -> onOpenNote(id, type) } }
    }
    val importingPdf by viewModel.importingPdf.collectAsStateWithLifecycle()
    val scan = rememberDocumentScanner(
        onScanned = { file -> scope.launch { viewModel.createNoteFromScan(file)?.let { onOpenNote(it, NoteType.TEXT) } } },
        onUnavailable = viewModel::scannerUnavailable,
    )
    // A notebook/tag shown here was deleted elsewhere: go back to All notes.
    LaunchedEffect(state.collection, state.loading, state.collectionName) {
        val c = state.collection
        if (!state.loading && (c is NoteCollection.InNotebook || c is NoteCollection.WithTag) && state.collectionName == null) {
            viewModel.onCollectionMissing()
        }
    }
    LibraryScreen(
        state = state,
        selectedNoteId = selectedNoteId,
        onOpenNote = onOpenNote,
        create = CreateActions(
            blankNote = { scope.launch { viewModel.createBlankNote()?.let { (id, type) -> onOpenNote(id, type) } } },
            chooseTemplate = {
                val c = state.collection
                onChooseTemplate((c as? NoteCollection.InNotebook)?.id?.value, (c as? NoteCollection.WithTag)?.id?.value)
            },
            scanNote = scan,
            importFile = { importLauncher.launch(arrayOf("*/*")) },
        ),
        inkThumbnail = viewModel::inkThumbnail,
        cover = viewModel::cover,
        onOpenSearch = onOpenSearch,
        onSelectCollection = viewModel::selectCollection,
        onSort = viewModel::setSort,
        importingPdf = importingPdf,
        folders = FolderActions(
            create = viewModel::createFolder,
            rename = viewModel::renameFolder,
            move = viewModel::moveFolder,
            delete = viewModel::deleteFolder,
        ),
        onManageNotebooks = onManageNotebooks,
        onManageTags = onManageTags,
        actions = NoteActions(
            setPinned = viewModel::setPinned,
            setFavourite = viewModel::setFavourite,
            setArchived = viewModel::setArchived,
            trash = viewModel::moveToTrash,
            restore = viewModel::restore,
            deleteForever = viewModel::deleteForever,
            duplicate = viewModel::duplicate,
            move = viewModel::moveToNotebook,
            emptyTrash = viewModel::emptyTrash,
            rename = viewModel::rename,
        ),
        bulk = BulkActions(
            setPinned = viewModel::setPinned,
            setArchived = viewModel::setArchived,
            trash = viewModel::moveToTrash,
            restore = viewModel::restore,
            deleteForever = viewModel::deleteForever,
            move = viewModel::moveToNotebook,
        ),
        onMessageShown = viewModel::messageShown,
    )
}

/** Callbacks for note actions, grouped to keep signatures short. */
data class NoteActions(
    val setPinned: (NoteId, Boolean) -> Unit,
    val setFavourite: (NoteId, Boolean) -> Unit,
    val setArchived: (NoteId, Boolean) -> Unit,
    val trash: (NoteId) -> Unit,
    val restore: (NoteId) -> Unit,
    val deleteForever: (NoteId) -> Unit,
    val duplicate: (NoteId) -> Unit,
    val move: (NoteId, NotebookId?) -> Unit,
    val emptyTrash: () -> Unit,
    val rename: (NoteId, String) -> Unit = { _, _ -> },
)

/** The same actions for several selected notes at once. */
data class BulkActions(
    val setPinned: (Set<NoteId>, Boolean) -> Unit,
    val setArchived: (Set<NoteId>, Boolean) -> Unit,
    val trash: (Set<NoteId>) -> Unit,
    val restore: (Set<NoteId>) -> Unit,
    val deleteForever: (Set<NoteId>) -> Unit,
    val move: (Set<NoteId>, NotebookId?) -> Unit,
)

/** Selected note ids survive rotation and process death. */
private val SelectionSaver = Saver<MutableState<Set<String>>, ArrayList<String>>(
    save = { ArrayList(it.value) },
    restore = { mutableStateOf(it.toSet()) },
)

/**
 * The notes-first home (owner brief §2–3): a glass header (collection, search, sort, more) with quick filters,
 * the notes as cards scrolling under it, and one "+" that opens the create menu. Long-press selects notes for
 * actions on several at once; each card's ⋮ has that note's actions.
 */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    selectedNoteId: String?,
    onOpenNote: (NoteId, NoteType) -> Unit,
    create: CreateActions,
    inkThumbnail: suspend (NoteSummary) -> InkThumbnail?,
    onOpenSearch: () -> Unit,
    onSelectCollection: (NoteCollection) -> Unit,
    onSort: (NoteSort) -> Unit,
    onManageNotebooks: () -> Unit,
    onManageTags: () -> Unit,
    actions: NoteActions,
    onMessageShown: () -> Unit,
    bulk: BulkActions? = null,
    importingPdf: Boolean = false,
    cover: (suspend (String) -> Bitmap?)? = null,
    folders: FolderActions? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    // Folders inside folders: new / actions / rename / move / delete.
    var newFolder by rememberSaveable { mutableStateOf(false) }
    var folderActionsFor by remember { mutableStateOf<Notebook?>(null) }
    var renameFolderFor by remember { mutableStateOf<Notebook?>(null) }
    var moveFolderFor by remember { mutableStateOf<Notebook?>(null) }
    var deleteFolderFor by remember { mutableStateOf<Notebook?>(null) }
    // Back from a folder inside a folder goes up one level.
    val parentFolder = state.parentFolder
    BackHandler(enabled = parentFolder != null) { parentFolder?.let { onSelectCollection(NoteCollection.InNotebook(it.id)) } }
    MessageEffect(state.message, snackbar, onMessageShown)

    var showCollections by rememberSaveable { mutableStateOf(false) }
    var createOpen by rememberSaveable { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<NoteSummary?>(null) }
    var renameFor by remember { mutableStateOf<NoteSummary?>(null) }
    var moveFor by remember { mutableStateOf<NoteSummary?>(null) }
    var moveSelected by remember { mutableStateOf(false) }
    var confirmDeleteFor by remember { mutableStateOf<NoteSummary?>(null) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    var confirmEmptyTrash by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable(saver = SelectionSaver) { mutableStateOf(emptySet()) }

    val all = remember(state.pinned, state.others) { state.pinned + state.others }
    val selecting = selected.isNotEmpty()
    val selectedNotes = all.filter { it.id.value in selected }
    // Notes that left this view (deleted, moved, other collection) drop out of the selection.
    LaunchedEffect(all) {
        if (!state.loading) {
            val ids = all.mapTo(HashSet()) { it.id.value }
            if (!ids.containsAll(selected)) selected = selected intersect ids
        }
    }
    // Switching to another collection ends selecting (but rotation and process death keep it).
    var selectionScope by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.collection, state.loading) {
        if (state.loading) return@LaunchedEffect
        val key = LibraryViewModel.encode(state.collection)
        if (selectionScope != null && selectionScope != key) selected = emptySet()
        selectionScope = key
    }
    BackHandler(enabled = selecting) { selected = emptySet() }

    fun toggle(note: NoteSummary) {
        val id = note.id.value
        selected = if (id in selected) selected - id else selected + id
    }
    val selectedIds = { selectedNotes.mapTo(HashSet()) { it.id } }

    val title = collectionTitle(state)
    val importingDescription = stringResource(R.string.pdf_importing)
    val inTrash = state.collection == NoteCollection.Trash
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(0.dp) }
    val backdrop = rememberBackdrop()

    Scaffold(
        modifier = Modifier.testTag(LibraryTestTags.SCREEN),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.canCreate && !selecting) {
                CreateMenu(
                    expanded = createOpen,
                    onExpandedChange = { createOpen = it },
                    actions = if (folders != null && state.canCreate) create.copy(newFolder = { newFolder = true }) else create,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // The notes scroll under the glass header, which blurs them (Android 12+).
            Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
                when {
                    state.loading -> LoadingNotes(Modifier.align(Alignment.Center))
                    state.isEmpty && state.currentFolder == null -> EmptyCollection(state.collection, Modifier.align(Alignment.Center).padding(top = headerHeight))
                    else -> NotesGrid(
                        state = state,
                        topPadding = headerHeight,
                        selectedNoteId = selectedNoteId,
                        selected = selected,
                        onClick = { note -> if (selecting) toggle(note) else onOpenNote(note.id, note.type) },
                        onLongPress = ::toggle,
                        onMore = if (selecting) null else { note: NoteSummary -> actionsFor = note },
                        inkThumbnail = inkThumbnail,
                        cover = cover,
                        onOpenFolder = { onSelectCollection(NoteCollection.InNotebook(it.id)) },
                        onFolderMore = if (folders != null && !selecting) { nb: Notebook -> folderActionsFor = nb } else null,
                        onNewFolder = if (folders != null && !selecting) { { newFolder = true } } else null,
                        accessibilityActions = { note ->
                            noteActions(
                                note, state.collection, actions,
                                onMove = { moveFor = it }, onDelete = { confirmDeleteFor = it },
                                onRename = { renameFor = it }, onSelect = ::toggle,
                            ).map { CardAction(stringResourceLabel(it.label), it.onClick) }
                        },
                    )
                }
            }
            GlassSurface(
                shape = MaterialTheme.shapes.extraLarge,
                elevation = 3.dp,
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f)
                    // Measured with the status bar and margins: the grid starts below all of it.
                    .onSizeChanged { with(density) { headerHeight = it.height.toDp() } }
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .testTag(LibraryTestTags.HEADER),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    if (selecting) {
                        SelectionBar(
                            count = selectedNotes.size,
                            collection = state.collection,
                            allPinned = selectedNotes.isNotEmpty() && selectedNotes.all { it.isPinned },
                            actions = SelectionActions(
                                clear = { selected = emptySet() },
                                selectAll = { selected = all.mapTo(HashSet()) { it.id.value } },
                                pin = {
                                    val pin = !selectedNotes.all { it.isPinned }
                                    bulk?.setPinned?.invoke(selectedIds(), pin)
                                    selected = emptySet()
                                },
                                move = { moveSelected = true },
                                archive = {
                                    bulk?.setArchived?.invoke(selectedIds(), state.collection != NoteCollection.Archive)
                                    selected = emptySet()
                                },
                                trash = { bulk?.trash?.invoke(selectedIds()); selected = emptySet() },
                                restore = { bulk?.restore?.invoke(selectedIds()); selected = emptySet() },
                                deleteForever = { confirmDeleteSelected = true },
                            ),
                        )
                    } else {
                        HomeTopRow(
                            title = title,
                            sort = state.sort,
                            inTrash = inTrash,
                            onCollections = { showCollections = true },
                            onOpenSearch = onOpenSearch,
                            onSort = onSort,
                            onEmptyTrash = { confirmEmptyTrash = true },
                            onManageNotebooks = onManageNotebooks,
                            onManageTags = onManageTags,
                            onNewFolder = if (folders != null && state.canCreate) { { newFolder = true } } else null,
                        )
                        if (parentFolder != null) {
                            // Inside a folder that is inside another: one tap back up.
                            AssistChip(
                                onClick = { onSelectCollection(NoteCollection.InNotebook(parentFolder.id)) },
                                label = { Text(stringResource(R.string.folder_up, parentFolder.title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp)) },
                                modifier = Modifier.padding(start = 12.dp).testTag(FolderTestTags.UP),
                            )
                        }
                        CollectionChips(state.collection, FolderTree.children(state.notebooks, null), onSelectCollection, Modifier.padding(bottom = 6.dp))
                    }
                }
            }
            if (importingPdf) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter).zIndex(2f).testTag(LibraryTestTags.IMPORTING_PDF)
                        .semantics { contentDescription = importingDescription },
                )
            }
            CreateMenuScrim(visible = createOpen, onDismiss = { createOpen = false })
        }
    }

    if (newFolder && folders != null) {
        RenameNoteDialog(
            current = "",
            onRename = { if (it.isNotBlank()) folders.create(it) },
            onDismiss = { newFolder = false },
            titleRes = R.string.folder_new,
            labelRes = R.string.folder_name,
        )
    }
    folderActionsFor?.let { nb ->
        ActionSheet(
            title = nb.title,
            actions = listOf(
                SheetAction(stringResource(R.string.action_rename), Icons.Outlined.Edit) { renameFolderFor = nb },
                SheetAction(stringResource(R.string.folder_move), Icons.Outlined.DriveFileMove) { moveFolderFor = nb },
                SheetAction(stringResource(R.string.action_delete), Icons.Outlined.Delete) { deleteFolderFor = nb },
            ),
            onDismiss = { folderActionsFor = null },
        )
    }
    renameFolderFor?.let { nb ->
        RenameNoteDialog(
            current = nb.title,
            onRename = { folders?.rename?.invoke(nb.id, it) },
            onDismiss = { renameFolderFor = null },
            titleRes = R.string.folder_rename,
            labelRes = R.string.folder_name,
        )
    }
    moveFolderFor?.let { nb ->
        // Not into itself or a folder inside it.
        val blocked = FolderTree.descendants(state.notebooks, nb.id)
        MoveToNotebookSheet(
            state.notebooks.filter { it.id !in blocked },
            nb.parentId,
            onPick = { folders?.move?.invoke(nb.id, it) },
            onDismiss = { moveFolderFor = null },
            noneLabel = stringResource(R.string.folder_top_level),
        )
    }
    deleteFolderFor?.let { nb ->
        ConfirmDialog(
            title = stringResource(R.string.folder_delete_title, nb.title),
            body = stringResource(R.string.folder_delete_body),
            confirm = stringResource(R.string.action_delete),
            onConfirm = {
                folders?.delete?.invoke(nb.id)
                if (state.currentFolder in FolderTree.descendants(state.notebooks, nb.id)) onSelectCollection(NoteCollection.All)
            },
            onDismiss = { deleteFolderFor = null },
        )
    }
    if (showCollections) {
        CollectionSheet(state, onSelect = { showCollections = false; onSelectCollection(it) }, onDismiss = { showCollections = false },
            onManageNotebooks = { showCollections = false; onManageNotebooks() }, onManageTags = { showCollections = false; onManageTags() })
    }
    actionsFor?.let { note ->
        ActionSheet(
            title = note.displayTitle(),
            actions = noteActions(
                note, state.collection, actions,
                onMove = { moveFor = it }, onDelete = { confirmDeleteFor = it },
                onRename = { renameFor = it }, onSelect = ::toggle,
            ).map { SheetAction(stringResource(it.label), it.icon, it.onClick) },
            onDismiss = { actionsFor = null },
        )
    }
    renameFor?.let { note ->
        RenameNoteDialog(current = note.title, onRename = { actions.rename(note.id, it) }, onDismiss = { renameFor = null })
    }
    moveFor?.let { note ->
        MoveToNotebookSheet(state.notebooks, note.notebookId, onPick = { actions.move(note.id, it) }, onDismiss = { moveFor = null })
    }
    if (moveSelected) {
        val current = selectedNotes.map { it.notebookId }.distinct().singleOrNull()
        MoveToNotebookSheet(
            state.notebooks,
            current,
            onPick = { bulk?.move?.invoke(selectedIds(), it); selected = emptySet() },
            onDismiss = { moveSelected = false },
        )
    }
    confirmDeleteFor?.let { note ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_forever_title),
            body = stringResource(R.string.confirm_delete_forever_body),
            confirm = stringResource(R.string.action_delete),
            onConfirm = { actions.deleteForever(note.id) },
            onDismiss = { confirmDeleteFor = null },
        )
    }
    if (confirmDeleteSelected) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_many_title),
            body = stringResource(R.string.confirm_delete_many_body),
            confirm = stringResource(R.string.action_delete),
            onConfirm = { bulk?.deleteForever?.invoke(selectedIds()); selected = emptySet() },
            onDismiss = { confirmDeleteSelected = false },
        )
    }
    if (confirmEmptyTrash) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_empty_trash_title),
            body = stringResource(R.string.confirm_empty_trash_body),
            confirm = stringResource(R.string.library_empty_trash),
            onConfirm = actions.emptyTrash,
            onDismiss = { confirmEmptyTrash = false },
        )
    }
}

@Composable
private fun NoteSummary.displayTitle(): String =
    title.ifBlank { preview.lineSequence().firstOrNull().orEmpty() }.ifBlank { stringResource(R.string.untitled) }

/** "All notes ▾" (all collections, archive, trash, tags), search, sort and more. */
@Composable
private fun HomeTopRow(
    title: String,
    sort: NoteSort,
    inTrash: Boolean,
    onCollections: () -> Unit,
    onOpenSearch: () -> Unit,
    onSort: (NoteSort) -> Unit,
    onEmptyTrash: () -> Unit,
    onManageNotebooks: () -> Unit,
    onManageTags: () -> Unit,
    onNewFolder: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val description = stringResource(R.string.collection_choose, title)
        // The title takes the free space; a long name is shortened, the ▾ always shows.
        Box(Modifier.weight(1f)) {
            TextButton(
                onClick = onCollections,
                modifier = Modifier
                    .testTag(LibraryTestTags.COLLECTION_BUTTON)
                    .semantics { contentDescription = description },
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 2.dp).size(24.dp),
                )
            }
        }
        IconButton(onClick = onOpenSearch, modifier = Modifier.testTag(LibraryTestTags.SEARCH)) {
            Icon(Icons.Outlined.Search, stringResource(R.string.library_search))
        }
        SortMenu(sort, onSort)
        OverflowMenu(inTrash, onEmptyTrash, onManageNotebooks, onManageTags, onNewFolder)
    }
}

@Composable
private fun LoadingNotes(modifier: Modifier) {
    // Only after a moment, so a quick load doesn't flash a spinner.
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(LOADING_DELAY_MS); show = true }
    if (show) {
        val description = stringResource(R.string.library_loading)
        CircularProgressIndicator(modifier.testTag(LibraryTestTags.LOADING).semantics { contentDescription = description })
    }
}

private const val LOADING_DELAY_MS = 400L

/** Resolves a string resource id to text inside composition (used for accessibility action labels). */
@Composable
private fun stringResourceLabel(id: Int): String = stringResource(id)

@Composable
private fun collectionTitle(state: LibraryUiState): String = when (val c = state.collection) {
    NoteCollection.All -> stringResource(R.string.collection_all)
    NoteCollection.Favourites -> stringResource(R.string.collection_favourites)
    NoteCollection.Archive -> stringResource(R.string.collection_archive)
    NoteCollection.Trash -> stringResource(R.string.collection_trash)
    is NoteCollection.InNotebook -> state.collectionName ?: stringResource(R.string.collection_notebooks)
    is NoteCollection.WithTag -> "#" + (state.collectionName ?: "")
}

@Composable
private fun NotesGrid(
    state: LibraryUiState,
    topPadding: androidx.compose.ui.unit.Dp,
    selectedNoteId: String?,
    selected: Set<String>,
    onClick: (NoteSummary) -> Unit,
    onLongPress: (NoteSummary) -> Unit,
    onMore: ((NoteSummary) -> Unit)?,
    inkThumbnail: suspend (NoteSummary) -> InkThumbnail?,
    cover: (suspend (String) -> Bitmap?)?,
    accessibilityActions: @Composable (NoteSummary) -> List<CardAction>,
    onOpenFolder: (Notebook) -> Unit = {},
    onFolderMore: ((Notebook) -> Unit)? = null,
    onNewFolder: (() -> Unit)? = null,
) {
    val spacing = AppTheme.spacing
    val card: @Composable (NoteSummary) -> Unit = { note ->
        NoteCard(
            note = note,
            onClick = { onClick(note) },
            onLongClick = { onLongPress(note) },
            accessibilityActions = accessibilityActions(note),
            selected = note.id.value == selectedNoteId,
            inkThumbnail = inkThumbnail,
            cover = cover,
            onMore = onMore?.let { { it(note) } },
            checked = note.id.value in selected,
        )
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 260.dp),
        contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, top = topPadding + spacing.xs, bottom = 112.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalItemSpacing = spacing.md,
        modifier = Modifier.fillMaxSize().testTag(LibraryTestTags.GRID),
    ) {
        // Folders inside this folder come first, like a file manager.
        if (state.currentFolder != null) {
            if (state.subfolders.isNotEmpty()) {
                item(key = "h_folders", span = StaggeredGridItemSpan.FullLine) { SectionLabel(stringResource(R.string.folder_section)) }
                items(state.subfolders, key = { "f_" + it.id.value }) { nb ->
                    FolderCard(nb, state.notebooks, onOpen = { onOpenFolder(nb) }, onMore = onFolderMore?.let { { it(nb) } })
                }
            }
            if (onNewFolder != null) item(key = "f_new") { NewFolderCard(onNewFolder) }
            if (state.pinned.isNotEmpty() || state.others.isNotEmpty()) {
                item(key = "h_notes", span = StaggeredGridItemSpan.FullLine) { SectionLabel(stringResource(R.string.folder_notes_section)) }
            }
        }
        if (state.pinned.isNotEmpty()) {
            item(key = "h_pinned", span = StaggeredGridItemSpan.FullLine) { SectionLabel(stringResource(R.string.library_pinned)) }
            items(state.pinned, key = { it.id.value }) { card(it) }
            if (state.others.isNotEmpty()) {
                item(key = "h_others", span = StaggeredGridItemSpan.FullLine) { SectionLabel(stringResource(R.string.library_others)) }
            }
        }
        items(state.others, key = { it.id.value }) { card(it) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 8.dp, bottom = 2.dp)
            .semantics { heading() },
    )
}

private data class ActionSpec(val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector, val onClick: () -> Unit)

/** The actions offered for a note, depending on where it is shown. */
private fun noteActions(
    note: NoteSummary,
    collection: NoteCollection,
    a: NoteActions,
    onMove: (NoteSummary) -> Unit,
    onDelete: (NoteSummary) -> Unit,
    onRename: (NoteSummary) -> Unit,
    onSelect: (NoteSummary) -> Unit,
): List<ActionSpec> = when (collection) {
    NoteCollection.Trash -> listOf(
        ActionSpec(R.string.note_restore, Icons.Outlined.RestoreFromTrash) { a.restore(note.id) },
        ActionSpec(R.string.note_delete_forever, Icons.Outlined.DeleteForever) { onDelete(note) },
        ActionSpec(R.string.note_select, Icons.Outlined.CheckCircle) { onSelect(note) },
    )
    else -> buildList {
        add(ActionSpec(R.string.note_rename, Icons.Outlined.DriveFileRenameOutline) { onRename(note) })
        add(ActionSpec(if (note.isPinned) R.string.note_unpin else R.string.note_pin, Icons.Outlined.PushPin) { a.setPinned(note.id, !note.isPinned) })
        add(
            ActionSpec(
                if (note.isFavourite) R.string.note_unfavourite else R.string.note_favourite,
                if (note.isFavourite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
            ) { a.setFavourite(note.id, !note.isFavourite) },
        )
        add(ActionSpec(R.string.note_move, Icons.AutoMirrored.Outlined.DriveFileMove) { onMove(note) })
        add(ActionSpec(R.string.note_duplicate, Icons.Outlined.ContentCopy) { a.duplicate(note.id) })
        add(
            if (note.isArchived) {
                ActionSpec(R.string.note_unarchive, Icons.Outlined.Unarchive) { a.setArchived(note.id, false) }
            } else {
                ActionSpec(R.string.note_archive, Icons.Outlined.Archive) { a.setArchived(note.id, true) }
            },
        )
        add(ActionSpec(R.string.note_trash, Icons.Outlined.DeleteOutline) { a.trash(note.id) })
        add(ActionSpec(R.string.note_select, Icons.Outlined.CheckCircle) { onSelect(note) })
    }
}

@Composable
private fun EmptyCollection(collection: NoteCollection, modifier: Modifier) {
    val (title, body, icon) = when (collection) {
        NoteCollection.All -> Triple(R.string.home_empty_title, R.string.home_empty_body, Icons.Outlined.EditNote)
        NoteCollection.Favourites -> Triple(R.string.empty_favourites_title, R.string.empty_favourites_body, Icons.Outlined.StarOutline)
        NoteCollection.Archive -> Triple(R.string.empty_archive_title, R.string.empty_archive_body, Icons.Outlined.Inventory2)
        NoteCollection.Trash -> Triple(R.string.empty_trash_title, R.string.empty_trash_body, Icons.Outlined.DeleteOutline)
        else -> Triple(R.string.empty_collection_title, R.string.empty_collection_body, Icons.Outlined.Folder)
    }
    EmptyState(title = stringResource(title), body = stringResource(body), icon = icon, modifier = modifier)
}

@Composable
private fun SortMenu(current: NoteSort, onSort: (NoteSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.library_sort)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            NoteSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                when (sort) {
                                    NoteSort.MODIFIED -> R.string.sort_modified
                                    NoteSort.CREATED -> R.string.sort_created
                                    NoteSort.TITLE -> R.string.sort_title
                                },
                            ),
                        )
                    },
                    leadingIcon = { RadioButton(selected = sort == current, onClick = null) },
                    onClick = { open = false; onSort(sort) },
                )
            }
        }
    }
}

/** Less frequent things; creating and importing live in the + menu (owner brief §3). */
@Composable
private fun OverflowMenu(
    inTrash: Boolean,
    onEmptyTrash: () -> Unit,
    onManageNotebooks: () -> Unit,
    onManageTags: () -> Unit,
    onNewFolder: (() -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (inTrash) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_empty_trash)) },
                    leadingIcon = { Icon(Icons.Outlined.DeleteForever, null) },
                    onClick = { open = false; onEmptyTrash() },
                )
            }
            if (onNewFolder != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folder_new)) },
                    leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) },
                    onClick = { open = false; onNewFolder() },
                    modifier = Modifier.testTag(FolderTestTags.NEW_FOLDER + "_menu"),
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_manage_notebooks)) },
                leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                onClick = { open = false; onManageNotebooks() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_manage_tags)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
                onClick = { open = false; onManageTags() },
            )
        }
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
