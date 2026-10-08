package com.cycling.rssradar.core.data.ai

import com.cycling.rssradar.core.model.AiFeature
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
    fun `关键词去重去空并限长`() {
        val raw = """{"keywords":["大模型","大模型","  ","推理成本","算力","A","B","C","D","E"]}"""
        val parsed = AiParsers.keywords(raw)
        assertEquals(listOf("大模型", "推理成本", "算力", "A", "B", "C", "D", "E"), parsed.keywords)
        assertTrue(parsed.keywords.size <= AiParsers.MAX_KEYWORDS)
    }

    @Test
    fun `垃圾输入返回空载荷而不是抛异常`() {
        assertTrue(AiParsers.keywords("这不是 JSON").keywords.isEmpty())
    }

    @Test
    fun `中文健康档位兜底为规范值`() {
        assertEquals("BROKEN", AiParsers.feedHealth("""{"status":"失效"}""").status)
        assertEquals("UNKNOWN", AiParsers.feedHealth("""{"status":"瞎写的值"}""").status)
        assertEquals("DEGRADED", AiParsers.feedHealth("""{"status":"degraded"}""").status)
    }

    @Test
    fun `信息价值分超出 0 到 100 时夹取`() {
        assertEquals(100, AiParsers.noise("""{"value":150}""").value)
        assertEquals(0, AiParsers.noise("""{"value":-20}""").value)
    }

    @Test
    fun `空壳产物被判为无意义`() {
        assertFalse(AiFeatureSpecs.isMeaningful(AiFeature.KEYWORDS, AiParsers.keywords("""{"keywords":[]}""")))
        assertFalse(AiFeatureSpecs.isMeaningful(AiFeature.FILTER_RULE, AiParsers.filterRule("""{"rules":[]}""")))
        assertFalse(AiFeatureSpecs.isMeaningful(AiFeature.SUMMARY, "   "))
        assertTrue(AiFeatureSpecs.isMeaningful(AiFeature.KEYWORDS, AiParsers.keywords("""{"keywords":["a"]}""")))
    }

    @Test
    fun `问答不按 JSON 输出时整段当答案`() {
        val parsed = AiParsers.qa("文中提到发布时间是 3 月 4 日。")
        assertEquals("文中提到发布时间是 3 月 4 日。", parsed.answer)
        assertFalse(parsed.notFound)
    }

    @Test
    fun `未知字段不导致解析失败`() {
        val raw = """{"keywords":["a"],"未来新增的字段":{"嵌套":1}}"""
        assertEquals(listOf("a"), AiParsers.keywords(raw).keywords)
    }

    @Test
    fun `解析出的载荷按功能类型正确分发`() {
        val parsed = AiFeatureSpecs.parse(AiFeature.KEYWORDS, """{"keywords":["甲","乙"]}""")
        assertTrue(parsed is AiKeywordsPayload)
        assertEquals(listOf("甲", "乙"), (parsed as AiKeywordsPayload).keywords)
    }
}
