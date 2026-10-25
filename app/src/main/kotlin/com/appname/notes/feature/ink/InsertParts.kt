package com.appname.notes.feature.ink

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
                text = { Text(stringResource(R.string.insert_paste)) },
                leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) },
                onClick = { open = false; actions.paste() },
                modifier = Modifier.testTag(InsertTags.PASTE),
            )
        }
    }
}

/** What's on the clipboard that can go on a page: picture/PDF links, or text. */
data class ClipContent(val uris: List<Uri>, val text: String?)

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
    return ClipContent(uris, text.toString().ifBlank { null })
}

/** Whether a drag carries something a page can take. */
fun acceptsDrop(description: ClipDescription?): Boolean {
    if (description == null) return false
    return (0 until description.mimeTypeCount).any {
        val m = description.getMimeType(it)
        m.startsWith("image/") || m == "application/pdf" || m.startsWith("text/") || m == ClipDescription.MIMETYPE_TEXT_URILIST
    }
}

/**
 * Writing or editing a text box (owner request: text with fonts on handwritten pages): the text, font (the phone's
 * own families), size, colour, bold, italic, alignment, with a live preview. Saving empty text removes the box.
 */
@Composable
fun TextBoxDialog(request: TextEditRequest, onSave: (String, TextStyle) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(request.text) }
    var font by rememberSaveable { mutableStateOf(request.style.font) }
    var size by rememberSaveable { mutableStateOf(request.style.size) }
    var color by rememberSaveable { mutableStateOf(request.style.colorArgb) }
    var bold by rememberSaveable { mutableStateOf(request.style.bold) }
    var italic by rememberSaveable { mutableStateOf(request.style.italic) }
    var align by rememberSaveable { mutableStateOf(request.style.align) }
    val style = TextStyle(font, size, color, bold, italic, align)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(InsertTags.TEXT_DIALOG),
        title = { Text(stringResource(if (request.existing == null) R.string.text_box_new else R.string.text_box_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = ComposeTextStyle(
                        fontFamily = font.composeFamily(),
                        fontSize = (size * 0.8f).coerceIn(12f, 36f).sp,
                        color = Color(color),
                        fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else null,
                        fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
                    ),
                    placeholder = { Text(stringResource(R.string.text_box_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag(InsertTags.TEXT_FIELD),
                )
                Text(stringResource(R.string.text_box_font), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextFont.entries.forEach { f ->
                        FilterChip(
                            selected = font == f,
                            onClick = { font = f },
                            label = { Text(stringResource(f.label()), fontFamily = f.composeFamily()) },
                            modifier = Modifier.testTag(InsertTags.font(f)),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.text_box_size, size.roundToInt()), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    IconToggleButton(checked = bold, onCheckedChange = { bold = it }) { Icon(Icons.Outlined.FormatBold, stringResource(R.string.text_box_bold)) }
                    IconToggleButton(checked = italic, onCheckedChange = { italic = it }) { Icon(Icons.Outlined.FormatItalic, stringResource(R.string.text_box_italic)) }
                }
                Slider(value = size, onValueChange = { size = it }, valueRange = 10f..72f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextAlign.entries.forEach { a ->
                        IconToggleButton(checked = align == a, onCheckedChange = { align = a }) {
                            Icon(
                                when (a) {
                                    TextAlign.START -> Icons.AutoMirrored.Outlined.FormatAlignLeft
                                    TextAlign.CENTER -> Icons.Outlined.FormatAlignCenter
                                    TextAlign.END -> Icons.AutoMirrored.Outlined.FormatAlignRight
                                },
                                stringResource(
                                    when (a) {
                                        TextAlign.START -> R.string.text_box_align_start
                                        TextAlign.CENTER -> R.string.text_box_align_center
                                        TextAlign.END -> R.string.text_box_align_end
                                    },
                                ),
                            )
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    (listOf(0xFF1F1F1F.toInt()) + QuickColors).distinct().forEach { c ->
                        val selected = (c and 0xFFFFFF) == (color and 0xFFFFFF)
                        val label = stringResource(R.string.ink_color_hex, "#" + Integer.toHexString(c and 0xFFFFFF).padStart(6, '0').uppercase())
                        Box(
                            Modifier
                                .size(44.dp)
                                .clickable(role = Role.RadioButton) { color = c or (0xFF shl 24) }
                                .semantics { contentDescription = label; this.selected = selected },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .border(if (selected) 2.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                                    .padding(if (selected) 4.dp else 1.dp)
                                    .background(Color(c), CircleShape),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text, style) }, modifier = Modifier.testTag(InsertTags.TEXT_SAVE)) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (request.existing != null) TextButton(onClick = { onSave("", style) }) { Text(stringResource(R.string.action_delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

private fun TextFont.composeFamily(): FontFamily = when (this) {
    TextFont.SANS -> FontFamily.SansSerif
    TextFont.SERIF -> FontFamily.Serif
    TextFont.MONO -> FontFamily.Monospace
    TextFont.CURSIVE -> FontFamily.Cursive
    TextFont.CASUAL -> FontFamily(android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL))
    TextFont.CONDENSED -> FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
    TextFont.ROUNDED -> FontFamily(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))
}

private fun TextFont.label(): Int = when (this) {
    TextFont.SANS -> R.string.font_sans
    TextFont.SERIF -> R.string.font_serif
    TextFont.MONO -> R.string.font_mono
    TextFont.CURSIVE -> R.string.font_cursive
    TextFont.CASUAL -> R.string.font_casual
    TextFont.CONDENSED -> R.string.font_condensed
    TextFont.ROUNDED -> R.string.font_medium
}
