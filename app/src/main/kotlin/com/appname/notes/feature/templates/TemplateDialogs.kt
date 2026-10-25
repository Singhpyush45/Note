package com.appname.notes.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.GlassSurface
import com.appname.notes.core.domain.repository.TemplateRepository
import com.appname.notes.core.ink.template.PageFormat
import com.appname.notes.core.ink.template.TemplateDefinition

/**
 * A template shown large before using it (owner brief §4: previews and clear use actions). The user's own
 * templates can also be edited, renamed, duplicated and deleted here; a built-in can be customised (copied
 * into an editable template of the user's).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TemplatePreviewDialog(
    template: TemplateDefinition,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassSurface(
            shape = MaterialTheme.shapes.extraLarge,
            tint = MaterialTheme.colorScheme.surfaceContainerHigh,
            elevation = 6.dp,
            // A dialog is its own window: expose test tags as resource ids here too (UI Automator tests).
            modifier = Modifier
                .padding(20.dp)
                .widthIn(max = 520.dp)
                .semantics { testTagsAsResourceId = true }
                .testTag(TemplatesTestTags.PREVIEW),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(template.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(R.string.templates_preview_meta, stringResource(template.category.label()), stringResource(formatLabel(template))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // The whole page, fitted (portrait, landscape or long pages alike).
                TemplatePreview(template.layout, Modifier.fillMaxWidth().height(400.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (template.isCustom) {
                        IconButton(onClick = onEdit, modifier = Modifier.testTag(TemplatesTestTags.EDIT)) {
                            Icon(Icons.Outlined.Edit, stringResource(R.string.templates_edit))
                        }
                        IconButton(onClick = onRename, modifier = Modifier.testTag(TemplatesTestTags.RENAME)) {
                            Icon(Icons.Outlined.DriveFileRenameOutline, stringResource(R.string.note_rename))
                        }
                        IconButton(onClick = onDuplicate, modifier = Modifier.testTag(TemplatesTestTags.DUPLICATE)) {
                            Icon(Icons.Outlined.ContentCopy, stringResource(R.string.templates_duplicate))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.testTag(TemplatesTestTags.DELETE)) {
                            Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.action_delete))
                        }
                    } else {
                        OutlinedButton(onClick = onEdit, modifier = Modifier.testTag(TemplatesTestTags.EDIT)) {
                            Text(stringResource(R.string.templates_customise))
                        }
                    }
                    Box(Modifier.weight(1f))
                    Button(onClick = onUse, modifier = Modifier.testTag(TemplatesTestTags.USE)) {
                        Text(stringResource(R.string.templates_use))
                    }
                }
            }
        }
    }
}

private fun formatLabel(t: TemplateDefinition): Int = when (PageFormat.closest(t.layout.width, t.layout.height)) {
    PageFormat.A4_PORTRAIT -> R.string.page_format_a4_portrait
    PageFormat.A4_LANDSCAPE -> R.string.page_format_a4_landscape
    PageFormat.LONG_20_9 -> R.string.page_format_long
}

/** Asks for a template name; blank names can't be saved. */
@Composable
fun TemplateNameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val valid = TemplateRepository.cleanName(name) != null
    val save = { if (valid) { onDismiss(); onConfirm(name) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(TemplateRepository.MAX_NAME) },
                singleLine = true,
                label = { Text(stringResource(R.string.note_title_label)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().testTag(TemplatesTestTags.NAME_FIELD),
            )
        },
        confirmButton = {
            TextButton(onClick = save, enabled = valid, modifier = Modifier.testTag(TemplatesTestTags.NAME_SAVE)) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
