package com.appname.notes.core.performance

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 120Hz Motion & Display Refresh Rate Engine (`HighRefreshRateHelper`).
 * - Requests the highest native-resolution refresh rate (90Hz / 120Hz / 144Hz) on the Activity window.
 * - Provides crisp, zero-float 120Hz spring specs that settle in ~130ms instead of sluggish 320ms springs.
 */
object HighRefreshRateHelper {

    fun enableMaxRefreshRate(activity: Activity) {
        runCatching {
            val window = activity.window ?: return
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay
            } ?: return

            val currentMode = display.mode
            val bestMode = display.supportedModes
                .filter {
                    it.physicalWidth == currentMode.physicalWidth &&
                        it.physicalHeight == currentMode.physicalHeight
                }
                .maxByOrNull { it.refreshRate }
                ?: return

            if (bestMode.refreshRate > currentMode.refreshRate || bestMode.refreshRate >= 90f) {
                val params = window.attributes
                params.preferredDisplayModeId = bestMode.modeId
                params.preferredRefreshRate = bestMode.refreshRate
                window.attributes = params
            }
        }
    }
}

object MotionSpecs120Hz {
    /** Snappy, zero-overshoot slide for bottom navigation & segmented controls (~135ms settle at 120Hz). */
    fun <T> snappySlideSpring() = spring<T>(
        dampingRatio = 0.88f,
        stiffness = 1050f,
    )

    /** Instant tactile press/selection spring (~95ms settle). */
    fun <T> instantTapSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1400f,
    )

    /** Fast colour/fade transition matching 120Hz frame cadence. */
    fun <T> fastColorTween() = tween<T>(durationMillis = 110)
}
