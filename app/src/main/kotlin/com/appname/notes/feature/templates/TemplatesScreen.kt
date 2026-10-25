package com.appname.notes.feature.templates

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.withSave
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.GlassSurface
import com.appname.notes.core.designsystem.component.backdropSource
import com.appname.notes.core.designsystem.component.rememberBackdrop
import com.appname.notes.core.domain.model.NoteId
import com.appname.notes.core.ink.template.TemplateCategory
import com.appname.notes.core.ink.template.TemplateDefinition
import com.appname.notes.core.ink.template.TemplateLayout
import com.appname.notes.feature.library.ConfirmDialog
import com.appname.notes.ui.common.MessageEffect
import kotlinx.coroutines.launch

object TemplatesTestTags {
    const val SCREEN = "screen_templates"
    const val GRID = "templates_grid"
    const val SEARCH = "templates_search"
    const val SEARCH_FIELD = "templates_search_field"
    const val NEW = "template_new"
    const val BASIC_CUSTOM = "template_basic_custom"
    const val PREVIEW = "template_preview"
    const val USE = "template_use"
    const val EDIT = "template_edit"
    const val RENAME = "template_rename"
    const val DUPLICATE = "template_duplicate"
    const val DELETE = "template_delete"
    const val NAME_FIELD = "template_name_field"
    const val NAME_SAVE = "template_name_save"
    const val EMPTY_SEARCH = "templates_empty_search"

    fun tile(id: String) = "template_$id"
    fun chip(c: TemplateCategory) = "template_chip_${c.name.lowercase()}"
    fun section(c: TemplateCategory) = "template_section_${c.name.lowercase()}"
}

@Composable
fun TemplatesRoute(
    viewModel: TemplatesViewModel,
    manage: Boolean,
    onBack: () -> Unit,
    onNoteCreated: (NoteId) -> Unit,
    onEdit: (templateId: String) -> Unit,
    onNewTemplate: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    TemplatesScreen(
        state = state,
        manage = manage,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        actions = TemplateActions(
            use = { t -> scope.launch { viewModel.use(t)?.let(onNoteCreated) } },
            edit = { t -> onEdit(t.id) },
            customise = { t -> scope.launch { viewModel.duplicate(t)?.let(onEdit) } },
            duplicate = { t -> scope.launch { viewModel.duplicate(t) } },
            rename = viewModel::rename,
            delete = viewModel::delete,
            newTemplate = onNewTemplate,
        ),
        onMessageShown = viewModel::messageShown,
    )
}

/** What can be done with a template. */
data class TemplateActions(
    val use: (TemplateDefinition) -> Unit,
    val edit: (TemplateDefinition) -> Unit,
    /** Built-ins: a custom copy, opened in the editor. */
    val customise: (TemplateDefinition) -> Unit,
    val duplicate: (TemplateDefinition) -> Unit,
    val rename: (TemplateDefinition, String) -> Unit,
    val delete: (TemplateDefinition) -> Unit,
    val newTemplate: () -> Unit,
)

/**
 * A separate screen (owner brief §4): back, title and search in a glass header, category chips, then every
 * category's templates as page thumbnails. Tapping one shows it large with "Use template" (and, for the user's
 * own, edit/rename/duplicate/delete). [manage] is the same screen opened from Settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplatesScreen(
    state: TemplatesUiState,
    manage: Boolean,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    actions: TemplateActions,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, onMessageShown)
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val all = state.sections.flatMap { it.templates }
    fun find(id: String?) = all.firstOrNull { it.id == id }

    // Grid rows: a header per category, then its tiles. Remember where each category starts for the chips.
    val sections = state.sections.filter { it.templates.isNotEmpty() || it.category == TemplateCategory.CUSTOM }
    val starts = remember(sections, state.isEmptySearch) {
        var i = if (state.isEmptySearch) 1 else 0
        sections.associate { s ->
            val start = i
            i += 1 + s.templates.size + if (s.category == TemplateCategory.BASIC || s.category == TemplateCategory.CUSTOM) 1 else 0
            s.category to start
        }
    }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val current by remember(starts) {
        derivedStateOf { starts.entries.lastOrNull { it.value <= grid.firstVisibleItemIndex }?.key ?: TemplateCategory.BASIC }
    }
    // From Settings, start at the user's own templates.
    LaunchedEffect(manage, starts.isNotEmpty()) {
        if (manage) starts[TemplateCategory.CUSTOM]?.let { grid.scrollToItem(it) }
    }

    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(0.dp) }
    val backdrop = rememberBackdrop()

    Scaffold(
        modifier = Modifier.testTag(TemplatesTestTags.SCREEN),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                state = grid,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = headerHeight + 8.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().backdropSource(backdrop).testTag(TemplatesTestTags.GRID),
            ) {
                if (state.isEmptySearch) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.templates_no_match, state.query.trim()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp).testTag(TemplatesTestTags.EMPTY_SEARCH),
                        )
                    }
                }
                for (section in sections) {
                    item(key = "h_${section.category}", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(section.category.label()),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding(top = 12.dp, bottom = 2.dp)
                                .semantics { heading() }
                                .testTag(TemplatesTestTags.section(section.category)),
                        )
                    }
                    items(section.templates, key = { it.id }) { t ->
                        TemplateTile(t.name, Modifier.testTag(TemplatesTestTags.tile(t.id)), onClick = { previewId = t.id }) {
                            TemplatePreview(t.layout, Modifier.fillMaxSize())
                        }
                    }
                    if (section.category == TemplateCategory.BASIC) {
                        item(key = "basic_custom") {
                            NewTile(stringResource(R.string.templates_custom_tile), Modifier.testTag(TemplatesTestTags.BASIC_CUSTOM), actions.newTemplate)
                        }
                    }
                    if (section.category == TemplateCategory.CUSTOM) {
                        item(key = "custom_new") {
                            NewTile(stringResource(R.string.templates_new), Modifier.testTag(TemplatesTestTags.NEW), actions.newTemplate)
                        }
                    }
                }
            }

            GlassSurface(
                shape = MaterialTheme.shapes.extraLarge,
                elevation = 3.dp,
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f)
                    .onSizeChanged { with(density) { headerHeight = it.height.toDp() } }
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                        if (searching) {
                            OutlinedTextField(
                                value = state.query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                placeholder = { Text(stringResource(R.string.templates_search_hint)) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                ),
                                modifier = Modifier.weight(1f).testTag(TemplatesTestTags.SEARCH_FIELD),
                            )
                            IconButton(onClick = { searching = false; onQueryChange("") }) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.templates_search_close))
                            }
                        } else {
                            Text(
                                stringResource(if (manage) R.string.templates_title_manage else R.string.create_choose_template),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
                            )
                            IconButton(onClick = { searching = true }, modifier = Modifier.testTag(TemplatesTestTags.SEARCH)) {
                                Icon(Icons.Outlined.Search, stringResource(R.string.templates_search))
                            }
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) {
                        TemplateCategory.entries.forEach { c ->
                            FilterChip(
                                selected = c == current,
                                onClick = { starts[c]?.let { scope.launch { grid.animateScrollToItem(it) } } },
                                label = { Text(stringResource(c.label())) },
                                shape = MaterialTheme.shapes.extraLarge,
                                enabled = starts.containsKey(c),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                                modifier = Modifier.testTag(TemplatesTestTags.chip(c)),
                            )
                        }
                    }
                }
            }
        }
    }

    find(previewId)?.let { t ->
        TemplatePreviewDialog(
            template = t,
            onUse = { previewId = null; actions.use(t) },
            onEdit = { previewId = null; if (t.isCustom) actions.edit(t) else actions.customise(t) },
            onRename = { previewId = null; renaming = t.id },
            onDuplicate = { previewId = null; actions.duplicate(t) },
            onDelete = { previewId = null; deleting = t.id },
            onDismiss = { previewId = null },
        )
    }
    find(renaming)?.let { t ->
        TemplateNameDialog(
            title = stringResource(R.string.templates_rename_title),
            initial = t.name,
            onConfirm = { actions.rename(t, it) },
            onDismiss = { renaming = null },
        )
    }
    find(deleting)?.let { t ->
        ConfirmDialog(
            title = stringResource(R.string.templates_delete_title, t.name),
            body = stringResource(R.string.templates_delete_body),
            confirm = stringResource(R.string.action_delete),
            onConfirm = { actions.delete(t) },
            onDismiss = { deleting = null },
        )
    }
}

fun TemplateCategory.label(): Int = when (this) {
    TemplateCategory.BASIC -> R.string.templates_cat_basic
    TemplateCategory.ACADEMIC -> R.string.templates_cat_academic
    TemplateCategory.PLANNERS -> R.string.templates_cat_planners
    TemplateCategory.JOURNALS -> R.string.templates_cat_journals
    TemplateCategory.CREATIVE -> R.string.templates_cat_creative
    TemplateCategory.CUSTOM -> R.string.templates_cat_custom
}

/** A glass tile: the page in miniature and its name under it. */
@Composable
private fun TemplateTile(name: String, modifier: Modifier, onClick: () -> Unit, preview: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    GlassSurface(shape = shape, elevation = 1.dp, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(shape)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.templates_preview_action), onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .padding(8.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(TILE_RATIO)) { preview() }
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun NewTile(label: String, modifier: Modifier, onClick: () -> Unit) {
    TemplateTile(label, modifier, onClick) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(6.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
    }
}

/** A page drawn from its layout, fitted inside the available space (landscape pages lie across). */
@Composable
fun TemplatePreview(layout: TemplateLayout, modifier: Modifier = Modifier) {
    val renderer = remember { TemplateRenderer() }
    val outline = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val scale = minOf(size.width / layout.width, size.height / layout.height)
        val w = layout.width * scale
        val h = layout.height * scale
        val left = (size.width - w) / 2
        val top = (size.height - h) / 2
        drawIntoCanvas { c ->
            c.nativeCanvas.withSave {
                translate(left, top)
                scale(scale, scale)
                renderer.drawPage(this, layout)
            }
        }
        drawRect(
            outline,
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(w, h),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
        )
    }
}

private const val TILE_RATIO = 0.74f
