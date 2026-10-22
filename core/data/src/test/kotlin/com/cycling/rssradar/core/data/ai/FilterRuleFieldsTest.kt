package com.cycling.rssradar.core.data.ai

import com.cycling.rssradar.core.domain.filter.RuleField
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 模型字段标签 → 规则字段的映射测试。
 *
 * 这条映射错了不会报错：它只会让一条规则悄悄多看或少看一个字段，
 * 而表现是"这条规则有时候管用有时候不管用"，极难回溯。
 */
class FilterRuleFieldsTest {

    @Test
    fun `标题与摘要各归各的`() {
        assertEquals(setOf(RuleField.TITLE), filterRuleFields("TITLE"))
        assertEquals(setOf(RuleField.SUMMARY), filterRuleFields("SUMMARY"))
    }

    @Test
    fun `二者兼指展开成标题加摘要而不是全部字段`() {
        // 展开成"全部字段"会让规则顺带扫正文，命中面比用户看到的大得多
        assertEquals(setOf(RuleField.TITLE, RuleField.SUMMARY), filterRuleFields("BOTH"))
    }

    @Test
    fun `大小写与空白不影响判定`() {
        assertEquals(setOf(RuleField.TITLE), filterRuleFields(" title "))
    }

    @Test
    fun `未知标签退到二者兼指的安全侧`() {
        assertEquals(setOf(RuleField.TITLE, RuleField.SUMMARY), filterRuleFields("AUTHOR"))
    }
}
