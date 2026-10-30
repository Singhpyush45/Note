package com.appname.notes.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Lock
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.feature.pro.NoteProStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.stateDescription
import com.appname.notes.core.designsystem.component.GlassSurface
import com.appname.notes.feature.reminders.ReminderDates
import java.time.LocalDateTime
import java.time.ZoneId
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appname.notes.R
import com.appname.notes.core.designsystem.theme.AppTheme
import com.appname.notes.core.domain.model.NoteSummary
import com.appname.notes.core.domain.model.NoteType

/** One accessibility action per thing the long-press sheet can do (SRS 8.2: no gesture-only features). */
data class CardAction(val label: String, val run: () -> Unit)

/**
 * Note preview card on frosted glass (owner brief §1–2): title and when it was changed, a ⋮ for this note's actions,
 * then a preview (text, handwriting thumbnail or first photo) and its notebook, tags, attachments and reminder.
 * Tap opens; long-press starts selecting several notes ([onLongClick]); [checked] marks a selected one.
 * TalkBack users get every action through the "Actions" menu (custom accessibility actions).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteSummary,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    accessibilityActions: List<CardAction>,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    inkThumbnail: (suspend (NoteSummary) -> InkThumbnail?)? = null,
    /** Loads the cover photo (first attached image) by stored file name. */
    cover: (suspend (String) -> Bitmap?)? = null,
    /** The ⋮ button; null hides it (e.g. while selecting several notes). */
    onMore: (() -> Unit)? = null,
    /** Shown as selected in multi-select. */
    checked: Boolean = false,
    /** Home screen › view: small, big, extra big or a list row. */
    size: com.appname.notes.core.settings.CardSize = com.appname.notes.core.settings.CardSize.BIG,
) {
    val spacing = AppTheme.spacing
    val context = LocalContext.current
    val lockedSet by NoteProStore.lockedNotes.collectAsStateWithLifecycle()
    val isNoteLocked = note.id.value in lockedSet
    // PDF notes are ink pages over the document's pages: same thumbnail, with the PDF page under the ink.
    val isInk = note.type == NoteType.INK || note.type == NoteType.PDF
    val firstLine = note.preview.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
    val untitled = stringResource(
        when (note.type) {
            NoteType.INK -> R.string.ink_untitled
            NoteType.PDF -> R.string.pdf_untitled
            NoteType.TEXT -> R.string.untitled
        },
    )
    val heading = note.title.ifBlank { firstLine }.ifBlank { untitled }
    val body = if (note.title.isBlank()) note.preview.substringAfter(firstLine).trim() else note.preview
    val changed = ReminderDates.format(context, LocalDateTime.ofInstant(note.updatedAt, ZoneId.systemDefault()))
    val highlighted = selected || checked
    val shape = MaterialTheme.shapes.large
    val selectedLabel = stringResource(R.string.library_selected_note)
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (highlighted) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .testTag("note_card_${note.id.value}")
            .semantics {
                customActions = accessibilityActions.map { CustomAccessibilityAction(it.label) { it.run(); true } }
                if (checked) stateDescription = selectedLabel
            },
        shape = shape,
        elevation = 2.dp,
    ) {
        if (size == com.appname.notes.core.settings.CardSize.LIST) {
            // One compact row: a small page preview, title, date and the first line.
            Row(
                modifier = Modifier
                    .clip(shape)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick, onClickLabel = stringResource(R.string.note_open))
                    .padding(start = spacing.md, end = if (onMore != null) 0.dp else spacing.md, top = spacing.sm, bottom = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (checked) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = spacing.sm).size(22.dp))
                }
                if (isInk && inkThumbnail != null) {
                    Box(Modifier.width(56.dp).padding(end = spacing.md)) { InkThumbnailView(note, inkThumbnail) }
                }
                Column(Modifier.weight(1f)) {
                    Text(heading, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val line = body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                    Text(
                        if (line.isBlank()) changed else "$changed · $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (note.isFavourite) Icon(Icons.Filled.Star, stringResource(R.string.collection_favourites), Modifier.padding(start = spacing.xs).size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                if (note.isPinned) Icon(Icons.Filled.PushPin, stringResource(R.string.library_pinned), Modifier.padding(start = spacing.xs).size(16.dp), tint = MaterialTheme.colorScheme.primary)
                if (onMore != null) {
                    IconButton(onClick = onMore, modifier = Modifier.testTag("note_more_${note.id.value}")) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.note_more_for, heading), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            return@GlassSurface
        }
        val small = size == com.appname.notes.core.settings.CardSize.SMALL
        val extraBig = size == com.appname.notes.core.settings.CardSize.EXTRA_BIG
        Column(
            modifier = Modifier
                .clip(shape) // ripple inside the card; the soft shadow stays outside
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onClickLabel = stringResource(R.string.note_open))
                .padding(
                    start = if (small) spacing.md else spacing.lg,
                    end = if (onMore != null) spacing.xs else if (small) spacing.md else spacing.lg,
                    top = spacing.md,
                    bottom = if (small) spacing.md else spacing.lg,
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                if (checked) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp, end = spacing.sm).size(22.dp),
                    )
                }
                Column(Modifier.weight(1f).padding(top = spacing.xs)) {
                    Text(
                        text = heading,
                        style = when {
                            small -> MaterialTheme.typography.titleSmall
                            extraBig -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = changed,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isNoteLocked) {
                    Icon(Icons.Outlined.Lock, stringResource(R.string.note_lock), Modifier.padding(top = spacing.sm, end = 4.dp).size(17.dp), tint = MaterialTheme.colorScheme.primary)
                }
                if (note.isFavourite) {
                    Icon(Icons.Filled.Star, stringResource(R.string.collection_favourites), Modifier.padding(top = spacing.sm).size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                }
                if (note.isPinned) {
                    Icon(Icons.Filled.PushPin, stringResource(R.string.library_pinned), Modifier.padding(top = spacing.sm).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                if (onMore != null) {
                    IconButton(onClick = onMore, modifier = Modifier.testTag("note_more_${note.id.value}")) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.note_more_for, heading), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(Modifier.padding(end = if (onMore != null) spacing.md else 0.dp), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (!isNoteLocked && isInk && inkThumbnail != null) {
                    InkThumbnailView(note, inkThumbnail)
                }
                val coverFile = note.coverImage
                if (!isNoteLocked && !isInk && coverFile != null && cover != null) {
                    CoverPhoto(coverFile, cover)
                }
                if (!isNoteLocked && body.isNotBlank()) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = when {
                            small -> 3
                            extraBig -> 16
                            else -> 8
                        },
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val hasMeta = !small && (note.notebookTitle != null || note.tags.isNotEmpty() || note.attachmentCount > 0 || note.nextReminderAt != null)
                if (hasMeta) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        note.notebookTitle?.let { MetaChip(it, emphasised = true) }
                        note.tags.take(3).forEach { MetaChip("#${it.name}") }
                        if (note.attachmentCount > 0) AttachmentCountChip(note.attachmentCount)
                        note.nextReminderAt?.let { ReminderChip(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(text: String, emphasised: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (emphasised) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (emphasised) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** First attached photo of a text note, cropped to a band (FR-009 previews). */
@Composable
private fun CoverPhoto(fileName: String, load: suspend (String) -> Bitmap?) {
    val bitmap by produceState<Bitmap?>(null, fileName) { value = load(fileName) }
    val b = bitmap ?: return
    Image(
        b.asImageBitmap(),
        contentDescription = null, // decorative: the attachment count chip says there are attachments
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().height(140.dp).clip(MaterialTheme.shapes.small),
    )
}

/** When the note's next reminder goes off (FR-010), e.g. "Tomorrow, 9:00 AM". */
@Composable
private fun ReminderChip(at: java.time.Instant) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val text = com.appname.notes.feature.reminders.ReminderDates.format(context, java.time.LocalDateTime.ofInstant(at, java.time.ZoneId.systemDefault()))
    val label = stringResource(R.string.card_reminder, text)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.clearAndSetSemantics { contentDescription = label },
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Alarm, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 1,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

@Composable
private fun AttachmentCountChip(count: Int) {
    val label = pluralStringResource(R.plurals.card_attachments, count, count)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.clearAndSetSemantics { contentDescription = label },
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AttachFile, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$count", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 2.dp))
        }
    }
}
