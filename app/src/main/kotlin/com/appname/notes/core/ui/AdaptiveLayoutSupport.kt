package com.appname.notes.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ScreenWidthBucket {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

/**
 * Step 5 — Responsive Layout Spec for Phones, Foldables, and Tablets.
 * Uses `screenWidthDp` breakpoints (600dp / 840dp) matching Material 3 WindowSizeClass
 * without requiring extra adaptive library dependencies.
 */
@Immutable
data class AdaptiveLayoutSpec(
    val widthBucket: ScreenWidthBucket,
    val gridColumnsRegular: Int,
    val gridColumnsCompactCards: Int,
    val useTwoPaneLayout: Boolean,
    val horizontalPadding: Dp,
    val maxFloatingToolbarWidth: Dp,
) {
    companion object {
        fun fromWidthDp(screenWidthDp: Int): AdaptiveLayoutSpec = when {
            screenWidthDp >= 840 -> AdaptiveLayoutSpec(
                widthBucket = ScreenWidthBucket.EXPANDED,
                gridColumnsRegular = 4,
                gridColumnsCompactCards = 5,
                useTwoPaneLayout = true,
                horizontalPadding = 24.dp,
                maxFloatingToolbarWidth = 560.dp,
            )
            screenWidthDp >= 600 -> AdaptiveLayoutSpec(
                widthBucket = ScreenWidthBucket.MEDIUM,
                gridColumnsRegular = 3,
                gridColumnsCompactCards = 4,
                useTwoPaneLayout = false,
                horizontalPadding = 18.dp,
                maxFloatingToolbarWidth = 500.dp,
            )
            else -> AdaptiveLayoutSpec(
                widthBucket = ScreenWidthBucket.COMPACT,
                gridColumnsRegular = 2,
                gridColumnsCompactCards = 3,
                useTwoPaneLayout = false,
                horizontalPadding = 12.dp,
                maxFloatingToolbarWidth = 420.dp,
            )
        }
    }
}

@Composable
fun rememberAdaptiveLayoutSpec(): AdaptiveLayoutSpec {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return remember(screenWidthDp) {
        AdaptiveLayoutSpec.fromWidthDp(screenWidthDp)
    }
}
