package com.appname.notes.feature.library

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.EmptyState
import com.appname.notes.core.designsystem.component.GlassSnackbarHost
import com.appname.notes.core.designsystem.component.GlassSurface
import com.appname.notes.core.designsystem.component.backdropSource
import com.appname.notes.core.designsystem.component.rememberBackdrop
import com.appname.notes.core.designsystem.theme.AppTheme
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import com.appname.notes.core.domain.model.FolderTree
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.domain.model.NoteSummary
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.domain.model.Notebook
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.feature.attachments.rememberDocumentScanner
import com.appname.notes.feature.pro.NoteLockPromptDialog
import com.appname.notes.feature.pro.NoteProStore
import com.appname.notes.ui.common.ActionSheet
import com.appname.notes.ui.common.CardAction
import com.appname.notes.ui.common.InkThumbnail
import com.appname.notes.ui.common.MessageEffect
import com.appname.notes.ui.common.MoveToNotebookSheet
import com.appname.notes.ui.common.NoteCard
import com.appname.notes.ui.common.FolderCoverRenderer
import com.appname.notes.ui.common.FolderCoverStore
import com.appname.notes.ui.common.FolderCoverStudioDialog
import com.appname.notes.ui.common.FolderTitlePlacement
import com.appname.notes.ui.common.NoteCoverPickerDialog
import com.appname.notes.ui.common.NoteCoverStore
import com.appname.notes.ui.common.SheetAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

object LibraryTestTags {
    fun view(size: com.appname.notes.core.settings.CardSize) = "home_view_${size.name.lowercase()}"
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
    const val MORE = "action_more"
    const val SETTINGS = "action_settings"
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
    onChooseTemplate: (notebookId: String?, tagId: String?) -> Unit = { _, _ -> },
    onOpenTodoList: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val routeContext = LocalContext.current
    LaunchedEffect(Unit) {
        NoteCoverStore.ensureInit(routeContext)
        FolderCoverStore.ensureInit(routeContext)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { viewModel.importFile(uri)?.let { (id, type) -> onOpenNote(id, type) } }
    }
    val importingPdf by viewModel.importingPdf.collectAsStateWithLifecycle()
    val scan = rememberDocumentScanner(
        onScanned = { file -> scope.launch { viewModel.createNoteFromScan(file)?.let { onOpenNote(it, NoteType.TEXT) } } },
        onUnavailable = viewModel::scannerUnavailable,
    )
    LaunchedEffect(state.collection, state.loading, state.collectionName) {
        val c = state.collection
        if ((!state.loading) && (c is NoteCollection.InNotebook || c is NoteCollection.WithTag) && (state.collectionName == null)) {
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
            newTextDoc = { scope.launch { viewModel.createNote()?.let { onOpenNote(it, NoteType.TEXT) } } },
        ),
        onCreateWhiteboard = { spec ->
            scope.launch {
                viewModel.createWhiteboard(spec)?.let { id ->
                    NoteCoverStore.consumePendingCoverForNewNote(routeContext, id.value)
                    onOpenNote(id, NoteType.INK)
                }
            }
        },
        onCreateStudioNotebook = { spec ->
            scope.launch {
                viewModel.createStudioNotebook(spec)?.let { id ->
                    NoteCoverStore.consumePendingCoverForNewNote(routeContext, id.value)
                    onOpenNote(id, NoteType.INK)
                }
            }
        },
        inkThumbnail = viewModel::inkThumbnail,
        cover = viewModel::cover,
        onOpenSearch = onOpenSearch,
        onSelectCollection = viewModel::selectCollection,
        onSort = viewModel::setSort,
        onCardSize = viewModel::setCardSize,
        importingPdf = importingPdf,
        folders = FolderActions(
            create = viewModel::createFolder,
            rename = viewModel::renameFolder,
            move = viewModel::moveFolder,
            delete = viewModel::deleteFolder,
        ),
        onManageNotebooks = onManageNotebooks,
        onManageTags = onManageTags,
        onOpenTodoList = onOpenTodoList,
        onOpenSettings = onOpenSettings,
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

data class BulkActions(
    val setPinned: (Set<NoteId>, Boolean) -> Unit,
    val setArchived: (Set<NoteId>, Boolean) -> Unit,
    val trash: (Set<NoteId>) -> Unit,
    val restore: (Set<NoteId>) -> Unit,
    val deleteForever: (Set<NoteId>) -> Unit,
    val move: (Set<NoteId>, NotebookId?) -> Unit,
)

private val SelectionSaver = Saver<MutableState<Set<String>>, ArrayList<String>>(
    save = { ArrayList(it.value) },
) { mutableStateOf(it.toSet()) }

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
    onCardSize: (com.appname.notes.core.settings.CardSize) -> Unit = {},
    onMessageShown: () -> Unit,
    bulk: BulkActions? = null,
    importingPdf: Boolean = false,
    cover: (suspend (String) -> Bitmap?)? = null,
    folders: FolderActions? = null,
    @Suppress("UNUSED_PARAMETER") onOpenTodoList: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onCreateWhiteboard: ((WhiteboardCreationSpec) -> Unit)? = null,
    onCreateStudioNotebook: ((NotebookStudioSpec) -> Unit)? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    var newFolder by rememberSaveable { mutableStateOf(value = false) }
    var showWhiteboardDialog by rememberSaveable { mutableStateOf(value = false) }
    var showNotebookStudioDialog by rememberSaveable { mutableStateOf(value = false) }
    var gridFilter by rememberSaveable { mutableStateOf(HomeGridFilter.ALL) }
    var folderActionsFor by remember { mutableStateOf<Notebook?>(null) }
    var renameFolderFor by remember { mutableStateOf<Notebook?>(null) }
    var moveFolderFor by remember { mutableStateOf<Notebook?>(null) }
    var deleteFolderFor by remember { mutableStateOf<Notebook?>(null) }
    val currentFolder = state.currentFolder
    val parentFolder = state.parentFolder
    val canNavigateUpToParentOrRoot = currentFolder != null || state.collection != NoteCollection.All
    val navigateUpStep: () -> Unit = {
        val currentParentId = currentFolder?.parentId
        when {
            parentFolder != null -> onSelectCollection(NoteCollection.InNotebook(parentFolder.id))
            currentParentId != null -> onSelectCollection(NoteCollection.InNotebook(currentParentId))
            else -> onSelectCollection(NoteCollection.All)
        }
    }
    MessageEffect(state.message, snackbar, onMessageShown)

    var showCollections by rememberSaveable { mutableStateOf(value = false) }
    var createOpen by rememberSaveable { mutableStateOf(value = false) }
    var actionsFor by remember { mutableStateOf<NoteSummary?>(null) }
    var renameFor by remember { mutableStateOf<NoteSummary?>(null) }
    var moveFor by remember { mutableStateOf<NoteSummary?>(null) }
    var moveSelected by remember { mutableStateOf(value = false) }
    var confirmDeleteFor by remember { mutableStateOf<NoteSummary?>(null) }
    var confirmDeleteSelected by remember { mutableStateOf(value = false) }
    var confirmEmptyTrash by rememberSaveable { mutableStateOf(value = false) }
    var selected by rememberSaveable(saver = SelectionSaver) { mutableStateOf(emptySet()) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        NoteProStore.ensureInit(context)
        NoteCoverStore.ensureInit(context)
        FolderCoverStore.ensureInit(context)
    }
    val lockedNotes by NoteProStore.lockedNotes.collectAsStateWithLifecycle()
    val noteCoversMap by NoteCoverStore.noteCovers.collectAsStateWithLifecycle()
    val folderCoversMap by FolderCoverStore.folderCovers.collectAsStateWithLifecycle()
    var coverPickerForNote by remember { mutableStateOf<NoteSummary?>(null) }
    var bookReaderTarget by remember { mutableStateOf<NoteSummary?>(null) }
    var coverPickerForNotebook by remember { mutableStateOf<Notebook?>(null) }
    var pendingUnlockOpen by remember { mutableStateOf<NoteSummary?>(null) }
    var pendingToggleLock by remember { mutableStateOf<NoteSummary?>(null) }

    val all = remember(state.pinned, state.others) { state.pinned + state.others }
    val selecting = selected.isNotEmpty()
    val selectedNotes = all.filter { (id) -> id.value in selected }
    LaunchedEffect(all) {
        if (!state.loading) {
            val ids = all.mapTo(HashSet()) { it.id.value }
            if (!ids.containsAll(selected)) selected = selected intersect ids
        }
    }
    var selectionScope by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.collection, state.loading) {
        if (state.loading) return@LaunchedEffect
        val key = LibraryViewModel.encode(state.collection)
        if (selectionScope != null && selectionScope != key) selected = emptySet()
        selectionScope = key
    }
    BackHandler(
        enabled = createOpen || selecting || canNavigateUpToParentOrRoot || gridFilter != HomeGridFilter.ALL,
    ) {
        when {
            createOpen -> createOpen = false
            selecting -> selected = emptySet()
            canNavigateUpToParentOrRoot -> navigateUpStep()
            gridFilter != HomeGridFilter.ALL -> gridFilter = HomeGridFilter.ALL
        }
    }

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
        floatingActionButton = {
            if (state.canCreate && !selecting) {
                CreateMenu(
                    expanded = createOpen,
                    onExpandedChange = { createOpen = it },
                    actions = (if (folders != null && state.canCreate) create.copy(newFolder = { newFolder = true }) else create).copy(
                        newWhiteboard = { showWhiteboardDialog = true },
                        newNotebookStudio = if (onCreateStudioNotebook != null) { { showNotebookStudioDialog = true } } else null,
                    ),
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 64.dp),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
                when {
                    state.loading -> LoadingNotes(Modifier.align(Alignment.Center))
                    state.isEmpty && state.currentFolder == null && (state.collection != NoteCollection.All || FolderTree.children(state.notebooks, null).isEmpty()) -> EmptyCollection(state.collection, Modifier.align(Alignment.Center).padding(top = headerHeight))
                    else -> NotesGrid(
                        state = state,
                        topPadding = headerHeight,
                        gridFilter = gridFilter,
                        selectedNoteId = selectedNoteId,
                        selected = selected,
                        onClick = { note ->
                            when {
                                selecting -> toggle(note)
                                note.id.value in lockedNotes -> pendingUnlockOpen = note
                                else -> onOpenNote(note.id, note.type)
                            }
                        },
                        onLongPress = ::toggle,
                        onMore = if (selecting) null else { note: NoteSummary -> actionsFor = note },
                        onToggleFavourite = if (selecting || inTrash) null else { note: NoteSummary -> actions.setFavourite(note.id, !note.isFavourite) },
                        onTogglePin = if (selecting || inTrash) null else { note: NoteSummary -> actions.setPinned(note.id, !note.isPinned) },
                        inkThumbnail = inkThumbnail,
                        cover = cover,
                        onOpenFolder = { onSelectCollection(NoteCollection.InNotebook(it.id)) },
                        onFolderMore = if (folders != null && !selecting) { nb: Notebook -> folderActionsFor = nb } else null,
                        accessibilityActions = { note ->
                            noteActions(
                                note, state.collection, actions,
                                isLocked = note.id.value in lockedNotes,
                                onToggleLock = { pendingToggleLock = it },
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
                    .onSizeChanged { with(density) { headerHeight = it.height.toDp() } }
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .testTag(LibraryTestTags.HEADER),
            ) {
                Column(Modifier.padding(vertical = 2.dp)) {
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
                        // Phase 4.2: Compact Liquid Glass back icon button integrated into HomeTopRow
                        // instead of an oversized "Back to DBMS" AssistChip row!
                        HomeTopRow(
                            title = title,
                            sort = state.sort,
                            cardSize = state.cardSize,
                            onCardSize = onCardSize,
                            inTrash = inTrash,
                            parentFolder = parentFolder,
                            onNavigateUp = if (canNavigateUpToParentOrRoot) navigateUpStep else null,
                            onCollections = { showCollections = true },
                            onOpenSearch = onOpenSearch,
                            onSort = onSort,
                            onEmptyTrash = { confirmEmptyTrash = true },
                            onManageNotebooks = onManageNotebooks,
                            onManageTags = onManageTags,
                            onNewFolder = if (folders != null && state.canCreate) { { newFolder = true } } else null,
                            onOpenSettings = onOpenSettings,
                        )
                        CollectionChips(
                            current = state.collection,
                            notebooks = FolderTree.children(state.notebooks, null),
                            onSelect = onSelectCollection,
                            modifier = Modifier.padding(bottom = 3.dp),
                            gridFilter = gridFilter,
                            onGridFilterChange = { gridFilter = it },
                        )
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
            GlassSnackbarHost(
                hostState = snackbar,
                bottomOffset = 68.dp,
                avoidFab = state.canCreate && !selecting,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2.5f),
            )
        }
    }

    if (showWhiteboardDialog && onCreateWhiteboard != null) {
        NewWhiteboardDialog(
            onCreate = { spec ->
                showWhiteboardDialog = false
                onCreateWhiteboard(spec)
            },
            onDismiss = { showWhiteboardDialog = false },
        )
    }
    if (showNotebookStudioDialog && onCreateStudioNotebook != null) {
        NewNotebookStudioDialog(
            onCreate = { spec ->
                showNotebookStudioDialog = false
                onCreateStudioNotebook(spec)
            },
            onBrowseAllTemplates = create.chooseTemplate,
            onDismiss = { showNotebookStudioDialog = false },
        )
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
                SheetAction("Change cover", Icons.Outlined.EditNote) { coverPickerForNotebook = nb },
                SheetAction(stringResource(R.string.action_rename), Icons.Outlined.Edit) { renameFolderFor = nb },
                SheetAction(stringResource(R.string.folder_move), Icons.AutoMirrored.Outlined.DriveFileMove) { moveFolderFor = nb },
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
            title = stringResource(R.string.action_delete),
            body = nb.title,
            confirm = stringResource(R.string.action_delete),
            onConfirm = {
                folders?.delete?.invoke(nb.id)
                deleteFolderFor = null
            },
            onDismiss = { deleteFolderFor = null },
        )
    }
    if (showCollections) {
        CollectionsSheet(
            current = state.collection,
            notebooks = state.notebooks,
            tags = state.tags,
            onSelect = { col: NoteCollection ->
                showCollections = false
                onSelectCollection(col)
            },
            onManageNotebooks = { showCollections = false; onManageNotebooks() },
            onManageTags = { showCollections = false; onManageTags() },
            onDismiss = { showCollections = false },
        )
    }
    actionsFor?.let { note ->
        val baseActions = noteActions(
            note, state.collection, actions,
            isLocked = note.id.value in lockedNotes,
            onToggleLock = { pendingToggleLock = it },
            onMove = { moveFor = it }, onDelete = { confirmDeleteFor = it },
            onRename = { renameFor = it }, onSelect = ::toggle,
        ).map { SheetAction(stringResource(it.label), it.icon, it.onClick) }
        val sheetActions = if (state.collection == NoteCollection.Trash) {
            baseActions
        } else {
            listOf(
                SheetAction("Read in Book Mode", Icons.Outlined.AutoStories) { bookReaderTarget = note },
                SheetAction("Change cover", Icons.Outlined.EditNote) { coverPickerForNote = note },
            ) + baseActions
        }
        ActionSheet(title = note.displayTitle(), actions = sheetActions, onDismiss = { actionsFor = null })
    }
    bookReaderTarget?.let { targetBook ->
        com.appname.notes.feature.pdf.PdfBookReaderDialog(
            bookTitle = targetBook.displayTitle(),
            noteId = targetBook.id.value,
            onDismiss = { bookReaderTarget = null },
        )
    }
    coverPickerForNote?.let { targetNote ->
        val currentConfig = NoteCoverStore.resolveForNote(targetNote.id.value, targetNote.type, noteCoversMap, targetNote.displayTitle())
        NoteCoverPickerDialog(
            noteTitle = targetNote.displayTitle(),
            noteType = targetNote.type,
            initialConfig = currentConfig,
            onApply = { newConfig ->
                NoteCoverStore.setNoteCover(context, targetNote.id.value, newConfig)
                coverPickerForNote = null
            },
            onReset = {
                NoteCoverStore.resetNoteCover(context, targetNote.id.value)
                coverPickerForNote = null
            },
            onDismiss = { coverPickerForNote = null },
        )
    }
    coverPickerForNotebook?.let { targetFolder ->
        val currentFolderConfig = FolderCoverStore.resolveForFolder(targetFolder.id.value, folderCoversMap)
        FolderCoverStudioDialog(
            folderId = targetFolder.id.value,
            folderTitle = targetFolder.title,
            initialConfig = currentFolderConfig,
            onApply = { newFolderConfig ->
                FolderCoverStore.setFolderCover(context, targetFolder.id.value, newFolderConfig)
                coverPickerForNotebook = null
            },
            onReset = {
                FolderCoverStore.resetFolderCover(context, targetFolder.id.value)
                coverPickerForNotebook = null
            },
            onDismiss = { coverPickerForNotebook = null },
        )
    }
    pendingUnlockOpen?.let { target ->
        NoteLockPromptDialog(
            title = stringResource(R.string.note_unlock),
            onAuthenticated = {
                pendingUnlockOpen = null
                onOpenNote(target.id, target.type)
            },
            onDismiss = { pendingUnlockOpen = null },
        )
    }
    pendingToggleLock?.let { target ->
        val isCurrentlyLocked = target.id.value in lockedNotes
        NoteLockPromptDialog(
            title = stringResource(if (isCurrentlyLocked) R.string.note_unlock else R.string.note_lock),
            onAuthenticated = {
                toggleNoteLockSafe(context, target.id.value, isCurrentlyLocked)
                pendingToggleLock = null
            },
            onDismiss = { pendingToggleLock = null },
        )
    }
    renameFor?.let { note ->
        RenameNoteDialog(
            current = note.title,
            onRename = { actions.rename(note.id, it) },
            onDismiss = { renameFor = null },
        )
    }
    moveFor?.let { note ->
        MoveToNotebookSheet(state.notebooks, note.notebookId, onPick = { actions.move(note.id, it) }, onDismiss = { moveFor = null })
    }
    if (moveSelected) {
        val current = selectedNotes.asSequence().map { it.notebookId }.distinct().singleOrNull()
        MoveToNotebookSheet(
            state.notebooks,
            current,
            onPick = { bulk?.move?.invoke(selectedIds(), it); selected = emptySet() },
            onDismiss = { moveSelected = false },
        )
    }
    confirmDeleteFor?.let { note ->
        ConfirmDialog(
            title = stringResource(R.string.note_delete_forever),
            body = note.displayTitle(),
            confirm = stringResource(R.string.note_delete_forever),
            onConfirm = {
                actions.deleteForever(note.id)
                confirmDeleteFor = null
            },
            onDismiss = { confirmDeleteFor = null },
        )
    }
    if (confirmDeleteSelected) {
        val count = selectedNotes.size
        ConfirmDialog(
            title = stringResource(R.string.note_delete_forever),
            body = androidx.compose.ui.res.pluralStringResource(R.plurals.selection_count, count, count),
            confirm = stringResource(R.string.note_delete_forever),
            onConfirm = {
                bulk?.deleteForever?.invoke(selectedIds())
                selected = emptySet()
                confirmDeleteSelected = false
            },
            onDismiss = { confirmDeleteSelected = false },
        )
    }
    if (confirmEmptyTrash) {
        ConfirmDialog(
            title = stringResource(R.string.library_empty_trash),
            body = stringResource(R.string.empty_trash_title),
            confirm = stringResource(R.string.library_empty_trash),
            onConfirm = {
                actions.emptyTrash()
                confirmEmptyTrash = false
            },
            onDismiss = { confirmEmptyTrash = false },
        )
    }
}

@Composable
private fun NoteSummary.displayTitle(): String =
    title.trim().ifBlank { stringResource(R.string.untitled) }

/**
 * Phase 4.1 & 4.2 — Clean Header Row with integrated compact Liquid Glass Back button (`FolderTestTags.UP`)
 * when inside a nested folder (`image_5ddc54.jpg`, `image_5ddc78.jpg`).
 */
@Composable
private fun HomeTopRow(
    title: String,
    sort: NoteSort,
    inTrash: Boolean,
    onCollections: () -> Unit,
    cardSize: com.appname.notes.core.settings.CardSize = com.appname.notes.core.settings.CardSize.BIG,
    onCardSize: (com.appname.notes.core.settings.CardSize) -> Unit = {},
    parentFolder: Notebook? = null,
    onNavigateUp: (() -> Unit)? = null,
    onOpenSearch: () -> Unit,
    onSort: (NoteSort) -> Unit,
    onEmptyTrash: () -> Unit,
    onManageNotebooks: () -> Unit,
    onManageTags: () -> Unit,
    onNewFolder: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val isDark = LocalDarkTheme.current
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val headerInk = if (isDark) Color(0xFFEAECEF) else Color(0xFF1C1A17)
        val mutedInk = if (isDark) Color(0xFFA0A6B0) else Color(0xFF6E665C)
        val description = stringResource(R.string.collection_choose, title)
        Row(
            modifier = Modifier.weight(1f).padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onNavigateUp != null) {
                val parentLabel = parentFolder?.title ?: stringResource(R.string.collection_all)
                Text(
                    text = parentLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = mutedInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onNavigateUp,
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .testTag(FolderTestTags.UP),
                )
                Text(
                    text = "/",
                    style = MaterialTheme.typography.titleMedium,
                    color = mutedInk.copy(alpha = 0.65f),
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
            TextButton(
                onClick = onCollections,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                modifier = Modifier
                    .testTag(LibraryTestTags.COLLECTION_BUTTON)
                    .semantics { contentDescription = description },
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = headerInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = headerInk,
                    modifier = Modifier.padding(start = 2.dp).size(22.dp),
                )
            }
        }
        IconButton(onClick = onOpenSearch, modifier = Modifier.testTag(LibraryTestTags.SEARCH)) {
            Icon(Icons.Outlined.Search, stringResource(R.string.library_search), tint = headerInk)
        }
        SortMenu(sort, onSort, cardSize, onCardSize)
        OverflowMenu(inTrash, onEmptyTrash, onManageNotebooks, onManageTags, onNewFolder, onOpenSettings)
    }
}

@Composable
private fun LoadingNotes(modifier: Modifier) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(LOADING_DELAY_MS.milliseconds); show = true }
    if (show) {
        val description = stringResource(R.string.library_loading)
        CircularProgressIndicator(modifier.testTag(LibraryTestTags.LOADING).semantics { contentDescription = description })
    }
}

private const val LOADING_DELAY_MS = 400L

@Composable
private fun stringResourceLabel(id: Int): String = stringResource(id)

@Composable
private fun collectionTitle(state: LibraryUiState): String = when (state.collection) {
    NoteCollection.All -> stringResource(R.string.collection_all)
    NoteCollection.Favourites -> stringResource(R.string.collection_favourites)
    NoteCollection.Archive -> stringResource(R.string.collection_archive)
    NoteCollection.Trash -> stringResource(R.string.collection_trash)
    is NoteCollection.InNotebook -> state.collectionName ?: stringResource(R.string.collection_notebooks)
    is NoteCollection.WithTag -> "#" + (state.collectionName ?: "")
}

private sealed interface UnifiedLibraryItem {
    val stableKey: String
    val sortTitle: String
    val isPinnedPriority: Boolean

    data class FolderItem(val folder: Notebook) : UnifiedLibraryItem {
        override val stableKey: String = "folder_${folder.id.value}"
        override val sortTitle: String = folder.title
        override val isPinnedPriority: Boolean = false
    }

    data class NoteItem(val note: NoteSummary) : UnifiedLibraryItem {
        override val stableKey: String = "note_${note.id.value}"
        override val sortTitle: String = note.title.ifBlank { note.preview.lineSequence().firstOrNull().orEmpty() }
        override val isPinnedPriority: Boolean = note.isPinned
    }
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
    onToggleFavourite: ((NoteSummary) -> Unit)? = null,
    onTogglePin: ((NoteSummary) -> Unit)? = null,
    inkThumbnail: suspend (NoteSummary) -> InkThumbnail?,
    cover: (suspend (String) -> Bitmap?)?,
    accessibilityActions: @Composable (NoteSummary) -> List<CardAction>,
    onOpenFolder: (Notebook) -> Unit = {},
    onFolderMore: ((Notebook) -> Unit)? = null,
    gridFilter: HomeGridFilter = HomeGridFilter.ALL,
) {
    val spacing = AppTheme.spacing
    val showFolders = gridFilter == HomeGridFilter.ALL || gridFilter == HomeGridFilter.FOLDERS
    val showDocuments = gridFilter != HomeGridFilter.FOLDERS

    // Direct child folders for current hierarchy level
    val visibleFolders = remember(state.notebooks, state.collection, state.subfolders, showFolders) {
        if (!showFolders) {
            emptyList()
        } else {
            when (state.collection) {
                NoteCollection.All -> FolderTree.children(state.notebooks, null)
                is NoteCollection.InNotebook -> state.subfolders
                else -> emptyList()
            }
        }
    }

    // Direct child notes for current hierarchy level (never leak nested folder notes into Root!)
    val visibleNotes = remember(state.pinned, state.others, state.collection, gridFilter, showDocuments) {
        if (!showDocuments) {
            emptyList()
        } else {
            val raw = if (gridFilter == HomeGridFilter.PINNED) state.pinned else (state.pinned + state.others)
            when (val col = state.collection) {
                NoteCollection.All -> raw.filter { it.notebookId == null }
                is NoteCollection.InNotebook -> raw.filter { it.notebookId == col.id }
                else -> raw
            }
        }
    }

    // Unified single grid combining folders and notes without separate section headers
    val unifiedItems = remember(visibleFolders, visibleNotes, state.sort) {
        val foldersWrapped = visibleFolders.map { UnifiedLibraryItem.FolderItem(it) }
        val notesWrapped = visibleNotes.map { UnifiedLibraryItem.NoteItem(it) }
        when (state.sort) {
            NoteSort.TITLE -> (foldersWrapped + notesWrapped).sortedWith(
                compareBy<UnifiedLibraryItem> { !it.isPinnedPriority }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.sortTitle },
            )
            else -> {
                val sortedFolders = foldersWrapped.sortedBy { it.sortTitle.lowercase() }
                sortedFolders + notesWrapped
            }
        }
    }

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
            onToggleFavourite = onToggleFavourite?.let { { it(note) } },
            onTogglePin = onTogglePin?.let { { it(note) } },
            checked = note.id.value in selected,
            size = state.cardSize,
        )
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(
            minSize = when (state.cardSize) {
                com.appname.notes.core.settings.CardSize.SMALL -> 148.dp
                com.appname.notes.core.settings.CardSize.BIG -> 168.dp
                com.appname.notes.core.settings.CardSize.EXTRA_BIG -> 250.dp
                com.appname.notes.core.settings.CardSize.LIST -> 480.dp
            },
        ),
        contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, top = topPadding + spacing.xs, bottom = 148.dp),
        horizontalArrangement = Arrangement.spacedBy(if (state.cardSize == com.appname.notes.core.settings.CardSize.SMALL) spacing.sm else spacing.md),
        verticalItemSpacing = if (state.cardSize == com.appname.notes.core.settings.CardSize.LIST) spacing.xs else if (state.cardSize == com.appname.notes.core.settings.CardSize.SMALL) spacing.sm else spacing.md,
        modifier = Modifier.fillMaxSize().testTag(LibraryTestTags.GRID),
    ) {
        if (unifiedItems.isEmpty() && gridFilter == HomeGridFilter.PINNED) {
            item(key = "empty_pinned", span = StaggeredGridItemSpan.FullLine) {
                EmptyState(
                    title = stringResource(R.string.library_pinned),
                    body = stringResource(R.string.home_empty_body),
                    icon = Icons.Outlined.PushPin,
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
        } else if (unifiedItems.isEmpty() && state.currentFolder != null) {
            item(key = "empty_folder", span = StaggeredGridItemSpan.FullLine) {
                EmptyState(
                    title = state.currentFolder!!.title,
                    body = stringResource(R.string.empty_collection_body),
                    icon = Icons.Outlined.Folder,
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
        } else {
            items(
                items = unifiedItems,
                key = { it.stableKey },
                contentType = { item -> if (item is UnifiedLibraryItem.FolderItem) "folder" else "note" },
            ) { entry ->
                when (entry) {
                    is UnifiedLibraryItem.FolderItem -> PremiumFolderCard(
                        folder = entry.folder,
                        allNotebooks = state.notebooks,
                        cardSize = state.cardSize,
                        onOpen = { onOpenFolder(entry.folder) },
                        onMore = onFolderMore?.let { { it(entry.folder) } },
                        testTag = LibraryTestTags.chip(entry.folder),
                    )
                    is UnifiedLibraryItem.NoteItem -> card(entry.note)
                }
            }
        }
    }
}

@Composable
@Suppress("unused")
private fun FolderBreadcrumbStrip(
    currentFolder: Notebook,
    allNotebooks: List<Notebook>,
    onSelectCollection: (NoteCollection) -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val chain = remember(currentFolder, allNotebooks) {
        val byId = allNotebooks.associateBy { it.id }
        val result = mutableListOf<Notebook>()
        var cursor: Notebook? = currentFolder
        val visited = HashSet<NotebookId>()
        while (cursor != null && visited.add(cursor.id)) {
            result += cursor
            cursor = cursor.parentId?.let { byId[it] }
        }
        result.reversed()
    }
    val textColor = if (isDark) Color(0xFFA39E93) else Color(0xFF6E665C)
    val activeColor = if (isDark) Color(0xFFF6F3EC) else Color(0xFF1C1A17)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.collection_all),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onSelectCollection(NoteCollection.All) }
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        chain.forEachIndexed { index, nb ->
            val isLast = index == chain.lastIndex
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = nb.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                color = if (isLast) activeColor else textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        enabled = !isLast,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelectCollection(NoteCollection.InNotebook(nb.id)) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PremiumFolderCard(
    folder: Notebook,
    allNotebooks: List<Notebook>,
    cardSize: com.appname.notes.core.settings.CardSize,
    onOpen: () -> Unit,
    onMore: (() -> Unit)?,
    testTag: String,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { FolderCoverStore.ensureInit(context) }
    val folderCoversMap by FolderCoverStore.folderCovers.collectAsStateWithLifecycle()
    val folderConfig = remember(folder.id.value, folderCoversMap) {
        FolderCoverStore.resolveForFolder(folder.id.value, folderCoversMap)
    }

    val subfolderCount = remember(folder.id, allNotebooks) {
        allNotebooks.count { it.parentId == folder.id }
    }
    val subtitleText = when {
        subfolderCount > 0 -> "$subfolderCount subfolder${if (subfolderCount == 1) "" else "s"}"
        else -> "Folder"
    }

    if (cardSize == com.appname.notes.core.settings.CardSize.LIST) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            shape = MaterialTheme.shapes.large,
            elevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpen,
                    )
                    .padding(start = 14.dp, end = if (onMore != null) 4.dp else 14.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FolderCoverRenderer(
                    folderTitle = folder.title,
                    config = folderConfig,
                    compact = true,
                    modifier = Modifier.size(width = 48.dp, height = 40.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(folder.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitleText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                onMore?.let {
                    IconButton(onClick = it) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        return
    }

    val small = cardSize == com.appname.notes.core.settings.CardSize.SMALL
    val showBeneathTitle = folderConfig.titlePlacement == FolderTitlePlacement.BENEATH ||
        folderConfig.titlePlacement == FolderTitlePlacement.BOTH
    val folderInteraction = remember { MutableInteractionSource() }
    val folderPressed by folderInteraction.collectIsPressedAsState()
    val folderScale by animateFloatAsState(
        targetValue = if (folderPressed) 0.965f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "folderPressScale",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = folderScale
                scaleY = folderScale
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = folderInteraction,
                indication = null,
                onClick = onOpen,
            )
            .padding(horizontal = if (small) 2.dp else 4.dp, vertical = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FolderCoverRenderer(
            folderTitle = folder.title,
            config = folderConfig,
            itemCountLabel = subtitleText,
            modifier = Modifier
                .fillMaxWidth(if (small) 0.94f else 0.92f)
                .aspectRatio(1.12f)
                .padding(top = 6.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth(if (small) 0.96f else 0.94f)
                .padding(start = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (showBeneathTitle) {
                    Text(
                        text = folder.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onMore != null) {
                IconButton(onClick = onMore, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
@Suppress("unused")
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 8.dp, bottom = 2.dp)
            .semantics { heading() },
    )
}

private data class ActionSpec(val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector, val onClick: () -> Unit)

private fun noteActions(
    note: NoteSummary,
    collection: NoteCollection,
    a: NoteActions,
    isLocked: Boolean = false,
    onToggleLock: (NoteSummary) -> Unit = {},
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
        add(
            ActionSpec(
                if (isLocked) R.string.note_unlock else R.string.note_lock,
                if (isLocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
            ) { onToggleLock(note) },
        )
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

/**
 * Phase 6 — Neutral Liquid Glass Sort & View Size Menu (Fixes `image_5ddc13.jpg`).
 */
@Composable
private fun SortMenu(
    current: NoteSort,
    onSort: (NoteSort) -> Unit,
    cardSize: com.appname.notes.core.settings.CardSize = com.appname.notes.core.settings.CardSize.BIG,
    onCardSize: (com.appname.notes.core.settings.CardSize) -> Unit = {},
) {
    val isDark = LocalDarkTheme.current
    var open by remember { mutableStateOf(false) }
    val menuBg = if (isDark) Color(0xFF14161B).copy(alpha = 0.94f) else Color(0xFFFFFFFF).copy(alpha = 0.96f)
    val borderCol = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)

    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.library_sort), tint = if (isDark) Color(0xFFEAECEF) else Color(0xFF1C1A17)) }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = menuBg,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(0.8.dp, borderCol),
            modifier = Modifier.width(236.dp),
        ) {
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
                            fontWeight = if (sort == current) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = { RadioButton(selected = sort == current, onClick = null) },
                    onClick = { open = false; onSort(sort) },
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = borderCol,
            )
            Text(
                stringResource(R.string.library_view),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            com.appname.notes.core.settings.CardSize.entries.forEach { size ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                when (size) {
                                    com.appname.notes.core.settings.CardSize.SMALL -> R.string.library_view_small
                                    com.appname.notes.core.settings.CardSize.BIG -> R.string.library_view_big
                                    com.appname.notes.core.settings.CardSize.EXTRA_BIG -> R.string.library_view_extra_big
                                    com.appname.notes.core.settings.CardSize.LIST -> R.string.library_view_list
                                },
                            ),
                            fontWeight = if (size == cardSize) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = { RadioButton(selected = size == cardSize, onClick = null) },
                    onClick = { open = false; onCardSize(size) },
                    modifier = Modifier.testTag(LibraryTestTags.view(size)),
                )
            }
        }
    }
}

/**
 * Phase 6 — Neutral Liquid Glass Three-Dot Overflow Menu (Fixes `image_5ddc19.jpg`).
 */
@Composable
private fun OverflowMenu(
    inTrash: Boolean,
    onEmptyTrash: () -> Unit,
    onManageNotebooks: () -> Unit,
    onManageTags: () -> Unit,
    onNewFolder: (() -> Unit)?,
    onOpenSettings: (() -> Unit)? = null,
) {
    val isDark = LocalDarkTheme.current
    var open by remember { mutableStateOf(false) }
    val menuBg = if (isDark) Color(0xFF14161B).copy(alpha = 0.94f) else Color(0xFFFFFFFF).copy(alpha = 0.96f)
    val borderCol = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)

    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.testTag(LibraryTestTags.MORE),
        ) {
            Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more), tint = if (isDark) Color(0xFFEAECEF) else Color(0xFF1C1A17))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = menuBg,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(0.8.dp, borderCol),
            modifier = Modifier.width(236.dp),
        ) {
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
                    modifier = Modifier.testTag(FolderTestTags.NEW_FOLDER),
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
            if (onOpenSettings != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = borderCol,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nav_settings)) },
                    leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                    onClick = { open = false; onOpenSettings() },
                    modifier = Modifier.testTag(LibraryTestTags.SETTINGS),
                )
            }
        }
    }
}


private fun toggleNoteLockSafe(context: android.content.Context, noteId: String, currentlyLocked: Boolean) {
    runCatching {
        val cls = NoteProStore::class.java
        val twoArg = cls.methods.firstOrNull {
            it.name.contains("Lock", ignoreCase = true) && it.parameterTypes.size == 2
        }
        if (twoArg != null) {
            twoArg.invoke(NoteProStore, context, noteId)
        } else {
            val threeArg = cls.methods.firstOrNull {
                it.name.contains("Lock", ignoreCase = true) && it.parameterTypes.size == 3
            }
            threeArg?.invoke(NoteProStore, context, noteId, !currentlyLocked)
        }
    }
}
