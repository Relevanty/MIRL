package com.personal.sleepalarm.ui.diary

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

class MarkdownVisualTransformation(
    private val headingColor: Color,
    private val quoteColor: Color,
    private val listColor: Color,
    private val codeColor: Color,
    private val codeBackground: Color,
    private val boldColor: Color,
    private val italicColor: Color,
    private val mathColor: Color
) : VisualTransformation {

    private val invisible = SpanStyle(color = Color.Transparent)
    private val mathBase by lazy {
        SpanStyle(color = mathColor, fontWeight = FontWeight.Medium)
    }

    override fun filter(text: AnnotatedString): TransformedText = try {
        val styled = buildStyled(text.text)
        TransformedText(styled, SafeMapping(styled.length))
    } catch (_: Exception) {
        TransformedText(text, SafeMapping(text.length))
    }

    private class SafeMapping(private val length: Int) : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int = offset.coerceIn(0, length)
        override fun transformedToOriginal(offset: Int): Int = offset.coerceIn(0, length)
    }

    private fun buildStyled(text: String): AnnotatedString = buildAnnotatedString {
        append(text)
        var lineStart = 0
        var prevContext = "text"
        val lines = text.split("\n")
        for ((i, line) in lines.withIndex()) {
            prevContext = styleLine(line, lineStart, prevContext)
            lineStart += line.length + 1
        }
    }

    private fun AnnotatedString.Builder.styleLine(
        line: String,
        start: Int,
        prevContext: String
    ): String {
        // Пустая строка внутри math-блока не прерывает его
        if (line.isEmpty()) {
            return if (prevContext == "mathblock") "mathblock" else "text"
        }

        val leadingWs = line.length - line.trimStart().length
        val trimmed = line.trimStart()
        val contentStart = start + leadingWs
        val lineEnd = start + line.length

        // === Внутри многострочного \[ ... \] ===
        if (prevContext == "mathblock") {
            addStyle(mathBase, start, lineEnd)
            hideBracesAndSupSub(line, start)
            val closeIdx = trimmed.indexOf("\\]")
            if (closeIdx >= 0) {
                addStyle(invisible, contentStart + closeIdx, contentStart + closeIdx + 2)
                return "text"
            }
            return "mathblock"
        }

        // === Начало блочной формулы \[ ===
        if (trimmed.startsWith("\\[")) {
            val rest = trimmed.removePrefix("\\[")
            if (rest.contains("\\]")) {
                // Однострочная \[ ... \] — обрабатываем как инлайн
                applyInline(line, start)
                return "text"
            }
            addStyle(mathBase, contentStart, lineEnd)
            addStyle(invisible, contentStart, contentStart + 2) // скрыть \[
            hideBracesAndSupSub(line, start)
            return "mathblock"
        }

        // === Есть ли в строке latex/математика ===
        val hasMath = trimmed.contains("\\(") || trimmed.contains("\\)") ||
                trimmed.contains("\\[") || trimmed.contains("\\]") ||
                trimmed.contains("$")

        val isIndented = line.startsWith("\t") || line.startsWith("    ")

        return when {
            trimmed.startsWith("### ") -> {
                addStyle(invisible, contentStart, contentStart + 4)
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = headingColor),
                    contentStart + 4, lineEnd
                )
                "heading"
            }
            trimmed.startsWith("## ") -> {
                addStyle(invisible, contentStart, contentStart + 3)
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = headingColor),
                    contentStart + 3, lineEnd
                )
                "heading"
            }
            trimmed.startsWith("# ") -> {
                addStyle(invisible, contentStart, contentStart + 2)
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, color = headingColor),
                    contentStart + 2, lineEnd
                )
                "heading"
            }
            trimmed.startsWith("> ") -> {
                addStyle(invisible, contentStart, contentStart + 2)
                addStyle(
                    SpanStyle(fontStyle = FontStyle.Italic, color = quoteColor),
                    contentStart + 2, lineEnd
                )
                "quote"
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                addStyle(
                    SpanStyle(color = listColor, fontWeight = FontWeight.Bold),
                    contentStart, contentStart + 2
                )
                addStyle(SpanStyle(color = listColor), contentStart + 2, lineEnd)
                applyInline(trimmed.removePrefix("- ").removePrefix("* "), contentStart + 2)
                "list"
            }
            Regex("^\\d+\\. ").containsMatchIn(trimmed) -> {
                addStyle(SpanStyle(color = listColor), start, lineEnd)
                applyInline(line, start)
                "list"
            }
            // Горизонтальная линия: ---, ***, ___ (3 и более символов)
            Regex("^(-{3,}|\\*{3,}|_{3,})$").matches(trimmed) -> {
                addStyle(
                    SpanStyle(color = quoteColor, letterSpacing = 8.sp),
                    start, lineEnd
                )
                "hr"
            }
            // Отступ = код ТОЛЬКО если в строке нет математики
            isIndented && trimmed.isNotEmpty() && !hasMath -> {
                if (prevContext == "heading" || prevContext == "indent") {
                    addStyle(
                        SpanStyle(fontStyle = FontStyle.Italic, color = quoteColor),
                        start, lineEnd
                    )
                    "indent"
                } else {
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = codeColor.copy(alpha = 0.7f),
                            background = codeBackground
                        ),
                        start, lineEnd
                    )
                    "code"
                }
            }
            else -> {
                applyInline(line, start)
                "text"
            }
        }
    }

    /** Скрывает {} и поднимает степени в строке внутри math-блока. */
    private fun AnnotatedString.Builder.hideBracesAndSupSub(line: String, base: Int) {
        for (k in line.indices) {
            if (line[k] == '{' || line[k] == '}') {
                addStyle(invisible, base + k, base + k + 1)
            }
        }
        applySupSub(line, base, 0, line.length)
    }

    private fun AnnotatedString.Builder.applyInline(line: String, base: Int) {
        data class M(val range: IntRange, val inner: IntRange, val type: String)

        val all = mutableListOf<M>()

        // LaTeX: \( ... \) и однострочные \[ ... \]
        Regex("\\\\\\((.+?)\\\\\\)").findAll(line).forEach { m ->
            all.add(M(m.range, m.groups[1]!!.range, "latex"))
        }
        Regex("\\\\\\[(.+?)\\\\\\]").findAll(line).forEach { m ->
            all.add(M(m.range, m.groups[1]!!.range, "latex"))
        }

        // Математика: $...$
        Regex("\\$(.+?)\\$").findAll(line).forEach { m ->
            all.add(M(m.range, m.groups[1]!!.range, "math"))
        }
        // Простые выражения: 2+2=4
        Regex("\\d+(?:[.,]\\d+)?\\s?(?:[+×÷=<>≤≥±]\\s?\\d+(?:[.,]\\d+)?)+").findAll(line).forEach { m ->
            all.add(M(m.range, m.range, "mathexpr"))
        }
        // Матсимволы
        Regex("[×÷≠≈≤≥±√∞∑∏∫∂∇≡∈∉⊂⊃∪∀∃∴∵πΔΩαβγθλμσφω²³¹⁰⁴⁶⁷⁸⁹₀₁₂₃₄₅₆₇₈₉]").findAll(line).forEach { m ->
            all.add(M(m.range, m.range, "mathsym"))
        }

        // Остальные стили
        Regex("\\*\\*\\*(.+?)\\*\\*\\*").findAll(line).forEach { m ->
            all.add(M(m.range, m.groups[1]!!.range, "bolditalic"))
        }
        Regex("\\*\\*(.+?)\\*\\*").findAll(line).forEach { m ->
            all.add(M(m.range, m.groups[1]!!.range, "bold"))
        }
        Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)").findAll(line).forEach { m ->
            all.add(M(m.range, m.groups[1]!!.range, "italic"))
        }
        Regex("`(.+?)`").findAll(line).forEach { m ->
            all.add(M(m.range, m.groups[1]!!.range, "code"))
        }
        Regex("~~(.+?)~~").findAll(line).forEach { m ->
            all.add(M(m.range, m.groups[1]!!.range, "strike"))
        }
        Regex("==(.+?)==").findAll(line).forEach { m ->
            all.add(M(m.range, m.groups[1]!!.range, "highlight"))
        }
        Regex("\\[(.+?)]\\((.+?)\\)").findAll(line).forEach { m ->
            all.add(M(m.range, m.groups[1]!!.range, "link"))
        }

        var lastEnd = -1
        all.sortedBy { it.range.first }.forEach { m ->
            if (m.range.first > lastEnd) {
                lastEnd = m.range.last

                val contentStyle = when (m.type) {
                    "latex", "math", "mathexpr", "mathsym" -> mathBase
                    "bold" -> SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)
                    "italic" -> SpanStyle(fontStyle = FontStyle.Italic, color = italicColor)
                    "bolditalic" -> SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        color = boldColor
                    )
                    "code" -> SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = codeColor,
                        background = codeBackground
                    )
                    "strike" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    "highlight" -> SpanStyle(background = codeBackground)
                    else -> SpanStyle(
                        color = headingColor,
                        textDecoration = TextDecoration.Underline
                    )
                }

                addStyle(contentStyle, base + m.inner.first, base + m.inner.last + 1)

                if (m.inner.first > m.range.first) {
                    addStyle(invisible, base + m.range.first, base + m.inner.first)
                }
                if (m.range.last + 1 > m.inner.last + 1) {
                    addStyle(invisible, base + m.inner.last + 1, base + m.range.last + 1)
                }

                // LaTeX: скрыть {} и живые степени/индексы
                if (m.type == "latex" || m.type == "math") {
                    for (k in m.inner) {
                        if (line[k] == '{' || line[k] == '}') {
                            addStyle(invisible, base + k, base + k + 1)
                        }
                    }
                    applySupSub(line, base, m.inner.first, m.inner.last + 1)
                }
            }
        }
    }

    /** Живые степени/индексы: ^2 → верх, _x → низ, маркеры скрыты. */
    private fun AnnotatedString.Builder.applySupSub(
        line: String,
        base: Int,
        from: Int,
        to: Int
    ) {
        var k = from
        while (k < to && k < line.length) {
            val ch = line[k]
            if (ch == '^' || ch == '_') {
                addStyle(invisible, base + k, base + k + 1)
                val argStart: Int
                val argEnd: Int
                if (k + 1 < to && line[k + 1] == '{') {
                    val close = line.indexOf('}', k + 2)
                    argStart = k + 2
                    argEnd = if (close in 0 until to) close else to
                } else {
                    argStart = k + 1
                    argEnd = (k + 1).coerceAtMost(to)
                }
                if (argEnd > argStart) {
                    val st = if (ch == '^') {
                        SpanStyle(
                            baselineShift = BaselineShift.Superscript,
                            fontSize = 11.sp, color = mathColor, fontWeight = FontWeight.Medium
                        )
                    } else {
                        SpanStyle(
                            baselineShift = BaselineShift.Subscript,
                            fontSize = 11.sp, color = mathColor, fontWeight = FontWeight.Medium
                        )
                    }
                    addStyle(st, base + argStart, base + argEnd)
                }
                k = if (k + 1 < to && line[k + 1] == '{') {
                    (line.indexOf('}', k + 2).takeIf { it in 0 until to } ?: (to - 1)) + 1
                } else {
                    argEnd
                }
            } else {
                k++
            }
        }
    }
}