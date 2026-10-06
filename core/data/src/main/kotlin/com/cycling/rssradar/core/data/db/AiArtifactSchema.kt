package com.cycling.rssradar.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cycling.rssradar.core.model.AiFeature
import com.cycling.rssradar.core.model.AiScope

/**
 * AI 产物（AI 智能功能模块，35 项）。
 *
 * **为什么所有功能共用一张表、且用 (subjectKind, subjectId, kind) 三元组作主键**：
 * 35 项功能会持续增减。若每项往 `articles` 加一列，每次加功能都要
 * 升 schema 版本 + 写迁移 + 同步 `ARTICLE_LIST_COLUMNS`（漏一处就是列表页静默拿到默认值）；
 * 若每项建一张表，app 会背上几十张表。共用一张表后，**加功能零迁移**——
 * 新功能只是新的 kind 值，产物是 payload 里的 JSON，由 [AiParsers] 解释。
 *
 * **为什么不加外键**：subjectKind 有文章 / 订阅源 / 全局三种，全局产物（每日简报、
 * 阅读报告）没有对应的父行，加 FK 会直接插不进去。改为接受孤儿，
 * 由每日批处理末段的 `deleteOrphanArtifacts()` 清理——文章被归档删除、订阅源被删除后，
 * 残留产物最多活到下一次每日任务。
 *
 * payload 是模型原始输出的**结构化 JSON**（不存渲染后的文本），理由与 ADR-0005 一致：
 * 展示形态可以改，产物只有一次，重跑要花钱。
 */
@Entity(
    tableName = "ai_artifacts",
    primaryKeys = ["subjectKind", "subjectId", "kind"],
    indices = [
        // 按功能统计用量 / 按功能批量清除（用户在设置页关掉某项后清产物）。
        Index(value = ["kind"]),
        // 全局产物的时间滚动清理，以及「最近生成」排序。
        Index(value = ["createdAt"]),
    ],
)

data class AiArtifactEntity(
    /** [AiScope.dbValue]：0=文章 1=订阅源 2=全局。 */
    val subjectKind: Int,
    /** 文章 id / 订阅源 id / 全局产物为 0（用 createdAt 区分不同天的简报）。 */
    val subjectId: Long,
    /** [AiFeature.dbValue]。 */
    val kind: Int,
    /** 结构化产物的 JSON 字符串，由 [AiParsers] 按 kind 解释。 */
    val payload: String,
    /** 生成时使用的模型标识，便于日后判断产物是否过期。 */
    val model: String,
    /** 送入模型的字符数（截断后），用量统计与成本归因用。 */
    @ColumnInfo(defaultValue = "0") val inputChars: Int = 0,
    /** 模型返回的字符数。 */
    @ColumnInfo(defaultValue = "0") val outputChars: Int = 0,
    val createdAt: Long,
) {
    companion object {
        /** 全局产物的 subjectId 固定为 0；同一天同功能只有一行（覆盖式写入）。 */
        const val GLOBAL_SUBJECT_ID = 0L
    }
}

@Dao

interface AiArtifactDao {
    @Query("SELECT * FROM ai_artifacts WHERE subjectKind = 2 ORDER BY createdAt ASC")
    suspend fun globalArtifacts(): List<AiArtifactEntity>

    @Query("SELECT * FROM ai_artifacts WHERE subjectKind = :subjectKind AND subjectId = :subjectId")
    suspend fun ofSubject(subjectKind: Int, subjectId: Long): List<AiArtifactEntity>

    @Query(
        """
        SELECT * FROM ai_artifacts
        WHERE subjectKind = :subjectKind AND subjectId = :subjectId AND kind = :kind
        LIMIT 1
        """,
    )
    suspend fun of(subjectKind: Int, subjectId: Long, kind: Int): AiArtifactEntity?

    /** 列表页批量取产物：一次查回 N 篇文章的全部产物，避免逐篇查库。 */
    @Query(
        """
        SELECT * FROM ai_artifacts
        WHERE subjectKind = 0 AND subjectId IN (:articleIds)
        """,
    )
    suspend fun ofArticles(articleIds: List<Long>): List<AiArtifactEntity>

    /** 某项功能在一段时间内的产物（全局类产物按时间滚动查询，如历史简报）。 */
    @Query(
        """
        SELECT * FROM ai_artifacts
        WHERE subjectKind = :subjectKind AND kind = :kind
        ORDER BY createdAt DESC LIMIT :limit
        """,
    )
    suspend fun recentOfKind(subjectKind: Int, kind: Int, limit: Int): List<AiArtifactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiArtifactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AiArtifactEntity>)

    @Query(
        """
        DELETE FROM ai_artifacts
        WHERE subjectKind = :subjectKind AND subjectId = :subjectId AND kind = :kind
        """,
    )
    suspend fun delete(subjectKind: Int, subjectId: Long, kind: Int)

    /** 用户在设置页关掉某项功能后清掉它的全部产物（「不留残留」是开关的应有语义）。 */
    @Query("DELETE FROM ai_artifacts WHERE kind = :kind")
    suspend fun deleteKind(kind: Int)

    /** 单篇文章的全部产物（重新生成前清场，或文章被删时）。 */
    @Query("DELETE FROM ai_artifacts WHERE subjectKind = :subjectKind AND subjectId = :subjectId")
    suspend fun deleteSubject(subjectKind: Int, subjectId: Long)

    @Query("DELETE FROM ai_artifacts WHERE subjectKind = :subjectKind AND createdAt < :before")
    suspend fun deleteBefore(subjectKind: Int, before: Long)

    @Query("SELECT COUNT(*) FROM ai_artifacts")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM ai_artifacts WHERE kind = :kind")
    suspend fun countOfKind(kind: Int): Int

    /**
     * 清孤儿：文章已被归档删除 / 订阅源已被删除后残留的产物。
     * 不加外键的代价，由每日任务末段偿还。
     */
    @Query(
        """
        DELETE FROM ai_artifacts
        WHERE (subjectKind = 0 AND subjectId NOT IN (SELECT id FROM articles))
           OR (subjectKind = 1 AND subjectId NOT IN (SELECT id FROM feeds))
        """,
    )
    suspend fun deleteOrphans()

    // ── 产物中心 ─────────────────────────────────────────────────────────
    // 三条查询都是为了同一个页面：让 35 项功能的产物**第一次可见**。
    // 在此之前，只有那些有专属 UI 的功能看得到结果，其余功能跑完就石沉大海，
    // 用户无从判断"到底跑了没跑"。

    /**
     * 按功能聚合的概览：每个已产出过的功能一行（数量 / 最近生成 / 累计输出字数）。
     *
     * 走 `kind` 索引做分组，比逐项 COUNT 少几十次查询。
     * `CAST` 是必需的：SQLite 的 SUM 在混合类型下可能返回 REAL，
     * Room 校验返回类型时对不上就会在编译期报类型不符。
     */
    @Query(
        """
        SELECT kind,
               COUNT(*) AS total,
               MAX(createdAt) AS latestAt,
               CAST(COALESCE(SUM(outputChars), 0) AS INTEGER) AS outputChars
        FROM ai_artifacts
        GROUP BY kind
        ORDER BY latestAt DESC
        """,
    )
    suspend fun kindOverview(): List<AiArtifactKindOverview>

    /**
     * 全部功能的最近产物。
     *
     * `LIMIT` 不是可选优化：规模上千订阅源时这张表能到数万行，
     * 一次全读进内存再去 UI 侧截断，OOM 风险落在这个本来只是"看看结果"的页面上。
     */
    @Query("SELECT * FROM ai_artifacts ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentAll(limit: Int): List<AiArtifactEntity>

    /** 单个功能的最近产物（跨主体）。 */
    @Query("SELECT * FROM ai_artifacts WHERE kind = :kind ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentOfKindAll(kind: Int, limit: Int): List<AiArtifactEntity>
}

/** 产物中心按功能聚合的一行。Room 要求构造函数参数名与列名一致。 */
data class AiArtifactKindOverview(
    val kind: Int,
    val total: Int,
    val latestAt: Long,
    val outputChars: Long,
)
