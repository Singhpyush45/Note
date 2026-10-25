package com.appname.notes.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.appname.notes.core.designsystem.theme.GlassStyle
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import com.appname.notes.core.designsystem.theme.LocalGlass
import com.appname.notes.core.designsystem.theme.supportsBlur

/**
 * What glass surfaces blur: the content drawn by [backdropSource] (the scrolling list, the wallpaper) is recorded
 * into a graphics layer once per frame; each glass surface over it draws the part under itself from that layer,
 * blurred, then its tint (ADR-0012). No copy of pixels is kept between frames.
 *
 * Rules: a glass surface must not be inside the content it blurs (that would draw itself into itself), and on
 * phones without hardware blur (Android 11 and older, or software rendering) surfaces are simply more opaque.
 */
@Stable
class Backdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
    internal var recorded by mutableStateOf(false)
}

/** The backdrop glass surfaces on this screen blur; null when nothing is recorded (glass is then tinted only). */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** Records this content as [backdrop] for glass surfaces drawn over it, and draws it as usual. */
fun Modifier.backdropSource(backdrop: Backdrop?, enabled: Boolean = true): Modifier =
    if (backdrop == null || !enabled || !supportsBlur()) {
        this
    } else {
        this
            .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
            .drawWithContent {
                if (!drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                    backdrop.recorded = false
                    drawContent()
                    return@drawWithContent
                }
                backdrop.layer.record { this@drawWithContent.drawContent() }
                drawLayer(backdrop.layer)
                backdrop.recorded = true
            }
    }

/**
 * A restrained glass surface (owner brief §1): the blurred backdrop, a translucent tint of [tint], a soft top
 * highlight, a fine low-contrast border and a soft shadow. Clearer or more solid with [style].
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    tint: Color = MaterialTheme.colorScheme.surface,
    elevation: Dp = 2.dp,
    style: GlassStyle = LocalGlass.current,
    backdrop: Backdrop? = LocalBackdrop.current,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = LocalDarkTheme.current
    val blurLayer = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .onGloballyPositioned { position = it.positionInRoot() }
            .drawBehind { drawGlass(shape, tint, style, dark, backdrop, blurLayer, position, elevation) },
        content = content,
    )
}

private fun shadowColor(dark: Boolean) = if (dark) Color.Black else Color(0xFF1B2440)

/**
 * A soft shadow drawn only outside the shape (a platform elevation shadow would show through translucent glass as
 * a grey smudge): rings of fading tint, slightly lower than the surface, like light from above.
 */
private fun DrawScope.softShadow(outline: Outline, path: Path, elevation: Dp, dark: Boolean) {
    val rounded = (outline as? Outline.Rounded)?.roundRect ?: return
    val spread = elevation.toPx() * 3f
    val steps = 8
    val color = shadowColor(dark)
    val strength = if (dark) 0.35f else 0.10f
    clipPath(path, ClipOp.Difference) {
        for (i in steps downTo 1) {
            val f = i / steps.toFloat()
            val grow = spread * f
            val shifted = RoundRect(
                left = rounded.left - grow,
                top = rounded.top - grow + spread * 0.35f,
                right = rounded.right + grow,
                bottom = rounded.bottom + grow + spread * 0.35f,
                cornerRadius = CornerRadius(rounded.topLeftCornerRadius.x + grow, rounded.topLeftCornerRadius.y + grow),
            )
            drawPath(Path().apply { addRoundRect(shifted) }, color.copy(alpha = strength * (1f - f) * (1f - f) / steps * 4f))
        }
    }
}

private fun DrawScope.drawGlass(
    shape: Shape,
    tint: Color,
    style: GlassStyle,
    dark: Boolean,
    backdrop: Backdrop?,
    blurLayer: GraphicsLayer,
    position: Offset,
    elevation: Dp,
) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    if (elevation > 0.dp) softShadow(outline, path, elevation, dark)
    val blurs = backdrop != null && backdrop.recorded && style.blur > 0f && supportsBlur() &&
        drawContext.canvas.nativeCanvas.isHardwareAccelerated
    clipPath(path) {
        if (blurs) {
            val radius = style.blurRadius.toPx()
            blurLayer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
            blurLayer.record(IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
                translate(backdrop!!.origin.x - position.x, backdrop.origin.y - position.y) { drawLayer(backdrop.layer) }
            }
            drawLayer(blurLayer)
        }
        drawRect(tint.copy(alpha = style.tintAlpha(blurs)))
        // Light catching the top edge: very subtle.
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (dark) 0.06f else 0.35f * style.intensity),
                0.35f to Color.Transparent,
            ),
        )
    }
    // Fine border, brighter at the top like a real glass edge.
    drawOutline(
        outline,
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (dark) 0.16f else 0.70f),
                (if (dark) Color.White.copy(alpha = 0.05f) else Color(0xFF1B2440).copy(alpha = 0.08f)),
            ),
        ),
        style = Stroke(width = 1.dp.toPx()),
    )
}

private fun Path.addOutline(outline: Outline) {
    when (outline) {
        is Outline.Rectangle -> addRect(outline.rect)
        is Outline.Rounded -> addRoundRect(outline.roundRect)
        is Outline.Generic -> addPath(outline.path)
    }
}
