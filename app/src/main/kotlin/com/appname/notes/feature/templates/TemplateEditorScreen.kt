package com.appname.notes.feature.templates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.GlassSurface
import com.appname.notes.core.designsystem.component.SettingsCard
import com.appname.notes.core.designsystem.component.SettingsSegmentedRow
import com.appname.notes.core.designsystem.component.SettingsSliderRow
import com.appname.notes.core.designsystem.component.SettingsSwitchRow
import com.appname.notes.core.ink.template.HeaderStyle
import com.appname.notes.core.ink.template.PageFormat
import com.appname.notes.core.ink.template.Ruling
import com.appname.notes.core.ink.template.TemplateColors
import com.appname.notes.core.ink.template.TemplateDraft
import com.appname.notes.feature.library.ConfirmDialog
import com.appname.notes.ui.common.MessageEffect
import androidx.compose.foundation.text.KeyboardOptions

object TemplateEditorTestTags {
    const val SCREEN = "screen_template_editor"
    const val NAME = "template_editor_name"
    const val SAVE = "template_editor_save"
    const val PREVIEW = "template_editor_preview"
    const val FORM = "template_editor_form"
    fun format(f: PageFormat) = "template_format_${f.name.lowercase()}"
    fun ruling(r: Ruling) = "template_ruling_${r.name.lowercase()}"
    fun header(h: HeaderStyle) = "template_header_${h.name.lowercase()}"
    fun paper(i: Int) = "template_paper_$i"
    fun line(i: Int) = "template_line_$i"
}

@Composable
fun TemplateEditorRoute(viewModel: TemplateEditorViewModel, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved, state.missing) { if (state.saved || state.missing) onClose() }
    TemplateEditorScreen(
        state = state,
        onNameChange = viewModel::onNameChange,
        onDraftChange = viewModel::onDraftChange,
        onSave = viewModel::save,
        onClose = onClose,
        onMessageShown = viewModel::messageShown,
    )
}

/**
 * Make or change a custom template (owner brief §4): a live preview, then name, page size, background with
 * spacing and line colour, margin, paper colour and header. Every option changes the preview at once.
 */
@Composable
fun TemplateEditorScreen(
    state: TemplateEditorUiState,
    onNameChange: (String) -> Unit,
    onDraftChange: (TemplateDraft) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, onMessageShown)
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val leave: () -> Unit = {
        if (state.dirty) {
            confirmDiscard = true
        } else {
            onClose()
        }
    }
    BackHandler(enabled = state.dirty) { confirmDiscard = true }
    val d = state.draft
    val layout = remember(d) { d.toLayout() }
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        modifier = Modifier.testTag(TemplateEditorTestTags.SCREEN),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding().imePadding()) {
            GlassSurface(shape = MaterialTheme.shapes.extraLarge, elevation = 3.dp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                    Text(
                        stringResource(if (state.existing) R.string.templates_editor_edit_title else R.string.templates_editor_new_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.padding(end = 8.dp).testTag(TemplateEditorTestTags.SAVE)) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .testTag(TemplateEditorTestTags.FORM)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                GlassSurface(shape = MaterialTheme.shapes.large, elevation = 1.dp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                    TemplatePreview(layout, Modifier.fillMaxWidth().height(300.dp).padding(12.dp).testTag(TemplateEditorTestTags.PREVIEW))
                }
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.templates_editor_name)) },
                    isError = state.name.isNotEmpty() && !state.canSave && !state.saving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().testTag(TemplateEditorTestTags.NAME),
                )
                SettingsCard(stringResource(R.string.templates_editor_page)) {
                    val formats = PageFormat.entries
                    SettingsSegmentedRow(
                        title = stringResource(R.string.templates_editor_size),
                        options = formats.map { stringResource(it.shortLabel()) },
                        selectedIndex = formats.indexOf(d.format),
                        onSelect = { onDraftChange(d.copy(format = formats[it])) },
                        optionTag = { TemplateEditorTestTags.format(formats[it]) },
                    )
                    val headers = HeaderStyle.entries
                    SettingsSegmentedRow(
                        title = stringResource(R.string.templates_editor_header),
                        options = headers.map { stringResource(it.label()) },
                        selectedIndex = headers.indexOf(d.header),
                        onSelect = { onDraftChange(d.copy(header = headers[it])) },
                        optionTag = { TemplateEditorTestTags.header(headers[it]) },
                    )
                }
                SettingsCard(stringResource(R.string.templates_editor_background)) {
                    val rulings = Ruling.entries
                    SettingsSegmentedRow(
                        title = stringResource(R.string.templates_editor_ruling),
                        options = rulings.map { stringResource(it.label()) },
                        selectedIndex = rulings.indexOf(d.ruling),
                        onSelect = {
                            val r = rulings[it]
                            // Dots look right in the dot grey; lines and grids in the rule colour.
                            val line = if (r == Ruling.DOTS && d.lineColor == TemplateColors.RULE) TemplateColors.DOT
                            else if (r != Ruling.DOTS && d.lineColor == TemplateColors.DOT) TemplateColors.RULE else d.lineColor
                            onDraftChange(d.copy(ruling = r, lineColor = line))
                        },
                        optionTag = { TemplateEditorTestTags.ruling(rulings[it]) },
                    )
                    SettingsSliderRow(
                        title = stringResource(R.string.templates_editor_spacing),
                        value = d.spacing,
                        valueRange = TemplateDraft.MIN_SPACING..TemplateDraft.MAX_SPACING,
                        valueLabel = { spacingLabel(context, it) },
                        onValueCommitted = { onDraftChange(d.copy(spacing = it)) },
                        enabled = d.ruling != Ruling.NONE,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.templates_editor_margin),
                        checked = d.margin && d.marginApplies,
                        onCheckedChange = { onDraftChange(d.copy(margin = it)) },
                        enabled = d.marginApplies,
                    )
                    SwatchRow(
                        title = stringResource(R.string.templates_editor_line_colour),
                        colors = (TemplateColors.LINES + TemplateColors.DOT).distinct(),
                        selected = d.lineColor,
                        enabled = d.ruling != Ruling.NONE,
                        tag = TemplateEditorTestTags::line,
                        onPick = { onDraftChange(d.copy(lineColor = it)) },
                    )
                }
                SettingsCard(stringResource(R.string.templates_editor_paper)) {
                    SwatchRow(
                        title = stringResource(R.string.templates_editor_paper_colour),
                        colors = TemplateColors.PAPERS,
                        selected = d.paperColor,
                        enabled = true,
                        tag = TemplateEditorTestTags::paper,
                        onPick = { onDraftChange(d.copy(paperColor = it)) },
                    )
                    if (d.extras.isNotEmpty()) {
                        Text(
                            stringResource(R.string.templates_editor_kept_parts),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.templates_editor_discard_title),
            body = stringResource(R.string.templates_editor_discard_body),
            confirm = stringResource(R.string.templates_editor_discard),
            onConfirm = onClose,
            onDismiss = { confirmDiscard = false },
        )
    }
}

private fun spacingLabel(context: android.content.Context, value: Float): String = context.getString(
    when {
        value < 21f -> R.string.templates_spacing_narrow
        value <= 33f -> R.string.templates_spacing_normal
        else -> R.string.templates_spacing_wide
    },
    value.toInt(),
)

/** Colour choices as round swatches; the chosen one has a ring and a tick. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SwatchRow(title: String, colors: List<Int>, selected: Int, enabled: Boolean, tag: (Int) -> String, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            colors.forEachIndexed { i, argb ->
                val isSelected = argb == selected
                val name = stringResource(R.string.templates_colour_n, i + 1)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable(enabled = enabled, role = Role.RadioButton) { onPick(argb) }
                        .semantics {
                            contentDescription = name
                            this.selected = isSelected
                        }
                        .testTag(tag(i)),
                ) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Color(argb)))
                    if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF1F1F1F), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun PageFormat.shortLabel(): Int = when (this) {
    PageFormat.A4_PORTRAIT -> R.string.page_format_a4_portrait_short
    PageFormat.A4_LANDSCAPE -> R.string.page_format_a4_landscape_short
    PageFormat.LONG_20_9 -> R.string.page_format_long_short
}

private fun HeaderStyle.label(): Int = when (this) {
    HeaderStyle.NONE -> R.string.templates_header_none
    HeaderStyle.TITLE -> R.string.templates_header_title
    HeaderStyle.TITLE_AND_DATE -> R.string.templates_header_title_date
}

private fun Ruling.label(): Int = when (this) {
    Ruling.NONE -> R.string.ink_template_blank
    Ruling.LINES -> R.string.ink_template_lined
    Ruling.GRID -> R.string.ink_template_grid
    Ruling.DOTS -> R.string.templates_ruling_dot
}
