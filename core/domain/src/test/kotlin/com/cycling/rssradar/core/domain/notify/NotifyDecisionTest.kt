package com.cycling.rssradar.core.domain.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyDecisionTest {

    private val base = NotifyPrefs(enabled = true)

    @Test
    fun `全局开关关闭时不通知`() {
        assertFalse(
            NotifyDecision.shouldNotify(
                prefs = NotifyPrefs(enabled = false),
                feedEnabled = true,
                title = "标题",
                summary = "",
                nowMinute = 600,
            ),
        )
    }

    @Test
    fun `订阅源级关闭时不通知`() {
        assertFalse(
            NotifyDecision.shouldNotify(base, feedEnabled = false, title = "标题", summary = "", nowMinute = 600),
        )
    }

    @Test
    fun `全部条件满足时通知`() {
        assertTrue(
            NotifyDecision.shouldNotify(base, feedEnabled = true, title = "标题", summary = "", nowMinute = 600),
        )
    }

    @Test
    fun `勿扰时段内不通知`() {
        val prefs = NotifyPrefs(enabled = true, dndStartMinute = 22 * 60, dndEndMinute = 7 * 60)
        assertFalse(
            NotifyDecision.shouldNotify(prefs, feedEnabled = true, title = "标题", summary = "", nowMinute = 23 * 60),
        )
    }

    @Test
    fun `勿扰时段外正常通知`() {
        val prefs = NotifyPrefs(enabled = true, dndStartMinute = 22 * 60, dndEndMinute = 7 * 60)
        assertTrue(
            NotifyDecision.shouldNotify(prefs, feedEnabled = true, title = "标题", summary = "", nowMinute = 12 * 60),
        )
    }

    @Test
    fun `只设了单边时间不视为勿扰`() {
        val prefs = NotifyPrefs(enabled = true, dndStartMinute = 22 * 60, dndEndMinute = null)
        assertTrue(
            NotifyDecision.shouldNotify(prefs, feedEnabled = true, title = "标题", summary = "", nowMinute = 23 * 60),
        )
    }

    @Test
    fun `包含关键词未命中时不通知`() {
        val prefs = NotifyPrefs(enabled = true, includeKeywords = listOf("发布", "上线"))
        assertFalse(
            NotifyDecision.shouldNotify(prefs, feedEnabled = true, title = "普通更新", summary = "", nowMinute = 600),
        )
    }

    @Test
    fun `包含关键词命中时通知`() {
        val prefs = NotifyPrefs(enabled = true, includeKeywords = listOf("发布", "上线"))
        assertTrue(
            NotifyDecision.shouldNotify(prefs, feedEnabled = true, title = "v2 正式上线", summary = "", nowMinute = 600),
        )
    }

    @Test
    fun `排除关键词命中时不通知`() {
        val prefs = NotifyPrefs(enabled = true, excludeKeywords = listOf("广告"))
        assertFalse(
            NotifyDecision.shouldNotify(prefs, feedEnabled = true, title = "这不是广告", summary = "", nowMinute = 600),
        )
    }

    @Test
    fun `摘要也参与关键词判定`() {
        val prefs = NotifyPrefs(enabled = true, excludeKeywords = listOf("抽奖"))
        assertFalse(
            NotifyDecision.shouldNotify(
                prefs,
                feedEnabled = true,
                title = "活动通知",
                summary = "参与抽奖赢好礼",
                nowMinute = 600,
            ),
        )
    }

    @Test
    fun `被规则抑制时不通知`() {
        assertFalse(
            NotifyDecision.shouldNotify(
                base,
                feedEnabled = true,
                title = "标题",
                summary = "",
                nowMinute = 600,
                suppressedByRule = true,
            ),
        )
    }

    @Test
    fun `包含与排除同时命中时排除优先`() {
        val prefs = NotifyPrefs(
            enabled = true,
            includeKeywords = listOf("发布"),
            excludeKeywords = listOf("广告"),
        )
        assertFalse(
            NotifyDecision.shouldNotify(
                prefs,
                feedEnabled = true,
                title = "广告发布",
                summary = "",
                nowMinute = 600,
            ),
        )
    }
}
