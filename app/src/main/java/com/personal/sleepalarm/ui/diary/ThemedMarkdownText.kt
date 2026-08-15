package com.personal.sleepalarm.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Indent(val text: String) : MdBlock()
    data class Bullet(val text: String) : MdBlock()
    data class Numbered(val number: String, val text: String) : MdBlock()
    data class CodeBlock(val code: String) : MdBlock()
    data class MathBlock(val code: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    object Hr : MdBlock()
}

private fun parseMarkdown(src: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    var codeAcc: StringBuilder? = null
    var mathAcc: StringBuilder? = null
    val numberRegex = Regex("^(\\d+)\\. (.*)")

    val lines = src.split("\n")
    var index = 0
    while (index < lines.size) {
        val raw = lines[index]
        val trimmed = raw.trimStart()

        try {
            if (codeAcc != null) {
                if (trimmed.contains("```")) {
                    blocks += MdBlock.CodeBlock(codeAcc.toString().trimEnd('\n'))
                    codeAcc = null
                } else {
                    codeAcc.append(raw).append('\n')
                }
                index++
            } else if (mathAcc != null) {
                if (trimmed.contains("\\]")) {
                    mathAcc.append(' ').append(trimmed.substringBefore("\\]"))
                    blocks += MdBlock.MathBlock(mathAcc.toString())
                    mathAcc = null
                } else {
                    mathAcc.append(' ').append(trimmed)
                }
                index++
            } else if (trimmed.startsWith("```")) {
                codeAcc = StringBuilder()
                index++
            } else if (trimmed.startsWith("\\[")) {
                val rest = trimmed.removePrefix("\\[")
                if (rest.contains("\\]")) {
                    blocks += MdBlock.MathBlock(rest.substringBefore("\\]"))
                } else {
                    mathAcc = StringBuilder(rest)
                }
                index++
            } else {
                val isIndented = raw.startsWith("\t") || raw.startsWith("    ")

                when {
                    trimmed.isEmpty() -> {}
                    trimmed == "---" || trimmed == "***" || trimmed == "___" -> blocks += MdBlock.Hr
                    trimmed.startsWith("### ") -> blocks += MdBlock.Heading(3, trimmed.removePrefix("### "))
                    trimmed.startsWith("## ") -> blocks += MdBlock.Heading(2, trimmed.removePrefix("## "))
                    trimmed.startsWith("# ") -> blocks += MdBlock.Heading(1, trimmed.removePrefix("# "))
                    trimmed.startsWith("> ") -> blocks += MdBlock.Quote(trimmed.removePrefix("> "))
                    trimmed.startsWith("- [ ] ") -> blocks += MdBlock.Bullet("☐  " + trimmed.removePrefix("- [ ] "))
                    trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") ->
                        blocks += MdBlock.Bullet("☑  " + trimmed.removePrefix("- [x] ").removePrefix("- [X] "))
                    trimmed.startsWith("- ") -> blocks += MdBlock.Bullet(trimmed.removePrefix("- "))
                    trimmed.startsWith("* ") -> blocks += MdBlock.Bullet(trimmed.removePrefix("* "))
                    numberRegex.matchEntire(trimmed) != null -> {
                        val m = numberRegex.matchEntire(trimmed)!!
                        blocks += MdBlock.Numbered(m.groupValues[1], m.groupValues[2])
                    }
                    isIndented && trimmed.isNotEmpty() -> {
                        val last = blocks.lastOrNull()
                        when {
                            last is MdBlock.Heading || last is MdBlock.Indent -> {
                                if (last is MdBlock.Indent) {
                                    blocks[blocks.lastIndex] = last.copy(text = last.text + "\n" + trimmed)
                                } else {
                                    blocks += MdBlock.Indent(trimmed)
                                }
                            }
                            else -> blocks += MdBlock.Paragraph(raw)
                        }
                    }
                    else -> blocks += MdBlock.Paragraph(raw)
                }
                index++
            }
        } catch (e: Exception) {
            if (codeAcc == null && mathAcc == null) {
                blocks += MdBlock.Paragraph(raw)
            }
            index++
        }
    }
    codeAcc?.let { blocks += MdBlock.CodeBlock(it.toString().trimEnd('\n')) }
    mathAcc?.let { blocks += MdBlock.MathBlock(it.toString()) }
    return blocks
}

private fun buildInline(
    text: String,
    scheme: androidx.compose.material3.ColorScheme
): AnnotatedString = buildAnnotatedString {
    var remaining = text
    var iterations = 0
    val maxIterations = text.length + 10

    while (remaining.isNotEmpty() && iterations < maxIterations) {
        iterations++

        // LaTeX inline: \( ... \) — ПЕРВЫМ, чтобы поглотить содержимое целиком
        val latexInline = Regex("\\\\\\((.+?)\\\\\\)").find(remaining)

        val mathMatch = Regex("\\$(.+?)\\$").find(remaining)
        val mathExprMatch = Regex("\\d+(?:[.,]\\d+)?\\s?(?:[+×÷=<>≤≥±]\\s?\\d+(?:[.,]\\d+)?)+").find(remaining)
        val mathSymMatch = Regex("[×÷≠≈≤≥±√∞∑∏∫∂∇≡∈∉⊂⊃∪∩∀∃∴πΔΩαβγθλμσφω²³¹⁰⁴⁵⁶⁷⁸⁹₀₁₂₃₄₅₆₇₈₉]").find(remaining)

        val boldItalicMatch = Regex("\\*\\*\\*(.+?)\\*\\*\\*").find(remaining)
        val boldMatch = Regex("\\*\\*(.+?)\\*\\*").find(remaining)
        val italicMatch = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)").find(remaining)
        val codeMatch = Regex("`(.+?)`").find(remaining)
        val strikeMatch = Regex("~~(.+?)~~").find(remaining)
        val highlightMatch = Regex("==(.+?)==").find(remaining)
        val linkMatch = Regex("\\[(.+?)]\\((.+?)\\)").find(remaining)

        val mathStyle = SpanStyle(color = scheme.tertiary, fontWeight = FontWeight.Medium)

        val matches = listOfNotNull(
            latexInline?.let { it to "latex" },
            mathMatch?.let { it to "math" },
            mathExprMatch?.let { it to "mathexpr" },
            mathSymMatch?.let { it to "mathsym" },
            boldItalicMatch?.let { it to "bolditalic" },
            boldMatch?.let { it to "bold" },
            italicMatch?.let { it to "italic" },
            codeMatch?.let { it to "code" },
            strikeMatch?.let { it to "strike" },
            highlightMatch?.let { it to "highlight" },
            linkMatch?.let { it to "link" }
        ).sortedBy { it.first.range.first }

        if (matches.isEmpty()) {
            append(remaining)
            break
        }

        val (match, type) = matches.first()
        if (match.range.first > 0) append(remaining.substring(0, match.range.first))

        when (type) {
            "latex" -> append(renderLatex(match.groupValues[1], scheme.tertiary))
            "math" -> append(renderLatex(match.groupValues[1], scheme.tertiary))
            "mathexpr" -> withStyle(mathStyle) { append(match.value) }
            "mathsym" -> withStyle(mathStyle) { append(match.value) }
            "bold" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = scheme.onBackground)) {
                append(match.groupValues[1])
            }
            "italic" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = scheme.onSurfaceVariant)) {
                append(match.groupValues[1])
            }
            "bolditalic" -> withStyle(SpanStyle(
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                color = scheme.primary
            )) {
                append(match.groupValues[1])
            }
            "code" -> withStyle(SpanStyle(
                fontFamily = FontFamily.Monospace,
                color = scheme.error,
                background = scheme.surfaceVariant
            )) {
                append(" ${match.groupValues[1]} ")
            }
            "strike" -> withStyle(SpanStyle(
                textDecoration = TextDecoration.LineThrough,
                color = scheme.onSurfaceVariant
            )) {
                append(match.groupValues[1])
            }
            "highlight" -> withStyle(SpanStyle(
                background = scheme.tertiaryContainer,
                color = scheme.onTertiaryContainer
            )) {
                append(match.groupValues[1])
            }
            "link" -> withStyle(SpanStyle(
                color = scheme.primary,
                textDecoration = TextDecoration.Underline
            )) {
                append(match.groupValues[1])
            }
        }
        remaining = remaining.substring(match.range.last + 1)
    }
}

@Composable
fun ThemedMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    val scheme = MaterialTheme.colorScheme
    val typo = MaterialTheme.typography
    val blocks = remember(markdown) {
        try {
            parseMarkdown(markdown)
        } catch (e: Exception) {
            listOf(MdBlock.Paragraph(markdown))
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    Column {
                        Text(
                            text = buildInline(block.text, scheme),
                            style = when (block.level) {
                                1 -> typo.headlineMedium
                                2 -> typo.headlineSmall
                                else -> typo.titleLarge
                            }.copy(color = scheme.primary, fontWeight = FontWeight.Bold),
                            maxLines = maxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(scheme.onSurface.copy(alpha = 0.3f))
                        )
                    }
                }

                is MdBlock.Quote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(scheme.primary.copy(alpha = 0.6f))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = buildInline(block.text, scheme),
                            style = typo.bodyLarge.copy(
                                color = scheme.onSurfaceVariant,
                                fontStyle = FontStyle.Italic
                            ),
                            maxLines = maxLines,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MdBlock.Indent -> {
                    Text(
                        text = buildInline(block.text, scheme),
                        style = typo.bodyLarge.copy(
                            color = scheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        ),
                        modifier = Modifier.padding(start = 16.dp),
                        maxLines = maxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                is MdBlock.Bullet -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "•  ",
                            color = scheme.tertiary,
                            style = typo.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            buildInline(block.text, scheme),
                            style = typo.bodyLarge.copy(color = scheme.onBackground),
                            maxLines = maxLines,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MdBlock.Numbered -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${block.number}. ",
                            color = scheme.tertiary,
                            style = typo.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            buildInline(block.text, scheme),
                            style = typo.bodyLarge.copy(color = scheme.onBackground),
                            maxLines = maxLines,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MdBlock.CodeBlock -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(12.dp)
                    ) {
                        Text(
                            block.code,
                            style = typo.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        )
                    }
                }

                is MdBlock.MathBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = renderLatex(block.code, scheme.tertiary),
                            style = typo.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is MdBlock.Hr -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .height(1.5.dp)
                            .background(scheme.onSurface.copy(alpha = 0.55f))
                    )
                }
                is MdBlock.Paragraph -> {
                    Text(
                        buildInline(block.text, scheme),
                        style = typo.bodyLarge.copy(color = scheme.onBackground),
                        maxLines = maxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}