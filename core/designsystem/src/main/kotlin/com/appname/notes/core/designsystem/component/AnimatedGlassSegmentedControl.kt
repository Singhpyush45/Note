package com.appname.notes.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

private val SlidingItemIdGenerator = AtomicInteger(1)

private class SlidingRowTrackState {
    val itemSelected = mutableMapOf<Int, Boolean>()
    val itemX = mutableMapOf<Int, Float>()
    val itemW = mutableMapOf<Int, Float>()
    val itemH = mutableMapOf<Int, Float>()
    var activeItemId: Int = -1
    var prevX: Float = 0f
    var prevW: Float = 0f
    var currentX: Float = 0f
    var currentW: Float = 0f
    var hasHistory: Boolean = false

    val isSegmentedTrack: Boolean
        get() {
            val total = itemSelected.size
            val selectedCount = itemSelected.values.count { it }
            val unselectedCount = total - selectedCount
            return total in 2..6 && selectedCount == 1 && unselectedCount >= 1
        }
}

private val ActiveSlidingTracks = mutableStateMapOf<Int, SlidingRowTrackState>()

/**
 * Phase 7 — Universal Smooth-Sliding Segmented Background Modifier.
 * Supports BOTH translucent glass pills AND opaque theme-colored segmented pills (such as
 * `Compact | Comfortable`, `Typed | Handwriting`, `A4 | A4 wide | Long`, `Split pages | Infinite scroll`,
 * `Vertical | Horizontal`, `Light | Dark | System` in `SettingsScreen.kt`!).
 * Distinguishes segmented option rows (`selectedCount == 1 && unselectedCount >= 1`) from colour swatch rows
 * (`unselectedCount == 0`) so colour swatches stay static circles while segmented pills smoothly glide!
 */
fun Modifier.slidingGlassBackground(
    color: Color,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
): Modifier = composed {
    val isDark = LocalDarkTheme.current
    val isSelected = color.alpha > 0.01f && color != Color.Transparent

    val itemId = remember { SlidingItemIdGenerator.getAndIncrement() }
    var rowKey by remember { mutableIntStateOf(0) }
    var myX by remember { mutableFloatStateOf(0f) }
    var myW by remember { mutableFloatStateOf(0f) }
    var myH by remember { mutableFloatStateOf(0f) }
    var startFromX by remember { mutableFloatStateOf(0f) }
    var startFromW by remember { mutableFloatStateOf(0f) }
    val slideProgress = remember { Animatable(1f) }

    val track = if (rowKey != 0) ActiveSlidingTracks[rowKey] else null

    SideEffect {
        if (rowKey != 0) {
            val t = ActiveSlidingTracks.getOrPut(rowKey) { SlidingRowTrackState() }
            t.itemSelected[itemId] = isSelected
            if (myW > 1f) {
                t.itemX[itemId] = myX
                t.itemW[itemId] = myW
                t.itemH[itemId] = myH
            }
        }
    }

    LaunchedEffect(isSelected, rowKey) {
        if (isSelected && rowKey != 0) {
            val t = ActiveSlidingTracks.getOrPut(rowKey) { SlidingRowTrackState() }
            t.itemSelected[itemId] = true
            val px = t.itemX[itemId] ?: myX
            val pw = t.itemW[itemId] ?: myW
            if (t.hasHistory && t.activeItemId != -1 && t.activeItemId != itemId && abs(t.currentX - px) > 2f) {
                t.prevX = t.currentX
                t.prevW = t.currentW
                t.currentX = px
                t.currentW = pw
                t.activeItemId = itemId
                startFromX = t.prevX
                startFromW = t.prevW
                slideProgress.snapTo(0f)
                slideProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = 1050f,
                    ),
                )
            } else {
                t.currentX = px
                t.currentW = pw
                t.activeItemId = itemId
                t.hasHistory = true
                startFromX = px
                startFromW = pw
                slideProgress.snapTo(1f)
            }
        }
    }

    DisposableEffect(itemId) {
        onDispose {
            if (rowKey != 0) {
                ActiveSlidingTracks[rowKey]?.let {
                    it.itemSelected.remove(itemId)
                    it.itemX.remove(itemId)
                    it.itemW.remove(itemId)
                    it.itemH.remove(itemId)
                }
            }
        }
    }

    val defaultPillBorder = if (isDark) Color.White.copy(alpha = 0.24f) else Color(0xFF141518).copy(alpha = 0.16f)

    Modifier
        .onGloballyPositioned { coords ->
            if (coords.isAttached) {
                val winPos = coords.positionInWindow()
                val w = coords.size.width.toFloat()
                val h = coords.size.height.toFloat()
                val yBucket = (winPos.y.toInt() + 6) / 12
                val hBucket = (coords.size.height + 4) / 8
                val rk = (yBucket shl 12) xor hBucket
                rowKey = rk
                myX = winPos.x
                myW = w
                myH = h
                val t = ActiveSlidingTracks.getOrPut(rk) { SlidingRowTrackState() }
                t.itemSelected[itemId] = isSelected
                t.itemX[itemId] = winPos.x
                t.itemW[itemId] = w
                t.itemH[itemId] = h
                if (isSelected && !t.hasHistory && w > 1f) {
                    t.currentX = winPos.x
                    t.currentW = w
                    t.activeItemId = itemId
                    t.hasHistory = true
                }
            }
        }
        .drawBehind {
            if (isSelected && size.height <= 64.dp.toPx()) {
                val isCircularSwatch = abs(size.width - size.height) <= 8f && (track == null || !track.isSegmentedTrack)
                val useSliding = !isCircularSwatch && track != null && track.isSegmentedTrack && myW > 1f
                val prog = slideProgress.value
                val fromX = if (startFromW > 1f) startFromX else myX
                val fromW = if (startFromW > 1f) startFromW else size.width
                val offsetX = if (useSliding && prog < 0.999f) (fromX - myX) * (1f - prog) else 0f
                val pillW = if (useSliding && prog < 0.999f) (fromW + (size.width - fromW) * prog) else size.width
                val pillSize = Size(pillW.coerceAtLeast(1f), size.height)
                val outline = shape.createOutline(pillSize, layoutDirection, this)
                translate(left = offsetX, top = 0f) {
                    drawOutline(
                        outline = outline,
                        color = color,
                    )
                    if (useSliding) {
                        drawOutline(
                            outline = outline,
                            color = defaultPillBorder,
                            style = Stroke(width = 0.8.dp.toPx()),
                        )
                    }
                }
            }
        }
}

fun Modifier.slidingGlassBackground(
    brush: Brush,
    shape: Shape = CircleShape,
    alpha: Float = 1.0f,
): Modifier = this.background(brush = brush, shape = shape, alpha = alpha)

/**
 * Global Smooth Animated Segmented Selection Control.
 */
@Composable
fun <T> AnimatedGlassSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onSelect: (T) -> Unit,
    labelProvider: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    tagProvider: ((T) -> String?)? = null,
) {
    val isDark = LocalDarkTheme.current
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val trackFill = if (isDark) Color(0xFF16181D).copy(alpha = 0.86f) else Color(0xFFF3F4F6).copy(alpha = 0.92f)
    val trackBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    val thumbFill = if (isDark) Color(0xFF235342) else Color(0xFF1E3F32)
    val thumbBorder = if (isDark) Color.White.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.12f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackFill, CircleShape)
            .border(0.8.dp, trackBorder, CircleShape)
            .padding(3.dp)
            .selectableGroup(),
    ) {
        val count = items.size.coerceAtLeast(1)
        val spacing = 3.dp
        val itemWidth = (maxWidth - spacing * (count - 1)) / count
        val targetOffset = (itemWidth + spacing) * selectedIndex

        val slidingOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = 0.88f,
                stiffness = 1050f,
            ),
            label = "segmentedSlidingThumb",
        )

        Box(
            modifier = Modifier
                .graphicsLayer { translationX = slidingOffset.toPx() }
                .width(itemWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(thumbFill, CircleShape)
                .border(0.8.dp, thumbBorder, CircleShape),
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items.forEach { item ->
                val selected = item == selectedItem
                val text = labelProvider(item)
                val tag = tagProvider?.invoke(item)
                val textColor by animateColorAsState(
                    targetValue = when {
                        selected -> Color(0xFFFAF8F5)
                        isDark -> Color(0xFF9CA3AF)
                        else -> Color(0xFF5A606A)
                    },
                    animationSpec = tween(110),
                    label = "segmentedTextColor",
                )
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.02f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                    label = "segmentedScale",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(item) },
                        )
                        .then(if (tag != null) Modifier.testTag(tag) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                    )
                }
            }
        }
    }
}
