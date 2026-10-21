package com.appname.notes.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
 * Note preview card. Tap opens, long-press shows actions. TalkBack users get the same actions
 * through the "Actions" menu (custom accessibility actions).
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
) {
    val spacing = AppTheme.spacing
    val isInk = note.type == NoteType.INK
    val firstLine = note.preview.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
    val untitled = stringResource(if (isInk) R.string.ink_untitled else R.string.untitled)
    val heading = note.title.ifBlank { firstLine }.ifBlank { untitled }
    val body = if (note.title.isBlank()) note.preview.substringAfter(firstLine).trim() else note.preview
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("note_card_${note.id.value}")
            .semantics {
                customActions = accessibilityActions.map { CustomAccessibilityAction(it.label) { it.run(); true } }
            },
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.material3.CardDefaults.outlinedCardBorder()
        },
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onClickLabel = stringResource(R.string.note_open))
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (note.isFavourite) {
                    Icon(Icons.Filled.Star, stringResource(R.string.collection_favourites), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                }
                if (note.isPinned) {
                    Icon(Icons.Filled.PushPin, stringResource(R.string.library_pinned), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (isInk && inkThumbnail != null) {
                InkThumbnailView(note, inkThumbnail)
            }
            if (body.isNotBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                note.notebookTitle?.let { MetaChip(it, emphasised = true) }
                note.tags.take(3).forEach { MetaChip("#${it.name}") }
                Spacer(Modifier.weight(1f))
                Text(
                    text = NoteDates.format(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
