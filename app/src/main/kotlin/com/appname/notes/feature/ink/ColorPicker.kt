package com.appname.notes.feature.ink

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.appname.notes.R
import kotlin.math.roundToInt

object ColorPickerTags {
    const val DIALOG = "color_picker"
    const val TAB_PALETTES = "color_tab_palettes"
    const val TAB_CUSTOM = "color_tab_custom"
    const val SV = "color_sv"
    const val HUE = "color_hue"
    const val HEX = "color_hex"
    const val RED = "color_red"
    const val GREEN = "color_green"
    const val BLUE = "color_blue"
    const val PREVIEW = "color_preview"
    const val SAVE = "color_save"
    const val USE = "color_use"
    fun swatch(argb: Int) = "color_swatch_${Integer.toHexString(argb and 0xFFFFFF).padStart(6, '0')}"
    fun saved(argb: Int) = "color_saved_${Integer.toHexString(argb and 0xFFFFFF).padStart(6, '0')}"
}

/** Ready-made palettes (owner brief §14): a basic grid, greys, pastels, deep tones and neons. */
internal object ColorPalettes {
    data class Palette(@param:StringRes val name: Int, val colors: List<Int>)

    private fun of(vararg rgb: Long) = rgb.map { (0xFF000000 or it).toInt() }

    val BASIC = Palette(
        R.string.color_palette_basic,
        of(
            0x000000, 0x434343, 0xD93025, 0xE8710A, 0xF9AB00, 0xFDD663, 0x188038, 0x34A853,
            0x12B5CB, 0x1A73E8, 0x174EA6, 0x9334E6, 0xE52592, 0x795548, 0xA142F4, 0xFFFFFF,
        ),
    )
    val GREYS = Palette(R.string.color_palette_greys, of(0x000000, 0x202124, 0x3C4043, 0x5F6368, 0x80868B, 0x9AA0A6, 0xBDC1C6, 0xDADCE0, 0xF1F3F4, 0xFFFFFF))
    val PASTEL = Palette(R.string.color_palette_pastel, of(0xF6AEA9, 0xFBCB9A, 0xFDE293, 0xCCFF90, 0xA8DAB5, 0xA7FFEB, 0xAECBFA, 0xD7AEFB, 0xFDCFE8, 0xE6C9A8))
    val DEEP = Palette(R.string.color_palette_deep, of(0x5C0011, 0x7A2E0E, 0x6B4E00, 0x0D3B1E, 0x00363D, 0x0B2A5B, 0x1A1446, 0x3B0A45, 0x4A0E2E, 0x3E2723))
    val NEON = Palette(R.string.color_palette_neon, of(0xFF1744, 0xFF9100, 0xFFEA00, 0x76FF03, 0x00E676, 0x1DE9B6, 0x00E5FF, 0x2979FF, 0xD500F9, 0xFF4081))

    val ALL = listOf(BASIC, GREYS, PASTEL, DEEP, NEON)
}

/** Colour maths for the custom picker, kept free of Android so it can be tested. */
internal object ColorMath {
    /** "#RRGGBB" for an ARGB colour. */
    fun hex(argb: Int): String = "#" + Integer.toHexString(argb and 0xFFFFFF).padStart(6, '0').uppercase()

    /** Reads "#RGB", "RGB", "#RRGGBB" or "RRGGBB" (any case); null if it isn't one of those. Opaque result. */
    fun parseHex(text: String): Int? {
        val t = text.trim().removePrefix("#")
        val full = when {
            t.length == 3 && t.all { it.isHex() } -> t.map { "$it$it" }.joinToString("")
            t.length == 6 && t.all { it.isHex() } -> t
            else -> return null
        }
        return (0xFF000000 or full.toLong(16)).toInt()
    }

    private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** HSV (hue 0..360, saturation and value 0..1) → opaque ARGB. */
    fun hsvToColor(h: Float, s: Float, v: Float): Int {
        val hue = ((h % 360f) + 360f) % 360f
        val c = v.coerceIn(0f, 1f) * s.coerceIn(0f, 1f)
        val x = c * (1 - kotlin.math.abs((hue / 60f) % 2 - 1))
        val m = v.coerceIn(0f, 1f) - c
        val (r, g, b) = when ((hue / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return rgb(((r + m) * 255).roundToInt(), ((g + m) * 255).roundToInt(), ((b + m) * 255).roundToInt())
    }

    /** ARGB → floatArrayOf(hue 0..360, saturation 0..1, value 0..1). */
    fun colorToHsv(argb: Int): FloatArray {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        return floatArrayOf((h + 360f) % 360f, if (max == 0f) 0f else d / max, max)
    }
}

/**
 * The full colour picker (owner brief §14): ready-made palettes, recent and saved colours, and a custom colour
 * from a saturation/brightness square and hue bar, HEX, or red/green/blue. Opacity is set in the tool panel.
 * Long-pressing a saved colour removes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerDialog(
    initial: Int,
    recent: List<Int>,
    saved: List<Int>,
    onPick: (Int) -> Unit,
    onSave: (Int) -> Unit,
    onRemoveSaved: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var color by rememberSaveable { mutableIntStateOf(initial or OPAQUE) }
    var custom by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 480.dp)
                .semantics { testTagsAsResourceId = true }
                .testTag(ColorPickerTags.DIALOG),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.color_picker_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(color))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                            .semantics { contentDescription = ColorMath.hex(color) }
                            .testTag(ColorPickerTags.PREVIEW),
                    )
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    SegmentedButton(
                        selected = !custom,
                        onClick = { custom = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag(ColorPickerTags.TAB_PALETTES),
                    ) { Text(stringResource(R.string.color_tab_palettes)) }
                    SegmentedButton(
                        selected = custom,
                        onClick = { custom = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag(ColorPickerTags.TAB_CUSTOM),
                    ) { Text(stringResource(R.string.color_tab_custom)) }
                }

                if (custom) {
                    CustomColor(color, onColor = { color = it })
                } else {
                    if (recent.isNotEmpty()) Swatches(stringResource(R.string.color_recent), recent, color, onPick = { color = it })
                    SavedSwatches(saved, color, onPick = { color = it }, onRemove = onRemoveSaved)
                    ColorPalettes.ALL.forEach { p -> Swatches(stringResource(p.name), p.colors, color, onPick = { color = it }) }
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { onSave(color) },
                        enabled = color !in saved,
                        modifier = Modifier.testTag(ColorPickerTags.SAVE),
                    ) {
                        Icon(Icons.Outlined.BookmarkAdd, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.color_save))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = { onPick(color) }, modifier = Modifier.testTag(ColorPickerTags.USE)) { Text(stringResource(R.string.color_use)) }
                }
            }
        }
    }
}

@Composable
private fun Swatches(title: String, colors: List<Int>, current: Int, onPick: (Int) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        colors.forEach { c -> Swatch(c, selected = (c and RGB) == (current and RGB), tag = ColorPickerTags.swatch(c), onClick = { onPick(c) }) }
    }
}

@Composable
private fun SavedSwatches(saved: List<Int>, current: Int, onPick: (Int) -> Unit, onRemove: (Int) -> Unit) {
    Text(stringResource(R.string.color_saved), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
    if (saved.isEmpty()) {
        Text(
            stringResource(R.string.color_saved_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        return
    }
    val removeLabel = stringResource(R.string.color_saved_remove)
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        saved.forEach { c ->
            Swatch(c, selected = (c and RGB) == (current and RGB), tag = ColorPickerTags.saved(c), onClick = { onPick(c) }, onLongClick = { onRemove(c) }, longClickLabel = removeLabel)
        }
    }
}

@Composable
private fun Swatch(argb: Int, selected: Boolean, tag: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, longClickLabel: String? = null) {
    val name = ColorMath.hex(argb)
    Box(
        Modifier
            .size(48.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = longClickLabel)
            .semantics {
                contentDescription = name
                role = Role.RadioButton
                this.selected = selected
            }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier.border(1.dp, Color(0x33000000), CircleShape)
                    },
                )
                .padding(if (selected) 5.dp else 1.dp)
                .background(Color(argb or OPAQUE), CircleShape),
        )
    }
}

@Composable
private fun CustomColor(color: Int, onColor: (Int) -> Unit) {
    // Hue is kept separately so it isn't lost on greys (where the colour alone has no hue).
    val start = remember { ColorMath.colorToHsv(color) }
    var hue by rememberSaveable { mutableFloatStateOf(start[0]) }
    val hsv = ColorMath.colorToHsv(color)
    val sat = hsv[1]
    val value = hsv[2]
    val svLabel = stringResource(R.string.color_sv_description)

    // Saturation (left → right) and brightness (bottom → top) of the hue.
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(180.dp)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        fun pick(o: Offset) = onColor(ColorMath.hsvToColor(hue, (o.x / w).coerceIn(0f, 1f), 1f - (o.y / h).coerceIn(0f, 1f)))
        Canvas(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(hue, w, h) { detectTapGestures { pick(it) } }
                .pointerInput(hue, w, h) { detectDragGestures { change, _ -> pick(change.position) } }
                .semantics { contentDescription = svLabel }
                .testTag(ColorPickerTags.SV),
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color(ColorMath.hsvToColor(hue, 1f, 1f)))))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        }
        val knob = 24.dp
        Box(
            Modifier
                .offset { IntOffset((sat * w).roundToInt() - knob.roundToPx() / 2, ((1f - value) * h).roundToInt() - knob.roundToPx() / 2) }
                .size(knob)
                .border(3.dp, Color.White, CircleShape)
                .padding(3.dp)
                .background(Color(color), CircleShape),
        )
    }

    // Hue: a rainbow bar under an ordinary (accessible) slider.
    LabelledSlider(stringResource(R.string.color_hue), "${hue.roundToInt()}°", ColorPickerTags.HUE) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(HueStops.map { Color(ColorMath.hsvToColor(it, 1f, 1f)) })),
            )
            Slider(
                value = hue,
                onValueChange = {
                    hue = it
                    onColor(ColorMath.hsvToColor(it, if (sat == 0f && value > 0f) 1f else sat, if (value == 0f) 1f else value))
                },
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
            )
        }
    }

    // HEX.
    // The field follows the colour when it changes elsewhere, but never rewrites what is being typed.
    var hexText by rememberSaveable { mutableStateOf(ColorMath.hex(color)) }
    LaunchedEffect(color) { if (ColorMath.parseHex(hexText) != color) hexText = ColorMath.hex(color) }
    val parsed = ColorMath.parseHex(hexText)
    OutlinedTextField(
        value = hexText,
        onValueChange = { t ->
            hexText = t.take(HEX_MAX)
            ColorMath.parseHex(hexText)?.let {
                hue = ColorMath.colorToHsv(it)[0]
                onColor(it)
            }
        },
        label = { Text(stringResource(R.string.color_hex)) },
        isError = parsed == null,
        supportingText = if (parsed == null) ({ Text(stringResource(R.string.color_hex_invalid)) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).testTag(ColorPickerTags.HEX),
    )

    // Red, green, blue.
    val r = color shr 16 and 0xFF
    val g = color shr 8 and 0xFF
    val b = color and 0xFF
    fun set(c: Int) {
        hue = ColorMath.colorToHsv(c)[0]
        onColor(c)
    }
    ChannelSlider(R.string.color_red, r, Color(0xFFD93025), ColorPickerTags.RED) { set(ColorMath.rgb(it, g, b)) }
    ChannelSlider(R.string.color_green, g, Color(0xFF188038), ColorPickerTags.GREEN) { set(ColorMath.rgb(r, it, b)) }
    ChannelSlider(R.string.color_blue, b, Color(0xFF1A73E8), ColorPickerTags.BLUE) { set(ColorMath.rgb(r, g, it)) }
}

@Composable
private fun ChannelSlider(@StringRes name: Int, value: Int, tint: Color, tag: String, onValue: (Int) -> Unit) {
    LabelledSlider(stringResource(name), value.toString(), tag) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.roundToInt()) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint),
        )
    }
}

@Composable
private fun LabelledSlider(label: String, value: String, tag: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp).testTag(tag)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

private val HueStops = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f)
private const val OPAQUE = 0xFF shl 24
private const val RGB = 0xFFFFFF
private const val HEX_MAX = 7
