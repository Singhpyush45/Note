package com.appname.notes.feature.onboarding

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appname.notes.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Persistence helper for QuickNote's first-time 30-second feature-introduction tour.
 */
object QuickNoteLaunchPrefs {
    private const val PREFS_NAME = "quicknote_launch_prefs"
    private const val KEY_TOUR_DONE = "quicknote_first_launch_tour_done"

    fun isFirstLaunchTourDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_TOUR_DONE, false)

    fun markFirstLaunchTourDone(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TOUR_DONE, true)
            .apply()
    }

    fun resetFirstLaunchTour(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TOUR_DONE, false)
            .apply()
    }
}

private enum class LaunchStage {
    FIRST_TIME_TOUR,
    QUICK_SPLASH,
    DONE,
}

private val QuickNoteObsidian = Color(0xFF0E0F12)
private val QuickNoteDarkRose = Color(0xFF231115)
private val QuickNoteCoral = Color(0xFFFF5247)
private val QuickNoteCrimson = Color(0xFFDC1930)
private val QuickNoteBlush = Color(0xFFFFB4AE)
private val QuickNoteIvory = Color(0xFFF9F7F5)

/**
 * Root launch experience overlay for QuickNote:
 * - First launch ever: plays the 30-second (5 × 6s) choreographed feature showcase.
 * - Every subsequent launch: plays the 1.35-second 120Hz premium opening animation.
 */
@Composable
fun QuickNoteLaunchOverlay(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val inTestHarness = remember {
        android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true) ||
            (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                android.app.ActivityManager.isRunningInUserTestHarness())
    }
    if (inTestHarness) return

    val context = LocalContext.current
    var stage by remember {
        mutableStateOf(
            if (QuickNoteLaunchPrefs.isFirstLaunchTourDone(context)) {
                LaunchStage.QUICK_SPLASH
            } else {
                LaunchStage.FIRST_TIME_TOUR
            },
        )
    }

    AnimatedVisibility(
        visible = stage != LaunchStage.DONE,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(340, easing = FastOutSlowInEasing)),
        modifier = modifier.fillMaxSize(),
    ) {
        when (stage) {
            LaunchStage.FIRST_TIME_TOUR -> {
                QuickNoteFirstTimeTour(
                    onFinish = {
                        QuickNoteLaunchPrefs.markFirstLaunchTourDone(context)
                        stage = LaunchStage.DONE
                    },
                )
            }
            LaunchStage.QUICK_SPLASH -> {
                QuickNoteOpeningSplash(
                    darkTheme = darkTheme,
                    onFinished = { stage = LaunchStage.DONE },
                )
            }
            LaunchStage.DONE -> Unit
        }
    }
}

/**
 * Premium 1.35-second 120Hz opening animation shown on every normal app launch.
 */
@Composable
private fun QuickNoteOpeningSplash(
    darkTheme: Boolean,
    onFinished: () -> Unit,
) {
    val iconScale = remember { Animatable(0.70f) }
    val iconAlpha = remember { Animatable(0f) }
    val ringSweep = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val exitScale = remember { Animatable(1f) }
    val exitAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch {
            iconAlpha.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        }
        launch {
            iconScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
        launch {
            delay(100)
            ringSweep.animateTo(1f, tween(680, easing = FastOutSlowInEasing))
        }
        launch {
            delay(260)
            textAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        }
        delay(1060)
        launch { exitScale.animateTo(1.06f, tween(280, easing = FastOutSlowInEasing)) }
        exitAlpha.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
        onFinished()
    }

    val bgBrush = if (darkTheme) {
        Brush.radialGradient(colors = listOf(QuickNoteDarkRose, QuickNoteObsidian))
    } else {
        Brush.radialGradient(colors = listOf(Color(0xFFFFF8F7), Color(0xFFF4EAE8)))
    }
    val titleColor = if (darkTheme) QuickNoteIvory else Color(0xFF1A1415)
    val subtitleColor = if (darkTheme) QuickNoteBlush else QuickNoteCrimson

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .graphicsLayer {
                scaleX = exitScale.value
                scaleY = exitScale.value
                alpha = exitAlpha.value
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier.size(136.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Luminous animated coral-red sweep halo
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 2.8.dp.toPx()
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                QuickNoteCoral.copy(alpha = 0.90f),
                                Color.White.copy(alpha = 0.92f),
                                QuickNoteCrimson.copy(alpha = 0.45f),
                            ),
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * ringSweep.value,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round),
                    )
                }
                Image(
                    painter = painterResource(id = R.drawable.quicknote_icon_hero),
                    contentDescription = "QuickNote",
                    modifier = Modifier
                        .size(102.dp)
                        .scale(iconScale.value)
                        .alpha(iconAlpha.value)
                        .clip(RoundedCornerShape(26.dp)),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(textAlpha.value),
            ) {
                Text(
                    text = "QuickNote",
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = titleColor,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "WRITE · READ · ORGANIZE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.0.sp,
                    color = subtitleColor,
                )
            }
        }
    }
}

private data class TourChapter(
    val badge: String,
    val title: String,
    val subtitle: String,
)

private val QUICKNOTE_TOUR_CHAPTERS = listOf(
    TourChapter(
        badge = "WELCOME TO QUICKNOTE",
        title = "Write Fast.\nRead Like a Book.",
        subtitle = "Crafted for 120Hz fluidity — capture handwritten ideas instantly, read PDFs in 3D Book Mode, and stay organized.",
    ),
    TourChapter(
        badge = "HANDWRITING STUDIO",
        title = "Natural Ink,\nZero Distraction",
        subtitle = "Switch smoothly between Pen, Pencil, Brush, Highlighter, Shapes, Lasso, and Text with stylus palm rejection.",
    ),
    TourChapter(
        badge = "3D BOOK MODE",
        title = "Turn Any PDF or Note\nInto a Real Book",
        subtitle = "Flip pages with realistic 3D spine shadows, Paper, Sepia & Night reading modes, and one-tap Book Mode right inside any note.",
    ),
    TourChapter(
        badge = "ALL-IN-ONE WORKSPACE",
        title = "Notes, Calendar &\nTasks Together",
        subtitle = "Keep Notebooks, Folders, Tags, Calendar schedules, and To-Do lists connected with instant search.",
    ),
    TourChapter(
        badge = "PRIVATE & ADAPTIVE",
        title = "Optimized for Every\nPhone & Tablet",
        subtitle = "Protected by Biometric Vault & screen privacy, with responsive layouts built for phones, foldables, and tablets.",
    ),
)

/**
 * 30-Second (5 chapters × 6 seconds) first-time interactive opening animation introducing all QuickNote features.
 */
@Composable
private fun QuickNoteFirstTimeTour(
    onFinish: () -> Unit,
) {
    val chapterCount = QUICKNOTE_TOUR_CHAPTERS.size
    val chapterDurationMs = 6000 // 5 × 6s = 30 seconds total
    var currentChapter by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val chapterProgress = remember { Animatable(0f) }

    LaunchedEffect(currentChapter, paused) {
        if (!paused) {
            val remainingMs = ((1f - chapterProgress.value) * chapterDurationMs).toInt().coerceAtLeast(100)
            chapterProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = remainingMs, easing = LinearEasing),
            )
            if (currentChapter < chapterCount - 1) {
                currentChapter += 1
                chapterProgress.snapTo(0f)
            } else {
                paused = true
            }
        }
    }

    val totalElapsedSec = (currentChapter * 6) + (chapterProgress.value * 6f).toInt().coerceAtMost(6)
    val remainingSec = (30 - totalElapsedSec).coerceIn(0, 30)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(QuickNoteObsidian, QuickNoteDarkRose, Color(0xFF0C0D10)),
                ),
            )
            .pointerInput(currentChapter) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    when {
                        offset.x < w * 0.25f && currentChapter > 0 -> {
                            currentChapter -= 1
                            paused = false
                        }
                        offset.x > w * 0.75f && currentChapter < chapterCount - 1 -> {
                            currentChapter += 1
                            paused = false
                        }
                    }
                }
            },
    ) {
        LaunchedEffect(currentChapter) {
            chapterProgress.snapTo(0f)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .widthIn(max = 680.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top 5-Segment Story Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(chapterCount) { idx ->
                    val fill = when {
                        idx < currentChapter -> 1f
                        idx == currentChapter -> chapterProgress.value
                        else -> 0f
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fill)
                                .clip(CircleShape)
                                .background(QuickNoteCoral),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Top Controls: Brand mini-pill + Countdown + Pause/Play + Skip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.quicknote_icon_hero),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)),
                    )
                    Text(
                        text = "QuickNote",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = QuickNoteIvory,
                    )
                    Text(
                        text = "· 0:${remainingSec.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = QuickNoteBlush,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = { paused = !paused },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (paused) "Play tour" else "Pause tour",
                            tint = QuickNoteIvory,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.10f),
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.18f)),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onFinish),
                    ) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.labelMedium,
                            color = QuickNoteIvory,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            // Center Animated Feature Stage
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = currentChapter,
                    transitionSpec = {
                        val forward = targetState >= initialState
                        (slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { if (forward) it / 4 else -it / 4 } +
                            fadeIn(tween(360))) togetherWith
                            (slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { if (forward) -it / 4 else it / 4 } +
                                fadeOut(tween(240)))
                    },
                    label = "quickNoteTourStage",
                ) { idx ->
                    val chapter = QUICKNOTE_TOUR_CHAPTERS[idx]
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    ) {
                        when (idx) {
                            0 -> TourHeroVisual()
                            1 -> TourHandwritingVisual()
                            2 -> TourBookModeVisual()
                            3 -> TourWorkspaceVisual()
                            else -> TourVaultAdaptiveVisual()
                        }

                        Spacer(Modifier.height(28.dp))

                        Surface(
                            shape = CircleShape,
                            color = QuickNoteCoral.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, QuickNoteCoral.copy(alpha = 0.42f)),
                        ) {
                            Text(
                                text = chapter.badge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.8.sp,
                                color = QuickNoteBlush,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = chapter.title,
                            fontSize = 28.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = QuickNoteIvory,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = chapter.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = QuickNoteIvory.copy(alpha = 0.76f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 420.dp),
                        )
                    }
                }
            }

            // Bottom Navigation / Primary CTA
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(chapterCount) { idx ->
                        val active = idx == currentChapter
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (active) 22.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (active) QuickNoteCoral else Color.White.copy(alpha = 0.28f))
                                .clickable {
                                    currentChapter = idx
                                    paused = false
                                },
                        )
                    }
                }

                Button(
                    onClick = {
                        if (currentChapter < chapterCount - 1) {
                            currentChapter += 1
                            paused = false
                        } else {
                            onFinish()
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QuickNoteCoral,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = if (currentChapter < chapterCount - 1) "Next" else "Start with QuickNote",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TourHeroVisual() {
    val pulse by rememberInfiniteTransition(label = "heroPulse").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heroScale",
    )
    Box(
        modifier = Modifier.size(210.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(QuickNoteCoral.copy(alpha = 0.30f), Color.Transparent),
                ),
                radius = size.minDimension * 0.5f * pulse,
            )
            drawCircle(
                color = QuickNoteCoral.copy(alpha = 0.28f),
                radius = size.minDimension * 0.42f,
                style = Stroke(width = 1.3.dp.toPx()),
            )
        }
        Image(
            painter = painterResource(id = R.drawable.quicknote_icon_hero),
            contentDescription = "QuickNote Icon",
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(34.dp)),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TourHandwritingVisual() {
    val strokeProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        strokeProgress.animateTo(1f, tween(3400, easing = FastOutSlowInEasing))
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF171416),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(126.dp)) {
                for (i in 1..4) {
                    val y = size.height * (i / 5f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                val fullPath = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.68f)
                    cubicTo(
                        size.width * 0.22f, size.height * 0.15f,
                        size.width * 0.36f, size.height * 0.85f,
                        size.width * 0.52f, size.height * 0.44f,
                    )
                    cubicTo(
                        size.width * 0.66f, size.height * 0.12f,
                        size.width * 0.78f, size.height * 0.78f,
                        size.width * 0.92f, size.height * 0.34f,
                    )
                }
                val pm = PathMeasure()
                pm.setPath(fullPath, false)
                val seg = Path()
                pm.getSegment(0f, pm.length * strokeProgress.value, seg, true)
                drawPath(
                    path = seg,
                    brush = Brush.horizontalGradient(listOf(QuickNoteCoral, QuickNoteBlush, QuickNoteIvory)),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("Pen", "Pencil", "Brush", "Highlighter", "Shapes", "Palm Reject").forEach { label ->
                    FeatureMiniChip(Icons.Outlined.Draw, label)
                }
            }
        }
    }
}

@Composable
private fun TourBookModeVisual() {
    val flip by rememberInfiniteTransition(label = "bookFlip").animateFloat(
        initialValue = -16f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "flipAngle",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .width(176.dp)
                .height(212.dp)
                .graphicsLayer {
                    cameraDistance = 16f * density
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationY = -flip
                    shadowElevation = 18.dp.toPx()
                }
                .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, topStart = 4.dp, bottomStart = 4.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFE7D7D2), QuickNoteIvory, Color(0xFFFFFFFF)))),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoStories,
                    contentDescription = null,
                    tint = QuickNoteCrimson,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = "Chapter I",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1F1617),
                )
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (it == 3) 0.65f else 1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1F1617).copy(alpha = 0.14f)),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Paper", "Sepia", "Night").forEach { mode ->
                FeatureMiniChip(Icons.Outlined.AutoStories, mode)
            }
        }
    }
}

@Composable
private fun TourWorkspaceVisual() {
    val cards = listOf(
        Triple(Icons.Outlined.FolderOpen, "Notebooks & Folders", "Nested organization with custom covers"),
        Triple(Icons.Outlined.CalendarMonth, "Integrated Calendar", "Daily notes linked to your schedule"),
        Triple(Icons.Outlined.CheckCircle, "To-Do & Reminders", "Turn handwritten notes into tasks"),
        Triple(Icons.Outlined.Search, "Instant Search", "Find notes, tags, and PDFs in milliseconds"),
    )
    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        cards.forEach { (icon, title, desc) ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.14f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = QuickNoteCoral.copy(alpha = 0.20f),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = QuickNoteBlush, modifier = Modifier.size(19.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = QuickNoteIvory)
                        Text(desc, style = MaterialTheme.typography.labelSmall, color = QuickNoteIvory.copy(alpha = 0.68f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TourVaultAdaptiveVisual() {
    val shieldPulse by rememberInfiniteTransition(label = "vaultPulse").animateFloat(
        initialValue = 0.95f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shieldScale",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = QuickNoteCoral.copy(alpha = 0.18f),
            border = BorderStroke(1.2.dp, QuickNoteCoral.copy(alpha = 0.55f)),
            modifier = Modifier.size(116.dp).scale(shieldPulse),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = QuickNoteBlush,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeatureMiniChip(Icons.Outlined.Speed, "120Hz Smooth")
            FeatureMiniChip(Icons.Outlined.TabletAndroid, "Tablet Ready")
            FeatureMiniChip(Icons.Outlined.Lock, "Biometric Lock")
        }
    }
}

@Composable
private fun FeatureMiniChip(icon: ImageVector, label: String) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.09f),
        border = BorderStroke(0.7.dp, Color.White.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = null, tint = QuickNoteBlush, modifier = Modifier.size(13.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = QuickNoteIvory)
        }
    }
}
