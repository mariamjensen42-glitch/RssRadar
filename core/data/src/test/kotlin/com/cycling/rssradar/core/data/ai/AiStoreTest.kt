package com.cycling.rssradar.core.data.ai

import com.cycling.rssradar.core.data.FakeSharedPreferences
import com.cycling.rssradar.core.data.store.AiBudgetState
import com.cycling.rssradar.core.data.store.AiBudgetStore
import com.cycling.rssradar.core.data.store.AiDayIndex
import com.cycling.rssradar.core.data.store.AiFeatureSettings
import com.cycling.rssradar.core.data.store.AiFeatureStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


/**
 * 35 项开关与预算的持久化测试。
 *
 * 最关键的一条是 `reset 后默认值等于出厂设置`：
 * 它钉死的是「逐项读 key、缺 key 回落 defaultEnabled」这个设计——
 * 若哪天改成存整份集合快照，新功能就再也推不到老用户身上，这条会先红。
 */
class AiStoreTest {

    private fun featureStore(): Pair<AiFeatureStore, FakeSharedPreferences> {
        val prefs = FakeSharedPreferences()
        return AiFeatureStore(prefs) to prefs
    }

    @Test
    fun `默认开启集合等于各项 defaultEnabled`() {
        val (store, _) = featureStore()
        assertEquals(AiFeature.DEFAULT_ENABLED, store.state.value.enabled)
        AiFeature.entries.forEach { feature ->
            assertEquals(
                "${feature.name} 默认开关应与 defaultEnabled 一致",
                feature.defaultEnabled,
                store.isEnabled(feature),
            )
        }
    }

    @Test
    fun `单项开关落到独立 key 且能读回`() {
        val (store, prefs) = featureStore()
        store.set(AiFeature.TAGS, true)
        assertTrue(store.isEnabled(AiFeature.TAGS))
        assertEquals(true, prefs.getBoolean(AiFeatureStore.keyFor(AiFeature.TAGS), false))
    }

    @Test
    fun `重启后开关保留`() {
        val (_, prefs) = featureStore()
        AiFeatureStore(prefs).set(AiFeature.QUALITY, true)
        assertTrue(AiFeatureStore(prefs).isEnabled(AiFeature.QUALITY))
    }

    @Test
    fun `分组一键开关只影响该组`() {
        val (store, _) = featureStore()
        store.setCategory(AiCategory.CONTENT, true)
        assertTrue(AiFeature.ofCategory(AiCategory.CONTENT).all { store.isEnabled(it) })
        // 辅助推送组的默认态不能被带偏
        assertEquals(
            AiFeature.ofCategory(AiCategory.ASSIST).filter { it.defaultEnabled }.toSet(),
            AiFeature.ofCategory(AiCategory.ASSIST).filter { store.isEnabled(it) }.toSet(),
        )
    }

    @Test
    fun `reset 恢复出厂默认`() {
        val (store, _) = featureStore()
        AiFeature.entries.forEach { store.set(it, !it.defaultEnabled) }
        store.reset()
        assertEquals(AiFeature.DEFAULT_ENABLED, store.state.value.enabled)
    }

    @Test
    fun `紧急刹车只关会调模型的功能`() {
        val (store, _) = featureStore()
        store.disableAllPaid()
        assertTrue(AiFeature.LLM_FEATURES.none { store.isEnabled(it) })
        // 本地功能（用量看板 / 任务队列 / 提示词管理）必须留在开启状态，否则用户没法恢复
        assertTrue(store.isEnabled(AiFeature.USAGE))
        assertTrue(store.isEnabled(AiFeature.TASK_QUEUE))
        assertTrue(store.isEnabled(AiFeature.PROMPT_TEMPLATE))
    }

    @Test
    fun `设置项计数与全开判定`() {
        val settings = AiFeatureSettings(setOf(AiFeature.TAGS))
        assertEquals(1, settings.countIn(AiCategory.CONTENT))
        assertFalse(settings.allIn(AiCategory.CONTENT))
        assertEquals(0, settings.countIn(AiCategory.ASSIST))
        assertTrue(AiFeatureSettings(AiFeature.entries.toSet()).allIn(AiCategory.CONTENT))
    }

    // ── 预算 ────────────────────────────────────────────────────────────────

    private fun budgetStore(now: Long = 1_700_000_000_000L): Pair<AiBudgetStore, FakeSharedPreferences> {
        val prefs = FakeSharedPreferences()
        return AiBudgetStore(prefs, clock = { now }, zoneOffsetAt = { 0 }) to prefs
    }

    @Test
    fun `自然日编号按时区偏移换算`() {
        val day = 86_400_000L
        assertEquals(1L, AiDayIndex.indexOf(day + 1, 0))
        // 同一个时刻：UTC 下是第 1 天的 20 点，东八区下已经跨到第 2 天的 4 点。
        // 这条断言钉死的是"本地日"而非"UTC 日"——按 UTC 算的话，国内用户每天 8 点前
        // 的调用会被记到前一天，日上限与"今日用量"就会对不上账。
        assertEquals(1L, AiDayIndex.indexOf(day + 20 * 3_600_000L, 0))
        assertEquals(2L, AiDayIndex.indexOf(day + 20 * 3_600_000L, 8 * 3_600_000))
    }

    @Test
    fun `跨天后今日计数归零但累计保留`() {
        val day = 86_400_000L
        var now = 10 * day
        val prefs = FakeSharedPreferences()
        val store = AiBudgetStore(prefs, clock = { now }, zoneOffsetAt = { 0 })
        store.record(100, 200, success = true)
        assertEquals(1, store.current().usedToday)
        assertEquals(1L, store.current().totalCalls)

        now = 11 * day
        assertEquals(0, store.current().usedToday)
        assertEquals(1L, store.current().totalCalls)
    }

    @Test
    fun `失败同样占用额度`() {
        val (store, _) = budgetStore()
        store.setDailyLimit(2)
        store.record(10, 10, success = false)
        store.record(10, 10, success = false)
        assertEquals(0, store.current().remainingToday)
        assertFalse(store.hasBudget())
        assertEquals(2, store.current().failedToday)
    }

    @Test
    fun `上限为零表示不限`() {
        val (store, _) = budgetStore()
        store.setDailyLimit(0)
        assertTrue(store.hasBudget())
        assertEquals(Int.MAX_VALUE, store.current().remainingToday)
    }

    @Test
    fun `并发与间隔被夹在合法区间`() {
        val (store, _) = budgetStore()
        store.setConcurrentLimit(99)
        assertEquals(AiBudgetState.MAX_CONCURRENT, store.current().concurrentLimit)
        store.setConcurrentLimit(0)
        assertEquals(AiBudgetState.MIN_CONCURRENT, store.current().concurrentLimit)
        store.setMinIntervalMs(999_999L)
        assertEquals(30_000L, store.current().minIntervalMs)
    }

    @Test
    fun `重启后今日计数不串天`() {
        val day = 86_400_000L
        val prefs = FakeSharedPreferences()
        AiBudgetStore(prefs, clock = { 10 * day }, zoneOffsetAt = { 0 }).record(1, 1, true)
        val reloaded = AiBudgetStore(prefs, clock = { 12 * day }, zoneOffsetAt = { 0 })
        assertEquals(0, reloaded.current().usedToday)
        assertEquals(1L, reloaded.current().totalCalls)
    }

    @Test
    fun `重置今日不清除累计`() {
        val (store, _) = budgetStore()
        store.record(5, 5, true)
        store.resetToday()
        assertEquals(0, store.current().usedToday)
        assertEquals(1L, store.current().totalCalls)
    }
}
