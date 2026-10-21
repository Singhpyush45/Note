package com.appname.notes.ui.common

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
            NotebookChoice(stringResource(R.string.no_notebook), current == null) { onPick(null); onDismiss() }
            notebooks.forEach { nb ->
                NotebookChoice(nb.title, current == nb.id) { onPick(nb.id); onDismiss() }
            }
        }
    }
}

@Composable
private fun NotebookChoice(title: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        trailingContent = if (title == stringResource(R.string.no_notebook)) {
            { Icon(Icons.Outlined.FolderOff, contentDescription = null) }
        } else {
            null
        },
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

