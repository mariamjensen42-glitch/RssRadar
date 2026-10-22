package com.cycling.rssradar.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 订阅源级 AI 配置。
 *
 * 「为每个订阅源配置不同的摘要提示词」这条需求落在 `summaryPrompt`：
 * 为空 = 跟随全局模板，非空 = 覆盖。同理 `autoSummary / autoScore / watchHealth`
 * 是**三态**：列里存的是「是否覆盖全局」，真正的三态语义由仓库层合并得出
 * （见 [com.cycling.rssradar.core.data.ai.FeedAiProfile]）。
 *
 * `autoTags` / `autoClassify` 两列已无对应功能（「自动标签」「智能分类」都已删），
 * 但列必须保留以匹配表结构——Room 会校验实体与表的列集合，
 * 删字段就得为一个死列写一次重建表迁移。
 *
 * 只有配置过的订阅源才有行——没配过的源走全局默认值，不写空行，避免几千个订阅源
 * 撑出几千行无用配置。
 */
@Entity(tableName = "feed_ai_profiles")

data class FeedAiProfileEntity(
    @PrimaryKey val feedId: Long,
    /** 覆盖全局摘要提示词；null = 跟随全局。支持 {title} {feed} {content} 变量。 */
    val summaryPrompt: String? = null,
    /** 刷新后是否自动为该源的新文章生成摘要。null = 跟随全局。 */
    val autoSummary: Boolean? = null,
    /** 已无消费者（「自动标签」已删）；保留字段只为匹配表结构。 */
    val autoTags: Boolean? = null,
    /** 已无消费者（「智能分类」已删）；保留字段只为匹配表结构。 */
    val autoClassify: Boolean? = null,
    /** 是否自动跑质量与降噪评分。null = 跟随全局。 */
    val autoScore: Boolean? = null,
    /** 是否纳入订阅源健康监控。null = 跟随全局。 */
    val watchHealth: Boolean? = null,
    /** 批处理优先级 [0,100]，越大越先跑。高价值源调高可在日预算耗尽前抢到额度。 */
    @ColumnInfo(defaultValue = "0") val priority: Int = 0,
    val updatedAt: Long = 0,
)

@Dao

interface FeedAiProfileDao {
    @Query("SELECT * FROM feed_ai_profiles")
    suspend fun getAll(): List<FeedAiProfileEntity>

    @Query("SELECT * FROM feed_ai_profiles")
    fun observeAll(): Flow<List<FeedAiProfileEntity>>

    @Query("SELECT * FROM feed_ai_profiles WHERE feedId = :feedId LIMIT 1")
    suspend fun get(feedId: Long): FeedAiProfileEntity?

    @Query("SELECT * FROM feed_ai_profiles WHERE feedId = :feedId LIMIT 1")
    fun observe(feedId: Long): Flow<FeedAiProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: FeedAiProfileEntity)

    @Query("DELETE FROM feed_ai_profiles WHERE feedId = :feedId")
    suspend fun delete(feedId: Long)

    /** 只更新摘要提示词，避免整行覆盖把用户其他配置冲掉。 */
    @Query("UPDATE feed_ai_profiles SET summaryPrompt = :prompt, updatedAt = :now WHERE feedId = :feedId")
    suspend fun updateSummaryPrompt(feedId: Long, prompt: String?, now: Long)
}
