package com.cycling.rssradar.core.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


/**
 * 产物可读化的测试。
 *
 * 这些用例钉死的是产物中心赖以成立的两条保证：
 * 1. **不认识具体 payload 类型也能渲染**——所以新增功能零成本纳入，不需要补渲染器。
 * 2. **模型输出再脏也不崩**——带代码围栏、前后写废话、直接给纯文本，都得显示出东西。
 *
 * 第 2 条尤其关键：产物中心是"到底跑没跑"的自查入口，
 * 它自己要是会在脏数据上崩或显示一片空白，这个自查就失效了。
 */
class AiPayloadTextTest {

    @Test
    fun `键值渲染成标签与值`() {
        val lines = AiPayloadText.lines("""{"topic":"科技","confidence":0.9}""")
        assertEquals("话题", lines[0].label)
        assertEquals("科技", lines[0].value)
        assertEquals("置信度", lines[1].label)
        // 不做数值换算：语义无法确知，换算错了就是把一个数字变成另一个数字。
        assertEquals("0.9", lines[1].value)
    }

    @Test
    fun `标量数组并成一行而不是拆成十行`() {
        val lines = AiPayloadText.lines("""{"tags":["大模型","推理","成本"]}""")
        assertEquals(1, lines.size)
        assertEquals("标签", lines[0].label)
        assertEquals("大模型 / 推理 / 成本", lines[0].value)
    }

    @Test
    fun `对象数组逐项渲染并带序号`() {
        val lines = AiPayloadText.lines(
            """{"headline":"今天三条","items":[{"title":"A","why":"与你相关"},{"title":"B","why":"新进展"}]}""",
        )
        val values = lines.map { it.value }
        assertTrue(values.contains("今天三条"))
        assertTrue(values.contains("第 1 项"))
        assertTrue(values.contains("第 2 项"))
        assertTrue(values.contains("A"))
        assertTrue(values.contains("B"))
        // 数组元素内的字段要缩进一级，否则"第 1 项"和它的字段看起来是平级的。
        val itemFields = lines.filter { it.value == "A" || it.value == "与你相关" }
        assertTrue(itemFields.all { it.depth >= 1 })
    }

    @Test
    fun `嵌套对象保持层级`() {
        val lines = AiPayloadText.lines("""{"quality":{"overall":82,"note":"证据充分"}}""")
        val note = lines.first { it.value == "证据充分" }
        assertTrue(note.depth >= 1)
    }

    @Test
    fun `枚举取值直译为中文`() {
        val lines = AiPayloadText.lines("""{"polarity":"POSITIVE","level":"LOW"}""")
        assertEquals("偏正面", lines[0].value)
        assertEquals("低", lines[1].value)
    }

    @Test
    fun `布尔值直译为是否`() {
        val lines = AiPayloadText.lines("""{"notFound":false,"ok":true}""")
        assertEquals("否", lines[0].value)
        assertEquals("是", lines[1].value)
    }

    @Test
    fun `带代码围栏也能渲染`() {
        // 执行器对不需要收口 id 的功能**直接存模型原文**，围栏与客套话是常态。
        val raw = "好的，这是结果：\n```json\n{\"tags\":[\"大模型\"]}\n```\n希望有帮助"
        val lines = AiPayloadText.lines(raw)
        assertEquals(1, lines.size)
        assertEquals("大模型", lines[0].value)
    }

    @Test
    fun `纯文本产物原样显示而不是空白`() {
        val lines = AiPayloadText.lines("这是一段摘要，不是 JSON")
        assertEquals(1, lines.size)
        assertEquals("这是一段摘要，不是 JSON", lines[0].value)
    }

    @Test
    fun `空产物不产生任何行`() {
        assertTrue(AiPayloadText.lines("").isEmpty())
        assertTrue(AiPayloadText.lines("   ").isEmpty())
    }

    @Test
    fun `空对象与空数组不产生行`() {
        assertTrue(AiPayloadText.lines("""{"items":[]}""").isEmpty())
        assertTrue(AiPayloadText.lines("{}").isEmpty())
    }

    @Test
    fun `原文格式化后带缩进`() {
        val pretty = AiPayloadText.prettyRaw("""{"topic":"科技"}""")
        assertTrue(pretty.contains("\n"))
        assertTrue(pretty.contains("科技"))
    }

    @Test
    fun `原文无法解析时原样返回`() {
        assertEquals("不是 JSON", AiPayloadText.prettyRaw("不是 JSON"))
    }
}
