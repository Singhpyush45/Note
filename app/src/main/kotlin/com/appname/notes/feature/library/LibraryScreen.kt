package com.appname.notes.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.R
import androidx.compose.foundation.layout.Column
import com.appname.notes.ui.common.InkThumbnail
import com.appname.notes.feature.ink.InkIcons
import androidx.compose.material3.SmallFloatingActionButton
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.designsystem.component.EmptyState
import com.appname.notes.core.designsystem.theme.AppTheme
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteSort
import com.appname.notes.core.domain.model.NoteSummary
import com.appname.notes.ui.common.ActionSheet
import com.appname.notes.ui.common.CardAction
import com.appname.notes.ui.common.MessageEffect
import com.appname.notes.ui.common.MoveToNotebookSheet
import com.appname.notes.ui.common.NoteCard
import com.appname.notes.ui.common.SheetAction
import kotlinx.coroutines.launch

object LibraryTestTags {
    const val SCREEN = "screen_home"
    const val NEW_NOTE = "fab_new_note"
    const val NEW_INK_NOTE = "fab_new_ink_note"
    const val COLLECTION_BUTTON = "collection_button"
    const val SEARCH = "action_search"
    const val GRID = "notes_grid"
}

@Composable
fun LibraryRoute(
    onOpenNote: (NoteId, NoteType) -> Unit,
    onOpenSearch: () -> Unit,
    onManageNotebooks: () -> Unit,
    onManageTags: () -> Unit,
    selectedNoteId: String?,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { viewModel.import(uri)?.let { onOpenNote(it, NoteType.TEXT) } }
    }
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
        onNewNote = { scope.launch { viewModel.createNote()?.let { onOpenNote(it, NoteType.TEXT) } } },
        onNewInkNote = { scope.launch { viewModel.createInkNote()?.let { onOpenNote(it, NoteType.INK) } } },
        inkThumbnail = viewModel::inkThumbnail,
        onOpenSearch = onOpenSearch,
        onSelectCollection = viewModel::selectCollection,
        onSort = viewModel::setSort,
        onImport = { importLauncher.launch(arrayOf("text/markdown", "text/x-markdown", "text/plain", "application/octet-stream")) },
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
    val move: (NoteId, com.appname.notes.core.domain.model.NotebookId?) -> Unit,
    val emptyTrash: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    selectedNoteId: String?,
    onOpenNote: (NoteId, NoteType) -> Unit,
    onNewNote: () -> Unit,
    onNewInkNote: () -> Unit,
    inkThumbnail: suspend (NoteSummary) -> InkThumbnail?,
    onOpenSearch: () -> Unit,
    onSelectCollection: (NoteCollection) -> Unit,
    onSort: (NoteSort) -> Unit,
    onImport: () -> Unit,
    onManageNotebooks: () -> Unit,
    onManageTags: () -> Unit,
    actions: NoteActions,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, onMessageShown)

    var showCollections by rememberSaveable { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<NoteSummary?>(null) }
    var moveFor by remember { mutableStateOf<NoteSummary?>(null) }
    var confirmDeleteFor by remember { mutableStateOf<NoteSummary?>(null) }
    var confirmEmptyTrash by rememberSaveable { mutableStateOf(false) }

    val title = collectionTitle(state)
    val inTrash = state.collection == NoteCollection.Trash

    Scaffold(
        modifier = Modifier.testTag(LibraryTestTags.SCREEN),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    val description = stringResource(R.string.collection_choose, title)
                    TextButton(
                        onClick = { showCollections = true },
                        modifier = Modifier
                            .testTag(LibraryTestTags.COLLECTION_BUTTON)
                            .semantics { contentDescription = description },
                    ) {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch, modifier = Modifier.testTag(LibraryTestTags.SEARCH)) {
                        Icon(Icons.Outlined.Search, stringResource(R.string.library_search))
                    }
                    SortMenu(state.sort, onSort)
                    OverflowMenu(
                        inTrash = inTrash,
                        onImport = onImport,
                        onEmptyTrash = { confirmEmptyTrash = true },
                        onManageNotebooks = onManageNotebooks,
                        onManageTags = onManageTags,
                    )
                },
            )
        },
        floatingActionButton = {
            if (state.canCreate) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(
                        onClick = onNewInkNote,
                        modifier = Modifier.testTag(LibraryTestTags.NEW_INK_NOTE),
                    ) {
                        Icon(InkIcons.Handwriting, contentDescription = stringResource(R.string.ink_new_note))
                    }
                    ExtendedFloatingActionButton(
                        onClick = onNewNote,
                        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.library_new_note)) },
                        modifier = Modifier.testTag(LibraryTestTags.NEW_NOTE),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Unit
                state.isEmpty -> EmptyCollection(state.collection, Modifier.align(Alignment.Center))
                else -> NotesGrid(
                    state = state,
                    selectedNoteId = selectedNoteId,
                    onOpenNote = onOpenNote,
                    inkThumbnail = inkThumbnail,
                    onLongPress = { actionsFor = it },
                    accessibilityActions = { note ->
                        noteActions(note, state.collection, actions, onMove = { moveFor = it }, onDelete = { confirmDeleteFor = it })
                            .map { CardAction(stringResourceLabel(it.label), it.onClick) }
                    },
                )
            }
        }
    }

    if (showCollections) {
        CollectionSheet(state, onSelect = { showCollections = false; onSelectCollection(it) }, onDismiss = { showCollections = false },
            onManageNotebooks = { showCollections = false; onManageNotebooks() }, onManageTags = { showCollections = false; onManageTags() })
    }
    actionsFor?.let { note ->
        ActionSheet(
            title = note.title.ifBlank { note.preview.lineSequence().firstOrNull().orEmpty() }.ifBlank { stringResource(R.string.untitled) },
            actions = noteActions(note, state.collection, actions, onMove = { moveFor = it }, onDelete = { confirmDeleteFor = it })
                .map { SheetAction(stringResource(it.label), it.icon, it.onClick) },
            onDismiss = { actionsFor = null },
        )
    }
    moveFor?.let { note ->
        MoveToNotebookSheet(state.notebooks, note.notebookId, onPick = { actions.move(note.id, it) }, onDismiss = { moveFor = null })
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
    selectedNoteId: String?,
    onOpenNote: (NoteId, NoteType) -> Unit,
    inkThumbnail: suspend (NoteSummary) -> InkThumbnail?,
    onLongPress: (NoteSummary) -> Unit,
    accessibilityActions: @Composable (NoteSummary) -> List<CardAction>,
) {
    val spacing = AppTheme.spacing
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 260.dp),
        contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, top = spacing.sm, bottom = 96.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing.md),
        verticalItemSpacing = spacing.md,
        modifier = Modifier.fillMaxSize().testTag(LibraryTestTags.GRID),
    ) {
        if (state.pinned.isNotEmpty()) {
            item(key = "h_pinned", span = StaggeredGridItemSpan.FullLine) { SectionLabel(stringResource(R.string.library_pinned)) }
            items(state.pinned, key = { it.id.value }) { note ->
                NoteCard(note, { onOpenNote(note.id, note.type) }, { onLongPress(note) }, accessibilityActions(note), selected = note.id.value == selectedNoteId, inkThumbnail = inkThumbnail)
            }
            if (state.others.isNotEmpty()) {
                item(key = "h_others", span = StaggeredGridItemSpan.FullLine) { SectionLabel(stringResource(R.string.library_others)) }
            }
        }
        items(state.others, key = { it.id.value }) { note ->
            NoteCard(note, { onOpenNote(note.id, note.type) }, { onLongPress(note) }, accessibilityActions(note), selected = note.id.value == selectedNoteId, inkThumbnail = inkThumbnail)
        }
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
): List<ActionSpec> = when (collection) {
    NoteCollection.Trash -> listOf(
        ActionSpec(R.string.note_restore, Icons.Outlined.RestoreFromTrash) { a.restore(note.id) },
        ActionSpec(R.string.note_delete_forever, Icons.Outlined.DeleteForever) { onDelete(note) },
    )
    else -> buildList {
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

@Composable
private fun OverflowMenu(
    inTrash: Boolean,
    onImport: () -> Unit,
    onEmptyTrash: () -> Unit,
    onManageNotebooks: () -> Unit,
    onManageTags: () -> Unit,
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
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_import)) },
                leadingIcon = { Icon(Icons.Outlined.FileOpen, null) },
                onClick = { open = false; onImport() },
            )
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
