package com.appname.notes.feature.attachments

import android.graphics.Bitmap
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.appname.notes.R
import com.appname.notes.core.domain.model.Attachment
import com.appname.notes.core.domain.model.AttachmentKind

object AttachmentTestTags {
    const val STRIP = "attachment_strip"
    const val ATTACH_BUTTON = "attach_button"
    const val ATTACHING = "attaching_progress"
    const val VIEWER = "image_viewer"
    const val SHEET = "attachment_sheet"
    const val SCAN = "attach_scan"
    fun item(a: Attachment) = "attachment_${a.id.value}"
}

/** What the user can do with an attachment. Null actions aren't offered (e.g. no Remove in a trashed note). */
data class AttachmentActions(
    val open: (Attachment) -> Unit,
    val share: (Attachment) -> Unit,
    val openWith: (Attachment) -> Unit,
    val remove: ((Attachment) -> Unit)?,
    val annotate: ((Attachment) -> Unit)?,
)

/**
 * The note's attachments, above the text (FR-009): photos as a row of previews, other files as rows with
 * name, type and size. Every item opens on tap and has Share/Remove as TalkBack actions too.
 */
@Composable
fun AttachmentStrip(
    attachments: List<Attachment>,
    attaching: Int,
    previews: AttachmentPreviews,
    actions: AttachmentActions,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty() && attaching == 0) return
    var sheetFor by remember { mutableStateOf<Attachment?>(null) }
    val images = attachments.filter { it.kind == AttachmentKind.IMAGE }
    val files = attachments.filter { it.kind != AttachmentKind.IMAGE }
    Column(modifier.fillMaxWidth().testTag(AttachmentTestTags.STRIP), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (attaching > 0) {
            Column(Modifier.testTag(AttachmentTestTags.ATTACHING)) {
                Text(
                    pluralStringResource(R.plurals.attach_in_progress, attaching, attaching),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
        if (images.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(images, key = { it.id.value }) { a -> ImageTile(a, previews, actions, onMore = { sheetFor = a }) }
            }
        }
        files.forEach { a -> FileRow(a, previews, actions, onMore = { sheetFor = a }) }
    }
    sheetFor?.let { a -> AttachmentSheet(a, actions, onDismiss = { sheetFor = null }) }
}

@Composable
private fun accessibilityActions(a: Attachment, actions: AttachmentActions, onMore: () -> Unit): List<CustomAccessibilityAction> {
    val share = stringResource(R.string.attach_share)
    val remove = stringResource(R.string.attach_remove)
    val more = stringResource(R.string.attach_more)
    return listOfNotNull(
        CustomAccessibilityAction(share) { actions.share(a); true },
        actions.remove?.let { r -> CustomAccessibilityAction(remove) { r(a); true } },
        CustomAccessibilityAction(more) { onMore(); true },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageTile(a: Attachment, previews: AttachmentPreviews, actions: AttachmentActions, onMore: () -> Unit) {
    val w = a.width
    val h = a.height
    val ratio = if (w != null && h != null && h > 0) (w.toFloat() / h).coerceIn(0.6f, 1.8f) else 1f
    val label = stringResource(R.string.attach_photo_label, a.displayName)
    val custom = accessibilityActions(a, actions, onMore)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .height(120.dp)
            .aspectRatio(ratio)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClickLabel = stringResource(R.string.attach_open), onClick = { actions.open(a) }, onLongClick = onMore)
            .testTag(AttachmentTestTags.item(a))
            .semantics {
                contentDescription = label
                customActions = custom
            },
    ) {
        AttachmentPicture(a, previews, targetPx = with(LocalDensity.current) { 240.dp.roundToPx() }, Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(a: Attachment, previews: AttachmentPreviews, actions: AttachmentActions, onMore: () -> Unit) {
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, a.sizeBytes)
    val type = stringResource(if (a.kind == AttachmentKind.PDF) R.string.attach_type_pdf else R.string.attach_type_file)
    val custom = accessibilityActions(a, actions, onMore)
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClickLabel = stringResource(R.string.attach_open), onClick = { actions.open(a) }, onLongClick = onMore)
            .testTag(AttachmentTestTags.item(a))
            .semantics { customActions = custom },
    ) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (a.kind == AttachmentKind.PDF) {
                    AttachmentPicture(a, previews, targetPx = with(LocalDensity.current) { 80.dp.roundToPx() }, Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Outlined.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(a.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                Text("$type · $size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onMore) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.attach_more_for, a.displayName)) }
        }
    }
}

/** The attachment's preview, or a type icon while loading / when it has none. */
@Composable
fun AttachmentPicture(a: Attachment, previews: AttachmentPreviews, targetPx: Int, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val bitmap by produceState<Bitmap?>(null, a.fileName, targetPx) { value = previews.load(a.fileName, a.kind, targetPx) }
    val b = bitmap
    if (b != null) {
        Image(b.asImageBitmap(), contentDescription = null, contentScale = contentScale, modifier = modifier)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                if (a.kind == AttachmentKind.PDF) Icons.Outlined.PictureAsPdf else Icons.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSheet(a: Attachment, actions: AttachmentActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag(AttachmentTestTags.SHEET)) {
        Text(
            a.displayName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        fun act(block: (Attachment) -> Unit) { onDismiss(); block(a) }
        ListItem(
            headlineContent = { Text(stringResource(R.string.attach_open)) },
            leadingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
            modifier = Modifier.clickable { act(actions.open) },
        )
        actions.annotate?.takeIf { a.kind == AttachmentKind.PDF }?.let { annotate ->
            ListItem(
                headlineContent = { Text(stringResource(R.string.attach_annotate)) },
                supportingContent = { Text(stringResource(R.string.attach_annotate_summary)) },
                leadingContent = { Icon(Icons.Outlined.Draw, null) },
                modifier = Modifier.clickable { act(annotate) },
            )
        }
        if (a.kind == AttachmentKind.IMAGE) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.attach_open_with)) },
                leadingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                modifier = Modifier.clickable { act(actions.openWith) },
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.attach_share)) },
            leadingContent = { Icon(Icons.Outlined.Share, null) },
            modifier = Modifier.clickable { act(actions.share) },
        )
        actions.remove?.let { remove ->
            ListItem(
                headlineContent = { Text(stringResource(R.string.attach_remove)) },
                leadingContent = { Icon(Icons.Outlined.Delete, null) },
                modifier = Modifier.clickable { act(remove) },
            )
        }
        Box(Modifier.navigationBarsPadding().height(8.dp))
    }
}

/**
 * Full-screen photo viewer: pinch to zoom, drag to pan, double-tap to zoom in/out. The photo is decoded at
 * about screen size, not at camera resolution.
 */
@Composable
fun ImageViewer(a: Attachment, previews: AttachmentPreviews, actions: AttachmentActions, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val screenPx = with(LocalDensity.current) { 1600.dp.roundToPx() }.coerceAtMost(4096)
        Box(Modifier.fillMaxSize().background(Color.Black).testTag(AttachmentTestTags.VIEWER)) {
            AttachmentPicture(
                a,
                previews,
                targetPx = screenPx,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = a.displayName }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        })
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().background(Color.Black.copy(alpha = 0.4f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = Color.White) }
                Text(
                    a.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { actions.share(a) }) { Icon(Icons.Outlined.Share, stringResource(R.string.attach_share), tint = Color.White) }
                IconButton(onClick = { actions.openWith(a) }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.attach_open_with), tint = Color.White)
                }
                actions.remove?.let { remove ->
                    IconButton(onClick = { onDismiss(); remove(a) }) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.attach_remove), tint = Color.White)
                    }
                }
            }
        }
    }
}
