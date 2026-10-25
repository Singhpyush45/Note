package com.appname.notes.feature.ink

import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Mic
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appname.notes.R
import com.appname.notes.core.ink.model.TextAlign
import com.appname.notes.core.ink.model.TextFont
import kotlin.math.roundToInt

object InsertTags {
    const val BUTTON = "ink_insert"
    const val PHOTO = "ink_insert_photo"
    const val CAMERA = "ink_insert_camera"
    const val TEXT = "ink_insert_text"
    const val PDF = "ink_insert_pdf"
    const val PASTE = "ink_insert_paste"
    const val RECORD = "ink_insert_record"
    const val AUDIO_FILE = "ink_insert_audio_file"
    const val STICKER = "ink_insert_sticker"
    const val TABLE = "ink_insert_table"
    const val TABLE_INSERT = "ink_table_insert"
    fun sticker(i: Int) = "ink_sticker_$i"
    fun math(s: String) = "ink_math_$s"
    const val TEXT_MOVE = "ink_text_move"
    const val EQUATION = "ink_insert_equation"
    const val EQUATION_FIELD = "ink_equation_field"
    const val EQUATION_INSERT = "ink_equation_insert"
    const val TEXT_BAR = "ink_text_bar"
    const val TEXT_DIALOG = "ink_text_dialog"
    const val TEXT_FIELD = "ink_text_field"
    const val TEXT_SAVE = "ink_text_save"
    fun font(f: TextFont) = "ink_text_font_${f.name.lowercase()}"
}

/** What the Insert (+) menu can do on a handwritten page (owner request 8a). */
data class InsertActions(
    val photo: () -> Unit,
    val camera: (() -> Unit)?,
    val text: () -> Unit,
    val pdf: () -> Unit,
    val paste: () -> Unit,
    val record: () -> Unit = {},
    val audioFile: () -> Unit = {},
    val sticker: () -> Unit = {},
    val table: () -> Unit = {},
    val equation: () -> Unit = {},
)

/** The Insert button in the top bar: photo, camera, text box, PDF pages, paste. */
@Composable
fun InsertButton(actions: InsertActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag(InsertTags.BUTTON)) {
            Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.insert_menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.insert_photo)) },
                leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, null) },
                onClick = { open = false; actions.photo() },
                modifier = Modifier.testTag(InsertTags.PHOTO),
            )
            actions.camera?.let { camera ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.insert_camera)) },
                    leadingIcon = { Icon(Icons.Outlined.CameraAlt, null) },
                    onClick = { open = false; camera() },
                    modifier = Modifier.testTag(InsertTags.CAMERA),
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.insert_text)) },
                leadingIcon = { Icon(Icons.Outlined.TextFields, null) },
                onClick = { open = false; actions.text() },
                modifier = Modifier.testTag(InsertTags.TEXT),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.insert_pdf)) },
                leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) },
                onClick = { open = false; actions.pdf() },
                modifier = Modifier.testTag(InsertTags.PDF),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.insert_sticker)) },
                leadingIcon = { Icon(Icons.Outlined.EmojiEmotions, null) },
                onClick = { open = false; actions.sticker() },
                modifier = Modifier.testTag(InsertTags.STICKER),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.insert_table)) },
                leadingIcon = { Icon(Icons.Outlined.TableChart, null) },
                onClick = { open = false; actions.table() },
                modifier = Modifier.testTag(InsertTags.TABLE),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.insert_equation)) },
                leadingIcon = { Icon(Icons.Outlined.Functions, null) },
                onClick = { open = false; actions.equation() },
                modifier = Modifier.testTag(InsertTags.EQUATION),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.audio_record)) },
                leadingIcon = { Icon(Icons.Outlined.Mic, null) },
                onClick = { open = false; actions.record() },
                modifier = Modifier.testTag(InsertTags.RECORD),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.insert_audio_file)) },
                leadingIcon = { Icon(Icons.Outlined.AudioFile, null) },
                onClick = { open = false; actions.audioFile() },
                modifier = Modifier.testTag(InsertTags.AUDIO_FILE),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.insert_paste)) },
                leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) },
                onClick = { open = false; actions.paste() },
                modifier = Modifier.testTag(InsertTags.PASTE),
            )
        }
    }
}

/** What's on the clipboard that can go on a page: picture/PDF links, or text. */
data class ClipContent(val uris: List<Uri>, val text: String?, val label: String? = null)

/** Reads pictures (content links) and text from the clipboard, or from a dropped [clip]. */
fun clipContent(context: Context, clip: ClipData? = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip): ClipContent {
    if (clip == null) return ClipContent(emptyList(), null)
    val uris = ArrayList<Uri>()
    val text = StringBuilder()
    for (i in 0 until clip.itemCount) {
        val item = clip.getItemAt(i)
        val uri = item.uri
        if (uri != null) {
            uris += uri
        } else {
            val t = item.coerceToText(context)?.toString().orEmpty()
            if (t.isNotBlank()) {
                if (text.isNotEmpty()) text.append('\n')
                text.append(t)
            }
        }
    }
    return ClipContent(uris, text.toString().ifBlank { null }, clip.description?.label?.toString())
}

/** Whether a drag carries something a page can take. */
fun acceptsDrop(description: ClipDescription?): Boolean {
    if (description == null) return false
    return (0 until description.mimeTypeCount).any {
        val m = description.getMimeType(it)
        m.startsWith("image/") || m == "application/pdf" || m.startsWith("text/") || m == ClipDescription.MIMETYPE_TEXT_URILIST
    }
}

internal fun TextFont.composeFamily(): FontFamily = when (this) {
    TextFont.SANS -> FontFamily.SansSerif
    TextFont.SERIF -> FontFamily.Serif
    TextFont.MONO -> FontFamily.Monospace
    TextFont.CURSIVE -> FontFamily.Cursive
    TextFont.CASUAL -> FontFamily(android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL))
    TextFont.CONDENSED -> FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
    TextFont.ROUNDED -> FontFamily(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))
}

internal fun TextFont.label(): Int = when (this) {
    TextFont.SANS -> R.string.font_sans
    TextFont.SERIF -> R.string.font_serif
    TextFont.MONO -> R.string.font_mono
    TextFont.CURSIVE -> R.string.font_cursive
    TextFont.CASUAL -> R.string.font_casual
    TextFont.CONDENSED -> R.string.font_condensed
    TextFont.ROUNDED -> R.string.font_medium
}

/** Stickers: emoji that read well large, grouped loosely (study, feelings, marks). */
internal val Stickers = listOf(
    "⭐", "✅", "❌", "❗", "❓", "💡", "📌", "📎", "📚", "✏️", "📝", "🎯", "⏰", "📅", "🔥", "⚠️",
    "👍", "👎", "👏", "🙏", "💪", "🙂", "😂", "😍", "🤔", "😮", "😢", "😴", "🎉", "❤️", "💯", "🚀",
    "➡️", "⬅️", "⬆️", "⬇️", "✔️", "✳️", "🔵", "🟢", "🟡", "🔴", "🟣", "⚫", "🏆", "🌟", "☀️", "🌙",
)

/** Picks a sticker. */
@Composable
fun StickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.insert_sticker)) },
        text = {
            androidx.compose.foundation.layout.FlowRow(Modifier.verticalScroll(rememberScrollState())) {
                Stickers.forEachIndexed { i, s ->
                    Box(
                        Modifier
                            .size(52.dp)
                            .clickable(role = Role.Button) { onPick(s) }
                            .semantics { contentDescription = s }
                            .testTag(InsertTags.sticker(i)),
                        contentAlignment = Alignment.Center,
                    ) { Text(s, fontSize = 28.sp) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Table grid size: rows and columns, each 1–12. */
@Composable
fun TableDialog(onInsert: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var rows by rememberSaveable { mutableStateOf(3) }
    var cols by rememberSaveable { mutableStateOf(3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.insert_table)) },
        text = {
            Column {
                Stepper(stringResource(R.string.table_rows), rows) { rows = it }
                Stepper(stringResource(R.string.table_columns), cols) { cols = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onInsert(rows, cols) }, modifier = Modifier.testTag(InsertTags.TABLE_INSERT)) { Text(stringResource(R.string.table_insert)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, onValue: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("$label: $value", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { onValue((value - 1).coerceAtLeast(1)) }, enabled = value > 1) { Text("−", fontSize = 22.sp) }
        TextButton(onClick = { onValue((value + 1).coerceAtMost(12)) }, enabled = value < 12) { Text("+", fontSize = 22.sp) }
    }
}

/** Symbols for writing equations in a text box (Unicode, so they show in every font and export). */
internal val MathSymbols = listOf(
    "+", "−", "×", "÷", "=", "≠", "≈", "≤", "≥", "±", "√", "∛", "∞", "π", "θ", "α", "β", "γ", "Δ", "λ", "μ", "σ", "Σ", "Ω",
    "∫", "∑", "∏", "∂", "∇", "→", "⇒", "⇔", "∈", "∉", "⊂", "∪", "∩", "∀", "∃", "°", "²", "³", "ⁿ", "⁻¹", "₀", "₁", "₂", "½", "¼", "⅓",
)

/** Equation snippets for the quick buttons: what is shown, and what goes into the equation. */
private val EquationSnippets = listOf(
    "x²" to "^{2}", "xₙ" to "_{n}", "a⁄b" to "\\frac{a}{b}", "√" to "\\sqrt{x}", "∛" to "\\sqrt[3]{x}", "∑" to "\\sum_{i=1}^{n} ",
    "∫" to "\\int_{a}^{b} ", "lim" to "\\lim_{x \\to 0} ", "α" to "\\alpha ", "β" to "\\beta ", "θ" to "\\theta ", "π" to "\\pi ",
    "Δ" to "\\Delta ", "λ" to "\\lambda ", "μ" to "\\mu ", "≤" to "\\leq ", "≥" to "\\geq ", "≠" to "\\neq ", "≈" to "\\approx ",
    "±" to "\\pm ", "×" to "\\times ", "·" to "\\cdot ", "→" to "\\to ", "∞" to "\\infty ", "∂" to "\\partial ", "sin" to "\\sin ",
    "log" to "\\log ",
)

/**
 * Insert › Equation (8c): typed in the familiar LaTeX style with buttons for the usual pieces, shown as it will
 * look while typing; placed on the page as a picture (move and resize it like a photo).
 */
@Composable
fun EquationDialog(onInsert: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    val ink = MaterialTheme.colorScheme.onSurface.toArgb()
    val density = androidx.compose.ui.platform.LocalDensity.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.insert_equation)) },
        text = {
            Column {
                // Preview, scaled down to fit when long.
                val box = remember(value.text, ink) { MathRender.layout(value.text, with(density) { 26.dp.toPx() }, ink) }
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                    val b = box ?: return@Canvas
                    val scale = minOf(1f, size.width / b.width.coerceAtLeast(1f), size.height / (b.ascent + b.descent).coerceAtLeast(1f))
                    drawIntoCanvas { c ->
                        val nc = c.nativeCanvas
                        nc.save()
                        nc.translate((size.width - b.width * scale) / 2, (size.height - (b.ascent + b.descent) * scale) / 2)
                        nc.scale(scale, scale)
                        b.draw(nc, 0f, b.ascent)
                        nc.restore()
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { Text("x^2 + \\frac{a}{b}") },
                    textStyle = ComposeTextStyle(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().testTag(InsertTags.EQUATION_FIELD),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    EquationSnippets.forEach { (label, snippet) ->
                        TextButton(onClick = {
                            val t = value.text
                            val sel = value.selection
                            val next = t.substring(0, sel.min) + snippet + t.substring(sel.max)
                            value = androidx.compose.ui.text.input.TextFieldValue(next, androidx.compose.ui.text.TextRange(sel.min + snippet.length))
                        }) { Text(label, fontSize = 17.sp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onInsert(value.text) }, enabled = value.text.isNotBlank(), modifier = Modifier.testTag(InsertTags.EQUATION_INSERT)) {
                Text(stringResource(R.string.equation_insert))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
