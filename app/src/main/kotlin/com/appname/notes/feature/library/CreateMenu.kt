package com.appname.notes.feature.library

import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.GlassSurface

/** What the + menu can create (owner brief §3). */
data class CreateActions(
    val blankNote: () -> Unit,
    val chooseTemplate: () -> Unit,
    val scanNote: () -> Unit,
    val importFile: () -> Unit,
    /** A new folder (in the open folder, or at the top level); null where folders can't be made. */
    val newFolder: (() -> Unit)? = null,
)

/**
 * A light veil over the screen while the + menu is open; tapping it closes the menu. Draw it above the content
 * and below [CreateMenu].
 */
@Composable
fun CreateMenuScrim(visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .testTag(LibraryTestTags.CREATE_SCRIM),
        )
    }
}

/**
 * One expandable "+" (owner brief §3, like Google Drive's): Blank note, Choose template, Scan note, Import file
 * on glass labels above it. Closes with the button (now ×), a tap outside ([CreateMenuScrim]) or Back.
 */
@Composable
fun CreateMenu(expanded: Boolean, onExpandedChange: (Boolean) -> Unit, actions: CreateActions, modifier: Modifier = Modifier) {
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, label = "plus")
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(
            expanded,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 },
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                fun pick(action: () -> Unit): () -> Unit = { onExpandedChange(false); action() }
                actions.newFolder?.let { CreateItem(R.string.folder_new, Icons.Outlined.CreateNewFolder, LibraryTestTags.NEW_FOLDER, pick(it)) }
                CreateItem(R.string.create_import_file, Icons.Outlined.UploadFile, LibraryTestTags.IMPORT_FILE, pick(actions.importFile))
                CreateItem(R.string.create_scan_note, Icons.Outlined.DocumentScanner, LibraryTestTags.SCAN, pick(actions.scanNote))
                CreateItem(R.string.create_choose_template, Icons.Outlined.GridView, LibraryTestTags.CHOOSE_TEMPLATE, pick(actions.chooseTemplate))
                CreateItem(R.string.create_blank_note, Icons.Outlined.NoteAdd, LibraryTestTags.NEW_NOTE, pick(actions.blankNote))
            }
        }
        val label = stringResource(if (expanded) R.string.create_close else R.string.create_open)
        val state = stringResource(if (expanded) R.string.create_state_open else R.string.create_state_closed)
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
            modifier = Modifier
                .size(64.dp)
                .testTag(LibraryTestTags.CREATE)
                .semantics {
                    contentDescription = label
                    stateDescription = state
                },
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(30.dp).rotate(rotation))
        }
    }
}

/** A glass label with a round icon, both tappable as one item. */
@Composable
private fun CreateItem(label: Int, icon: ImageVector, tag: String, onClick: () -> Unit) {
    val text = stringResource(label)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .testTag(tag),
    ) {
        GlassSurface(shape = CircleShape, elevation = 2.dp) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
        GlassSurface(
            shape = CircleShape,
            tint = MaterialTheme.colorScheme.primaryContainer,
            elevation = 2.dp,
            modifier = Modifier.size(52.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.align(Alignment.Center))
        }
    }
}
