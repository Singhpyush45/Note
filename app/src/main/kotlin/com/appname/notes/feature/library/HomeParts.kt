package com.appname.notes.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appname.notes.R
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import com.appname.notes.core.domain.model.NoteCollection
import com.appname.notes.core.domain.model.Notebook
import com.appname.notes.core.domain.model.Tag
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface

enum class HomeGridFilter {
    ALL,
    DOCUMENTS,
    FOLDERS,
    PINNED,
}

/**
 * Phase 21.1 & 21.5 — Top Category Navigation (`Notes · Documents · Folders · Favourites · Pinned`)
 * with a TRUE Horizontally Sliding Neutral Liquid Glass Indicator Pill:
 * - Measures each tab's position & width inside the scrollable track and smoothly glides a single shared
 *   Liquid Glass capsule (`animateFloatAsState` with spring physics) from the old tab to the newly selected tab.
 */
@Composable
fun CollectionChips(
    current: NoteCollection,
    @Suppress("UNUSED_PARAMETER") notebooks: List<Notebook>,
    onSelect: (NoteCollection) -> Unit,
    modifier: Modifier = Modifier,
    gridFilter: HomeGridFilter = HomeGridFilter.ALL,
    onGridFilterChange: (HomeGridFilter) -> Unit = {},
) {
    val isDark = LocalDarkTheme.current
    val density = LocalDensity.current

    data class CategoryTabSpec(
        val key: String,
        val label: String,
        val tag: String,
        val selected: Boolean,
        val onClick: () -> Unit,
    )

    val tabs = listOf(
        CategoryTabSpec(
            key = "all",
            label = stringResource(R.string.collection_all),
            tag = LibraryTestTags.CHIP_ALL,
            selected = (current == NoteCollection.All) && (gridFilter == HomeGridFilter.ALL),
            onClick = {
                onGridFilterChange(HomeGridFilter.ALL)
                onSelect(NoteCollection.All)
            },
        ),
        CategoryTabSpec(
            key = "docs",
            label = "Documents",
            tag = "chip_documents",
            selected = (current == NoteCollection.All) && (gridFilter == HomeGridFilter.DOCUMENTS),
            onClick = {
                onGridFilterChange(HomeGridFilter.DOCUMENTS)
                if (current != NoteCollection.All) onSelect(NoteCollection.All)
            },
        ),
        CategoryTabSpec(
            key = "folders",
            label = stringResource(R.string.folder_section),
            tag = "chip_folders",
            selected = current == NoteCollection.All && gridFilter == HomeGridFilter.FOLDERS,
            onClick = {
                onGridFilterChange(HomeGridFilter.FOLDERS)
                if (current != NoteCollection.All) onSelect(NoteCollection.All)
            },
        ),
        CategoryTabSpec(
            key = "fav",
            label = stringResource(R.string.collection_favourites),
            tag = LibraryTestTags.CHIP_FAVOURITES,
            selected = current == NoteCollection.Favourites,
            onClick = {
                onGridFilterChange(HomeGridFilter.ALL)
                onSelect(NoteCollection.Favourites)
            },
        ),
        CategoryTabSpec(
            key = "pinned",
            label = stringResource(R.string.library_pinned),
            tag = "chip_pinned",
            selected = current == NoteCollection.All && gridFilter == HomeGridFilter.PINNED,
            onClick = {
                onGridFilterChange(HomeGridFilter.PINNED)
                if (current != NoteCollection.All) onSelect(NoteCollection.All)
            },
        ),
    )

    val selectedIndex = tabs.indexOfFirst { (_, _, _, selected) -> selected }.coerceAtLeast(0)
    val tabLeftPx = remember { mutableStateMapOf<Int, Float>() }
    val tabWidthPx = remember { mutableStateMapOf<Int, Float>() }

    val targetLeftPx = tabLeftPx[selectedIndex] ?: 0f
    val targetWidthPx = tabWidthPx[selectedIndex] ?: 0f

    val animatedLeftPx by animateFloatAsState(
        targetValue = targetLeftPx,
        animationSpec = spring(
            dampingRatio = 0.88f,
            stiffness = 1050f,
        ),
        label = "categorySlidingLeftPx",
    )
    val animatedWidthPx by animateFloatAsState(
        targetValue = targetWidthPx,
        animationSpec = spring(
            dampingRatio = 0.88f,
            stiffness = 1050f,
        ),
        label = "categorySlidingWidthPx",
    )

    val pillFill = if (isDark) Color.White.copy(alpha = 0.16f) else Color(0xFF141518).copy(alpha = 0.10f)
    val pillBorder = if (isDark) Color.White.copy(alpha = 0.30f) else Color(0xFF141518).copy(alpha = 0.22f)
    val idleBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag(LibraryTestTags.CHIPS),
    ) {
        // Shared horizontally sliding Liquid Glass indicator capsule
        if (animatedWidthPx > 1f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedLeftPx.roundToInt(), 0) }
                    .width(with(density) { animatedWidthPx.toDp() })
                    .height(30.dp)
                    .clip(CircleShape)
                    .background(pillFill, CircleShape)
                    .border(0.9.dp, pillBorder, CircleShape),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val contentColor by animateColorAsState(
                    targetValue = when {
                        tab.selected && isDark -> Color(0xFFF4F5F7)
                        tab.selected && !isDark -> Color(0xFF111317)
                        isDark -> Color(0xFF9CA3AF)
                        else -> Color(0xFF5A606A)
                    },
                    animationSpec = tween(180),
                    label = "tabTextColor",
                )
                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .onGloballyPositioned { coords ->
                            tabLeftPx[index] = coords.positionInParent().x
                            tabWidthPx[index] = coords.size.width.toFloat()
                        }
                        .clip(CircleShape)
                        .border(
                            width = 0.75.dp,
                            color = if (tab.selected) Color.Transparent else idleBorder,
                            shape = CircleShape,
                        )
                        .selectable(
                            selected = tab.selected,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = tab.onClick,
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag(tab.tag),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (tab.selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Actions for the multi-select bar at the top of the home screen. */
data class SelectionActions(
    val clear: () -> Unit,
    val selectAll: () -> Unit,
    val pin: () -> Unit,
    val move: () -> Unit,
    val archive: () -> Unit,
    val trash: () -> Unit,
    val restore: () -> Unit,
    val deleteForever: () -> Unit,
)

/** Shown in the header instead of search/sort when one or more notes are selected. */
@Composable
fun SelectionBar(
    count: Int,
    collection: NoteCollection,
    allPinned: Boolean,
    actions: SelectionActions,
) {
    val countText = pluralStringResource(R.plurals.selection_count, count, count)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .testTag(LibraryTestTags.SELECTION_BAR),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.clear, modifier = Modifier.testTag(LibraryTestTags.SELECTION_CLEAR)) {
            Icon(Icons.Outlined.Close, stringResource(R.string.selection_clear))
        }
        Text(
            text = countText,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .testTag(LibraryTestTags.SELECTION_COUNT)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        IconButton(onClick = actions.selectAll, modifier = Modifier.testTag(LibraryTestTags.SELECT_ALL)) {
            Icon(Icons.Outlined.SelectAll, stringResource(R.string.selection_select_all))
        }
        if (collection == NoteCollection.Trash) {
            IconButton(onClick = actions.restore, modifier = Modifier.testTag(LibraryTestTags.SELECTION_RESTORE)) {
                Icon(Icons.Outlined.RestoreFromTrash, stringResource(R.string.note_restore))
            }
            IconButton(onClick = actions.deleteForever, modifier = Modifier.testTag(LibraryTestTags.SELECTION_DELETE)) {
                Icon(Icons.Outlined.DeleteForever, stringResource(R.string.note_delete_forever))
            }
        } else {
            IconButton(onClick = actions.pin, modifier = Modifier.testTag(LibraryTestTags.SELECTION_PIN)) {
                Icon(Icons.Outlined.PushPin, stringResource(if (allPinned) R.string.note_unpin else R.string.note_pin))
            }
            IconButton(onClick = actions.move, modifier = Modifier.testTag(LibraryTestTags.SELECTION_MOVE)) {
                Icon(Icons.AutoMirrored.Outlined.DriveFileMove, stringResource(R.string.note_move))
            }
            IconButton(onClick = actions.archive, modifier = Modifier.testTag(LibraryTestTags.SELECTION_ARCHIVE)) {
                Icon(
                    if (collection == NoteCollection.Archive) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                    stringResource(if (collection == NoteCollection.Archive) R.string.note_unarchive else R.string.note_archive),
                )
            }
            IconButton(onClick = actions.trash, modifier = Modifier.testTag(LibraryTestTags.SELECTION_TRASH)) {
                Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.note_trash))
            }
        }
    }
}

@Composable
fun RenameNoteDialog(
    current: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
    titleRes: Int = R.string.note_rename,
    labelRes: Int = R.string.editor_title_hint,
) {
    var value by remember { mutableStateOf(TextFieldValue(current, TextRange(current.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val save = {
        onRename(value.text.trim())
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(labelRes)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .testTag(LibraryTestTags.RENAME_FIELD),
            )
        },
        confirmButton = {
            TextButton(onClick = save, modifier = Modifier.testTag(LibraryTestTags.RENAME_SAVE)) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        modifier = Modifier.testTag(LibraryTestTags.RENAME_DIALOG),
    )
}


/**
 * Phase 6 — Neutral Liquid Glass Collections & Folders Bottom Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsSheet(
    current: NoteCollection,
    notebooks: List<Notebook>,
    tags: List<Tag>,
    onSelect: (NoteCollection) -> Unit,
    onManageNotebooks: () -> Unit,
    onManageTags: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val sheetBg = if (isDark) Color(0xFF101114) else Color(0xFFFFFFFF)
    val cardBg = if (isDark) Color(0xFF181A20) else Color(0xFFF4F5F7)
    val borderCol = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)
    val primaryText = if (isDark) Color(0xFFF4F5F7) else Color(0xFF111317)
    val secondaryText = if (isDark) Color(0xFF9CA3AF) else Color(0xFF5A606A)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetBg,
        contentColor = primaryText,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardBg,
                border = BorderStroke(0.8.dp, borderCol),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    CollectionRowItem(
                        label = stringResource(R.string.collection_all),
                        icon = Icons.Outlined.EditNote,
                        selected = current == NoteCollection.All,
                        primaryText = primaryText,
                        onClick = { onSelect(NoteCollection.All) },
                    )
                    HorizontalDivider(color = borderCol)
                    CollectionRowItem(
                        label = stringResource(R.string.collection_favourites),
                        icon = Icons.Outlined.StarOutline,
                        selected = current == NoteCollection.Favourites,
                        primaryText = primaryText,
                        onClick = { onSelect(NoteCollection.Favourites) },
                    )
                    HorizontalDivider(color = borderCol)
                    CollectionRowItem(
                        label = stringResource(R.string.collection_archive),
                        icon = Icons.Outlined.Inventory2,
                        selected = current == NoteCollection.Archive,
                        primaryText = primaryText,
                        onClick = { onSelect(NoteCollection.Archive) },
                    )
                    HorizontalDivider(color = borderCol)
                    CollectionRowItem(
                        label = stringResource(R.string.collection_trash),
                        icon = Icons.Outlined.DeleteOutline,
                        selected = current == NoteCollection.Trash,
                        primaryText = primaryText,
                        onClick = { onSelect(NoteCollection.Trash) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.collection_notebooks),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryText,
                )
                TextButton(onClick = onManageNotebooks) {
                    Text(stringResource(R.string.library_manage_notebooks), color = primaryText)
                }
            }

            if (notebooks.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    border = BorderStroke(0.8.dp, borderCol),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        notebooks.forEachIndexed { idx, nb ->
                            if (idx > 0) HorizontalDivider(color = borderCol)
                            CollectionRowItem(
                                label = nb.title,
                                icon = Icons.Outlined.Folder,
                                selected = current == NoteCollection.InNotebook(nb.id),
                                primaryText = primaryText,
                                onClick = { onSelect(NoteCollection.InNotebook(nb.id)) },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.library_manage_tags),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryText,
                )
                TextButton(onClick = onManageTags) {
                    Text(stringResource(R.string.library_manage_tags), color = primaryText)
                }
            }

            if (tags.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    border = BorderStroke(0.8.dp, borderCol),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        tags.forEachIndexed { idx, tag ->
                            if (idx > 0) HorizontalDivider(color = borderCol)
                            CollectionRowItem(
                                label = "#${tag.name}",
                                icon = Icons.AutoMirrored.Outlined.Label,
                                selected = current == NoteCollection.WithTag(tag.id),
                                primaryText = primaryText,
                                onClick = { onSelect(NoteCollection.WithTag(tag.id)) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CollectionRowItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    primaryText: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = primaryText, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}


/**
 * Neutral Liquid Glass Confirmation Dialog for destructive actions (delete note, delete folder, empty trash).
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val dialogBg = if (isDark) Color(0xFF14161B) else Color(0xFFFFFFFF)
    val primaryText = if (isDark) Color(0xFFF4F5F7) else Color(0xFF111317)
    val secondaryText = if (isDark) Color(0xFF9CA3AF) else Color(0xFF5A606A)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = dialogBg,
        titleContentColor = primaryText,
        textContentColor = secondaryText,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            if (body.isNotBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text(
                    text = confirm,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = primaryText,
                )
            }
        },
    )
}
