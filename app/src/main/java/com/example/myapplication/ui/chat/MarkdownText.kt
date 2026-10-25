package com.example.myapplication.ui.chat

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/** 轻量 Markdown：粗体、行内代码、链接与图片语法中的 URL。 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, color, linkColor) { parseMarkdown(text, color, linkColor) }
    val uriHandler = LocalUriHandler.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(
        text = annotated,
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { pos ->
                val layoutResult = layout ?: return@detectTapGestures
                val offset = layoutResult.getOffsetForPosition(pos)
                annotated.getStringAnnotations("URL", offset, offset)
                    .firstOrNull()
                    ?.let { uriHandler.openUri(it.item) }
            }
        },
        style = style,
        onTextLayout = { layout = it },
    )
}

internal fun parseMarkdown(source: String, color: Color, linkColor: Color): AnnotatedString {
    val cleaned = source
        .replace(Regex("""!\[([^\]]*)]\(([^)]+)\)"""), "$1 ($2)")
        .replace(Regex("""^\s*[-*]\s+""", RegexOption.MULTILINE), "• ")
    return buildAnnotatedString {
        withStyle(SpanStyle(color = color)) {
            var i = 0
            while (i < cleaned.length) {
                when {
                    cleaned.startsWith("**", i) -> {
                        val end = cleaned.indexOf("**", i + 2)
                        if (end > i) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) {
                                append(cleaned.substring(i + 2, end))
                            }
                            i = end + 2
                        } else {
                            append(cleaned[i])
                            i++
                        }
                    }
                    cleaned.startsWith("`", i) -> {
                        val end = cleaned.indexOf('`', i + 1)
                        if (end > i) {
                            withStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = Color(0x22000000),
                                    color = color,
                                ),
                            ) {
                                append(cleaned.substring(i + 1, end))
                            }
                            i = end + 1
                        } else {
                            append(cleaned[i])
                            i++
                        }
                    }
                    cleaned.startsWith("[", i) -> {
                        val close = cleaned.indexOf(']', i + 1)
                        val openParen = if (close >= 0) cleaned.indexOf('(', close) else -1
                        val closeParen = if (openParen >= 0) cleaned.indexOf(')', openParen) else -1
                        if (close > i && openParen == close + 1 && closeParen > openParen) {
                            val label = cleaned.substring(i + 1, close)
                            val url = cleaned.substring(openParen + 1, closeParen)
                            pushStringAnnotation("URL", url)
                            withStyle(
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ) {
                                append(label)
                            }
                            pop()
                            i = closeParen + 1
                        } else {
                            append(cleaned[i])
                            i++
                        }
                    }
                    else -> {
                        append(cleaned[i])
                        i++
                    }
                }
            }
        }
    }
}

fun extractMarkdownImageUrls(text: String): List<String> =
    Regex("""!\[[^\]]*]\(([^)]+)\)""").findAll(text).map { it.groupValues[1] }.toList()
