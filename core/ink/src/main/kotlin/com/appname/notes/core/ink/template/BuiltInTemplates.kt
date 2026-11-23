package com.appname.notes.core.ink.template

import com.appname.notes.core.ink.template.TemplateColors.HEADER_FILL
import com.appname.notes.core.ink.template.TemplateColors.LABEL
import com.appname.notes.core.ink.template.TemplateColors.TITLE

/** Groups on the Choose-template screen, in the owner's order (brief §4). */
enum class TemplateCategory { BASIC, ACADEMIC, PLANNERS, JOURNALS, CREATIVE, CUSTOM }

/** A template the user can pick: built in, or made by the user ([TemplateCategory.CUSTOM]). */
data class TemplateDefinition(
    val id: String,
    val name: String,
    val category: TemplateCategory,
    val layout: TemplateLayout,
) {
    val isCustom: Boolean get() = category == TemplateCategory.CUSTOM
}

/**
 * The built-in templates: original, functional designs for every template the owner listed. Each is built
 * from components, so it prints sharply at any zoom and can be copied into a custom template and changed.
 */
object BuiltInTemplates {

    val all: List<TemplateDefinition> by lazy { basic() + academic() + planners() + journals() + creative() }

    fun byId(id: String): TemplateDefinition? = all.firstOrNull { it.id == id }

    fun inCategory(category: TemplateCategory): List<TemplateDefinition> = all.filter { it.category == category }

    // ------------------------------------------------------------------ Basic

    private fun basic(): List<TemplateDefinition> {
        fun def(id: String, name: String, paper: PaperStyle) =
            TemplateDefinition("basic.$id", name, TemplateCategory.BASIC, TemplateLayout(paper = paper))
        val lines = { s: Float -> PaperStyle(Ruling.LINES, s, marginX = 64f, top = s * 3) }
        return listOf(
            def("blank", "Blank page", PaperStyle()),
            def("lined", "Lined", lines(30f)),
            def("lined_narrow", "Lined (narrow)", lines(24f)),
            def("lined_wide", "Lined (wide)", lines(38f)),
            def("grid", "Grid", PaperStyle(Ruling.GRID, 24f)),
            def("grid_narrow", "Grid (narrow)", PaperStyle(Ruling.GRID, 16f)),
            def("grid_wide", "Grid (wide)", PaperStyle(Ruling.GRID, 32f)),
            def("dot", "Dot", PaperStyle(Ruling.DOTS, 24f, lineColor = TemplateColors.DOT)),
            def("dot_narrow", "Dot (narrow)", PaperStyle(Ruling.DOTS, 16f, lineColor = TemplateColors.DOT)),
        )
    }

    // ------------------------------------------------------------------ Academic

    private fun academic() = listOf(
        page("academic.notebook", "Notebook", TemplateCategory.ACADEMIC) {
            // Cornell-style: cues on the left, notes on the right, a summary at the bottom.
            header("Topic", date = true)
            label(M, 128f, "Cues", bold = true)
            label(250f, 128f, "Notes", bold = true)
            lines(M, 136f, W - 2 * M, 760f, 30f)
            rule(232f, 108f, 232f, 896f)
            box(M, 914f, W - 2 * M, 160f, radius = 10f)
            label(M + 14f, 940f, "Summary", bold = true)
            lines(M + 14f, 948f, W - 2 * M - 28f, 120f, 30f)
        },
        page("academic.planner1", "Planner 1", TemplateCategory.ACADEMIC) {
            // Day planner: hourly schedule, priorities, to-do and notes.
            header("Day planner", date = true)
            section(M, 128f, "Schedule")
            table(M, 140f, 330f, 690f, listOf(1f, 3f), rows = 15, rowLabels = (7..21).map { it.toString().padStart(2, '0') + ":00" })
            section(410f, 128f, "Top priorities")
            checklist(410f, 140f, W - M - 410f, 3 * 34f)
            section(410f, 290f, "To do")
            checklist(410f, 302f, W - M - 410f, 16 * 33f, 33f)
            section(M, 868f, "Notes")
            lines(M, 876f, W - 2 * M, 200f, 30f)
        },
        page("academic.planner2", "Planner 2", TemplateCategory.ACADEMIC) {
            // Study week: one row per day.
            header("Study week", date = true)
            table(M, 120f, W - 2 * M, 700f, listOf(1.1f, 2f, 3f, 1f), rows = 7,
                headers = listOf("Day", "Subjects", "Tasks", "Hours"), rowLabels = DAYS)
            section(M, 856f, "Goals this week")
            checklist(M, 868f, W - 2 * M, 6 * 34f)
        },
        page("academic.planner3", "Planner 3", TemplateCategory.ACADEMIC) {
            // Assignment tracker.
            header("Assignments")
            table(M, 120f, W - 2 * M, 950f, listOf(2f, 4f, 1.4f, 1.4f), rows = 22,
                headers = listOf("Subject", "Assignment", "Due", "Done"))
        },
        page("academic.planner4", "Planner 4", TemplateCategory.ACADEMIC) {
            // Exam revision.
            header("Exam revision")
            field(M, 132f, "Exam", 330f)
            field(420f, 132f, "Date", W - M - 420f)
            table(M, 160f, W - 2 * M, 700f, listOf(4f, 1.4f, 1.4f, 1.4f), rows = 18,
                headers = listOf("Topic", "Notes", "Practised", "Confident"))
            section(M, 900f, "Weak spots to review")
            lines(M, 908f, W - 2 * M, 170f, 30f)
        },
        page("academic.schedule", "School schedule", TemplateCategory.ACADEMIC, LANDSCAPE) {
            val w = LANDSCAPE.first
            val h = LANDSCAPE.second
            header("Timetable", width = w)
            table(M, 116f, w - 2 * M, h - 116f - M, listOf(1f, 1.6f, 1.6f, 1.6f, 1.6f, 1.6f, 1.6f), rows = 8,
                headers = listOf("Period", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"), rowLabels = (1..8).map { "$it" })
        },
    )

    // ------------------------------------------------------------------ Planners

    private fun planners() = listOf(
        page("planners.checklist", "Checklist", TemplateCategory.PLANNERS) {
            header("Checklist", date = true)
            checklist(M, 124f, W - 2 * M, 950f, 36f)
        },
        page("planners.weekly", "Weekly planner", TemplateCategory.PLANNERS) {
            header("Week of", date = false)
            val colW = (W - 2 * M - 16f) / 2
            val boxH = 228f
            (DAYS + "Notes").forEachIndexed { i, day ->
                val x = M + (i % 2) * (colW + 16f)
                val y = 116f + (i / 2) * (boxH + 12f)
                box(x, y, colW, boxH, radius = 10f)
                label(x + 12f, y + 24f, day, bold = true)
                lines(x + 12f, y + 30f, colW - 24f, boxH - 36f, 28f)
            }
        },
        page("planners.monthly_plan", "Monthly plan", TemplateCategory.PLANNERS, LANDSCAPE) {
            val w = LANDSCAPE.first
            val h = LANDSCAPE.second
            header("Month", width = w)
            table(M, 112f, w - 2 * M, h - 112f - M, List(7) { 1f }, rows = 6, headers = DAYS_SHORT, headerFill = HEADER_FILL)
        },
        page("planners.monthly_planner", "Monthly planner", TemplateCategory.PLANNERS) {
            header("Month", date = false)
            table(M, 116f, W - 2 * M, 520f, List(7) { 1f }, rows = 5, headers = DAYS_SHORT)
            val half = (W - 2 * M - 24f) / 2
            section(M, 676f, "Goals")
            checklist(M, 688f, half, 11 * 34f)
            section(M + half + 24f, 676f, "Notes")
            lines(M + half + 24f, 684f, half, 390f, 30f)
        },
        page("planners.work", "Work planner", TemplateCategory.PLANNERS) {
            header("Work planner", date = true)
            section(M, 128f, "Top 3")
            checklist(M, 140f, W - 2 * M, 3 * 34f)
            section(M, 282f, "Meetings")
            table(M, 294f, W - 2 * M, 300f, listOf(1f, 2f, 4f), rows = 6, headers = listOf("Time", "With", "Notes"))
            val half = (W - 2 * M - 24f) / 2
            section(M, 634f, "Tasks")
            checklist(M, 646f, half, 12 * 35f, 35f)
            section(M + half + 24f, 634f, "Follow-ups")
            lines(M + half + 24f, 642f, half, 430f, 30f)
        },
        page("planners.health", "Health planner", TemplateCategory.PLANNERS) {
            header("Health", date = true)
            section(M, 132f, "Water")
            circles(170f, 114f, 8)
            section(M, 182f, "Sleep")
            field(170f, 182f, "Hours", 200f)
            field(420f, 182f, "Quality", W - M - 420f)
            section(M, 228f, "Meals")
            table(M, 240f, W - 2 * M, 300f, listOf(1.3f, 4f, 1f), rows = 4,
                headers = listOf("Meal", "What I ate", "Water"), rowLabels = listOf("Breakfast", "Lunch", "Dinner", "Snacks"))
            section(M, 584f, "Exercise")
            table(M, 596f, W - 2 * M, 200f, listOf(3f, 1f, 1f), rows = 4, headers = listOf("Activity", "Minutes", "Done"))
            section(M, 836f, "Mood")
            circles(170f, 818f, 5)
            section(M, 884f, "Notes")
            lines(M, 892f, W - 2 * M, 186f, 30f)
        },
        page("planners.travel", "Travel plan", TemplateCategory.PLANNERS) {
            header("Trip", date = false)
            field(M, 132f, "Destination", W - 2 * M)
            field(M, 170f, "Dates", 330f)
            field(420f, 170f, "Stay", W - M - 420f)
            section(M, 218f, "Itinerary")
            table(M, 230f, W - 2 * M, 470f, listOf(1f, 5f), rows = 7, headers = listOf("Day", "Plan"), rowLabels = (1..7).map { "Day $it" })
            val half = (W - 2 * M - 24f) / 2
            section(M, 742f, "Packing")
            checklist(M, 754f, half, 9 * 35f, 35f)
            checklist(M + half + 24f, 754f, half, 9 * 35f, 35f)
        },
        page("planners.budget", "Monthly budget", TemplateCategory.PLANNERS) {
            header("Monthly budget", date = false)
            section(M, 128f, "Income")
            table(M, 140f, W - 2 * M, 150f, listOf(3f, 1.4f, 1.4f), rows = 4, headers = listOf("Source", "Planned", "Actual"))
            section(M, 330f, "Expenses")
            table(
                M, 342f, W - 2 * M, 580f, listOf(3f, 1.4f, 1.4f, 1.4f), rows = 14,
                headers = listOf("Category", "Planned", "Actual", "Difference"),
                rowLabels = listOf(
                    "Rent / housing", "Groceries", "Eating out", "Transport", "Bills", "Phone & internet", "Health",
                    "Education", "Shopping", "Entertainment", "Gifts", "Savings", "Other", "Total",
                ),
            )
            section(M, 962f, "Notes")
            lines(M, 970f, W - 2 * M, 108f, 30f)
        },
    )

    // ------------------------------------------------------------------ Journals

    private fun journals() = listOf(
        page("journals.daily", "Daily journal", TemplateCategory.JOURNALS) {
            header("Daily journal", date = true)
            section(M, 132f, "Mood")
            circles(150f, 114f, 5, diameter = 24f, gap = 14f)
            section(M, 184f, "Grateful for")
            lines(M, 192f, W - 2 * M, 96f, 32f)
            section(M, 324f, "Today")
            lines(M, 332f, W - 2 * M, 746f, 32f)
        },
        page("journals.review", "Movie/book review", TemplateCategory.JOURNALS) {
            header("Review", date = true)
            box(M, 120f, 220f, 310f, radius = 10f)
            label(M + 110f, 282f, "Cover / poster", align = LabelAlign.CENTER)
            val x = M + 244f
            field(x, 150f, "Title", W - M - x)
            field(x, 196f, "By", W - M - x)
            field(x, 242f, "Genre", W - M - x)
            field(x, 288f, "Finished on", W - M - x)
            label(x, 338f, "Rating", bold = true)
            circles(x + 70f, 320f, 5)
            section(M, 470f, "Thoughts")
            lines(M, 478f, W - 2 * M, 420f, 30f)
            box(M, 920f, W - 2 * M, 156f, radius = 10f)
            label(M + 14f, 946f, "Favourite quote or scene", bold = true)
        },
        page("journals.pet", "Pet care log", TemplateCategory.JOURNALS) {
            header("Pet care log")
            field(M, 132f, "Pet", 330f)
            field(420f, 132f, "Vet", W - M - 420f)
            table(M, 160f, W - 2 * M, 915f, listOf(1.2f, 1.6f, 1f, 1.4f, 3f), rows = 22,
                headers = listOf("Date", "Food", "Walk", "Medicine", "Notes"))
        },
    )

    // ------------------------------------------------------------------ Creative

    private fun creative() = listOf(
        page("creative.music", "Music", TemplateCategory.CREATIVE) {
            field(M, 76f, "Title", 420f)
            field(520f, 76f, "Tempo", W - M - 520f)
            staves(M, 120f, W - 2 * M, 960f)
        },
        page("creative.recipes", "Recipes and meal plan", TemplateCategory.CREATIVE) {
            header("Recipe", date = false)
            field(M, 132f, "Serves", 200f)
            field(280f, 132f, "Time", 200f)
            field(512f, 132f, "Tags", W - M - 512f)
            val left = 250f
            section(M, 180f, "Ingredients")
            checklist(M, 192f, left - 16f, 13 * 34f)
            section(M + left, 180f, "Method")
            lines(M + left, 188f, W - 2 * M - left, 450f, 30f)
            section(M, 690f, "Meal plan")
            table(M, 702f, W - 2 * M, 372f, listOf(1f, 2f, 2f, 2f), rows = 7,
                headers = listOf("", "Breakfast", "Lunch", "Dinner"), rowLabels = DAYS_SHORT)
        },
    )

    // ------------------------------------------------------------------ building blocks

    private const val W = 794f
    private const val M = 48f
    private val LANDSCAPE = 1123f to 794f
    private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val DAYS_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private fun page(
        id: String,
        name: String,
        category: TemplateCategory,
        size: Pair<Float, Float> = 794f to 1123f,
        build: Builder.() -> Unit,
    ): TemplateDefinition {
        val b = Builder().apply(build)
        return TemplateDefinition(id, name, category, TemplateLayout(size.first, size.second, components = b.items.toList()))
    }

    private class Builder {
        val items = ArrayList<TemplateComponent>()

        /** Big title at the top; optionally a "Date ____" field on the right; a line under both. */
        fun header(title: String, date: Boolean = false, width: Float = W) {
            items += TextLabel(M, 76f, title, size = 26f, bold = true, color = TITLE)
            if (date) field(width - M - 230f, 76f, "Date", 230f)
            items += Rule(M, 94f, width - M, 94f, width = 2f)
        }

        fun label(x: Float, y: Float, text: String, bold: Boolean = false, align: LabelAlign = LabelAlign.START) {
            items += TextLabel(x, y, text, size = 14f, bold = bold, align = align, color = if (bold) TITLE else LABEL)
        }

        fun section(x: Float, y: Float, title: String) {
            items += TextLabel(x, y, title, size = 15f, bold = true, color = TITLE)
        }

        /** "Label ______": a label and a line to write on, [w] wide in total. */
        fun field(x: Float, y: Float, label: String, w: Float) {
            items += TextLabel(x, y, label, size = 13f, color = LABEL)
            val start = x + label.length * 7.4f + 10f
            items += Rule(start, y + 4f, x + w, y + 4f, color = TemplateColors.RULE, width = 1.2f)
        }

        fun rule(x1: Float, y1: Float, x2: Float, y2: Float) {
            items += Rule(x1, y1, x2, y2)
        }

        fun box(x: Float, y: Float, w: Float, h: Float, radius: Float = 0f) {
            items += Box(x, y, w, h, radius)
        }

        fun lines(x: Float, y: Float, w: Float, h: Float, spacing: Float) {
            items += LinedArea(x, y, w, h, spacing)
        }

        fun checklist(x: Float, y: Float, w: Float, h: Float, spacing: Float = 34f) {
            items += Checklist(x, y, w, h, spacing)
        }

        fun table(
            x: Float, y: Float, w: Float, h: Float, columns: List<Float>, rows: Int,
            headers: List<String> = emptyList(), rowLabels: List<String> = emptyList(), headerFill: Int? = HEADER_FILL,
        ) {
            items += Table(x, y, w, h, columns, rows, headers, rowLabels, headerFill = headerFill)
        }

        fun circles(x: Float, y: Float, count: Int, diameter: Float = 22f, gap: Float = 10f) {
            items += CircleRow(x, y, count, diameter, gap)
        }

        fun staves(x: Float, y: Float, w: Float, h: Float) {
            items += Staves(x, y, w, h)
        }
    }
}
