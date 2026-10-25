package com.appname.notes.feature.ink

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appname.notes.core.ink.input.EditorTool

/**
 * Tool pictures for the tool bar, drawn like the real tools (owner request: premium icons): a body with soft
 * shading and the tip in the tool's current colour — so the colour is part of the tool, not a line under it.
 * [body] is the neutral body colour; [ink] the tool's colour ([rainbow] for the RGB laser).
 */
@Composable
fun ToolGlyph(tool: EditorTool, body: Color, ink: Color, modifier: Modifier = Modifier, size: Dp = 30.dp, rainbow: Boolean = false) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val light = lerp(body, Color.White, 0.55f)
        val shade = Brush.linearGradient(listOf(light, body), start = Offset(0f, 0f), end = Offset(w * 0.25f, w * 0.25f))
        val inkBrush: Brush = if (rainbow) Brush.sweepGradient(RAINBOW) else androidx.compose.ui.graphics.SolidColor(ink)
        when (tool) {
            EditorTool.PEN -> slanted { pen(w, shade, body, inkBrush, nibWidth = 0.16f) }
            EditorTool.MARKER -> slanted { marker(w, shade, body, inkBrush, chisel = false) }
            EditorTool.HIGHLIGHTER -> slanted { marker(w, shade, body, inkBrush, chisel = true) }
            EditorTool.PENCIL -> slanted { pencil(w, body, inkBrush) }
            EditorTool.BRUSH -> slanted { brush(w, shade, body, inkBrush) }
            EditorTool.ERASER -> slanted { eraser(w, body) }
            EditorTool.LASSO -> lasso(w, body, inkBrush)
            EditorTool.LASER -> laser(w, shade, body, inkBrush)
            EditorTool.SHAPE -> shapes(w, body, inkBrush)
            EditorTool.TEXT -> textGlyph(w, body, inkBrush)
        }
    }
}

private val RAINBOW = listOf(Color(0xFFFF1744), Color(0xFFFFEA00), Color(0xFF00E676), Color(0xFF00E5FF), Color(0xFF2979FF), Color(0xFFD500F9), Color(0xFFFF1744))

/** Tools lie at 45°, tip at the bottom-left, like a pen held in the hand. Drawn upright, then turned. */
private inline fun DrawScope.slanted(block: DrawScope.() -> Unit) = rotate(45f) { block() }

/** Upright pen: rounded body, clip, a metal grip and a pointed nib whose tip is the ink colour. */
private fun DrawScope.pen(w: Float, shade: Brush, body: Color, ink: Brush, nibWidth: Float) {
    val cx = w / 2
    val bw = w * 0.22f
    drawRoundRect(shade, Offset(cx - bw / 2, w * 0.02f), Size(bw, w * 0.58f), CornerRadius(bw / 2, bw / 2))
    // Clip on the cap.
    drawRoundRect(body, Offset(cx + bw / 2 - w * 0.02f, w * 0.08f), Size(w * 0.05f, w * 0.22f), CornerRadius(w * 0.03f, w * 0.03f))
    // Grip.
    drawRect(lerp(body, Color.Black, 0.25f), Offset(cx - bw / 2, w * 0.58f), Size(bw, w * 0.08f))
    // Nib: a cone, ink at the very tip.
    val cone = Path().apply {
        moveTo(cx - bw / 2, w * 0.66f); lineTo(cx + bw / 2, w * 0.66f); lineTo(cx + w * nibWidth / 4, w * 0.86f); lineTo(cx - w * nibWidth / 4, w * 0.86f); close()
    }
    drawPath(cone, lerp(body, Color.White, 0.3f))
    val tip = Path().apply { moveTo(cx - w * nibWidth / 4, w * 0.86f); lineTo(cx + w * nibWidth / 4, w * 0.86f); lineTo(cx, w * 0.98f); close() }
    drawPath(tip, ink)
}

/** Marker / highlighter: thicker body, coloured cap band, and a round or chisel tip in the ink colour. */
private fun DrawScope.marker(w: Float, shade: Brush, body: Color, ink: Brush, chisel: Boolean) {
    val cx = w / 2
    val bw = w * (if (chisel) 0.34f else 0.28f)
    drawRoundRect(shade, Offset(cx - bw / 2, w * 0.04f), Size(bw, w * 0.62f), CornerRadius(w * 0.07f, w * 0.07f))
    // Colour band: you can tell the colour from the side, like a real marker.
    drawRect(ink, Offset(cx - bw / 2, w * 0.12f), Size(bw, w * 0.08f))
    val neck = Path().apply { moveTo(cx - bw / 2, w * 0.66f); lineTo(cx + bw / 2, w * 0.66f); lineTo(cx + bw * 0.28f, w * 0.78f); lineTo(cx - bw * 0.28f, w * 0.78f); close() }
    drawPath(neck, lerp(body, Color.Black, 0.2f))
    if (chisel) {
        val tip = Path().apply { moveTo(cx - bw * 0.28f, w * 0.78f); lineTo(cx + bw * 0.28f, w * 0.78f); lineTo(cx + bw * 0.28f, w * 0.86f); lineTo(cx - bw * 0.28f, w * 0.97f); close() }
        drawPath(tip, ink)
    } else {
        drawRoundRect(ink, Offset(cx - bw * 0.2f, w * 0.77f), Size(bw * 0.4f, w * 0.2f), CornerRadius(bw * 0.2f, bw * 0.2f))
    }
}

/** Pencil: painted six-sided body, rubber and metal band at the top, sharpened wood, graphite in the colour. */
private fun DrawScope.pencil(w: Float, body: Color, ink: Brush) {
    val cx = w / 2
    val bw = w * 0.24f
    drawRoundRect(Color(0xFFE88E9A), Offset(cx - bw / 2, w * 0.0f), Size(bw, w * 0.1f), CornerRadius(w * 0.05f, w * 0.05f))
    drawRect(Color(0xFFB8BCC2), Offset(cx - bw / 2, w * 0.08f), Size(bw, w * 0.08f))
    drawRect(lerp(body, Color.White, 0.45f), Offset(cx - bw / 2, w * 0.16f), Size(bw / 3, w * 0.5f))
    drawRect(lerp(body, Color.White, 0.2f), Offset(cx - bw / 6, w * 0.16f), Size(bw / 3, w * 0.5f))
    drawRect(body, Offset(cx + bw / 6, w * 0.16f), Size(bw / 3, w * 0.5f))
    val wood = Path().apply { moveTo(cx - bw / 2, w * 0.66f); lineTo(cx + bw / 2, w * 0.66f); lineTo(cx + bw * 0.16f, w * 0.86f); lineTo(cx - bw * 0.16f, w * 0.86f); close() }
    drawPath(wood, Color(0xFFE9C79A))
    val lead = Path().apply { moveTo(cx - bw * 0.16f, w * 0.86f); lineTo(cx + bw * 0.16f, w * 0.86f); lineTo(cx, w * 0.98f); close() }
    drawPath(lead, ink)
}

/** Brush: long handle, metal ferrule and a bristle tuft dipped in the colour. */
private fun DrawScope.brush(w: Float, shade: Brush, body: Color, ink: Brush) {
    val cx = w / 2
    drawRoundRect(shade, Offset(cx - w * 0.07f, w * 0.0f), Size(w * 0.14f, w * 0.52f), CornerRadius(w * 0.07f, w * 0.07f))
    drawRect(Color(0xFFB8BCC2), Offset(cx - w * 0.1f, w * 0.5f), Size(w * 0.2f, w * 0.14f))
    val tuft = Path().apply {
        moveTo(cx - w * 0.1f, w * 0.64f)
        cubicTo(cx - w * 0.16f, w * 0.78f, cx - w * 0.04f, w * 0.9f, cx, w * 0.99f)
        cubicTo(cx + w * 0.04f, w * 0.9f, cx + w * 0.16f, w * 0.78f, cx + w * 0.1f, w * 0.64f)
        close()
    }
    drawPath(tuft, ink)
}

/** Eraser: a two-tone block. */
private fun DrawScope.eraser(w: Float, body: Color) {
    val cx = w / 2
    val bw = w * 0.36f
    drawRoundRect(lerp(body, Color.White, 0.55f), Offset(cx - bw / 2, w * 0.14f), Size(bw, w * 0.44f), CornerRadius(w * 0.06f, w * 0.06f))
    drawRoundRect(Color(0xFFE88E9A), Offset(cx - bw / 2, w * 0.54f), Size(bw, w * 0.32f), CornerRadius(w * 0.06f, w * 0.06f))
    drawRoundRect(body, Offset(cx - bw / 2, w * 0.14f), Size(bw, w * 0.72f), CornerRadius(w * 0.06f, w * 0.06f), style = Stroke(w * 0.035f))
}

/** Lasso: a dashed loop with a tail. */
private fun DrawScope.lasso(w: Float, body: Color, @Suppress("UNUSED_PARAMETER") ink: Brush) {
    drawOval(
        body,
        Offset(w * 0.12f, w * 0.14f),
        Size(w * 0.76f, w * 0.5f),
        style = Stroke(w * 0.07f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(w * 0.12f, w * 0.07f))),
    )
    val tail = Path().apply { moveTo(w * 0.36f, w * 0.6f); cubicTo(w * 0.26f, w * 0.72f, w * 0.44f, w * 0.78f, w * 0.32f, w * 0.92f) }
    drawPath(tail, body, style = Stroke(w * 0.07f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
}

/** Laser: a slim pointer with a glowing dot in the colour. */
private fun DrawScope.laser(w: Float, shade: Brush, body: Color, ink: Brush) {
    rotate(45f) {
        val cx = w / 2
        drawRoundRect(shade, Offset(cx - w * 0.09f, w * 0.34f), Size(w * 0.18f, w * 0.62f), CornerRadius(w * 0.09f, w * 0.09f))
        drawRect(lerp(body, Color.Black, 0.3f), Offset(cx - w * 0.09f, w * 0.34f), Size(w * 0.18f, w * 0.05f))
        drawLine(ink, Offset(cx, w * 0.32f), Offset(cx, w * 0.14f), strokeWidth = w * 0.05f)
    }
    drawCircle(ink, w * 0.14f, Offset(w * 0.78f, w * 0.22f), alpha = 0.35f)
    drawCircle(ink, w * 0.08f, Offset(w * 0.78f, w * 0.22f))
}

/** Text: a serif "T" with a coloured underline (the text colour). */
private fun DrawScope.textGlyph(w: Float, body: Color, ink: Brush) {
    drawRoundRect(body, Offset(w * 0.18f, w * 0.12f), Size(w * 0.64f, w * 0.12f), CornerRadius(w * 0.03f, w * 0.03f))
    drawRoundRect(body, Offset(w * 0.43f, w * 0.12f), Size(w * 0.14f, w * 0.62f), CornerRadius(w * 0.03f, w * 0.03f))
    drawRect(body, Offset(w * 0.34f, w * 0.68f), Size(w * 0.32f, w * 0.07f))
    drawRoundRect(ink, Offset(w * 0.14f, w * 0.84f), Size(w * 0.72f, w * 0.08f), CornerRadius(w * 0.04f, w * 0.04f))
}

/** Shapes: a square and a circle overlapping, the circle in the colour. */
private fun DrawScope.shapes(w: Float, body: Color, ink: Brush) {
    drawRoundRect(body, Offset(w * 0.1f, w * 0.12f), Size(w * 0.48f, w * 0.48f), CornerRadius(w * 0.06f, w * 0.06f), style = Stroke(w * 0.07f))
    drawCircle(ink, w * 0.24f, Offset(w * 0.64f, w * 0.64f), alpha = 0.85f)
}
