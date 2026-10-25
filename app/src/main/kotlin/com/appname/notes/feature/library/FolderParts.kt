package com.appname.notes.feature.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appname.notes.R
import com.appname.notes.core.domain.model.FolderTree
import com.appname.notes.core.domain.model.Notebook
import com.appname.notes.core.domain.model.NotebookId
import com.appname.notes.ui.common.FolderIcon

/** What can be done with folders from the home screen (folders inside folders). */
data class FolderActions(
    val create: (String) -> Unit,
    val rename: (NotebookId, String) -> Unit,
    val move: (NotebookId, NotebookId?) -> Unit,
    val delete: (NotebookId) -> Unit,
)

object FolderTestTags {
    const val NEW_FOLDER = "folder_new"
    const val UP = "folder_up"
    fun card(id: NotebookId) = "folder_card_${id.value}"
    fun more(id: NotebookId) = "folder_more_${id.value}"
}

/** A folder inside the shown folder: icon, name, what's in it. Tap opens it; ⋮ or long-press for actions. */
@Composable
fun FolderCard(folder: Notebook, all: List<Notebook>, onOpen: () -> Unit, onMore: (() -> Unit)?) {
    val subfolders = FolderTree.children(all, folder.id).size
    val notes = pluralStringResource(R.plurals.note_count, folder.noteCount, folder.noteCount)
    val summary = if (subfolders > 0) stringResource(R.string.folder_summary, notes, pluralStringResource(R.plurals.folder_count, subfolders, subfolders)) else notes
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .combinedClickable(role = Role.Button, onClick = onOpen, onLongClick = onMore)
            .testTag(FolderTestTags.card(folder.id)),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FolderIcon(size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (onMore != null) {
                IconButton(onClick = onMore, modifier = Modifier.testTag(FolderTestTags.more(folder.id))) {
                    Icon(Icons.Outlined.MoreVert, stringResource(R.string.folder_actions, folder.title))
                }
            }
        }
    }
}

/** "New folder" here, as a card next to the folders. */
@Composable
fun NewFolderCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(FolderTestTags.NEW_FOLDER),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.folder_new), style = MaterialTheme.typography.labelLarge)
        }
    }
}
