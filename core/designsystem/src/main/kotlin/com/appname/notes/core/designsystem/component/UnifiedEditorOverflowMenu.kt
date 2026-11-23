package com.appname.notes.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appname.notes.core.designsystem.theme.LocalDarkTheme

/**
 * Phase 26 & UI/UX Refinement #1 — Unified Single-Column Liquid Glass Overflow Menu System.
 * Ensures Typed Text Notes, Handwritten Notes, Whiteboard, and Text Documents share the exact same
 * single-column layout, typography, icon column alignment, section headers, and smooth vertical scrolling.
 */
@Composable
fun UnifiedMenuColumnGroup(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    @Suppress("UNUSED_PARAMETER") verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
fun UnifiedEditorMenuSectionHeader(
    title: String,
    showTopDivider: Boolean = true,
) {
    val isDark = LocalDarkTheme.current
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.08f)
    val headerColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF5A606A)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (showTopDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = dividerColor,
            )
        }
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 1.1.sp,
            ),
            fontWeight = FontWeight.SemiBold,
            color = headerColor,
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
        )
    }
}

/**
 * Resolves the appropriate Material Outlined icon for any editor overflow action label.
 */
fun resolveUnifiedEditorMenuIcon(label: String): ImageVector {
    val l = label.lowercase()
    return when {
        l.contains("find") || l.contains("search") -> Icons.Outlined.Search
        l.contains("pin") -> Icons.Outlined.PushPin
        l.contains("fav") || l.contains("star") -> Icons.Outlined.StarOutline
        l.contains("bookmark") -> Icons.Outlined.BookmarkBorder
        l.contains("paste") -> Icons.Outlined.ContentPaste
        l.contains("add page") -> Icons.Outlined.EditNote
        l.contains("duplicate") -> Icons.Outlined.ContentCopy
        l.contains("page settings") || l.contains("settings") -> Icons.Outlined.Settings
        l.contains("scroll") || l.contains("fit") || l.contains("go to") -> Icons.AutoMirrored.Outlined.Sort
        l.contains("clear") || l.contains("delete") || l.contains("trash") -> Icons.Outlined.DeleteOutline
        l.contains("tag") -> Icons.AutoMirrored.Outlined.Label
        l.contains("move") || l.contains("notebook") -> Icons.AutoMirrored.Outlined.DriveFileMove
        l.contains("remind") -> Icons.Outlined.Alarm
        l.contains("to-do") || l.contains("todo") || l.contains("task") -> Icons.Outlined.CheckCircle
        l.contains("share") -> Icons.Outlined.Share
        l.contains("pdf") -> Icons.Outlined.PictureAsPdf
        l.contains("markdown") || l.contains("export") -> Icons.Outlined.FileDownload
        l.contains("rename") -> Icons.Outlined.DriveFileRenameOutline
        l.contains("archive") -> Icons.Outlined.Archive
        l.contains("restore") -> Icons.Outlined.RestoreFromTrash
        else -> Icons.Outlined.Edit
    }
}

/**
 * Determines if this action label starts a new logical group in the Unified Editor Overflow Menu.
 */
private fun resolveSectionHeaderForLabel(label: String): Pair<String, Boolean>? {
    return when (label.trim().lowercase()) {
        "find in note" -> "NOTE ACTIONS" to false
        "add page" -> "PAGE ACTIONS" to true
        "tags" -> "NOTE ORGANISATION" to true
        ("export as pdf") -> "SHARE & EXPORT" to true
        "share as text" -> null
        "rename" -> "STORAGE" to true
        "archive", "unarchive" -> null
        else -> null
    }
}

@Composable
fun UnifiedEditorOverflowMenuItem(
    label: String,
    onClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    destructive: Boolean = false,
) {
    val isDark = LocalDarkTheme.current
    val isDestructive = destructive ||
        label.contains("trash", ignoreCase = true) ||
        label.contains("delete", ignoreCase = true) ||
        label.contains("clear page", ignoreCase = true)

    val textColor = when {
        isDestructive -> if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
        isDark -> Color(0xFFF4F5F7)
        else -> Color(0xFF111317)
    }
    val iconColor = when {
        isDestructive -> textColor
        isDark -> Color(0xFFD1D5DB)
        else -> Color(0xFF374151)
    }

    val sectionInfo = resolveSectionHeaderForLabel(label)
    val icon = leadingIcon ?: resolveUnifiedEditorMenuIcon(label)

    // Always wrap SectionHeader + Row inside a single full-width Column so section headers
    // never push sibling items horizontally off-screen.
    Column(modifier = Modifier.fillMaxWidth()) {
        sectionInfo?.let { (header, showDivider) ->
            UnifiedEditorMenuSectionHeader(
                title = header,
                showTopDivider = showDivider,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
