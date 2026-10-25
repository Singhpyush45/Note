package com.appname.notes.feature.editor

import androidx.compose.material.icons.automirrored.outlined.KeyboardTab
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.PlaylistRemove
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TravelExplore
import com.appname.notes.core.domain.model.AudioMarker
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.AlarmAdd
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.appname.notes.R
import com.appname.notes.core.domain.document.BlockKind
import com.appname.notes.core.domain.document.SpanType

/**
 * Formatting buttons above the keyboard. Buttons can't take focus, so tapping them never closes the
 * keyboard or moves the cursor out of the note.
 */
@Composable
fun FormatToolbar(
    controller: EditorController,
    modifier: Modifier = Modifier,
    onAudioMarker: (AudioMarker) -> Unit = {},
    /** "Remind me about this task", shown while the cursor is on a checklist item (FR-010). */
    onRemindTask: (() -> Unit)? = null,
    /** Voice typing (§11); first in the toolbar so it's always visible. */
    onVoiceTyping: (() -> Unit)? = null,
    /** "Clip page" for a web link (8c). */
    onClipLink: ((String) -> Unit)? = null,
) {
    val active = controller.activeStyles
    val kind = controller.currentKind
    val linkUrl = controller.currentLinkUrl
    var showLinkDialog by rememberSaveable { mutableStateOf(false) }
    val toolbarLabel = stringResource(R.string.fmt_toolbar)
    val uriHandler = LocalUriHandler.current

    Surface(tonalElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp)
                .testTag(EditorTestTags.TOOLBAR)
                .semantics { contentDescription = toolbarLabel },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onVoiceTyping != null) {
                val voiceLabel = stringResource(R.string.voice_typing)
                IconButton(onClick = onVoiceTyping, modifier = noFocus.testTag(EditorTestTags.VOICE).semantics { contentDescription = voiceLabel }) {
                    Icon(Icons.Outlined.Mic, contentDescription = null)
                }
                VerticalDivider(Modifier.padding(horizontal = 4.dp).height(24.dp))
            }
            StyleButton(Icons.Outlined.FormatBold, R.string.fmt_bold, SpanType.BOLD in active) { controller.toggleStyle(SpanType.BOLD) }
            StyleButton(Icons.Outlined.FormatItalic, R.string.fmt_italic, SpanType.ITALIC in active) { controller.toggleStyle(SpanType.ITALIC) }
            StyleButton(Icons.Outlined.FormatUnderlined, R.string.fmt_underline, SpanType.UNDERLINE in active) { controller.toggleStyle(SpanType.UNDERLINE) }
            StyleButton(Icons.Outlined.FormatStrikethrough, R.string.fmt_strike, SpanType.STRIKETHROUGH in active) { controller.toggleStyle(SpanType.STRIKETHROUGH) }
            StyleButton(Icons.Outlined.Code, R.string.fmt_code, SpanType.CODE in active) { controller.toggleStyle(SpanType.CODE) }
            val marker = linkUrl?.let(AudioMarker::parse)
            if (marker != null) {
                // An audio marker (FR-012): play the recording from that moment.
                val label = stringResource(R.string.audio_play_from, marker.label)
                IconButton(onClick = { onAudioMarker(marker) }, modifier = noFocus.semantics { contentDescription = label }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                }
                ToolbarIcon(Icons.Outlined.LinkOff, R.string.fmt_remove_link) { controller.removeLink() }
            } else if (linkUrl != null) {
                ToolbarIcon(Icons.AutoMirrored.Outlined.OpenInNew, R.string.fmt_open_link) { runCatching { uriHandler.openUri(linkUrl) } }
                if (onClipLink != null && (linkUrl.startsWith("https://") || linkUrl.startsWith("http://"))) {
                    ToolbarIcon(Icons.Outlined.TravelExplore, R.string.clip_page, tag = EditorTestTags.CLIP) { onClipLink(linkUrl) }
                }
                ToolbarIcon(Icons.Outlined.LinkOff, R.string.fmt_remove_link) { controller.removeLink() }
            } else {
                ToolbarIcon(Icons.Outlined.Link, R.string.fmt_link, enabled = controller.hasSelection) { showLinkDialog = true }
            }
            VerticalDivider(Modifier.padding(horizontal = 4.dp).height(24.dp))
            HeadingButton("H1", R.string.fmt_h1, kind == BlockKind.HEADING1) { controller.toggleKind(BlockKind.HEADING1) }
            HeadingButton("H2", R.string.fmt_h2, kind == BlockKind.HEADING2) { controller.toggleKind(BlockKind.HEADING2) }
            HeadingButton("H3", R.string.fmt_h3, kind == BlockKind.HEADING3) { controller.toggleKind(BlockKind.HEADING3) }
            StyleButton(Icons.AutoMirrored.Outlined.FormatListBulleted, R.string.fmt_bullet, kind == BlockKind.BULLET) { controller.toggleKind(BlockKind.BULLET) }
            StyleButton(Icons.Outlined.FormatListNumbered, R.string.fmt_numbered, kind == BlockKind.NUMBERED) { controller.toggleKind(BlockKind.NUMBERED) }
            StyleButton(Icons.Outlined.CheckBox, R.string.fmt_checklist, kind == BlockKind.CHECKLIST) { controller.toggleKind(BlockKind.CHECKLIST) }
            if (kind == BlockKind.CHECKLIST && onRemindTask != null) {
                val remindLabel = stringResource(R.string.reminder_add_task)
                IconButton(onClick = onRemindTask, modifier = noFocus.testTag(EditorTestTags.REMIND_TASK).semantics { contentDescription = remindLabel }) {
                    Icon(Icons.Outlined.AlarmAdd, contentDescription = null)
                }
            }
            StyleButton(Icons.Outlined.FormatQuote, R.string.fmt_quote, kind == BlockKind.QUOTE) { controller.toggleKind(BlockKind.QUOTE) }
            VerticalDivider(Modifier.padding(horizontal = 4.dp).height(24.dp))
            if (controller.inTable) {
                // In a table: next cell, add/remove rows and columns.
                ToolbarIcon(Icons.AutoMirrored.Outlined.KeyboardTab, R.string.table_next_cell, tag = EditorTestTags.TABLE_NEXT) { controller.tableNextCell() }
                ToolbarIcon(Icons.Outlined.TableRows, R.string.table_add_row, tag = EditorTestTags.TABLE_ADD_ROW) { controller.tableAddRow() }
                ToolbarIcon(Icons.Outlined.ViewColumn, R.string.table_add_column, tag = EditorTestTags.TABLE_ADD_COLUMN) { controller.tableAddColumn() }
                ToolbarIcon(Icons.Outlined.PlaylistRemove, R.string.table_delete_row) { controller.tableDeleteRow() }
                ToolbarIcon(Icons.Outlined.DeleteSweep, R.string.table_delete_column) { controller.tableDeleteColumn() }
            } else {
                ToolbarIcon(Icons.Outlined.TableChart, R.string.typed_table_insert, tag = EditorTestTags.TABLE_INSERT) { controller.insertTable(3, 3) }
            }
        }
    }

    if (showLinkDialog) {
        LinkDialog(onDismiss = { showLinkDialog = false }, onConfirm = { controller.setLink(it); showLinkDialog = false })
    }
}

private val noFocus = Modifier.focusProperties { canFocus = false }

@Composable
private fun StyleButton(icon: ImageVector, label: Int, checked: Boolean, onClick: () -> Unit) {
    IconToggleButton(checked = checked, onCheckedChange = { onClick() }, modifier = noFocus) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

@Composable
private fun ToolbarIcon(icon: ImageVector, label: Int, enabled: Boolean = true, tag: String? = null, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = noFocus.then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

@Composable
private fun HeadingButton(text: String, label: Int, checked: Boolean, onClick: () -> Unit) {
    val description = stringResource(label)
    IconToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        modifier = noFocus.semantics { contentDescription = description },
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/** Asks for a URL; adds https:// when the user types just a domain. */
@Composable
private fun LinkDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    val normalized = normalizeUrl(url)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_dialog_title)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it.trim() },
                label = { Text(stringResource(R.string.link_dialog_hint)) },
                singleLine = true,
                isError = url.isNotEmpty() && normalized == null,
                supportingText = if (url.isNotEmpty() && normalized == null) {
                    { Text(stringResource(R.string.link_dialog_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        },
        confirmButton = {
            TextButton(onClick = { normalized?.let(onConfirm) }, enabled = normalized != null) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** "example.com" → "https://example.com"; rejects text that clearly isn't an address. */
internal fun normalizeUrl(input: String): String? {
    val s = input.trim()
    if (s.isEmpty() || s.any { it.isWhitespace() }) return null
    val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(s)) s else "https://$s"
    val scheme = withScheme.substringBefore(':').lowercase()
    if (scheme !in setOf("http", "https", "mailto", "tel")) return null
    if ((scheme == "http" || scheme == "https") && !withScheme.substringAfter("://").substringBefore('/').contains('.')) return null
    return withScheme
}
