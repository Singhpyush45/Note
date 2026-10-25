package com.appname.notes.feature.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appname.notes.R
import com.appname.notes.core.ink.model.TextAlign
import com.appname.notes.core.ink.model.TextFont
import kotlin.math.roundToInt
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign

/** Blue of the text cursor and the box outline while typing on the page. */
private val CursorBlue = Color(0xFF1A73E8)

/**
 * Typing straight onto the page (owner request): the text box sits exactly where it will be saved, at the page's
 * zoom, with a blue cursor; a slim bar at the top changes font, size, bold, italic, alignment, colour and adds maths
 * symbols. The four-arrow handle moves the box. Tapping the page (or ✓, or Back) finishes; empty text removes the box.
 */
@Composable
fun InlineTextEditor(
    request: TextEditRequest,
    view: InkCanvasView,
    transformTick: Int,
    onChange: (String, TextStyle) -> Unit,
    onMove: (Float, Float) -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var value by remember(request.key) { mutableStateOf(TextFieldValue(request.text, TextRange(request.text.length))) }
    val style = request.style
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(request.key) {
        focus.requestFocus()
        keyboard?.show()
    }
    if (transformTick < 0) return // read so the box is placed again whenever the page moves under it
    val corner = view.screenPoint(request.pageId, request.left, request.top) ?: return
    val scale = view.pxPerUnit

    Box(modifier.fillMaxSize()) {
        // Keep the box above the keyboard: scroll the page up if the keyboard would cover it.
        val ime = WindowInsets.ime.getBottom(density)
        val windowHeight = LocalWindowInfo.current.containerSize.height
        var fieldBottom by remember { mutableIntStateOf(0) }
        LaunchedEffect(ime, fieldBottom) {
            val limit = windowHeight - ime - with(density) { 24.dp.roundToPx() }
            if (ime > 0 && fieldBottom > limit) view.panBy(0f, -(fieldBottom - limit).toFloat())
        }

        val fontSize = with(density) { (style.size * scale).toSp() }
        BasicTextField(
            value = value,
            onValueChange = {
                value = it
                onChange(it.text, style)
            },
            textStyle = ComposeTextStyle(
                color = Color(style.colorArgb),
                fontSize = fontSize,
                fontFamily = style.font.composeFamily(),
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                textAlign = when (style.align) {
                    TextAlign.START -> ComposeTextAlign.Start
                    TextAlign.CENTER -> ComposeTextAlign.Center
                    TextAlign.END -> ComposeTextAlign.End
                },
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            cursorBrush = SolidColor(CursorBlue),
            modifier = Modifier
                .offset { IntOffset(corner.x.roundToInt(), corner.y.roundToInt()) }
                .width(with(density) { (request.width * scale).toDp() })
                .graphicsLayer {
                    rotationZ = request.rotationDeg
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .border(1.dp, CursorBlue.copy(alpha = 0.55f))
                .focusRequester(focus)
                .onGloballyPositioned { c ->
                    val b = c.boundsInWindow()
                    fieldBottom = b.bottom.roundToInt()
                }
                .testTag(InsertTags.TEXT_FIELD),
        )
        // Move handle, just outside the box's top-left corner.
        val moveLabel = stringResource(R.string.text_move)
        val handleSize = 32.dp
        Box(
            Modifier
                .offset {
                    val h = handleSize.roundToPx()
                    IntOffset(corner.x.roundToInt() - h, corner.y.roundToInt() - h)
                }
                .size(handleSize)
                .background(CursorBlue, CircleShape)
                .pointerInput(request.key, scale) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onMove(drag.x / scale, drag.y / scale)
                    }
                }
                .semantics { contentDescription = moveLabel }
                .testTag(InsertTags.TEXT_MOVE),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.OpenWith, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }

        TextFormatBar(
            style = style,
            canDelete = request.existing != null || value.text.isNotEmpty(),
            onStyle = { onChange(value.text, it) },
            onInsert = { sym ->
                val t = value.text
                val sel = value.selection
                val next = t.substring(0, sel.min) + sym + t.substring(sel.max)
                value = TextFieldValue(next, TextRange(sel.min + sym.length))
                onChange(next, style)
            },
            onDone = onDone,
            onDelete = onDelete,
            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
        )
    }
}

@Composable
private fun TextFormatBar(
    style: TextStyle,
    canDelete: Boolean,
    onStyle: (TextStyle) -> Unit,
    onInsert: (String) -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var maths by remember { mutableStateOf(false) }
    var fonts by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        modifier = modifier.widthIn(max = 720.dp).testTag(InsertTags.TEXT_BAR),
    ) {
        Column {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDone, modifier = Modifier.testTag(InsertTags.TEXT_SAVE)) {
                    Icon(Icons.Filled.Check, stringResource(R.string.text_done), tint = CursorBlue)
                }
                Box {
                    TextButton(onClick = { fonts = true }) {
                        Text(stringResource(style.font.label()), fontFamily = style.font.composeFamily(), maxLines = 1)
                    }
                    DropdownMenu(expanded = fonts, onDismissRequest = { fonts = false }) {
                        TextFont.entries.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(stringResource(f.label()), fontFamily = f.composeFamily()) },
                                onClick = { fonts = false; onStyle(style.copy(font = f)) },
                                modifier = Modifier.testTag(InsertTags.font(f)),
                            )
                        }
                    }
                }
                IconButton(onClick = { onStyle(style.copy(size = (style.size - 2f).coerceAtLeast(MIN_SIZE))) }) {
                    Icon(Icons.Outlined.Remove, stringResource(R.string.text_size_smaller))
                }
                Text("${style.size.roundToInt()}", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { onStyle(style.copy(size = (style.size + 2f).coerceAtMost(MAX_SIZE))) }) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.text_size_bigger))
                }
                IconToggleButton(checked = style.bold, onCheckedChange = { onStyle(style.copy(bold = it)) }) {
                    Icon(Icons.Outlined.FormatBold, stringResource(R.string.text_box_bold))
                }
                IconToggleButton(checked = style.italic, onCheckedChange = { onStyle(style.copy(italic = it)) }) {
                    Icon(Icons.Outlined.FormatItalic, stringResource(R.string.text_box_italic))
                }
                val nextAlign = TextAlign.entries[(style.align.ordinal + 1) % TextAlign.entries.size]
                IconButton(onClick = { onStyle(style.copy(align = nextAlign)) }) {
                    Icon(
                        when (style.align) {
                            TextAlign.START -> Icons.AutoMirrored.Outlined.FormatAlignLeft
                            TextAlign.CENTER -> Icons.Outlined.FormatAlignCenter
                            TextAlign.END -> Icons.AutoMirrored.Outlined.FormatAlignRight
                        },
                        stringResource(
                            when (style.align) {
                                TextAlign.START -> R.string.text_box_align_start
                                TextAlign.CENTER -> R.string.text_box_align_center
                                TextAlign.END -> R.string.text_box_align_end
                            },
                        ),
                    )
                }
                (listOf(0xFF1F1F1F.toInt()) + QuickColors).distinct().take(7).forEach { c ->
                    ColorDot(c, selected = (c and 0xFFFFFF) == (style.colorArgb and 0xFFFFFF), onClick = { onStyle(style.copy(colorArgb = c or (0xFF shl 24))) })
                }
                IconToggleButton(checked = maths, onCheckedChange = { maths = it }) {
                    Icon(Icons.Outlined.Functions, stringResource(R.string.text_maths))
                }
                if (canDelete) {
                    IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                }
            }
            if (maths) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    MathSymbols.forEach { sym ->
                        Box(
                            Modifier
                                .size(44.dp)
                                .clickable { onInsert(sym) }
                                .testTag(InsertTags.math(sym)),
                            contentAlignment = Alignment.Center,
                        ) { Text(sym, fontSize = 18.sp) }
                    }
                }
            }
        }
    }
}

private const val MIN_SIZE = 8f
private const val MAX_SIZE = 96f
