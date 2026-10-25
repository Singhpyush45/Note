package com.appname.notes.feature.ink

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Brush as BrushIcon
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withSave
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.GlassSurface
import com.appname.notes.core.ink.input.EditorTool
import com.appname.notes.core.ink.input.EraserMode
import com.appname.notes.core.ink.model.BrushStyle
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.StrokeItem
import com.appname.notes.core.ink.model.StrokePoints
import com.appname.notes.core.settings.LaserSettings
import com.appname.notes.core.settings.LaserStyle
import kotlin.math.PI
import kotlin.math.sin

object InkToolTags {
    const val PANEL = "ink_tool_panel"
    const val PANEL_CLOSE = "ink_tool_panel_close"
    const val VARIANT_CHIP = "ink_variant_chip"
    const val SIZE = "ink_tool_size"
    const val OPACITY = "ink_tool_opacity"
    const val PRESSURE = "ink_tool_pressure"
    const val MORE_COLORS = "ink_more_colors"
    fun variant(s: BrushStyle) = "ink_variant_${s.name.lowercase()}"
    fun recent(i: Int) = "ink_recent_$i"
    fun color(argb: Int) = "ink_color_${Integer.toHexString(argb and 0xFFFFFF)}"
    const val LASER_RAINBOW = "ink_laser_rainbow"
    fun laserStyle(s: LaserStyle) = "ink_laser_${s.name.lowercase()}"
}

/** Everything the tool bar and its variation panel can change. */
data class InkToolActions(
    val onTool: (EditorTool) -> Unit,
    val onStyle: (BrushStyle) -> Unit,
    val onColor: (Int) -> Unit,
    val onSize: (Float) -> Unit,
    val onOpacity: (Float) -> Unit,
    val onPressure: (Boolean) -> Unit,
    val onRecent: (InkBrush) -> Unit,
    val onMoreColors: () -> Unit,
    val onEraserMode: (EraserMode) -> Unit,
    val onEraserSize: (Double) -> Unit,
    val onDeleteSelection: () -> Unit,
    val onDuplicateSelection: () -> Unit,
    val onRecolorSelection: (Int) -> Unit,
    val onDoneSelection: () -> Unit,
    val onLaser: (LaserSettings) -> Unit,
)

/** The tools in the bar, in order (owner brief §13). Only tools that work are shown. */
private val BarTools = listOf(
    EditorTool.PEN, EditorTool.MARKER, EditorTool.PENCIL, EditorTool.BRUSH, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.LASSO, EditorTool.LASER,
)

/** Laser widths in dp: thin, medium, bold. */
internal val LaserWidths = listOf(4f, 6f, 10f)

internal val LaserColors = listOf(0xFFFF3B30, 0xFFFF9500, 0xFFFFEA00, 0xFF00E676, 0xFF00E5FF, 0xFF2979FF, 0xFFD500F9, 0xFFFFFFFF).map { it.toInt() }

/** A small, well-spread colour row for one tap; the full picker has everything else. */
internal val QuickColors = listOf(
    0xFF1F1F1F, 0xFF5F6368, 0xFFD93025, 0xFFE8710A, 0xFFF9AB00, 0xFF188038, 0xFF1A73E8, 0xFF9334E6, 0xFFE52592, 0xFF795548,
).map { it.toInt() }

internal val HighlighterQuickColors = listOf(0xFFFFEB3B, 0xFF76FF03, 0xFF40C4FF, 0xFFFF80AB, 0xFFFFAB40, 0xFFB388FF).map { it.toInt() }

private val EraserSizes = listOf(10.0, 16.0, 28.0)
private val SizeNames = listOf(R.string.ink_size_fine, R.string.ink_size_medium, R.string.ink_size_bold)

/**
 * The drawing tool bar (owner brief §13): tool buttons, each drawing tool marked with its current colour; the
 * chosen tool's variant, sizes and colours. Tapping the chosen tool again (or its variant) opens its
 * variation panel ([VariationPanel]), shown above this bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkToolbar(
    ui: InkEditorUi,
    actions: InkToolActions,
    panelOpen: Boolean,
    onPanel: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tools = ui.tools
    GlassSurface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), elevation = 4.dp, modifier = modifier.testTag(InkTestTags.TOOLBAR)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            ) {
                BarTools.forEach { tool ->
                    val mark: Brush? = when {
                        tool == EditorTool.LASER -> if (ui.laser.rainbow) RainbowBrush else SolidColor(Color(ui.laser.colorArgb or OPAQUE))
                        else -> tools.brushFor(tool)?.let { SolidColor(Color(it.colorArgb or OPAQUE)) }
                    }
                    ToolButton(
                        tool = tool,
                        selected = tools.tool == tool,
                        mark = mark,
                        onClick = { if (tools.tool == tool && tool.inkTool != null) onPanel(!panelOpen) else actions.onTool(tool) },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp).height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val inkTool = tools.tool.inkTool
                when {
                    inkTool != null -> {
                        val brush = tools.brushes[inkTool] ?: tools.brushFor(tools.tool)!!
                        VariantChip(brush.effectiveStyle, panelOpen, onClick = { onPanel(!panelOpen) })
                        val base = brush.effectiveStyle.defaultSize
                        listOf(0.6f, 1f, 1.8f).forEachIndexed { i, f ->
                            val size = (base * f * 10).toInt() / 10f
                            SizeDot(f / 1.8f, SizeNames[i], selected = kotlin.math.abs(brush.size - size) < 0.05f, onClick = { actions.onSize(size) })
                        }
                        Spacer(Modifier.width(6.dp))
                        val palette = (ui.recentColors + if (inkTool == InkTool.HIGHLIGHTER) HighlighterQuickColors else QuickColors)
                            .map { it or OPAQUE }.distinct().take(QUICK_COLOURS)
                        palette.forEach { c ->
                            ColorDot(c, selected = (brush.colorArgb and 0xFFFFFF) == (c and 0xFFFFFF), onClick = { actions.onColor(c) })
                        }
                        MoreColorsButton(actions.onMoreColors)
                    }
                    tools.tool == EditorTool.ERASER -> {
                        SingleChoiceSegmentedButtonRow {
                            EraserMode.entries.forEachIndexed { i, mode ->
                                val description = stringResource(
                                    if (mode == EraserMode.STROKE) R.string.ink_eraser_stroke_description else R.string.ink_eraser_area_description,
                                )
                                SegmentedButton(
                                    selected = tools.eraserMode == mode,
                                    onClick = { actions.onEraserMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(i, EraserMode.entries.size),
                                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
                                ) {
                                    Text(stringResource(if (mode == EraserMode.STROKE) R.string.ink_eraser_stroke else R.string.ink_eraser_area), maxLines = 1)
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        EraserSizes.forEachIndexed { i, size ->
                            SizeDot((size / EraserSizes.last()).toFloat(), SizeNames[i], selected = tools.eraserSizeDp == size, onClick = { actions.onEraserSize(size) })
                        }
                    }
                    tools.tool == EditorTool.LASER -> LaserControls(ui.laser, actions)
                    tools.tool == EditorTool.LASSO -> if (ui.hasSelection) {
                        IconButton(onClick = actions.onDoneSelection) { Icon(Icons.Filled.Check, stringResource(R.string.ink_selection_done)) }
                        IconButton(onClick = actions.onDeleteSelection) { Icon(Icons.Filled.Delete, stringResource(R.string.ink_selection_delete)) }
                        IconButton(onClick = actions.onDuplicateSelection) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.ink_selection_duplicate)) }
                        Spacer(Modifier.width(8.dp))
                        QuickColors.forEach { c -> ColorDot(c, selected = false, onClick = { actions.onRecolorSelection(c) }) }
                    }
                }
            }
        }
    }
}

/**
 * The variation panel (owner brief §13): the tool's variants with a stroke preview each, size, opacity,
 * pressure, a live preview of the current set-up, and recently used set-ups. Closing keeps everything.
 */
@Composable
fun VariationPanel(ui: InkEditorUi, actions: InkToolActions, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val tool = ui.tools.tool.inkTool ?: return
    val brush = ui.tools.brushes[tool] ?: ui.tools.brushFor(ui.tools.tool) ?: return
    val style = brush.effectiveStyle
    GlassSurface(
        shape = RoundedCornerShape(24.dp),
        elevation = 6.dp,
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            // Touches on the panel never reach the page under it.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .testTag(InkToolTags.PANEL),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(tool.label()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(style.label()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onClose, modifier = Modifier.testTag(InkToolTags.PANEL_CLOSE)) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.ink_panel_close))
                }
            }
            // The current set-up, live.
            StrokePreview(brush, Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp))

            // Variants of this tool (and the pen's markers, the brush's calligraphy …): one tile each.
            val styles = BrushStyle.of(tool)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                styles.forEach { s -> VariantTile(s, brush, selected = s == style, onClick = { actions.onStyle(s) }) }
            }

            // Size and opacity.
            val maxSize = style.defaultSize * 3f
            val minSize = (style.defaultSize * 0.3f).coerceAtLeast(0.5f)
            var size by remember(brush.size, style) { mutableFloatStateOf(brush.size.coerceIn(minSize, maxSize)) }
            SliderRow(
                label = stringResource(R.string.ink_panel_size),
                value = stringResource(R.string.ink_panel_size_value, size),
                tag = InkToolTags.SIZE,
            ) {
                Slider(value = size, onValueChange = { size = it }, valueRange = minSize..maxSize, onValueChangeFinished = { actions.onSize(size) })
            }
            val alpha = (brush.colorArgb ushr 24) / 255f
            var opacity by remember(brush.colorArgb) { mutableFloatStateOf(alpha) }
            val maxOpacity = if (tool == InkTool.HIGHLIGHTER) HIGHLIGHTER_MAX_OPACITY else 1f
            SliderRow(
                label = stringResource(R.string.ink_panel_opacity),
                value = stringResource(R.string.ink_panel_percent, (opacity * 100).toInt()),
                tag = InkToolTags.OPACITY,
            ) {
                Slider(value = opacity.coerceIn(0.1f, maxOpacity), onValueChange = { opacity = it }, valueRange = 0.1f..maxOpacity, onValueChangeFinished = { actions.onOpacity(opacity) })
            }

            // Pressure (a stylus reports it; a finger draws evenly anyway).
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ink_panel_pressure), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.ink_panel_pressure_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = brush.pressure, onCheckedChange = actions.onPressure, modifier = Modifier.testTag(InkToolTags.PRESSURE))
            }

            if (ui.recentBrushes.isNotEmpty()) {
                Text(
                    stringResource(R.string.ink_panel_recent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ui.recentBrushes.forEachIndexed { i, b ->
                        val name = stringResource(R.string.ink_panel_recent_item, stringResource(b.effectiveStyle.label()))
                        Box(
                            Modifier
                                .size(width = 76.dp, height = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(InkPaints.PAPER))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button) { actions.onRecent(b) }
                                .semantics { contentDescription = name }
                                .testTag(InkToolTags.recent(i)),
                        ) { StrokePreview(b, Modifier.size(width = 76.dp, height = 48.dp).padding(6.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: String, tag: String, slider: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(tag)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        slider()
    }
}

@Composable
private fun VariantTile(style: BrushStyle, current: InkBrush, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val name = stringResource(style.label())
    // Each tile shows this variant in the current colour, at its own natural size.
    val sample = InkBrush(style.tool, current.colorArgb, style.defaultSize, style, current.pressure)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .testTag(InkToolTags.variant(style))
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(InkPaints.PAPER))) {
            StrokePreview(sample, Modifier.fillMaxWidth().height(40.dp).padding(4.dp))
        }
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** A sample S-curve drawn with [brush], pressing harder in the middle, as the real stroke would look. */
@Composable
internal fun StrokePreview(brush: InkBrush, modifier: Modifier = Modifier) {
    val styled = remember { StyledInk(cache = false) }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    Canvas(modifier) {
        val n = 40
        val w = PREVIEW_W
        val h = PREVIEW_H
        val x = FloatArray(n) { it * w / (n - 1) }
        val y = FloatArray(n) { h / 2 + sin(it / (n - 1f) * 2 * PI).toFloat() * h * 0.3f }
        val pressure = FloatArray(n) { 0.25f + 0.7f * sin(it / (n - 1f) * PI).toFloat() }
        val item = StrokeItem("preview", 0, brush, StrokePoints(x, y, IntArray(n) { it * 8 }, pressure))
        val scale = minOf(size.width / w, size.height / h)
        drawIntoCanvas { c ->
            c.nativeCanvas.withSave {
                translate((size.width - w * scale) / 2, (size.height - h * scale) / 2)
                scale(scale, scale)
                paint.set(InkPaints.inkPaint(brush))
                InkPaths.drawItem(this, item, paint, styled)
            }
        }
    }
}

@Composable
private fun VariantChip(style: BrushStyle, open: Boolean, onClick: () -> Unit) {
    val label = stringResource(style.label())
    val state = stringResource(if (open) R.string.ink_panel_state_open else R.string.ink_panel_state_closed)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.ink_panel_open), onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = state }
            .testTag(InkToolTags.VARIANT_CHIP)
            .padding(horizontal = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
        Icon(if (open) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Laser pointer options (owner brief §17): look, rainbow ("RGB") or one colour, and width. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LaserControls(laser: LaserSettings, actions: InkToolActions) {
    SingleChoiceSegmentedButtonRow {
        LaserStyle.entries.forEachIndexed { i, style ->
            SegmentedButton(
                selected = laser.style == style,
                onClick = { actions.onLaser(laser.copy(style = style)) },
                shape = SegmentedButtonDefaults.itemShape(i, LaserStyle.entries.size),
                modifier = Modifier.heightIn(min = 48.dp).testTag(InkToolTags.laserStyle(style)),
            ) { Text(stringResource(style.label()), maxLines = 1) }
        }
    }
    Spacer(Modifier.width(6.dp))
    val rainbowLabel = stringResource(R.string.laser_rainbow)
    FilterChip(
        selected = laser.rainbow,
        onClick = { actions.onLaser(laser.copy(rainbow = !laser.rainbow)) },
        label = { Text(rainbowLabel) },
        leadingIcon = { Box(Modifier.size(16.dp).clip(CircleShape).background(RainbowBrush)) },
        modifier = Modifier.heightIn(min = 48.dp).testTag(InkToolTags.LASER_RAINBOW),
    )
    Spacer(Modifier.width(4.dp))
    LaserWidths.forEachIndexed { i, w ->
        SizeDot(w / LaserWidths.last(), SizeNames[i], selected = laser.widthDp == w, onClick = { actions.onLaser(laser.copy(widthDp = w)) })
    }
    if (!laser.rainbow) {
        Spacer(Modifier.width(4.dp))
        LaserColors.forEach { c ->
            ColorDot(c, selected = (laser.colorArgb and 0xFFFFFF) == (c and 0xFFFFFF), onClick = { actions.onLaser(laser.copy(colorArgb = c)) })
        }
        MoreColorsButton(actions.onMoreColors)
    }
}

@Composable
private fun ToolButton(tool: EditorTool, selected: Boolean, mark: Brush?, onClick: () -> Unit) {
    val label = stringResource(tool.label())
    Box(contentAlignment = Alignment.BottomCenter) {
        FilledIconToggleButton(
            checked = selected,
            onCheckedChange = { onClick() },
            modifier = Modifier.testTag(InkTestTags.tool(tool)),
        ) {
            Icon(tool.icon(), label)
        }
        // The tool's current colour, as on a real pen cap.
        if (mark != null) {
            Box(
                Modifier
                    .padding(bottom = 2.dp)
                    .size(width = 14.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(mark),
            )
        }
    }
}

/** 48 dp touch target with a 28 dp colour dot (SRS 8.2). */
@Composable
internal fun ColorDot(argb: Int, selected: Boolean, onClick: () -> Unit, label: String? = null) {
    val hex = "#" + Integer.toHexString(argb and 0xFFFFFF).padStart(6, '0').uppercase()
    val name = label ?: ColorNames[argb and 0xFFFFFF]?.let { stringResource(it) } ?: stringResource(R.string.ink_color_hex, hex)
    val ring = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = name
                role = Role.RadioButton
                this.selected = selected
            }
            .testTag(InkToolTags.color(argb)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .then(if (selected) Modifier.border(3.dp, ring, CircleShape) else Modifier.border(1.dp, Color(0x33000000), CircleShape))
                .padding(if (selected) 5.dp else 1.dp)
                .background(Color(argb or OPAQUE), CircleShape),
        )
    }
}

@Composable
private fun MoreColorsButton(onClick: () -> Unit) {
    // 48 dp even when scrolled out of the row (an icon button is 40 dp with a 48 dp touch area).
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).testTag(InkToolTags.MORE_COLORS)) {
        Icon(Icons.Outlined.Palette, stringResource(R.string.ink_more_colors))
    }
}

@Composable
private fun SizeDot(fraction: Float, @StringRes name: Int, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(name)
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .then(if (selected) Modifier.background(colors.secondaryContainer, CircleShape) else Modifier)
            .semantics {
                contentDescription = label
                role = Role.RadioButton
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size((6 + 16 * fraction).dp).background(colors.onSurface, CircleShape))
    }
}

private fun EditorTool.icon(): ImageVector = when (this) {
    EditorTool.PEN -> Icons.Outlined.Edit
    EditorTool.MARKER -> Icons.Outlined.BorderColor
    EditorTool.PENCIL -> Icons.Outlined.Create
    EditorTool.BRUSH -> Icons.Outlined.BrushIcon
    EditorTool.HIGHLIGHTER -> InkIcons.Highlighter
    EditorTool.ERASER -> InkIcons.Eraser
    EditorTool.LASSO -> InkIcons.Lasso
    EditorTool.LASER -> InkIcons.Laser
}

@StringRes
private fun EditorTool.label(): Int = when (this) {
    EditorTool.PEN -> R.string.ink_tool_pen
    EditorTool.MARKER -> R.string.ink_tool_marker
    EditorTool.PENCIL -> R.string.ink_tool_pencil
    EditorTool.BRUSH -> R.string.ink_tool_brush
    EditorTool.HIGHLIGHTER -> R.string.ink_tool_highlighter
    EditorTool.ERASER -> R.string.ink_tool_eraser
    EditorTool.LASSO -> R.string.ink_tool_lasso
    EditorTool.LASER -> R.string.ink_tool_laser
}

@StringRes
private fun LaserStyle.label(): Int = when (this) {
    LaserStyle.SOLID -> R.string.laser_style_solid
    LaserStyle.OUTLINE -> R.string.laser_style_outline
    LaserStyle.DOT -> R.string.laser_style_dot
}

/** Spoken names for the quick colours (other colours are read as their hex code). */
private val ColorNames: Map<Int, Int> = mapOf(
    0x1F1F1F to R.string.ink_color_black, 0x5F6368 to R.string.ink_color_gray, 0xD93025 to R.string.ink_color_red,
    0xE8710A to R.string.ink_color_orange, 0xF9AB00 to R.string.ink_color_yellow, 0x188038 to R.string.ink_color_green,
    0x1A73E8 to R.string.ink_color_blue, 0x9334E6 to R.string.ink_color_purple, 0xE52592 to R.string.ink_color_pink,
    0x795548 to R.string.ink_color_brown, 0xFFEB3B to R.string.ink_color_yellow, 0x76FF03 to R.string.ink_color_green,
    0x40C4FF to R.string.ink_color_blue, 0xFF80AB to R.string.ink_color_pink, 0xFFAB40 to R.string.ink_color_orange,
    0xB388FF to R.string.ink_color_purple, 0xFF3B30 to R.string.ink_color_red, 0xFF9500 to R.string.ink_color_orange,
    0xFFEA00 to R.string.ink_color_yellow, 0x00E676 to R.string.ink_color_green, 0x00E5FF to R.string.ink_color_cyan,
    0x2979FF to R.string.ink_color_blue, 0xD500F9 to R.string.ink_color_purple, 0xFFFFFF to R.string.ink_color_white,
)

private val RainbowBrush = Brush.horizontalGradient(listOf(0xFFFF1744, 0xFFFFEA00, 0xFF00E676, 0xFF00E5FF, 0xFF2979FF, 0xFFD500F9, 0xFFFF1744).map { Color(it) })

@StringRes
internal fun InkTool.label(): Int = when (this) {
    InkTool.PEN -> R.string.ink_tool_pen
    InkTool.MARKER -> R.string.ink_tool_marker
    InkTool.PENCIL -> R.string.ink_tool_pencil
    InkTool.BRUSH -> R.string.ink_tool_brush
    InkTool.HIGHLIGHTER -> R.string.ink_tool_highlighter
}

@StringRes
internal fun BrushStyle.label(): Int = when (this) {
    BrushStyle.BALLPOINT -> R.string.brush_ballpoint
    BrushStyle.FINE_PEN -> R.string.brush_fine_pen
    BrushStyle.GEL_PEN -> R.string.brush_gel_pen
    BrushStyle.FOUNTAIN_PEN -> R.string.brush_fountain_pen
    BrushStyle.FINELINER -> R.string.brush_fineliner
    BrushStyle.TECHNICAL_PEN -> R.string.brush_technical_pen
    BrushStyle.DOT_PEN -> R.string.brush_dot_pen
    BrushStyle.FELT_MARKER -> R.string.brush_felt_marker
    BrushStyle.CHISEL_MARKER -> R.string.brush_chisel_marker
    BrushStyle.CHISEL_HIGHLIGHTER -> R.string.brush_chisel_highlighter
    BrushStyle.ROUND_HIGHLIGHTER -> R.string.brush_round_highlighter
    BrushStyle.FINE_HIGHLIGHTER -> R.string.brush_fine_highlighter
    BrushStyle.NATURAL_PENCIL -> R.string.brush_natural_pencil
    BrushStyle.HB_PENCIL -> R.string.brush_hb_pencil
    BrushStyle.PENCIL_2B -> R.string.brush_2b_pencil
    BrushStyle.PENCIL_4B -> R.string.brush_4b_pencil
    BrushStyle.MECHANICAL_PENCIL -> R.string.brush_mechanical_pencil
    BrushStyle.SKETCH_PENCIL -> R.string.brush_sketch_pencil
    BrushStyle.GRAPHITE_PENCIL -> R.string.brush_graphite_pencil
    BrushStyle.BASIC_BRUSH -> R.string.brush_basic
    BrushStyle.SOFT_BRUSH -> R.string.brush_soft
    BrushStyle.CALLIGRAPHY_BRUSH -> R.string.brush_calligraphy_brush
    BrushStyle.CALLIGRAPHY_PEN -> R.string.brush_calligraphy_pen
    BrushStyle.AIRBRUSH -> R.string.brush_airbrush
    BrushStyle.OIL_BRUSH -> R.string.brush_oil
    BrushStyle.WATERCOLOUR_BRUSH -> R.string.brush_watercolour
    BrushStyle.CRAYON -> R.string.brush_crayon
    BrushStyle.CHALK -> R.string.brush_chalk
    BrushStyle.CHARCOAL -> R.string.brush_charcoal
    BrushStyle.TEXTURE_BRUSH -> R.string.brush_texture
    BrushStyle.ARTISTIC_BRUSH -> R.string.brush_artistic
    BrushStyle.MARKER_BRUSH -> R.string.brush_marker_brush
}

private const val OPAQUE = 0xFF shl 24
private const val QUICK_COLOURS = 10
private const val PREVIEW_W = 180f
private const val PREVIEW_H = 44f
internal const val HIGHLIGHTER_MAX_OPACITY = 0.7f
