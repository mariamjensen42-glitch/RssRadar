package com.cycling.rssradar.core.domain.reading

/**
 * 把「按段落的正文」摊平成一条带坐标的文本，供高亮定位与页内查找共用。
 *
 * 之所以要这一层：渲染树是一段段独立文本，而标注与查找的坐标是**全文偏移**。
 * 没有映射就只能按段落单独找，跨段命中和"第 3 处命中"这类语义都无从表达。
 *
 * 块之间插入 `\n` —— 保证相邻块的文本不会粘成一个词（否则跨块边界会凭空出现命中）。
 */
object ReadingTextMap {

    const val SEPARATOR = '\n'

    data class Span(val blockIndex: Int, val start: Int, val end: Int) {
        fun contains(offset: Int): Boolean = offset in start until end
    }

    data class FlatText(
        val text: String,
        val spans: List<Span>,
    ) {
        fun spanOf(offset: Int): Span? = spans.firstOrNull { it.contains(offset) }
    }

    fun flatten(blocks: List<String>): FlatText {
        val builder = StringBuilder()
        val spans = ArrayList<Span>(blocks.size)
        blocks.forEachIndexed { index, block ->
            if (index > 0) builder.append(SEPARATOR)
            val start = builder.length
            builder.append(block)
            spans += Span(index, start, builder.length)
        }
        return FlatText(builder.toString(), spans)
    }

    /** 全局区间 → 各块内的局部区间；跨越块边界的部分会被切开，落在分隔符上的部分丢弃。 */
    fun blockRangesFor(flat: FlatText, start: Int, end: Int): List<Pair<Int, IntRange>> {
        if (end <= start) return emptyList()
        val result = mutableListOf<Pair<Int, IntRange>>()
        flat.spans.forEach { span ->
            val from = maxOf(start, span.start)
            val to = minOf(end, span.end)
            if (from < to) {
                result += span.blockIndex to (from - span.start until to - span.start)
            }
        }
        return result
    }
}
