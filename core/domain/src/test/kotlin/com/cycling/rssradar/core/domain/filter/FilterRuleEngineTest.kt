package com.cycling.rssradar.core.domain.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterRuleEngineTest {

    private fun rule(
        pattern: String = "广告",
        action: RuleAction = RuleAction.HIDE,
        fields: Set<RuleField> = RuleField.entries.toSet(),
        scopeType: RuleScopeType = RuleScopeType.GLOBAL,
        scopeId: String? = null,
        enabled: Boolean = true,
        matchType: RuleMatchType = RuleMatchType.KEYWORD,
        caseSensitive: Boolean = false,
        wholeWord: Boolean = false,
        priority: Int = 0,
    ) = FilterRule(
        name = "规则",
        enabled = enabled,
        priority = priority,
        matchType = matchType,
        fields = fields,
        pattern = pattern,
        caseSensitive = caseSensitive,
        wholeWord = wholeWord,
        scopeType = scopeType,
        scopeId = scopeId,
        action = action,
    )

    private fun target(
        title: String = "正常标题",
        summary: String = "",
        content: String = "",
        author: String = "",
        feedId: Long = 1L,
        groupName: String = "默认",
    ) = RuleTarget(
        feedId = feedId,
        groupName = groupName,
        title = title,
        summary = summary,
        content = content,
        author = author,
    )

    @Test
    fun `标题命中即产出隐藏`() {
        val engine = FilterRuleEngine(listOf(rule(pattern = "广告")))
        assertTrue(engine.evaluate(target(title = "这是一条广告")).hidden)
    }

    @Test
    fun `未命中时不产出任何动作`() {
        val engine = FilterRuleEngine(listOf(rule(pattern = "广告")))
        assertTrue(engine.evaluate(target(title = "正常内容")).isEmpty)
    }

    @Test
    fun `字段掩码限定只匹配标题时不看摘要`() {
        val engine = FilterRuleEngine(listOf(rule(pattern = "优惠", fields = setOf(RuleField.TITLE))))
        assertFalse(engine.evaluate(target(title = "标题", summary = "优惠")).hidden)
        assertTrue(engine.evaluate(target(title = "优惠来了")).hidden)
    }

    @Test
    fun `正文与作者字段可分别匹配`() {
        val contentRule = FilterRuleEngine(listOf(rule(pattern = "内幕", fields = setOf(RuleField.CONTENT))))
        assertTrue(contentRule.evaluate(target(content = "这里有内幕")).hidden)

        val authorRule = FilterRuleEngine(listOf(rule(pattern = "小编", fields = setOf(RuleField.AUTHOR))))
        assertTrue(authorRule.evaluate(target(author = "小编")).hidden)
    }

    @Test
    fun `作用域限定订阅源时其他源不受影响`() {
        val engine = FilterRuleEngine(
            listOf(rule(pattern = "广告", scopeType = RuleScopeType.FEED, scopeId = "7")),
        )
        assertTrue(engine.evaluate(target(title = "广告", feedId = 7L)).hidden)
        assertFalse(engine.evaluate(target(title = "广告", feedId = 8L)).hidden)
    }

    @Test
    fun `作用域限定分组时按分组名匹配`() {
        val engine = FilterRuleEngine(
            listOf(rule(pattern = "广告", scopeType = RuleScopeType.GROUP, scopeId = "技术")),
        )
        assertTrue(engine.evaluate(target(title = "广告", groupName = "技术")).hidden)
        assertFalse(engine.evaluate(target(title = "广告", groupName = "生活")).hidden)
    }

    @Test
    fun `正则匹配可用`() {
        val engine = FilterRuleEngine(
            listOf(rule(pattern = "第\\d+期", matchType = RuleMatchType.REGEX)),
        )
        assertTrue(engine.evaluate(target(title = "周刊第12期")).hidden)
        assertFalse(engine.evaluate(target(title = "周刊第十二期")).hidden)
    }

    @Test
    fun `非法正则被跳过而不是崩`() {
        val engine = FilterRuleEngine(
            listOf(rule(pattern = "([", matchType = RuleMatchType.REGEX)),
        )
        assertTrue(engine.evaluate(target(title = "任意内容")).isEmpty)
    }

    @Test
    fun `禁用的规则不参与`() {
        val engine = FilterRuleEngine(listOf(rule(pattern = "广告", enabled = false)))
        assertTrue(engine.evaluate(target(title = "广告")).isEmpty)
    }

    @Test
    fun `空模式串的规则被忽略`() {
        val engine = FilterRuleEngine(listOf(rule(pattern = "   ")))
        assertTrue(engine.evaluate(target(title = "广告")).isEmpty)
    }

    @Test
    fun `多条规则的动作合并`() {
        val engine = FilterRuleEngine(
            listOf(
                rule(pattern = "广告", action = RuleAction.HIDE),
                rule(pattern = "广告", action = RuleAction.STAR),
                rule(pattern = "广告", action = RuleAction.MARK_READ),
            ),
        )
        val outcome = engine.evaluate(target(title = "广告"))
        assertTrue(outcome.hidden)
        assertTrue(outcome.star)
        assertTrue(outcome.markRead)
        assertFalse(outcome.bookmark)
    }

    @Test
    fun `抑制通知动作可被单独查询`() {
        val engine = FilterRuleEngine(
            listOf(rule(pattern = "广告", action = RuleAction.SUPPRESS_NOTIFY)),
        )
        assertTrue(engine.suppressesNotify(target(title = "广告")))
        assertFalse(engine.suppressesNotify(target(title = "正常")))
    }

    @Test
    fun `字段掩码与集合互转一致`() {
        val fields = setOf(RuleField.TITLE, RuleField.CONTENT)
        assertEquals(fields, RuleField.fromMask(RuleField.toMask(fields)))
    }

    @Test
    fun `掩码为零视为全字段`() {
        assertEquals(RuleField.entries.toSet(), RuleField.fromMask(0))
    }

    @Test
    fun `整词开关透传到关键词匹配`() {
        val engine = FilterRuleEngine(listOf(rule(pattern = "cat", wholeWord = true)))
        assertFalse(engine.evaluate(target(title = "scatter")).hidden)
        assertTrue(engine.evaluate(target(title = "a cat here")).hidden)
    }
}
