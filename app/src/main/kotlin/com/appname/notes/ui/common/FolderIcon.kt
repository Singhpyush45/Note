package com.appname.notes.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A two-tone folder drawn in the accent colour: a back sheet with a tab, a paper edge peeking out, and a front
 * flap with a soft gradient and a highlight line — so folders read at a glance next to notes. Decorative only
 * (the label next to it names the folder).
 */
@Composable
fun FolderIcon(modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = MaterialTheme.colorScheme.primary) {
    val back = lerp(tint, Color.Black, 0.18f)
    val frontTop = lerp(tint, Color.White, 0.35f)
    val frontBottom = tint
    val paper = lerp(Color.White, tint, 0.08f)
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val r = w * 0.09f
        // Back sheet with a tab on the left.
        val tab = Path().apply {
            moveTo(w * 0.06f, h * 0.30f)
            lineTo(w * 0.06f, h * 0.22f)
            quadraticTo(w * 0.06f, h * 0.16f, w * 0.12f, h * 0.16f)
            lineTo(w * 0.36f, h * 0.16f)
            quadraticTo(w * 0.40f, h * 0.16f, w * 0.43f, h * 0.20f)
            lineTo(w * 0.48f, h * 0.26f)
            lineTo(w * 0.88f, h * 0.26f)
            quadraticTo(w * 0.94f, h * 0.26f, w * 0.94f, h * 0.32f)
            lineTo(w * 0.94f, h * 0.40f)
            lineTo(w * 0.06f, h * 0.40f)
            close()
        }
        drawPath(tab, back)
        drawRoundRect(back, Offset(w * 0.06f, h * 0.28f), Size(w * 0.88f, h * 0.56f), CornerRadius(r, r))
        // A sheet of paper peeking out.
        drawRoundRect(paper, Offset(w * 0.14f, h * 0.31f), Size(w * 0.72f, h * 0.30f), CornerRadius(r * 0.6f, r * 0.6f))
        // Front flap.
        drawRoundRect(
            Brush.verticalGradient(listOf(frontTop, frontBottom), startY = h * 0.40f, endY = h * 0.86f),
            Offset(w * 0.04f, h * 0.40f),
            Size(w * 0.92f, h * 0.46f),
            CornerRadius(r, r),
        )
        // Highlight along the flap's top edge.
        drawLine(Color.White.copy(alpha = 0.55f), Offset(w * 0.10f, h * 0.44f), Offset(w * 0.90f, h * 0.44f), strokeWidth = (w * 0.035f).coerceAtLeast(1f))
    }
}
