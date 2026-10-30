package com.appname.notes.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.liquidGlassPill
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.Notebook

/** Quick filters under the home header (owner brief §2): All notes, Favourites, and each notebook (styled as Liquid Glass pills). */
@Composable
fun CollectionChips(
    current: NoteCollection,
    notebooks: List<Notebook>,
    onSelect: (NoteCollection) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().testTag(LibraryTestTags.CHIPS),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            Chip(
                label = stringResource(R.string.collection_all),
                selected = current == NoteCollection.All,
                tag = LibraryTestTags.CHIP_ALL,
            ) { onSelect(NoteCollection.All) }
        }
        item(key = "fav") {
            Chip(
                label = stringResource(R.string.collection_favourites),
                selected = current == NoteCollection.Favourites,
                tag = LibraryTestTags.CHIP_FAVOURITES,
            ) { onSelect(NoteCollection.Favourites) }
        }
        items(notebooks, key = { "nb_" + it.id.value }) { nb ->
            val c = NoteCollection.InNotebook(nb.id)
            Chip(nb.title, current == c, LibraryTestTags.chip(nb), folder = true) { onSelect(c) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, tag: String, folder: Boolean = false, onClick: () -> Unit) {
    val isDark = LocalDarkTheme.current
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "chipContentColor",
    )
    Row(
        modifier = Modifier
            .heightIn(min = 34.dp)
            .clip(CircleShape)
            .liquidGlassPill(
                selected = selected,
                dark = isDark,
                accentColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                cornerRadius = 50.dp,
            )
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (folder) {
            com.appname.notes.ui.common.FolderIcon(size = 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What the selection bar can do with the selected notes. */
data class SelectionActions(
    val clear: () -> Unit,
    val selectAll: () -> Unit,
    val pin: () -> Unit,
    val move: () -> Unit,
    val archive: () -> Unit,
    val trash: () -> Unit,
    val restore: () -> Unit,
    val deleteForever: () -> Unit,
)

/**
 * Replaces the header while notes are selected (long-press): how many, and what can be done to all of them.
 * In Trash only Restore and Delete forever make sense; in Archive, Archive becomes Unarchive.
 */
@Composable
fun SelectionBar(count: Int, collection: NoteCollection, allPinned: Boolean, actions: SelectionActions, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).testTag(LibraryTestTags.SELECTION_BAR),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.clear, modifier = Modifier.testTag(LibraryTestTags.SELECTION_CLEAR)) {
            Icon(Icons.Outlined.Close, stringResource(R.string.selection_clear))
        }
        Text(
            pluralStringResource(R.plurals.selection_count, count, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .testTag(LibraryTestTags.SELECTION_COUNT)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        BarAction(Icons.Outlined.SelectAll, R.string.selection_select_all, LibraryTestTags.SELECT_ALL, actions.selectAll)
        when (collection) {
            NoteCollection.Trash -> {
                BarAction(Icons.Outlined.RestoreFromTrash, R.string.note_restore, LibraryTestTags.SELECTION_RESTORE, actions.restore)
                BarAction(Icons.Outlined.DeleteForever, R.string.note_delete_forever, LibraryTestTags.SELECTION_DELETE, actions.deleteForever)
            }
            else -> {
                BarAction(Icons.Outlined.PushPin, if (allPinned) R.string.note_unpin else R.string.note_pin, LibraryTestTags.SELECTION_PIN, actions.pin)
                BarAction(Icons.AutoMirrored.Outlined.DriveFileMove, R.string.note_move, LibraryTestTags.SELECTION_MOVE, actions.move)
                if (collection == NoteCollection.Archive) {
                    BarAction(Icons.Outlined.Unarchive, R.string.note_unarchive, LibraryTestTags.SELECTION_ARCHIVE, actions.archive)
                } else {
                    BarAction(Icons.Outlined.Archive, R.string.note_archive, LibraryTestTags.SELECTION_ARCHIVE, actions.archive)
                }
                BarAction(Icons.Outlined.DeleteOutline, R.string.note_trash, LibraryTestTags.SELECTION_TRASH, actions.trash)
            }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: Int, tag: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.testTag(tag)) { Icon(icon, stringResource(label)) }
}

/** Rename a note from the home screen. An empty name is allowed: the card then shows the first line. */
@Composable
fun RenameNoteDialog(
    current: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
    @androidx.annotation.StringRes titleRes: Int = R.string.note_rename,
    @androidx.annotation.StringRes labelRes: Int = R.string.note_title_label,
) {
    var value by remember { mutableStateOf(TextFieldValue(current, TextRange(0, current.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val save = { onDismiss(); onRename(value.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(LibraryTestTags.RENAME_DIALOG),
        title = { Text(stringResource(titleRes)) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = { Text(stringResource(labelRes)) },
                    placeholder = { Text(stringResource(R.string.untitled)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(LibraryTestTags.RENAME_FIELD),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = save, modifier = Modifier.testTag(LibraryTestTags.RENAME_SAVE)) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
