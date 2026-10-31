package com.appname.notes.navigation

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val LAUNCH_PREFS = "notes_launch_prefs"
private const val KEY_FIRST_OPEN_DONE = "first_open_cinematic_v2_done"

enum class LaunchAnimationMode {
    NONE,
    FIRST_TIME_CINEMATIC,
    EVERY_OPEN_NOTEBOOK,
}

/**
 * Coordinates the two launch animations requested by the owner:
 * 1. [LaunchAnimationMode.FIRST_TIME_CINEMATIC]: Played the very first time the user opens the app
 *    (floating 3D idea cards, spark underline, converging Icon 06 Notebook with concentric ripples).
 * 2. [LaunchAnimationMode.EVERY_OPEN_NOTEBOOK]: Played on every subsequent cold app launch
 *    (live stylus sketching the Icon 06 spiral notebook, glowing shimmer bar, and smooth vertical curtain reveal).
 */
object AppLaunchAnimationState {
    private var playedInProcess = false

    fun resolveLaunchMode(context: Context): LaunchAnimationMode {
        if (isRunningUnderTest()) return LaunchAnimationMode.NONE
        if (playedInProcess) return LaunchAnimationMode.NONE
        playedInProcess = true
        val prefs = context.getSharedPreferences(LAUNCH_PREFS, Context.MODE_PRIVATE)
        val firstDone = prefs.getBoolean(KEY_FIRST_OPEN_DONE, false)
        return if (!firstDone) {
            prefs.edit().putBoolean(KEY_FIRST_OPEN_DONE, true).apply()
            LaunchAnimationMode.FIRST_TIME_CINEMATIC
        } else {
            LaunchAnimationMode.EVERY_OPEN_NOTEBOOK
        }
    }

    /**
     * Detects Robolectric or instrumentation test runners so automated screenshot/UI tests
     * immediately see the settled UI without waiting for the launch overlay.
     */
    private fun isRunningUnderTest(): Boolean {
        if (Build.FINGERPRINT == "robolectric" || Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) {
            return true
        }
        val isRobolectric = runCatching {
            Class.forName("org.robolectric.Robolectric")
            true
        }.getOrDefault(false)
        if (isRobolectric) return true
        val isInstrumentation = runCatching {
            Class.forName("androidx.test.platform.app.InstrumentationRegistry")
            // Check if an instrumentation runner is active on the thread/process
            android.app.ActivityManager.isRunningInTestHarness()
        }.getOrDefault(false)
        return isInstrumentation
    }
}

/**
 * Wraps the main application shell with the dual-mode opening animation:
 * - First-time install/open: Cinematic floating workspace & spark convergence intro.
 * - Every subsequent app open: Stylus-sketched Icon 06 Notebook & smooth vertical curtain reveal.
 */
@Composable
fun AppLaunchContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(AppLaunchAnimationState.resolveLaunchMode(context)) }
    val showOverlay = mode != LaunchAnimationMode.NONE

    val shellScale = remember { Animatable(if (showOverlay) 0.94f else 1f) }
    val shellAlpha = remember { Animatable(if (showOverlay) 0f else 1f) }

    LaunchedEffect(mode) {
        when (mode) {
            LaunchAnimationMode.NONE -> {
                shellScale.snapTo(1f)
                shellAlpha.snapTo(1f)
            }
            LaunchAnimationMode.FIRST_TIME_CINEMATIC -> {
                shellScale.snapTo(0.94f)
                shellAlpha.snapTo(0.15f)
                coroutineScope {
                    launch {
                        delay(2600)
                        shellAlpha.animateTo(1f, tween(durationMillis = 650, easing = LinearOutSlowInEasing))
                    }
                    launch {
                        delay(2550)
                        shellScale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                        )
                    }
                }
            }
            LaunchAnimationMode.EVERY_OPEN_NOTEBOOK -> {
                shellScale.snapTo(0.95f)
                shellAlpha.snapTo(0.25f)
                coroutineScope {
                    launch {
                        delay(750)
                        shellAlpha.animateTo(1f, tween(durationMillis = 480, easing = LinearOutSlowInEasing))
                    }
                    launch {
                        delay(720)
                        shellScale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.82f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                    }
                }
            }
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

        when (mode) {
            LaunchAnimationMode.FIRST_TIME_CINEMATIC -> {
                FirstTimeCinematicIntro(
                    onFinished = { mode = LaunchAnimationMode.NONE },
                )
            }
            LaunchAnimationMode.EVERY_OPEN_NOTEBOOK -> {
                EveryLaunchNotebookReveal(
                    onFinished = { mode = LaunchAnimationMode.NONE },
                )
            }
            LaunchAnimationMode.NONE -> Unit
        }
    }
}

/**
 * FIRST-TIME OPENING ANIMATION (inspired by Reference Video 1 `gemini_generated_video_c12d48ab.mp4`):
 * 1. Floating 3D workspace cards (sticky notes, audio waveform, strategy graph, checklist, timeline) drift in
 *    parallax space around "Every great idea starts as a spark ✦" while a glowing ink curve underlines it.
 * 2. The floating cards converge into the central Icon 06 Spiral Notebook emblem with concentric sonar ripples
 *    and "Capture, connect, and create effortlessly", then smoothly reveal the workspace.
 */
@Composable
private fun FirstTimeCinematicIntro(
    onFinished: () -> Unit,
) {
    val masterProgress = remember { Animatable(0f) }
    val sparkStroke = remember { Animatable(0f) }
    val converge = remember { Animatable(0f) }
    val notebookScale = remember { Animatable(0f) }
    val rippleProgress = remember { Animatable(0f) }
    val exitCurtain = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                masterProgress.animateTo(1f, tween(durationMillis = 3600, easing = LinearEasing))
            }
            launch {
                delay(220)
                sparkStroke.animateTo(1f, tween(durationMillis = 950, easing = FastOutSlowInEasing))
            }
            launch {
                delay(1450)
                converge.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
            }
            launch {
                delay(1650)
                notebookScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                )
            }
            launch {
                delay(1800)
                rippleProgress.animateTo(1f, tween(durationMillis = 1200, easing = LinearOutSlowInEasing))
            }
            launch {
                delay(3050)
                exitCurtain.animateTo(1f, tween(durationMillis = 550, easing = FastOutSlowInEasing))
            }
        }
        onFinished()
    }

    val overlayAlpha = (1f - exitCurtain.value).coerceIn(0f, 1f)
    val phase1Alpha = (1f - (converge.value * 1.45f)).coerceIn(0f, 1f)
    val phase2Alpha = ((converge.value - 0.25f) / 0.55f).coerceIn(0f, 1f) * (1f - exitCurtain.value)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = overlayAlpha
                translationY = -exitCurtain.value * size.height * 0.18f
                val s = 1f + exitCurtain.value * 0.08f
                scaleX = s
                scaleY = s
            }
            .background(Color(0xFF0A0D14))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.5f
            val t = masterProgress.value
            val conv = converge.value

            // Deep ambient studio glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2B82F6).copy(alpha = 0.20f + 0.12f * conv),
                        Color(0xFF6366F1).copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = maxOf(w, h) * 0.62f,
                ),
                radius = maxOf(w, h) * 0.62f,
                center = Offset(cx, cy),
            )

            // Floating spark particles drifting upward
            val particles = listOf(
                Triple(0.18f, 0.28f, 3.2f),
                Triple(0.82f, 0.24f, 4.0f),
                Triple(0.26f, 0.72f, 3.5f),
                Triple(0.76f, 0.68f, 3.0f),
                Triple(0.48f, 0.20f, 2.8f),
                Triple(0.56f, 0.78f, 3.6f),
                Triple(0.14f, 0.52f, 2.6f),
                Triple(0.88f, 0.48f, 3.4f),
            )
            particles.forEachIndexed { idx, (px, py, radDp) ->
                val driftY = (py - t * (0.08f + (idx % 3) * 0.03f))
                val curX = cx + (w * px - cx) * (1f - conv * 0.85f)
                val curY = cy + (h * driftY - cy) * (1f - conv * 0.85f)
                val pAlpha = (0.35f + 0.45f * sin((t * 6f + idx).toDouble()).toFloat()).coerceIn(0.1f, 0.85f) * (1f - exitCurtain.value)
                drawCircle(
                    color = if (idx % 2 == 0) Color(0xFF72CBFF).copy(alpha = pAlpha) else Color(0xFFFFE082).copy(alpha = pAlpha),
                    radius = radDp.dp.toPx(),
                    center = Offset(curX, curY),
                )
            }

            // 6 Floating 3D Workspace Cards around the canvas (converging smoothly toward center in Phase 2)
            val cardAlpha = (1f - conv * 0.92f).coerceIn(0f, 1f)
            if (cardAlpha > 0.02f) {
                val drift = sin(t * PI).toFloat() * 10.dp.toPx()

                // 1. Top-Left: Strategy & Rising Graph Card
                drawFloatingStrategyCard(
                    cx = cx + (w * 0.22f - cx) * (1f - conv * 0.82f),
                    cy = cy + (h * 0.20f - cy) * (1f - conv * 0.82f) - drift,
                    scale = 1f - conv * 0.45f,
                    alpha = cardAlpha,
                )

                // 2. Top-Right: Pink Sticky Note
                drawFloatingStickyNote(
                    cx = cx + (w * 0.78f - cx) * (1f - conv * 0.82f),
                    cy = cy + (h * 0.22f - cy) * (1f - conv * 0.82f) + drift * 0.8f,
                    color = Color(0xFFF48FB1),
                    rotationDeg = 6f * (1f - conv),
                    scale = 1f - conv * 0.45f,
                    alpha = cardAlpha,
                )

                // 3. Mid-Left: Audio Waveform Chip
                drawFloatingWaveformCard(
                    cx = cx + (w * 0.19f - cx) * (1f - conv * 0.82f),
                    cy = cy + (h * 0.62f - cy) * (1f - conv * 0.82f) + drift * 0.6f,
                    phase = t * 10f,
                    scale = 1f - conv * 0.45f,
                    alpha = cardAlpha,
                )

                // 4. Mid-Right: Yellow Idea Sticky Note
                drawFloatingStickyNote(
                    cx = cx + (w * 0.81f - cx) * (1f - conv * 0.82f),
                    cy = cy + (h * 0.60f - cy) * (1f - conv * 0.82f) - drift * 0.7f,
                    color = Color(0xFFFFDF7E),
                    rotationDeg = -7f * (1f - conv),
                    scale = 1f - conv * 0.45f,
                    alpha = cardAlpha,
                )

                // 5. Bottom-Center: Timeline & Checklist Card
                drawFloatingTimelineCard(
                    cx = cx + (w * 0.50f - cx) * (1f - conv * 0.82f),
                    cy = cy + (h * 0.79f - cy) * (1f - conv * 0.82f) + drift * 0.5f,
                    scale = 1f - conv * 0.45f,
                    alpha = cardAlpha,
                )
            }

            // Phase 1 Handwritten Spark Underline under "Every great idea starts as a spark"
            if (phase1Alpha > 0.01f && sparkStroke.value > 0.01f) {
                val arcPath = Path().apply {
                    moveTo(cx - 135.dp.toPx(), cy + 44.dp.toPx())
                    cubicTo(
                        cx - 45.dp.toPx(), cy + 28.dp.toPx(),
                        cx + 45.dp.toPx(), cy + 56.dp.toPx(),
                        cx + 138.dp.toPx(), cy + 34.dp.toPx(),
                    )
                }
                val pm = PathMeasure()
                pm.setPath(arcPath, false)
                val seg = Path()
                val stopD = pm.length * sparkStroke.value
                pm.getSegment(0f, stopD, seg, true)
                drawPath(
                    path = seg,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFF72CBFF).copy(alpha = phase1Alpha),
                            Color(0xFFFFE082).copy(alpha = phase1Alpha),
                        ),
                    ),
                    style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round),
                )
                val tip = pm.getPosition(stopD)
                if (tip != Offset.Unspecified) {
                    drawSparkStar(tip, radius = 9.dp.toPx(), color = Color(0xFFFFE082).copy(alpha = phase1Alpha))
                }
            }

            // Phase 2 Concentric Sonar Ripples + Central Icon 06 Spiral Notebook Emblem
            if (notebookScale.value > 0.01f) {
                val notebookOffsetY = 36.dp.toPx()
                val rip = rippleProgress.value
                for (rIdx in 0..2) {
                    val localR = ((rip - rIdx * 0.18f) / 0.75f).coerceIn(0f, 1f)
                    if (localR > 0f) {
                        val ringRadius = (56.dp.toPx() + localR * 120.dp.toPx())
                        val ringAlpha = (1f - localR) * 0.36f * notebookScale.value
                        drawCircle(
                            color = Color(0xFF6BC3FF).copy(alpha = ringAlpha),
                            radius = ringRadius,
                            center = Offset(cx, cy - notebookOffsetY),
                            style = Stroke(width = 1.6.dp.toPx()),
                        )
                    }
                }

                withTransform({
                    translate(cx, cy - notebookOffsetY)
                    scale(notebookScale.value, notebookScale.value, Offset.Zero)
                }) {
                    drawIcon06NotebookBadge(
                        boxSize = 112.dp.toPx(),
                        lineProgress = 1f,
                        ringProgress = 1f,
                    )
                }
            }
        }

        // Phase 1 Center Headline ("Every great idea starts as a spark ✦")
        if (phase1Alpha > 0.02f) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .graphicsLayer {
                        alpha = phase1Alpha
                        val s = 0.94f + 0.06f * sparkStroke.value
                        scaleX = s
                        scaleY = s
                    },
            ) {
                Text(
                    text = "Every great idea ✦\nstarts as a spark",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 36.sp,
                        letterSpacing = (-0.3).sp,
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Phase 2 Headline under the Icon 06 Notebook ("Capture, connect, and create effortlessly")
        if (phase2Alpha > 0.02f) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(top = 152.dp, start = 32.dp, end = 32.dp)
                    .graphicsLayer {
                        alpha = phase2Alpha
                        translationY = (1f - phase2Alpha) * 20f
                    },
            ) {
                Text(
                    text = "Capture, connect,\nand create effortlessly",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 30.sp,
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "NOTES · HANDWRITING · PLANNER",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.8.sp),
                    color = Color(0xFF8EC5FF),
                )
            }
        }

        // Subtle Skip button in top-right corner
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.12f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFinished,
                ),
        ) {
            Text(
                text = "Skip",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * EVERY-TIME APP OPEN ANIMATION (inspired by Reference Video 2 `gemini_generated_video_9d12614f.mp4`
 * & Reference Video 1 `00:07–00:08`):
 * - Plays automatically on every cold app open.
 * - A sleek 3D stylus pen sketches the Icon 06 Spiral Notebook (2 binder rings + 3 ruled lines),
 *   accompanied by a smooth glowing shimmer bar and concentric pulse, then lifts with a soft
 *   vertical curtain reveal into the Notes home screen.
 */
@Composable
private fun EveryLaunchNotebookReveal(
    onFinished: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val bg = if (isDark) Color(0xFF0C1018) else Color(0xFFF6F5F0)

    val badgeScale = remember { Animatable(0.80f) }
    val sketchProgress = remember { Animatable(0f) }
    val barProgress = remember { Animatable(0f) }
    val curtainLift = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                badgeScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow),
                )
            }
            launch {
                sketchProgress.animateTo(1f, tween(durationMillis = 680, easing = FastOutSlowInEasing))
            }
            launch {
                delay(140)
                barProgress.animateTo(1f, tween(durationMillis = 640, easing = LinearOutSlowInEasing))
            }
            launch {
                delay(860)
                curtainLift.animateTo(1f, tween(durationMillis = 440, easing = FastOutSlowInEasing))
            }
        }
        onFinished()
    }

    val lift = curtainLift.value
    val overlayAlpha = (1f - lift * 0.92f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = overlayAlpha
                translationY = -lift * size.height * 0.88f
            }
            .background(bg)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.46f
            val sketch = sketchProgress.value
            val bar = barProgress.value

            // Ambient radial glow behind the Icon 06 Notebook
            val glowR = 160.dp.toPx() * badgeScale.value
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF3A9BFF).copy(alpha = if (isDark) 0.26f else 0.16f),
                        Color(0xFF72CBFF).copy(alpha = if (isDark) 0.08f else 0.05f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = glowR,
                ),
                radius = glowR,
                center = Offset(cx, cy),
            )

            // Concentric pulse ring around the Notebook
            if (sketch > 0.25f) {
                val pulse = ((sketch - 0.25f) / 0.75f).coerceIn(0f, 1f)
                drawCircle(
                    color = Color(0xFF3A9BFF).copy(alpha = (1f - pulse) * 0.32f),
                    radius = 58.dp.toPx() + pulse * 48.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }

            // Central Icon 06 ("NOTEBOOK") with progressive stylus sketch
            val boxPx = 114.dp.toPx()
            withTransform({
                translate(cx, cy)
                scale(badgeScale.value, badgeScale.value, Offset.Zero)
            }) {
                drawIcon06NotebookBadge(
                    boxSize = boxPx,
                    lineProgress = sketch,
                    ringProgress = (sketch * 1.35f).coerceIn(0f, 1f),
                )

                // Sleek 3D Stylus Pen gliding across the ruled lines while sketching (0f..0.92f)
                if (sketch in 0.04f..0.94f) {
                    val activeLine = when {
                        sketch < 0.34f -> 0
                        sketch < 0.66f -> 1
                        else -> 2
                    }
                    val localT = when (activeLine) {
                        0 -> (sketch / 0.34f).coerceIn(0f, 1f)
                        1 -> ((sketch - 0.34f) / 0.32f).coerceIn(0f, 1f)
                        else -> ((sketch - 0.66f) / 0.28f).coerceIn(0f, 1f)
                    }
                    val startX = -boxPx * 0.10f
                    val endX = if (activeLine == 2) boxPx * 0.11f else boxPx * 0.22f
                    val tipX = startX + (endX - startX) * localT
                    val tipY = (-boxPx * 0.11f) + activeLine * (boxPx * 0.10f)
                    val stylusAlpha = if (sketch > 0.82f) ((0.94f - sketch) / 0.12f).coerceIn(0f, 1f) else 1f
                    drawStylusPen(tip = Offset(tipX, tipY), length = boxPx * 0.62f, alpha = stylusAlpha)
                }
            }

            // Rotating dot spinner + glowing progress pill below the Notebook (like Reference Video 2)
            val spinnerCy = cy + 92.dp.toPx()
            val dotCount = 8
            val spinnerR = 9.dp.toPx()
            for (i in 0 until dotCount) {
                val angle = (i * (360f / dotCount) + bar * 240f) * (PI / 180.0)
                val dx = cx + cos(angle).toFloat() * spinnerR
                val dy = spinnerCy + sin(angle).toFloat() * spinnerR
                val dotAlpha = ((i + 1).toFloat() / dotCount) * (1f - lift)
                drawCircle(
                    color = Color(0xFF3A9BFF).copy(alpha = dotAlpha * 0.85f),
                    radius = 1.8.dp.toPx(),
                    center = Offset(dx, dy),
                )
            }

            // Sleek horizontal shimmer progress bar
            val barW = 118.dp.toPx()
            val barH = 5.dp.toPx()
            val barLeft = cx - barW * 0.5f
            val barTop = spinnerCy + 22.dp.toPx()
            drawRoundRect(
                color = Color(0xFF3A9BFF).copy(alpha = if (isDark) 0.18f else 0.14f),
                topLeft = Offset(barLeft, barTop),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barH, barH),
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFF72CBFF), Color(0xFF2B82F6)),
                    startX = barLeft,
                    endX = barLeft + barW * bar.coerceAtLeast(0.05f),
                ),
                topLeft = Offset(barLeft, barTop),
                size = Size(barW * bar, barH),
                cornerRadius = CornerRadius(barH, barH),
            )

            // Soft pleated vertical curtain fold shading along the bottom edge as it lifts (Reference Video 2 00:04)
            if (lift > 0.01f) {
                val curtainH = 84.dp.toPx()
                val curtainTop = h - curtainH
                val pleatCount = 7
                val pleatW = w / pleatCount
                for (p in 0 until pleatCount) {
                    val px = p * pleatW
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = if (isDark) 0.18f else 0.05f),
                                Color.White.copy(alpha = if (isDark) 0.04f else 0.35f),
                                Color.Black.copy(alpha = if (isDark) 0.22f else 0.07f),
                            ),
                            startX = px,
                            endX = px + pleatW,
                        ),
                        topLeft = Offset(px, curtainTop),
                        size = Size(pleatW, curtainH),
                    )
                }
            }
        }
    }
}

/**
 * Draws the exact Icon 06 ("NOTEBOOK") artwork centered at (0, 0) with size [boxSize]:
 * - Sky-to-royal blue squircle background (#72CBFF -> #2B82F6) with specular rim
 * - 3D white/ice-blue rounded notebook cover with soft left spine bevel
 * - 2 dark navy-blue spiral binder rings on the left spine
 * - 3 rounded horizontal cornflower-blue ruled lines (animated by [lineProgress])
 */
private fun DrawScope.drawIcon06NotebookBadge(
    boxSize: Float,
    lineProgress: Float,
    ringProgress: Float,
) {
    val half = boxSize * 0.5f
    val squircleRad = boxSize * 0.24f

    // 1. Sky-to-royal blue squircle background
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF72CBFF), Color(0xFF3A9BFF), Color(0xFF2575F0)),
            start = Offset(-half, -half),
            end = Offset(half, half),
        ),
        topLeft = Offset(-half, -half),
        size = Size(boxSize, boxSize),
        cornerRadius = CornerRadius(squircleRad, squircleRad),
    )

    // Specular border on squircle
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.12f)),
            start = Offset(-half, -half),
            end = Offset(half, half),
        ),
        topLeft = Offset(-half, -half),
        size = Size(boxSize, boxSize),
        cornerRadius = CornerRadius(squircleRad, squircleRad),
        style = Stroke(width = boxSize * 0.014f),
    )

    // 2. Notebook cover geometry
    val nbLeft = -boxSize * 0.21f
    val nbTop = -boxSize * 0.28f
    val nbWidth = boxSize * 0.46f
    val nbHeight = boxSize * 0.56f
    val nbRad = boxSize * 0.09f

    // Drop shadow under notebook cover
    drawRoundRect(
        color = Color(0xFF0E3E91).copy(alpha = 0.34f),
        topLeft = Offset(nbLeft - boxSize * 0.01f, nbTop + boxSize * 0.03f),
        size = Size(nbWidth + boxSize * 0.02f, nbHeight + boxSize * 0.03f),
        cornerRadius = CornerRadius(nbRad, nbRad),
    )

    // Left 3D ice-blue spine layer behind the white cover
    drawRoundRect(
        color = Color(0xFFA8D6FF).copy(alpha = 0.88f),
        topLeft = Offset(nbLeft - boxSize * 0.025f, nbTop + boxSize * 0.008f),
        size = Size(nbWidth, nbHeight),
        cornerRadius = CornerRadius(nbRad, nbRad),
    )

    // Main 3D white-to-ice-blue notebook cover
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.White, Color(0xFFDCEBFF)),
            start = Offset(nbLeft, nbTop),
            end = Offset(nbLeft + nbWidth, nbTop + nbHeight),
        ),
        topLeft = Offset(nbLeft, nbTop),
        size = Size(nbWidth, nbHeight),
        cornerRadius = CornerRadius(nbRad, nbRad),
    )

    // 3. Three rounded horizontal cornflower-blue ruled lines (animated sequentially by lineProgress)
    val lineH = boxSize * 0.038f
    val lineStartX = nbLeft + nbWidth * 0.26f
    val lineFullW = nbWidth * 0.56f
    val lineShortW = nbWidth * 0.36f
    val lineYs = listOf(
        nbTop + nbHeight * 0.29f,
        nbTop + nbHeight * 0.47f,
        nbTop + nbHeight * 0.65f,
    )
    lineYs.forEachIndexed { idx, ly ->
        val localP = ((lineProgress - idx * 0.26f) / 0.38f).coerceIn(0f, 1f)
        if (localP > 0f) {
            val targetW = if (idx == 2) lineShortW else lineFullW
            drawRoundRect(
                color = Color(0xFF4A98FA),
                topLeft = Offset(lineStartX, ly),
                size = Size(targetW * localP, lineH),
                cornerRadius = CornerRadius(lineH * 0.5f, lineH * 0.5f),
            )
        }
    }

    // 4. Two dark navy-blue spiral binder rings on the left spine
    if (ringProgress > 0.01f) {
        val ringW = boxSize * 0.115f * ringProgress
        val ringH = boxSize * 0.082f
        val ringStroke = boxSize * 0.026f
        val ringYs = listOf(nbTop + nbHeight * 0.22f, nbTop + nbHeight * 0.62f)
        for (ry in ringYs) {
            // Binder hole
            drawCircle(
                color = Color(0xFF1E5BB0),
                radius = boxSize * 0.019f * ringProgress,
                center = Offset(nbLeft + boxSize * 0.036f, ry + ringH * 0.5f),
            )
            // C-ring capsule
            drawRoundRect(
                color = Color(0xFF2162BA),
                topLeft = Offset(nbLeft - boxSize * 0.062f, ry),
                size = Size(ringW, ringH),
                cornerRadius = CornerRadius(ringH * 0.5f, ringH * 0.5f),
                style = Stroke(width = ringStroke),
            )
        }
    }
}

private fun DrawScope.drawStylusPen(tip: Offset, length: Float, alpha: Float) {
    withTransform({
        translate(tip.x, tip.y)
        rotate(-42f, Offset.Zero)
    }) {
        val bodyW = length * 0.13f
        val coneLen = length * 0.22f
        // Dark nib tip
        val nibPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(coneLen, -bodyW * 0.5f)
            lineTo(coneLen, bodyW * 0.5f)
            close()
        }
        drawPath(nibPath, Color(0xFF1E293B).copy(alpha = alpha))
        // Sleek white/silver barrel
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = alpha),
                    Color(0xFFD8E2F0).copy(alpha = alpha),
                ),
            ),
            topLeft = Offset(coneLen * 0.85f, -bodyW * 0.5f),
            size = Size(length * 0.82f, bodyW),
            cornerRadius = CornerRadius(bodyW * 0.5f, bodyW * 0.5f),
        )
    }
}

private fun DrawScope.drawSparkStar(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawFloatingStrategyCard(cx: Float, cy: Float, scale: Float, alpha: Float) {
    val w = 108.dp.toPx() * scale
    val h = 72.dp.toPx() * scale
    val left = cx - w * 0.5f
    val top = cy - h * 0.5f
    drawRoundRect(
        color = Color(0xFF1B2230).copy(alpha = 0.88f * alpha),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(14.dp.toPx() * scale, 14.dp.toPx() * scale),
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.16f * alpha),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(14.dp.toPx() * scale, 14.dp.toPx() * scale),
        style = Stroke(width = 1.dp.toPx()),
    )
    val chart = Path().apply {
        moveTo(left + w * 0.16f, top + h * 0.74f)
        cubicTo(
            left + w * 0.38f, top + h * 0.68f,
            left + w * 0.52f, top + h * 0.32f,
            left + w * 0.84f, top + h * 0.24f,
        )
    }
    drawPath(chart, Color(0xFF72CBFF).copy(alpha = alpha), style = Stroke(width = 2.2.dp.toPx() * scale, cap = StrokeCap.Round))
}

private fun DrawScope.drawFloatingStickyNote(
    cx: Float,
    cy: Float,
    color: Color,
    rotationDeg: Float,
    scale: Float,
    alpha: Float,
) {
    val s = 74.dp.toPx() * scale
    withTransform({
        translate(cx, cy)
        rotate(rotationDeg, Offset.Zero)
    }) {
        drawRoundRect(
            color = color.copy(alpha = 0.92f * alpha),
            topLeft = Offset(-s * 0.5f, -s * 0.5f),
            size = Size(s, s),
            cornerRadius = CornerRadius(10.dp.toPx() * scale, 10.dp.toPx() * scale),
        )
        for (i in 0..2) {
            val ly = -s * 0.20f + i * (s * 0.20f)
            val lw = if (i == 2) s * 0.36f else s * 0.58f
            drawLine(
                color = Color(0xFF1E293B).copy(alpha = 0.45f * alpha),
                start = Offset(-s * 0.28f, ly),
                end = Offset(-s * 0.28f + lw, ly),
                strokeWidth = 2.dp.toPx() * scale,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawFloatingWaveformCard(cx: Float, cy: Float, phase: Float, scale: Float, alpha: Float) {
    val w = 116.dp.toPx() * scale
    val h = 48.dp.toPx() * scale
    val left = cx - w * 0.5f
    val top = cy - h * 0.5f
    drawRoundRect(
        color = Color(0xFF1B2230).copy(alpha = 0.88f * alpha),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(24.dp.toPx() * scale, 24.dp.toPx() * scale),
    )
    val bars = 11
    val step = (w * 0.72f) / bars
    val startX = left + w * 0.16f
    for (i in 0 until bars) {
        val amp = (0.28f + 0.55f * kotlin.math.abs(sin((phase + i * 0.7f).toDouble())).toFloat())
        val bh = h * amp
        val bx = startX + i * step
        drawLine(
            color = Color(0xFF6BC3FF).copy(alpha = alpha),
            start = Offset(bx, cy - bh * 0.5f),
            end = Offset(bx, cy + bh * 0.5f),
            strokeWidth = 2.4.dp.toPx() * scale,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawFloatingTimelineCard(cx: Float, cy: Float, scale: Float, alpha: Float) {
    val w = 148.dp.toPx() * scale
    val h = 44.dp.toPx() * scale
    val left = cx - w * 0.5f
    val top = cy - h * 0.5f
    drawRoundRect(
        color = Color(0xFF1B2230).copy(alpha = 0.86f * alpha),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(14.dp.toPx() * scale, 14.dp.toPx() * scale),
    )
    drawLine(
        color = Color(0xFF818CF8).copy(alpha = 0.75f * alpha),
        start = Offset(left + w * 0.15f, cy),
        end = Offset(left + w * 0.85f, cy),
        strokeWidth = 2.dp.toPx() * scale,
        cap = StrokeCap.Round,
    )
    for (i in 0..2) {
        val nx = left + w * (0.20f + i * 0.30f)
        drawCircle(
            color = if (i == 1) Color(0xFFFFE082).copy(alpha = alpha) else Color(0xFF72CBFF).copy(alpha = alpha),
            radius = 4.dp.toPx() * scale,
            center = Offset(nx, cy),
        )
    }
}
