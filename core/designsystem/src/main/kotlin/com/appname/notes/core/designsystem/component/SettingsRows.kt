package com.appname.notes.core.designsystem.component

import androidx.compose.foundation.clickable
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

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

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

/** A row that performs an action when tapped (e.g. "Rebuild search index"). */
@Composable
fun SettingsActionRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, summary: String? = null) {
    val spacing = AppTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minTouchTarget + spacing.sm)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

/** A group of settings on a glass card (owner brief §19), with its heading above. */
@Composable
fun SettingsCard(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val spacing = AppTheme.spacing
    Column(modifier.fillMaxWidth().padding(horizontal = spacing.lg)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = spacing.xs, top = spacing.xl, bottom = spacing.sm).semantics { heading() },
            )
        }
        GlassSurface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, elevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(vertical = spacing.xs), content = content)
        }
    }
}

/**
 * A slider with its title and current value ("60%"). The value is saved when the finger lifts
 * ([onValueCommitted]); while dragging, the label follows the finger.
 */
@Composable
fun SettingsSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    onValueCommitted: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val spacing = AppTheme.spacing
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: value
    Column(modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowText(title = title, summary = summary, enabled = enabled, modifier = Modifier.weight(1f))
            Text(
                valueLabel(shown),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
            )
        }
        GlassSlider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let(onValueCommitted); dragging = null },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

/**
 * A calm slider in the glass style (owner brief §1): a slim rounded track filled with the accent up to the value,
 * and a round white thumb with a soft shadow and a hairline edge — instead of Material's heavy default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    val active = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else DISABLED_ALPHA)
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val edge = MaterialTheme.colorScheme.outlineVariant
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        steps = steps,
        valueRange = valueRange,
        thumb = {
            Box(
                Modifier
                    .size(24.dp)
                    .shadow(3.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .border(1.dp, edge, CircleShape),
            )
        },
        track = { state ->
            val range = state.valueRange
            val fraction = if (range.endInclusive > range.start) {
                ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            } else {
                0f
            }
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val r = CornerRadius(size.height / 2, size.height / 2)
                drawRoundRect(inactive, cornerRadius = r)
                drawRoundRect(active, size = Size(size.width * fraction, size.height), cornerRadius = r)
            }
        },
    )
}

/** One choice out of a few, as a segmented button row ("Light | Dark | System", "Compact | Comfortable"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSegmentedRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    optionTag: (Int) -> String = { "segment_$it" },
) {
    val spacing = AppTheme.spacing
    Column(modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.sm)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = spacing.sm)) {
            options.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = i == selectedIndex,
                    onClick = { onSelect(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    modifier = Modifier.testTag(optionTag(i)),
                ) { Text(label, maxLines = 1) }
            }
        }
    }
}

/** A row that opens something, showing what's chosen on the right (e.g. the wallpaper's thumbnail). */
@Composable
fun SettingsLinkRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    val spacing = AppTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minTouchTarget + spacing.sm)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        RowText(title = title, summary = summary, enabled = true, modifier = Modifier.weight(1f))
        trailing()
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
