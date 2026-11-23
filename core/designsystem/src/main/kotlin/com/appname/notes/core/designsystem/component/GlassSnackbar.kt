package com.appname.notes.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appname.notes.core.designsystem.theme.LocalDarkTheme

/**
 * Centralized Neutral Liquid Glass Snackbar Host (`GlassSnackbarHost`).
 * - Positioned near the bottom of the usable content area (just above the bottom navigation bar).
 * - Respects Android system navigation insets (`navigationBarsPadding()`).
 * - Supports `avoidFab = true` (adds 84.dp end clearance so the snackbar sits cleanly beside the
 *   56.dp floating action button without being pushed up into the middle of the notebook grid by Scaffold!).
 * - Preserves the Undo action safely with single-click guards so rapid deletions or dismissals never misfire.
 */
@Composable
fun GlassSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    bottomOffset: Dp = 68.dp,
    avoidFab: Boolean = false,
) {
    val startPad = 16.dp
    val endPad = if (avoidFab) 86.dp else 16.dp

    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = bottomOffset, start = startPad, end = endPad),
    ) { data: SnackbarData ->
        val isDark = LocalDarkTheme.current
        val bg = if (isDark) Color(0xFF16181E).copy(alpha = 0.95f) else Color(0xFF1C1E24).copy(alpha = 0.94f)
        val border = if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.12f)
        val primaryText = Color(0xFFF4F5F7)
        val actionBg = Color.White.copy(alpha = 0.16f)

        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            ) + fadeIn(animationSpec = tween(150)),
            exit = slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(130),
            ) + fadeOut(animationSpec = tween(120)),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = if (avoidFab) Alignment.CenterStart else Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = bg,
                    border = BorderStroke(0.8.dp, border),
                    tonalElevation = 0.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = data.visuals.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 10.dp),
                        )
                        val actionLabel = data.visuals.actionLabel
                        if (!actionLabel.isNullOrBlank()) {
                            var actionTriggered = remember(data) { false }
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(actionBg, CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        if (!actionTriggered) {
                                            actionTriggered = true
                                            data.performAction()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = actionLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryText,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
