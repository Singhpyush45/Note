package com.appname.notes.navigation

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appname.notes.R
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tracks whether the choreographed app-opening animation has already played in this process session,
 * and allows the user to replay it on demand from Settings › Appearance.
 */
object AppLaunchAnimationState {
    private var playedInProcess = false

    var replayTrigger by mutableStateOf(0)
        private set

    fun shouldPlayOnLaunch(): Boolean {
        if (isRunningUnderTest()) return false
        if (playedInProcess) return false
        playedInProcess = true
        return true
    }

    fun triggerReplay() {
        replayTrigger++
    }

    /**
     * Detects Robolectric or instrumentation test runners so automated screenshot/UI tests
     * immediately see the settled UI without waiting for the launch overlay.
     */
    private fun isRunningUnderTest(): Boolean {
        if (Build.FINGERPRINT == "robolectric" || Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) {
            return true
        }
        return runCatching {
            Class.forName("org.robolectric.Robolectric")
            true
        }.getOrDefault(false)
    }
}

/**
 * Wraps the main application shell with a choreographed first-open reveal animation:
 * - A spring-physics entrance scale/alpha on the workspace shell.
 * - A non-blocking emblem & live ink-stroke signature animation that draws across a glassmorphic badge
 *   and dissolves smoothly into the home screen.
 */
@Composable
fun AppLaunchContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var showOverlay by remember { mutableStateOf(AppLaunchAnimationState.shouldPlayOnLaunch()) }
    val replayCount = AppLaunchAnimationState.replayTrigger

    LaunchedEffect(replayCount) {
        if (replayCount > 0) {
            showOverlay = true
        }
    }

    val shellScale = remember { Animatable(if (showOverlay) 0.94f else 1f) }
    val shellAlpha = remember { Animatable(if (showOverlay) 0f else 1f) }

    LaunchedEffect(showOverlay, replayCount) {
        if (showOverlay) {
            shellScale.snapTo(0.95f)
            shellAlpha.snapTo(0.15f)
            coroutineScope {
                launch {
                    delay(180)
                    shellAlpha.animateTo(1f, tween(durationMillis = 420, easing = LinearOutSlowInEasing))
                }
                launch {
                    delay(160)
                    shellScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                    )
                }
            }
        } else {
            shellScale.snapTo(1f)
            shellAlpha.snapTo(1f)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = shellScale.value
                    scaleY = shellScale.value
                    alpha = shellAlpha.value
                },
        ) {
            content()
        }

        if (showOverlay) {
            LaunchSplashOverlay(
                onFinished = { showOverlay = false },
            )
        }
    }
}

@Composable
private fun LaunchSplashOverlay(
    onFinished: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surfaceBg = if (isDark) Color(0xFF111318) else Color(0xFFF6F7FB)
    val cardBg = if (isDark) Color(0xFF1E222B) else Color(0xFFFFFFFF)

    val cardScale = remember { Animatable(0.72f) }
    val strokeProgress = remember { Animatable(0f) }
    val glowRadius = remember { Animatable(0.3f) }
    val overlayAlpha = remember { Animatable(1f) }
    val subtitleAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                cardScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
            launch {
                glowRadius.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
            }
            launch {
                delay(90)
                strokeProgress.animateTo(1f, tween(durationMillis = 460, easing = FastOutSlowInEasing))
            }
            launch {
                delay(180)
                subtitleAlpha.animateTo(1f, tween(durationMillis = 260, easing = LinearOutSlowInEasing))
            }
        }
        delay(140)
        overlayAlpha.animateTo(0f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(overlayAlpha.value)
            .background(surfaceBg.copy(alpha = 0.94f))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        // Ambient radial glow behind the emblem
        Canvas(modifier = Modifier.size(280.dp)) {
            val r = (size.minDimension / 2f) * glowRadius.value.coerceAtLeast(0.1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = if (isDark) 0.32f else 0.22f),
                        tertiary.copy(alpha = if (isDark) 0.14f else 0.08f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = cardScale.value
                scaleY = cardScale.value
            },
        ) {
            // Floating glassmorphic note & live-ink emblem
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(cardBg),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(104.dp)) {
                    val w = size.width
                    val h = size.height
                    // Subtle card border
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(primary.copy(alpha = 0.55f), tertiary.copy(alpha = 0.25f)),
                        ),
                        cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )

                    // Ruled note lines inside the emblem
                    val linePaintColor = primary.copy(alpha = 0.14f)
                    for (i in 1..3) {
                        val y = h * (0.28f + i * 0.14f)
                        drawLine(
                            color = linePaintColor,
                            start = Offset(w * 0.22f, y),
                            end = Offset(w * 0.78f, y),
                            strokeWidth = 1.5.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }

                    // Animated fluid ink signature wave + pen nib
                    val fullPath = Path().apply {
                        moveTo(w * 0.22f, h * 0.64f)
                        cubicTo(
                            w * 0.36f, h * 0.30f,
                            w * 0.50f, h * 0.76f,
                            w * 0.66f, h * 0.42f,
                        )
                        quadraticTo(
                            w * 0.74f, h * 0.28f,
                            w * 0.80f, h * 0.34f,
                        )
                    }
                    val measure = PathMeasure()
                    measure.setPath(fullPath, false)
                    val animatedPath = Path()
                    val stopDistance = measure.length * strokeProgress.value
                    measure.getSegment(0f, stopDistance, animatedPath, true)

                    drawPath(
                        path = animatedPath,
                        brush = Brush.linearGradient(
                            colors = listOf(primary, tertiary),
                            start = Offset(w * 0.2f, h * 0.6f),
                            end = Offset(w * 0.8f, h * 0.3f),
                        ),
                        style = Stroke(
                            width = 4.2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )

                    // Glowing pen tip at the leading edge of the stroke
                    if (strokeProgress.value > 0.02f) {
                        val tipPos = measure.getPosition(stopDistance)
                        if (tipPos != Offset.Unspecified) {
                            drawCircle(
                                color = primary.copy(alpha = 0.30f),
                                radius = 8.dp.toPx(),
                                center = tipPos,
                            )
                            drawCircle(
                                color = primary,
                                radius = 3.5.dp.toPx(),
                                center = tipPos,
                            )
                        }
                    }

                    // Small checkmark badge in bottom-right corner
                    if (strokeProgress.value > 0.6f) {
                        val badgeAlpha = ((strokeProgress.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
                        drawRoundRect(
                            color = primary.copy(alpha = 0.16f * badgeAlpha),
                            topLeft = Offset(w * 0.62f, h * 0.64f),
                            size = Size(w * 0.20f, h * 0.20f),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alpha(subtitleAlpha.value),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Notes · Handwriting · Planner",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(subtitleAlpha.value * 0.85f),
            )
        }
    }
}
