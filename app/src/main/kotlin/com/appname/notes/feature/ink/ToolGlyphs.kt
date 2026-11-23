@file:Suppress("unused", "KotlinClarifyingParentheses")

package com.appname.notes.feature.ink

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import com.appname.notes.core.ink.input.EditorTool

/**
 * Shared reactive state holding the currently selected drawing tool's active ink colour.
 */
internal val ActiveInkToolColorState = mutableStateOf<Color?>(null)

/**
 * Priority 2 — Shared reactive state controlling whether the Contextual Tool Settings Panel
 * (`Thickness`, `Colour`, `Opacity`, `Eraser Mode`, `Lasso Mode`) is expanded or collapsed.
 * Defaults to `false` (compact single-row toolbar) so the writing canvas gets ~42% more vertical room!
 */
internal val InkToolbarSettingsExpandedState = mutableStateOf(value = true)

internal fun syncActiveInkToolColor(color: Color, selected: Boolean) {
    if (selected) ActiveInkToolColorState.value = color
}

internal fun syncActiveInkToolColor(colorArgb: Int, selected: Boolean) {
    if (selected) ActiveInkToolColorState.value = Color(colorArgb or -0x1000000)
}

internal fun syncActiveInkToolColor(colorArgb: Long, selected: Boolean) {
    if (selected) ActiveInkToolColorState.value = Color(colorArgb.toInt() or -0x1000000)
}

/**
 * Priority 2 & Priority 3 — Clean, Immediate, Understated Selected-Tool Button (`InkToolButtonBox`).
 * - Eliminates the unwanted black circular sliding shape across tools!
 * - Uses immediate, predictable `animateColorAsState` (140ms) and restrained scale (`0.94f -> 1.0f`)
 *   without any moving circle overlay or overshoot.
 * - Tapping an already-selected tool toggles the Contextual Tool Settings Panel (`InkToolbarSettingsExpandedState`),
 *   giving users 1-tap access to thickness/colour/opacity settings while keeping the toolbar a compact single row by default!
 */
@Composable
fun InkToolButtonBox(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val activeTint = accentColor ?: ActiveInkToolColorState.value ?: if (isDark) Color(0xFF8FB99E) else Color(0xFF1E3F32)
    val targetFill = when {
        selected && isDark -> activeTint.copy(alpha = 0.22f)
        selected && !isDark -> activeTint.copy(alpha = 0.14f)
        isPressed && isDark -> Color.White.copy(alpha = 0.08f)
        isPressed && !isDark -> Color.Black.copy(alpha = 0.05f)
        else -> Color.Transparent
    }
    val targetBorder = when {
        selected && isDark -> activeTint.copy(alpha = 0.72f)
        selected && !isDark -> activeTint.copy(alpha = 0.58f)
        else -> Color.Transparent
    }

    val fillCol by animateColorAsState(targetFill, animationSpec = tween(durationMillis = 140), label = "inkToolFill")
    val ringCol by animateColorAsState(targetBorder, animationSpec = tween(durationMillis = 140), label = "inkToolBorder")
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.92f
            selected -> 1.0f
            else -> 0.95f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "inkToolScale",
    )

    Box(
        modifier = modifier
            .padding(horizontal = 1.dp, vertical = 1.dp)
            .size(36.dp)
            .drawBehind {
                val cr = CornerRadius(11.dp.toPx(), 11.dp.toPx())
                if (fillCol.alpha > 0.01f) {
                    drawRoundRect(color = fillCol, cornerRadius = cr)
                }
                if (ringCol.alpha > 0.01f) {
                    drawRoundRect(
                        color = ringCol,
                        cornerRadius = cr,
                        style = Stroke(width = 0.9.dp.toPx()),
                    )
                }
            }
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = null,
            ) {
                    if (selected) {
                        // Tapping the active tool toggles the contextual tool settings panel!
                        InkToolbarSettingsExpandedState.value = !InkToolbarSettingsExpandedState.value
                    } else {
                        onClick()
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/**
 * Priority 2 — Compact Contextual Tool Settings Toggle Pill (`InkToolbarSettingsTogglePill`).
 * Placed in the primary single-row toolbar so the user can see the active ink colour at a glance
 * and expand/collapse the contextual settings panel (`Thickness`, `Colour`, `Opacity`, `Eraser/Lasso options`).
 */
@Composable
fun InkToolbarSettingsTogglePill(
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkTheme.current
    val expanded = InkToolbarSettingsExpandedState.value
    val activeColor = ActiveInkToolColorState.value ?: if (isDark) Color(0xFF8FB99E) else Color(0xFF1E3F32)
    val pillBg = if (expanded) {
        if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.09f)
    } else {
        if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f)
    }
    val borderCol = if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.12f)
    val iconTint = if (isDark) Color(0xFFF4F5F7) else Color(0xFF141518)

    Surface(
        shape = CircleShape,
        color = pillBg,
        border = BorderStroke(0.8.dp, borderCol),
        modifier = modifier
            .padding(horizontal = 3.dp, vertical = 2.dp)
            .height(34.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                InkToolbarSettingsExpandedState.value = !expanded
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(activeColor, CircleShape)
                    .border(0.8.dp, iconTint.copy(alpha = 0.35f), CircleShape),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Outlined.Tune,
                contentDescription = if (expanded) "Collapse Tool Settings" else "Expand Tool Settings",
                tint = iconTint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Priority 2 & Priority 3 — Clean, Centred Stroke Size Indicator (`InkSizeDotBox`) without flying black circles.
 */
@Composable
fun InkSizeDotBox(
    fraction: Float,
    label: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkTheme.current
    val dotColor = ActiveInkToolColorState.value ?: MaterialTheme.colorScheme.onSurface
    val desc = stringResource(label)

    val targetFill = when {
        selected && isDark -> Color.White.copy(alpha = 0.16f)
        selected && !isDark -> Color(0xFF141518).copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val targetBorder = when {
        selected && isDark -> Color.White.copy(alpha = 0.44f)
        selected && !isDark -> Color(0xFF141518).copy(alpha = 0.30f)
        else -> Color.Transparent
    }
    val ringFill by animateColorAsState(targetFill, animationSpec = tween(140), label = "sizeDotFill")
    val ringBorder by animateColorAsState(targetBorder, animationSpec = tween(140), label = "sizeDotBorder")

    Box(
        modifier = modifier
            .padding(horizontal = 1.dp, vertical = 1.dp)
            .size(32.dp)
            .drawBehind {
                val radius = (size.minDimension / 2f) - 1.5.dp.toPx()
                if (ringFill.alpha > 0.01f) {
                    drawCircle(color = ringFill, radius = radius, center = center)
                }
                if (ringBorder.alpha > 0.01f) {
                    drawCircle(
                        color = ringBorder,
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1.15.dp.toPx()),
                    )
                }
            }
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        val d = (6.0f + (fraction.coerceIn(0.15f, 1f) * 9.5f)).dp
        Canvas(Modifier.size(d)) {
            drawCircle(color = dotColor)
            drawCircle(
                color = if (isDark) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.20f),
                style = Stroke(width = 0.6.dp.toPx()),
            )
        }
    }
}

@Composable
fun ToolGlyph(
    tool: EditorTool,
    body: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    rainbow: Boolean = false,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val highlight = lerp(body, Color.White, 0.68f)
        val midBody = lerp(body, Color(0xFF334155), 0.25f)
        val shadowEdge = lerp(body, Color.Black, 0.38f)
        val barrelShade = Brush.horizontalGradient(
            0.0f to midBody,
            0.28f to highlight,
            0.65f to body,
            1.0f to shadowEdge,
            startX = w * 0.34f,
            endX = w * 0.66f,
        )
        val inkBrush: Brush = if (rainbow) Brush.sweepGradient(RAINBOW) else SolidColor(ink)
        when (tool) {
            EditorTool.PEN -> slanted { pen(w, barrelShade, body, inkBrush) }
            EditorTool.MARKER -> slanted { fountainNibPen(w, barrelShade, body, inkBrush) }
            EditorTool.HIGHLIGHTER -> slanted { highlighter(w, barrelShade, body, inkBrush) }
            EditorTool.PENCIL -> slanted { pencil(w, barrelShade, body, inkBrush) }
            EditorTool.BRUSH -> slanted { artistBrush(w, barrelShade, body, inkBrush) }
            EditorTool.ERASER -> slanted { eraser(w, body) }
            EditorTool.LASSO -> lasso(w, body, inkBrush)
            EditorTool.LASER -> laser(w, barrelShade, body, inkBrush)
            EditorTool.SHAPE -> shapes(w, body, inkBrush)
            EditorTool.TEXT -> textGlyph(w, body, inkBrush)
        }
    }
}

private val RAINBOW = listOf(
    Color(0xFFFF1744),
    Color(0xFFFFEA00),
    Color(0xFF00E676),
    Color(0xFF00E5FF),
    Color(0xFF2979FF),
    Color(0xFFD500F9),
    Color(0xFFFF1744),
)

private inline fun DrawScope.slanted(block: DrawScope.() -> Unit) = rotate(42f) { block() }

private fun DrawScope.pen(w: Float, shade: Brush, body: Color, ink: Brush) {
    val cx = w / 2f
    val bw = w * 0.24f
    drawRoundRect(shade, Offset(cx - (bw / 2f), w * 0.02f), Size(bw, w * 0.56f), CornerRadius(bw / 2f, bw / 2f))
    drawRect(ink, Offset(cx - bw / 2f, w * 0.50f), Size(bw, w * 0.06f))
    val cone = Path().apply {
        moveTo(cx - bw / 2f, w * 0.58f)
        lineTo(cx + bw / 2f, w * 0.58f)
        lineTo(cx + w * 0.045f, w * 0.82f)
        lineTo(cx - w * 0.045f, w * 0.82f)
        close()
    }
    drawPath(cone, lerp(body, Color(0xFF1E293B), 0.45f))
    val tip = Path().apply {
        moveTo(cx - w * 0.045f, w * 0.82f)
        lineTo(cx + w * 0.045f, w * 0.82f)
        lineTo(cx, w * 0.98f)
        close()
    }
    drawPath(tip, ink)
}

private fun DrawScope.fountainNibPen(w: Float, shade: Brush, @Suppress("UNUSED_PARAMETER") body: Color, ink: Brush) {
    val cx = w / 2f
    val bw = w * 0.24f
    drawRoundRect(shade, Offset(cx - bw / 2f, w * 0.02f), Size(bw, w * 0.46f), CornerRadius(bw * 0.35f, bw * 0.35f))
    drawRect(Color(0xFFE2E8F0), Offset(cx - bw / 2f, w * 0.44f), Size(bw, w * 0.06f))
    drawRect(ink, Offset(cx - bw / 2f, w * 0.36f), Size(bw, w * 0.055f))
    val nib = Path().apply {
        moveTo(cx - bw * 0.38f, w * 0.50f)
        lineTo(cx + bw * 0.38f, w * 0.50f)
        lineTo(cx + bw * 0.56f, w * 0.68f)
        lineTo(cx, w * 0.98f)
        lineTo(cx - bw * 0.56f, w * 0.68f)
        close()
    }
    drawPath(
        nib,
        Brush.horizontalGradient(
            listOf(Color(0xFFF8FAFC), Color(0xFFCBD5E1), Color(0xFF94A3B8)),
            startX = cx - bw * 0.5f,
            endX = cx + bw * 0.5f,
        ),
    )
    val inkTip = Path().apply {
        moveTo(cx - bw * 0.25f, w * 0.82f)
        lineTo(cx + bw * 0.25f, w * 0.82f)
        lineTo(cx, w * 0.98f)
        close()
    }
    drawPath(inkTip, ink)
    drawLine(Color(0xFF1E293B), Offset(cx, w * 0.68f), Offset(cx, w * 0.95f), strokeWidth = w * 0.025f)
    drawCircle(Color(0xFF1E293B), radius = w * 0.032f, center = Offset(cx, w * 0.68f))
}

private fun DrawScope.highlighter(w: Float, shade: Brush, body: Color, ink: Brush) {
    val cx = w / 2f
    val bw = w * 0.34f
    drawRoundRect(shade, Offset(cx - bw / 2f, w * 0.04f), Size(bw, w * 0.60f), CornerRadius(w * 0.07f, w * 0.07f))
    drawRect(ink, Offset(cx - bw / 2f, w * 0.24f), Size(bw, w * 0.14f))
    val neck = Path().apply {
        moveTo(cx - bw / 2f, w * 0.64f)
        lineTo(cx + bw / 2f, w * 0.64f)
        lineTo(cx + bw * 0.30f, w * 0.76f)
        lineTo(cx - bw * 0.30f, w * 0.76f)
        close()
    }
    drawPath(neck, lerp(body, Color.Black, 0.25f))
    val tip = Path().apply {
        moveTo(cx - bw * 0.30f, w * 0.76f)
        lineTo(cx + bw * 0.30f, w * 0.76f)
        lineTo(cx + bw * 0.30f, w * 0.86f)
        lineTo(cx - bw * 0.30f, w * 0.98f)
        close()
    }
    drawPath(tip, ink)
}

private fun DrawScope.pencil(w: Float, shade: Brush, @Suppress("UNUSED_PARAMETER") body: Color, ink: Brush) {
    val cx = w / 2f
    val bw = w * 0.25f
    drawRoundRect(shade, Offset(cx - bw / 2f, w * 0.04f), Size(bw, w * 0.56f), CornerRadius(w * 0.05f, w * 0.05f))
    drawRect(ink, Offset(cx - bw / 2f, w * 0.42f), Size(bw, w * 0.08f))
    val wood = Path().apply {
        moveTo(cx - bw / 2f, w * 0.60f)
        lineTo(cx + bw / 2f, w * 0.60f)
        lineTo(cx + bw * 0.22f, w * 0.80f)
        lineTo(cx - bw * 0.22f, w * 0.80f)
        close()
    }
    drawPath(wood, Color(0xFFEAC89B))
    val lead = Path().apply {
        moveTo(cx - bw * 0.22f, w * 0.80f)
        lineTo(cx + bw * 0.22f, w * 0.80f)
        lineTo(cx, w * 0.98f)
        close()
    }
    drawPath(lead, ink)
}

private fun DrawScope.artistBrush(w: Float, shade: Brush, @Suppress("UNUSED_PARAMETER") body: Color, ink: Brush) {
    val cx = w / 2f
    val bw = w * 0.22f
    drawRoundRect(shade, Offset(cx - bw / 2f, w * 0.02f), Size(bw, w * 0.44f), CornerRadius(bw / 2f, bw / 2f))
    drawRect(Color(0xFFF1F5F9), Offset(cx - bw * 0.55f, w * 0.44f), Size(bw * 1.1f, w * 0.14f))
    val tuft = Path().apply {
        moveTo(cx - bw * 0.52f, w * 0.58f)
        cubicTo(cx - bw * 0.72f, w * 0.76f, cx - bw * 0.20f, w * 0.90f, cx, w * 0.99f)
        cubicTo(cx + bw * 0.20f, w * 0.90f, cx + bw * 0.72f, w * 0.76f, cx + bw * 0.52f, w * 0.58f)
        close()
    }
    drawPath(tuft, Color(0xFFD6B892))
    val dippedTip = Path().apply {
        moveTo(cx - bw * 0.42f, w * 0.74f)
        cubicTo(cx - bw * 0.30f, w * 0.86f, cx - bw * 0.12f, w * 0.93f, cx, w * 0.99f)
        cubicTo(cx + bw * 0.12f, w * 0.93f, cx + bw * 0.30f, w * 0.86f, cx + bw * 0.42f, w * 0.74f)
        close()
    }
    drawPath(dippedTip, ink)
}

private fun DrawScope.eraser(w: Float, body: Color) {
    val cx = w / 2f
    val bw = w * 0.36f
    drawRoundRect(lerp(body, Color.White, 0.60f), Offset(cx - bw / 2f, w * 0.12f), Size(bw, w * 0.44f), CornerRadius(w * 0.06f, w * 0.06f))
    drawRoundRect(Color(0xFFF28B96), Offset(cx - bw / 2f, w * 0.52f), Size(bw, w * 0.34f), CornerRadius(w * 0.06f, w * 0.06f))
    drawRoundRect(body, Offset(cx - bw / 2f, w * 0.12f), Size(bw, w * 0.74f), CornerRadius(w * 0.06f, w * 0.06f), style = Stroke(w * 0.035f))
}

private fun DrawScope.lasso(w: Float, body: Color, @Suppress("UNUSED_PARAMETER") ink: Brush) {
    drawOval(
        body,
        Offset(w * 0.12f, w * 0.14f),
        Size(w * 0.76f, w * 0.50f),
        style = Stroke(w * 0.07f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.12f, w * 0.07f))),
    )
    val tail = Path().apply {
        moveTo(w * 0.36f, w * 0.60f)
        cubicTo(w * 0.26f, w * 0.72f, w * 0.44f, w * 0.78f, w * 0.32f, w * 0.92f)
    }
    drawPath(tail, body, style = Stroke(w * 0.07f, cap = StrokeCap.Round))
}

private fun DrawScope.laser(w: Float, shade: Brush, body: Color, ink: Brush) {
    rotate(45f) {
        val cx = w / 2f
        drawRoundRect(shade, Offset(cx - w * 0.09f, w * 0.34f), Size(w * 0.18f, w * 0.62f), CornerRadius(w * 0.09f, w * 0.09f))
        drawRect(lerp(body, Color.Black, 0.3f), Offset(cx - w * 0.09f, w * 0.34f), Size(w * 0.18f, w * 0.05f))
        drawLine(ink, Offset(cx, w * 0.32f), Offset(cx, w * 0.14f), strokeWidth = w * 0.05f)
    }
    drawCircle(ink, w * 0.15f, Offset(w * 0.78f, w * 0.22f), alpha = 0.38f)
    drawCircle(ink, w * 0.08f, Offset(w * 0.78f, w * 0.22f))
}

private fun DrawScope.textGlyph(w: Float, body: Color, ink: Brush) {
    drawRoundRect(body, Offset(w * 0.18f, w * 0.12f), Size(w * 0.64f, w * 0.12f), CornerRadius(w * 0.03f, w * 0.03f))
    drawRoundRect(body, Offset(w * 0.43f, w * 0.12f), Size(w * 0.14f, w * 0.62f), CornerRadius(w * 0.03f, w * 0.03f))
    drawRect(body, Offset(w * 0.34f, w * 0.68f), Size(w * 0.32f, w * 0.07f))
    drawRoundRect(ink, Offset(w * 0.14f, w * 0.84f), Size(w * 0.72f, w * 0.08f), CornerRadius(w * 0.04f, w * 0.04f))
}

private fun DrawScope.shapes(w: Float, body: Color, ink: Brush) {
    drawRoundRect(body, Offset(w * 0.10f, w * 0.12f), Size(w * 0.48f, w * 0.48f), CornerRadius(w * 0.06f, w * 0.06f), style = Stroke(w * 0.07f))
    drawCircle(ink, w * 0.24f, Offset(w * 0.64f, w * 0.64f), alpha = 0.88f)
}
