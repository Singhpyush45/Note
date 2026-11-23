package com.appname.notes.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import com.appname.notes.core.domain.model.NoteType
import com.appname.notes.ui.common.CoverCollection
import com.appname.notes.ui.common.CoverColorPreset
import com.appname.notes.ui.common.NoteCoverArtwork
import com.appname.notes.ui.common.NoteCoverConfig
import com.appname.notes.ui.common.NoteCoverDesign
import com.appname.notes.ui.common.NoteCoverPickerDialog
import com.appname.notes.ui.common.NoteCoverStore
import com.appname.notes.core.ink.layout.PageArrangement
import com.appname.notes.core.ink.layout.PageMode
import com.appname.notes.core.ink.layout.ScrollAxis
import com.appname.notes.core.ink.model.InkBrush
import com.appname.notes.core.ink.model.InkItem
import com.appname.notes.core.ink.model.InkTool
import com.appname.notes.core.ink.model.Placement
import com.appname.notes.core.ink.model.Pt
import com.appname.notes.core.ink.model.ShapeGeometry
import com.appname.notes.core.ink.model.ShapeItem
import com.appname.notes.core.ink.model.ShapeKind
import com.appname.notes.core.ink.model.TextAlign as InkTextAlign
import com.appname.notes.core.ink.model.TextFont
import com.appname.notes.core.ink.model.TextItem
import com.appname.notes.core.ink.template.BuiltInTemplates
import com.appname.notes.core.ink.template.HeaderStyle
import com.appname.notes.core.ink.template.PageDesign
import com.appname.notes.core.ink.template.PageFormat
import com.appname.notes.core.ink.template.Ruling
import com.appname.notes.core.ink.template.SpacingPreset
import com.appname.notes.core.ink.template.TemplateDraft
import com.appname.notes.core.ink.template.TemplateLayout

enum class WhiteboardTemplateKind(val title: String, val category: String) {
    BLANK("Dot Grid", "Blank"),
    KWL_CHART("KWL Chart", "Education"),
    STUDY_BOARD("Study Board", "Education"),
    BRAINSTORMING("Brainstorming", "Brainstorming"),
    BRAINSTORMING_LARGE("Brainstorming (Large)", "Brainstorming"),
    CRAZY_8S("Crazy 8s", "Brainstorming"),
    IDEA_POSTER("Idea Poster", "Brainstorming"),
    SPIDER_MAP("Spider Map", "Brainstorming"),
    VENN_2("2-Venn Diagram", "Diagramming"),
    VENN_3("3-Venn Diagram", "Diagramming"),
    CONCEPT_MAP("Concept Map", "Diagramming"),
    FLOWCHART("Flowchart", "Diagramming"),
    DAILY_STANDUP("Daily Standup", "Collaboration"),
    ICEBREAKER("Icebreaker", "Collaboration"),
    RETROSPECTIVE("Retrospective", "Collaboration"),
    TEAM_MEETING("Team Meeting", "Collaboration"),
    PROJECT_PLANNING("Project Planning", "Planning"),
    SWOT_ANALYSIS("SWOT Analysis", "Planning"),
}

data class WhiteboardCreationSpec(
    val title: String,
    val ruling: Ruling,
    val paperColor: Int,
    val template: WhiteboardTemplateKind,
) {
    fun toLayout(): TemplateLayout = TemplateDraft(
        format = PageFormat.A4_LANDSCAPE,
        ruling = ruling,
        spacing = PageDesign.spacing(ruling, SpacingPreset.NORMAL),
        paperColor = paperColor,
        margin = false,
        header = HeaderStyle.NONE,
    ).toLayout()

    val arrangement: PageArrangement
        get() = PageArrangement(mode = PageMode.INFINITE, axis = ScrollAxis.VERTICAL)

    fun buildInitialItems(): List<InkItem> {
        val items = mutableListOf<InkItem>()
        var z = 1L
        fun rect(x: Float, y: Float, w: Float, h: Float, strokeColor: Int, fillColor: Int? = null) {
            items += ShapeItem(
                id = "wb_s_$z",
                z = z++,
                brush = InkBrush(InkTool.PEN, strokeColor, 2.5f),
                kind = ShapeKind.RECTANGLE,
                geometry = ShapeGeometry.Polygon(listOf(Pt(x, y), Pt(x + w, y), Pt(x + w, y + h), Pt(x, y + h))),
                source = null,
                fill = fillColor,
            )
        }
        fun circle(cx: Float, cy: Float, rx: Float, ry: Float, strokeColor: Int, fillColor: Int? = null) {
            items += ShapeItem(
                id = "wb_c_$z",
                z = z++,
                brush = InkBrush(InkTool.PEN, strokeColor, 2.8f),
                kind = if (rx == ry) ShapeKind.CIRCLE else ShapeKind.ELLIPSE,
                geometry = ShapeGeometry.Ellipse(Pt(cx, cy), rx, ry, 0f),
                source = null,
                fill = fillColor,
            )
        }
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, color: Int, arrow: Boolean = false) {
            items += ShapeItem(
                id = "wb_l_$z",
                z = z++,
                brush = InkBrush(InkTool.PEN, color, 2.5f),
                kind = if (arrow) ShapeKind.ARROW else ShapeKind.LINE,
                geometry = ShapeGeometry.Segment(Pt(x0, y0), Pt(x1, y1), if (arrow) 14f else 0f),
                source = null,
            )
        }
        fun label(text: String, cx: Float, cy: Float, w: Float, color: Int, size: Float = 20f, bold: Boolean = true) {
            items += TextItem(
                id = "wb_t_$z",
                z = z++,
                brush = InkBrush(InkTool.PEN, color, size),
                text = text,
                place = Placement(cx, cy, w, size * 1.6f),
                font = TextFont.SANS,
                bold = bold,
                align = InkTextAlign.CENTER,
            )
        }

        when (template) {
            WhiteboardTemplateKind.BLANK -> Unit
            WhiteboardTemplateKind.KWL_CHART -> {
                rect(80f, 70f, 960f, 70f, 0xFF1F2937.toInt(), 0x181F2937)
                label("KWL CHART — TOPIC", 560f, 105f, 500f, 0xFF111827.toInt(), 22f)
                val cols = listOf(
                    Triple("K — What I Know", 80f, 0xFF334155.toInt() to 0x14334155),
                    Triple("W — What I Want to Know", 410f, 0xFF475569.toInt() to 0x14475569),
                    Triple("L — What I Learned", 740f, 0xFF1E293B.toInt() to 0x141E293B),
                )
                cols.forEach { (t, x, colors) ->
                    rect(x, 165f, 300f, 540f, colors.first, colors.second)
                    label(t, x + 150f, 200f, 270f, colors.first, 18f)
                }
            }
            WhiteboardTemplateKind.STUDY_BOARD -> {
                val cols = listOf(
                    Triple("Key Concepts", 80f, 0xFF334155.toInt() to 0x14334155),
                    Triple("Examples & Formulas", 410f, 0xFF475569.toInt() to 0x14475569),
                    Triple("Summary & Review", 740f, 0xFF1E293B.toInt() to 0x141E293B),
                )
                cols.forEach { (t, x, colors) ->
                    rect(x, 100f, 300f, 590f, colors.first, colors.second)
                    label(t, x + 150f, 140f, 270f, colors.first, 19f)
                }
            }
            WhiteboardTemplateKind.SWOT_ANALYSIS -> {
                val quads = listOf(
                    Triple("S — Strengths", Offset(100f, 90f), 0xFF1E293B.toInt() to 0x141E293B),
                    Triple("W — Weaknesses", Offset(570f, 90f), 0xFF334155.toInt() to 0x14334155),
                    Triple("O — Opportunities", Offset(100f, 410f), 0xFF475569.toInt() to 0x14475569),
                    Triple("T — Threats", Offset(570f, 410f), 0xFF0F172A.toInt() to 0x140F172A),
                )
                quads.forEach { (t, pos, colors) ->
                    rect(pos.x, pos.y, 440f, 290f, colors.first, colors.second)
                    label(t, pos.x + 220f, pos.y + 36f, 380f, colors.first, 20f)
                }
            }
            WhiteboardTemplateKind.VENN_2 -> {
                circle(430f, 390f, 220f, 220f, 0xFF334155.toInt(), 0x18334155)
                circle(690f, 390f, 220f, 220f, 0xFF64748B.toInt(), 0x1864748B)
                label("Concept A", 330f, 390f, 180f, 0xFF1E293B.toInt(), 18f)
                label("Both", 560f, 390f, 120f, 0xFF0F172A.toInt(), 18f)
                label("Concept B", 790f, 390f, 180f, 0xFF334155.toInt(), 18f)
            }
            WhiteboardTemplateKind.VENN_3 -> {
                circle(450f, 310f, 185f, 185f, 0xFF334155.toInt(), 0x16334155)
                circle(670f, 310f, 185f, 185f, 0xFF475569.toInt(), 0x16475569)
                circle(560f, 490f, 185f, 185f, 0xFF64748B.toInt(), 0x1664748B)
            }
            WhiteboardTemplateKind.CRAZY_8S -> {
                for (r in 0..1) {
                    for (c in 0..3) {
                        val x = 70f + c * 248f
                        val y = 95f + r * 295f
                        rect(x, y, 228f, 265f, 0xFF64748B.toInt(), 0x1264748B)
                        label("#${r * 4 + c + 1}", x + 36f, y + 26f, 60f, 0xFF475569.toInt(), 15f)
                    }
                }
            }
            WhiteboardTemplateKind.PROJECT_PLANNING -> {
                val lanes = listOf(
                    Triple("Phase 1 — Research & Scope", 95f, 0xFF1E293B.toInt() to 0x141E293B),
                    Triple("Phase 2 — Build & Iterate", 295f, 0xFF334155.toInt() to 0x14334155),
                    Triple("Phase 3 — Launch & Review", 495f, 0xFF475569.toInt() to 0x14475569),
                )
                lanes.forEach { (t, y, colors) ->
                    rect(80f, y, 960f, 170f, colors.first, colors.second)
                    label(t, 260f, y + 32f, 340f, colors.first, 18f)
                }
            }
            WhiteboardTemplateKind.CONCEPT_MAP, WhiteboardTemplateKind.SPIDER_MAP, WhiteboardTemplateKind.FLOWCHART -> {
                rect(430f, 80f, 260f, 76f, 0xFF1E293B.toInt(), 0x181E293B)
                label("Main Topic", 560f, 118f, 220f, 0xFF0F172A.toInt(), 19f)
                val subXs = listOf(140f, 450f, 760f)
                subXs.forEachIndexed { idx, sx ->
                    line(560f, 156f, sx + 110f, 280f, 0xFF64748B.toInt(), arrow = true)
                    rect(sx, 280f, 220f, 80f, 0xFF334155.toInt(), 0x14334155)
                    label("Subtopic ${idx + 1}", sx + 110f, 320f, 190f, 0xFF1E293B.toInt(), 16f)
                    line(sx + 110f, 360f, sx + 110f, 460f, 0xFF94A3B8.toInt(), arrow = true)
                    rect(sx, 460f, 220f, 160f, 0xFF475569.toInt(), 0x10475569)
                }
            }
            else -> {
                val cols = listOf(
                    Triple("Column 1", 80f, 0xFF1E293B.toInt() to 0x141E293B),
                    Triple("Column 2", 410f, 0xFF334155.toInt() to 0x14334155),
                    Triple("Column 3", 740f, 0xFF475569.toInt() to 0x14475569),
                )
                cols.forEach { (t, x, colors) ->
                    rect(x, 100f, 300f, 580f, colors.first, colors.second)
                    label(t, x + 150f, 138f, 240f, colors.first, 18f)
                }
            }
        }
        return items
    }
}

/**
 * Phase 11 — Neutral Liquid Glass "New Whiteboard" Modal.
 * Pure white (#FFFFFF) in Light Mode, near-black (#101114) in Dark Mode, with full-width
 * neutral glass form fields so labels never wrap or clip.
 */
@Composable
fun NewWhiteboardDialog(
    onCreate: (WhiteboardCreationSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val sheetBg = if (isDark) Color(0xFF101114) else Color(0xFFFFFFFF)
    val cardBg = if (isDark) Color(0xFF1A1C22) else Color(0xFFF4F5F7)
    val borderCol = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.12f)
    val primaryText = if (isDark) Color(0xFFF4F5F7) else Color(0xFF111317)
    val secondaryText = if (isDark) Color(0xFF9CA3AF) else Color(0xFF5A606A)
    val accentCol = if (isDark) Color(0xFFF4F5F7) else Color(0xFF141518)

    var title by rememberSaveable { mutableStateOf("Untitled Whiteboard") }
    var ruling by rememberSaveable { mutableStateOf(Ruling.DOTS) }
    var colorIdx by rememberSaveable { mutableIntStateOf(if (isDark) 1 else 0) }
    var selectedTemplate by rememberSaveable { mutableStateOf(WhiteboardTemplateKind.BLANK) }

    // Genuine White & Near-Black paper options (Phase 2.1: no cream/beige)
    val paperPalette = listOf(
        "Pure White" to 0xFFFFFFFF.toInt(),
        "Near Black" to 0xFF121317.toInt(),
        "Soft Graphite" to 0xFFF3F4F6.toInt(),
    )
    val chosenPaperColor = paperPalette[colorIdx].second

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
            shadowElevation = 18.dp,
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(0.94f)
                .heightIn(max = 740.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header: Close (X) | New Whiteboard | Confirm (✓)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = cardBg,
                        border = BorderStroke(0.8.dp, borderCol),
                        modifier = Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onDismiss),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, contentDescription = null, tint = primaryText, modifier = Modifier.size(20.dp))
                        }
                    }
                    Text(
                        text = "New Whiteboard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText,
                    )
                    Surface(
                        shape = CircleShape,
                        color = accentCol,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable {
                                NoteCoverStore.stagePendingCoverForNewNote(
                                    NoteCoverConfig(
                                        design = NoteCoverDesign.DOT_GRID_BOOKPLATE,
                                        colorPreset = CoverColorPreset.DEFAULT,
                                        hasCover = true,
                                    ),
                                )
                                onCreate(
                                    WhiteboardCreationSpec(
                                        title = title.ifBlank { "Untitled Whiteboard" },
                                        ruling = ruling,
                                        paperColor = chosenPaperColor,
                                        template = selectedTemplate,
                                    ),
                                )
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF101114) else Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // Centered Preview Tile
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("PREVIEW", style = MaterialTheme.typography.labelSmall, color = secondaryText)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 176.dp, height = 116.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(chosenPaperColor))
                            .border(1.5.dp, accentCol, RoundedCornerShape(16.dp))
                            .padding(8.dp),
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawTemplateMiniPreview(selectedTemplate)
                        }
                    }
                }

                // Full-width Neutral Glass Settings Card (never squeezes or clips text)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    border = BorderStroke(0.8.dp, borderCol),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Name", style = MaterialTheme.typography.bodyMedium, color = secondaryText, modifier = Modifier.width(84.dp))
                            BasicTextField(
                                value = title,
                                onValueChange = { title = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = primaryText,
                                    fontWeight = FontWeight.Medium,
                                ),
                                cursorBrush = SolidColor(accentCol),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        HorizontalDivider(color = borderCol)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ruling = when (ruling) {
                                        Ruling.DOTS -> Ruling.GRID
                                        Ruling.GRID -> Ruling.NONE
                                        else -> Ruling.DOTS
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Grid Pattern", style = MaterialTheme.typography.bodyMedium, color = primaryText)
                            Text(
                                text = when (ruling) {
                                    Ruling.DOTS -> "Dot Grid ⌄"
                                    Ruling.GRID -> "Squared Grid ⌄"
                                    else -> "Blank ⌄"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryText,
                            )
                        }
                        HorizontalDivider(color = borderCol)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { colorIdx = (colorIdx + 1) % paperPalette.size }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Canvas Colour", style = MaterialTheme.typography.bodyMedium, color = primaryText)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(chosenPaperColor))
                                        .border(1.dp, primaryText.copy(alpha = 0.55f), CircleShape),
                                )
                                Text("${paperPalette[colorIdx].first} ⌄", style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                            }
                        }
                    }
                }

                HorizontalDivider(color = borderCol)

                Text(
                    text = "Start with a Template",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryText,
                )

                val categories = listOf("Education", "Brainstorming", "Diagramming", "Collaboration", "Planning")
                categories.forEach { category ->
                    val items = WhiteboardTemplateKind.entries.filter { it.category == category }
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = secondaryText,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items.forEach { item ->
                            val isSelected = selectedTemplate == item
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(142.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        selectedTemplate = item
                                        if (title == "Untitled Whiteboard" || WhiteboardTemplateKind.entries.any { it.title == title }) {
                                            title = item.title
                                        }
                                    }
                                    .padding(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 134.dp, height = 94.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF8F9FA))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) accentCol else borderCol,
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                        .padding(8.dp),
                                ) {
                                    Canvas(Modifier.fillMaxSize()) {
                                        drawTemplateMiniPreview(item)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) primaryText else secondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawTemplateMiniPreview(kind: WhiteboardTemplateKind) {
    val w = size.width
    val h = size.height
    when (kind) {
        WhiteboardTemplateKind.BLANK -> {
            for (r in 1..4) {
                for (c in 1..6) {
                    drawCircle(Color(0xFF94A3B8), radius = 1.5f, center = Offset(w * c / 7f, h * r / 5f))
                }
            }
        }
        WhiteboardTemplateKind.KWL_CHART -> {
            drawRoundRect(Color(0xFF1E293B), Offset(0f, 0f), Size(w * 0.62f, h * 0.22f), CornerRadius(4f, 4f))
            drawRoundRect(Color(0xFFCBD5E1), Offset(w * 0.66f, 0f), Size(w * 0.34f, h * 0.22f), CornerRadius(4f, 4f))
            val cols = listOf(Color(0xFF334155), Color(0xFF64748B), Color(0xFF94A3B8))
            cols.forEachIndexed { i, col ->
                val x = i * (w * 0.34f)
                drawRoundRect(col, Offset(x, h * 0.30f), Size(w * 0.30f, h * 0.70f), CornerRadius(4f, 4f))
            }
        }
        WhiteboardTemplateKind.STUDY_BOARD, WhiteboardTemplateKind.TEAM_MEETING -> {
            val cols = listOf(Color(0xFF334155), Color(0xFF64748B), Color(0xFF94A3B8))
            cols.forEachIndexed { i, col ->
                val x = i * (w * 0.34f)
                drawRoundRect(col, Offset(x, h * 0.08f), Size(w * 0.30f, h * 0.88f), CornerRadius(4f, 4f))
            }
        }
        WhiteboardTemplateKind.VENN_2 -> {
            drawCircle(Color(0xFF334155).copy(alpha = 0.65f), radius = h * 0.36f, center = Offset(w * 0.38f, h * 0.5f))
            drawCircle(Color(0xFF64748B).copy(alpha = 0.65f), radius = h * 0.36f, center = Offset(w * 0.62f, h * 0.5f))
        }
        WhiteboardTemplateKind.VENN_3 -> {
            drawCircle(Color(0xFF1E293B).copy(alpha = 0.60f), radius = h * 0.30f, center = Offset(w * 0.40f, h * 0.38f))
            drawCircle(Color(0xFF475569).copy(alpha = 0.60f), radius = h * 0.30f, center = Offset(w * 0.60f, h * 0.38f))
            drawCircle(Color(0xFF94A3B8).copy(alpha = 0.60f), radius = h * 0.30f, center = Offset(w * 0.50f, h * 0.64f))
        }
        WhiteboardTemplateKind.SWOT_ANALYSIS, WhiteboardTemplateKind.RETROSPECTIVE, WhiteboardTemplateKind.BRAINSTORMING_LARGE -> {
            val colors = listOf(Color(0xFF1E293B), Color(0xFF334155), Color(0xFF475569), Color(0xFF64748B))
            for (r in 0..1) {
                for (c in 0..1) {
                    val col = colors[r * 2 + c]
                    drawRoundRect(
                        color = col.copy(alpha = 0.82f),
                        topLeft = Offset(c * (w * 0.52f), r * (h * 0.52f)),
                        size = Size(w * 0.46f, h * 0.46f),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }
            }
        }
        WhiteboardTemplateKind.CRAZY_8S -> {
            for (r in 0..1) {
                for (c in 0..3) {
                    drawRoundRect(
                        color = Color(0xFFCBD5E1),
                        topLeft = Offset(c * (w * 0.255f), r * (h * 0.52f)),
                        size = Size(w * 0.22f, h * 0.45f),
                        cornerRadius = CornerRadius(3f, 3f),
                    )
                }
            }
        }
        WhiteboardTemplateKind.PROJECT_PLANNING -> {
            val cols = listOf(Color(0xFF1E293B), Color(0xFF475569), Color(0xFF64748B))
            cols.forEachIndexed { i, col ->
                val y = i * (h * 0.34f)
                drawRoundRect(col, Offset(0f, y), Size(w, h * 0.28f), CornerRadius(4f, 4f))
            }
        }
        WhiteboardTemplateKind.FLOWCHART, WhiteboardTemplateKind.CONCEPT_MAP, WhiteboardTemplateKind.SPIDER_MAP -> {
            drawRoundRect(Color(0xFF1E293B), Offset(w * 0.35f, h * 0.06f), Size(w * 0.30f, h * 0.22f), CornerRadius(4f, 4f))
            for (i in 0..2) {
                val x = w * (0.08f + i * 0.32f)
                drawLine(Color(0xFF94A3B8), Offset(w * 0.5f, h * 0.28f), Offset(x + w * 0.10f, h * 0.56f), strokeWidth = 2f)
                drawRoundRect(
                    color = Color(0xFF475569),
                    topLeft = Offset(x, h * 0.56f),
                    size = Size(w * 0.20f, h * 0.28f),
                    cornerRadius = CornerRadius(4f, 4f),
                )
            }
        }
        else -> {
            val cols = listOf(Color(0xFF334155), Color(0xFF64748B), Color(0xFF94A3B8))
            for (c in 0..2) {
                for (r in 0..2) {
                    drawRoundRect(
                        color = cols[c].copy(alpha = 0.78f),
                        topLeft = Offset(c * (w * 0.34f), r * (h * 0.34f)),
                        size = Size(w * 0.28f, h * 0.28f),
                        cornerRadius = CornerRadius(3f, 3f),
                    )
                }
            }
        }
    }
}

enum class NotebookCoverStyle(val label: String, val design: NoteCoverDesign) {
    GRAPHITE("Graphite Fold", NoteCoverDesign.GRAPHITE_FOLD),
    EDITORIAL("Pure White", NoteCoverDesign.EDITORIAL_WRITER),
    OBSIDIAN("Midnight Black", NoteCoverDesign.OBSIDIAN_EDITION),
    PURE_LINEN("Soft Grey Linen", NoteCoverDesign.PURE_LINEN),
    DOT_BOOKPLATE("Minimal Bookplate", NoteCoverDesign.DOT_GRID_BOOKPLATE),
    ARCHITECT("Minimal Two-Tone", NoteCoverDesign.ARCHITECT_GRID),
    DUO_NAVY("Navy + White", NoteCoverDesign.DUO_NAVY_WHITE),
    DUO_GOLD("Black + Gold", NoteCoverDesign.DUO_BLACK_GOLD),
    DUO_EMERALD("Emerald + Cream", NoteCoverDesign.DUO_EMERALD_CREAM),
    DUO_LAVENDER("Lavender + White", NoteCoverDesign.DUO_LAVENDER_WHITE),
    DUO_BURGUNDY("Burgundy + Rose", NoteCoverDesign.DUO_BURGUNDY_ROSE),
    DUO_TEAL("Teal + Grey", NoteCoverDesign.DUO_TEAL_GREY),
    DUO_TERRACOTTA("Terracotta + Charcoal", NoteCoverDesign.DUO_TERRACOTTA_CHARCOAL),
    NATURE_FLORAL("Floral Blossom", NoteCoverDesign.NATURE_FLORAL_BLOSSOM),
    NATURE_BOTANICAL("Botanical Leaves", NoteCoverDesign.NATURE_BOTANICAL_LEAVES),
    NATURE_WILDFLOWER("Minimal Wildflower", NoteCoverDesign.NATURE_MINIMAL_WILDFLOWER),
    NATURE_MONSTERA("Lush Monstera", NoteCoverDesign.NATURE_MONSTERA_LEAF),
    NATURE_MOUNTAIN("Alpine Peaks", NoteCoverDesign.NATURE_MOUNTAIN_MIST),
    SUBJ_CS("Computer Science", NoteCoverDesign.SUBJECT_COMPUTER_SCIENCE),
    SUBJ_MATH("Mathematics", NoteCoverDesign.SUBJECT_MATHEMATICS),
    SUBJ_PHYSICS("Physics", NoteCoverDesign.SUBJECT_PHYSICS),
    SUBJ_CHEM("Chemistry", NoteCoverDesign.SUBJECT_CHEMISTRY),
    SUBJ_BIO("Biology", NoteCoverDesign.SUBJECT_BIOLOGY),
    SUBJ_ELEC("Electronics", NoteCoverDesign.SUBJECT_ELECTRONICS),
    SUBJ_QUANTUM("Quantum Physics", NoteCoverDesign.SUBJECT_QUANTUM_PHYSICS),
    SUBJ_ENG("Engineering", NoteCoverDesign.SUBJECT_ENGINEERING),
    SUBJ_ASTRO("Astronomy", NoteCoverDesign.SUBJECT_ASTRONOMY),
    SUBJ_STATS("Statistics", NoteCoverDesign.STATISTICS),
    SUBJ_ARCH("Architecture", NoteCoverDesign.ARCHITECT_GRID),
    CREAT_SKETCH("Sketchbook", NoteCoverDesign.SKETCHBOOK),
    CREAT_ART("Art Journal", NoteCoverDesign.ART_JOURNAL),
    CREAT_PHOTO("Photography", NoteCoverDesign.PHOTOGRAPHY),
    CREAT_MUSIC("Music Journal", NoteCoverDesign.MUSIC_JOURNAL),
    CREAT_TRAVEL("Travel Journal", NoteCoverDesign.TRAVEL_JOURNAL),
    CREAT_DIARY("Personal Diary", NoteCoverDesign.PERSONAL_DIARY),
    CREAT_WRITING("Creative Writing", NoteCoverDesign.CREATIVE_WRITING),
}

enum class NotebookPaperKind(val label: String, val ruling: Ruling, val spacing: SpacingPreset, val margin: Boolean) {
    BLANK("Blank", Ruling.NONE, SpacingPreset.NORMAL, false),
    DOTTED("Dotted Paper", Ruling.DOTS, SpacingPreset.NORMAL, false),
    SQUARED("Squared Paper", Ruling.GRID, SpacingPreset.NORMAL, false),
    RULED_NARROW("Ruled Narrow", Ruling.LINES, SpacingPreset.NARROW, true),
    RULED_WIDE("Ruled Wide", Ruling.LINES, SpacingPreset.WIDE, true),
    CORNELL("Cornell Notes", Ruling.LINES, SpacingPreset.NORMAL, true),
}

data class NotebookStudioSpec(
    val title: String,
    val format: PageFormat,
    val paperKind: NotebookPaperKind,
    val paperColor: Int,
) {
    fun toLayout(): TemplateLayout {
        if (paperKind == NotebookPaperKind.CORNELL) {
            val cornell = BuiltInTemplates.byId("academic.notebook")?.layout
            if (cornell != null) {
                return cornell.resized(format.width, format.height).copy(paperColor = paperColor)
            }
        }
        return TemplateDraft(
            format = format,
            ruling = paperKind.ruling,
            spacing = PageDesign.spacing(paperKind.ruling, paperKind.spacing),
            paperColor = paperColor,
            margin = paperKind.margin,
            header = HeaderStyle.TITLE_AND_DATE,
        ).toLayout()
    }
}

/**
 * Phase 11 — Redesigned Neutral Liquid Glass "New Notebook" Creation Modal (Fixes `image_5ddc78.jpg`):
 * - Uses pure white (#FFFFFF) in Light Mode and near-black (#101114) in Dark Mode (no blue outlines/accents).
 * - Places Cover & Paper previews side-by-side in a dedicated centered top showcase row.
 * - Places Name, Cover toggle, Paper Size, Orientation, and Paper Colour in a **full-width** neutral glass
 *   card underneath so "Untitled Notebook", "Standard, Portrait", and "Black / Pure White" never wrap or clip!
 * - Gives the Paper Templates carousel sufficient width and padding so "Squared Paper" never clips.
 */
@Composable
fun NewNotebookStudioDialog(
    onCreate: (NotebookStudioSpec) -> Unit,
    onBrowseAllTemplates: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val sheetBg = if (isDark) Color(0xFF101114) else Color(0xFFFFFFFF)
    val cardBg = if (isDark) Color(0xFF1A1C22) else Color(0xFFF4F5F7)
    val borderCol = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.12f)
    val primaryText = if (isDark) Color(0xFFF4F5F7) else Color(0xFF111317)
    val secondaryText = if (isDark) Color(0xFF9CA3AF) else Color(0xFF5A606A)
    val accentCol = if (isDark) Color(0xFFF4F5F7) else Color(0xFF141518)

    var title by rememberSaveable { mutableStateOf("Untitled Notebook") }
    var coverStyle by rememberSaveable { mutableStateOf(NotebookCoverStyle.GRAPHITE) }
    var hasCover by rememberSaveable { mutableStateOf(true) }
    var paperKind by rememberSaveable { mutableStateOf(NotebookPaperKind.RULED_WIDE) }
    var format by rememberSaveable { mutableStateOf(PageFormat.A4_PORTRAIT) }
    var colorIdx by rememberSaveable { mutableIntStateOf(if (isDark) 2 else 0) }

    // Phase 2.1 compliant neutral paper palette (Pure White, Soft Neutral, Near Black — no cream/beige)
    val paperColors = listOf(
        "Pure White" to 0xFFFFFFFF.toInt(),
        "Soft Neutral" to 0xFFF3F4F6.toInt(),
        "Near Black" to 0xFF16181D.toInt(),
    )
    val chosenColor = paperColors[colorIdx].second

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
            shadowElevation = 18.dp,
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(0.94f)
                .heightIn(max = 760.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Header: Close (X) | New Notebook | Confirm (✓)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = cardBg,
                        border = BorderStroke(0.8.dp, borderCol),
                        modifier = Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onDismiss),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, contentDescription = null, tint = primaryText, modifier = Modifier.size(20.dp))
                        }
                    }
                    Text(
                        text = "New Notebook",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText,
                    )
                    Surface(
                        shape = CircleShape,
                        color = accentCol,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable {
                                NoteCoverStore.stagePendingCoverForNewNote(
                                    NoteCoverConfig(
                                        design = if (hasCover) coverStyle.design else NoteCoverDesign.CLEAN_PAPER,
                                        colorPreset = CoverColorPreset.DEFAULT,
                                        hasCover = hasCover,
                                    ),
                                )
                                onCreate(
                                    NotebookStudioSpec(
                                        title = title.ifBlank { "Untitled Notebook" },
                                        format = format,
                                        paperKind = paperKind,
                                        paperColor = chosenColor,
                                    ),
                                )
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF101114) else Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // 2. Centered Cover & Paper Side-by-Side Preview Showcase
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (hasCover) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(108.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val next = (NotebookCoverStyle.entries.indexOf(coverStyle) + 1) % NotebookCoverStyle.entries.size
                                    coverStyle = NotebookCoverStyle.entries[next]
                                }
                                .padding(4.dp),
                        ) {
                            Text("COVER", style = MaterialTheme.typography.labelSmall, color = secondaryText)
                            Spacer(Modifier.height(6.dp))
                            NoteCoverArtwork(
                                title = title.ifBlank { "Untitled Notebook" },
                                noteType = NoteType.INK,
                                config = NoteCoverConfig(
                                    design = coverStyle.design,
                                    colorPreset = CoverColorPreset.DEFAULT,
                                    hasCover = true,
                                ),
                                subtitle = paperKind.label,
                                modifier = Modifier.width(96.dp).aspectRatio(0.71f),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = coverStyle.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(108.dp).padding(4.dp),
                    ) {
                        Text("PAPER", style = MaterialTheme.typography.labelSmall, color = secondaryText)
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(92.dp).aspectRatio(0.72f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(chosenColor))
                                .border(1.5.dp, accentCol, RoundedCornerShape(12.dp))
                                .padding(6.dp),
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawPaperKindPreview(paperKind, darkPaper = colorIdx == 2)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = paperKind.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 3. Full-Width Neutral Liquid Glass Settings Form (No squeezed text!)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    border = BorderStroke(0.8.dp, borderCol),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        // Notebook Name Input
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Name",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryText,
                                modifier = Modifier.width(88.dp),
                            )
                            BasicTextField(
                                value = title,
                                onValueChange = { title = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = primaryText,
                                    fontWeight = FontWeight.Medium,
                                ),
                                cursorBrush = SolidColor(accentCol),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        HorizontalDivider(color = borderCol)
                        // Cover Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { hasCover = !hasCover }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Notebook Cover", style = MaterialTheme.typography.bodyMedium, color = primaryText)
                            Box(
                                modifier = Modifier
                                    .size(width = 44.dp, height = 24.dp)
                                    .clip(CircleShape)
                                    .background(if (hasCover) accentCol else secondaryText.copy(alpha = 0.35f))
                                    .padding(3.dp),
                                contentAlignment = if (hasCover) Alignment.CenterEnd else Alignment.CenterStart,
                            ) {
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(if (hasCover && isDark) Color(0xFF101114) else Color.White),
                                )
                            }
                        }
                        HorizontalDivider(color = borderCol)
                        // Size & Orientation Row (Single clear line with plenty of horizontal room)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    format = when (format) {
                                        PageFormat.A4_PORTRAIT -> PageFormat.A4_LANDSCAPE
                                        PageFormat.A4_LANDSCAPE -> PageFormat.LONG_20_9
                                        PageFormat.LONG_20_9 -> PageFormat.A4_PORTRAIT
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Size & Orientation", style = MaterialTheme.typography.bodyMedium, color = primaryText)
                            Text(
                                text = when (format) {
                                    PageFormat.A4_PORTRAIT -> "A4 Standard · Portrait ⌄"
                                    PageFormat.A4_LANDSCAPE -> "A4 Standard · Landscape ⌄"
                                    PageFormat.LONG_20_9 -> "Compact 20:9 · Portrait ⌄"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryText,
                                maxLines = 1,
                            )
                        }
                        HorizontalDivider(color = borderCol)
                        // Paper Colour Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { colorIdx = (colorIdx + 1) % paperColors.size }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Paper Colour", style = MaterialTheme.typography.bodyMedium, color = primaryText)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(chosenColor))
                                        .border(1.dp, primaryText.copy(alpha = 0.55f), CircleShape),
                                )
                                Text(
                                    text = "${paperColors[colorIdx].first} ⌄",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryText,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = borderCol)

                // 3B. Notebook Cover Collections Carousel (Minimal, Two-Colour, Nature, Academic)
                if (hasCover) {
                    var selectedCoverCollection by rememberSaveable { mutableStateOf(CoverCollection.MINIMAL) }
                    Text(
                        text = "Notebook Cover Collection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CoverCollection.entries.forEach { col ->
                            val activeCol = selectedCoverCollection == col
                            Surface(
                                shape = CircleShape,
                                color = if (activeCol) accentCol else cardBg,
                                border = BorderStroke(0.8.dp, if (activeCol) accentCol else borderCol),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { selectedCoverCollection = col },
                            ) {
                                Text(
                                    text = col.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (activeCol) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (activeCol) (if (isDark) Color(0xFF101114) else Color.White) else primaryText,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val designsInCollection = if (selectedCoverCollection == CoverCollection.ALL || selectedCoverCollection == CoverCollection.CUSTOM) NotebookCoverStyle.entries else NotebookCoverStyle.entries.filter { it.design.collection == selectedCoverCollection }
                        designsInCollection.forEach { itemStyle ->
                            val isSelectedCover = coverStyle == itemStyle
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(96.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { coverStyle = itemStyle }
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(86.dp)
                                        .aspectRatio(0.72f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            width = if (isSelectedCover) 2.dp else 0.8.dp,
                                            color = if (isSelectedCover) accentCol else borderCol,
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                        .padding(2.dp),
                                ) {
                                    NoteCoverArtwork(
                                        title = title.ifBlank { "Untitled Notebook" },
                                        noteType = NoteType.INK,
                                        config = NoteCoverConfig(
                                            design = itemStyle.design,
                                            colorPreset = CoverColorPreset.DEFAULT,
                                            hasCover = true,
                                        ),
                                        subtitle = itemStyle.label,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = itemStyle.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelectedCover) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelectedCover) primaryText else secondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = borderCol)
                }

                // 4. Paper Templates Carousel (Generous card width so "Squared Paper" never clips)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Paper Templates",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                    )
                    Text(
                        text = "All Templates ›",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss(); onBrowseAllTemplates() }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }

                Text(
                    text = "Essentials",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryText,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NotebookPaperKind.entries.forEach { kind ->
                        val selected = paperKind == kind
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(116.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { paperKind = kind }
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 104.dp, height = 132.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(chosenColor))
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) accentCol else borderCol,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .padding(6.dp),
                            ) {
                                Canvas(Modifier.fillMaxSize()) {
                                    drawPaperKindPreview(kind, darkPaper = colorIdx == 2)
                                }
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(accentCol),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.Check,
                                            null,
                                            tint = if (isDark) Color(0xFF101114) else Color.White,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = kind.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) primaryText else secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                paperColors.forEachIndexed { idx, (_, colInt) ->
                                    Box(
                                        modifier = Modifier
                                            .size(11.dp)
                                            .clip(CircleShape)
                                            .background(Color(colInt))
                                            .border(
                                                width = if (colorIdx == idx) 1.5.dp else 0.6.dp,
                                                color = if (colorIdx == idx) accentCol else secondaryText.copy(alpha = 0.55f),
                                                shape = CircleShape,
                                            )
                                            .clickable {
                                                paperKind = kind
                                                colorIdx = idx
                                            },
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

private fun DrawScope.drawPaperKindPreview(kind: NotebookPaperKind, darkPaper: Boolean) {
    val lineCol = if (darkPaper) Color.White.copy(alpha = 0.22f) else Color(0xFF94A3B8).copy(alpha = 0.55f)
    val marginCol = if (darkPaper) Color.White.copy(alpha = 0.30f) else Color(0xFF64748B).copy(alpha = 0.45f)
    val w = size.width
    val h = size.height
    when (kind) {
        NotebookPaperKind.BLANK -> Unit
        NotebookPaperKind.DOTTED -> {
            for (r in 1..9) {
                for (c in 1..7) {
                    drawCircle(lineCol, radius = 1.3f, center = Offset(w * c / 8f, h * r / 10f))
                }
            }
        }
        NotebookPaperKind.SQUARED -> {
            for (r in 1..9) {
                val y = h * r / 10f
                drawLine(lineCol, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            for (c in 1..7) {
                val x = w * c / 8f
                drawLine(lineCol, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            }
        }
        NotebookPaperKind.RULED_NARROW -> {
            for (r in 2..12) {
                val y = h * r / 13f
                drawLine(lineCol, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            drawLine(marginCol, Offset(w * 0.18f, 0f), Offset(w * 0.18f, h), strokeWidth = 1f)
        }
        NotebookPaperKind.RULED_WIDE -> {
            for (r in 2..8) {
                val y = h * r / 9f
                drawLine(lineCol, Offset(0f, y), Offset(w, y), strokeWidth = 1.2f)
            }
            drawLine(marginCol, Offset(w * 0.18f, 0f), Offset(w * 0.18f, h), strokeWidth = 1f)
        }
        NotebookPaperKind.CORNELL -> {
            drawLine(lineCol, Offset(0f, h * 0.16f), Offset(w, h * 0.16f), strokeWidth = 1.5f)
            drawLine(marginCol, Offset(w * 0.30f, h * 0.16f), Offset(w * 0.30f, h * 0.80f), strokeWidth = 1.2f)
            drawLine(lineCol, Offset(0f, h * 0.80f), Offset(w, h * 0.80f), strokeWidth = 1.5f)
            for (r in 3..7) {
                val y = h * r / 9f
                drawLine(lineCol, Offset(w * 0.30f, y), Offset(w, y), strokeWidth = 1f)
            }
        }
    }
}
