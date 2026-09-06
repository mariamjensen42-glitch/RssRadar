package com.cycling.rssradar.core.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AiFeatureSpecs 注册表的**全覆盖守门**。
 *
 * 注册表从 map 登记后失去了旧 when 的编译期穷尽性：漏登记一项，运行期只会
 * 静默 Skipped/放行——这条测试把「漏登记」重新变红。每加一项枚举，这里先红，
 * 补登记后转绿；prompt 侧则顺带验证 LLM 功能都能从同一份上下文构建出 prompt。
 */
class AiFeatureSpecsTest {

    @Test
    fun `every feature is registered`() {
        val missing = AiFeature.entries.filter { it !in AiFeatureSpecs.all }
        assertTrue("漏登记: $missing", missing.isEmpty())
        assertEquals(AiFeature.entries.size, AiFeatureSpecs.all.size)
    }

    @Test
    fun `llm features build a prompt from a plain article context`() {
        val context = AiPromptContext(
            title = "标题",
            feedTitle = "来源",
            body = "正文内容",
        )
        val noPrompt = AiFeature.entries
            .filter { it.needsLlm }
            .filter { AiFeatureSpecs.buildPrompt(it, context) == null }
        assertTrue("LLM 功能却构建不出 prompt: $noPrompt", noPrompt.isEmpty())
    }

    @Test
    fun `non llm features are skipped at the prompt stage`() {
        val context = AiPromptContext(title = "t", feedTitle = "f", body = "b")
        val offenders = AiFeature.entries
            .filter { !it.needsLlm }
            .filter { AiFeatureSpecs.buildPrompt(it, context) != null }
        assertTrue("不调模型的功能却构建出了 prompt: $offenders", offenders.isEmpty())
    }

    @Test
    fun `raw text features reject blank results`() {
        // SUMMARY/TRANSLATE 不解析，产物即原文——空串必须是「没生成」而不是空壳入库
        assertFalse(AiFeatureSpecs.isMeaningful(AiFeature.SUMMARY, "   "))
        assertTrue(AiFeatureSpecs.isMeaningful(AiFeature.SUMMARY, "结论"))
        assertEquals("原文", AiFeatureSpecs.parse(AiFeature.SUMMARY, "原文"))
    }
}
