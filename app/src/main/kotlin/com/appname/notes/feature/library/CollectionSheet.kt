package com.appname.notes.feature.library

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.appname.notes.R
import com.appname.notes.core.domain.model.NoteCollection

/** Picks which notes the library shows: built-in collections, notebooks and tags. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionSheet(
    state: LibraryUiState,
    onSelect: (NoteCollection) -> Unit,
    onDismiss: () -> Unit,
    onManageNotebooks: () -> Unit,
    onManageTags: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.navigationBarsPadding().testTag("collection_sheet")) {
            item { Choice(stringResource(R.string.collection_all), Icons.AutoMirrored.Outlined.Notes, state.collection == NoteCollection.All) { onSelect(NoteCollection.All) } }
            item { Choice(stringResource(R.string.collection_favourites), Icons.Outlined.StarOutline, state.collection == NoteCollection.Favourites) { onSelect(NoteCollection.Favourites) } }

            item { Header(stringResource(R.string.collection_notebooks)) }
            items(state.notebooks, key = { "nb_${it.id.value}" }) { nb ->
                val c = NoteCollection.InNotebook(nb.id)
                Choice(nb.title, Icons.Outlined.Folder, state.collection == c, pluralStringResource(R.plurals.note_count, nb.noteCount, nb.noteCount)) { onSelect(c) }
            }
            item { Choice(stringResource(R.string.library_manage_notebooks), Icons.Outlined.Settings, false, onClick = onManageNotebooks) }

            item { Header(stringResource(R.string.collection_tags)) }
            items(state.tags, key = { "tag_${it.id.value}" }) { tag ->
                val c = NoteCollection.WithTag(tag.id)
                Choice("#${tag.name}", Icons.AutoMirrored.Outlined.Label, state.collection == c, pluralStringResource(R.plurals.note_count, tag.noteCount, tag.noteCount)) { onSelect(c) }
            }
            item { Choice(stringResource(R.string.library_manage_tags), Icons.Outlined.Settings, false, onClick = onManageTags) }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Choice(stringResource(R.string.collection_archive), Icons.Outlined.Inventory2, state.collection == NoteCollection.Archive) { onSelect(NoteCollection.Archive) } }
            item { Choice(stringResource(R.string.collection_trash), Icons.Outlined.DeleteOutline, state.collection == NoteCollection.Trash) { onSelect(NoteCollection.Trash) } }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 24.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Choice(title: String, icon: ImageVector, selected: Boolean, trailing: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = trailing?.let { { Text(it, style = MaterialTheme.typography.labelMedium) } },
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        modifier = Modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick),
    )
}
