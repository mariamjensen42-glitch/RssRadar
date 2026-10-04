package com.cycling.rssradar.ui.article

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.cycling.rssradar.core.model.ReadingFontFamily
import com.cycling.rssradar.core.model.ReadingStyleState
import com.cycling.rssradar.core.model.ReadingTextAlign
import com.cycling.rssradar.core.ui.theme.radarColors

/**
 * 行内片段 → AnnotatedString。
 * 链接用 LinkAnnotation.Url，点击经 LocalUriHandler（见 [ArticleNativeReader]）统一走 onLinkClick。
 */
@Composable
internal fun runsToAnnotated(runs: List<InlineRun>, style: ReadingStyleState): AnnotatedString {
    val overlays = rememberBlockOverlays(runs)
    return buildAnnotatedString {
        val baseFamily = style.fontFamily.toComposeFontFamily()
        val builder = this
        fun applyOverlays(from: Int, to: Int) {
            if (overlays.isEmpty()) return
            overlays.forEach { overlay ->
                val start = maxOf(overlay.start, from)
                val end = minOf(overlay.end, to)
                if (start < end) builder.addStyle(SpanStyle(background = Color(overlay.color)), start, end)
            }
        }
        for (run in runs) {
            when (run) {
                is InlineText -> {
                    pushStyle(
                        SpanStyle(
                            fontWeight = if (run.bold) FontWeight.Bold else null,
                            fontStyle = if (run.italic) FontStyle.Italic else null,
                            fontFamily = if (run.code) FontFamily.Monospace else baseFamily,
                            background = when {
                                run.code -> radarColors().surface2
                                run.mark -> MarkHighlight
                                else -> Color.Unspecified
                            },
                            color = run.color?.let { Color(it) } ?: Color.Unspecified,
                            textDecoration = when {
                                run.strike && run.underline -> TextDecoration.combine(
                                    listOf(TextDecoration.LineThrough, TextDecoration.Underline),
                                )
                                run.strike -> TextDecoration.LineThrough
                                run.underline -> TextDecoration.Underline
                                else -> null
                            },
                            // <sup>/<sub>：真上标/下标（脚注、化学式、指数），字号收一档
                            baselineShift = when (run.script) {
                                MathScript.NORMAL -> null
                                MathScript.SUPER -> BaselineShift.Superscript
                                MathScript.SUB -> BaselineShift.Subscript
                            },
                            fontSize = when {
                                run.script != MathScript.NORMAL -> (style.fontSize * SCRIPT_SIZE_FACTOR).sp
                                run.small -> (style.fontSize * SMALL_SIZE_FACTOR).sp
                                else -> TextUnit.Unspecified
                            },
                        ),
                    )
                    val runStart = length
                    append(run.text)
                    pop()
                    applyOverlays(runStart, length)
                }
                is InlineMath -> mathSpans(run.spans, style)
                is InlineLink -> {
                    val start = length
                    pushStyle(
                        SpanStyle(
                            color = radarColors().link,
                            textDecoration = TextDecoration.Underline,
                            fontFamily = baseFamily,
                        ),
                    )
                    append(run.text)
                    pop()
                    addLink(LinkAnnotation.Url(run.url), start, length)
                }
            }
        }
    }
}

/** 上下标相对正文的字号比例。 */
private const val SCRIPT_SIZE_FACTOR = 0.75f

/** <small> 相对正文的字号比例。 */
private const val SMALL_SIZE_FACTOR = 0.85f

/** mark / style background-color 的高亮底色：半透明琥珀，深浅主题下都可见。 */
private val MarkHighlight = Color(0x66FFC107)

/** 解析端的段落对齐枚举 → Compose TextAlign。 */
internal fun ReadingTextAlign.toComposeAlign(): TextAlign = when (this) {
    ReadingTextAlign.START -> TextAlign.Start
    ReadingTextAlign.JUSTIFY -> TextAlign.Justify
    ReadingTextAlign.CENTER -> TextAlign.Center
    ReadingTextAlign.END -> TextAlign.End
}

internal fun ParagraphAlign?.toCompose(): TextAlign? = when (this) {
    ParagraphAlign.LEFT -> TextAlign.Left
    ParagraphAlign.CENTER -> TextAlign.Center
    ParagraphAlign.RIGHT -> TextAlign.Right
    ParagraphAlign.JUSTIFY -> TextAlign.Justify
    null -> null
}

/** 公式片段 → SpanStyle 序列：变量斜体、上下标缩放。 */
private fun AnnotatedString.Builder.mathSpans(
    spans: List<MathSpan>,
    style: ReadingStyleState,
) {
    for (span in spans) {
        pushStyle(
            SpanStyle(
                fontStyle = if (span.italic) FontStyle.Italic else null,
                baselineShift = when (span.script) {
                    MathScript.NORMAL -> null
                    MathScript.SUPER -> BaselineShift.Superscript
                    MathScript.SUB -> BaselineShift.Subscript
                },
                fontSize = if (span.script == MathScript.NORMAL) {
                    TextUnit.Unspecified
                } else {
                    (style.fontSize * SCRIPT_SIZE_FACTOR).sp
                },
            ),
        )
        append(span.text)
        pop()
    }
}

/** 块级公式 → AnnotatedString。 */
internal fun mathToAnnotated(spans: List<MathSpan>, style: ReadingStyleState): AnnotatedString =
    buildAnnotatedString { mathSpans(spans, style) }

/** Store 层的纯 JVM 字体族枚举 → Compose FontFamily。 */
internal fun ReadingFontFamily.toComposeFontFamily(): FontFamily = when (this) {
    ReadingFontFamily.SYSTEM -> FontFamily.Default
    ReadingFontFamily.SERIF -> FontFamily.Serif
    ReadingFontFamily.MONOSPACE -> FontFamily.Monospace
}
