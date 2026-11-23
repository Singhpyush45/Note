package com.appname.notes.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale (4dp grid). Use `AppTheme.spacing.md` instead of raw numbers so layouts stay consistent. */
@Immutable
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    /** Minimum size for anything tappable (Android accessibility guidance, SRS 8.2). */
    val minTouchTarget: Dp = 48.dp,
    /** Readable line length cap for text on tablets. */
    val maxContentWidth: Dp = 720.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

/** Spacing for a [UiDensity]: Compact tightens gaps and paddings; touch targets stay 48 dp. */
fun spacingFor(density: UiDensity): Spacing = when (density) {
    UiDensity.COMFORTABLE -> Spacing()
    UiDensity.COMPACT -> Spacing(xxs = 2.dp, xs = 3.dp, sm = 6.dp, md = 9.dp, lg = 12.dp, xl = 18.dp, xxl = 24.dp, xxxl = 36.dp)
}

/**
 * Shapes from the "Corner radius" setting (owner brief §19): [radius] is the large (card) radius; the others keep
 * the same proportions as Material's scale, so small chips stay small.
 */
fun shapesFor(radius: Dp): Shapes {
    val r = radius.value.coerceIn(CornerRadius.MIN.value, CornerRadius.MAX.value)
    return Shapes(
        extraSmall = RoundedCornerShape((r * 0.3f).dp),
        small = RoundedCornerShape((r * 0.5f).dp),
        medium = RoundedCornerShape((r * 0.7f).dp),
        large = RoundedCornerShape(r.dp),
        extraLarge = RoundedCornerShape((r * 1.4f).dp),
    )
}

internal val AppShapes = shapesFor(CornerRadius.DEFAULT)
