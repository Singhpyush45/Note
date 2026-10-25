package com.appname.notes.feature.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.SettingsSegmentedRow
import com.appname.notes.core.designsystem.component.SettingsSwitchRow
import com.appname.notes.core.ink.layout.PageArrangement
import com.appname.notes.core.ink.layout.PageMode
import com.appname.notes.core.ink.layout.ScrollAxis
import com.appname.notes.core.ink.template.PageDesign
import com.appname.notes.core.ink.template.PageFormat
import com.appname.notes.core.ink.template.Ruling
import com.appname.notes.core.ink.template.SpacingPreset
import com.appname.notes.core.ink.template.TemplateLayout

object PageSettingsTags {
    const val DIALOG = "page_settings"
    const val SCOPE_ALL = "page_scope_all"
    const val SCOPE_PAGE = "page_scope_page"
    const val DARK_PAPER = "page_dark_paper"
    const val CUSTOM_USE = "page_custom_colour_use"
    const val DONE = "page_settings_done"
    fun mode(m: PageMode) = "page_mode_${m.name.lowercase()}"
    fun axis(a: ScrollAxis) = "page_axis_${a.name.lowercase()}"
    fun format(f: PageFormat) = "page_format_${f.name.lowercase()}"
    fun ruling(r: Ruling) = "page_ruling_${r.name.lowercase()}"
    fun spacing(s: SpacingPreset) = "page_spacing_${s.name.lowercase()}"
    fun paper(palette: Int, i: Int) = "page_paper_${palette}_$i"
}

/** What the page settings can change. */
data class PageSettingsActions(
    val onArrangement: (PageArrangement) -> Unit,
    val onFormat: (PageFormat) -> Unit,
    val onBackground: (Ruling, SpacingPreset, allPages: Boolean) -> Unit,
    val onPaperColor: (Int, allPages: Boolean) -> Unit,
    val onDarkPaper: (Boolean) -> Unit,
)

/**
 * Page settings for one note (owner brief §5). Layout (split pages / infinite scroll), scroll direction and page
 * size are separate choices; background, spacing and paper colour apply to this page or all pages. PDF notes
 * keep the document's pages, so only the layout and direction apply. Every choice applies at once.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PageSettingsDialog(
    arrangement: PageArrangement,
    format: PageFormat?,
    design: TemplateLayout?,
    darkPaper: Boolean,
    isPdf: Boolean,
    actions: PageSettingsActions,
    onDismiss: () -> Unit,
) {
    var allPages by rememberSaveable { mutableStateOf(true) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 560.dp)
                .heightIn(max = 720.dp)
                // A dialog is its own window: expose test tags as resource ids here too.
                .semantics { testTagsAsResourceId = true }
                .testTag(PageSettingsTags.DIALOG),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                Text(
                    stringResource(R.string.page_settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() },
                )

                Section(R.string.page_settings_layout)
                val modes = PageMode.entries
                SettingsSegmentedRow(
                    title = stringResource(R.string.page_settings_pages),
                    options = listOf(stringResource(R.string.page_mode_split), stringResource(R.string.page_mode_infinite)),
                    selectedIndex = modes.indexOf(arrangement.mode),
                    onSelect = { actions.onArrangement(arrangement.copy(mode = modes[it])) },
                    optionTag = { PageSettingsTags.mode(modes[it]) },
                )
                val axes = ScrollAxis.entries
                SettingsSegmentedRow(
                    title = stringResource(R.string.page_settings_scroll),
                    options = listOf(stringResource(R.string.page_axis_vertical), stringResource(R.string.page_axis_horizontal)),
                    selectedIndex = axes.indexOf(arrangement.axis),
                    onSelect = { actions.onArrangement(arrangement.copy(axis = axes[it])) },
                    optionTag = { PageSettingsTags.axis(axes[it]) },
                )

                if (isPdf || format == null || design == null) {
                    Text(
                        stringResource(R.string.page_settings_pdf_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                } else {
                    Section(R.string.page_settings_size)
                    val formats = PageFormat.entries
                    SettingsSegmentedRow(
                        title = stringResource(R.string.page_settings_size_all),
                        options = formats.map { stringResource(it.label()) },
                        selectedIndex = formats.indexOf(format),
                        onSelect = { actions.onFormat(formats[it]) },
                        optionTag = { PageSettingsTags.format(formats[it]) },
                    )

                    Section(R.string.page_settings_background)
                    SettingsSegmentedRow(
                        title = stringResource(R.string.page_settings_apply_to),
                        options = listOf(stringResource(R.string.page_scope_page), stringResource(R.string.page_scope_all)),
                        selectedIndex = if (allPages) 1 else 0,
                        onSelect = { allPages = it == 1 },
                        optionTag = { if (it == 0) PageSettingsTags.SCOPE_PAGE else PageSettingsTags.SCOPE_ALL },
                    )
                    val rulings = Ruling.entries
                    val ruling = design.paper.ruling
                    val preset = PageDesign.preset(ruling, design.paper.spacing)
                    SettingsSegmentedRow(
                        title = stringResource(R.string.page_settings_style),
                        options = rulings.map { stringResource(it.label()) },
                        // A template design isn't a plain style: nothing is selected then.
                        selectedIndex = if (PageDesign.hasDesign(design)) -1 else rulings.indexOf(ruling),
                        onSelect = { actions.onBackground(rulings[it], preset, allPages) },
                        optionTag = { PageSettingsTags.ruling(rulings[it]) },
                    )
                    if (PageDesign.hasDesign(design)) {
                        Text(
                            stringResource(R.string.page_settings_template_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    } else if (ruling != Ruling.NONE) {
                        val presets = SpacingPreset.entries
                        SettingsSegmentedRow(
                            title = stringResource(R.string.page_settings_spacing),
                            options = presets.map { stringResource(it.label()) },
                            selectedIndex = presets.indexOf(preset),
                            onSelect = { actions.onBackground(ruling, presets[it], allPages) },
                            optionTag = { PageSettingsTags.spacing(presets[it]) },
                        )
                    }

                    Section(R.string.page_settings_paper)
                    PageDesign.PALETTES.forEachIndexed { p, palette ->
                        Text(
                            stringResource(palette.labelRes()),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 24.dp, top = 8.dp),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                        ) {
                            palette.colors.forEachIndexed { i, c ->
                                PaperSwatch(
                                    color = c,
                                    selected = c == design.paperColor,
                                    label = stringResource(R.string.page_paper_colour_n, stringResource(palette.labelRes()), i + 1),
                                    tag = PageSettingsTags.paper(p, i),
                                    onClick = { actions.onPaperColor(c, allPages) },
                                )
                            }
                        }
                    }
                    CustomPaper(onUse = { actions.onPaperColor(it, allPages) })
                }

                Section(R.string.page_settings_dark)
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dark_paper),
                    summary = stringResource(R.string.settings_dark_paper_summary),
                    checked = darkPaper,
                    onCheckedChange = actions.onDarkPaper,
                    modifier = Modifier.testTag(PageSettingsTags.DARK_PAPER),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag(PageSettingsTags.DONE)) { Text(stringResource(R.string.action_done)) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 2.dp).semantics { heading() },
    )
}

@Composable
private fun PaperSwatch(color: Int, selected: Boolean, label: String, tag: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .testTag(tag),
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF1F1F1F), modifier = Modifier.size(18.dp))
    }
}

/** Any paper colour: a hue and how strong the tint is; it always stays light enough to write on. */
@Composable
private fun CustomPaper(onUse: (Int) -> Unit) {
    var hue by rememberSaveable { mutableFloatStateOf(40f) }
    var tint by rememberSaveable { mutableFloatStateOf(0.5f) }
    val color = PageDesign.customPaper(hue, tint)
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.page_paper_custom), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.page_paper_hue), style = MaterialTheme.typography.bodySmall)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text(stringResource(R.string.page_paper_tint), style = MaterialTheme.typography.bodySmall)
                Slider(value = tint, onValueChange = { tint = it }, valueRange = 0f..1f)
            }
            Box(
                Modifier
                    .padding(start = 12.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onUse(color) }, modifier = Modifier.testTag(PageSettingsTags.CUSTOM_USE)) {
                Text(stringResource(R.string.page_paper_use))
            }
        }
    }
}

internal fun PageFormat.label(): Int = when (this) {
    PageFormat.A4_PORTRAIT -> R.string.page_format_a4_portrait_short
    PageFormat.A4_LANDSCAPE -> R.string.page_format_a4_landscape_short
    PageFormat.LONG_20_9 -> R.string.page_format_long_short
}

private fun Ruling.label(): Int = when (this) {
    Ruling.NONE -> R.string.ink_template_blank
    Ruling.LINES -> R.string.ink_template_lined
    Ruling.GRID -> R.string.ink_template_grid
    Ruling.DOTS -> R.string.templates_ruling_dot
}

private fun SpacingPreset.label(): Int = when (this) {
    SpacingPreset.NARROW -> R.string.page_spacing_narrow
    SpacingPreset.NORMAL -> R.string.page_spacing_normal
    SpacingPreset.WIDE -> R.string.page_spacing_wide
}

private fun com.appname.notes.core.ink.template.PaperPalette.labelRes(): Int = when (name) {
    "warm" -> R.string.page_palette_warm
    "cool" -> R.string.page_palette_cool
    "neutral" -> R.string.page_palette_neutral
    else -> R.string.page_palette_pastel
}
