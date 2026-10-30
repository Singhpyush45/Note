package com.appname.notes.feature.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.appname.notes.R
import com.appname.notes.core.ink.input.EditorTool
import kotlin.math.roundToInt

/**
 * The tool bar in full screen (owner request): a slim floating bar, upright on the side by default (in portrait and
 * landscape) or across; drag its handle to put it anywhere. It holds leave full screen, upright/across, undo, redo,
 * the tools, the ruler, tool options, Insert, Paste, and Add Page. Tapping the chosen tool again (or the options button)
 * shows the tool's options in a pill at the bottom; a lasso selection shows its actions there too.
 */
@Composable
fun FloatingInkToolbar(
    ui: InkEditorUi,
    actions: InkToolActions,
    panelOpen: Boolean,
    onPanel: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExit: () -> Unit,
    insert: InsertActions?,
    modifier: Modifier = Modifier,
    onAddPage: (() -> Unit)? = null,
) {
    var vertical by rememberSaveable { mutableStateOf(true) }
    var posX by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    var posY by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val margin = with(LocalDensity.current) { 8.dp.toPx() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val maxWidthDp = maxWidth
        val maxHeightDp = maxHeight
        fun clampX(x: Float) = x.coerceIn(margin, (maxW - barSize.width - margin).coerceAtLeast(margin))
        fun clampY(y: Float) = y.coerceIn(margin, (maxH - barSize.height - margin).coerceAtLeast(margin))
        fun currentX() = if (posX.isNaN()) margin else clampX(posX)
        fun currentY() = if (posY.isNaN()) ((maxH - barSize.height) / 2).coerceAtLeast(margin) else clampY(posY)

        val showOptions = optionsOpen || (ui.tools.tool == EditorTool.LASSO && ui.hasSelection)
        val bottomClearance = if (vertical && showOptions) 88.dp else 16.dp
        if (showOptions) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).widthIn(max = 640.dp),
            ) {
                ToolOptionsRow(ui, actions, panelOpen, onPanel)
            }
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .offset { IntOffset(currentX().roundToInt(), currentY().roundToInt()) }
                .onSizeChanged { barSize = it }
                .testTag(InkToolTags.FLOATING_BAR),
        ) {
            val moveLabel = stringResource(R.string.ink_bar_move)
            val handle = Modifier
                .size(if (vertical) 48.dp else 32.dp, if (vertical) 32.dp else 48.dp)
                .pointerInput(maxW, maxH) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        posX = clampX(currentX() + drag.x)
                        posY = clampY(currentY() + drag.y)
                    }
                }
                .semantics { contentDescription = moveLabel }
                .testTag(InkToolTags.BAR_HANDLE)
            val items: @Composable () -> Unit = {
                Box(handle, contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.DragIndicator,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (vertical) Modifier.rotate(90f) else Modifier,
                    )
                }
                FullScreenButton(entering = false, onClick = onExit)
                IconButton(onClick = { vertical = !vertical }, modifier = Modifier.testTag(InkToolTags.BAR_ORIENTATION)) {
                    Icon(
                        if (vertical) Icons.Outlined.SwapHoriz else Icons.Outlined.SwapVert,
                        stringResource(if (vertical) R.string.ink_bar_horizontal else R.string.ink_bar_vertical),
                    )
                }
                UndoRedoButtons(ui, onUndo, onRedo)
                BarDivider(vertical)
                ToolButtons(ui, actions) { optionsOpen = !optionsOpen }
                BarDivider(vertical)
                RulerButton(ui, actions)
                IconButton(onClick = { optionsOpen = !optionsOpen }) {
                    Icon(
                        Icons.Outlined.Tune,
                        stringResource(R.string.ink_bar_options),
                        tint = if (showOptions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (insert != null) {
                    InsertButton(insert)
                    IconButton(
                        onClick = insert.paste,
                        modifier = Modifier.testTag(InkToolTags.FULL_SCREEN_PASTE),
                    ) {
                        Icon(Icons.Outlined.ContentPaste, stringResource(R.string.insert_paste))
                    }
                }
                if (onAddPage != null) {
                    IconButton(
                        onClick = onAddPage,
                        modifier = Modifier.testTag(InkToolTags.FULL_SCREEN_ADD_PAGE),
                    ) {
                        Icon(
                            Icons.Outlined.NoteAdd,
                            stringResource(if (ui.isPdf) R.string.pdf_add_blank_page else R.string.ink_page_add),
                        )
                    }
                }
            }
            if (vertical) {
                Column(
                    Modifier
                        .heightIn(max = maxHeightDp - bottomClearance)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { items() }
            } else {
                Row(
                    Modifier
                        .widthIn(max = maxWidthDp - 16.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { items() }
            }
        }
    }
}

@Composable
private fun BarDivider(vertical: Boolean) {
    Box(
        Modifier
            .padding(horizontal = if (vertical) 0.dp else 3.dp, vertical = if (vertical) 3.dp else 0.dp)
            .size(width = if (vertical) 24.dp else 1.dp, height = if (vertical) 1.dp else 24.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
