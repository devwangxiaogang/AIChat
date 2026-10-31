package com.yuguang.lumo.ui.chat

import android.content.ClipData
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuguang.lumo.R
import kotlinx.coroutines.launch

/** 轻量 Markdown：粗体、行内代码、链接、列表，以及围栏代码块。 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val blocks = remember(text) { splitMarkdownBlocks(text) }
    if (blocks.none { it is MarkdownBlock.Code }) {
        InlineMarkdown(text = text.trim(), modifier = modifier, color = color, style = style)
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> {
                    if (block.text.isNotBlank()) {
                        InlineMarkdown(
                            text = block.text.trim(),
                            modifier = Modifier.fillMaxWidth(),
                            color = color,
                            style = style,
                        )
                    }
                }
                is MarkdownBlock.Code -> CodeBlock(block = block, color = color)
            }
        }
    }
}

@Composable
private fun InlineMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    style: TextStyle,
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

/** 代码块单独渲染，等宽字体、可横向滚动、一键复制。 */
@Composable
private fun CodeBlock(block: MarkdownBlock.Code, color: Color) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copyLabel = stringResource(R.string.copy_code)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 2.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = block.language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipData.newPlainText(copyLabel, block.code).toClipEntry())
                        }
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = copyLabel,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = block.code,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                ),
                color = color,
            )
        }
    }
}

internal sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Code(val language: String, val code: String) : MarkdownBlock
}

/** 按 ``` 围栏把正文拆成段落和代码块。 */
internal fun splitMarkdownBlocks(source: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val text = StringBuilder()
    val code = StringBuilder()
    var language = ""
    var inCode = false
    fun flushText() {
        if (text.isNotBlank()) blocks += MarkdownBlock.Paragraph(text.toString())
        text.clear()
    }
    fun flushCode() {
        blocks += MarkdownBlock.Code(language, code.toString().trimEnd())
        code.clear()
        language = ""
    }
    for (line in source.lines()) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (inCode) {
                flushCode()
                inCode = false
            } else {
                flushText()
                language = trimmed.removePrefix("```").trim()
                inCode = true
            }
            continue
        }
        if (inCode) code.appendLine(line) else text.appendLine(line)
    }
    if (inCode) flushCode() else flushText()
    return blocks
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

/** 只认 http(s)，避免把模型给出的相对路径或奇怪 scheme 交给图片加载器。 */
fun extractMarkdownImageUrls(text: String): List<String> =
    Regex("""!\[[^\]]*]\(([^)]+)\)""")
        .findAll(text)
        .map { it.groupValues[1].trim() }
        .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        .toList()
