package com.halo.app.ui.glass.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun HaloMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    if (markdown.isBlank()) return

    val lines = androidx.compose.runtime.remember(markdown) { markdown.lines() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var inCodeBlock = false
        val codeBuffer = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    CodeBlock(code = codeBuffer.toString().trimEnd())
                    codeBuffer.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                codeBuffer.append(line).append("\n")
                continue
            }

            when {
                trimmed.startsWith("# ") -> {
                    Text(
                        text = formattedText(trimmed.removePrefix("# ")),
                        style = MaterialTheme.typography.titleLarge,
                        color = textColor,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = formattedText(trimmed.removePrefix("## ")),
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("### ") -> {
                    Text(
                        text = formattedText(trimmed.removePrefix("### ")),
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    val content = trimmed.substring(2)
                    Row(
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("•", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = formattedText(content),
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }
                }
                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val parts = trimmed.split(Regex("\\s+"), limit = 2)
                    val num = parts.getOrNull(0) ?: "1."
                    val content = parts.getOrNull(1) ?: ""
                    Row(
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(num, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = formattedText(content),
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }
                }
                trimmed.isBlank() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                else -> {
                    Text(
                        text = formattedText(line),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }
            }
        }

        if (inCodeBlock && codeBuffer.isNotEmpty()) {
            CodeBlock(code = codeBuffer.toString().trimEnd())
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun formattedText(text: String): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceContainer
    val codeColor = MaterialTheme.colorScheme.primary
    return androidx.compose.runtime.remember(text, codeBackground, codeColor) {
        parseFormattedText(text, codeBackground, codeColor)
    }
}

private fun parseFormattedText(text: String, codeBackground: Color, codeColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = text.length

        while (i < len) {
            if (i + 1 < len && text[i] == '*' && text[i + 1] == '*') {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(parseFormattedText(text.substring(i + 2, end), codeBackground, codeColor))
                    }
                    i = end + 2
                    continue
                }
            }

            if (text[i] == '*' || text[i] == '_') {
                val delimiter = text[i]
                val end = text.indexOf(delimiter, i + 1)
                if (end != -1 && end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }

            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            color = codeColor
                        )
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }

            append(text[i])
            i++
        }
    }
}
