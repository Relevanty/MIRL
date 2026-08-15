package com.personal.sleepalarm.ui.diary

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

private val LATEX_COMMANDS = mapOf(
    // Операторы
    "cdot" to "·", "times" to "×", "div" to "÷", "pm" to "±", "mp" to "∓",
    "le" to "≤", "leq" to "≤", "leqslant" to "≤",
    "ge" to "≥", "geq" to "≥", "geqslant" to "≥",
    "ne" to "≠", "neq" to "≠", "approx" to "≈", "equiv" to "≡",
    "sim" to "∼", "propto" to "∝",
    // Большие операторы
    "infty" to "∞", "sum" to "∑", "prod" to "∏", "int" to "∫",
    "partial" to "∂", "nabla" to "∇",
    // Функции
    "log" to "log", "ln" to "ln", "lg" to "lg", "lim" to "lim", "exp" to "exp",
    "sin" to "sin", "cos" to "cos", "tan" to "tan", "tg" to "tg", "ctg" to "ctg",
    "arcsin" to "arcsin", "arccos" to "arccos", "arctg" to "arctg",
    // Пробелы
    "quad" to "  ", "qquad" to "    ",
    // Греческие
    "pi" to "π", "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
    "theta" to "θ", "lambda" to "λ", "mu" to "μ", "sigma" to "σ", "phi" to "φ",
    "omega" to "ω", "Delta" to "Δ", "Omega" to "Ω", "varepsilon" to "ε",
    // Множества и логика
    "in" to "∈", "notin" to "∉", "subset" to "⊂", "supset" to "⊃",
    "cup" to "∪", "cap" to "∩", "forall" to "∀", "exists" to "∃",
    "emptyset" to "∅", "varnothing" to "∅",
    // Стрелки
    "rightarrow" to "→", "leftarrow" to "←", "Rightarrow" to "⇒",
    "Leftarrow" to "⇐", "to" to "→", "leftrightarrow" to "↔",
    // Геометрия
    "angle" to "∠", "triangle" to "△", "perp" to "⊥", "parallel" to "∥",
    // Прочее
    "circ" to "°", "degree" to "°",
    "dots" to "…", "ldots" to "…", "cdots" to "⋯",
    "vert" to "|", "Vert" to "‖"
)

private fun latexSpace(sym: Char): String? = when (sym) {
    ',' -> " "
    ';' -> "  "
    '!' -> ""
    else -> null
}

/** Читает группу {…} начиная с pos. Возвращает (содержимое, индекс после). */
private fun readBraceGroup(src: String, pos: Int): Pair<String, Int> {
    if (pos >= src.length || src[pos] != '{') {
        return if (pos < src.length) src[pos].toString() to (pos + 1) else "" to pos
    }
    var depth = 0
    var j = pos
    while (j < src.length) {
        when (src[j]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return src.substring(pos + 1, j) to (j + 1)
            }
        }
        j++
    }
    return src.substring(pos + 1) to src.length
}

/** Оборачивает содержимое дроби в скобки, если оно длиннее одного символа. */
private fun wrapFrac(content: AnnotatedString): AnnotatedString {
    val text = content.text
    return if (text.length > 1 && !text.startsWith("(") && !text.startsWith("‾")) {
        buildAnnotatedString {
            append("(")
            append(content)
            append(")")
        }
    } else {
        content
    }
}

/** Рендерит подмножество LaTeX в стилизованную строку. */
internal fun renderLatex(src: String, color: Color): AnnotatedString = buildAnnotatedString {
    val base = SpanStyle(color = color, fontWeight = FontWeight.Medium)
    var i = 0
    while (i < src.length) {
        val c = src[i]
        when {
            c == '\\' -> {
                var j = i + 1
                while (j < src.length && src[j].isLetter()) j++
                val cmd = src.substring(i + 1, j)
                when {
                    // \, \; \! — тонкие пробелы; \\ и т.п.
                    cmd.isEmpty() -> {
                        if (j < src.length) {
                            append(latexSpace(src[j]) ?: src[j].toString())
                            j++
                        }
                        i = j
                    }
                    // Дроби
                    cmd == "frac" || cmd == "dfrac" || cmd == "tfrac" -> {
                        val a = readBraceGroup(src, j)
                        val b = readBraceGroup(src, a.second)
                        withStyle(base) { append(wrapFrac(renderLatex(a.first, color))) }
                        withStyle(base) { append("⁄") }
                        withStyle(base) { append(wrapFrac(renderLatex(b.first, color))) }
                        i = b.second
                    }
                    // \left( \right) — просто скобки (\left. — невидимая)
                    cmd == "left" || cmd == "right" -> {
                        if (j < src.length && src[j] != '.') {
                            withStyle(base) { append(src[j]) }
                            i = j + 1
                        } else {
                            i = j + 1
                        }
                    }
                    // \operatorname{ctg}, \text{...}, \mathrm{...}
                    cmd == "operatorname" || cmd == "text" || cmd == "mathrm" || cmd == "mathbf" -> {
                        val g = readBraceGroup(src, j)
                        withStyle(base) { append(g.first) }
                        i = g.second
                    }
                    // \overline{xxxx} — черта над символами
                    cmd == "overline" || cmd == "bar" -> {
                        val g = readBraceGroup(src, j)
                        withStyle(base) {
                            g.first.forEach { ch -> append(ch.toString() + "\u0304") }
                        }
                        i = g.second
                    }
                    // \sqrt — корень, дальше содержимое идёт как есть
                    cmd == "sqrt" -> {
                        withStyle(base) { append("√") }
                        i = j
                    }
                    // Обычная команда из словаря
                    else -> {
                        withStyle(base) { append(LATEX_COMMANDS[cmd] ?: cmd) }
                        i = j
                    }
                }
            }
            c == '^' || c == '_' -> {
                val st = if (c == '^') {
                    SpanStyle(
                        color = color, baselineShift = BaselineShift.Superscript,
                        fontSize = 11.sp, fontWeight = FontWeight.Medium
                    )
                } else {
                    SpanStyle(
                        color = color, baselineShift = BaselineShift.Subscript,
                        fontSize = 11.sp, fontWeight = FontWeight.Medium
                    )
                }
                val j = i + 1
                when {
                    // ^{группа}
                    j < src.length && src[j] == '{' -> {
                        val g = readBraceGroup(src, j)
                        withStyle(st) { append(renderLatex(g.first, color)) }
                        i = g.second
                    }
                    // ^\circ и другие степени-команды
                    j < src.length && src[j] == '\\' -> {
                        var k = j + 1
                        while (k < src.length && src[k].isLetter()) k++
                        val cmd = src.substring(j + 1, k)
                        withStyle(st) { append(LATEX_COMMANDS[cmd] ?: cmd) }
                        i = k
                    }
                    // ^x — одиночный символ
                    j < src.length -> {
                        withStyle(st) { append(src[j]) }
                        i = j + 1
                    }
                    else -> i = j
                }
            }
            c == '{' || c == '}' -> i++
            else -> {
                withStyle(base) { append(c) }
                i++
            }
        }
    }
}