package com.appname.notes.ui.common

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import com.appname.notes.core.domain.model.FolderTree
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.appname.notes.R
import com.appname.notes.core.domain.model.Notebook
import com.appname.notes.core.domain.model.NotebookId

/** A row in an action sheet. */
data class SheetAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(title: String, actions: List<SheetAction>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            actions.forEach { action ->
                ListItem(
                    headlineContent = { Text(action.label) },
                    leadingContent = { Icon(action.icon, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = false, role = Role.Button) {
                            onDismiss()
                            action.onClick()
                        },
                )
            }
        }
    }
}

/** Choose a notebook (or "No notebook") for a note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToNotebookSheet(
    notebooks: List<Notebook>,
    current: NotebookId?,
    onPick: (NotebookId?) -> Unit,
    onDismiss: () -> Unit,
    noneLabel: String? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .selectableGroup(),
        ) {
            Text(
                stringResource(R.string.move_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            NotebookChoice(noneLabel ?: stringResource(R.string.no_notebook), current == null, none = true) { onPick(null); onDismiss() }
            // Folders in tree order, indented by depth.
            FolderTree.ordered(notebooks).forEach { (nb, depth) ->
                NotebookChoice(nb.title, current == nb.id, depth = depth) { onPick(nb.id); onDismiss() }
            }
        }
    }
}

@Composable
private fun NotebookChoice(title: String, selected: Boolean, none: Boolean = false, depth: Int = 0, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width((16 * depth.coerceAtMost(6)).dp))
                RadioButton(selected = selected, onClick = null)
            }
        },
        trailingContent = if (none) {
            { Icon(Icons.Outlined.FolderOff, contentDescription = null) }
        } else {
            { FolderIcon(size = 24.dp) }
        },
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

