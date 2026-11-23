@file:Suppress("unused")

package com.appname.notes.ui.common

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Section 3A — Dedicated Folder Styles for the Folder Cover Studio.
 */
enum class FolderStyleKind(val id: String, val label: String, val description: String) {
    CLASSIC("classic", "Classic Folder", "Traditional top-left tab with warm cream paper insert"),
    MODERN("modern", "Modern Folder", "Sleek angled pocket with contemporary geometry"),
    MINIMAL("minimal", "Minimal Folder", "Clean, understated silhouette without paper clutter"),
    ROUNDED("rounded", "Rounded Folder", "Soft pill-contoured tab and generous pocket curves"),
    LAYERED("layered", "Layered Folder", "Multi-tiered divider tabs and stacked document sheets"),
    FLAT_2D("flat_2d", "2D Flat Folder", "Crisp flat vector rendering with subtle outline"),
    REALISTIC_3D("realistic_3d", "3D Realistic Folder", "Tactile 3D pocket depth, stitched seam, and shadow");

    companion object {
        fun fromId(id: String?): FolderStyleKind = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/**
 * Section 3B — Dedicated Folder Colour Combinations.
 */
enum class FolderColorPalette(
    val id: String,
    val label: String,
    val frontHex: Long,
    val tabHex: Long,
    val accentHex: Long,
    val textHex: Long = 0xFFFAF8F5,
) {
    AMBER("amber", "Amber", 0xFFD99B43, 0xFFB57824, 0xFFFDE68A, 0xFFFAF8F5),
    EMERALD("emerald", "Emerald", 0xFF1F6E58, 0xFF144D3D, 0xFFA7F3D0, 0xFFF4FAF7),
    NAVY("navy", "Navy", 0xFF26436C, 0xFF192D4B, 0xFFBFDBFE, 0xFFF5F8FC),
    BURGUNDY("burgundy", "Burgundy", 0xFF6E2638, 0xFF4D1825, 0xFFFECDD3, 0xFFFAF4F5),
    LAVENDER("lavender", "Lavender", 0xFF5E4B8B, 0xFF423366, 0xFFDDD6FE, 0xFFFAF8FC),
    SKY_BLUE("sky_blue", "Sky Blue", 0xFF2B6CB0, 0xFF1E4E8C, 0xFFBAE6FD, 0xFFF4F9FD),
    GRAPHITE("graphite", "Graphite", 0xFF3B4252, 0xFF272C37, 0xFFE2E8F0, 0xFFF8FAFC),
    ROSE("rose", "Rose", 0xFF9C425B, 0xFF732D41, 0xFFFBCFE8, 0xFFFAF5F7),
    FOREST_GREEN("forest_green", "Forest Green", 0xFF1E3F32, 0xFF142B22, 0xFFA3C9B3, 0xFFF6F4EE),
    CUSTOM("custom", "Custom Colour", 0xFF8C5630, 0xFF633A1D, 0xFFF3E5AB, 0xFFFBF7F0);

    companion object {
        fun fromId(id: String?): FolderColorPalette = entries.firstOrNull { it.id == id } ?: AMBER
    }
}

/**
 * Custom colour swatch presets when `FolderColorPalette.CUSTOM` is selected.
 */
enum class FolderCustomHexSwatch(
    val id: String,
    val label: String,
    val frontHex: Long,
    val tabHex: Long,
    val accentHex: Long,
) {
    CARAMEL("caramel", "Caramel", 0xFF8C5630, 0xFF633A1D, 0xFFF3E5AB),
    TERRACOTTA("terracotta", "Terracotta", 0xFFA24E36, 0xFF783622, 0xFFFAD6C8),
    DEEP_TEAL("deep_teal", "Deep Teal", 0xFF155E63, 0xFF0E4044, 0xFFB2F5EA),
    OLIVE("olive", "Warm Olive", 0xFF4A553B, 0xFF333B28, 0xFFD9E2C6),
    MIDNIGHT_GOLD("midnight_gold", "Midnight Gold", 0xFF1F2229, 0xFF14161B, 0xFFE6C167),
    ESPRESSO("espresso", "Espresso", 0xFF473123, 0xFF2E1E14, 0xFFE6CCB8);

    companion object {
        fun fromId(id: String?): FolderCustomHexSwatch = entries.firstOrNull { it.id == id } ?: CARAMEL
    }
}

/**
 * Section 3C — Decorative Folder Artwork Options.
 */
enum class FolderArtworkKind(val id: String, val label: String) {
    NONE("none", "None"),
    GEOMETRIC("geometric", "Minimal Geometric"),
    BOTANICAL("botanical", "Botanical Artwork"),
    ACADEMIC("academic", "Academic Illustration"),
    ABSTRACT("abstract", "Abstract Pattern"),
    GRADIENT("gradient", "Subtle Gradient");

    companion object {
        fun fromId(id: String?): FolderArtworkKind = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/**
 * Section 3D — Folder Title Placement Options.
 */
enum class FolderTitlePlacement(val id: String, val label: String) {
    BENEATH("beneath", "Beneath Folder"),
    ON_COVER("on_cover", "On Folder Cover"),
    BOTH("both", "On Cover & Beneath");

    companion object {
        fun fromId(id: String?): FolderTitlePlacement = entries.firstOrNull { it.id == id } ?: BENEATH
    }
}

/**
 * Section 5 — Dedicated FolderCover Data Model (Completely separate from NoteCoverConfig).
 */
data class FolderCoverConfig(
    val style: FolderStyleKind = FolderStyleKind.CLASSIC,
    val palette: FolderColorPalette = FolderColorPalette.AMBER,
    val customSwatch: FolderCustomHexSwatch = FolderCustomHexSwatch.CARAMEL,
    val artwork: FolderArtworkKind = FolderArtworkKind.NONE,
    val titlePlacement: FolderTitlePlacement = FolderTitlePlacement.BENEATH,
    val isFavouriteBadgeVisible: Boolean = true,
) {
    val resolvedFrontHex: Long
        get() = if (palette == FolderColorPalette.CUSTOM) customSwatch.frontHex else palette.frontHex

    val resolvedTabHex: Long
        get() = if (palette == FolderColorPalette.CUSTOM) customSwatch.tabHex else palette.tabHex

    val resolvedAccentHex: Long
        get() = if (palette == FolderColorPalette.CUSTOM) customSwatch.accentHex else palette.accentHex

    fun encode(): String = listOf(
        style.id,
        palette.id,
        customSwatch.id,
        artwork.id,
        titlePlacement.id,
        isFavouriteBadgeVisible.toString(),
    ).joinToString("|")

    companion object {
        fun decode(raw: String?): FolderCoverConfig? {
            if (raw.isNullOrBlank()) return null
            val p = raw.split("|")
            return FolderCoverConfig(
                style = FolderStyleKind.fromId(p.getOrNull(0)),
                palette = FolderColorPalette.fromId(p.getOrNull(1)),
                customSwatch = FolderCustomHexSwatch.fromId(p.getOrNull(2)),
                artwork = FolderArtworkKind.fromId(p.getOrNull(3)),
                titlePlacement = FolderTitlePlacement.fromId(p.getOrNull(4)),
                isFavouriteBadgeVisible = p.getOrNull(5)?.toBooleanStrictOrNull() ?: true,
            )
        }
    }
}

/**
 * Section 5 & 6 — Dedicated FolderCoverStore (Persists folder covers independently of notes & notebooks).
 */
object FolderCoverStore {
    private val _folderCovers = MutableStateFlow<Map<String, FolderCoverConfig>>(emptyMap())
    val folderCovers: StateFlow<Map<String, FolderCoverConfig>> = _folderCovers.asStateFlow()

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
        val map = mutableMapOf<String, FolderCoverConfig>()
        for ((id, encoded) in db.getAllCovers(com.appname.notes.data.metadata.AppMetadataSqliteStore.TYPE_FOLDER)) {
            FolderCoverConfig.decode(encoded)?.let { map[id] = it }
        }
        _folderCovers.value = map
    }

    fun setFolderCover(context: Context, folderId: String, config: FolderCoverConfig) {
        ensureInit(context)
        val db = com.appname.notes.data.metadata.AppMetadataSqliteStore.getInstance(context)
        db.putCover(com.appname.notes.data.metadata.AppMetadataSqliteStore.TYPE_FOLDER, folderId, config.encode())
        _folderCovers.value = _folderCovers.value.toMutableMap().apply { put(folderId, config) }
    }

    fun resetFolderCover(context: Context, folderId: String) {
        ensureInit(context)
        val db = com.appname.notes.data.metadata.AppMetadataSqliteStore.getInstance(context)
        db.deleteCover(com.appname.notes.data.metadata.AppMetadataSqliteStore.TYPE_FOLDER, folderId)
        _folderCovers.value = _folderCovers.value.toMutableMap().apply { remove(folderId) }
    }

    fun resolveForFolder(
        folderId: String,
        map: Map<String, FolderCoverConfig>,
    ): FolderCoverConfig {
        map[folderId]?.let { return it }
        val defaultPalettes = listOf(
            FolderColorPalette.AMBER,
            FolderColorPalette.FOREST_GREEN,
            FolderColorPalette.NAVY,
            FolderColorPalette.BURGUNDY,
            FolderColorPalette.EMERALD,
            FolderColorPalette.GRAPHITE,
        )
        val idx = (folderId.hashCode() and 0x7FFFFFFF) % defaultPalettes.size
        return FolderCoverConfig(
            style = FolderStyleKind.CLASSIC,
            palette = defaultPalettes[idx],
            artwork = FolderArtworkKind.NONE,
            titlePlacement = FolderTitlePlacement.BENEATH,
        )
    }
}

/**
 * Section 4 & 5 — Dedicated Reusable `FolderCoverRenderer` Component.
 * Renders all 7 folder styles, 10 colour palettes, 6 decorative artworks, on-cover title overlay,
 * and seamlessly adapts to the global 2D/3D display mode (`NoteCoverStore.displayMode`).
 */
@Composable
fun FolderCoverRenderer(
    folderTitle: String,
    config: FolderCoverConfig,
    modifier: Modifier = Modifier,
    itemCountLabel: String? = null,
    compact: Boolean = false,
    overrideDisplayMode: NotebookDisplayMode? = null,
) {
    val isDark = LocalDarkTheme.current
    val globalDisplayMode by NoteCoverStore.displayMode.collectAsStateWithLifecycle()
    val effectiveGlobalMode = overrideDisplayMode ?: globalDisplayMode

    // Respect both the global 2D/3D mode AND explicit 2D/3D folder styles
    val is3D = when (config.style) {
        FolderStyleKind.FLAT_2D -> false
        FolderStyleKind.REALISTIC_3D -> true
        else -> effectiveGlobalMode == NotebookDisplayMode.MODE_3D
    }

    val depthProgress by animateFloatAsState(
        targetValue = if (is3D) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "folder2D3DDepth",
    )

    val frontColor by animateColorAsState(Color(config.resolvedFrontHex), tween(200), label = "folderFrontCol")
    val tabColor by animateColorAsState(Color(config.resolvedTabHex), tween(200), label = "folderTabCol")
    val accentColor by animateColorAsState(Color(config.resolvedAccentHex), tween(200), label = "folderAccentCol")
    val onPocketTextColor = Color(config.palette.textHex)

    val showTitleOnPocket = (!compact) && (
        config.titlePlacement == FolderTitlePlacement.ON_COVER ||
            config.titlePlacement == FolderTitlePlacement.BOTH
        )

    BoxWithConstraints(modifier = modifier) {
        val isSmallThumb = maxWidth < 110.dp

        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val tabH = when (config.style) {
                FolderStyleKind.ROUNDED -> h * 0.16f
                FolderStyleKind.MINIMAL, FolderStyleKind.FLAT_2D -> h * 0.12f
                else -> h * 0.14f
            }
            val tabW = when (config.style) {
                FolderStyleKind.MODERN -> w * 0.48f
                FolderStyleKind.ROUNDED -> w * 0.44f
                FolderStyleKind.MINIMAL -> w * 0.34f
                else -> w * 0.40f
            }
            val cornerR = when (config.style) {
                FolderStyleKind.ROUNDED -> (if (compact) 8.dp else 18.dp).toPx()
                FolderStyleKind.MINIMAL, FolderStyleKind.FLAT_2D -> (if (compact) 5.dp else 10.dp).toPx()
                else -> (if (compact) 6.dp else 14.dp).toPx()
            }

            // 1. Optional 3D Ground Drop Shadow under Folder
            if (depthProgress > 0.05f && !compact) {
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.22f * depthProgress),
                    topLeft = Offset(w * 0.03f, tabH + 4.dp.toPx()),
                    size = Size(w * 0.94f, h - tabH - 2.dp.toPx()),
                    cornerRadius = CornerRadius(cornerR, cornerR),
                )
            }

            // 2. Back Folder Board + Top-Left Folder Tab
            val backPath = Path().apply {
                moveTo(0f, tabH)
                quadraticTo(0f, 0f, cornerR * 0.55f, 0f)
                lineTo(tabW - cornerR * 0.45f, 0f)
                quadraticTo(tabW, 0f, tabW + tabH * 0.55f, tabH)
                lineTo(w - cornerR, tabH)
                quadraticTo(w, tabH, w, tabH + cornerR)
                lineTo(w, h - cornerR)
                quadraticTo(w, h, w - cornerR, h)
                lineTo(cornerR, h)
                quadraticTo(0f, h, 0f, h - cornerR)
                close()
            }
            drawPath(backPath, tabColor)

            // Secondary stacked tab for LAYERED style
            if (config.style == FolderStyleKind.LAYERED) {
                val secondTabPath = Path().apply {
                    val x0 = tabW * 0.75f
                    val x1 = w * 0.78f
                    moveTo(x0, tabH)
                    lineTo(x0 + tabH * 0.4f, tabH * 0.28f)
                    lineTo(x1 - tabH * 0.4f, tabH * 0.28f)
                    lineTo(x1, tabH)
                    close()
                }
                drawPath(secondTabPath, accentColor.copy(alpha = 0.55f))
            }

            // 3. Cream Paper Insert Sheets (Shown in 3D mode or Classic/Layered styles; hidden in Minimal/Flat 2D)
            val showPaperInsert = config.style != FolderStyleKind.MINIMAL &&
                config.style != FolderStyleKind.FLAT_2D &&
                (depthProgress > 0.15f || config.style == FolderStyleKind.LAYERED)

            if (showPaperInsert) {
                val paperAlpha = if (config.style == FolderStyleKind.LAYERED) 1f else depthProgress.coerceIn(0.35f, 1f)
                val paperCol = (if (isDark) Color(0xFFE6DFD3) else Color(0xFFFAF7F0)).copy(alpha = paperAlpha)
                if (config.style == FolderStyleKind.LAYERED && !compact) {
                    drawRoundRect(
                        color = paperCol.copy(alpha = 0.72f * paperAlpha),
                        topLeft = Offset(w * 0.09f, tabH + h * 0.01f),
                        size = Size(w * 0.82f, h * 0.36f),
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                    )
                }
                drawRoundRect(
                    color = paperCol,
                    topLeft = Offset(w * 0.06f, tabH + h * 0.045f),
                    size = Size(w * 0.88f, h * 0.38f),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                )
            }

            // 4. Front Folder Pocket
            val frontTop = if (showPaperInsert) tabH + h * 0.105f else tabH + h * 0.06f
            val pocketH = (h - frontTop).coerceAtLeast(8f)

            val pocketBrush = if (config.artwork == FolderArtworkKind.GRADIENT) {
                Brush.linearGradient(
                    colors = listOf(frontColor, tabColor),
                    start = Offset(0f, frontTop),
                    end = Offset(w, h),
                )
            } else if (depthProgress > 0.1f) {
                Brush.verticalGradient(
                    colors = listOf(frontColor, frontColor.copy(alpha = 0.90f)),
                    startY = frontTop,
                    endY = h,
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(frontColor, frontColor),
                    startY = frontTop,
                    endY = h,
                )
            }

            if (config.style == FolderStyleKind.MODERN) {
                val modernPocket = Path().apply {
                    moveTo(0f, frontTop + cornerR * 0.6f)
                    quadraticTo(0f, frontTop, cornerR * 0.7f, frontTop)
                    lineTo(w * 0.56f, frontTop)
                    lineTo(w * 0.64f, frontTop + h * 0.045f)
                    lineTo(w - cornerR * 0.7f, frontTop + h * 0.045f)
                    quadraticTo(w, frontTop + h * 0.045f, w, frontTop + h * 0.045f + cornerR * 0.6f)
                    lineTo(w, h - cornerR)
                    quadraticTo(w, h, w - cornerR, h)
                    lineTo(cornerR, h)
                    quadraticTo(0f, h, 0f, h - cornerR)
                    close()
                }
                drawPath(modernPocket, pocketBrush)
            } else {
                drawRoundRect(
                    brush = pocketBrush,
                    topLeft = Offset(0f, frontTop),
                    size = Size(w, pocketH),
                    cornerRadius = CornerRadius(cornerR, cornerR),
                )
            }

            // 5. Decorative Folder Artwork inside the Front Pocket
            if (!compact) {
                drawFolderPocketArtwork(
                    artwork = config.artwork,
                    w = w,
                    h = h,
                    frontTop = frontTop,
                    accentColor = accentColor,
                    isSmall = isSmallThumb,
                )
            }

            // 6. Pocket Rim Highlight & 3D Stitching / 2D Crisp Border
            if (depthProgress > 0.05f) {
                drawLine(
                    color = Color.White.copy(alpha = 0.28f * depthProgress),
                    start = Offset(cornerR * 0.65f, frontTop + 1.dp.toPx()),
                    end = Offset(
                        if (config.style == FolderStyleKind.MODERN) w * 0.54f else w - cornerR * 0.65f,
                        frontTop + 1.dp.toPx(),
                    ),
                    strokeWidth = 1.dp.toPx(),
                )
                if (config.style == FolderStyleKind.REALISTIC_3D && !compact) {
                    val inset = 5.dp.toPx()
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.30f * depthProgress),
                        topLeft = Offset(inset, frontTop + inset),
                        size = Size((w - inset * 2f).coerceAtLeast(4f), (pocketH - inset * 2f).coerceAtLeast(4f)),
                        cornerRadius = CornerRadius((cornerR - inset).coerceAtLeast(2f), (cornerR - inset).coerceAtLeast(2f)),
                        style = Stroke(width = 0.9.dp.toPx()),
                    )
                }
            } else {
                // 2D Flat Mode subtle border
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.18f),
                    topLeft = Offset(0.5.dp.toPx(), frontTop + 0.5.dp.toPx()),
                    size = Size(w - 1.dp.toPx(), pocketH - 1.dp.toPx()),
                    cornerRadius = CornerRadius(cornerR, cornerR),
                    style = Stroke(width = 0.9.dp.toPx()),
                )
            }
        }

        // 7. Optional Folder Title & Item Count Overlay on the Front Pocket (Section 3D)
        if (showTitleOnPocket) {
            val titleSp = when {
                isSmallThumb && folderTitle.length > 12 -> 10.sp
                isSmallThumb -> 11.5.sp
                folderTitle.length > 18 -> 12.5.sp
                else -> 14.5.sp
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (isSmallThumb) 10.dp else 14.dp,
                        end = if (isSmallThumb) 10.dp else 14.dp,
                        top = if (isSmallThumb) 26.dp else 36.dp,
                        bottom = if (isSmallThumb) 8.dp else 12.dp,
                    ),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = folderTitle.ifBlank { "Untitled Folder" },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = titleSp,
                        lineHeight = (titleSp.value * 1.18f).sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = onPocketTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!itemCountLabel.isNullOrBlank() && !isSmallThumb) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = itemCountLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = onPocketTextColor.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawFolderPocketArtwork(
    artwork: FolderArtworkKind,
    w: Float,
    h: Float,
    frontTop: Float,
    accentColor: Color,
    isSmall: Boolean,
) {
    val pocketH = h - frontTop
    val cx = w * 0.74f
    val cy = frontTop + pocketH * 0.54f
    val baseR = (pocketH * 0.28f).coerceAtMost(w * 0.18f)
    val col = accentColor.copy(alpha = 0.26f)

    when (artwork) {
        FolderArtworkKind.NONE, FolderArtworkKind.GRADIENT -> Unit

        FolderArtworkKind.GEOMETRIC -> {
            drawCircle(col, radius = baseR, center = Offset(cx, cy), style = Stroke(width = 1.1.dp.toPx()))
            drawCircle(col, radius = baseR * 0.56f, center = Offset(cx, cy), style = Stroke(width = 0.9.dp.toPx()))
            drawRect(
                color = col,
                topLeft = Offset(cx - baseR * 0.7f, cy - baseR * 0.7f),
                size = Size(baseR * 1.4f, baseR * 1.4f),
                style = Stroke(width = 0.85.dp.toPx()),
            )
        }

        FolderArtworkKind.BOTANICAL -> {
            for (i in 0 until 6) {
                val angle = (i * 60.0) * PI / 180.0
                val px = cx + (cos(angle) * baseR * 0.58f).toFloat()
                val py = cy + (sin(angle) * baseR * 0.58f).toFloat()
                drawCircle(col, radius = baseR * 0.46f, center = Offset(px, py))
            }
            drawCircle(accentColor.copy(alpha = 0.45f), radius = baseR * 0.20f, center = Offset(cx, cy))
        }

        FolderArtworkKind.ACADEMIC -> {
            // Subtle orbital + constellation nodes
            drawOval(
                color = col,
                topLeft = Offset(cx - baseR * 1.1f, cy - baseR * 0.45f),
                size = Size(baseR * 2.2f, baseR * 0.90f),
                style = Stroke(width = 1.0.dp.toPx()),
            )
            val n1 = Offset(cx - baseR * 0.65f, cy - baseR * 0.45f)
            val n2 = Offset(cx + baseR * 0.65f, cy + baseR * 0.45f)
            drawLine(col, n1, n2, strokeWidth = 1.0.dp.toPx())
            drawCircle(accentColor.copy(alpha = 0.48f), radius = 2.5.dp.toPx(), center = Offset(cx, cy))
            drawCircle(col, radius = 2.0.dp.toPx(), center = n1)
            drawCircle(col, radius = 2.0.dp.toPx(), center = n2)
        }

        FolderArtworkKind.ABSTRACT -> {
            drawCircle(
                color = accentColor.copy(alpha = 0.16f),
                radius = baseR * 1.35f,
                center = Offset(w * 0.78f, frontTop + pocketH * 0.66f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = baseR * 0.90f,
                center = Offset(w * 0.62f, frontTop + pocketH * 0.76f),
            )
        }
    }
}

/**
 * Sections 2, 3, 5 & 6 — Dedicated `FolderCoverStudioDialog`
 * Opened exclusively when selecting `Folder → Three-dot menu → Change cover`.
 * Never opens the Note/Notebook Cover Studio and never modifies notes or notebooks.
 */
@Composable
fun FolderCoverStudioDialog(
    folderId: String,
    folderTitle: String,
    initialConfig: FolderCoverConfig,
    onApply: (FolderCoverConfig) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val sheetBg = if (isDark) Color(0xFF141518) else Color(0xFFFAF7F2)
    val cardBg = if (isDark) Color(0xFF1D1F24) else Color(0xFFF1ECE2)
    val borderCol = if (isDark) Color.White.copy(alpha = 0.13f) else Color(0xFF2C251E).copy(alpha = 0.12f)
    val primaryText = if (isDark) Color(0xFFF6F3EC) else Color(0xFF1C1A17)
    val secondaryText = if (isDark) Color(0xFFA39E93) else Color(0xFF6E665C)
    val accentCol = if (isDark) Color(0xFFD9A668) else Color(0xFF1E3F32)
    val accentContentCol = if (isDark) Color(0xFF141518) else Color(0xFFFAF7F2)

    var selectedStyle by rememberSaveable { mutableStateOf(initialConfig.style) }
    var selectedPalette by rememberSaveable { mutableStateOf(initialConfig.palette) }
    var selectedCustomSwatch by rememberSaveable { mutableStateOf(initialConfig.customSwatch) }
    var selectedArtwork by rememberSaveable { mutableStateOf(initialConfig.artwork) }
    var selectedTitlePlacement by rememberSaveable { mutableStateOf(initialConfig.titlePlacement) }
    var expandedPreview by rememberSaveable { mutableStateOf(value = false) }

    val currentPreviewConfig = FolderCoverConfig(
        style = selectedStyle,
        palette = selectedPalette,
        customSwatch = selectedCustomSwatch,
        artwork = selectedArtwork,
        titlePlacement = selectedTitlePlacement,
    )

    val previewScale by animateFloatAsState(
        targetValue = if (expandedPreview) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "folderStudioPreviewScale",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = sheetBg,
            contentColor = primaryText,
            border = BorderStroke(1.dp, borderCol),
            tonalElevation = 0.dp,
            shadowElevation = 20.dp,
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(0.95f)
                .heightIn(max = 820.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 1. Header: Close (✕) | "Folder Cover Studio" | Preview | Apply (✓)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = cardBg,
                        border = BorderStroke(0.8.dp, borderCol),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close", tint = primaryText, modifier = Modifier.size(19.dp))
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Folder Cover Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = folderTitle.ifBlank { "Folder" },
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (expandedPreview) accentCol.copy(alpha = 0.16f) else cardBg,
                            border = BorderStroke(0.8.dp, if (expandedPreview) accentCol else borderCol),
                            modifier = Modifier
                                .height(38.dp)
                                .clip(CircleShape)
                                .clickable { expandedPreview = !expandedPreview },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Outlined.Visibility, contentDescription = "Preview", tint = primaryText, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (expandedPreview) "Studio" else "Preview",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryText,
                                    maxLines = 1,
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = accentCol,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { onApply(currentPreviewConfig) },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Check, contentDescription = "Apply Folder Cover", tint = accentContentCol, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // 2. Live Folder Cover Preview (Matches exact Home/Folder Grid appearance)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "LIVE FOLDER COVER PREVIEW",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.0.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = secondaryText,
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = previewScale
                                scaleY = previewScale
                            }
                            .width(if (expandedPreview) 210.dp else 172.dp),
                    ) {
                        FolderCoverRenderer(
                            folderTitle = folderTitle,
                            config = currentPreviewConfig,
                            itemCountLabel = "Folder",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.12f),
                        )
                        if (selectedTitlePlacement == FolderTitlePlacement.BENEATH || selectedTitlePlacement == FolderTitlePlacement.BOTH) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = folderTitle.ifBlank { "Untitled Folder" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${selectedStyle.label} · ${selectedPalette.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryText,
                        maxLines = 1,
                    )
                }

                if (!expandedPreview) {
                    // 3A. Folder Styles Carousel (Classic, Modern, Minimal, Rounded, Layered, 2D Flat, 3D Realistic)
                    Text(
                        text = "Folder Style",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FolderStyleKind.entries.forEach { styleItem ->
                            val isSelected = selectedStyle == styleItem
                            val itemConfig = currentPreviewConfig.copy(style = styleItem)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(116.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedStyle = styleItem }
                                    .border(
                                        width = if (isSelected) 2.dp else 0.8.dp,
                                        color = if (isSelected) accentCol else borderCol,
                                        shape = RoundedCornerShape(14.dp),
                                    )
                                    .background(cardBg.copy(alpha = 0.55f))
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            ) {
                                FolderCoverRenderer(
                                    folderTitle = folderTitle,
                                    config = itemConfig,
                                    compact = true,
                                    modifier = Modifier
                                        .width(84.dp)
                                        .aspectRatio(1.12f),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = styleItem.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) primaryText else secondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    // 3B. Folder Colours (Amber, Emerald, Navy, Burgundy, Lavender, Sky Blue, Graphite, Rose, Forest Green, Custom)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cardBg,
                        border = BorderStroke(0.8.dp, borderCol),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "Folder Colour",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryText,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FolderColorPalette.entries.forEach { pal ->
                                    val selected = selectedPalette == pal
                                    val swatchCol = Color(
                                        if (pal == FolderColorPalette.CUSTOM) selectedCustomSwatch.frontHex else pal.frontHex,
                                    )
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) { selectedPalette = pal }
                                            .padding(horizontal = 5.dp, vertical = 3.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(swatchCol, CircleShape)
                                                .border(
                                                    width = if (selected) 2.2.dp else 1.dp,
                                                    color = if (selected) accentCol else borderCol,
                                                    shape = CircleShape,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (selected) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = pal.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) primaryText else secondaryText,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }

                            if (selectedPalette == FolderColorPalette.CUSTOM) {
                                Text(
                                    text = "Custom Colour Swatches",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryText,
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FolderCustomHexSwatch.entries.forEach { cSwatch ->
                                        val isCustomSel = selectedCustomSwatch == cSwatch
                                        FolderStudioChip(
                                            label = cSwatch.label,
                                            selected = isCustomSel,
                                            dotColor = Color(cSwatch.frontHex),
                                            primaryText = primaryText,
                                            secondaryText = secondaryText,
                                            borderCol = borderCol,
                                            accentCol = accentCol,
                                            onClick = { selectedCustomSwatch = cSwatch },
                                        )
                                    }
                                }
                            }

                            // 3C. Folder Artwork
                            Text(
                                text = "Folder Artwork",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryText,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FolderArtworkKind.entries.forEach { art ->
                                    FolderStudioChip(
                                        label = art.label,
                                        selected = selectedArtwork == art,
                                        primaryText = primaryText,
                                        secondaryText = secondaryText,
                                        borderCol = borderCol,
                                        accentCol = accentCol,
                                        onClick = { selectedArtwork = art },
                                    )
                                }
                            }

                            // 3D. Folder Title Display Placement
                            Text(
                                text = "Folder Title Placement",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryText,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FolderTitlePlacement.entries.forEach { tp ->
                                    FolderStudioChip(
                                        label = tp.label,
                                        selected = selectedTitlePlacement == tp,
                                        primaryText = primaryText,
                                        secondaryText = secondaryText,
                                        borderCol = borderCol,
                                        accentCol = accentCol,
                                        onClick = { selectedTitlePlacement = tp },
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = borderCol)

                // Footer Actions: Reset Default & Apply Folder Cover
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onReset) {
                        Icon(Icons.Outlined.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(18.dp), tint = secondaryText)
                        Spacer(Modifier.width(6.dp))
                        Text("Reset Default", color = secondaryText)
                    }
                    Surface(
                        shape = CircleShape,
                        color = accentCol,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onApply(currentPreviewConfig) },
                    ) {
                        Text(
                            text = "Apply Folder Cover",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = accentContentCol,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderStudioChip(
    label: String,
    selected: Boolean,
    primaryText: Color,
    secondaryText: Color,
    borderCol: Color,
    accentCol: Color,
    dotColor: Color? = null,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (selected) accentCol.copy(alpha = 0.16f) else Color.Transparent,
        border = BorderStroke(if (selected) 1.4.dp else 0.8.dp, if (selected) accentCol else borderCol),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            dotColor?.let {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(it, CircleShape)
                        .border(0.6.dp, primaryText.copy(alpha = 0.4f), CircleShape),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) primaryText else secondaryText,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
