package com.appname.notes.feature.organize

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.EmptyState
import com.appname.notes.core.domain.model.Notebook
import com.appname.notes.core.domain.model.NotebookDeleteMode
import com.appname.notes.core.domain.model.Tag
import com.appname.notes.ui.common.MessageEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebooksRoute(onBack: () -> Unit, viewModel: NotebooksViewModel = hiltViewModel()) {
    val notebooks by viewModel.notebooks.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(message, snackbar, viewModel::messageShown)

    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Notebook?>(null) }
    var deleting by remember { mutableStateOf<Notebook?>(null) }

    Scaffold(
        modifier = Modifier.testTag("screen_notebooks"),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notebooks_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text(stringResource(R.string.notebooks_new)) },
                modifier = Modifier.testTag("fab_new_notebook"),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val list = notebooks
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(
                    title = stringResource(R.string.notebooks_empty_title),
                    body = stringResource(R.string.notebooks_empty_body),
                    icon = Icons.Outlined.Folder,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(list, key = { _, nb -> nb.id.value }) { index, nb ->
                        ListItem(
                            headlineContent = { Text(nb.title) },
                            supportingContent = { Text(pluralStringResource(R.plurals.note_count, nb.noteCount, nb.noteCount)) },
                            leadingContent = { Icon(Icons.Outlined.Folder, null) },
                            trailingContent = {
                                androidx.compose.foundation.layout.Row {
                                    IconButton(onClick = { viewModel.move(nb.id, -1) }, enabled = index > 0) {
                                        Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.notebooks_move_up))
                                    }
                                    IconButton(onClick = { viewModel.move(nb.id, +1) }, enabled = index < list.lastIndex) {
                                        Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.notebooks_move_down))
                                    }
                                    IconButton(onClick = { renaming = nb }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_rename)) }
                                    IconButton(onClick = { deleting = nb }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = stringResource(R.string.notebooks_new),
            label = stringResource(R.string.notebooks_name_hint),
            initial = "",
            confirm = stringResource(R.string.action_create),
            onConfirm = { viewModel.create(it); creating = false },
            onDismiss = { creating = false },
        )
    }
    renaming?.let { nb ->
        NameDialog(
            title = stringResource(R.string.action_rename),
            label = stringResource(R.string.notebooks_name_hint),
            initial = nb.title,
            confirm = stringResource(R.string.action_save),
            onConfirm = { viewModel.rename(nb.id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { nb ->
        DeleteNotebookDialog(nb, onConfirm = { mode -> viewModel.delete(nb.id, mode); deleting = null }, onDismiss = { deleting = null })
    }
}

/** FR-003: deleting a notebook must not silently destroy its notes, so the user chooses explicitly. */
@Composable
private fun DeleteNotebookDialog(notebook: Notebook, onConfirm: (NotebookDeleteMode) -> Unit, onDismiss: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(NotebookDeleteMode.KEEP_NOTES) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notebooks_delete_title, notebook.title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                Text(stringResource(R.string.notebooks_delete_body))
                listOf(
                    NotebookDeleteMode.KEEP_NOTES to R.string.notebooks_delete_keep,
                    NotebookDeleteMode.TRASH_NOTES to R.string.notebooks_delete_trash,
                ).forEach { (value, label) ->
                    ListItem(
                        headlineContent = { Text(stringResource(label)) },
                        leadingContent = { RadioButton(selected = mode == value, onClick = null) },
                        modifier = Modifier.selectable(selected = mode == value, role = Role.RadioButton) { mode = value },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(mode) }) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsRoute(onBack: () -> Unit, viewModel: TagsViewModel = hiltViewModel()) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(message, snackbar, viewModel::messageShown)
    var renaming by remember { mutableStateOf<Tag?>(null) }
    var deleting by remember { mutableStateOf<Tag?>(null) }

    Scaffold(
        modifier = Modifier.testTag("screen_tags"),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tags_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val list = tags
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(
                    title = stringResource(R.string.tags_empty_title),
                    body = stringResource(R.string.tags_empty_body),
                    icon = Icons.AutoMirrored.Outlined.Label,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(list, key = { _, t -> t.id.value }) { _, tag ->
                        ListItem(
                            headlineContent = { Text("#${tag.name}") },
                            supportingContent = { Text(pluralStringResource(R.plurals.note_count, tag.noteCount, tag.noteCount)) },
                            trailingContent = {
                                androidx.compose.foundation.layout.Row {
                                    IconButton(onClick = { renaming = tag }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_rename)) }
                                    IconButton(onClick = { deleting = tag }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    renaming?.let { tag ->
        NameDialog(
            title = stringResource(R.string.action_rename),
            label = stringResource(R.string.tags_name_hint),
            initial = tag.name,
            confirm = stringResource(R.string.action_save),
            onConfirm = { viewModel.rename(tag.id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.tags_delete_title, tag.name)) },
            text = { Text(stringResource(R.string.tags_delete_body)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(tag.id); deleting = null }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
fun NameDialog(title: String, label: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.replace('\n', ' ') },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.padding(top = 4.dp).testTag("name_field"),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
