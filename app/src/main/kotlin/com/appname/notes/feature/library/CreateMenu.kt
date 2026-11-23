package com.appname.notes.feature.library
import androidx.compose.ui.platform.LocalContext

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appname.notes.R
import com.appname.notes.core.designsystem.theme.LocalDarkTheme

/** What the Goodnotes-style `+ New` menu can create (`image_1ee3b6.jpg`). */
data class CreateActions(
    val blankNote: () -> Unit,
    val chooseTemplate: () -> Unit,
    val scanNote: () -> Unit,
    val importFile: () -> Unit,
    /** A new folder (in the open folder, or at the top level); null where folders can't be made. */
    val newFolder: (() -> Unit)? = null,
    /** Opens the New Whiteboard & Diagram Templates dialog (`image_1ee018.jpg`). */
    val newWhiteboard: (() -> Unit)? = null,
    /** Creates a new Typed Text Document directly. */
    val newTextDoc: (() -> Unit)? = null,
    /** Opens the Goodnotes New Notebook Cover & Paper Studio (`image_1ee034.jpg`). */
    val newNotebookStudio: (() -> Unit)? = null,
)

/**
 * A light veil over the screen while the + menu is open; tapping it closes the menu. Draw it above the content
 * and below [CreateMenu].
 */
@Composable
fun CreateMenuScrim(visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.36f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .testTag(LibraryTestTags.CREATE_SCRIM),
        )
    }
}

/**
 * Goodnotes-style `+ New` Popover Card (`image_1ee3b6.jpg`):
 * - Top 3 tiles: `Notebook` (`CHOOSE_TEMPLATE`), `Whiteboard` (`NEW`), `Text Doc` (`NEW`)
 * - Second row 2 tiles: `Import` (`IMPORT_FILE`), `Quick Record`
 * - Grouped list rows: `QuickNote` (`NEW_NOTE`), `Scan Documents` (`SCAN`), `Image`, `Take Photo`
 * - Bottom row: `Folder` (`NEW_FOLDER`)
 */
@Composable
fun CreateMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: CreateActions,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, label = "plus")
    val isDark = LocalDarkTheme.current
    val cardBg = if (isDark) Color(0xFF1F232B) else Color(0xFFF8FAFC)
    val tileBg = if (isDark) Color(0xFF2E3440) else Color(0xFFE9EEF6)
    val textColor = if (isDark) Color.White else Color(0xFF0F172A)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(initialScale = 0.90f, transformOrigin = TransformOrigin(1f, 1f)),
            exit = fadeOut() + scaleOut(targetScale = 0.90f, transformOrigin = TransformOrigin(1f, 1f)),
        ) {
            fun pick(action: () -> Unit): () -> Unit = { onExpandedChange(false); action() }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardBg,
                tonalElevation = 8.dp,
                shadowElevation = 14.dp,
                modifier = Modifier.width(304.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Row 1: Notebook | Whiteboard (NEW) | Text Doc (NEW)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TopCreateTile(
                            label = "Notebook",
                            icon = Icons.Outlined.Book,
                            tag = LibraryTestTags.CHOOSE_TEMPLATE,
                            tileBg = tileBg,
                            textColor = textColor,
                            onClick = pick(actions.chooseTemplate),
                            modifier = Modifier.weight(1f),
                        )
                        TopCreateTile(
                            label = "Whiteboard",
                            icon = Icons.Outlined.GridOn,
                            tag = "create_whiteboard",
                            tileBg = tileBg,
                            textColor = textColor,
                            badge = "",
                            onClick = pick(actions.newWhiteboard ?: actions.chooseTemplate),
                            modifier = Modifier.weight(1f),
                        )
                        TopCreateTile(
                            label = "Text Doc",
                            icon = Icons.Outlined.Description,
                            tag = "create_text_doc",
                            tileBg = tileBg,
                            textColor = textColor,
                            badge = "",
                            onClick = pick(actions.newTextDoc ?: actions.blankNote),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Row 2: Import | Quick Record
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TopCreateTile(
                            label = stringResource(R.string.create_import_file),
                            icon = Icons.Outlined.UploadFile,
                            tag = LibraryTestTags.IMPORT_FILE,
                            tileBg = tileBg,
                            textColor = textColor,
                            onClick = pick(actions.importFile),
                            modifier = Modifier.weight(1f),
                        )
                        TopCreateTile(
                            label = "Quick Record",
                            icon = Icons.Outlined.Mic,
                            tag = "create_quick_record",
                            tileBg = tileBg,
                            textColor = textColor,
                            onClick = pick(actions.blankNote),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Grouped List Card: QuickNote, Scan Documents, Image, Take Photo
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = tileBg,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            if (actions.newNotebookStudio != null) {
                                PopoverListRow(
                                    label = "Notebook Studio (Cover & Paper)",
                                    icon = Icons.Outlined.Book,
                                    tag = "create_notebook_studio_row",
                                    textColor = Color(0xFF38BDF8),
                                    onClick = pick(actions.newNotebookStudio),
                                )
                                HorizontalDivider(color = textColor.copy(alpha = 0.08f))
                            }
                            PopoverListRow(
                                label = stringResource(R.string.create_blank_note),
                                icon = Icons.Outlined.EditNote,
                                tag = LibraryTestTags.NEW_NOTE,
                                textColor = textColor,
                                onClick = pick(actions.blankNote),
                            )
                            HorizontalDivider(color = textColor.copy(alpha = 0.08f))
                            PopoverListRow(
                                label = stringResource(R.string.create_scan_note),
                                icon = Icons.Outlined.DocumentScanner,
                                tag = LibraryTestTags.SCAN,
                                textColor = textColor,
                                onClick = pick(actions.scanNote),
                            )
                            HorizontalDivider(color = textColor.copy(alpha = 0.08f))
                            PopoverListRow(
                                label = "Image",
                                icon = Icons.Outlined.Image,
                                tag = "create_image_row",
                                textColor = textColor,
                                onClick = pick(actions.importFile),
                            )
                            HorizontalDivider(color = textColor.copy(alpha = 0.08f))
                            PopoverListRow(
                                label = "Take Photo",
                                icon = Icons.Outlined.CameraAlt,
                                tag = "create_camera_row",
                                textColor = textColor,
                                onClick = pick(actions.scanNote),
                            )
                        }
                    }

                    // Bottom Folder Card
                    if (actions.newFolder != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = tileBg,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            PopoverListRow(
                                label = stringResource(R.string.folder_new),
                                icon = Icons.Outlined.CreateNewFolder,
                                tag = LibraryTestTags.NEW_FOLDER,
                                textColor = textColor,
                                onClick = pick(actions.newFolder),
                            )
                        }
                    }
                }
            }
        }

        val label = stringResource(if (expanded) R.string.create_close else R.string.create_open)
        val state = stringResource(if (expanded) R.string.create_state_open else R.string.create_state_closed)
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            modifier = Modifier
                .size(62.dp)
                .testTag(LibraryTestTags.CREATE)
                .semantics {
                    contentDescription = label
                    stateDescription = state
                },
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(30.dp).rotate(rotation))
        }
    }
}

@Composable
private fun TopCreateTile(
    label: String,
    icon: ImageVector,
    tag: String,
    tileBg: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    Box(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(tileBg)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 8.dp)
                .testTag(tag),
        ) {
            Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 4.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = badge,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E293B),
                )
            }
        }
    }
}

@Composable
private fun PopoverListRow(
    label: String,
    icon: ImageVector,
    tag: String,
    textColor: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .testTag(tag),
    ) {
        Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
