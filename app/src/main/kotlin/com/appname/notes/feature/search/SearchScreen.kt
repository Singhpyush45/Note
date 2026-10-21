package com.appname.notes.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.EmptyState
import com.appname.notes.core.designsystem.theme.AppTheme
import com.appname.notes.core.domain.model.ModifiedWithin
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.core.domain.model.SearchHit
import com.appname.notes.core.domain.search.Highlighted
import com.appname.notes.ui.common.NoteDates

object SearchTestTags {
    const val SCREEN = "screen_search"
    const val FIELD = "search_field"
    const val RESULTS = "search_results"
}

@Composable
fun SearchRoute(onBack: () -> Unit, onOpenNote: (NoteId, NoteType) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onOpenNote = onOpenNote,
        onNotebook = viewModel::setNotebook,
        onTag = viewModel::setTag,
        onModified = viewModel::setModified,
        onIncludeArchived = viewModel::setIncludeArchived,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenNote: (NoteId, NoteType) -> Unit,
    onNotebook: (com.appname.notes.core.domain.model.NotebookId?) -> Unit,
    onTag: (com.appname.notes.core.domain.model.TagId?) -> Unit,
    onModified: (ModifiedWithin) -> Unit,
    onIncludeArchived: (Boolean) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (state.query.isEmpty()) focus.requestFocus() }
    Scaffold(
        modifier = Modifier.testTag(SearchTestTags.SCREEN),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                title = {
                    TextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.search_clear)) }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(SearchTestTags.FIELD),
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Filters(state, onNotebook, onTag, onModified, onIncludeArchived)
            HorizontalDivider()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val results = state.results
                when {
                    results == null -> EmptyState(
                        title = stringResource(R.string.search_start_title),
                        body = stringResource(R.string.search_start_body),
                        icon = Icons.Outlined.Search,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    results.isEmpty() -> EmptyState(
                        title = stringResource(R.string.search_empty_title),
                        body = stringResource(R.string.search_empty_body),
                        icon = Icons.Outlined.SearchOff,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> LazyColumn(Modifier.fillMaxSize().testTag(SearchTestTags.RESULTS)) {
                        items(results, key = { it.note.id.value }) { hit ->
                            SearchResultRow(hit, onClick = { onOpenNote(hit.note.id, hit.note.type) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Filters(
    state: SearchUiState,
    onNotebook: (com.appname.notes.core.domain.model.NotebookId?) -> Unit,
    onTag: (com.appname.notes.core.domain.model.TagId?) -> Unit,
    onModified: (ModifiedWithin) -> Unit,
    onIncludeArchived: (Boolean) -> Unit,
) {
    val f = state.filters
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val notebookName = state.notebooks.firstOrNull { it.id == f.notebookId }?.title
        MenuChip(
            label = notebookName ?: stringResource(R.string.filter_notebook),
            selected = notebookName != null,
            options = listOf(stringResource(R.string.filter_any_notebook) to { onNotebook(null) }) +
                state.notebooks.map { nb -> nb.title to { onNotebook(nb.id) } },
        )
        val tagName = state.tags.firstOrNull { it.id == f.tagId }?.name
        MenuChip(
            label = tagName?.let { "#$it" } ?: stringResource(R.string.filter_tag),
            selected = tagName != null,
            options = listOf(stringResource(R.string.filter_any_tag) to { onTag(null) }) +
                state.tags.map { t -> "#${t.name}" to { onTag(t.id) } },
        )
        val modifiedLabels = mapOf(
            ModifiedWithin.ANY to stringResource(R.string.filter_any),
            ModifiedWithin.TODAY to stringResource(R.string.filter_today),
            ModifiedWithin.WEEK to stringResource(R.string.filter_week),
            ModifiedWithin.MONTH to stringResource(R.string.filter_month),
        )
        MenuChip(
            label = if (f.modifiedWithin == ModifiedWithin.ANY) stringResource(R.string.filter_modified) else modifiedLabels.getValue(f.modifiedWithin),
            selected = f.modifiedWithin != ModifiedWithin.ANY,
            options = modifiedLabels.map { (value, label) -> label to { onModified(value) } },
        )
        FilterChip(
            selected = f.includeArchived,
            onClick = { onIncludeArchived(!f.includeArchived) },
            label = { Text(stringResource(R.string.filter_include_archived)) },
        )
    }
}

@Composable
private fun MenuChip(label: String, selected: Boolean, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, action) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; action() })
            }
        }
    }
}

@Composable
private fun SearchResultRow(hit: SearchHit, onClick: () -> Unit) {
    val spacing = AppTheme.spacing
    val note = hit.note
    val highlight = SpanStyle(fontWeight = FontWeight.Bold, background = MaterialTheme.colorScheme.tertiaryContainer)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            note.title.ifBlank { note.preview.lineSequence().firstOrNull().orEmpty() }.ifBlank { stringResource(R.string.untitled) },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            highlighted(hit.snippet.replace('\n', ' '), highlight),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(note.notebookTitle, NoteDates.format(note.updatedAt)).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun highlighted(snippet: String, style: SpanStyle): AnnotatedString {
    val parsed = Highlighted.parse(snippet)
    return buildAnnotatedString {
        append(parsed.text)
        parsed.ranges.forEach { addStyle(style, it.first, it.last + 1) }
    }
}
