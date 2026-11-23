package com.appname.notes.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appname.notes.R
import com.appname.notes.core.domain.model.FolderTree
import com.appname.notes.core.domain.model.Notebook
import com.appname.notes.core.domain.model.NotebookId
import kotlin.math.abs

/** What can be done with folders from the home screen (folders inside folders). */
data class FolderActions(
    val create: (String) -> Unit,
    val rename: (NotebookId, String) -> Unit,
    val move: (NotebookId, NotebookId?) -> Unit,
    val delete: (NotebookId) -> Unit,
)

object FolderTestTags {
    const val NEW_FOLDER = "folder_new"
    const val UP = "folder_up"
    fun card(id: NotebookId) = "folder_card_${id.value}"
    fun more(id: NotebookId) = "folder_more_${id.value}"
}

/**
 * Rich Goodnotes-inspired folder color palette (`image_1ee3bd.jpg` & `image_1ee2db.jpg`):
 * Coral Pink (Botany), Soft Lavender (Zoology), Lime Meadow (Physical Chemistry),
 * Sky Azure (Organic Chemistry), Vibrant Magenta (Inorganic Chemistry), Crimson Red (Physics),
 * Warm Amber, and Mint Teal.
 */
private val GoodnotesFolderPalette = listOf(
    Color(0xFFF28B92) to Color(0xFFE06C75), // Coral Pink
    Color(0xFFDCC4EB) to Color(0xFFC3A3D8), // Soft Lavender
    Color(0xFFC2F279) to Color(0xFF9FE042), // Lime Meadow
    Color(0xFF7BC4E8) to Color(0xFF56A5D1), // Sky Azure
    Color(0xFFEC1B9E) to Color(0xFFC41080), // Vibrant Magenta
    Color(0xFFF51E1E) to Color(0xFFC91212), // Crimson Red
    Color(0xFFFFB74D) to Color(0xFFF59E0B), // Warm Amber
    Color(0xFF4DB6AC) to Color(0xFF0D9488), // Mint Teal
)

private fun folderColorsFor(folder: Notebook): Pair<Color, Color> {
    val idx = abs(folder.title.hashCode() + folder.id.value.hashCode()) % GoodnotesFolderPalette.size
    return GoodnotesFolderPalette[idx]
}

/**
 * Goodnotes-style 3D Colored Folder Card (`image_1ee3bd.jpg`, `image_1ee2db.jpg`):
 * - Glossy 3D folder pocket with top-left tab, inner bevel highlight, and top-right star badge
 * - Centered folder title + `⌄` action trigger underneath
 * - Note & subfolder count subtitle underneath
 */
@Composable
fun FolderCard(
    folder: Notebook,
    all: List<Notebook>,
    onOpen: () -> Unit,
    onMore: (() -> Unit)?,
    testTag: String = FolderTestTags.card(folder.id),
) {
    val subfolders = FolderTree.children(all, folder.id).size
    val notes = pluralStringResource(R.plurals.note_count, folder.noteCount, folder.noteCount)
    val summary = if (subfolders > 0) {
        stringResource(R.string.folder_summary, notes, pluralStringResource(R.plurals.folder_count, subfolders, subfolders))
    } else {
        notes
    }
    val (frontColor, backColor) = folderColorsFor(folder)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(role = Role.Button, onClick = onOpen, onLongClick = onMore)
            .padding(vertical = 4.dp)
            .testTag(testTag),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .aspectRatio(1.26f),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val r = w * 0.10f

                // 1. Back folder tab & rear cover
                val tabPath = Path().apply {
                    moveTo(0f, h * 0.22f)
                    lineTo(0f, h * 0.11f)
                    quadraticTo(0f, 0f, r * 0.9f, 0f)
                    lineTo(w * 0.36f, 0f)
                    quadraticTo(w * 0.42f, 0f, w * 0.46f, h * 0.08f)
                    lineTo(w * 0.50f, h * 0.14f)
                    lineTo(w - r, h * 0.14f)
                    quadraticTo(w, h * 0.14f, w, h * 0.14f + r)
                    lineTo(w, h - r)
                    quadraticTo(w, h, w - r, h)
                    lineTo(r, h)
                    quadraticTo(0f, h, 0f, h - r)
                    close()
                }
                drawPath(
                    path = tabPath,
                    brush = Brush.verticalGradient(
                        listOf(lerp(backColor, Color.Black, 0.08f), lerp(backColor, Color.Black, 0.22f)),
                    ),
                )

                // 2. Front 3D glossy folder pocket
                val pocketTop = h * 0.17f
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            lerp(frontColor, Color.White, 0.24f),
                            frontColor,
                            lerp(frontColor, backColor, 0.55f),
                        ),
                        start = Offset(0f, pocketTop),
                        end = Offset(w * 0.8f, h),
                    ),
                    topLeft = Offset(0f, pocketTop),
                    size = Size(w, h - pocketTop),
                    cornerRadius = CornerRadius(r, r),
                )

                // 3. Top specular rim highlight on front pocket
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.65f),
                            Color.White.copy(alpha = 0.10f),
                        ),
                    ),
                    topLeft = Offset(1.dp.toPx(), pocketTop + 1.dp.toPx()),
                    size = Size(w - 2.dp.toPx(), h - pocketTop - 2.dp.toPx()),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }

            // Star icon in the top-right corner of the front pocket (matches Goodnotes folders)
            Icon(
                imageVector = if (folder.noteCount > 0) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.88f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 10.dp)
                    .size(18.dp),
            )
        }

        Spacer(Modifier.height(6.dp))

        // Centered folder title + chevron (tapping chevron/more opens folder actions)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = folder.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (onMore != null) {
                IconButton(
                    onClick = onMore,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag(FolderTestTags.more(folder.id)),
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.folder_actions, folder.title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Text(
            text = summary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** "New folder" here, as a card next to the folders. */
@Composable
fun NewFolderCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(FolderTestTags.NEW_FOLDER),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.folder_new), style = MaterialTheme.typography.labelLarge)
        }
    }
}
