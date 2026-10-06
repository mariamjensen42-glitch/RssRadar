package com.cycling.rssradar.core.data.repository

import com.cycling.rssradar.core.data.db.FeedAiProfileDao
import com.cycling.rssradar.core.data.db.dao.FeedDao
import com.cycling.rssradar.core.data.db.FeedAiProfileEntity

/**
 * 订阅源级提示词覆盖的读写门面。
 *
 * 空白模板一律当「清除覆盖」——存一个只有空格的模板等于让模型收到空 system。
 * 新建行用 upsert 兜底：该源没有 profile 行时 updateSummaryPrompt 更新 0 行等于白存。
 *
 * 刻意**不清除**已生成的摘要：旧摘要忠实于原文，用户点「重新生成」即可套用新提示词。
 */
class FeedPromptOverrideRepository(
    private val profileDao: FeedAiProfileDao,
    private val feedDao: FeedDao,
) {

    /** 有非空提示词的源 id 集合。 */
    suspend fun overriddenFeedIds(): List<Long> =
        profileDao.getAll().filter { !it.summaryPrompt.isNullOrBlank() }.map { it.feedId }

    suspend fun summaryPromptOf(feedId: Long): String? = profileDao.get(feedId)?.summaryPrompt

    /** 写入覆盖；[prompt] 传空白即清除。返回是否真的落了库。 */
    suspend fun saveSummaryPrompt(feedId: Long, prompt: String?): Boolean {
        val normalized = prompt?.trim()?.takeIf { it.isNotBlank() }
        val now = System.currentTimeMillis()
        val current = profileDao.get(feedId)
        if (current == null && normalized == null) return false
        if (current == null) {
            profileDao.upsert(FeedAiProfileEntity(feedId = feedId, summaryPrompt = normalized, updatedAt = now))
        } else {
            profileDao.updateSummaryPrompt(feedId, normalized, now)
        }
        return true
    }

    suspend fun allFeedTitles(): List<Pair<Long, String>> = feedDao.getAll().map { it.id to it.title }
}
