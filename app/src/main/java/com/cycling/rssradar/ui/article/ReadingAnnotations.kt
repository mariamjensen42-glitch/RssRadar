package com.cycling.rssradar.ui.article

import androidx.compose.runtime.staticCompositionLocalOf
import com.cycling.rssradar.core.data.db.ArticleAnnotationEntity
import com.cycling.rssradar.core.domain.annotation.AnnotationAnchor
import com.cycling.rssradar.core.domain.annotation.AnnotationPalette

/**
 * 当前文章的标注，供原生渲染器在行内文本上叠加底色。
 *
 * 走 CompositionLocal 而不是给十几个渲染函数加参数：标注只影响「行内文本怎么画」这一件事，
 * 而渲染函数有段落/标题/表格/引用/列表/定义列表等一大串，逐个加参数是纯噪音。
 *
 * 定位用**块内锚定**而非全文坐标：渲染树里每个块的文本就是它的 runs 拼接，
 * 与 [runsToAnnotated] 的追加顺序天然一致，所以只要在块内把引文找回来就够了，
 * 不需要维护「全文偏移 ↔ 块索引」的映射（那是页内查找才需要的东西）。
 */
internal val LocalReadingAnnotations = staticCompositionLocalOf<List<ArticleAnnotationEntity>> { emptyList() }

/**
 * 页内查找的活跃项：原生渲染器据此在行内文本上叠查找底色。
 *
 * [activeBlockText] 用**块文本**而不是 blockIndex 来标识活跃块——渲染树是递归的，
 * 行内文本渲染时不知道自己属于第几块，但它一定知道自己的文本。取到当前命中所在块的
 * 文本就能比对出「我是不是那个活跃块」，避免把块索引顺着十几层渲染函数传下去。
 */
internal data class FindHighlight(
    val query: String,
    val activeBlockText: String?,
    val activeLocalStart: Int,
)

internal val LocalFindHighlight = staticCompositionLocalOf<FindHighlight?> { null }

internal object ReadingAnnotations {

    data class Overlay(val start: Int, val end: Int, val color: Int)

    /** 普通命中：半透明天蓝，与标注的琥珀/绿/粉区分开。 */
    const val FIND_HIT_COLOR = 0x664FC3F7

    /** 当前命中：不透明珊瑚，一眼能从其余命中里认出来。 */
    val FIND_ACTIVE_COLOR = 0xA6FF8A65.toInt()

    fun blockText(runs: List<InlineRun>): String = runs.joinToString("") { it.text }

    fun overlays(blockText: String, annotations: List<ArticleAnnotationEntity>): List<Overlay> {
        if (annotations.isEmpty() || blockText.isEmpty()) return emptyList()
        return annotations.mapNotNull { annotation ->
            locate(blockText, annotation, hintStart = 0)?.let { range ->
                Overlay(range.first, range.last + 1, highlightColor(annotation))
            }
        }
    }

    /**
     * 查找命中区间。同一块内出现多次**全部**标出——用户按下查找时想看的是"这个词
     * 在本文哪儿出现过"，只标第一处会让人以为其余没有命中。
     */
    fun findOverlays(blockText: String, highlight: FindHighlight): List<Overlay> {
        val query = highlight.query.trim()
        if (query.isEmpty() || blockText.isEmpty()) return emptyList()
        val overlays = mutableListOf<Overlay>()
        var from = 0
        while (from <= blockText.length - query.length) {
            val index = blockText.indexOf(query, from, ignoreCase = true)
            if (index < 0) break
            val isActive = highlight.activeBlockText == blockText && index == highlight.activeLocalStart
            overlays += Overlay(
                start = index,
                end = index + query.length,
                color = if (isActive) FIND_ACTIVE_COLOR else FIND_HIT_COLOR,
            )
            from = index + 1
        }
        return overlays
    }

    fun locate(blockText: String, annotation: ArticleAnnotationEntity, hintStart: Int): IntRange? {
        val location = AnnotationAnchor.locate(
            text = blockText,
            quote = annotation.quote,
            prefix = annotation.prefix,
            suffix = annotation.suffix,
            hintStart = hintStart,
        ) ?: return null
        return location.start until location.end
    }

    fun highlightColor(annotation: ArticleAnnotationEntity): Int =
        AnnotationPalette.colorAt(annotation.color)
}
