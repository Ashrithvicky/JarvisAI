package com.privateai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MarkdownElement {
    data class Header(val level: Int, val text: String) : MarkdownElement()
    data class Paragraph(val text: String) : MarkdownElement()
    data class BulletItem(val text: String) : MarkdownElement()
    data class NumberedItem(val number: String, val text: String) : MarkdownElement()
    data class Code(val language: String?, val code: String) : MarkdownElement()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownElement()
}

@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val elements = remember(content) { parseMarkdown(content) }

    Column(modifier = modifier.fillMaxWidth()) {
        elements.forEachIndexed { index, element ->
            when (element) {
                is MarkdownElement.Header -> {
                    val style = when (element.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = buildInlineMarkdown(element.text, textColor),
                        style = style,
                        color = textColor,
                        modifier = Modifier.padding(top = if (index > 0) 8.dp else 0.dp, bottom = 4.dp)
                    )
                }

                is MarkdownElement.BulletItem -> {
                    Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
                        Text(text = "• ", style = MaterialTheme.typography.bodyMedium, color = textColor)
                        Text(
                            text = buildInlineMarkdown(element.text, textColor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                }

                is MarkdownElement.NumberedItem -> {
                    Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
                        Text(text = "${element.number} ", style = MaterialTheme.typography.bodyMedium, color = textColor)
                        Text(
                            text = buildInlineMarkdown(element.text, textColor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                }

                is MarkdownElement.Code -> {
                    CodeBlock(code = element.code, language = element.language)
                }

                is MarkdownElement.Table -> {
                    MarkdownTableView(table = element, textColor = textColor)
                }

                is MarkdownElement.Paragraph -> {
                    if (element.text.isNotBlank()) {
                        Text(
                            text = buildInlineMarkdown(element.text, textColor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownTableView(
    table: MarkdownElement.Table,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.horizontalScroll(scrollState)) {
            // Table Header Row
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                table.headers.forEach { header ->
                    Box(
                        modifier = Modifier
                            .widthIn(min = 100.dp)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = buildInlineMarkdown(header.trim(), MaterialTheme.colorScheme.onSurfaceVariant),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Table Data Rows
            table.rows.forEachIndexed { rowIndex, row ->
                val rowBg = if (rowIndex % 2 == 1) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                } else {
                    Color.Transparent
                }

                Row(
                    modifier = Modifier
                        .background(rowBg)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    row.forEachIndexed { colIndex, cell ->
                        Box(
                            modifier = Modifier
                                .widthIn(min = 100.dp)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = buildInlineMarkdown(cell.trim(), textColor),
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor
                            )
                        }
                    }
                }

                if (rowIndex < table.rows.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

fun parseMarkdown(raw: String): List<MarkdownElement> {
    val result = mutableListOf<MarkdownElement>()
    val lines = raw.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // 1. Check for fenced code block (Streaming-Safe: handles unclosed fences)
        if (line.trimStart().startsWith("```")) {
            val lang = line.trimStart().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // skip closing ```
            result.add(MarkdownElement.Code(language = lang.ifBlank { null }, code = codeLines.joinToString("\n")))
            continue
        }

        // 2. Check for Table block
        if (isTableRow(line) && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
            val headers = parseCells(line)
            i += 2 // skip header and separator
            val rows = mutableListOf<List<String>>()

            while (i < lines.size && isTableRow(lines[i])) {
                val rowCells = parseCells(lines[i])
                if (rowCells.isNotEmpty()) {
                    rows.add(rowCells)
                }
                i++
            }

            result.add(MarkdownElement.Table(headers = headers, rows = rows))
            continue
        }

        // 3. Check for headers
        val trimmed = line.trim()
        if (trimmed.startsWith("### ")) {
            result.add(MarkdownElement.Header(level = 3, text = trimmed.removePrefix("### ")))
            i++
            continue
        } else if (trimmed.startsWith("## ")) {
            result.add(MarkdownElement.Header(level = 2, text = trimmed.removePrefix("## ")))
            i++
            continue
        } else if (trimmed.startsWith("# ")) {
            result.add(MarkdownElement.Header(level = 1, text = trimmed.removePrefix("# ")))
            i++
            continue
        }

        // 4. Bullet list
        if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
            val bulletText = trimmed.substring(2)
            result.add(MarkdownElement.BulletItem(bulletText))
            i++
            continue
        }

        // 5. Numbered list (e.g. "1. ")
        val numberMatch = Regex("^(\\d+[.)])\\s+(.*)").find(trimmed)
        if (numberMatch != null) {
            val num = numberMatch.groupValues[1]
            val text = numberMatch.groupValues[2]
            result.add(MarkdownElement.NumberedItem(num, text))
            i++
            continue
        }

        // 6. Normal paragraph
        result.add(MarkdownElement.Paragraph(line))
        i++
    }

    return result
}

private fun isTableRow(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.startsWith("|") && trimmed.indexOf("|", 1) >= 0
}

private fun isTableSeparator(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.startsWith("|")) return false
    val inner = trimmed.replace("|", "").trim()
    return inner.all { it == '-' || it == ':' || it == ' ' } && inner.contains("-")
}

private fun parseCells(rowLine: String): List<String> {
    val trimmed = rowLine.trim()
    val rawCells = trimmed.split("|")
    val contentCells = rawCells.filterIndexed { index, _ ->
        // drop outer empty tokens if line started/ended with '|'
        !(index == 0 && trimmed.startsWith("|")) &&
                !(index == rawCells.size - 1 && trimmed.endsWith("|"))
    }
    return contentCells.map { it.trim() }
}

/**
 * Parses inline formatting: **bold**, *italic*, and `inline code`
 * Safe against unclosed streaming tokens.
 */
fun buildInlineMarkdown(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0

        val pattern = Regex("(`[^`]+`|\\*\\*[^\\*]+\\*\\*|\\*[\\*]*[^\\*]+\\*)")
        val matches = pattern.findAll(text)

        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1

            if (start > currentIndex) {
                append(text.substring(currentIndex, start))
            }

            val token = match.value
            when {
                token.startsWith("`") && token.endsWith("`") -> {
                    val codeText = token.removeSurrounding("`")
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x33888888),
                            color = Color(0xFFE06C75),
                            fontSize = 13.sp
                        )
                    )
                    append(" $codeText ")
                    pop()
                }
                token.startsWith("**") && token.endsWith("**") -> {
                    val boldText = token.removeSurrounding("**")
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(boldText)
                    pop()
                }
                token.startsWith("*") && token.endsWith("*") -> {
                    val italicText = token.removeSurrounding("*")
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(italicText)
                    pop()
                }
                else -> append(token)
            }

            currentIndex = end
        }

        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}
