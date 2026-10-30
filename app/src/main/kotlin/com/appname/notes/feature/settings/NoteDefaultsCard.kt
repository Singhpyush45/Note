package com.appname.notes.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.SettingsCard
import com.appname.notes.core.designsystem.component.SettingsLinkRow
import com.appname.notes.core.designsystem.component.SettingsSegmentedRow
import com.appname.notes.core.designsystem.component.SettingsSwitchRow
import com.appname.notes.core.ink.layout.PageMode
import com.appname.notes.core.ink.layout.ScrollAxis
import com.appname.notes.core.ink.template.BuiltInTemplates
import com.appname.notes.core.ink.template.PageDesign
import com.appname.notes.core.ink.template.PageFormat
import com.appname.notes.core.settings.BlankNoteKind
import com.appname.notes.core.settings.NoteDefaults
import com.appname.notes.feature.ink.label
import com.appname.notes.feature.templates.TemplatePreview

/**
 * Settings › Note defaults (owner brief §5, §19): what Blank note makes, and the page style, size, layout,
 * scroll direction and paper new handwritten notes start with. Plus dark paper in dark theme.
 */
@Composable
fun NoteDefaultsCard(defaults: NoteDefaults, onChange: ((NoteDefaults) -> NoteDefaults) -> Unit) {
    var choosingStyle by rememberSaveable { mutableStateOf(false) }
    SettingsCard(stringResource(R.string.settings_section_note_defaults), Modifier.testTag(SettingsTestTags.DEFAULTS), icon = Icons.Outlined.Description) {
        val kinds = BlankNoteKind.entries
        SettingsSegmentedRow(
            title = stringResource(R.string.settings_blank_note),
            options = listOf(stringResource(R.string.settings_blank_typed), stringResource(R.string.settings_blank_handwriting)),
            selectedIndex = kinds.indexOf(defaults.blankNote),
            onSelect = { i -> onChange { it.copy(blankNote = kinds[i]) } },
            optionTag = { SettingsTestTags.blankNote(kinds[it]) },
        )
        val style = BuiltInTemplates.byId(defaults.pageStyle)
        SettingsLinkRow(
            title = stringResource(R.string.settings_default_style),
            summary = style?.name,
            onClick = { choosingStyle = true },
            modifier = Modifier.testTag(SettingsTestTags.DEFAULT_STYLE),
            trailing = {
                Box(Modifier.width(30.dp).height(40.dp)) { TemplatePreview(defaults.newPageLayout(), Modifier.fillMaxWidth().height(40.dp)) }
            },
        )
        val formats = PageFormat.entries
        SettingsSegmentedRow(
            title = stringResource(R.string.settings_default_size),
            options = formats.map { stringResource(it.label()) },
            selectedIndex = formats.indexOf(defaults.pageFormat),
            onSelect = { i -> onChange { it.copy(pageFormat = formats[i]) } },
            optionTag = { SettingsTestTags.defaultFormat(formats[it]) },
        )
        val modes = PageMode.entries
        SettingsSegmentedRow(
            title = stringResource(R.string.page_settings_pages),
            options = listOf(stringResource(R.string.page_mode_split), stringResource(R.string.page_mode_infinite)),
            selectedIndex = modes.indexOf(defaults.pageMode),
            onSelect = { i -> onChange { it.copy(pageMode = modes[i]) } },
            optionTag = { SettingsTestTags.defaultMode(modes[it]) },
        )
        val axes = ScrollAxis.entries
        SettingsSegmentedRow(
            title = stringResource(R.string.settings_default_scroll),
            options = listOf(stringResource(R.string.page_axis_vertical), stringResource(R.string.page_axis_horizontal)),
            selectedIndex = axes.indexOf(defaults.scrollAxis),
            onSelect = { i -> onChange { it.copy(scrollAxis = axes[i]) } },
            optionTag = { SettingsTestTags.defaultAxis(axes[it]) },
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.settings_default_paper), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                PageDesign.PALETTES.first().colors.forEachIndexed { i, c ->
                    val selected = c == defaults.paperColor
                    val label = stringResource(R.string.templates_colour_n, i + 1)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable(role = Role.RadioButton) { onChange { d -> d.copy(paperColor = c) } }
                            .semantics {
                                contentDescription = label
                                this.selected = selected
                            }
                            .testTag(SettingsTestTags.defaultPaper(i)),
                    ) {
                        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF1F1F1F), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        SettingsSwitchRow(
            title = stringResource(R.string.settings_dark_paper),
            summary = stringResource(R.string.settings_dark_paper_summary),
            checked = defaults.darkPaper,
            onCheckedChange = { on -> onChange { it.copy(darkPaper = on) } },
            modifier = Modifier.testTag(SettingsTestTags.DARK_PAPER),
        )
    }
    if (choosingStyle) {
        AlertDialog(
            onDismissRequest = { choosingStyle = false },
            title = { Text(stringResource(R.string.settings_default_style)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NoteDefaults.STYLES.forEach { id ->
                        val t = BuiltInTemplates.byId(id) ?: return@forEach
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.RadioButton) { onChange { it.copy(pageStyle = id) }; choosingStyle = false }
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(selected = id == defaults.pageStyle, onClick = null)
                            Text(t.name, modifier = Modifier.padding(start = 8.dp).weight(1f))
                            TemplatePreview(t.layout, Modifier.width(28.dp).height(38.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingStyle = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}
