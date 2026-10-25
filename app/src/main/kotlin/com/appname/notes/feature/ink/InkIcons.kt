package com.appname.notes.feature.ink

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Outline icons for tools the Material set doesn't have. 24 × 24, drawn with 1.8-unit strokes; tinted by Icon. */
internal object InkIcons {

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    /** A tilted block eraser over a baseline. */
    val Eraser: ImageVector by lazy {
        icon("InkEraser") {
            moveTo(4f, 15.5f); lineTo(13.5f, 6f); lineTo(19.5f, 12f); lineTo(10f, 21.5f); close()
            moveTo(8.7f, 10.8f); lineTo(14.7f, 16.8f)
            moveTo(13f, 21.5f); lineTo(21f, 21.5f)
        }
    }

    /** A chisel-tip highlighter with a mark under it. */
    val Highlighter: ImageVector by lazy {
        icon("InkHighlighter") {
            moveTo(13f, 3f); lineTo(20f, 10f); lineTo(12f, 18f); lineTo(5f, 11f); close()
            moveTo(5f, 11f); lineTo(3f, 16f); lineTo(7f, 17f); lineTo(12f, 18f)
            moveTo(2.5f, 21.5f); lineTo(11f, 21.5f)
        }
    }

    /** A dashed loop with a rope tail. */
    val Lasso: ImageVector by lazy {
        icon("InkLasso") {
            val cx = 12f
            val cy = 9.5f
            val rx = 8.5f
            val ry = 5.5f
            // Dashes: 20° drawn, 10° gap.
            var a = 0.0
            while (a < 360.0) {
                val r0 = a * PI / 180
                val r1 = (a + 20) * PI / 180
                moveTo(cx + rx * cos(r0).toFloat(), cy + ry * sin(r0).toFloat())
                for (k in 1..4) {
                    val r = r0 + (r1 - r0) * k / 4
                    lineTo(cx + rx * cos(r).toFloat(), cy + ry * sin(r).toFloat())
                }
                a += 30.0
            }
            moveTo(8f, 14.6f); curveTo(6.5f, 16.5f, 9.5f, 17.5f, 8f, 19.5f); lineTo(7f, 21.5f)
        }
    }

    /** A pencil writing a squiggle: "new handwritten note". */
    val Handwriting: ImageVector by lazy {
        icon("InkHandwriting") {
            moveTo(10f, 13.5f); lineTo(18f, 5.5f); lineTo(20.5f, 8f); lineTo(12.5f, 16f); close()
            moveTo(10f, 13.5f); lineTo(9f, 17f); lineTo(12.5f, 16f)
            moveTo(2.5f, 20.5f); curveTo(4.5f, 16.5f, 6f, 22.5f, 8f, 19.5f); curveTo(9.5f, 17.5f, 11f, 21.5f, 13f, 20f)
        }
    }

    /** A pointer pen sending out a beam that ends in a glowing dot: the laser pointer. */
    val Laser: ImageVector by lazy {
        icon("InkLaser") {
            moveTo(3f, 21f); lineTo(9.5f, 14.5f)
            moveTo(8f, 13f); lineTo(11f, 16f)
            moveTo(12f, 12f); lineTo(14f, 10f)
            // The dot and its rays.
            moveTo(19f, 5f); lineTo(19f, 5f)
            moveTo(15.5f, 5f); curveTo(15.5f, 3f, 17f, 1.5f, 19f, 1.5f); curveTo(21f, 1.5f, 22.5f, 3f, 22.5f, 5f); curveTo(22.5f, 7f, 21f, 8.5f, 19f, 8.5f); curveTo(17f, 8.5f, 15.5f, 7f, 15.5f, 5f); close()
        }
    }
}
