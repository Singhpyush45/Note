package com.appname.notes.feature.ink

import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.core.designsystem.component.AnimatedGlassSegmentedControl
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import com.appname.notes.data.metadata.AppMetadataSqliteStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PalmRejectionMode(val id: String, val label: String) {
    OFF("off", "Off"),
    AUTO_STYLUS("auto", "Auto Stylus"),
    STYLUS_ONLY("stylus_only", "Stylus Only");

    companion object {
        fun fromId(id: String?): PalmRejectionMode =
            entries.firstOrNull { it.id == id } ?: AUTO_STYLUS
    }
}

enum class InkTouchDecision {
    ALLOW_DRAW,
    ALLOW_NAVIGATION_GESTURE,
    REJECT_PALM,
}

/**
 * Step 3 — Production Stylus Palm Rejection Filter (`PalmRejectionFilter`).
 * - In `AUTO_STYLUS` mode, once a stylus is detected, accidental finger/palm contacts are
 *   suppressed for `stylusCooldownMs` (default 650ms) and large-area palm touches are rejected,
 *   while 2-finger pinch/zoom/pan gestures always pass through as `ALLOW_NAVIGATION_GESTURE`.
 * - In `STYLUS_ONLY` mode, single-finger drawing is always ignored so users can rest their hand
 *   freely on the screen while still panning/zooming with 2 fingers.
 */
class PalmRejectionFilter(
    private val stylusCooldownMs: Long = 650L,
    private val palmMajorThresholdPx: Float = 84f,
) {
    private var lastStylusEventMs: Long = -1L

    fun evaluate(
        mode: PalmRejectionMode,
        isStylus: Boolean,
        pointerCount: Int,
        touchMajorPx: Float,
        eventTimeMs: Long,
    ): InkTouchDecision {
        if (isStylus) {
            lastStylusEventMs = eventTimeMs
            return InkTouchDecision.ALLOW_DRAW
        }
        // Always allow 2+ finger pinch-to-zoom and pan gestures
        if (pointerCount >= 2) {
            return InkTouchDecision.ALLOW_NAVIGATION_GESTURE
        }
        return when (mode) {
            PalmRejectionMode.OFF -> InkTouchDecision.ALLOW_DRAW
            PalmRejectionMode.STYLUS_ONLY -> InkTouchDecision.REJECT_PALM
            PalmRejectionMode.AUTO_STYLUS -> {
                val withinStylusCooldown =
                    lastStylusEventMs >= 0L && (eventTimeMs - lastStylusEventMs) <= stylusCooldownMs
                val looksLikePalm = touchMajorPx >= palmMajorThresholdPx
                if (withinStylusCooldown || looksLikePalm) {
                    InkTouchDecision.REJECT_PALM
                } else {
                    InkTouchDecision.ALLOW_DRAW
                }
            }
        }
    }

    fun evaluateMotionEvent(event: MotionEvent, mode: PalmRejectionMode): InkTouchDecision {
        val idx = event.actionIndex.coerceIn(0, (event.pointerCount - 1).coerceAtLeast(0))
        val toolType = event.getToolType(idx)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        val touchMajor = runCatching { event.getTouchMajor(idx) }.getOrDefault(0f)
        return evaluate(
            mode = mode,
            isStylus = isStylus,
            pointerCount = event.pointerCount,
            touchMajorPx = touchMajor,
            eventTimeMs = event.eventTime,
        )
    }

    fun reset() {
        lastStylusEventMs = -1L
    }
}

object PalmRejectionStore {
    private const val PREF_KEY = "ink_palm_rejection_mode"
    private val _mode = MutableStateFlow(PalmRejectionMode.AUTO_STYLUS)
    val mode: StateFlow<PalmRejectionMode> = _mode.asStateFlow()

    @Volatile
    private var initialized = false

    fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val saved = AppMetadataSqliteStore.getInstance(context).getPreference(PREF_KEY)
            _mode.value = PalmRejectionMode.fromId(saved)
            initialized = true
        }
    }

    fun setMode(context: Context, mode: PalmRejectionMode) {
        ensureInit(context)
        AppMetadataSqliteStore.getInstance(context).putPreference(PREF_KEY, mode.id)
        _mode.value = mode
    }
}

@Composable
fun PalmRejectionSettingsCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { PalmRejectionStore.ensureInit(context) }
    val currentMode by PalmRejectionStore.mode.collectAsStateWithLifecycle()
    val isDark = LocalDarkTheme.current
    val surfaceBg = if (isDark) Color(0xFF16181D) else Color(0xFFFAF7F2)
    val borderCol = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = surfaceBg,
        border = BorderStroke(0.85.dp, borderCol),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Stylus Palm Rejection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Auto Stylus ignores accidental palm touches while writing with a stylus and keeps 2-finger zoom enabled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            AnimatedGlassSegmentedControl(
                items = PalmRejectionMode.entries,
                selectedItem = currentMode,
                onSelect = { PalmRejectionStore.setMode(context, it) },
                labelProvider = { it.label },
            )
        }
    }
}
