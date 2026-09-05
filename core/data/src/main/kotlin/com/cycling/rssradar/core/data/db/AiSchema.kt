package com.cycling.rssradar.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cycling.rssradar.core.data.ai.AiFeature
import com.cycling.rssradar.core.data.ai.AiScope
import kotlinx.coroutines.flow.Flow


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


/**
 * 订阅源级 AI 配置。
 *
 * 「为每个订阅源配置不同的摘要提示词」这条需求落在 `summaryPrompt`：
 * 为空 = 跟随全局模板，非空 = 覆盖。同理 `autoSummary / autoTags / autoClassify / autoScore`
 * 是**三态**：列里存的是「是否覆盖全局」，真正的三态语义由仓库层合并得出
 * （见 [com.cycling.rssradar.core.data.ai.FeedAiProfile]）。
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
    /** 是否自动打标签。null = 跟随全局。 */
    val autoTags: Boolean? = null,
    /** 是否自动分类。null = 跟随全局。 */
    val autoClassify: Boolean? = null,
    /** 是否自动跑质量与降噪评分。null = 跟随全局。 */
    val autoScore: Boolean? = null,
    /** 是否纳入订阅源健康监控。null = 跟随全局。 */
    val watchHealth: Boolean? = null,
    /** 批处理优先级 [0,100]，越大越先跑。高价值源调高可在日预算耗尽前抢到额度。 */
    @ColumnInfo(defaultValue = "0") val priority: Int = 0,
    val updatedAt: Long = 0,
)


/**
 * AI 任务队列。
 *
 * 存在的理由只有一个：**AI 调用又慢又要钱，不能跟着刷新同步跑**。
 * 刷新完 200 篇文章就同步发 200 次请求，主线程会卡、额度会瞬间烧穿。
 * 所有 BATCH 触发的功能都先进这张表，由 `AiDailyWorker` 在后台
 * 按并发上限、最小间隔和日预算慢慢消化；失败按指数退避重试，超过上限转终态并留错误。
 *
 * 去重靠 `dedupeKey`（`kind:targetId:scope` 形态），入队用 REPLACE：
 * 重复入队等于「重新排队」而不是堆两条，避免同一篇文章被批处理反复挑中。
 */
@Entity(
    tableName = "ai_tasks",
    indices = [
        // 领取任务的查询：待执行 + 到点，按优先级与时间排序。
        Index(value = ["status", "runAfter"]),
        // 去重键：入队前查是否已存在。
        Index(value = ["dedupeKey"]),
    ],
)
data class AiTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [AiFeature.dbValue]。 */
    val kind: Int,
    /** 目标 id：文章 id / 订阅源 id / 全局任务为 0。 */
    val targetId: Long,
    /** 附加参数 JSON（如问答的问题、过滤规则的自然语言描述）。 */
    val payload: String = "",
    /** [STATUS_PENDING] / [STATUS_RUNNING] / [STATUS_DONE] / [STATUS_FAILED]。 */
    val status: Int = STATUS_PENDING,
    /** 已尝试次数，达到 MAX_ATTEMPTS 转终态。 */
    val attempts: Int = 0,
    /** 越大越先跑，取自订阅源优先级或功能默认优先级。 */
    @ColumnInfo(defaultValue = "0") val priority: Int = 0,
    val createdAt: Long,
    /** 限速用：早于这个时间戳不领取。重试时按退避往后推。 */
    val runAfter: Long,
    val updatedAt: Long,
    val lastError: String? = null,
    /** `kind:targetId`；同一目标同一功能只保留一条在队。 */
    val dedupeKey: String,
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_RUNNING = 1
        const val STATUS_DONE = 2
        const val STATUS_FAILED = 3

        /** 失败重试上限。取 3 而不是更大：AI 失败多为内容问题（太长/无正文），重试再多也一样。 */
        const val MAX_ATTEMPTS = 3

        /** 终态任务保留时长（7 天）——留着让用户在队列页看见失败原因，之后自动清理。 */
        const val FINISHED_RETENTION_MS = 7 * 24 * 60 * 60 * 1000L

        /** RUNNING 超过这个时长视为上次进程被杀，重置回 PENDING 重跑。 */
        const val STALE_RUNNING_MS = 30 * 60 * 1000L

        fun dedupeKey(kind: Int, targetId: Long): String = "$kind:$targetId"
    }
}


@Dao
interface AiArtifactDao {
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


@Dao
interface AiTaskDao {
    /** 入队。REPLACE 语义 = 同 dedupeKey 重新排队（重置为待执行），不会堆重复任务。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(task: AiTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueAll(tasks: List<AiTaskEntity>)

    /** 入队前查重：已有非终态任务就不再插一条。 */
    @Query("SELECT * FROM ai_tasks WHERE dedupeKey = :dedupeKey LIMIT 1")
    suspend fun findByDedupeKey(dedupeKey: String): AiTaskEntity?

    @Query("SELECT * FROM ai_tasks WHERE dedupeKey IN (:keys)")
    suspend fun findByDedupeKeys(keys: List<String>): List<AiTaskEntity>

    /**
     * 领取一批可执行任务：待执行且已到点，优先级高、入队早的先跑。
     * limit 由并发上限决定——一次领太多会在进程被杀时白烧一批。
     */
    @Query(
        """
        SELECT * FROM ai_tasks
        WHERE status = 0 AND runAfter <= :now
        ORDER BY priority DESC, runAfter ASC, id ASC
        LIMIT :limit
        """,
    )
    suspend fun claimable(now: Long, limit: Int): List<AiTaskEntity>

    @Query("UPDATE ai_tasks SET status = 1, attempts = :attempts, updatedAt = :now WHERE id = :id")
    suspend fun markRunning(id: Long, attempts: Int, now: Long)

    @Query(
        """
        UPDATE ai_tasks
        SET status = :status, attempts = :attempts, updatedAt = :now, lastError = :error
        WHERE id = :id
        """,
    )
    suspend fun finish(id: Long, status: Int, attempts: Int, now: Long, error: String?)

    /** 失败后按退避重新排队。 */
    @Query("UPDATE ai_tasks SET status = 0, runAfter = :runAfter, updatedAt = :now WHERE id = :id")
    suspend fun requeue(id: Long, runAfter: Long, now: Long)

    @Query("SELECT * FROM ai_tasks WHERE status = :status ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun ofStatus(status: Int, limit: Int): List<AiTaskEntity>

    @Query("SELECT COUNT(*) FROM ai_tasks WHERE status = :status")
    suspend fun countOfStatus(status: Int): Int

    @Query("SELECT * FROM ai_tasks ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<AiTaskEntity>

    /** 队列页用：按状态分组的计数，一次查四个值，避免四个 COUNT 查询。 */
    @Query(
        """
        SELECT status, COUNT(*) AS total FROM ai_tasks
        GROUP BY status
        """,
    )
    suspend fun statusCounts(): List<AiTaskStatusCount>

    /** 上次进程被杀留下的 RUNNING 任务，重置回待执行。 */
    @Query(
        """
        UPDATE ai_tasks SET status = 0, runAfter = :now, updatedAt = :now
        WHERE status = 1 AND updatedAt < :before
        """,
    )
    suspend fun resetStaleRunning(before: Long, now: Long)

    @Query("DELETE FROM ai_tasks WHERE status IN (2, 3) AND updatedAt < :before")
    suspend fun purgeFinished(before: Long)

    @Query("DELETE FROM ai_tasks WHERE status = 0")
    suspend fun clearPending()

    @Query("DELETE FROM ai_tasks")
    suspend fun clearAll()

    @Query("DELETE FROM ai_tasks WHERE id = :id")
    suspend fun delete(id: Long)
}

/** `GROUP BY status` 的结果行。Room 要求有构造函数能承接列名。 */
data class AiTaskStatusCount(
    val status: Int,
    val total: Int,
)


/**
 * v13 → v14（AI 智能功能模块）：ai_artifacts / feed_ai_profiles / ai_tasks 三张新表。
 *
 * 全部是新增表，不改动任何既有列，存量数据零影响。
 *
 * 两条写在血泪里的约束：
 * 1. 索引名必须与 Room 从 `@Entity` 生成的名字**逐字符一致**（`index_<表>_<列1>_<列2>`），
 *    否则新装用户与升级用户的 schema 会分叉——Room 校验时才发现，且报错信息极难定位。
 * 2. **只有带 `@ColumnInfo(defaultValue = ...)` 的字段才有 SQL DEFAULT**。
 *    光写 Kotlin 默认值（`val status: Int = 0`）Room 不会生成 SQL DEFAULT——
 *    它只在构造 entity 时用这个值。migration 里多写一个 DEFAULT 就会让
 *    `onValidateSchema` 判定不符，升级用户一开 App 就崩。
 *    本文件里因此只有 `priority` / `inputChars` / `outputChars` 三列带 DEFAULT。
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ai_artifacts` (
                `subjectKind` INTEGER NOT NULL,
                `subjectId` INTEGER NOT NULL,
                `kind` INTEGER NOT NULL,
                `payload` TEXT NOT NULL,
                `model` TEXT NOT NULL,
                `inputChars` INTEGER NOT NULL DEFAULT 0,
                `outputChars` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`subjectKind`, `subjectId`, `kind`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_artifacts_kind` ON `ai_artifacts` (`kind`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_artifacts_createdAt` ON `ai_artifacts` (`createdAt`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `feed_ai_profiles` (
                `feedId` INTEGER NOT NULL,
                `summaryPrompt` TEXT,
                `autoSummary` INTEGER,
                `autoTags` INTEGER,
                `autoClassify` INTEGER,
                `autoScore` INTEGER,
                `watchHealth` INTEGER,
                `priority` INTEGER NOT NULL DEFAULT 0,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`feedId`)
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ai_tasks` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `kind` INTEGER NOT NULL,
                `targetId` INTEGER NOT NULL,
                `payload` TEXT NOT NULL,
                `status` INTEGER NOT NULL,
                `attempts` INTEGER NOT NULL,
                `priority` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL,
                `runAfter` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `lastError` TEXT,
                `dedupeKey` TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ai_tasks_status_runAfter` ON `ai_tasks` (`status`, `runAfter`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_tasks_dedupeKey` ON `ai_tasks` (`dedupeKey`)")
    }
}


/**
 * AI 模块专用查询。
 *
 * 单独一个 DAO 而不是往 `ArticleDao` 里塞：ArticleDao 已 500 多行、承载着列表分页这条主链路，
 * AI 的批处理查询（按时间窗扫候选）与它的关注点完全不同，混在一起两边都难读。
 * 这里每条查询都限定时间窗或走主键，避免后台批处理变成全表扫描拖慢前台。
 */
@Dao
interface AiSupportDao {
    /** 批量取文章要点（不含正文）：跨文章功能（去重/聚合/简报）组 prompt 用。 */
    @Query("SELECT id, feedId, title FROM articles WHERE id IN (:ids)")
    suspend fun briefsOf(ids: List<Long>): List<AiArticleBrief>

    /**
     * 待处理文章：近期抓取且**有内容可送模型**的。
     * 排除既无正文也无摘要的条目——送空内容给模型只会换回一段编造的文字。
     */
    @Query(
        """
        SELECT id FROM articles
        WHERE fetchedAt >= :since
          AND ((contentText IS NOT NULL AND contentText != '') OR (summary IS NOT NULL AND summary != ''))
        ORDER BY fetchedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun processableIdsSince(since: Long, limit: Int): List<Long>

    /** 某订阅源在时间窗内的文章数（健康监控判断"是否停更"）。 */
    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId AND fetchedAt >= :since")
    suspend fun countRecentOfFeed(feedId: Long, since: Long): Int

    /** 某订阅源最后一次抓到文章的时间（健康监控判断"是否失效"）。 */
    @Query("SELECT MAX(fetchedAt) FROM articles WHERE feedId = :feedId")
    suspend fun lastFetchedOfFeed(feedId: Long): Long?

    /** 某订阅源正文不完整的文章数（健康监控判断"抓取质量下滑"）。 */
    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId AND contentIncomplete = 1")
    suspend fun countIncompleteOfFeed(feedId: Long): Int

    /** 某订阅源的文章总数，算不完整率的分母。 */
    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId")
    suspend fun countOfFeed(feedId: Long): Int

    /** 阅读习惯：时间窗内的打开时刻，统计活跃时段用。 */
    @Query(
        """
        SELECT lastOpenedAt FROM articles
        WHERE lastOpenedAt IS NOT NULL AND lastOpenedAt >= :since
        """,
    )
    suspend fun openTimesSince(since: Long): List<Long>

    /** 阅读习惯：时间窗内按订阅源的打开次数，算集中度用。 */
    @Query(
        """
        SELECT feedId, COUNT(*) AS total FROM articles
        WHERE lastOpenedAt IS NOT NULL AND lastOpenedAt >= :since
        GROUP BY feedId
        ORDER BY total DESC
        """,
    )
    suspend fun openCountsByFeedSince(since: Long): List<FeedOpenCount>

    /** 每日报告：时间窗内读过的文章数。 */
    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 1 AND lastOpenedAt >= :since")
    suspend fun countReadSince(since: Long): Int

    /** 每日报告：时间窗内新增但未读的文章 id，供模型挑"错过的好文"。 */
    @Query(
        """
        SELECT id FROM articles
        WHERE isRead = 0 AND isStarred = 0 AND isBookmarked = 0 AND fetchedAt >= :since
        ORDER BY fetchedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun unreadIdsSince(since: Long, limit: Int): List<Long>
}

/** 文章要点：只带组 prompt 需要的字段，避免把正文一起查出来。 */
data class AiArticleBrief(
    val id: Long,
    val feedId: Long,
    val title: String,
)

/** 某订阅源被打开的次数，阅读习惯的集中度计算用。 */
data class FeedOpenCount(
    val feedId: Long,
    val total: Int,
)
