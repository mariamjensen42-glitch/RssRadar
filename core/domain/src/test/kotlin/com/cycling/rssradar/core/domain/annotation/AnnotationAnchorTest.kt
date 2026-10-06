package com.cycling.rssradar.core.domain.annotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnotationAnchorTest {

    @Test
    fun `唯一文本直接命中`() {
        val text = "今天发布了一款新手机，售价三千元。"
        val location = AnnotationAnchor.locate(text, quote = "新手机")
        assertEquals(text.indexOf("新手机"), location?.start)
        assertEquals(text.indexOf("新手机") + 3, location?.end)
    }

    @Test
    fun `前后文用于消歧重复片段`() {
        val text = "苹果很好吃。苹果很贵。"
        val location = AnnotationAnchor.locate(
            text = text,
            quote = "苹果",
            prefix = "。",
            suffix = "很贵",
        )
        assertEquals(text.lastIndexOf("苹果"), location?.start)
    }

    @Test
    fun `没有前后文时取离提示位置最近的一处`() {
        val text = "苹果很好吃。苹果很贵。"
        val location = AnnotationAnchor.locate(text, quote = "苹果", hintStart = text.lastIndexOf("苹果"))
        assertEquals(text.lastIndexOf("苹果"), location?.start)
    }

    @Test
    fun `正文变化后仍能按引文找回`() {
        val original = "开头。目标句子在这里。结尾。"
        val edited = "新增的一段。开头。目标句子在这里。结尾。"
        val location = AnnotationAnchor.locate(edited, quote = "目标句子", hintStart = 3)
        assertEquals(edited.indexOf("目标句子"), location?.start)
    }

    @Test
    fun `引文不存在时返回 null`() {
        assertNull(AnnotationAnchor.locate("完全无关的正文", quote = "目标句子"))
    }

    @Test
    fun `空引文返回 null`() {
        assertNull(AnnotationAnchor.locate("正文", quote = ""))
    }

    @Test
    fun `空正文返回 null`() {
        assertNull(AnnotationAnchor.locate("", quote = "目标"))
    }

    @Test
    fun `前后文不匹配时退化为仅引文定位`() {
        val text = "内容里有目标句子，但前后文已经变了。"
        val location = AnnotationAnchor.locate(
            text = text,
            quote = "目标句子",
            prefix = "不存在的开头",
            suffix = "不存在的结尾",
        )
        assertEquals(text.indexOf("目标句子"), location?.start)
    }

    @Test
    fun `返回区间长度等于引文长度`() {
        val text = "abcdefg"
        val location = AnnotationAnchor.locate(text, quote = "cde")
        assertEquals(3, location?.length)
    }

    @Test
    fun `色板索引越界时回落首个颜色`() {
        assertEquals(AnnotationPalette.swatches.first(), AnnotationPalette.colorAt(-1))
        assertEquals(AnnotationPalette.swatches.last(), AnnotationPalette.colorAt(99))
    }

    @Test
    fun `色板反查索引可往返`() {
        val argb = AnnotationPalette.colorAt(2)
        assertEquals(2, AnnotationPalette.indexOfArgb(argb))
    }

    @Test
    fun `色板反查未知颜色时回落默认索引`() {
        assertEquals(AnnotationPalette.defaultIndex, AnnotationPalette.indexOfArgb(0x123456))
    }
}
