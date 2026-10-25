package com.appname.notes.feature.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

/**
 * Equations (8c): a small typesetter for the maths students write most — powers and indices (x^2, a_n), fractions
 * (\frac{a}{b}), roots (\sqrt{x}, \sqrt[3]{x}), Greek letters and symbols (\alpha, \sum, \int, \leq, \to …) and
 * function names (\sin, \log, \lim). Written in the familiar LaTeX style; drawn with the phone's serif font. Pure
 * drawing code (no library), so it works offline.
 */
object MathRender {

    /** Measures and draws [source] at [size] px (the height of normal text); null if there is nothing to draw. */
    fun layout(source: String, size: Float, color: Int): Box? {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) return null
        return Parser(trimmed, size, color).parseAll()
    }

    /** The equation as a picture with a transparent background, [padding] px around it. */
    fun render(source: String, size: Float, color: Int, padding: Float = size * 0.3f): Bitmap? {
        val box = layout(source, size, color) ?: return null
        val w = (box.width + 2 * padding).toInt().coerceIn(1, 4096)
        val h = (box.ascent + box.descent + 2 * padding).toInt().coerceIn(1, 4096)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        box.draw(Canvas(bmp), padding, padding + box.ascent)
        return bmp
    }

    // ---------------------------------------------------------------- boxes

    abstract class Box {
        abstract val width: Float
        abstract val ascent: Float
        abstract val descent: Float
        abstract fun draw(c: Canvas, x: Float, baseline: Float)
    }

    private fun paint(size: Float, color: Int, italic: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SERIF, if (italic) Typeface.ITALIC else Typeface.NORMAL)
    }

    private class Glyphs(val text: String, size: Float, color: Int, italic: Boolean, val padL: Float = 0f, val padR: Float = 0f) : Box() {
        private val p = paint(size, color, italic)
        private val m = p.fontMetrics
        override val width = p.measureText(text) + padL + padR
        override val ascent = -m.ascent * 0.82f
        override val descent = m.descent * 0.9f
        override fun draw(c: Canvas, x: Float, baseline: Float) = c.drawText(text, x + padL, baseline, p)
    }

    private class Row(val items: List<Box>) : Box() {
        override val width = items.sumOf { it.width.toDouble() }.toFloat()
        override val ascent = items.maxOfOrNull { it.ascent } ?: 0f
        override val descent = items.maxOfOrNull { it.descent } ?: 0f
        override fun draw(c: Canvas, x: Float, baseline: Float) {
            var cx = x
            for (b in items) {
                b.draw(c, cx, baseline)
                cx += b.width
            }
        }
    }

    private class Scripts(val base: Box, val sup: Box?, val sub: Box?, size: Float) : Box() {
        private val up = maxOf(base.ascent * 0.55f, size * 0.42f)
        private val down = maxOf(base.descent * 0.6f, size * 0.22f)
        override val width = base.width + maxOf(sup?.width ?: 0f, sub?.width ?: 0f) + size * 0.04f
        override val ascent = maxOf(base.ascent, (sup?.ascent ?: 0f) + up)
        override val descent = maxOf(base.descent, (sub?.descent ?: 0f) + down)
        override fun draw(c: Canvas, x: Float, baseline: Float) {
            base.draw(c, x, baseline)
            val sx = x + base.width + 0.02f * width
            sup?.draw(c, sx, baseline - up)
            sub?.draw(c, sx, baseline + down)
        }
    }

    private class Fraction(val num: Box, val den: Box, val size: Float, color: Int) : Box() {
        private val gap = size * 0.12f
        private val axis = size * 0.28f // the bar sits at the height of a minus sign
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = maxOf(1f, size * 0.05f) }
        override val width = maxOf(num.width, den.width) + size * 0.3f
        override val ascent = axis + gap + num.descent + num.ascent
        override val descent = (den.ascent + den.descent + gap - axis).coerceAtLeast(0f)
        override fun draw(c: Canvas, x: Float, baseline: Float) {
            val bar = baseline - axis
            c.drawLine(x + size * 0.08f, bar, x + width - size * 0.08f, bar, line)
            num.draw(c, x + (width - num.width) / 2, bar - gap - num.descent)
            den.draw(c, x + (width - den.width) / 2, bar + gap + den.ascent)
        }
    }

    private class Root(val body: Box, val index: Box?, val size: Float, color: Int) : Box() {
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, size * 0.055f)
            strokeJoin = Paint.Join.ROUND
        }
        private val sign = size * 0.55f
        private val lead = maxOf(0f, (index?.width ?: 0f) - sign * 0.45f)
        override val width = lead + sign + body.width + size * 0.1f
        override val ascent = body.ascent + size * 0.16f
        override val descent = body.descent + size * 0.04f
        override fun draw(c: Canvas, x: Float, baseline: Float) {
            val top = baseline - ascent
            val bottom = baseline + descent
            val x0 = x + lead
            val path = Path().apply {
                moveTo(x0, baseline - size * 0.25f)
                lineTo(x0 + sign * 0.2f, baseline - size * 0.32f)
                lineTo(x0 + sign * 0.5f, bottom)
                lineTo(x0 + sign, top)
                lineTo(x + width, top)
            }
            c.drawPath(path, stroke)
            index?.draw(c, x0 + sign * 0.42f - index.width, baseline - size * 0.42f)
            body.draw(c, x0 + sign + size * 0.04f, baseline)
        }
    }

    // ---------------------------------------------------------------- parsing

    private val Symbols = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ",
        "eta" to "η", "theta" to "θ", "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "upsilon" to "υ", "phi" to "φ", "varphi" to "φ", "chi" to "χ",
        "psi" to "ψ", "omega" to "ω", "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π",
        "Sigma" to "Σ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±", "mp" to "∓", "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥",
        "neq" to "≠", "ne" to "≠", "approx" to "≈", "equiv" to "≡", "sim" to "∼", "propto" to "∝", "infty" to "∞", "partial" to "∂",
        "nabla" to "∇", "sum" to "∑", "prod" to "∏", "int" to "∫", "iint" to "∬", "oint" to "∮", "to" to "→", "rightarrow" to "→",
        "leftarrow" to "←", "Rightarrow" to "⇒", "implies" to "⇒", "Leftarrow" to "⇐", "iff" to "⇔", "Leftrightarrow" to "⇔",
        "leftrightarrow" to "↔", "in" to "∈", "notin" to "∉", "subset" to "⊂", "subseteq" to "⊆", "supset" to "⊃", "cup" to "∪",
        "cap" to "∩", "emptyset" to "∅", "forall" to "∀", "exists" to "∃", "neg" to "¬", "land" to "∧", "lor" to "∨",
        "degree" to "°", "circ" to "∘", "angle" to "∠", "perp" to "⊥", "parallel" to "∥", "ldots" to "…", "cdots" to "⋯",
        "prime" to "′", "hbar" to "ħ", "ell" to "ℓ", "therefore" to "∴", "because" to "∵", "langle" to "⟨", "rangle" to "⟩",
        "{" to "{", "}" to "}", "%" to "%", "," to " ", ";" to " ", "quad" to "  ", " " to " ",
    )

    private val Functions = setOf("sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh", "log", "ln", "lg", "exp", "lim", "max", "min", "det", "gcd", "mod")

    /** Symbols with a little room on both sides (relations and operators). */
    private val Spaced = setOf("+", "−", "=", "<", ">", "×", "·", "÷", "±", "∓", "≤", "≥", "≠", "≈", "≡", "∼", "∝", "→", "←", "⇒", "⇐", "⇔", "↔", "∈", "∉", "⊂", "⊆", "⊃", "∪", "∩")

    private val Big = setOf("∑", "∏", "∫", "∬", "∮")

    private class Parser(val s: String, val baseSize: Float, val color: Int) {
        var i = 0

        fun parseAll(): Box = Row(parseRow(baseSize, stop = null))

        private fun parseRow(size: Float, stop: Char?): List<Box> {
            val out = ArrayList<Box>()
            while (i < s.length) {
                val ch = s[i]
                if (stop != null && ch == stop) {
                    i++
                    return out
                }
                if (ch == '}' && stop == null) { i++; continue } // a stray closing brace
                var base = parseAtom(size) ?: continue
                var sup: Box? = null
                var sub: Box? = null
                while (i < s.length && (s[i] == '^' || s[i] == '_')) {
                    val upper = s[i] == '^'
                    i++
                    val script = parseArg(size * 0.68f) ?: break
                    if (upper) sup = script else sub = script
                }
                if (sup != null || sub != null) base = Scripts(base, sup, sub, size)
                out += base
            }
            return out
        }

        /** A `{…}` group or a single atom. */
        private fun parseArg(size: Float): Box? {
            skipSpaces()
            if (i >= s.length) return null
            if (s[i] == '{') {
                i++
                return Row(parseRow(size, '}'))
            }
            return parseAtom(size)
        }

        private fun skipSpaces() {
            while (i < s.length && s[i] == ' ') i++
        }

        private fun parseAtom(size: Float): Box? {
            if (i >= s.length) return null
            val ch = s[i]
            when {
                ch == ' ' -> { i++; return null }
                ch == '{' -> { i++; return Row(parseRow(size, '}')) }
                ch == '\\' -> return parseCommand(size)
                ch == '-' -> { i++; return glyph("−", size, italic = false) }
                ch == '*' -> { i++; return glyph("·", size, italic = false) }
                ch == '\'' -> { i++; return Glyphs("′", size, color, false) }
                ch.isLetter() -> {
                    i++
                    return Glyphs(ch.toString(), size, color, italic = ch in 'a'..'z' || ch in 'A'..'Z')
                }
                ch.isDigit() || ch == '.' -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    return Glyphs(s.substring(start, i), size, color, italic = false)
                }
                else -> { i++; return glyph(ch.toString(), size, italic = false) }
            }
        }

        private fun glyph(text: String, size: Float, italic: Boolean): Box {
            if (text in Spaced) return Glyphs(text, size, color, italic, size * 0.2f, size * 0.2f)
            if (text in Big) return Glyphs(text, size * 1.35f, color, false, size * 0.05f, size * 0.12f)
            return Glyphs(text, size, color, italic)
        }

        private fun parseCommand(size: Float): Box? {
            i++ // backslash
            if (i >= s.length) return null
            val start = i
            if (s[i].isLetter()) {
                while (i < s.length && s[i].isLetter()) i++
            } else {
                i++
            }
            val name = s.substring(start, i)
            return when (name) {
                "frac", "dfrac", "tfrac" -> {
                    val small = if (size >= baseSize * 0.99f) size * 0.85f else size * 0.75f
                    val num = parseArg(small) ?: Row(emptyList())
                    val den = parseArg(small) ?: Row(emptyList())
                    Fraction(num, den, size, color)
                }
                "sqrt" -> {
                    skipSpaces()
                    var index: Box? = null
                    if (i < s.length && s[i] == '[') {
                        i++
                        index = Row(parseRow(size * 0.55f, ']'))
                    }
                    Root(parseArg(size) ?: Row(emptyList()), index, size, color)
                }
                "left", "right", "big", "Big", "bigg", "Bigg" -> parseAtom(size)
                "text", "mathrm", "operatorname" -> {
                    skipSpaces()
                    if (i < s.length && s[i] == '{') {
                        val end = s.indexOf('}', i).let { if (it < 0) s.length else it }
                        val t = s.substring(i + 1, end)
                        i = (end + 1).coerceAtMost(s.length)
                        Glyphs(t, size, color, false, size * 0.08f, size * 0.08f)
                    } else {
                        null
                    }
                }
                in Functions -> Glyphs(name, size, color, false, size * 0.05f, size * 0.12f)
                else -> Symbols[name]?.let { glyph(it, size, italic = false) } ?: Glyphs("\\$name", size, color, false)
            }
        }
    }
}
