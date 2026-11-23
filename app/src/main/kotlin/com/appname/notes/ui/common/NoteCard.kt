package com.appname.notes.ui.common

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.R
import com.appname.notes.core.designsystem.component.GlassSurface
import com.appname.notes.core.designsystem.theme.AppTheme
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import com.appname.notes.core.domain.model.NoteSummary
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.feature.pro.NoteProStore
import com.appname.notes.feature.reminders.ReminderDates
import java.time.LocalDateTime
import java.time.ZoneId

data class CardAction(val label: String, val run: () -> Unit)

/**
 * Phases 2, 4 & 6 — Direct Physical Hardcover Notebook Card (`NoteCard`):
 * - Phase 2 Problem A Fix: Imported images inside a note (`note.coverImage`) NEVER replace or hide the note's
 *   dedicated `NoteCoverArtwork` on the Home or Folder screen!
 * - Phase 6 Fix: Consistent tactile press-scale feedback (`0.97f` spring scale on press) across all note types
 *   (`Typed`, `Handwritten`, `PDF`, `Whiteboard`, `Notebook`) and selection outline shown ONLY when multi-select
 *   (`checked == true`) is active (never leaving a sticky outline after opening a note!).
 */
@Composable
fun NoteCard(
    note: NoteSummary,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    accessibilityActions: List<CardAction>,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") selected: Boolean = false,
    @Suppress("UNUSED_PARAMETER") inkThumbnail: (suspend (NoteSummary) -> InkThumbnail?)? = null,
    @Suppress("UNUSED_PARAMETER") cover: (suspend (String) -> Bitmap?)? = null,
    onMore: (() -> Unit)? = null,
    onToggleFavourite: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    checked: Boolean = false,
    size: com.appname.notes.core.settings.CardSize = com.appname.notes.core.settings.CardSize.BIG,
) {
    val spacing = AppTheme.spacing
    val isDark = LocalDarkTheme.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        NoteProStore.ensureInit(context)
        NoteCoverStore.ensureInit(context)
    }
    val lockedSet by NoteProStore.lockedNotes.collectAsStateWithLifecycle()
    val noteCoversMap by NoteCoverStore.noteCovers.collectAsStateWithLifecycle()
    val globalDisplayMode by NoteCoverStore.displayMode.collectAsStateWithLifecycle()
    val isNoteLocked = note.id.value in lockedSet

    val untitled = stringResource(
        when (note.type) {
            NoteType.INK -> R.string.ink_untitled
            NoteType.PDF -> R.string.pdf_untitled
            NoteType.TEXT -> R.string.untitled
        },
    )
    // Always use the actual notebook/note name; NEVER internal note body text or attached photo!
    val heading = note.title.trim().ifBlank { untitled }
    val changed = ReminderDates.format(context, LocalDateTime.ofInstant(note.updatedAt, ZoneId.systemDefault()))

    // Phase 6: Show selection outline ONLY when multi-select mode (`checked`) is active
    val highlighted = checked
    val shape = MaterialTheme.shapes.large
    val selectedLabel = stringResource(R.string.library_selected_note)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "noteCardPressScale",
    )

    val coverConfig = remember(note.id.value, note.type, heading, noteCoversMap) {
        NoteCoverStore.resolveForNote(note.id.value, note.type, noteCoversMap, heading)
    }
    val typeLabel = when (note.type) {
        NoteType.TEXT -> "Typed Note"
        NoteType.INK -> "Notebook"
        NoteType.PDF -> "PDF Document"
    }

    if (size == com.appname.notes.core.settings.CardSize.LIST) {
        GlassSurface(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .then(if (highlighted) Modifier.border(1.75.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                .testTag("note_card_${note.id.value}")
                .semantics {
                    customActions = accessibilityActions.map { CustomAccessibilityAction(it.label) { it.run(); true } }
                    if (checked) stateDescription = selectedLabel
                },
            shape = shape,
            elevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .clip(shape)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onClickLabel = stringResource(R.string.note_open),
                    )
                    .padding(start = spacing.md, end = if (onMore != null) 0.dp else spacing.md, top = spacing.sm, bottom = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (checked) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = spacing.sm).size(22.dp))
                }
                Box(Modifier.padding(end = spacing.md).width(46.dp).aspectRatio(0.71f)) {
                    NoteCoverArtwork(
                        title = cleanDisplayHeading(heading),
                        noteType = note.type,
                        config = coverConfig,
                        isLocked = isNoteLocked,
                        compact = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(heading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "$typeLabel · $changed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onToggleFavourite != null) {
                    IconButton(
                        onClick = onToggleFavourite,
                        modifier = Modifier.size(34.dp).testTag("note_fav_${note.id.value}"),
                    ) {
                        Icon(
                            if (note.isFavourite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            stringResource(if (note.isFavourite) R.string.note_unfavourite else R.string.note_favourite),
                            Modifier.size(18.dp),
                            tint = if (note.isFavourite) Color(0xFFD4A853) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (note.isFavourite) {
                    Icon(Icons.Filled.Star, stringResource(R.string.collection_favourites), Modifier.padding(start = spacing.xs).size(16.dp), tint = Color(0xFFD4A853))
                }
                if (note.isPinned) Icon(Icons.Filled.PushPin, stringResource(R.string.library_pinned), Modifier.padding(start = spacing.xs).size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                onMore?.let {
                    IconButton(onClick = it, modifier = Modifier.testTag("note_more_${note.id.value}")) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.note_more_for, heading), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        return
    }

    val small = size == com.appname.notes.core.settings.CardSize.SMALL
    val extraBig = size == com.appname.notes.core.settings.CardSize.EXTRA_BIG
    val bookShape = RoundedCornerShape(
        topStart = 5.dp,
        bottomStart = 5.dp,
        topEnd = 13.dp,
        bottomEnd = 13.dp,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = stringResource(R.string.note_open),
            )
            .padding(horizontal = if (small) 2.dp else 4.dp, vertical = 4.dp)
            .testTag("note_card_${note.id.value}")
            .semantics {
                customActions = accessibilityActions.map { CustomAccessibilityAction(it.label) { it.run(); true } }
                if (checked) stateDescription = selectedLabel
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // 1. Always Render the Dedicated Notebook/Note Cover (Imported photos inside the note never overwrite the cover!)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (small) 0.94f else 0.92f)
                    .aspectRatio(coverConfig.aspectRatioValue)
                    .then(
                        if (highlighted) {
                            Modifier
                                .border(2.2.dp, MaterialTheme.colorScheme.primary, bookShape)
                                .padding(2.dp)
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                NoteCoverArtwork(
                    title = cleanDisplayHeading(heading),
                    noteType = note.type,
                    config = coverConfig,
                    subtitle = note.notebookTitle ?: changed,
                    isLocked = isNoteLocked,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (globalDisplayMode == NotebookDisplayMode.MODE_3D) {
                NotebookGroundShadow(isDark = isDark)
            }
        }

        // 2. Frameless Caption Footer Beneath the Notebook (Supports 2-line titles without truncation)
        Row(
            modifier = Modifier
                .fillMaxWidth(if (small) 0.96f else 0.94f)
                .padding(start = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (checked) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = spacing.xs).size(19.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = cleanDisplayHeading(heading),
                    style = when {
                        small -> MaterialTheme.typography.titleSmall
                        extraBig -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = changed,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isNoteLocked) {
                Icon(Icons.Outlined.Lock, stringResource(R.string.note_lock), Modifier.padding(end = 2.dp).size(15.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            if ((onTogglePin != null) && note.isPinned) {
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(28.dp).testTag("note_pin_${note.id.value}"),
                ) {
                    Icon(
                        Icons.Filled.PushPin,
                        stringResource(R.string.note_unpin),
                        Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else if (note.isPinned) {
                Icon(Icons.Filled.PushPin, stringResource(R.string.library_pinned), Modifier.padding(end = 2.dp).size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onToggleFavourite != null) {
                IconButton(
                    onClick = onToggleFavourite,
                    modifier = Modifier.size(30.dp).testTag("note_fav_${note.id.value}"),
                ) {
                    Icon(
                        if (note.isFavourite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        stringResource(if (note.isFavourite) R.string.note_unfavourite else R.string.note_favourite),
                        Modifier.size(17.dp),
                        tint = if (note.isFavourite) Color(0xFFD4A853) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                    )
                }
            } else if (note.isFavourite) {
                Icon(Icons.Filled.Star, stringResource(R.string.collection_favourites), Modifier.size(16.dp), tint = Color(0xFFD4A853))
            }
            if (onMore != null) {
                IconButton(onClick = onMore, modifier = Modifier.size(30.dp).testTag("note_more_${note.id.value}")) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.note_more_for, heading), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 3. Optional Tags / Notebook / Attachment / Reminder Metadata Chips
        val hasMeta = !small && (note.notebookTitle != null || note.tags.isNotEmpty() || note.attachmentCount > 0 || note.nextReminderAt != null)
        if (hasMeta) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(0.94f),
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

@Composable
private fun NotebookGroundShadow(isDark: Boolean) {
    Box(
        modifier = Modifier
            .padding(top = 2.dp)
            .fillMaxWidth(0.82f)
            .height(5.dp)
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        if (isDark) Color.Black.copy(alpha = 0.42f) else Color(0xFF2C241B).copy(alpha = 0.16f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

@Composable
private fun MetaChip(text: String, emphasised: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (emphasised) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
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

@Composable
private fun ReminderChip(at: java.time.Instant) {
    val context = LocalContext.current
    val text = ReminderDates.format(context, LocalDateTime.ofInstant(at, ZoneId.systemDefault()))
    val label = stringResource(R.string.card_reminder, text)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f),
        modifier = Modifier.clearAndSetSemantics { contentDescription = label },
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Alarm, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
        modifier = Modifier.clearAndSetSemantics { contentDescription = label },
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AttachFile, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(count.toString(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 2.dp))
        }
    }
}

/**
 * Formats noisy filenames (e.g. `Navi_Statement_19102026...` or `Man_s Search For Meaning ( PDFDrive.com )`)
 * into a clean editorial display title without mutating the stored database title.
 */
private fun cleanDisplayHeading(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    return trimmed
        .replace(Regex("""\(\s*PDFDrive\.com\s*\)""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\(\s*Z-Library\s*\)""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\.pdf$""", RegexOption.IGNORE_CASE), "")
        .replace('_', ' ')
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
        .ifEmpty { trimmed }
}
