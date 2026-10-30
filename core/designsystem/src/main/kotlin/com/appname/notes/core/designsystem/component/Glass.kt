package com.appname.notes.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.appname.notes.core.designsystem.theme.GlassStyle
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import com.appname.notes.core.designsystem.theme.LocalGlass
import com.appname.notes.core.designsystem.theme.supportsBlur

/**
 * What glass surfaces blur: the content drawn by [backdropSource] (the scrolling list, the wallpaper) is recorded
 * into a graphics layer once per frame; each glass surface over it draws the part under itself from that layer,
 * blurred, then its refractive Liquid Glass layers (ADR-0012). No copy of pixels is kept between frames.
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

private data class ShadowRing(val path: Path, val color: Color)

/**
 * Tactile Liquid Glass surface (owner brief §1) with zero per-frame object allocations during scrolling:
 * - Hardware backdrop blur (Android 12+)
 * - Multi-stop refractive glass body gradient (cool porcelain frost in Light mode, deep obsidian-sapphire in Dark mode)
 * - Top curved-lens specular sheen + inner recessed 3D bevel highlight
 * - Diagonal specular rim border (bright top-left light catch, soft bottom-right edge)
 * - Soft ambient drop shadow cached in [drawWithCache]
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
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val clipPath = Path().apply { addOutline(outline) }
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)

                // Precompute shadow rings once per size/elevation change (0 allocations during scroll frames)
                val shadowRings: List<ShadowRing> = if (elevation > 0.dp && outline is Outline.Rounded) {
                    val rounded = outline.roundRect
                    val spread = elevation.toPx() * 2.8f
                    val steps = 6
                    val baseShadow = if (dark) Color.Black else Color(0xFF283854)
                    val strength = if (dark) 0.36f else 0.11f
                    (steps downTo 1).map { i ->
                        val f = i / steps.toFloat()
                        val grow = spread * f
                        val shifted = RoundRect(
                            left = rounded.left - grow * 0.85f,
                            top = rounded.top - grow * 0.55f + spread * 0.38f,
                            right = rounded.right + grow * 0.85f,
                            bottom = rounded.bottom + grow + spread * 0.42f,
                            cornerRadius = CornerRadius(
                                rounded.topLeftCornerRadius.x + grow,
                                rounded.topLeftCornerRadius.y + grow,
                            ),
                        )
                        val alpha = (strength * (1f - f) * (1f - f) / steps * 4.2f).coerceIn(0f, 1f)
                        ShadowRing(Path().apply { addRoundRect(shifted) }, baseShadow.copy(alpha = alpha))
                    }
                } else {
                    emptyList()
                }

                // Inner bevel path for the molded 3D Liquid Glass rim (like image_1674d1.jpg)
                val insetPx = 1.35.dp.toPx()
                val innerBevelPath: Path? = if (outline is Outline.Rounded && w > insetPx * 4 && h > insetPx * 4) {
                    val r = outline.roundRect
                    Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = r.left + insetPx,
                                top = r.top + insetPx,
                                right = r.right - insetPx,
                                bottom = r.bottom - insetPx,
                                cornerRadius = CornerRadius(
                                    (r.topLeftCornerRadius.x - insetPx).coerceAtLeast(0f),
                                    (r.topLeftCornerRadius.y - insetPx).coerceAtLeast(0f),
                                ),
                            ),
                        )
                    }
                } else {
                    null
                }

                val topSpecAlpha = if (dark) 0.10f else (0.58f * style.intensity.coerceAtLeast(0.45f)).coerceAtMost(0.65f)
                val sheenBrush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = topSpecAlpha),
                    0.36f to Color.White.copy(alpha = if (dark) 0.02f else 0.14f),
                    0.75f to Color.Transparent,
                    1.0f to Color.White.copy(alpha = if (dark) 0.03f else 0.24f),
                )

                val innerBevelBrush = Brush.linearGradient(
                    colors = if (dark) {
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.22f),
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.88f),
                            Color.White.copy(alpha = 0.35f),
                            Color(0xFFCBD5E1).copy(alpha = 0.28f),
                        )
                    },
                    start = Offset.Zero,
                    end = Offset(w, h),
                )

                val outerRimBrush = Brush.linearGradient(
                    colors = if (dark) {
                        listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color(0xFF94A3B8).copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.06f),
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.98f),
                            Color(0xFFD8E2F0).copy(alpha = 0.75f),
                            Color(0xFFBCC9DC).copy(alpha = 0.55f),
                        )
                    },
                    start = Offset.Zero,
                    end = Offset(w, h),
                )

                val strokeWidthPx = 1.1.dp.toPx()
                val innerStrokeWidthPx = 1.dp.toPx()
                val blurRadiusPx = style.blurRadius.toPx()

                onDrawBehind {
                    if (shadowRings.isNotEmpty()) {
                        clipPath(clipPath, ClipOp.Difference) {
                            for (i in shadowRings.indices) {
                                val ring = shadowRings[i]
                                drawPath(ring.path, ring.color)
                            }
                        }
                    }

                    val blurs = backdrop != null && backdrop.recorded && style.blur > 0f && supportsBlur() &&
                        drawContext.canvas.nativeCanvas.isHardwareAccelerated

                    val alpha = style.tintAlpha(blurs)
                    val topColor = if (dark) {
                        lerp(tint, Color(0xFF283246), 0.28f).copy(alpha = (alpha + 0.03f).coerceAtMost(0.96f))
                    } else {
                        lerp(tint, Color.White, 0.65f).copy(alpha = (alpha + 0.04f).coerceAtMost(0.97f))
                    }
                    val bottomColor = if (dark) {
                        lerp(tint, Color(0xFF101520), 0.25f).copy(alpha = (alpha - 0.03f).coerceAtLeast(0.74f))
                    } else {
                        lerp(tint, Color(0xFFEAF0F8), 0.35f).copy(alpha = (alpha - 0.02f).coerceAtLeast(0.80f))
                    }

                    clipPath(clipPath) {
                        if (blurs) {
                            blurLayer.renderEffect = BlurEffect(blurRadiusPx, blurRadiusPx, TileMode.Clamp)
                            blurLayer.record(IntSize(w.toInt(), h.toInt())) {
                                translate(backdrop!!.origin.x - position.x, backdrop.origin.y - position.y) {
                                    drawLayer(backdrop.layer)
                                }
                            }
                            drawLayer(blurLayer)
                        }
                        drawRect(
                            Brush.linearGradient(
                                colors = listOf(topColor, tint.copy(alpha = alpha), bottomColor),
                                start = Offset.Zero,
                                end = Offset(w * 0.6f, h),
                            ),
                        )
                        drawRect(sheenBrush)
                        if (innerBevelPath != null) {
                            drawPath(innerBevelPath, innerBevelBrush, style = Stroke(width = innerStrokeWidthPx))
                        }
                    }

                    drawOutline(
                        outline = outline,
                        brush = outerRimBrush,
                        style = Stroke(width = strokeWidthPx),
                    )
                }
            },
        content = content,
    )
}

/**
 * Liquid Glass pill modifier for active sliding indicators, filter pills, and recessed slots
 * (inspired by iPhone Liquid Glass & the reference card UI). Cached in [drawWithCache] so zero
 * objects are allocated during animations or scroll frames.
 */
fun Modifier.liquidGlassPill(
    selected: Boolean,
    dark: Boolean,
    accentColor: Color,
    containerColor: Color,
    cornerRadius: Dp = 50.dp,
): Modifier = this.drawWithCache {
    val rPx = cornerRadius.toPx()
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val roundRect = RoundRect(Rect(0f, 0f, w, h), CornerRadius(rPx, rPx))
    val path = Path().apply { addRoundRect(roundRect) }
    val inset = 1.dp.toPx()
    val innerRect = RoundRect(
        Rect(inset, inset, (w - inset).coerceAtLeast(inset), (h - inset).coerceAtLeast(inset)),
        CornerRadius((rPx - inset).coerceAtLeast(0f), (rPx - inset).coerceAtLeast(0f)),
    )
    val innerPath = Path().apply { addRoundRect(innerRect) }

    val fillBrush = if (selected) {
        if (dark) {
            Brush.linearGradient(
                colors = listOf(
                    lerp(containerColor, accentColor, 0.35f).copy(alpha = 0.92f),
                    containerColor.copy(alpha = 0.84f),
                    lerp(containerColor, Color(0xFF0F172A), 0.25f).copy(alpha = 0.88f),
                ),
                start = Offset(0f, 0f),
                end = Offset(w * 0.5f, h),
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    lerp(containerColor, Color.White, 0.32f).copy(alpha = 0.96f),
                    containerColor.copy(alpha = 0.92f),
                    lerp(containerColor, accentColor, 0.14f).copy(alpha = 0.90f),
                ),
                start = Offset(0f, 0f),
                end = Offset(w * 0.5f, h),
            )
        }
    } else {
        if (dark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF171C26).copy(alpha = 0.62f),
                    Color(0xFF1E2532).copy(alpha = 0.52f),
                ),
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFEAF0F8).copy(alpha = 0.72f),
                    Color.White.copy(alpha = 0.86f),
                ),
            )
        }
    }

    val topSheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (selected) (if (dark) 0.20f else 0.55f) else (if (dark) 0.05f else 0.40f)),
        0.48f to Color.Transparent,
    )

    val rimBrush = if (selected) {
        Brush.linearGradient(
            colors = if (dark) {
                listOf(
                    Color.White.copy(alpha = 0.42f),
                    accentColor.copy(alpha = 0.38f),
                    Color.White.copy(alpha = 0.12f),
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.95f),
                    accentColor.copy(alpha = 0.45f),
                    accentColor.copy(alpha = 0.25f),
                )
            },
            start = Offset.Zero,
            end = Offset(w, h),
        )
    } else {
        Brush.linearGradient(
            colors = if (dark) {
                listOf(
                    Color.White.copy(alpha = 0.14f),
                    Color.White.copy(alpha = 0.05f),
                )
            } else {
                listOf(
                    Color(0xFFCBD5E1).copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.90f),
                )
            },
            start = Offset.Zero,
            end = Offset(w, h),
        )
    }

    val strokePx = 1.dp.toPx()

    onDrawBehind {
        drawPath(path, fillBrush)
        drawPath(innerPath, topSheen)
        drawPath(path, rimBrush, style = Stroke(width = strokePx))
    }
}

private fun Path.addOutline(outline: Outline) {
    when (outline) {
        is Outline.Rectangle -> addRect(outline.rect)
        is Outline.Rounded -> addRoundRect(outline.roundRect)
        is Outline.Generic -> addPath(outline.path)
    }
}
