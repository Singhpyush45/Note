@file:Suppress("unused")

package com.appname.notes.feature.editor

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AlignHorizontalLeft
import androidx.compose.material.icons.automirrored.outlined.AlignHorizontalRight
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.AlignHorizontalCenter
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

enum class ImageSizePreset(val id: String, val label: String, val widthFraction: Float) {
    SMALL("small", "Small", 0.38f),
    MEDIUM("medium", "Medium", 0.62f),
    LARGE("large", "Large", 0.84f),
    FIT_PAGE("fit", "Fit Page", 1.0f),
    ORIGINAL("orig", "Original", 0.76f);

    companion object {
        fun fromId(id: String?): ImageSizePreset = entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}

enum class ImageCropPreset(val id: String, val label: String, val ratio: Float?) {
    NONE("none", "Uncropped", null),
    LANDSCAPE_16_9("16_9", "16:9", 16f / 9f),
    STANDARD_4_3("4_3", "4:3", 4f / 3f),
    SQUARE_1_1("1_1", "1:1", 1f),
    PORTRAIT_3_4("3_4", "3:4", 3f / 4f),
    FREE("free", "Free Crop", null);

    companion object {
        fun fromId(id: String?): ImageCropPreset = entries.firstOrNull { it.id == id } ?: NONE
    }
}

enum class ImageHorizontalAlignment(val id: String, val label: String) {
    LEFT("left", "Left"),
    CENTRE("centre", "Centre"),
    RIGHT("right", "Right");

    companion object {
        fun fromId(id: String?): ImageHorizontalAlignment = entries.firstOrNull { it.id == id } ?: CENTRE
    }
}

data class NoteImageLayoutConfig(
    val widthFraction: Float = 0.76f,
    val heightScale: Float = 1.0f,
    val freeResize: Boolean = false,
    val rotationDegrees: Int = 0,
    val cropPreset: ImageCropPreset = ImageCropPreset.NONE,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val alignment: ImageHorizontalAlignment = ImageHorizontalAlignment.LEFT,
    val offsetX: Float = 0f,
    val zOrder: Int = 0,
) {
    fun encode(): String = listOf(
        widthFraction.toString(),
        heightScale.toString(),
        freeResize.toString(),
        rotationDegrees.toString(),
        cropPreset.id,
        cropLeft.toString(),
        cropTop.toString(),
        cropRight.toString(),
        cropBottom.toString(),
        alignment.id,
        offsetX.toString(),
        zOrder.toString(),
    ).joinToString("|")

    companion object {
        fun decode(raw: String?): NoteImageLayoutConfig? {
            if (raw.isNullOrBlank()) return null
            val p = raw.split("|")
            return NoteImageLayoutConfig(
                widthFraction = (p.getOrNull(0)?.toFloatOrNull() ?: 0.76f).coerceIn(0.25f, 1.0f),
                heightScale = (p.getOrNull(1)?.toFloatOrNull() ?: 1.0f).coerceIn(0.35f, 2.2f),
                freeResize = p.getOrNull(2)?.toBooleanStrictOrNull() ?: false,
                rotationDegrees = (p.getOrNull(3)?.toIntOrNull() ?: 0) % 360,
                cropPreset = ImageCropPreset.fromId(p.getOrNull(4)),
                cropLeft = (p.getOrNull(5)?.toFloatOrNull() ?: 0f).coerceIn(0f, 0.8f),
                cropTop = (p.getOrNull(6)?.toFloatOrNull() ?: 0f).coerceIn(0f, 0.8f),
                cropRight = (p.getOrNull(7)?.toFloatOrNull() ?: 1f).coerceIn(0.2f, 1f),
                cropBottom = (p.getOrNull(8)?.toFloatOrNull() ?: 1f).coerceIn(0.2f, 1f),
                alignment = ImageHorizontalAlignment.fromId(p.getOrNull(9)),
                offsetX = (p.getOrNull(10)?.toFloatOrNull() ?: 0f).coerceIn(-160f, 160f),
                zOrder = p.getOrNull(11)?.toIntOrNull() ?: 0,
            )
        }
    }
}

object NoteImageLayoutStore {
    private val _layouts = MutableStateFlow<Map<String, NoteImageLayoutConfig>>(emptyMap())
    val layouts: StateFlow<Map<String, NoteImageLayoutConfig>> = _layouts.asStateFlow()

    @Volatile
    private var initialized = false

    fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            loadFromDbLocked(context)
            initialized = true
        }
    }

    fun reloadFromStorage(context: Context) {
        synchronized(this) {
            loadFromDbLocked(context)
            initialized = true
        }
    }

    private fun loadFromDbLocked(context: Context) {
        val db = com.appname.notes.data.metadata.AppMetadataSqliteStore.getInstance(context)
        val map = mutableMapOf<String, NoteImageLayoutConfig>()
        for ((k, encoded) in db.getAllImageLayouts()) {
            NoteImageLayoutConfig.decode(encoded)?.let { map[k] = it }
        }
        _layouts.value = map
    }

    fun updateLayout(context: Context, imageKey: String, config: NoteImageLayoutConfig) {
        ensureInit(context)
        val db = com.appname.notes.data.metadata.AppMetadataSqliteStore.getInstance(context)
        db.putImageLayout(imageKey, config.encode())
        _layouts.value = _layouts.value.toMutableMap().apply { put(imageKey, config) }
    }
}

/**
 * Priority 4 & Priority 5 — Production-Grade Interactive Image Resizing & Editing in Text Notes.
 * - Features dedicated 32.dp touch-target Corner & Edge Resize Handle overlays (`TopLeft`, `TopRight`,
 *   `BottomLeft`, `BottomRight`, `CenterLeft`, `CenterRight`) that consume drag gestures directly
 *   so parent `verticalScroll` or `LazyRow` never steals the resize gesture!
 * - Also supports pinch-to-zoom (`detectTransformGestures`) and 1-tap size presets (`Small`, `Medium`, `Large`, `Fit Page`, `Original`).
 * - Supports portrait and landscape aspect ratios, clamps width within `25%..100%` of editor width, and persists
 *   dimensions across note save/reopen and app restarts.
 */
@Composable
fun InteractiveNoteImageCard(
    imageKey: String,
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    onOpenViewer: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current
    val density = LocalDensity.current
    LaunchedEffect(Unit) { NoteImageLayoutStore.ensureInit(context) }

    val allLayouts by NoteImageLayoutStore.layouts.collectAsStateWithLifecycle()
    val savedConfig = allLayouts[imageKey] ?: NoteImageLayoutConfig()

    var selected by rememberSaveable(imageKey) { mutableStateOf(value = false) }
    var isCropping by rememberSaveable(imageKey) { mutableStateOf(value = false) }
    var liveWidthFraction by remember(imageKey, savedConfig.widthFraction) { mutableFloatStateOf(savedConfig.widthFraction) }
    var liveHeightScale by remember(imageKey, savedConfig.heightScale) { mutableFloatStateOf(savedConfig.heightScale) }
    var liveOffsetX by remember(imageKey, savedConfig.offsetX) { mutableFloatStateOf(savedConfig.offsetX) }

    val accentColor = if (isDark) Color(0xFF8FB99E) else Color(0xFF1E3F32)
    val toolbarBg = if (isDark) Color(0xFF181A20) else Color(0xFFFAF7F2)
    val borderCol = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.12f)
    val primaryText = if (isDark) Color(0xFFF6F3EC) else Color(0xFF1C1A17)
    val secondaryText = if (isDark) Color(0xFFA39E93) else Color(0xFF6E665C)

    val displayBitmap = remember(
        bitmap,
        savedConfig.cropPreset,
        savedConfig.cropLeft,
        savedConfig.cropTop,
        savedConfig.cropRight,
        savedConfig.cropBottom,
    ) {
        runCatching {
            val bw = bitmap.width
            val bh = bitmap.height
            if ((bw <= 4) || (bh <= 4)) return@runCatching bitmap
            val l = (savedConfig.cropLeft * bw).toInt().coerceIn(0, bw - 2)
            val t = (savedConfig.cropTop * bh).toInt().coerceIn(0, bh - 2)
            val r = (savedConfig.cropRight * bw).toInt().coerceIn(l + 2, bw)
            val b = (savedConfig.cropBottom * bh).toInt().coerceIn(t + 2, bh)
            if ((l == 0) && (t == 0) && (r == bw) && (b == bh)) bitmap
            else Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
        }.getOrDefault(bitmap)
    }

    val naturalRatio = remember(displayBitmap, savedConfig.rotationDegrees) {
        val rotated90 = (savedConfig.rotationDegrees % 180) != 0
        val w = if (rotated90) displayBitmap.height.toFloat() else displayBitmap.width.toFloat()
        val h = if (rotated90) displayBitmap.width.toFloat() else displayBitmap.height.toFloat()
        (w / h.coerceAtLeast(1f)).coerceIn(0.35f, 3.0f)
    }

    val effectiveRatio = remember(naturalRatio, savedConfig.cropPreset, savedConfig.freeResize, liveHeightScale) {
        val base = savedConfig.cropPreset.ratio ?: naturalRatio
        if (savedConfig.freeResize) (base / liveHeightScale.coerceIn(0.4f, 2.2f)).coerceIn(0.3f, 3.2f)
        else base
    }

    val boxAlignment = when (savedConfig.alignment) {
        ImageHorizontalAlignment.LEFT -> Alignment.CenterStart
        ImageHorizontalAlignment.CENTRE -> Alignment.Center
        ImageHorizontalAlignment.RIGHT -> Alignment.CenterEnd
    }

    fun persistCurrent() {
        NoteImageLayoutStore.updateLayout(
            context,
            imageKey,
            savedConfig.copy(
                widthFraction = liveWidthFraction.coerceIn(0.25f, 1.0f),
                heightScale = liveHeightScale.coerceIn(0.4f, 2.2f),
                offsetX = liveOffsetX.coerceIn(-120f, 120f),
            ),
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = boxAlignment,
    ) {
        val containerWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(200f) }

        Column(
            horizontalAlignment = when (savedConfig.alignment) {
                ImageHorizontalAlignment.LEFT -> Alignment.Start
                ImageHorizontalAlignment.CENTRE -> Alignment.CenterHorizontally
                ImageHorizontalAlignment.RIGHT -> Alignment.End
            },
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(liveOffsetX.roundToInt(), 0) }
                    .fillMaxWidth(liveWidthFraction.coerceIn(0.25f, 1.0f))
                    .aspectRatio(effectiveRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (selected) 2.2.dp else 0.85.dp,
                        color = if (selected) accentColor else borderCol,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        selected = !selected
                        if (!selected) isCropping = false
                    }
                    .pointerInput(selected, savedConfig.freeResize) {
                        if (!selected) return@pointerInput
                        detectTransformGestures { _, pan, zoom, _ ->
                            if ( kotlin.math.abs(zoom - 1f) > 0.005f) {
                                liveWidthFraction = (liveWidthFraction * zoom).coerceIn(0.25f, 1.0f)
                                persistCurrent()
                            } else if (kotlin.math.abs(pan.x) > 1f) {
                                val deltaFrac = (pan.x / containerWidthPx) * 1.25f
                                liveWidthFraction = (liveWidthFraction + deltaFrac).coerceIn(0.25f, 1.0f)
                                persistCurrent()
                            }
                        }
                    },
            ) {
                Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = if (savedConfig.freeResize || savedConfig.cropPreset != ImageCropPreset.NONE) {
                        ContentScale.Crop
                    } else {
                        ContentScale.Fit
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = savedConfig.rotationDegrees.toFloat()
                        },
                )

                if (selected) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val handleR = 6.dp.toPx()
                        val handleStroke = 1.8.dp.toPx()

                        if (isCropping) {
                            val gridCol = Color.White.copy(alpha = 0.55f)
                            drawLine(gridCol, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.dp.toPx())
                            drawLine(gridCol, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = 1.dp.toPx())
                            drawLine(gridCol, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.dp.toPx())
                            drawLine(gridCol, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = 1.dp.toPx())
                        }

                        val handles = listOf(
                            Offset(handleR + 2f, handleR + 2f),
                            Offset(w / 2f, handleR + 2f),
                            Offset(w - handleR - 2f, handleR + 2f),
                            Offset(handleR + 2f, h / 2f),
                            Offset(w - handleR - 2f, h / 2f),
                            Offset(handleR + 2f, h - handleR - 2f),
                            Offset(w / 2f, h - handleR - 2f),
                            Offset(w - handleR - 2f, h - handleR - 2f),
                        )
                        handles.forEach { pt ->
                            drawCircle(color = Color.White, radius = handleR, center = pt)
                            drawCircle(color = accentColor, radius = handleR, center = pt, style = Stroke(width = handleStroke))
                        }
                    }

                    // Dedicated Corner & Edge Drag Handle Touch Boxes (Prevent parent scroll from stealing drag gestures!)
                    // 1. Bottom-Right Corner Resize Handle
                    ResizeCornerHandleBox(
                        alignment = Alignment.BottomEnd,
                        signX = 1f,
                        signY = 1f,
                        containerWidthPx = containerWidthPx,
                        freeResize = savedConfig.freeResize,
                        onResizeDelta = { dfW, dfH ->
                            liveWidthFraction = (liveWidthFraction + dfW).coerceIn(0.25f, 1.0f)
                            if (savedConfig.freeResize) liveHeightScale = (liveHeightScale + dfH).coerceIn(0.45f, 2.0f)
                        },
                        onResizeEnd = ::persistCurrent,
                    )
                    // 2. Bottom-Left Corner Resize Handle
                    ResizeCornerHandleBox(
                        alignment = Alignment.BottomStart,
                        signX = -1f,
                        signY = 1f,
                        containerWidthPx = containerWidthPx,
                        freeResize = savedConfig.freeResize,
                        onResizeDelta = { dfW, dfH ->
                            liveWidthFraction = (liveWidthFraction + dfW).coerceIn(0.25f, 1.0f)
                            if (savedConfig.freeResize) liveHeightScale = (liveHeightScale + dfH).coerceIn(0.45f, 2.0f)
                        },
                        onResizeEnd = ::persistCurrent,
                    )
                    // 3. Top-Right Corner Resize Handle
                    ResizeCornerHandleBox(
                        alignment = Alignment.TopEnd,
                        signX = 1f,
                        signY = -1f,
                        containerWidthPx = containerWidthPx,
                        freeResize = savedConfig.freeResize,
                        onResizeDelta = { dfW, dfH ->
                            liveWidthFraction = (liveWidthFraction + dfW).coerceIn(0.25f, 1.0f)
                            if (savedConfig.freeResize) liveHeightScale = (liveHeightScale + dfH).coerceIn(0.45f, 2.0f)
                        },
                        onResizeEnd = ::persistCurrent,
                    )
                    // 4. Top-Left Corner Resize Handle
                    ResizeCornerHandleBox(
                        alignment = Alignment.TopStart,
                        signX = -1f,
                        signY = -1f,
                        containerWidthPx = containerWidthPx,
                        freeResize = savedConfig.freeResize,
                        onResizeDelta = { dfW, dfH ->
                            liveWidthFraction = (liveWidthFraction + dfW).coerceIn(0.25f, 1.0f)
                            if (savedConfig.freeResize) liveHeightScale = (liveHeightScale + dfH).coerceIn(0.45f, 2.0f)
                        },
                        onResizeEnd = ::persistCurrent,
                    )
                    // 5. Right-Edge Resize Handle
                    ResizeCornerHandleBox(
                        alignment = Alignment.CenterEnd,
                        signX = 1f,
                        signY = 0f,
                        containerWidthPx = containerWidthPx,
                        freeResize = savedConfig.freeResize,
                        onResizeDelta = { dfW, _ ->
                            liveWidthFraction = (liveWidthFraction + dfW).coerceIn(0.25f, 1.0f)
                        },
                        onResizeEnd = ::persistCurrent,
                    )
                    // 6. Left-Edge Resize Handle
                    ResizeCornerHandleBox(
                        alignment = Alignment.CenterStart,
                        signX = -1f,
                        signY = 0f,
                        containerWidthPx = containerWidthPx,
                        freeResize = savedConfig.freeResize,
                        onResizeDelta = { dfW, _ ->
                            liveWidthFraction = (liveWidthFraction + dfW).coerceIn(0.25f, 1.0f)
                        },
                        onResizeEnd = ::persistCurrent,
                    )

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.62f),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp),
                    ) {
                        Text(
                            text = "${(liveWidthFraction * 100).roundToInt()}%${if (savedConfig.freeResize) " · Free" else ""}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // 2. Floating Interactive Image Editing Toolbar
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = toolbarBg,
                    border = BorderStroke(0.85.dp, borderCol),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ImageSizePreset.entries.forEach { preset ->
                                val isPresetActive = kotlin.math.abs(liveWidthFraction - preset.widthFraction) < 0.04f
                                ImageToolChip(
                                    label = preset.label,
                                    active = isPresetActive,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    accentColor = accentColor,
                                    borderCol = borderCol,
                                ) {
                                    liveWidthFraction = preset.widthFraction
                                    if (preset == ImageSizePreset.ORIGINAL) {
                                        liveHeightScale = 1.0f
                                        liveOffsetX = 0f
                                    }
                                    persistCurrent()
                                }
                            }

                            ImageToolChip(
                                label = if (savedConfig.freeResize) "Free Resize" else "Lock Ratio",
                                active = savedConfig.freeResize,
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                accentColor = accentColor,
                                borderCol = borderCol,
                            ) {
                                NoteImageLayoutStore.updateLayout(
                                    context,
                                    imageKey,
                                    savedConfig.copy(freeResize = !savedConfig.freeResize),
                                )
                            }
                        }

                        if (isCropping) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ImageCropPreset.entries.forEach { cp ->
                                    val isCropSel = savedConfig.cropPreset == cp
                                    ImageToolChip(
                                        label = cp.label,
                                        active = isCropSel,
                                        primaryText = primaryText,
                                        secondaryText = secondaryText,
                                        accentColor = accentColor,
                                        borderCol = borderCol,
                                        onClick = {
                                            val updated = when (cp) {
                                                ImageCropPreset.NONE -> savedConfig.copy(
                                                    cropPreset = cp,
                                                    cropLeft = 0f,
                                                    cropTop = 0f,
                                                    cropRight = 1f,
                                                    cropBottom = 1f,
                                                )
                                                ImageCropPreset.FREE -> savedConfig.copy(
                                                    cropPreset = cp,
                                                    cropLeft = 0.08f,
                                                    cropTop = 0.08f,
                                                    cropRight = 0.92f,
                                                    cropBottom = 0.92f,
                                                )
                                                else -> savedConfig.copy(cropPreset = cp)
                                            }
                                            NoteImageLayoutStore.updateLayout(context, imageKey, updated)
                                        },
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { isCropping = !isCropping },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Crop,
                                    contentDescription = "Crop",
                                    tint = if (isCropping) accentColor else primaryText,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    val nextRot = (savedConfig.rotationDegrees + 270) % 360
                                    NoteImageLayoutStore.updateLayout(context, imageKey, savedConfig.copy(rotationDegrees = nextRot))
                                },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.RotateLeft, contentDescription = "Rotate Left", tint = primaryText, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    val nextRot = (savedConfig.rotationDegrees + 90) % 360
                                    NoteImageLayoutStore.updateLayout(context, imageKey, savedConfig.copy(rotationDegrees = nextRot))
                                },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = "Rotate Right", tint = primaryText, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = {
                                    liveOffsetX = 0f
                                    NoteImageLayoutStore.updateLayout(context, imageKey, savedConfig.copy(alignment = ImageHorizontalAlignment.LEFT, offsetX = 0f))
                                },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.AlignHorizontalLeft,
                                    contentDescription = "Align Left",
                                    tint = if (savedConfig.alignment == ImageHorizontalAlignment.LEFT) accentColor else primaryText,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    liveOffsetX = 0f
                                    NoteImageLayoutStore.updateLayout(context, imageKey, savedConfig.copy(alignment = ImageHorizontalAlignment.CENTRE, offsetX = 0f))
                                },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.AlignHorizontalCenter,
                                    contentDescription = "Align Centre",
                                    tint = if (savedConfig.alignment == ImageHorizontalAlignment.CENTRE) accentColor else primaryText,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    liveOffsetX = 0f
                                    NoteImageLayoutStore.updateLayout(context, imageKey, savedConfig.copy(alignment = ImageHorizontalAlignment.RIGHT, offsetX = 0f))
                                },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.AlignHorizontalRight,
                                    contentDescription = "Align Right",
                                    tint = if (savedConfig.alignment == ImageHorizontalAlignment.RIGHT) accentColor else primaryText,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            onOpenViewer?.let {
                                IconButton(
                                    onClick = it,
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(Icons.Outlined.FitScreen, contentDescription = "Inspect Image", tint = primaryText, modifier = Modifier.size(18.dp))
                                }
                            }

                            onDelete?.let {
                                IconButton(
                                    onClick = it,
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete Image",
                                        tint = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ResizeCornerHandleBox(
    alignment: Alignment,
    signX: Float,
    signY: Float,
    containerWidthPx: Float,
    freeResize: Boolean,
    onResizeDelta: (Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
) {
    Box(
        modifier = Modifier
            .align(alignment)
            .size(32.dp)
            .pointerInput(signX, signY, containerWidthPx, freeResize) {
                detectDragGestures(
                    onDragEnd = onResizeEnd,
                ) { change, dragAmount ->
                    change.consume()
                    val dfW = (dragAmount.x * signX / containerWidthPx) * 1.4f
                    val dfH = if (freeResize && signY != 0f) (dragAmount.y * signY / 180f) else 0f
                    onResizeDelta(dfW, dfH)
                }
            },
    )
}

@Composable
private fun ImageToolChip(
    label: String,
    active: Boolean,
    primaryText: Color,
    secondaryText: Color,
    accentColor: Color,
    borderCol: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (active) accentColor.copy(alpha = 0.16f) else Color.Transparent,
        border = BorderStroke(if (active) 1.3.dp else 0.8.dp, if (active) accentColor else borderCol),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) primaryText else secondaryText,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
