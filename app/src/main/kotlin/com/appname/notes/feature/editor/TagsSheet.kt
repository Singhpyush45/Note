package com.appname.notes.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.appname.notes.R
import com.appname.notes.core.domain.model.Tag
import com.appname.notes.core.domain.model.TagId

/** Attach/detach tags, or create a new one, for the open note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsSheet(
    allTags: List<Tag>,
    attached: Set<TagId>,
    onToggle: (Tag, Boolean) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newTag by rememberSaveable { mutableStateOf("") }
    fun submit() {
        if (newTag.isNotBlank()) {
            onCreate(newTag)
            newTag = ""
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.tags_sheet_title), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it.replace('\n', ' ') },
                    label = { Text(stringResource(R.string.tags_new_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f).testTag("tag_input"),
                )
                IconButton(onClick = ::submit, enabled = newTag.isNotBlank()) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.tags_add))
                }
            }
            if (allTags.isEmpty()) {
                Text(stringResource(R.string.tags_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                allTags.forEach { tag ->
                    val selected = tag.id in attached
                    FilterChip(
                        selected = selected,
                        onClick = { onToggle(tag, !selected) },
                        label = { Text("#${tag.name}") },
                        leadingIcon = if (selected) { { Icon(Icons.Outlined.Check, null) } } else null,
                    )
                }
            }
        }
    }
}
