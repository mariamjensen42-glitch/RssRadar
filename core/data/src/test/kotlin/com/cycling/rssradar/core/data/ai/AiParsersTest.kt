package com.cycling.rssradar.core.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


/**
 * 解析层的容错测试。
 *
 * 这些用例全部来自模型**真实会犯的错**：加代码围栏、前后写废话、字段类型写错、
 * 回空壳 JSON、编造列表里没有的 id。解析层存在的意义就是把这些全兜住，
 * 所以每一条断言都是在钉死一次真实翻车。
 */
class AiParsersTest {

    @Test
    fun `剥掉 json 代码围栏`() {
        val raw = "好的，这是结果：\n```json\n{\"tags\":[\"大模型\"]}\n```\n希望有帮助"
        assertEquals("""{"tags":["大模型"]}""", AiParsers.extractJson(raw))
    }

    @Test
    fun `没有围栏时截取首尾花括号`() {
        val raw = "我觉得应该是 {\"topic\":\"科技\",\"confidence\":0.9} 就这样"
        assertEquals("""{"topic":"科技","confidence":0.9}""", AiParsers.extractJson(raw))
    }

    @Test
    fun `纯文本没有 json 时返回 null`() {
        assertEquals(null, AiParsers.extractJson("这篇文章讲了三件事"))
        assertEquals(null, AiParsers.extractJson(""))
    }

    @Test
    fun `标签去重去空并限长`() {
        val raw = """{"tags":["大模型","大模型","  ","推理成本","算力","A","B","C","D","E"]}"""
        val parsed = AiParsers.tags(raw)
        assertEquals(listOf("大模型", "推理成本", "算力", "A", "B", "C"), parsed.tags)
        assertTrue(parsed.tags.size <= AiParsers.MAX_TAGS)
    }

    @Test
    fun `垃圾输入返回空载荷而不是抛异常`() {
        val parsed = AiParsers.tags("这不是 JSON")
        assertTrue(parsed.tags.isEmpty())
    }

    @Test
    fun `中文情绪枚举兜底为规范值`() {
        assertEquals("POSITIVE", AiParsers.sentiment("""{"polarity":"偏正面","score":0.6}""").polarity)
        assertEquals("NEUTRAL", AiParsers.sentiment("""{"polarity":"瞎写的值"}""").polarity)
        assertEquals("NEGATIVE", AiParsers.sentiment("""{"polarity":"negative","score":0.9}""").polarity)
    }

    @Test
    fun `强度超出 0 到 1 时夹取`() {
        assertEquals(1.0, AiParsers.sentiment("""{"polarity":"POSITIVE","score":5}""").score, 0.001)
        assertEquals(0.0, AiParsers.sentiment("""{"polarity":"POSITIVE","score":-3}""").score, 0.001)
    }

    @Test
    fun `质量分超出 0 到 100 时夹取`() {
        val parsed = AiParsers.quality("""{"overall":150,"density":-20,"clickbait":999}""")
        assertEquals(100, parsed.overall)
        assertEquals(0, parsed.density)
        assertEquals(100, parsed.clickbait)
    }

    @Test
    fun `全文提取声称成功却没有内容按失败处理`() {
        val parsed = AiParsers.fulltext("""{"ok":true,"html":"  "}""")
        assertFalse(parsed.ok)
        assertTrue(parsed.html.isBlank())
    }

    @Test
    fun `模型返回列表外的 id 被过滤掉`() {
        val allowed = setOf(1L, 2L, 3L)
        assertEquals(listOf(1L, 3L), AiParsers.restrictIds(listOf(1L, 99L, 3L), allowed))
        assertTrue(AiParsers.restrictIds(listOf(7L), allowed).isEmpty())
    }

    @Test
    fun `空壳产物被判为无意义`() {
        assertFalse(AiParsers.isMeaningful(AiFeature.TAGS, AiParsers.tags("""{"tags":[]}""")))
        assertFalse(AiParsers.isMeaningful(AiFeature.CLASSIFY, AiParsers.classify("""{"topic":""}""")))
        assertFalse(AiParsers.isMeaningful(AiFeature.SUMMARY, "   "))
        assertTrue(AiParsers.isMeaningful(AiFeature.TAGS, AiParsers.tags("""{"tags":["a"]}""")))
    }

    @Test
    fun `问答不按 JSON 输出时整段当答案`() {
        val parsed = AiParsers.qa("文中提到发布时间是 3 月 4 日。")
        assertEquals("文中提到发布时间是 3 月 4 日。", parsed.answer)
        assertFalse(parsed.notFound)
    }

    @Test
    fun `事件合并的时间线去掉非法 id`() {
        val raw = """{"event":"某发布会","timeline":[{"articleId":0,"headline":"无效"},{"articleId":5,"headline":"有效"}]}"""
        val parsed = AiParsers.event(raw)
        assertEquals(listOf(5L), parsed.timeline.map { it.articleId })
    }

    @Test
    fun `未知字段不导致解析失败`() {
        val raw = """{"tags":["a"],"未来新增的字段":{"嵌套":1}}"""
        assertEquals(listOf("a"), AiParsers.tags(raw).tags)
    }

    @Test
    fun `解析出的载荷按功能类型正确分发`() {
        val parsed = AiParsers.parse(AiFeature.KEYWORDS, """{"keywords":["甲","乙"]}""")
        assertTrue(parsed is AiKeywordsPayload)
        assertEquals(listOf("甲", "乙"), (parsed as AiKeywordsPayload).keywords)
    }
}
