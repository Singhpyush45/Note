@file:Suppress("unused", "ClarifyingParentheses")

package com.appname.notes.ui.common

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.core.designsystem.component.AnimatedGlassSegmentedControl
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import com.appname.notes.core.domain.model.NoteType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cover Categories for the redesigned Notebook & Note Cover Studio.
 * Includes All, Minimal, Two-Tone, Nature, Academic, Creative, and Custom.
 */
enum class CoverCategory(val label: String) {
    ALL("All"),
    MINIMAL("Minimal"),
    TWO_TONE("Two-Tone"),
    NATURE("Nature"),
    ACADEMIC("Academic"),
    SCIENTIFIC("Scientific"),
    PROFESSIONAL("Professional"),
    CLASSIC("Classic"),
    MODERN("Modern"),
    ARTISTIC("Artistic"),
    CREATIVE("Creative"),
    CUSTOM("Custom"),
}

/**
 * Requirement 4 — Global 2D / 3D Notebook Cover Display Mode.
 * Stored globally in `NoteCoverStore` so switching between 2D Flat Covers and 3D Realistic Covers
 * updates all notebooks across the entire application immediately with a smooth transition.
 */
enum class NotebookDisplayMode(val id: String, val label: String, val shortLabel: String, val description: String) {
    MODE_2D(
        id = "2d",
        label = "2D Flat Covers",
        shortLabel = "2D Flat",
        description = "Clean, minimal flat notebook design with subtle borders and lightweight rendering",
    ),
    MODE_3D(
        id = "3d",
        label = "3D Realistic Covers",
        shortLabel = "3D Realistic",
        description = "Physical hardcover appearance with visible spine, page-block edges, and natural depth",
    );

    companion object {
        fun fromId(id: String?): NotebookDisplayMode = entries.firstOrNull { it.id == id } ?: MODE_3D
    }
}

typealias CoverCollection = CoverCategory

/**
 * Customization Studio Tabs (Section 6.5):
 * Colour, Pattern, Illustration, Layout, Typography.
 */
enum class CoverStudioTab(val label: String) {
    COLOUR("Colour"),
    PATTERN("Pattern"),
    ILLUSTRATION("Illustration"),
    LAYOUT("Layout"),
    TYPOGRAPHY("Typography"),
}

/**
 * Pattern options for Notebook Cover customization.
 */
enum class CoverPatternKind(val id: String, val label: String) {
    DEFAULT("default", "Default"),
    SOLID("solid", "Solid"),
    GRADIENT("gradient", "Gradient"),
    GRID("grid", "Grid"),
    LINES("lines", "Lines"),
    DOTS("dots", "Dots"),
    GEOMETRIC("geometric", "Geometric"),
    ABSTRACT("abstract", "Abstract"),
    TEXTURE("texture", "Texture");

    companion object {
        fun fromId(id: String?): CoverPatternKind = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Subject & Botanical Illustration options for Notebook Cover customization.
 */
enum class CoverIllustrationKind(val id: String, val label: String) {
    DEFAULT("default", "Default"),
    NONE("none", "None"),
    FLOWERS("flowers", "Flowers"),
    PLANTS("plants", "Plants"),
    LEAVES("leaves", "Leaves"),
    MOUNTAIN("mountain", "Mountain"),
    OCEAN("ocean", "Ocean"),
    SAKURA("sakura", "Sakura"),
    PHYSICS("physics", "Physics"),
    CHEMISTRY("chemistry", "Chemistry"),
    BIOLOGY("biology", "Biology"),
    MATHEMATICS("mathematics", "Mathematics"),
    ELECTRONICS("electronics", "Electronics"),
    COMPUTER_SCIENCE("computer_science", "Computer Science"),
    ASTRONOMY("astronomy", "Astronomy"),
    STATISTICS("statistics", "Statistics"),
    ARCHITECTURE("architecture", "Architecture"),
    SKETCH("sketch", "Sketch & Art"),
    MUSIC("music", "Music"),
    COMPASS("compass", "Travel Compass"),
    CAMERA("camera", "Photography"),
    QUILL("quill", "Quill & Journal");

    companion object {
        fun fromId(id: String?): CoverIllustrationKind = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Spine style & position options.
 */
enum class CoverSpineStyle(val id: String, val label: String, val widthFactor: Float) {
    CLASSIC("classic", "Classic Hardcover", 1.0f),
    RIDGED("ridged", "Ridged Binding", 1.15f),
    STITCHED("stitched", "Stitched Journal", 1.05f),
    MINIMAL("minimal", "Slim Seam", 0.65f),
    NONE("none", "Flat Cover", 0.0f);

    companion object {
        fun fromId(id: String?): CoverSpineStyle = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/**
 * Border style options.
 */
enum class CoverBorderStyle(val id: String, val label: String) {
    SUBTLE("subtle", "Subtle Edge"),
    EDITORIAL_FRAME("frame", "Editorial Frame"),
    DOUBLE_LINE("double", "Double Foil"),
    CORNER_FOLIO("corners", "Corner Folio"),
    NONE("none", "Borderless");

    companion object {
        fun fromId(id: String?): CoverBorderStyle = entries.firstOrNull { it.id == id } ?: SUBTLE
    }
}

/**
 * Title vertical placement on the cover.
 */
enum class CoverTitlePosition(val id: String, val label: String) {
    UPPER("upper", "Upper Third"),
    CENTRE("centre", "Centred"),
    LOWER("lower", "Lower Third");

    companion object {
        fun fromId(id: String?): CoverTitlePosition = entries.firstOrNull { it.id == id } ?: UPPER
    }
}

/**
 * Illustration placement on the cover.
 */
enum class CoverIllustrationPosition(val id: String, val label: String) {
    BOTTOM_RIGHT("bottom_right", "Lower Right"),
    CENTRE("centre", "Centre Emblem"),
    UPPER_RIGHT("upper_right", "Upper Right"),
    WATERMARK("watermark", "Full Watermark");

    companion object {
        fun fromId(id: String?): CoverIllustrationPosition = entries.firstOrNull { it.id == id } ?: BOTTOM_RIGHT
    }
}

/**
 * Portrait notebook aspect ratio presets.
 */
enum class CoverProportionPreset(val id: String, val label: String, val ratio: Float) {
    CLASSIC_NOTEBOOK("classic", "Classic (0.71)", 0.71f),
    TALL_JOURNAL("tall", "Tall Moleskine (0.66)", 0.66f),
    ACADEMY_FOLIO("folio", "Academy (0.74)", 0.74f);

    companion object {
        fun fromId(id: String?): CoverProportionPreset = entries.firstOrNull { it.id == id } ?: CLASSIC_NOTEBOOK
    }
}

/**
 * Font family options for cover typography.
 */
enum class CoverFontStyle(val id: String, val label: String) {
    DEFAULT("default", "Design Default"),
    SANS("sans", "Modern Sans"),
    SERIF("serif", "Editorial Serif"),
    MONO("mono", "Academic Mono"),
    CURSIVE("cursive", "Journal Script");

    companion object {
        fun fromId(id: String?): CoverFontStyle = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Font weight options for cover typography.
 */
enum class CoverFontWeightOption(val id: String, val label: String, val weight: FontWeight) {
    MEDIUM("medium", "Medium", FontWeight.Medium),
    SEMIBOLD("semibold", "SemiBold", FontWeight.SemiBold),
    BOLD("bold", "Bold", FontWeight.Bold),
    EXTRABOLD("extrabold", "Editorial Heavy", FontWeight.ExtraBold);

    companion object {
        fun fromId(id: String?): CoverFontWeightOption = entries.firstOrNull { it.id == id } ?: BOLD
    }
}

/**
 * Horizontal alignment of the cover title and header.
 */
enum class CoverTextAlignOption(val id: String, val label: String) {
    DEFAULT("default", "Auto"),
    LEFT("left", "Left"),
    CENTRE("centre", "Centre"),
    RIGHT("right", "Right");

    companion object {
        fun fromId(id: String?): CoverTextAlignOption = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Title size scale options.
 */
enum class CoverTitleScaleOption(val id: String, val label: String, val scale: Float) {
    COMPACT("compact", "Compact", 0.88f),
    BALANCED("balanced", "Balanced", 1.0f),
    LARGE("large", "Large", 1.15f);

    companion object {
        fun fromId(id: String?): CoverTitleScaleOption = entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

/**
 * Comprehensive Collection of Professionally Designed Notebook & Note Covers (Sections 4 & 5).
 * Covers Minimal, Two-Tone, Nature, Academic, and Creative families, with dedicated designs for
 * Typed Notes and Handwritten Notebooks.
 */
enum class NoteCoverDesign(
    val id: String,
    val label: String,
    val category: CoverCategory,
    val defaultBgLight: Long,
    val defaultBgDark: Long,
    val defaultFgLight: Long,
    val defaultFgDark: Long,
    val accentHex: Long,
    val categoryBadge: String,
    val defaultPattern: CoverPatternKind,
    val defaultIllustration: CoverIllustrationKind,
    val preferSerif: Boolean = false,
    val preferCenterAlign: Boolean = false,
    val twoToneBand: Boolean = false,
    val bookplateStyle: Boolean = false,
    val preferredNoteType: NoteType? = null,
) {
    // =========================================================================
    // A. MINIMAL COLLECTION (Pure White, Soft Grey, Graphite, Minimal Beige, Modern Black, Minimal Two-Tone + Typed/Handwritten Classics)
    // =========================================================================
    EDITORIAL_WRITER(
        id = "editorial_writer",
        label = "Pure White",
        category = CoverCategory.MINIMAL,
        defaultBgLight = 0xFFFCFBF9,
        defaultBgDark = 0xFF1E2026,
        defaultFgLight = 0xFF18181B,
        defaultFgDark = 0xFFF5F3EF,
        accentHex = 0xFF8C7A6B,
        categoryBadge = "EDITORIAL",
        defaultPattern = CoverPatternKind.SOLID,
        defaultIllustration = CoverIllustrationKind.NONE,
        preferSerif = true,
        preferCenterAlign = true,
        preferredNoteType = NoteType.TEXT,
    ),
    PURE_LINEN(
        id = "pure_linen",
        label = "Soft Grey",
        category = CoverCategory.MINIMAL,
        defaultBgLight = 0xFFEAE8E3,
        defaultBgDark = 0xFF25272E,
        defaultFgLight = 0xFF1F2124,
        defaultFgDark = 0xFFF3F1ED,
        accentHex = 0xFF6E6A63,
        categoryBadge = "LINEN EDITION",
        defaultPattern = CoverPatternKind.TEXTURE,
        defaultIllustration = CoverIllustrationKind.NONE,
        preferSerif = false,
        preferredNoteType = NoteType.TEXT,
    ),
    GRAPHITE_FOLD(
        id = "graphite_fold",
        label = "Graphite",
        category = CoverCategory.MINIMAL,
        defaultBgLight = 0xFF26282E,
        defaultBgDark = 0xFF1C1E24,
        defaultFgLight = 0xFFF7F5F0,
        defaultFgDark = 0xFFF7F5F0,
        accentHex = 0xFFD6D1C7,
        categoryBadge = "HARDCOVER",
        defaultPattern = CoverPatternKind.TEXTURE,
        defaultIllustration = CoverIllustrationKind.NONE,
        twoToneBand = true,
        preferredNoteType = NoteType.INK,
    ),
    MINIMAL_BEIGE(
        id = "minimal_beige",
        label = "Minimal Beige",
        category = CoverCategory.MINIMAL,
        defaultBgLight = 0xFFF2ECE1,
        defaultBgDark = 0xFF2A2621,
        defaultFgLight = 0xFF2C251E,
        defaultFgDark = 0xFFF5EFE6,
        accentHex = 0xFFA68966,
        categoryBadge = "ATELIER",
        defaultPattern = CoverPatternKind.TEXTURE,
        defaultIllustration = CoverIllustrationKind.LEAVES,
        preferSerif = true,
        preferredNoteType = NoteType.TEXT,
    ),
    OBSIDIAN_EDITION(
        id = "obsidian_edition",
        label = "Modern Black",
        category = CoverCategory.MINIMAL,
        defaultBgLight = 0xFF141518,
        defaultBgDark = 0xFF0E0F12,
        defaultFgLight = 0xFFF7F5F0,
        defaultFgDark = 0xFFF7F5F0,
        accentHex = 0xFFC8A97E,
        categoryBadge = "MONOGRAPH",
        defaultPattern = CoverPatternKind.SOLID,
        defaultIllustration = CoverIllustrationKind.NONE,
        preferredNoteType = NoteType.TEXT,
    ),
    SWISS_MONOGRAPH(
        id = "swiss_monograph",
        label = "Minimal Two-Tone",
        category = CoverCategory.MINIMAL,
        defaultBgLight = 0xFFF4F1EA,
        defaultBgDark = 0xFF1D2026,
        defaultFgLight = 0xFF18191C,
        defaultFgDark = 0xFFF5F3EF,
        accentHex = 0xFF2F3E46,
        categoryBadge = "MODERN",
        defaultPattern = CoverPatternKind.LINES,
        defaultIllustration = CoverIllustrationKind.NONE,
        twoToneBand = true,
        preferredNoteType = NoteType.TEXT,
    ),
    DOT_GRID_BOOKPLATE(
        id = "dot_grid_bookplate",
        label = "Classic Hardcover",
        category = CoverCategory.MINIMAL,
        defaultBgLight = 0xFF1D2228,
        defaultBgDark = 0xFF15191E,
        defaultFgLight = 0xFFF8F6F0,
        defaultFgDark = 0xFFF8F6F0,
        accentHex = 0xFFE6DFD3,
        categoryBadge = "CLASSIC",
        defaultPattern = CoverPatternKind.DOTS,
        defaultIllustration = CoverIllustrationKind.NONE,
        bookplateStyle = true,
        preferredNoteType = NoteType.INK,
    ),
    RULED_ATELIER(
        id = "ruled_atelier",
        label = "Minimal Ruled",
        category = CoverCategory.MINIMAL,
        defaultBgLight = 0xFFF7F4EE,
        defaultBgDark = 0xFF1F2229,
        defaultFgLight = 0xFF1E2022,
        defaultFgDark = 0xFFF4F2ED,
        accentHex = 0xFF948470,
        categoryBadge = "RULED",
        defaultPattern = CoverPatternKind.LINES,
        defaultIllustration = CoverIllustrationKind.NONE,
        preferredNoteType = NoteType.INK,
    ),

    // =========================================================================
    // B. TWO-TONE COLLECTION (Navy+White, Burgundy+Cream, Black+Gold, Forest Green+Beige, Brown+Ivory, Lavender+White, Charcoal+Silver, Terracotta+Cream)
    // =========================================================================
    NAVY_WHITE(
        id = "navy_white",
        label = "Navy + White",
        category = CoverCategory.TWO_TONE,
        defaultBgLight = 0xFF182B49,
        defaultBgDark = 0xFF101F36,
        defaultFgLight = 0xFFFAF8F5,
        defaultFgDark = 0xFFFAF8F5,
        accentHex = 0xFFF4F1EA,
        categoryBadge = "EXECUTIVE",
        defaultPattern = CoverPatternKind.SOLID,
        defaultIllustration = CoverIllustrationKind.NONE,
        twoToneBand = true,
        preferredNoteType = NoteType.TEXT,
    ),
    BURGUNDY_PINK(
        id = "burgundy_pink",
        label = "Burgundy + Cream",
        category = CoverCategory.TWO_TONE,
        defaultBgLight = 0xFF4A1823,
        defaultBgDark = 0xFF38111A,
        defaultFgLight = 0xFFF9F4EC,
        defaultFgDark = 0xFFF9F4EC,
        accentHex = 0xFFF3E8D6,
        categoryBadge = "COLLECTION",
        defaultPattern = CoverPatternKind.TEXTURE,
        defaultIllustration = CoverIllustrationKind.NONE,
        twoToneBand = true,
        preferSerif = true,
        preferredNoteType = NoteType.INK,
    ),
    BLACK_GOLD(
        id = "black_gold",
        label = "Black + Gold",
        category = CoverCategory.TWO_TONE,
        defaultBgLight = 0xFF151517,
        defaultBgDark = 0xFF0E0E10,
        defaultFgLight = 0xFFF6E3BA,
        defaultFgDark = 0xFFF6E3BA,
        accentHex = 0xFFD4A853,
        categoryBadge = "SIGNATURE",
        defaultPattern = CoverPatternKind.GEOMETRIC,
        defaultIllustration = CoverIllustrationKind.NONE,
        twoToneBand = true,
        preferSerif = true,
    ),
    GREEN_CREAM(
        id = "green_cream",
        label = "Forest Green + Beige",
        category = CoverCategory.TWO_TONE,
        defaultBgLight = 0xFF1B3B2F,
        defaultBgDark = 0xFF122920,
        defaultFgLight = 0xFFF7F2E7,
        defaultFgDark = 0xFFF7F2E7,
        accentHex = 0xFFE9DFCE,
        categoryBadge = "HERITAGE",
        defaultPattern = CoverPatternKind.TEXTURE,
        defaultIllustration = CoverIllustrationKind.LEAVES,
        twoToneBand = true,
        preferredNoteType = NoteType.INK,
    ),
    BROWN_IVORY(
        id = "brown_ivory",
        label = "Brown + Ivory",
        category = CoverCategory.TWO_TONE,
        defaultBgLight = 0xFF4A3222,
        defaultBgDark = 0xFF362317,
        defaultFgLight = 0xFFFBF7F0,
        defaultFgDark = 0xFFFBF7F0,
        accentHex = 0xFFF2E8D9,
        categoryBadge = "CHRONICLE",
        defaultPattern = CoverPatternKind.TEXTURE,
        defaultIllustration = CoverIllustrationKind.NONE,
        twoToneBand = true,
        preferSerif = true,
        preferredNoteType = NoteType.INK,
    ),
    LAVENDER_WHITE(
        id = "lavender_white",
        label = "Lavender + White",
        category = CoverCategory.TWO_TONE,
        defaultBgLight = 0xFF4A3B5C,
        defaultBgDark = 0xFF352944,
        defaultFgLight = 0xFFFAF8FC,
        defaultFgDark = 0xFFFAF8FC,
        accentHex = 0xFFEDE8F5,
        categoryBadge = "PASTEL",
        defaultPattern = CoverPatternKind.SOLID,
        defaultIllustration = CoverIllustrationKind.FLOWERS,
        twoToneBand = true,
        preferredNoteType = NoteType.TEXT,
    ),
    TEAL_GREY(
        id = "teal_grey",
        label = "Charcoal + Silver",
        category = CoverCategory.TWO_TONE,
        defaultBgLight = 0xFF282C34,
        defaultBgDark = 0xFF1C1F26,
        defaultFgLight = 0xFFF1F3F5,
        defaultFgDark = 0xFFF1F3F5,
        accentHex = 0xFFD0D5DD,
        categoryBadge = "STUDIO",
        defaultPattern = CoverPatternKind.GRID,
        defaultIllustration = CoverIllustrationKind.NONE,
        twoToneBand = true,
    ),
    ORANGE_CHARCOAL(
        id = "orange_charcoal",
        label = "Terracotta + Cream",
        category = CoverCategory.TWO_TONE,
        defaultBgLight = 0xFF8C4A32,
        defaultBgDark = 0xFF6B3623,
        defaultFgLight = 0xFFFBF6EE,
        defaultFgDark = 0xFFFBF6EE,
        accentHex = 0xFFF3E6D4,
        categoryBadge = "ARTISAN",
        defaultPattern = CoverPatternKind.TEXTURE,
        defaultIllustration = CoverIllustrationKind.PLANTS,
        twoToneBand = true,
    ),

    // =========================================================================
    // C. NATURE COLLECTION (Botanical Leaves, Floral Garden, Mountain Landscape, Ocean, Forest, Sakura, Minimal Plant, Earth Tones)
    // =========================================================================
    BOTANICAL_LEAVES(
        id = "botanical_leaves",
        label = "Botanical Leaves",
        category = CoverCategory.NATURE,
        defaultBgLight = 0xFF1E382D,
        defaultBgDark = 0xFF14261E,
        defaultFgLight = 0xFFF6F3EB,
        defaultFgDark = 0xFFF6F3EB,
        accentHex = 0xFFA3C9B3,
        categoryBadge = "BOTANICA",
        defaultPattern = CoverPatternKind.TEXTURE,
        defaultIllustration = CoverIllustrationKind.LEAVES,
        preferSerif = true,
        preferredNoteType = NoteType.INK,
    ),
    FLORAL_BOTANICAL(
        id = "floral_botanical",
        label = "Floral Garden",
        category = CoverCategory.NATURE,
        defaultBgLight = 0xFF3E242B,
        defaultBgDark = 0xFF2B181D,
        defaultFgLight = 0xFFFAF3F0,
        defaultFgDark = 0xFFFAF3F0,
        accentHex = 0xFFE8B4BC,
        categoryBadge = "FLORA",
        defaultPattern = CoverPatternKind.SOLID,
        defaultIllustration = CoverIllustrationKind.FLOWERS,
        preferSerif = true,
        preferredNoteType = NoteType.INK,
    ),
    MOUNTAIN_LANDSCAPE(
        id = "mountain_landscape",
        label = "Mountain Landscape",
        category = CoverCategory.NATURE,
        defaultBgLight = 0xFF243442,
        defaultBgDark = 0xFF18232E,
        defaultFgLight = 0xFFF5F7F8,
        defaultFgDark = 0xFFF5F7F8,
        accentHex = 0xFFB8C9D4,
        categoryBadge = "ALPINE",
        defaultPattern = CoverPatternKind.GRADIENT,
        defaultIllustration = CoverIllustrationKind.MOUNTAIN,
    ),
    OCEAN_WAVE(
        id = "ocean_wave",
        label = "Ocean",
        category = CoverCategory.NATURE,
        defaultBgLight = 0xFF183A4A,
        defaultBgDark = 0xFF102833,
        defaultFgLight = 0xFFF2F8FA,
        defaultFgDark = 0xFFF2F8FA,
        accentHex = 0xFF94C5D6,
        categoryBadge = "MARITIME",
        defaultPattern = CoverPatternKind.GRADIENT,
        defaultIllustration = CoverIllustrationKind.OCEAN,
    ),
    GREEN_PLANTS(
        id = "green_plants",
        label = "Forest",
        category = CoverCategory.NATURE,
        defaultBgLight = 0xFF182F25,
        defaultBgDark = 0xFF102019,
        defaultFgLight = 0xFFF4F6F0,
        defaultFgDark = 0xFFF4F6F0,
        accentHex = 0xFF8FB99E,
        categoryBadge = "WOODLAND",
        defaultPattern = CoverPatternKind.TEXTURE,
        defaultIllustration = CoverIllustrationKind.PLANTS,
    ),
    SAKURA_BLOSSOM(
        id = "sakura_blossom",
        label = "Sakura",
        category = CoverCategory.NATURE,
        defaultBgLight = 0xFFF6ECEA,
        defaultBgDark = 0xFF2A2023,
        defaultFgLight = 0xFF332226,
        defaultFgDark = 0xFFFAF0EE,
        accentHex = 0xFFD98A9C,
        categoryBadge = "BLOSSOM",
        defaultPattern = CoverPatternKind.SOLID,
        defaultIllustration = CoverIllustrationKind.SAKURA,
        preferSerif = true,
    ),
    MINIMAL_FLOWERS(
        id = "minimal_flowers",
        label = "Minimal Plant",
        category = CoverCategory.NATURE,
        defaultBgLight = 0xFFF3EFE6,
        defaultBgDark = 0xFF232520,
        defaultFgLight = 0xFF242B24,
        defaultFgDark = 0xFFF2EFE9,
        accentHex = 0xFF6B8A72,
        categoryBadge = "HERBARIUM",
        defaultPattern = CoverPatternKind.TEXTURE,
        defaultIllustration = CoverIllustrationKind.PLANTS,
        preferSerif = true,
    ),
    EARTH_TONES(
        id = "earth_tones",
        label = "Earth Tones",
        category = CoverCategory.NATURE,
        defaultBgLight = 0xFF6E4F3A,
        defaultBgDark = 0xFF473224,
        defaultFgLight = 0xFFF9F4EC,
        defaultFgDark = 0xFFF9F4EC,
        accentHex = 0xFFDCC3A5,
        categoryBadge = "TERRANE",
        defaultPattern = CoverPatternKind.ABSTRACT,
        defaultIllustration = CoverIllustrationKind.LEAVES,
    ),

    // =========================================================================
    // D. ACADEMIC COLLECTION (Computer Science, Mathematics, Physics, Chemistry, Biology, Electronics, Quantum Physics, Engineering, Astronomy, Statistics, Architecture)
    // =========================================================================
    COMPUTER_SCIENCE(
        id = "computer_science",
        label = "Computer Science",
        category = CoverCategory.ACADEMIC,
        defaultBgLight = 0xFF142238,
        defaultBgDark = 0xFF0E1829,
        defaultFgLight = 0xFFF5F7FA,
        defaultFgDark = 0xFFF5F7FA,
        accentHex = 0xFF67B7DC,
        categoryBadge = "COMPUTER SCIENCE",
        defaultPattern = CoverPatternKind.DOTS,
        defaultIllustration = CoverIllustrationKind.COMPUTER_SCIENCE,
    ),
    MATHEMATICS(
        id = "mathematics",
        label = "Mathematics",
        category = CoverCategory.ACADEMIC,
        defaultBgLight = 0xFF23201D,
        defaultBgDark = 0xFF171513,
        defaultFgLight = 0xFFFAF7F2,
        defaultFgDark = 0xFFFAF7F2,
        accentHex = 0xFFE0B46A,
        categoryBadge = "MATHEMATICS",
        defaultPattern = CoverPatternKind.GRID,
        defaultIllustration = CoverIllustrationKind.MATHEMATICS,
        preferSerif = true,
    ),
    PHYSICS(
        id = "physics",
        label = "Physics",
        category = CoverCategory.ACADEMIC,
        defaultBgLight = 0xFF192844,
        defaultBgDark = 0xFF111C30,
        defaultFgLight = 0xFFF6F8FC,
        defaultFgDark = 0xFFF6F8FC,
        accentHex = 0xFF8AB4F8,
        categoryBadge = "PHYSICS",
        defaultPattern = CoverPatternKind.GRID,
        defaultIllustration = CoverIllustrationKind.PHYSICS,
    ),
    CHEMISTRY(
        id = "chemistry",
        label = "Chemistry",
        category = CoverCategory.ACADEMIC,
        defaultBgLight = 0xFF163D3A,
        defaultBgDark = 0xFF0F2B29,
        defaultFgLight = 0xFFF2FAF8,
        defaultFgDark = 0xFFF2FAF8,
        accentHex = 0xFF6AD4C2,
        categoryBadge = "CHEMISTRY",
        defaultPattern = CoverPatternKind.DOTS,
        defaultIllustration = CoverIllustrationKind.CHEMISTRY,
    ),
    BIOLOGY(
        id = "biology",
        label = "Biology",
        category = CoverCategory.ACADEMIC,
        defaultBgLight = 0xFF1C3D2B,
        defaultBgDark = 0xFF132B1E,
        defaultFgLight = 0xFFF4FAF6,
        defaultFgDark = 0xFFF4FAF6,
        accentHex = 0xFF8CD4A4,
        categoryBadge = "BIOLOGY",
        defaultPattern = CoverPatternKind.TEXTURE,
        defaultIllustration = CoverIllustrationKind.BIOLOGY,
    ),
    ELECTRONICS(
        id = "electronics",
        label = "Electronics",
        category = CoverCategory.ACADEMIC,
        defaultBgLight = 0xFF212242,
        defaultBgDark = 0xFF16172E,
        defaultFgLight = 0xFFF4F5FA,
        defaultFgDark = 0xFFF4F5FA,
        accentHex = 0xFF9BA4F4,
        categoryBadge = "ELECTRONICS",
        defaultPattern = CoverPatternKind.GRID,
        defaultIllustration = CoverIllustrationKind.ELECTRONICS,
    ),
    QUANTUM_PHYSICS(
        id = "quantum_physics",
        label = "Quantum Physics",
        category = CoverCategory.ACADEMIC,
        defaultBgLight = 0xFF2A1B42,
        defaultBgDark = 0xFF1C122E,
        defaultFgLight = 0xFFF8F5FC,
        defaultFgDark = 0xFFF8F5FC,
        accentHex = 0xFFC8A2F8,
        categoryBadge = "QUANTUM",
        defaultPattern = CoverPatternKind.DOTS,
        defaultIllustration = CoverIllustrationKind.PHYSICS,
    ),
    ENGINEERING(
        id = "engineering",
        label = "Engineering",
        category = CoverCategory.ACADEMIC,
        defaultBgLight = 0xFF222D3A,
        defaultBgDark = 0xFF171F29,
        defaultFgLight = 0xFFF6F8FA,
        defaultFgDark = 0xFFF6F8FA,
        accentHex = 0xFFE89B5A,
        categoryBadge = "ENGINEERING",
        defaultPattern = CoverPatternKind.GRID,
        defaultIllustration = CoverIllustrationKind.ARCHITECTURE,
    ),
    ASTRONOMY(
        id = "astronomy",
        label = "Astronomy",
        category = CoverCategory.ACADEMIC,
        defaultBgLight = 0xFF111420,
        defaultBgDark = 0xFF0A0C14,
        defaultFgLight = 0xFFFAF8F2,
        defaultFgDark = 0xFFFAF8F2,
        accentHex = 0xFFEAD07A,
        categoryBadge = "ASTRONOMY",
        defaultPattern = CoverPatternKind.DOTS,
        defaultIllustration = CoverIllustrationKind.ASTRONOMY,
    ),
    STATISTICS(
        id = "statistics",
        label = "Statistics",
        category = CoverCategory.ACADEMIC,
        defaultBgLight = 0xFF242E38,
        defaultBgDark = 0xFF182028,
        defaultFgLight = 0xFFF5F7FA,
        defaultFgDark = 0xFFF5F7FA,
        accentHex = 0xFF7CC4B8,
        categoryBadge = "STATISTICS",
        defaultPattern = CoverPatternKind.GRID,
        defaultIllustration = CoverIllustrationKind.STATISTICS,
    ),
    ARCHITECT_GRID(
        id = "architect_grid",
        label = "Architecture",
        category = CoverCategory.ACADEMIC,
        defaultBgLight = 0xFF2A2E35,
        defaultBgDark = 0xFF1B1E24,
        defaultFgLight = 0xFFF6F5F2,
        defaultFgDark = 0xFFF6F5F2,
        accentHex = 0xFFC5B8A5,
        categoryBadge = "ARCHITECTURE",
        defaultPattern = CoverPatternKind.GRID,
        defaultIllustration = CoverIllustrationKind.ARCHITECTURE,
    ),

    // =========================================================================
    // E. CREATIVE COLLECTION (Sketchbook, Art Journal, Photography, Music Journal, Travel Journal, Personal Diary, Creative Writing)
    // =========================================================================
    SKETCHBOOK(
        id = "sketchbook",
        label = "Sketchbook",
        category = CoverCategory.CREATIVE,
        defaultBgLight = 0xFF332B24,
        defaultBgDark = 0xFF221D18,
        defaultFgLight = 0xFFF8F4EC,
        defaultFgDark = 0xFFF8F4EC,
        accentHex = 0xFFD9A673,
        categoryBadge = "SKETCHBOOK",
        defaultPattern = CoverPatternKind.TEXTURE,
        defaultIllustration = CoverIllustrationKind.SKETCH,
        preferredNoteType = NoteType.INK,
    ),
    ART_JOURNAL(
        id = "art_journal",
        label = "Art Journal",
        category = CoverCategory.CREATIVE,
        defaultBgLight = 0xFF42282C,
        defaultBgDark = 0xFF2D1B1E,
        defaultFgLight = 0xFFFAF4F0,
        defaultFgDark = 0xFFFAF4F0,
        accentHex = 0xFFE6A88A,
        categoryBadge = "ART STUDIO",
        defaultPattern = CoverPatternKind.ABSTRACT,
        defaultIllustration = CoverIllustrationKind.SKETCH,
        preferSerif = true,
        preferredNoteType = NoteType.INK,
    ),
    PHOTOGRAPHY(
        id = "photography",
        label = "Photography",
        category = CoverCategory.CREATIVE,
        defaultBgLight = 0xFF1E2022,
        defaultBgDark = 0xFF141517,
        defaultFgLight = 0xFFF5F5F3,
        defaultFgDark = 0xFFF5F5F3,
        accentHex = 0xFFC2B8A8,
        categoryBadge = "PORTFOLIO",
        defaultPattern = CoverPatternKind.GEOMETRIC,
        defaultIllustration = CoverIllustrationKind.CAMERA,
    ),
    MUSIC_JOURNAL(
        id = "music_journal",
        label = "Music Journal",
        category = CoverCategory.CREATIVE,
        defaultBgLight = 0xFF2B211B,
        defaultBgDark = 0xFF1D1612,
        defaultFgLight = 0xFFFAF5ED,
        defaultFgDark = 0xFFFAF5ED,
        accentHex = 0xFFDAB06C,
        categoryBadge = "SYMPHONY",
        defaultPattern = CoverPatternKind.LINES,
        defaultIllustration = CoverIllustrationKind.MUSIC,
        preferSerif = true,
    ),
    TRAVEL_JOURNAL(
        id = "travel_journal",
        label = "Travel Journal",
        category = CoverCategory.CREATIVE,
        defaultBgLight = 0xFF543A26,
        defaultBgDark = 0xFF3B281A,
        defaultFgLight = 0xFFFBF6EE,
        defaultFgDark = 0xFFFBF6EE,
        accentHex = 0xFFD9AC74,
        categoryBadge = "VOYAGER",
        defaultPattern = CoverPatternKind.TEXTURE,
        defaultIllustration = CoverIllustrationKind.COMPASS,
        preferSerif = true,
        preferredNoteType = NoteType.INK,
    ),
    PERSONAL_DIARY(
        id = "personal_diary",
        label = "Personal Diary",
        category = CoverCategory.CREATIVE,
        defaultBgLight = 0xFF3E2934,
        defaultBgDark = 0xFF2B1C24,
        defaultFgLight = 0xFFFAF4F6,
        defaultFgDark = 0xFFFAF4F6,
        accentHex = 0xFFDDB2C4,
        categoryBadge = "JOURNAL",
        defaultPattern = CoverPatternKind.TEXTURE,
        defaultIllustration = CoverIllustrationKind.QUILL,
        preferSerif = true,
        preferCenterAlign = true,
    ),
    CREATIVE_WRITING(
        id = "creative_writing",
        label = "Creative Writing",
        category = CoverCategory.CREATIVE,
        defaultBgLight = 0xFFF5EFE4,
        defaultBgDark = 0xFF26231F,
        defaultFgLight = 0xFF26211B,
        defaultFgDark = 0xFFF6F1E8,
        accentHex = 0xFF9C7A54,
        categoryBadge = "LITERARY",
        defaultPattern = CoverPatternKind.TEXTURE,
        defaultIllustration = CoverIllustrationKind.QUILL,
        preferSerif = true,
        preferredNoteType = NoteType.TEXT,
    ),
    DEEP_TEAL_WHITE(
        id = "deep_teal_white",
        label = "Deep Teal + White",
        category = CoverCategory.TWO_TONE,
        defaultBgLight = 0xFF134E4A,
        defaultBgDark = 0xFF0D3835,
        defaultFgLight = 0xFFFAF9F6,
        defaultFgDark = 0xFFFAF9F6,
        accentHex = 0xFFF4F1EA,
        categoryBadge = "MODERN",
        defaultPattern = CoverPatternKind.SOLID,
        defaultIllustration = CoverIllustrationKind.NONE,
        twoToneBand = true,
    ),
    OLIVE_WARM_GREY(
        id = "olive_warm_grey",
        label = "Olive + Warm Grey",
        category = CoverCategory.TWO_TONE,
        defaultBgLight = 0xFF3A4232,
        defaultBgDark = 0xFF282E22,
        defaultFgLight = 0xFFF5F3EE,
        defaultFgDark = 0xFFF5F3EE,
        accentHex = 0xFFDAD5CA,
        categoryBadge = "FIELD NOTES",
        defaultPattern = CoverPatternKind.TEXTURE,
        defaultIllustration = CoverIllustrationKind.LEAVES,
        twoToneBand = true,
    ),
    PROFESSIONAL_EXECUTIVE(
        id = "professional_executive",
        label = "Executive Folio",
        category = CoverCategory.PROFESSIONAL,
        defaultBgLight = 0xFF1B263B,
        defaultBgDark = 0xFF111927,
        defaultFgLight = 0xFFF8F9FA,
        defaultFgDark = 0xFFF8F9FA,
        accentHex = 0xFFD4A853,
        categoryBadge = "EXECUTIVE",
        defaultPattern = CoverPatternKind.LINES,
        defaultIllustration = CoverIllustrationKind.NONE,
        preferSerif = true,
        preferredNoteType = NoteType.TEXT,
    ),
    CLASSIC_LEATHER_BOUND(
        id = "classic_leather_bound",
        label = "Leather Journal",
        category = CoverCategory.CLASSIC,
        defaultBgLight = 0xFF4E3220,
        defaultBgDark = 0xFF382214,
        defaultFgLight = 0xFFFBF6EE,
        defaultFgDark = 0xFFFBF6EE,
        accentHex = 0xFFDAB06C,
        categoryBadge = "CLASSIC",
        defaultPattern = CoverPatternKind.TEXTURE,
        defaultIllustration = CoverIllustrationKind.NONE,
        preferSerif = true,
        preferredNoteType = NoteType.INK,
    ),
    MODERN_SWISS_GRID(
        id = "modern_swiss_grid",
        label = "Modern Grid",
        category = CoverCategory.MODERN,
        defaultBgLight = 0xFFF4F1EA,
        defaultBgDark = 0xFF1B1D22,
        defaultFgLight = 0xFF16171A,
        defaultFgDark = 0xFFF5F3EF,
        accentHex = 0xFF2B5946,
        categoryBadge = "MODERN",
        defaultPattern = CoverPatternKind.GRID,
        defaultIllustration = CoverIllustrationKind.MATHEMATICS,
        preferredNoteType = NoteType.TEXT,
    ),
    ARTISTIC_ATELIER(
        id = "artistic_atelier",
        label = "Artistic Canvas",
        category = CoverCategory.ARTISTIC,
        defaultBgLight = 0xFF472C32,
        defaultBgDark = 0xFF301C21,
        defaultFgLight = 0xFFFAF3EE,
        defaultFgDark = 0xFFFAF3EE,
        accentHex = 0xFFE6B08A,
        categoryBadge = "ATELIER",
        defaultPattern = CoverPatternKind.ABSTRACT,
        defaultIllustration = CoverIllustrationKind.SKETCH,
        preferSerif = true,
        preferredNoteType = NoteType.INK,
    ),
    SCIENTIFIC_LAB(
        id = "scientific_lab",
        label = "Scientific Research",
        category = CoverCategory.SCIENTIFIC,
        defaultBgLight = 0xFF153238,
        defaultBgDark = 0xFF0E2226,
        defaultFgLight = 0xFFF2FAF8,
        defaultFgDark = 0xFFF2FAF8,
        accentHex = 0xFF72D6C6,
        categoryBadge = "LABORATORY",
        defaultPattern = CoverPatternKind.GRID,
        defaultIllustration = CoverIllustrationKind.CHEMISTRY,
    ),
    CLEAN_PAPER(
        id = "clean_paper",
        label = "Clean Paper",
        category = CoverCategory.MINIMAL,
        defaultBgLight = 0xFFFAF7F0,
        defaultBgDark = 0xFF1A1C20,
        defaultFgLight = 0xFF1E2022,
        defaultFgDark = 0xFFF4F2ED,
        accentHex = 0xFF9E9486,
        categoryBadge = "PAPER",
        defaultPattern = CoverPatternKind.LINES,
        defaultIllustration = CoverIllustrationKind.NONE,
    );

    val collection: CoverCategory
        get() = category

    companion object {
        val DUO_NAVY_WHITE get() = NAVY_WHITE
        val DUO_BLACK_GOLD get() = BLACK_GOLD
        val DUO_EMERALD_CREAM get() = GREEN_CREAM
        val DUO_LAVENDER_WHITE get() = LAVENDER_WHITE
        val DUO_BURGUNDY_ROSE get() = BURGUNDY_PINK
        val DUO_TEAL_GREY get() = TEAL_GREY
        val DUO_TERRACOTTA_CHARCOAL get() = ORANGE_CHARCOAL
        val NATURE_FLORAL_BLOSSOM get() = FLORAL_BOTANICAL
        val NATURE_BOTANICAL_LEAVES get() = BOTANICAL_LEAVES
        val NATURE_MINIMAL_WILDFLOWER get() = MINIMAL_FLOWERS
        val NATURE_MONSTERA_LEAF get() = GREEN_PLANTS
        val NATURE_MOUNTAIN_MIST get() = MOUNTAIN_LANDSCAPE
        val SUBJECT_COMPUTER_SCIENCE get() = COMPUTER_SCIENCE
        val SUBJECT_MATHEMATICS get() = MATHEMATICS
        val SUBJECT_PHYSICS get() = PHYSICS
        val SUBJECT_CHEMISTRY get() = CHEMISTRY
        val SUBJECT_BIOLOGY get() = BIOLOGY
        val SUBJECT_ELECTRONICS get() = ELECTRONICS
        val SUBJECT_QUANTUM_PHYSICS get() = QUANTUM_PHYSICS
        val SUBJECT_ENGINEERING get() = ENGINEERING
        val SUBJECT_ASTRONOMY get() = ASTRONOMY

        fun fromId(id: String?): NoteCoverDesign? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Curated Background/Theme Palettes for the Colour tab in Cover Studio.
 */
enum class CoverColorPreset(
    val id: String,
    val label: String,
    val bgHex: Long?,
    val fgHex: Long?,
    val accentHex: Long? = null,
) {
    DEFAULT("default", "Cover Default", null, null, null),
    WARM_IVORY("ivory", "Warm Ivory", 0xFFFBF8F1, 0xFF1E1C19, 0xFF8C7355),
    SOFT_CREAM("cream", "Soft Cream", 0xFFF2ECE0, 0xFF28231D, 0xFFA3835E),
    PURE_WHITE("white", "Pure White", 0xFFFFFFFF, 0xFF141518, 0xFF6B7280),
    STONE("stone", "Warm Stone", 0xFFE6E1D8, 0xFF1F1D1A, 0xFF787166),
    PINE("pine", "Forest Green", 0xFF1B3B2F, 0xFFF7F3EB, 0xFFA3C9B3),
    CARAMEL("caramel", "Caramel Leather", 0xFF6E472B, 0xFFFBF6EE, 0xFFE2B887),
    BROWN("brown", "Espresso Brown", 0xFF3D281B, 0xFFF9F4EC, 0xFFD4A876),
    OXBLOOD("oxblood", "Burgundy Wine", 0xFF4A1823, 0xFFF9F4EC, 0xFFE8B4BC),
    SLATE("slate", "Royal Navy", 0xFF182B49, 0xFFF8F9FA, 0xFF93B8E0),
    GRAPHITE("graphite", "Graphite Slate", 0xFF262930, 0xFFF5F3EF, 0xFFB8BEC8),
    OBSIDIAN("obsidian", "Midnight Gold", 0xFF131417, 0xFFF7E7C4, 0xFFD4A853);

    companion object {
        fun fromId(id: String?): CoverColorPreset = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Custom Accent Colour swatches in Cover Studio.
 */
enum class CoverAccentChoice(val id: String, val label: String, val hex: Long?) {
    DEFAULT("default", "Auto", null),
    GOLD("gold", "Warm Gold", 0xFFD4A853),
    CARAMEL("caramel", "Caramel", 0xFFC88A4B),
    SAGE("sage", "Sage Leaf", 0xFF8FB99E),
    CREAM("cream", "Warm Cream", 0xFFF3E8D6),
    ROSE("rose", "Dusty Rose", 0xFFE2A6B0),
    SKY("sky", "Slate Blue", 0xFF7BAED4),
    CHARCOAL("charcoal", "Deep Ink", 0xFF222428);

    companion object {
        fun fromId(id: String?): CoverAccentChoice = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Custom Text Colour swatches in Cover Studio.
 */
enum class CoverTextColorChoice(val id: String, val label: String, val hex: Long?) {
    DEFAULT("default", "Auto Contrast", null),
    WARM_CREAM("cream", "Warm Cream", 0xFFFAF6EE),
    PURE_WHITE("white", "Pure White", 0xFFFFFFFF),
    GOLD_FOIL("gold", "Gold Foil", 0xFFF5DFAA),
    DEEP_INK("ink", "Deep Charcoal", 0xFF18191C),
    ESPRESSO("espresso", "Espresso", 0xFF2E2218);

    companion object {
        fun fromId(id: String?): CoverTextColorChoice = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Complete Persisted Configuration for a Note or Notebook Cover.
 * Backward-compatible with existing 3-field strings while persisting all 5 studio tabs!
 */
data class NoteCoverConfig(
    val design: NoteCoverDesign,
    val colorPreset: CoverColorPreset = CoverColorPreset.DEFAULT,
    val hasCover: Boolean = true,
    val accentChoice: CoverAccentChoice = CoverAccentChoice.DEFAULT,
    val textColorChoice: CoverTextColorChoice = CoverTextColorChoice.DEFAULT,
    val patternKind: CoverPatternKind = CoverPatternKind.DEFAULT,
    val illustrationKind: CoverIllustrationKind = CoverIllustrationKind.DEFAULT,
    val spineStyle: CoverSpineStyle = CoverSpineStyle.CLASSIC,
    val borderStyle: CoverBorderStyle = CoverBorderStyle.SUBTLE,
    val titlePosition: CoverTitlePosition = CoverTitlePosition.UPPER,
    val illustrationPosition: CoverIllustrationPosition = CoverIllustrationPosition.BOTTOM_RIGHT,
    val proportionPreset: CoverProportionPreset = CoverProportionPreset.CLASSIC_NOTEBOOK,
    val fontStyle: CoverFontStyle = CoverFontStyle.DEFAULT,
    val fontWeightOption: CoverFontWeightOption = CoverFontWeightOption.BOLD,
    val textAlignOption: CoverTextAlignOption = CoverTextAlignOption.DEFAULT,
    val titleScaleOption: CoverTitleScaleOption = CoverTitleScaleOption.BALANCED,
    val showSubtitle: Boolean = true,
) {
    val aspectRatioValue: Float
        get() = proportionPreset.ratio

    fun encode(): String = listOf(
        design.id,
        colorPreset.id,
        hasCover.toString(),
        accentChoice.id,
        textColorChoice.id,
        patternKind.id,
        illustrationKind.id,
        spineStyle.id,
        borderStyle.id,
        titlePosition.id,
        illustrationPosition.id,
        proportionPreset.id,
        fontStyle.id,
        fontWeightOption.id,
        textAlignOption.id,
        titleScaleOption.id,
        showSubtitle.toString(),
    ).joinToString("|")

    companion object {
        fun decode(raw: String?): NoteCoverConfig? {
            if (raw.isNullOrBlank()) return null
            val p = raw.split("|")
            val design = NoteCoverDesign.fromId(p.getOrNull(0)) ?: return null
            return NoteCoverConfig(
                design = design,
                colorPreset = CoverColorPreset.fromId(p.getOrNull(1)),
                hasCover = p.getOrNull(2)?.toBooleanStrictOrNull() ?: true,
                accentChoice = CoverAccentChoice.fromId(p.getOrNull(3)),
                textColorChoice = CoverTextColorChoice.fromId(p.getOrNull(4)),
                patternKind = CoverPatternKind.fromId(p.getOrNull(5)),
                illustrationKind = CoverIllustrationKind.fromId(p.getOrNull(6)),
                spineStyle = CoverSpineStyle.fromId(p.getOrNull(7)),
                borderStyle = CoverBorderStyle.fromId(p.getOrNull(8)),
                titlePosition = CoverTitlePosition.fromId(p.getOrNull(9)),
                illustrationPosition = CoverIllustrationPosition.fromId(p.getOrNull(10)),
                proportionPreset = CoverProportionPreset.fromId(p.getOrNull(11)),
                fontStyle = CoverFontStyle.fromId(p.getOrNull(12)),
                fontWeightOption = CoverFontWeightOption.fromId(p.getOrNull(13)),
                textAlignOption = CoverTextAlignOption.fromId(p.getOrNull(14)),
                titleScaleOption = CoverTitleScaleOption.fromId(p.getOrNull(15)),
                showSubtitle = p.getOrNull(16)?.toBooleanStrictOrNull() ?: true,
            )
        }
    }
}

object NoteCoverStore {
    private val _noteCovers = MutableStateFlow<Map<String, NoteCoverConfig>>(emptyMap())
    val noteCovers: StateFlow<Map<String, NoteCoverConfig>> = _noteCovers.asStateFlow()

    private val _notebookCovers = MutableStateFlow<Map<String, NoteCoverConfig>>(emptyMap())
    val notebookCovers: StateFlow<Map<String, NoteCoverConfig>> = _notebookCovers.asStateFlow()

    private val _displayMode = MutableStateFlow(NotebookDisplayMode.MODE_3D)
    val displayMode: StateFlow<NotebookDisplayMode> = _displayMode.asStateFlow()

    @Volatile
    private var initialized = false

    @Volatile
    private var pendingCreationCover: NoteCoverConfig? = null

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
        val notesMap = mutableMapOf<String, NoteCoverConfig>()
        for ((id, encoded) in db.getAllCovers(com.appname.notes.data.metadata.AppMetadataSqliteStore.TYPE_NOTE)) {
            NoteCoverConfig.decode(encoded)?.let { notesMap[id] = it }
        }
        val notebooksMap = mutableMapOf<String, NoteCoverConfig>()
        for ((id, encoded) in db.getAllCovers(com.appname.notes.data.metadata.AppMetadataSqliteStore.TYPE_NOTEBOOK)) {
            NoteCoverConfig.decode(encoded)?.let { notebooksMap[id] = it }
        }
        _noteCovers.value = notesMap
        _notebookCovers.value = notebooksMap
        _displayMode.value = NotebookDisplayMode.fromId(
            db.getPreference(com.appname.notes.data.metadata.AppMetadataSqliteStore.KEY_DISPLAY_MODE)
                ?: NotebookDisplayMode.MODE_3D.id,
        )
    }

    fun setDisplayMode(context: Context, mode: NotebookDisplayMode) {
        ensureInit(context)
        val db = com.appname.notes.data.metadata.AppMetadataSqliteStore.getInstance(context)
        db.putPreference(com.appname.notes.data.metadata.AppMetadataSqliteStore.KEY_DISPLAY_MODE, mode.id)
        _displayMode.value = mode
    }

    fun stagePendingCoverForNewNote(config: NoteCoverConfig) {
        pendingCreationCover = config
    }

    fun consumePendingCoverForNewNote(context: Context, noteId: String) {
        val pending = pendingCreationCover ?: return
        pendingCreationCover = null
        setNoteCover(context, noteId, pending)
    }

    fun setNoteCover(context: Context, noteId: String, config: NoteCoverConfig) {
        ensureInit(context)
        val db = com.appname.notes.data.metadata.AppMetadataSqliteStore.getInstance(context)
        db.putCover(com.appname.notes.data.metadata.AppMetadataSqliteStore.TYPE_NOTE, noteId, config.encode())
        _noteCovers.value = _noteCovers.value.toMutableMap().apply { put(noteId, config) }
    }

    fun resetNoteCover(context: Context, noteId: String) {
        ensureInit(context)
        val db = com.appname.notes.data.metadata.AppMetadataSqliteStore.getInstance(context)
        db.deleteCover(com.appname.notes.data.metadata.AppMetadataSqliteStore.TYPE_NOTE, noteId)
        _noteCovers.value = _noteCovers.value.toMutableMap().apply { remove(noteId) }
    }

    fun setNotebookCover(context: Context, notebookId: String, config: NoteCoverConfig) {
        ensureInit(context)
        val db = com.appname.notes.data.metadata.AppMetadataSqliteStore.getInstance(context)
        db.putCover(com.appname.notes.data.metadata.AppMetadataSqliteStore.TYPE_NOTEBOOK, notebookId, config.encode())
        _notebookCovers.value = _notebookCovers.value.toMutableMap().apply { put(notebookId, config) }
    }

    fun resetNotebookCover(context: Context, notebookId: String) {
        ensureInit(context)
        val db = com.appname.notes.data.metadata.AppMetadataSqliteStore.getInstance(context)
        db.deleteCover(com.appname.notes.data.metadata.AppMetadataSqliteStore.TYPE_NOTEBOOK, notebookId)
        _notebookCovers.value = _notebookCovers.value.toMutableMap().apply { remove(notebookId) }
    }

    fun resolveForNote(
        noteId: String,
        noteType: NoteType,
        map: Map<String, NoteCoverConfig>,
        titleHint: String = "",
    ): NoteCoverConfig {
        map[noteId]?.let { saved ->
            return if (!saved.hasCover) saved.copy(design = NoteCoverDesign.CLEAN_PAPER) else saved
        }
        val t = titleHint.lowercase()
        when {
            t.contains("dbms") || t.contains("sql") || t.contains("code") || t.contains("java") || t.contains("python") || t.contains("dsa") || t.contains("algo") || t.contains("computer") || t.contains("software") ->
                return NoteCoverConfig(NoteCoverDesign.COMPUTER_SCIENCE)
            t.contains("stat") || t.contains("probab") || t.contains("data") ->
                return NoteCoverConfig(NoteCoverDesign.STATISTICS)
            t.contains("math") || t.contains("calc") || t.contains("algebra") || t.contains("geom") ->
                return NoteCoverConfig(NoteCoverDesign.MATHEMATICS)
            t.contains("quantum") ->
                return NoteCoverConfig(NoteCoverDesign.QUANTUM_PHYSICS)
            t.contains("physic") || t.contains("mechan") || t.contains("optics") ->
                return NoteCoverConfig(NoteCoverDesign.PHYSICS)
            t.contains("chem") || t.contains("organic") || t.contains("molecule") ->
                return NoteCoverConfig(NoteCoverDesign.CHEMISTRY)
            t.contains("bio") || t.contains("anatom") || t.contains("botan") || t.contains("med") ->
                return NoteCoverConfig(NoteCoverDesign.BIOLOGY)
            t.contains("circuit") || t.contains("electron") || t.contains("iot") ->
                return NoteCoverConfig(NoteCoverDesign.ELECTRONICS)
            t.contains("astro") || t.contains("space") || t.contains("cosmos") ->
                return NoteCoverConfig(NoteCoverDesign.ASTRONOMY)
            t.contains("arch") || t.contains("design") ->
                return NoteCoverConfig(NoteCoverDesign.ARCHITECT_GRID)
            t.contains("eng") || t.contains("robot") ->
                return NoteCoverConfig(NoteCoverDesign.ENGINEERING)
            t.contains("sketch") || t.contains("draw") || t.contains("art") ->
                return NoteCoverConfig(NoteCoverDesign.SKETCHBOOK)
            t.contains("music") || t.contains("song") ->
                return NoteCoverConfig(NoteCoverDesign.MUSIC_JOURNAL)
            t.contains("travel") || t.contains("trip") ->
                return NoteCoverConfig(NoteCoverDesign.TRAVEL_JOURNAL)
            t.contains("diary") || t.contains("journal") ->
                return NoteCoverConfig(NoteCoverDesign.PERSONAL_DIARY)
        }
        val hash = (noteId.hashCode() and 0x7FFFFFFF)
        return when (noteType) {
            NoteType.TEXT -> {
                val typedDefaults = listOf(
                    NoteCoverDesign.EDITORIAL_WRITER,
                    NoteCoverDesign.MINIMAL_BEIGE,
                    NoteCoverDesign.NAVY_WHITE,
                    NoteCoverDesign.SWISS_MONOGRAPH,
                    NoteCoverDesign.PURE_LINEN,
                    NoteCoverDesign.CREATIVE_WRITING,
                    NoteCoverDesign.LAVENDER_WHITE,
                )
                NoteCoverConfig(design = typedDefaults[hash % typedDefaults.size])
            }
            NoteType.INK -> {
                val inkDefaults = listOf(
                    NoteCoverDesign.GREEN_CREAM,
                    NoteCoverDesign.BURGUNDY_PINK,
                    NoteCoverDesign.BOTANICAL_LEAVES,
                    NoteCoverDesign.BROWN_IVORY,
                    NoteCoverDesign.GRAPHITE_FOLD,
                    NoteCoverDesign.DOT_GRID_BOOKPLATE,
                    NoteCoverDesign.FLORAL_BOTANICAL,
                    NoteCoverDesign.TRAVEL_JOURNAL,
                )
                NoteCoverConfig(design = inkDefaults[hash % inkDefaults.size])
            }
            NoteType.PDF -> NoteCoverConfig(design = NoteCoverDesign.NAVY_WHITE)
        }
    }
}

/**
 * Reusable Physical 3D Hardcover Notebook Cover Component (Sections 2, 3, 4, 5, 7 & 8).
 */
@Composable
fun NoteCoverArtwork(
    title: String,
    noteType: NoteType,
    config: NoteCoverConfig,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isLocked: Boolean = false,
    compact: Boolean = false,
    overrideDisplayMode: NotebookDisplayMode? = null,
) {
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current
    val observedMode by NoteCoverStore.displayMode.collectAsStateWithLifecycle()
    val activeDisplayMode = overrideDisplayMode ?: observedMode
    val is3DMode = activeDisplayMode == NotebookDisplayMode.MODE_3D

    // Smooth animated transition when user toggles between 2D Flat Covers and 3D Realistic Covers
    val depthProgress by animateFloatAsState(
        targetValue = if (is3DMode) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "cover2D3DDepthProgress",
    )

    val effectiveDesign = if (!config.hasCover) NoteCoverDesign.CLEAN_PAPER else config.design

    val targetBg = remember(effectiveDesign, config.colorPreset, isDark) {
        val hex = config.colorPreset.bgHex ?: if (isDark) effectiveDesign.defaultBgDark else effectiveDesign.defaultBgLight
        Color(hex)
    }
    val targetFg = remember(effectiveDesign, config.colorPreset, config.textColorChoice, isDark) {
        val hex = config.textColorChoice.hex
            ?: config.colorPreset.fgHex
            ?: if (isDark) effectiveDesign.defaultFgDark else effectiveDesign.defaultFgLight
        Color(hex)
    }
    val targetAccent = remember(effectiveDesign, config.colorPreset, config.accentChoice) {
        val hex = config.accentChoice.hex
            ?: config.colorPreset.accentHex
            ?: effectiveDesign.accentHex
        Color(hex)
    }

    val bgColor by animateColorAsState(targetBg, animationSpec = tween(220), label = "coverBg")
    val fgColor by animateColorAsState(targetFg, animationSpec = tween(220), label = "coverFg")
    val accentColor by animateColorAsState(targetAccent, animationSpec = tween(220), label = "coverAccent")

    val effectivePattern = if (config.patternKind == CoverPatternKind.DEFAULT) effectiveDesign.defaultPattern else config.patternKind
    val effectiveIllustration = if (config.illustrationKind == CoverIllustrationKind.DEFAULT) effectiveDesign.defaultIllustration else config.illustrationKind

    // Section 3 Cover Text Rules: Always use the actual notebook name; never internal body text.
    val cleanTitle = title.trim().ifBlank {
        when (noteType) {
            NoteType.INK -> "Untitled Notebook"
            NoteType.PDF -> "Untitled Document"
            NoteType.TEXT -> "Untitled Note"
        }
    }

    // 2D vs 3D Shape & Shadow
    val leftCornerDp = if (compact) 4.dp else (10.dp - (6.dp * depthProgress))
    val rightCornerDp = if (compact) 7.dp else (10.dp + (2.dp * depthProgress))
    val bookShape = RoundedCornerShape(
        topStart = leftCornerDp,
        bottomStart = leftCornerDp,
        topEnd = rightCornerDp,
        bottomEnd = rightCornerDp,
    )
    val shadowElev = if (compact) (1.dp + (2.dp * depthProgress)) else (1.5.dp + (6.5.dp * depthProgress))
    val outerBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color(0xFF1E1B18).copy(alpha = 0.16f)

    BoxWithConstraints(
        modifier = modifier
            .shadow(
                elevation = shadowElev,
                shape = bookShape,
                ambientColor = Color(0xFF1A1510).copy(alpha = 0.18f + 0.28f * depthProgress),
                spotColor = Color(0xFF1A1510).copy(alpha = 0.22f + 0.32f * depthProgress),
                clip = false,
            )
            .clip(bookShape)
            .background(bgColor)
            .border(0.85.dp, outerBorderColor, bookShape),
    ) {
        val cardW = maxWidth
        val isTinyListThumb = compact && cardW < 64.dp
        val isGalleryThumb = cardW < 116.dp

        val baseSpineDp = when {
            isTinyListThumb -> 3.5.dp
            isGalleryThumb -> 7.5.dp
            else -> 12.dp
        }
        // In 2D mode, spine flattens to a subtle 2.5.dp accent margin; in 3D mode, full hardcover spine
        val spineDp = (baseSpineDp * config.spineStyle.widthFactor * depthProgress) + (2.5.dp * (1f - depthProgress))
        val pageRightDp = (if (isTinyListThumb) 1.5.dp else if (isGalleryThumb) 2.6.dp else 4.0.dp) * depthProgress
        val pageBottomDp = (if (isTinyListThumb) 1.2.dp else if (isGalleryThumb) 2.2.dp else 3.4.dp) * depthProgress

        // Top Header Label adapts to card width so it never crowds
        val topHeaderLabel = remember(effectiveDesign, isGalleryThumb, isTinyListThumb) {
            if (isTinyListThumb || isGalleryThumb) {
                "NOTES"
            } else {
                val cat = effectiveDesign.categoryBadge
                if (cat.isNotBlank() && cat != "NOTES") "NOTES · $cat" else "NOTES"
            }
        }

        // 1. Canvas Layer: Pattern, Strict Non-Overlapping Artwork Region (50%..82% height), Border, 2D/3D Spine & Page Edges
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val spineX = spineDp.toPx()
            val pageEdgeRightW = pageRightDp.toPx()
            val pageEdgeBottomH = pageBottomDp.toPx()
            val coverW = (w - pageEdgeRightW).coerceAtLeast(10f)
            val coverH = (h - pageEdgeBottomH).coerceAtLeast(10f)

            // A. Optional Two-Tone Header Band (Strictly in Top Region 0%..19% so it never collides with Title Region!)
            if (effectiveDesign.twoToneBand && config.patternKind != CoverPatternKind.SOLID) {
                val bandH = coverH * 0.19f
                drawRect(
                    color = accentColor.copy(alpha = 0.92f),
                    topLeft = Offset(0f, 0f),
                    size = Size(coverW, bandH),
                )
                drawLine(
                    color = Color.Black.copy(alpha = 0.15f),
                    start = Offset(0f, bandH),
                    end = Offset(coverW, bandH),
                    strokeWidth = 0.8.dp.toPx(),
                )
            }

            // B. Background Surface Pattern
            drawCoverPatternLayer(
                pattern = effectivePattern,
                w = coverW,
                h = coverH,
                spineX = spineX,
                fgColor = fgColor,
                accentColor = accentColor,
                compact = isTinyListThumb || isGalleryThumb,
            )

            // C. Dedicated Artwork Region (50%..82% of cover height — NEVER enters Title Region 19%..48%!)
            if (!isTinyListThumb) {
                drawCoverIllustrationLayer(
                    illustration = effectiveIllustration,
                    position = config.illustrationPosition,
                    w = coverW,
                    h = coverH,
                    spineX = spineX,
                    fgColor = fgColor,
                    accentColor = accentColor,
                    compact = isGalleryThumb,
                )
            }

            // D. Optional Border Frame
            drawCoverBorderLayer(
                borderStyle = config.borderStyle,
                w = coverW,
                h = coverH,
                spineX = spineX,
                fgColor = fgColor,
                accentColor = accentColor,
                compact = isTinyListThumb || isGalleryThumb,
            )

            // E. Spine Rendering (3D Hardcover Spine vs 2D Flat Seam)
            if (spineX > 1f) {
                if (depthProgress > 0.05f) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.26f * depthProgress),
                                Color.Black.copy(alpha = 0.12f * depthProgress),
                                Color.Black.copy(alpha = 0.30f * depthProgress),
                            ),
                            startX = 0f,
                            endX = spineX,
                        ),
                        topLeft = Offset.Zero,
                        size = Size(spineX, coverH),
                    )
                    drawLine(
                        color = Color.Black.copy(alpha = 0.36f * depthProgress),
                        start = Offset(spineX, 0f),
                        end = Offset(spineX, coverH),
                        strokeWidth = 1.2.dp.toPx(),
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.20f * depthProgress),
                        start = Offset(spineX + 1.3.dp.toPx(), 0f),
                        end = Offset(spineX + 1.3.dp.toPx(), coverH),
                        strokeWidth = 0.85.dp.toPx(),
                    )
                    if (config.spineStyle == CoverSpineStyle.RIDGED && !isTinyListThumb) {
                        listOf(0.15f, 0.28f, 0.72f, 0.85f).forEach { ry ->
                            val y = coverH * ry
                            drawLine(Color.Black.copy(alpha = 0.32f * depthProgress), Offset(1f, y), Offset(spineX - 1f, y), strokeWidth = 1.4.dp.toPx())
                        }
                    } else if (config.spineStyle == CoverSpineStyle.STITCHED && !isTinyListThumb) {
                        var sy = 8.dp.toPx()
                        val step = 7.dp.toPx()
                        while (sy < coverH - 6.dp.toPx()) {
                            drawLine(
                                color = accentColor.copy(alpha = 0.50f * depthProgress),
                                start = Offset(spineX * 0.5f, sy),
                                end = Offset(spineX * 0.5f, (sy + step * 0.45f).coerceAtMost(coverH - 4f)),
                                strokeWidth = 1.0.dp.toPx(),
                            )
                            sy += step
                        }
                    }
                } else {
                    // 2D Flat Mode: subtle flat left accent line
                    drawRect(
                        color = accentColor.copy(alpha = 0.28f),
                        topLeft = Offset.Zero,
                        size = Size(spineX, coverH),
                    )
                }
            }

            // F. 3D Right & Bottom Paper Page-Block Stack (Animated by depthProgress; 0px in 2D Flat Mode)
            if (depthProgress > 0.02f && pageEdgeRightW > 0.5f) {
                val creamPaperBase = if (isDark) Color(0xFFD8D2C5) else Color(0xFFF6F1E6)
                val creamPaperLine = Color(0xFF8C8273).copy(alpha = 0.38f * depthProgress)

                drawRect(
                    color = creamPaperBase.copy(alpha = depthProgress),
                    topLeft = Offset(coverW, 2.dp.toPx()),
                    size = Size(pageEdgeRightW, coverH - 2.dp.toPx()),
                )
                val rMid = coverW + pageEdgeRightW * 0.5f
                drawLine(creamPaperLine, Offset(coverW, 2.dp.toPx()), Offset(coverW, coverH), strokeWidth = 0.8.dp.toPx())
                drawLine(creamPaperLine, Offset(rMid, 3.dp.toPx()), Offset(rMid, coverH), strokeWidth = 0.6.dp.toPx())

                drawRect(
                    color = creamPaperBase.copy(alpha = 0.94f * depthProgress),
                    topLeft = Offset(spineX * 0.4f, coverH),
                    size = Size(w - spineX * 0.4f, pageEdgeBottomH),
                )
                val bMid = coverH + pageEdgeBottomH * 0.5f
                drawLine(creamPaperLine, Offset(spineX * 0.4f, coverH), Offset(w, coverH), strokeWidth = 0.8.dp.toPx())
                drawLine(creamPaperLine, Offset(spineX * 0.5f, bMid), Offset(w, bMid), strokeWidth = 0.6.dp.toPx())
            }
        }

        if (isLocked) {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.Lock, null, tint = fgColor.copy(alpha = 0.88f), modifier = Modifier.size(if (isTinyListThumb) 14.dp else 20.dp))
                if (!isTinyListThumb) {
                    Spacer(Modifier.height(4.dp))
                    Text(cleanTitle, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = fgColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            return@BoxWithConstraints
        }

        if (isTinyListThumb) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = spineDp + 3.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cleanTitle.take(12),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp, lineHeight = 9.sp),
                    fontWeight = FontWeight.Bold,
                    color = fgColor,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            return@BoxWithConstraints
        }

        // 2. Strict 4-Region Non-Overlapping Typography Architecture (Requirement 3):
        //    - Region 1 (0%..19%): Top "NOTES · <CATEGORY>" header + emblem dot
        //    - Region 2 (20%..48%): Dedicated Notebook Title Region (Dynamic sizing & wrapping; ZERO illustration overlap!)
        //    - Region 3 (49%..83%): Dedicated Artwork Region (Canvas illustration lives here unobstructed)
        //    - Region 4 (84%..100%): Bottom Subtitle / Category / Date Region
        val fontFamily = when (config.fontStyle) {
            CoverFontStyle.SERIF -> FontFamily.Serif
            CoverFontStyle.SANS -> FontFamily.SansSerif
            CoverFontStyle.MONO -> FontFamily.Monospace
            CoverFontStyle.CURSIVE -> FontFamily.Cursive
            CoverFontStyle.DEFAULT -> if (effectiveDesign.preferSerif) FontFamily.Serif else FontFamily.SansSerif
        }
        val textAlign = when (config.textAlignOption) {
            CoverTextAlignOption.LEFT -> TextAlign.Start
            CoverTextAlignOption.CENTRE -> TextAlign.Center
            CoverTextAlignOption.RIGHT -> TextAlign.End
            CoverTextAlignOption.DEFAULT -> if (effectiveDesign.preferCenterAlign || effectiveDesign.bookplateStyle) TextAlign.Center else TextAlign.Start
        }
        val horizontalAlignment = when (textAlign) {
            TextAlign.Center -> Alignment.CenterHorizontally
            TextAlign.End -> Alignment.End
            else -> Alignment.Start
        }

        // Dynamic Title Sizing based on card width AND title character length so it never clips or overflows
        val len = cleanTitle.length
        val baseTitleSp = if (isGalleryThumb) {
            when {
                len <= 8 -> 11.2f
                len <= 14 -> 9.8f
                len <= 22 -> 8.8f
                else -> 8.0f
            }
        } else {
            when {
                len <= 8 -> 16.5f
                len <= 15 -> 14.5f
                len <= 24 -> 12.8f
                else -> 11.4f
            }
        }
        val scaledTitleSize = (baseTitleSp * config.titleScaleOption.scale).sp
        val titleLineHeight = (scaledTitleSize.value * 1.16f).sp
        val badgeFontSize = if (isGalleryThumb) 6.2.sp else 7.6.sp

        val bandLuminance = (0.299f * accentColor.red + 0.587f * accentColor.green + 0.114f * accentColor.blue)
        val topHeaderColor = if (effectiveDesign.twoToneBand && config.patternKind != CoverPatternKind.SOLID) {
            if (bandLuminance > 0.55f) Color(0xFF18191C).copy(alpha = 0.86f) else Color(0xFFFAF8F5).copy(alpha = 0.90f)
        } else {
            accentColor.copy(alpha = 0.92f)
        }

        if (effectiveDesign.bookplateStyle) {
            val plateBg = Color(0xFFFAF7F0)
            val plateFg = Color(0xFF18191C)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = spineDp + (if (isGalleryThumb) 5.dp else 8.dp),
                        end = pageRightDp + (if (isGalleryThumb) 6.dp else 10.dp),
                        top = if (isGalleryThumb) 10.dp else 14.dp,
                        bottom = pageBottomDp + (if (isGalleryThumb) 10.dp else 14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = plateBg,
                    border = BorderStroke(0.85.dp, plateFg.copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = if (isGalleryThumb) 5.dp else 8.dp, vertical = if (isGalleryThumb) 5.dp else 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = topHeaderLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = badgeFontSize, letterSpacing = 0.8.sp),
                            fontWeight = FontWeight.Bold,
                            color = plateFg.copy(alpha = 0.62f),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(Modifier.width(20.dp).height(0.8.dp).background(plateFg.copy(alpha = 0.26f)))
                        Text(
                            text = cleanTitle,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = fontFamily,
                                fontSize = scaledTitleSize,
                                lineHeight = titleLineHeight,
                                fontWeight = config.fontWeightOption.weight,
                            ),
                            color = plateFg,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (config.showSubtitle && !isGalleryThumb) {
                            Text(
                                text = subtitle ?: effectiveDesign.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = badgeFontSize),
                                color = plateFg.copy(alpha = 0.58f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        } else {
            // Unified 4-Region Vertical Layout (Used identically in Creation, Cover Studio, Home Grid, Folders, Search & Favourites)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = spineDp + (if (isGalleryThumb) 5.dp else 8.dp),
                        end = pageRightDp + (if (isGalleryThumb) 5.dp else 8.dp),
                        bottom = pageBottomDp,
                    ),
            ) {
                // REGION 1 (0% .. 19%): Top "NOTES · <CATEGORY>" Header Band
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.19f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = topHeaderLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = badgeFontSize, letterSpacing = 0.75.sp),
                        fontWeight = FontWeight.Bold,
                        color = topHeaderColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(if (isGalleryThumb) 3.5.dp else 4.5.dp)
                            .clip(CircleShape)
                            .background(topHeaderColor.copy(alpha = 0.65f)),
                    )
                }

                // REGION 2 (19% .. 50%): Dedicated Notebook Title Region (Never overlaps Artwork Region below!)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.38f)
                        .padding(top = if (isGalleryThumb) 2.dp else 4.dp),
                    contentAlignment = when (config.titlePosition) {
                        CoverTitlePosition.CENTRE, CoverTitlePosition.LOWER -> Alignment.CenterStart
                        CoverTitlePosition.UPPER -> Alignment.TopStart
                    },
                ) {
                    Text(
                        text = cleanTitle,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = fontFamily,
                            fontSize = scaledTitleSize,
                            lineHeight = titleLineHeight,
                            fontWeight = config.fontWeightOption.weight,
                        ),
                        color = fgColor,
                        textAlign = textAlign,
                        maxLines = if (isGalleryThumb) 2 else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // REGION 3 (50% .. 82%): Dedicated Unobstructed Artwork Showcase Space
                Spacer(modifier = Modifier.weight(1f))

                // REGION 4 (82% .. 100%): Bottom Subtitle & Metadata Region
                if (config.showSubtitle) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (isGalleryThumb) 5.dp else 8.dp),
                        horizontalAlignment = horizontalAlignment,
                    ) {
                        Box(
                            Modifier
                                .width(if (isGalleryThumb) 14.dp else 22.dp)
                                .height(0.75.dp)
                                .background(accentColor.copy(alpha = 0.40f)),
                        )
                        Spacer(Modifier.height(if (isGalleryThumb) 2.dp else 3.dp))
                        Text(
                            text = subtitle ?: effectiveDesign.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = badgeFontSize),
                            color = fgColor.copy(alpha = 0.68f),
                            textAlign = textAlign,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Spacer(Modifier.height(if (isGalleryThumb) 5.dp else 8.dp))
                }
            }
        }
    }
}

/**
 * Requirement 4 — Reusable Settings Card for `Settings -> Appearance & Customisation -> Notebook Display Style`.
 * Provides a smooth-sliding toggle between `2D Flat Covers` and `3D Realistic Covers` plus a live side-by-side preview
 * of the same notebook in both modes!
 */
@Composable
fun NotebookDisplayModeSettingsCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current
    val currentMode by NoteCoverStore.displayMode.collectAsStateWithLifecycle()

    val cardBg = if (isDark) Color(0xFF1A1C22) else Color(0xFFF3EFE6)
    val borderCol = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFF2C251E).copy(alpha = 0.10f)
    val primaryText = if (isDark) Color(0xFFF6F3EC) else Color(0xFF1C1A17)
    val secondaryText = if (isDark) Color(0xFFA39E93) else Color(0xFF6E665C)
    val accentCol = if (isDark) Color(0xFFD9A668) else Color(0xFF1E3F32)

    val sampleConfig = remember {
        NoteCoverConfig(
            design = NoteCoverDesign.GREEN_CREAM,
            hasCover = true,
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardBg,
        border = BorderStroke(0.85.dp, borderCol),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Notebook Display Style",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = primaryText,
                )
                Text(
                    text = currentMode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                )
            }

            AnimatedGlassSegmentedControl(
                items = NotebookDisplayMode.entries,
                selectedItem = currentMode,
                onSelect = { mode -> NoteCoverStore.setDisplayMode(context, mode) },
                labelProvider = { it.label },
            )

            // Live Side-by-Side 2D vs 3D Preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NotebookDisplayMode.entries.forEach { mode ->
                    val isSelected = currentMode == mode
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { NoteCoverStore.setDisplayMode(context, mode) }
                            .border(
                                width = if (isSelected) 1.6.dp else 0.8.dp,
                                color = if (isSelected) accentCol else borderCol,
                                shape = RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(78.dp)
                                .aspectRatio(0.71f),
                        ) {
                            NoteCoverArtwork(
                                title = "Biology Notes",
                                noteType = NoteType.INK,
                                config = sampleConfig,
                                subtitle = mode.shortLabel,
                                overrideDisplayMode = mode,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = mode.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) primaryText else secondaryText,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawCoverPatternLayer(
    pattern: CoverPatternKind,
    w: Float,
    h: Float,
    spineX: Float,
    fgColor: Color,
    accentColor: Color,
    compact: Boolean,
) {
    when (pattern) {
        CoverPatternKind.DEFAULT, CoverPatternKind.SOLID -> Unit
        CoverPatternKind.GRADIENT -> {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        accentColor.copy(alpha = 0.18f),
                    ),
                    startY = 0f,
                    endY = h,
                ),
                topLeft = Offset(spineX, 0f),
                size = Size(w - spineX, h),
            )
        }
        CoverPatternKind.GRID -> {
            val step = if (compact) 11.dp.toPx() else 15.dp.toPx()
            val gCol = fgColor.copy(alpha = 0.06f)
            var x = spineX + step
            while (x < w) {
                drawLine(gCol, Offset(x, 0f), Offset(x, h), strokeWidth = 0.6.dp.toPx())
                x += step
            }
            var y = step
            while (y < h) {
                drawLine(gCol, Offset(spineX, y), Offset(w, y), strokeWidth = 0.6.dp.toPx())
                y += step
            }
        }
        CoverPatternKind.LINES -> {
            val step = if (compact) 9.dp.toPx() else 12.dp.toPx()
            val lCol = fgColor.copy(alpha = 0.065f)
            var y = h * 0.22f
            while (y < h - 8.dp.toPx()) {
                drawLine(lCol, Offset(spineX + 4.dp.toPx(), y), Offset(w - 4.dp.toPx(), y), strokeWidth = 0.6.dp.toPx())
                y += step
            }
        }
        CoverPatternKind.DOTS -> {
            val step = if (compact) 10.dp.toPx() else 13.dp.toPx()
            val dotCol = fgColor.copy(alpha = 0.13f)
            var y = step
            while (y < h - step * 0.5f) {
                var x = spineX + step * 0.7f
                while (x < w - step * 0.5f) {
                    drawCircle(dotCol, radius = 0.95.dp.toPx(), center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        CoverPatternKind.GEOMETRIC -> {
            val gCol = accentColor.copy(alpha = 0.13f)
            val cx = spineX + (w - spineX) * 0.66f
            val cy = h * 0.66f
            val r = (w - spineX) * 0.22f
            drawCircle(gCol, radius = r, center = Offset(cx, cy), style = Stroke(width = 1.0.dp.toPx()))
            drawRect(
                color = gCol,
                topLeft = Offset(cx - r * 0.68f, cy - r * 0.68f),
                size = Size(r * 1.36f, r * 1.36f),
                style = Stroke(width = 0.85.dp.toPx()),
            )
        }
        CoverPatternKind.ABSTRACT -> {
            drawCircle(
                color = accentColor.copy(alpha = 0.13f),
                radius = (w - spineX) * 0.30f,
                center = Offset(w * 0.76f, h * 0.72f),
            )
            drawCircle(
                color = fgColor.copy(alpha = 0.05f),
                radius = (w - spineX) * 0.20f,
                center = Offset(w * 0.44f, h * 0.78f),
            )
        }
        CoverPatternKind.TEXTURE -> {
            val step = if (compact) 6.dp.toPx() else 7.dp.toPx()
            val tCol = fgColor.copy(alpha = 0.03f)
            var y = step
            while (y < h) {
                drawLine(tCol, Offset(spineX, y), Offset(w, y), strokeWidth = 0.5.dp.toPx())
                y += step
            }
        }
    }
}

private fun DrawScope.drawCoverBorderLayer(
    borderStyle: CoverBorderStyle,
    w: Float,
    h: Float,
    spineX: Float,
    fgColor: Color,
    accentColor: Color,
    compact: Boolean,
) {
    val inset = if (compact) 4.dp.toPx() else 6.dp.toPx()
    val left = spineX + inset
    val top = inset
    val bw = (w - left - inset).coerceAtLeast(4f)
    val bh = (h - inset * 2f).coerceAtLeast(4f)
    val col = accentColor.copy(alpha = 0.32f)

    when (borderStyle) {
        CoverBorderStyle.SUBTLE, CoverBorderStyle.NONE -> Unit
        CoverBorderStyle.EDITORIAL_FRAME -> {
            drawRoundRect(
                color = col,
                topLeft = Offset(left, top),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = 0.85.dp.toPx()),
            )
        }
        CoverBorderStyle.DOUBLE_LINE -> {
            drawRoundRect(
                color = col,
                topLeft = Offset(left, top),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = 0.85.dp.toPx()),
            )
            val inner = 2.5.dp.toPx()
            drawRoundRect(
                color = col.copy(alpha = 0.20f),
                topLeft = Offset(left + inner, top + inner),
                size = Size((bw - inner * 2f).coerceAtLeast(2f), (bh - inner * 2f).coerceAtLeast(2f)),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                style = Stroke(width = 0.65.dp.toPx()),
            )
        }
        CoverBorderStyle.CORNER_FOLIO -> {
            val cornerLen = if (compact) 7.dp.toPx() else 12.dp.toPx()
            val sw = 1.1.dp.toPx()
            drawLine(col, Offset(left + bw - cornerLen, top), Offset(left + bw, top), strokeWidth = sw)
            drawLine(col, Offset(left + bw, top), Offset(left + bw, top + cornerLen), strokeWidth = sw)
            drawLine(col, Offset(left + bw - cornerLen, top + bh), Offset(left + bw, top + bh), strokeWidth = sw)
            drawLine(col, Offset(left + bw, top + bh - cornerLen), Offset(left + bw, top + bh), strokeWidth = sw)
        }
    }
}

/**
 * Requirement 3 & 5 — Strictly Non-Overlapping Artwork Renderer.
 * All subject, botanical, and creative illustrations are positioned inside the lower-middle
 * Artwork Region (`cy` between `0.64f * h` and `0.70f * h`, `cx` at `spineX + usableW * 0.68f`),
 * completely below the Title Region (`19% .. 48%` of height) and above the Bottom Metadata Region (`84% .. 100%`)!
 */
private fun DrawScope.drawCoverIllustrationLayer(
    illustration: CoverIllustrationKind,
    position: CoverIllustrationPosition,
    w: Float,
    h: Float,
    spineX: Float,
    fgColor: Color,
    accentColor: Color,
    compact: Boolean,
) {
    if (illustration == CoverIllustrationKind.NONE || illustration == CoverIllustrationKind.DEFAULT) return

    val usableW = (w - spineX).coerceAtLeast(10f)
    // Keep illustration strictly inside the Artwork Region (y in 0.54h .. 0.80h) so it NEVER touches the title!
    val cx = when (position) {
        CoverIllustrationPosition.CENTRE, CoverIllustrationPosition.WATERMARK -> spineX + usableW * 0.54f
        CoverIllustrationPosition.UPPER_RIGHT, CoverIllustrationPosition.BOTTOM_RIGHT -> spineX + usableW * 0.68f
    }
    val cy = when (position) {
        CoverIllustrationPosition.CENTRE, CoverIllustrationPosition.WATERMARK -> h * 0.66f
        CoverIllustrationPosition.UPPER_RIGHT -> h * 0.62f
        CoverIllustrationPosition.BOTTOM_RIGHT -> h * 0.68f
    }
    val scale = if (position == CoverIllustrationPosition.WATERMARK) 1.12f else 0.92f
    val alphaMul = if (position == CoverIllustrationPosition.WATERMARK) 0.55f else 0.90f
    val baseR = (usableW * 0.145f * scale).coerceAtMost(h * 0.115f)

    when (illustration) {
        CoverIllustrationKind.FLOWERS, CoverIllustrationKind.SAKURA -> {
            val petalCount = if (illustration == CoverIllustrationKind.SAKURA) 5 else 6
            for (i in 0 until petalCount) {
                val angle = (i * (360.0 / petalCount)) * PI / 180.0
                val px = cx + (cos(angle) * baseR * 0.62f).toFloat()
                val py = cy + (sin(angle) * baseR * 0.62f).toFloat()
                drawCircle(
                    color = accentColor.copy(alpha = 0.24f * alphaMul),
                    radius = baseR * 0.50f,
                    center = Offset(px, py),
                )
            }
            drawCircle(
                color = accentColor.copy(alpha = 0.48f * alphaMul),
                radius = baseR * 0.22f,
                center = Offset(cx, cy),
            )
        }

        CoverIllustrationKind.LEAVES, CoverIllustrationKind.PLANTS -> {
            val stemCol = accentColor.copy(alpha = 0.34f * alphaMul)
            drawLine(
                color = stemCol,
                start = Offset(cx + baseR * 0.22f, cy + baseR * 1.05f),
                end = Offset(cx - baseR * 0.25f, cy - baseR * 0.95f),
                strokeWidth = 1.3.dp.toPx(),
            )
            for (i in -1..1) {
                val ly = cy + i * baseR * 0.48f
                val lx = cx - i * baseR * 0.10f
                drawOval(
                    color = accentColor.copy(alpha = 0.22f * alphaMul),
                    topLeft = Offset(lx - baseR * 0.60f, ly - baseR * 0.22f),
                    size = Size(baseR * 1.2f, baseR * 0.44f),
                )
            }
        }

        CoverIllustrationKind.MOUNTAIN -> {
            val p1 = Path().apply {
                moveTo(spineX + usableW * 0.12f, h * 0.80f)
                lineTo(spineX + usableW * 0.44f, h * 0.58f)
                lineTo(spineX + usableW * 0.74f, h * 0.80f)
                close()
            }
            val p2 = Path().apply {
                moveTo(spineX + usableW * 0.38f, h * 0.80f)
                lineTo(spineX + usableW * 0.72f, h * 0.54f)
                lineTo(w - 4.dp.toPx(), h * 0.80f)
                close()
            }
            drawPath(p1, accentColor.copy(alpha = 0.18f * alphaMul))
            drawPath(p2, accentColor.copy(alpha = 0.28f * alphaMul))
        }

        CoverIllustrationKind.OCEAN -> {
            val waveCol = accentColor.copy(alpha = 0.26f * alphaMul)
            for (row in 0..2) {
                val baseY = h * (0.62f + row * 0.065f)
                val path = Path()
                path.moveTo(spineX + 8.dp.toPx(), baseY)
                var x = spineX + 8.dp.toPx()
                val step = (usableW - 16.dp.toPx()) / 3f
                while (x < w - 8.dp.toPx()) {
                    path.quadraticTo(
                        x + step * 0.5f,
                        baseY - 4.dp.toPx(),
                        (x + step).coerceAtMost(w - 8.dp.toPx()),
                        baseY,
                    )
                    x += step
                }
                drawPath(path, waveCol, style = Stroke(width = 1.1.dp.toPx()))
            }
        }

        CoverIllustrationKind.COMPUTER_SCIENCE, CoverIllustrationKind.ELECTRONICS -> {
            val cCol = accentColor.copy(alpha = 0.32f * alphaMul)
            val n1 = Offset(cx - baseR * 0.65f, cy - baseR * 0.65f)
            val n2 = Offset(cx + baseR * 0.55f, cy - baseR * 0.65f)
            val n3 = Offset(cx, cy)
            val n4 = Offset(cx + baseR * 0.65f, cy + baseR * 0.68f)
            val n5 = Offset(cx - baseR * 0.50f, cy + baseR * 0.68f)
            drawLine(cCol, n1, n2, strokeWidth = 1.1.dp.toPx())
            drawLine(cCol, n1, n3, strokeWidth = 1.1.dp.toPx())
            drawLine(cCol, n3, n4, strokeWidth = 1.1.dp.toPx())
            drawLine(cCol, n3, n5, strokeWidth = 1.1.dp.toPx())
            listOf(n1, n2, n3, n4, n5).forEach { pt ->
                drawCircle(cCol, radius = 2.4.dp.toPx(), center = pt)
            }
        }

        CoverIllustrationKind.MATHEMATICS, CoverIllustrationKind.ARCHITECTURE -> {
            val cCol = accentColor.copy(alpha = 0.28f * alphaMul)
            drawCircle(cCol, radius = baseR, center = Offset(cx, cy), style = Stroke(width = 1.0.dp.toPx()))
            drawCircle(cCol, radius = baseR * 0.58f, center = Offset(cx, cy), style = Stroke(width = 0.85.dp.toPx()))
            drawLine(cCol, Offset(cx - baseR * 1.15f, cy), Offset(cx + baseR * 1.15f, cy), strokeWidth = 0.75.dp.toPx())
            drawLine(cCol, Offset(cx, cy - baseR * 1.15f), Offset(cx, cy + baseR * 1.15f), strokeWidth = 0.75.dp.toPx())
        }

        CoverIllustrationKind.PHYSICS -> {
            val cCol = accentColor.copy(alpha = 0.28f * alphaMul)
            for (i in 1..2) {
                drawOval(
                    color = cCol,
                    topLeft = Offset(cx - baseR * 1.05f, cy - baseR * (0.38f * i)),
                    size = Size(baseR * 2.1f, baseR * (0.76f * i)),
                    style = Stroke(width = 0.95.dp.toPx()),
                )
            }
            drawCircle(accentColor.copy(alpha = 0.55f * alphaMul), radius = 2.6.dp.toPx(), center = Offset(cx, cy))
        }

        CoverIllustrationKind.CHEMISTRY -> {
            val cCol = accentColor.copy(alpha = 0.30f * alphaMul)
            val path = Path()
            for (i in 0..6) {
                val a = (i * 60.0 - 30.0) * PI / 180.0
                val px = cx + (cos(a) * baseR).toFloat()
                val py = cy + (sin(a) * baseR).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, cCol, style = Stroke(width = 1.2.dp.toPx()))
            drawCircle(cCol, radius = baseR * 0.50f, center = Offset(cx, cy), style = Stroke(width = 0.85.dp.toPx()))
        }

        CoverIllustrationKind.BIOLOGY -> {
            val cCol = accentColor.copy(alpha = 0.28f * alphaMul)
            for (i in -2..2) {
                val y = cy + i * (baseR * 0.38f)
                val off = sin(i * 0.95) * (baseR * 0.50f)
                val x1 = (cx + off).toFloat()
                val x2 = (cx - off).toFloat()
                drawLine(cCol, Offset(x1, y), Offset(x2, y), strokeWidth = 0.95.dp.toPx())
                drawCircle(cCol, radius = 2.0.dp.toPx(), center = Offset(x1, y))
                drawCircle(cCol, radius = 2.0.dp.toPx(), center = Offset(x2, y))
            }
        }

        CoverIllustrationKind.ASTRONOMY -> {
            val sCol = accentColor.copy(alpha = 0.38f * alphaMul)
            // Ringed planet + constellation stars in Artwork Region
            drawCircle(sCol.copy(alpha = 0.32f), radius = baseR * 0.46f, center = Offset(cx, cy))
            drawOval(
                color = sCol,
                topLeft = Offset(cx - baseR * 0.95f, cy - baseR * 0.22f),
                size = Size(baseR * 1.9f, baseR * 0.44f),
                style = Stroke(width = 1.0.dp.toPx()),
            )
            drawCircle(sCol, radius = 1.8.dp.toPx(), center = Offset(cx - baseR * 0.75f, cy - baseR * 0.65f))
            drawCircle(sCol, radius = 1.6.dp.toPx(), center = Offset(cx + baseR * 0.70f, cy - baseR * 0.55f))
        }

        CoverIllustrationKind.STATISTICS -> {
            val bCol = accentColor.copy(alpha = 0.26f * alphaMul)
            val heights = listOf(0.45f, 0.75f, 0.58f, 0.95f)
            val barW = baseR * 0.34f
            heights.forEachIndexed { idx, hf ->
                val bx = cx - baseR * 0.80f + idx * (barW * 1.35f)
                val bh = baseR * 1.25f * hf
                drawRoundRect(
                    color = bCol,
                    topLeft = Offset(bx, cy + baseR * 0.65f - bh),
                    size = Size(barW, bh),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            }
        }

        CoverIllustrationKind.SKETCH, CoverIllustrationKind.QUILL -> {
            val cCol = accentColor.copy(alpha = 0.28f * alphaMul)
            val p = Path().apply {
                moveTo(cx - baseR * 0.65f, cy + baseR * 0.65f)
                quadraticTo(cx - baseR * 0.1f, cy - baseR * 0.75f, cx + baseR * 0.75f, cy - baseR * 0.68f)
                quadraticTo(cx + baseR * 0.2f, cy + baseR * 0.18f, cx - baseR * 0.65f, cy + baseR * 0.65f)
                close()
            }
            drawPath(p, cCol, style = Stroke(width = 1.15.dp.toPx()))
        }

        CoverIllustrationKind.MUSIC -> {
            val cCol = accentColor.copy(alpha = 0.30f * alphaMul)
            drawCircle(cCol, radius = baseR * 0.28f, center = Offset(cx - baseR * 0.28f, cy + baseR * 0.36f))
            drawCircle(cCol, radius = baseR * 0.28f, center = Offset(cx + baseR * 0.42f, cy + baseR * 0.22f))
            drawLine(cCol, Offset(cx - baseR * 0.02f, cy + baseR * 0.36f), Offset(cx - baseR * 0.02f, cy - baseR * 0.58f), strokeWidth = 1.25.dp.toPx())
            drawLine(cCol, Offset(cx + baseR * 0.68f, cy + baseR * 0.22f), Offset(cx + baseR * 0.68f, cy - baseR * 0.72f), strokeWidth = 1.25.dp.toPx())
            drawLine(cCol, Offset(cx - baseR * 0.02f, cy - baseR * 0.58f), Offset(cx + baseR * 0.68f, cy - baseR * 0.72f), strokeWidth = 2.1.dp.toPx())
        }

        CoverIllustrationKind.COMPASS -> {
            val cCol = accentColor.copy(alpha = 0.28f * alphaMul)
            drawCircle(cCol, radius = baseR * 0.92f, center = Offset(cx, cy), style = Stroke(width = 1.0.dp.toPx()))
            val diamond = Path().apply {
                moveTo(cx, cy - baseR * 0.78f)
                lineTo(cx + baseR * 0.25f, cy)
                lineTo(cx, cy + baseR * 0.78f)
                lineTo(cx - baseR * 0.25f, cy)
                close()
            }
            drawPath(diamond, cCol)
        }

        CoverIllustrationKind.CAMERA -> {
            val cCol = accentColor.copy(alpha = 0.28f * alphaMul)
            drawRoundRect(
                color = cCol,
                topLeft = Offset(cx - baseR * 0.82f, cy - baseR * 0.54f),
                size = Size(baseR * 1.64f, baseR * 1.08f),
                cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx()),
                style = Stroke(width = 1.1.dp.toPx()),
            )
            drawCircle(cCol, radius = baseR * 0.34f, center = Offset(cx, cy), style = Stroke(width = 1.1.dp.toPx()))
        }
    }
}

@Composable
fun NoteCoverPickerDialog(
    noteTitle: String,
    noteType: NoteType,
    initialConfig: NoteCoverConfig,
    onApply: (NoteCoverConfig) -> Unit,
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

    var selectedDesign by rememberSaveable { mutableStateOf(initialConfig.design) }
    var selectedColor by rememberSaveable { mutableStateOf(initialConfig.colorPreset) }
    var selectedAccent by rememberSaveable { mutableStateOf(initialConfig.accentChoice) }
    var selectedTextColor by rememberSaveable { mutableStateOf(initialConfig.textColorChoice) }
    var selectedPattern by rememberSaveable { mutableStateOf(initialConfig.patternKind) }
    var selectedIllustration by rememberSaveable { mutableStateOf(initialConfig.illustrationKind) }
    var selectedSpineStyle by rememberSaveable { mutableStateOf(initialConfig.spineStyle) }
    var selectedBorderStyle by rememberSaveable { mutableStateOf(initialConfig.borderStyle) }
    var selectedTitlePos by rememberSaveable { mutableStateOf(initialConfig.titlePosition) }
    var selectedIllusPos by rememberSaveable { mutableStateOf(initialConfig.illustrationPosition) }
    var selectedProportion by rememberSaveable { mutableStateOf(initialConfig.proportionPreset) }
    var selectedFontStyle by rememberSaveable { mutableStateOf(initialConfig.fontStyle) }
    var selectedFontWeight by rememberSaveable { mutableStateOf(initialConfig.fontWeightOption) }
    var selectedTextAlign by rememberSaveable { mutableStateOf(initialConfig.textAlignOption) }
    var selectedTitleScale by rememberSaveable { mutableStateOf(initialConfig.titleScaleOption) }
    var showSubtitle by rememberSaveable { mutableStateOf(initialConfig.showSubtitle) }

    var categoryFilter by rememberSaveable { mutableStateOf(CoverCategory.ALL) }
    var activeTab by rememberSaveable { mutableStateOf(CoverStudioTab.COLOUR) }
    var expandedPreviewMode by rememberSaveable { mutableStateOf(value = false) }

    val displayTitle = noteTitle.trim().ifBlank {
        when (noteType) {
            NoteType.INK -> "Handwritten Notebook"
            NoteType.PDF -> "PDF Document"
            NoteType.TEXT -> "Typed Note"
        }
    }

    val currentPreviewConfig = NoteCoverConfig(
        design = selectedDesign,
        colorPreset = selectedColor,
        hasCover = selectedDesign != NoteCoverDesign.CLEAN_PAPER,
        accentChoice = selectedAccent,
        textColorChoice = selectedTextColor,
        patternKind = selectedPattern,
        illustrationKind = selectedIllustration,
        spineStyle = selectedSpineStyle,
        borderStyle = selectedBorderStyle,
        titlePosition = selectedTitlePos,
        illustrationPosition = selectedIllusPos,
        proportionPreset = selectedProportion,
        fontStyle = selectedFontStyle,
        fontWeightOption = selectedFontWeight,
        textAlignOption = selectedTextAlign,
        titleScaleOption = selectedTitleScale,
        showSubtitle = showSubtitle,
    )

    val previewScale by animateFloatAsState(
        targetValue = if (expandedPreviewMode) 1.06f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "studioPreviewScale",
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
                .widthIn(max = 620.dp)
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
                // 1. Studio Header: Close (✕) | Title | Preview Button | Apply (✓)
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

                    Text(
                        text = if (noteType == NoteType.TEXT) "Note Cover Studio" else "Notebook Cover Studio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        textAlign = TextAlign.Center,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (expandedPreviewMode) accentCol.copy(alpha = 0.16f) else cardBg,
                            border = BorderStroke(0.8.dp, if (expandedPreviewMode) accentCol else borderCol),
                            modifier = Modifier
                                .height(38.dp)
                                .clip(CircleShape)
                                .clickable { expandedPreviewMode = !expandedPreviewMode },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Visibility,
                                    contentDescription = "Preview",
                                    tint = primaryText,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = if (expandedPreviewMode) "Studio" else "Preview",
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
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Apply Cover",
                                    tint = accentContentCol,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                // 2. Large Live 3D Hardcover Notebook Preview
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (noteType == NoteType.TEXT) "LIVE NOTE COVER PREVIEW" else "LIVE NOTEBOOK COVER PREVIEW",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.0.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = secondaryText,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = previewScale
                                scaleY = previewScale
                            }
                            .width(if (expandedPreviewMode) 196.dp else 148.dp)
                            .aspectRatio(currentPreviewConfig.aspectRatioValue),
                        contentAlignment = Alignment.Center,
                    ) {
                        NoteCoverArtwork(
                            title = displayTitle,
                            noteType = noteType,
                            config = currentPreviewConfig,
                            subtitle = selectedDesign.label,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${selectedDesign.label} · ${selectedDesign.category.label}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = secondaryText,
                        maxLines = 1,
                    )
                }

                if (!expandedPreviewMode) {
                    // 3. Horizontally Scrollable Cover Categories (Zero Truncation!)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverCategory.entries.forEach { cat ->
                            val selected = categoryFilter == cat
                            Surface(
                                shape = CircleShape,
                                color = if (selected) accentCol else cardBg,
                                border = BorderStroke(0.8.dp, if (selected) accentCol else borderCol),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        categoryFilter = cat
                                        if (cat == CoverCategory.CUSTOM) {
                                            activeTab = CoverStudioTab.COLOUR
                                        }
                                    },
                            ) {
                                Text(
                                    text = cat.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) accentContentCol else primaryText,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }

                    // 4. Horizontally Scrollable Cover Gallery (Each card renders its own authentic palette & complete label!)
                    val visibleDesigns = remember(categoryFilter) {
                        when (categoryFilter) {
                            CoverCategory.ALL, CoverCategory.CUSTOM -> NoteCoverDesign.entries
                            else -> NoteCoverDesign.entries.filter { it.category == categoryFilter }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        visibleDesigns.forEach { design ->
                            val isSelected = selectedDesign == design
                            val galleryItemConfig = remember(design) {
                                NoteCoverConfig(design = design, colorPreset = CoverColorPreset.DEFAULT)
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(114.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        selectedDesign = design
                                        selectedColor = CoverColorPreset.DEFAULT
                                        selectedAccent = CoverAccentChoice.DEFAULT
                                        selectedTextColor = CoverTextColorChoice.DEFAULT
                                    }
                                    .padding(vertical = 4.dp, horizontal = 3.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(98.dp)
                                        .aspectRatio(0.71f)
                                        .border(
                                            width = if (isSelected) 2.2.dp else 0.dp,
                                            color = if (isSelected) accentCol else Color.Transparent,
                                            shape = RoundedCornerShape(
                                                topStart = 5.dp,
                                                bottomStart = 5.dp,
                                                topEnd = 13.dp,
                                                bottomEnd = 13.dp,
                                            ),
                                        )
                                        .padding(if (isSelected) 2.dp else 0.dp),
                                ) {
                                    NoteCoverArtwork(
                                        title = displayTitle,
                                        noteType = noteType,
                                        config = galleryItemConfig,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = design.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) primaryText else secondaryText,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = borderCol)

                    // 5. 5-Tab Customization Section (Colour | Pattern | Illustration | Layout | Typography)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CoverStudioTab.entries.forEach { tab ->
                            val isTabSelected = activeTab == tab
                            Surface(
                                shape = CircleShape,
                                color = if (isTabSelected) primaryText.copy(alpha = 0.12f) else Color.Transparent,
                                border = BorderStroke(0.8.dp, if (isTabSelected) primaryText.copy(alpha = 0.35f) else borderCol),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { activeTab = tab },
                            ) {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isTabSelected) primaryText else secondaryText,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }

                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                        label = "coverStudioTabContent",
                    ) { tab ->
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
                                when (tab) {
                                    CoverStudioTab.COLOUR -> {
                                        Text("Cover Colour Palette", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = primaryText)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            CoverColorPreset.entries.forEach { preset ->
                                                val selected = selectedColor == preset
                                                val swatchColor = preset.bgHex?.let { Color(it) }
                                                    ?: if (isDark) Color(selectedDesign.defaultBgDark) else Color(selectedDesign.defaultBgLight)
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null,
                                                        ) { selectedColor = preset }
                                                        .padding(horizontal = 5.dp, vertical = 3.dp),
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .clip(CircleShape)
                                                            .background(swatchColor, CircleShape)
                                                            .border(
                                                                width = if (selected) 2.dp else 1.dp,
                                                                color = if (selected) accentCol else borderCol,
                                                                shape = CircleShape,
                                                            ),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        if (selected) {
                                                            Icon(
                                                                Icons.Filled.Check,
                                                                contentDescription = null,
                                                                tint = preset.fgHex?.let { Color(it) } ?: primaryText,
                                                                modifier = Modifier.size(15.dp),
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.height(3.dp))
                                                    Text(preset.label, style = MaterialTheme.typography.labelSmall, color = if (selected) primaryText else secondaryText, maxLines = 1)
                                                }
                                            }
                                        }

                                        Text("Accent Colour", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = primaryText)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CoverAccentChoice.entries.forEach { acc ->
                                                val sel = selectedAccent == acc
                                                StudioOptionChip(
                                                    label = acc.label,
                                                    selected = sel,
                                                    dotColor = acc.hex?.let { Color(it) },
                                                    primaryText = primaryText,
                                                    secondaryText = secondaryText,
                                                    borderCol = borderCol,
                                                    accentCol = accentCol,
                                                ) { selectedAccent = acc }
                                            }
                                        }

                                        Text("Title & Text Colour", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = primaryText)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CoverTextColorChoice.entries.forEach { tc ->
                                                val sel = selectedTextColor == tc
                                                StudioOptionChip(
                                                    label = tc.label,
                                                    selected = sel,
                                                    dotColor = tc.hex?.let { Color(it) },
                                                    primaryText = primaryText,
                                                    secondaryText = secondaryText,
                                                    borderCol = borderCol,
                                                    accentCol = accentCol,
                                                    onClick = { selectedTextColor = tc },
                                                )
                                            }
                                        }
                                    }

                                    CoverStudioTab.PATTERN -> {
                                        Text("Surface Pattern & Texture", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = primaryText)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CoverPatternKind.entries.forEach { pat ->
                                                StudioOptionChip(
                                                    label = pat.label,
                                                    selected = selectedPattern == pat,
                                                    primaryText = primaryText,
                                                    secondaryText = secondaryText,
                                                    borderCol = borderCol,
                                                    accentCol = accentCol,
                                                    onClick = { selectedPattern = pat },
                                                )
                                            }
                                        }
                                    }

                                    CoverStudioTab.ILLUSTRATION -> {
                                        Text("Subject & Botanical Illustration", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = primaryText)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CoverIllustrationKind.entries.forEach { ill ->
                                                StudioOptionChip(
                                                    label = ill.label,
                                                    selected = selectedIllustration == ill,
                                                    primaryText = primaryText,
                                                    secondaryText = secondaryText,
                                                    borderCol = borderCol,
                                                    accentCol = accentCol,
                                                    onClick = { selectedIllustration = ill },
                                                )
                                            }
                                        }
                                        Text("Illustration Placement", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = primaryText)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CoverIllustrationPosition.entries.forEach { pos ->
                                                StudioOptionChip(
                                                    label = pos.label,
                                                    selected = selectedIllusPos == pos,
                                                    primaryText = primaryText,
                                                    secondaryText = secondaryText,
                                                    borderCol = borderCol,
                                                    accentCol = accentCol,
                                                    onClick = { selectedIllusPos = pos },
                                                )
                                            }
                                        }
                                    }

                                    CoverStudioTab.LAYOUT -> {
                                        Text("Spine Binding Style", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = primaryText)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CoverSpineStyle.entries.forEach { sp ->
                                                StudioOptionChip(
                                                    label = sp.label,
                                                    selected = selectedSpineStyle == sp,
                                                    primaryText = primaryText,
                                                    secondaryText = secondaryText,
                                                    borderCol = borderCol,
                                                    accentCol = accentCol,
                                                    onClick = { selectedSpineStyle = sp },
                                                )
                                            }
                                        }
                                        Text("Cover Frame & Border", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = primaryText)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CoverBorderStyle.entries.forEach { bd ->
                                                StudioOptionChip(
                                                    label = bd.label,
                                                    selected = selectedBorderStyle == bd,
                                                    primaryText = primaryText,
                                                    secondaryText = secondaryText,
                                                    borderCol = borderCol,
                                                    accentCol = accentCol,
                                                    onClick = { selectedBorderStyle = bd },
                                                )
                                            }
                                        }
                                        Text("Title Vertical Position", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = primaryText)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CoverTitlePosition.entries.forEach { tp ->
                                                StudioOptionChip(
                                                    label = tp.label,
                                                    selected = selectedTitlePos == tp,
                                                    primaryText = primaryText,
                                                    secondaryText = secondaryText,
                                                    borderCol = borderCol,
                                                    accentCol = accentCol,
                                                    onClick = { selectedTitlePos = tp },
                                                )
                                            }
                                        }
                                        Text("Notebook Proportions", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = primaryText)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CoverProportionPreset.entries.forEach { pr ->
                                                StudioOptionChip(
                                                    label = pr.label,
                                                    selected = selectedProportion == pr,
                                                    primaryText = primaryText,
                                                    secondaryText = secondaryText,
                                                    borderCol = borderCol,
                                                    accentCol = accentCol,
                                                    onClick = { selectedProportion = pr },
                                                )
                                            }
                                        }
                                    }

                                    CoverStudioTab.TYPOGRAPHY -> {
                                        Text("Font Family", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = primaryText)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CoverFontStyle.entries.forEach { fs ->
                                                StudioOptionChip(
                                                    label = fs.label,
                                                    selected = selectedFontStyle == fs,
                                                    primaryText = primaryText,
                                                    secondaryText = secondaryText,
                                                    borderCol = borderCol,
                                                    accentCol = accentCol,
                                                    onClick = { selectedFontStyle = fs },
                                                )
                                            }
                                        }
                                        Text("Font Weight", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = primaryText)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CoverFontWeightOption.entries.forEach { fw ->
                                                StudioOptionChip(
                                                    label = fw.label,
                                                    selected = selectedFontWeight == fw,
                                                    primaryText = primaryText,
                                                    secondaryText = secondaryText,
                                                    borderCol = borderCol,
                                                    accentCol = accentCol,
                                                    onClick = { selectedFontWeight = fw },
                                                )
                                            }
                                        }
                                        Text("Title Alignment & Scale", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = primaryText)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CoverTextAlignOption.entries.forEach { ta ->
                                                StudioOptionChip(
                                                    label = ta.label,
                                                    selected = selectedTextAlign == ta,
                                                    primaryText = primaryText,
                                                    secondaryText = secondaryText,
                                                    borderCol = borderCol,
                                                    accentCol = accentCol,
                                                    onClick = { selectedTextAlign = ta },
                                                )
                                            }
                                            CoverTitleScaleOption.entries.forEach { ts ->
                                                StudioOptionChip(
                                                    label = ts.label,
                                                    selected = selectedTitleScale == ts,
                                                    primaryText = primaryText,
                                                    secondaryText = secondaryText,
                                                    borderCol = borderCol,
                                                    accentCol = accentCol,
                                                    onClick = { selectedTitleScale = ts },
                                                )
                                            }
                                        }
                                        Text("Footer Metadata Visibility", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = primaryText)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            StudioOptionChip(
                                                label = "Show Subtitle & Date",
                                                selected = showSubtitle,
                                                primaryText = primaryText,
                                                secondaryText = secondaryText,
                                                borderCol = borderCol,
                                                accentCol = accentCol,
                                                onClick = { showSubtitle = true },
                                            )
                                            StudioOptionChip(
                                                label = "Title & NOTES Only",
                                                selected = !showSubtitle,
                                                primaryText = primaryText,
                                                secondaryText = secondaryText,
                                                borderCol = borderCol,
                                                accentCol = accentCol,
                                                onClick = { showSubtitle = false },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = borderCol)

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
                            text = "Apply Cover",
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
private fun StudioOptionChip(
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

typealias NoteCover = NoteCoverConfig
typealias NotebookCover = NoteCoverConfig
typealias FolderCover = FolderCoverConfig

@Composable
fun NoteCoverRenderer(
    title: String,
    config: NoteCoverConfig,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isLocked: Boolean = false,
    compact: Boolean = false,
    overrideDisplayMode: NotebookDisplayMode? = null,
) {
    NoteCoverArtwork(
        title = title,
        noteType = NoteType.TEXT,
        config = config,
        modifier = modifier,
        subtitle = subtitle,
        isLocked = isLocked,
        compact = compact,
        overrideDisplayMode = overrideDisplayMode,
    )
}

@Composable
fun NotebookCoverRenderer(
    title: String,
    config: NoteCoverConfig,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isLocked: Boolean = false,
    compact: Boolean = false,
    overrideDisplayMode: NotebookDisplayMode? = null,
) {
    NoteCoverArtwork(
        title = title,
        noteType = NoteType.INK,
        config = config,
        modifier = modifier,
        subtitle = subtitle,
        isLocked = isLocked,
        compact = compact,
        overrideDisplayMode = overrideDisplayMode,
    )
}
