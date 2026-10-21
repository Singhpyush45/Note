package com.appname.notes.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.appname.notes.core.designsystem.theme.AppTheme

/*
 * Settings building blocks. The whole row is the touch target (≥48dp) and carries the accessibility
 * role, so TalkBack reads "Dynamic colour, switch, on" instead of two separate items (SRS 8.2).
 */

@Composable
fun SettingsSectionHeader(text: String, modifier: Modifier = Modifier) {
    val spacing = AppTheme.spacing
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = spacing.lg, end = spacing.lg, top = spacing.xl, bottom = spacing.sm)
            .semantics { heading() },
    )
}

@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val spacing = AppTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minTouchTarget + spacing.sm)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        RowText(title = title, summary = summary, enabled = enabled, modifier = Modifier.weight(1f))
        // onCheckedChange = null: the row handles clicks, so the switch isn't a second focus stop.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A single-choice group. Wrap rows in [SettingsRadioGroup] so TalkBack announces "1 of 3". */
@Composable
fun SettingsRadioGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.selectableGroup()) { content() }
}

@Composable
fun SettingsRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    val spacing = AppTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minTouchTarget + spacing.sm)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        RadioButton(selected = selected, onClick = null)
        RowText(title = title, summary = summary, enabled = true, modifier = Modifier.weight(1f))
    }
}

/** Read-only label/value row, e.g. "Version — 0.1.0". */
@Composable
fun SettingsInfoRow(title: String, value: String, modifier: Modifier = Modifier) {
    val spacing = AppTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minTouchTarget + spacing.sm)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title = title, summary = value, enabled = true, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RowText(title: String, summary: String?, enabled: Boolean, modifier: Modifier) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
    }
}

private const val DISABLED_ALPHA = 0.38f
