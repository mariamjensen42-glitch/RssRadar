package com.cycling.rssradar.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow


/** 默认分组名。 */
const val DEFAULT_GROUP = "默认"
const val GROUP_TECH = "科技"
const val GROUP_DEV = "开发"
const val GROUP_DESIGN = "设计"

@Entity(
    tableName = "feeds",
    indices = [Index(value = ["url"], unique = true)],
)
data class FeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val createdAt: Long,
    /** 所属分组名。空表示未分组，归入 DEFAULT_GROUP。 */
    @ColumnInfo(defaultValue = "默认") val groupName: String = DEFAULT_GROUP,
    /** 站点图标 URL（favicon），由订阅信息或网络抓取得到。 */
    val iconUrl: String? = null,
    /** 订阅源类型：0=常规 RSS/Atom，1=RSSHub 路由。 */
    @ColumnInfo(defaultValue = "0") val sourceType: Int = SOURCE_TYPE_RSS,
    /** 是否参与自动同步（issue #58）。屏蔽后不参与自动同步，手动刷新照常。 */
    @ColumnInfo(defaultValue = "1") val syncEnabled: Boolean = true,
    /**
     * Feed 级预设（issue #9）：详情页是否自动抓取该源的原网页正文（ADR-0001 按需抓取）。
     * 关闭后详情页只显示 feed 自带内容，不再联网抓全文。
     */
    @ColumnInfo(defaultValue = "1") val fullContentEnabled: Boolean = true,
    /**
     * Feed 级通知开关（#31）：关闭后该源的新文章不进系统通知，其他行为不变。
     * 默认开（与全局通知开关默认关不冲突：全局关时一条都不发）。
     */
    @ColumnInfo(defaultValue = "1") val notificationsEnabled: Boolean = true,
) {
    companion object {
        const val SOURCE_TYPE_RSS = 0
        const val SOURCE_TYPE_RSSHUB = 1
    }
}

@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(
            entity = FeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["feedId", "link"], unique = true)],
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    val link: String,
    val title: String,
    /** 短摘要：列表与搜索用。由正文提纯截断而来，不是正文的替代品。 */
    val summary: String?,
    val publishedAt: Long?,
    val fetchedAt: Long,
    /** 正文 HTML（净化后），来自 feed 自带全文字段；无则说明需按需抓取原网页。 */
    val content: String? = null,
    /** 正文纯文本副本，供检索与阅读时长计算。 */
    val contentText: String? = null,
    /** 作者，feed 自带，可能为空。 */
    val author: String? = null,
    /** 正文来源：0=无 1=feed 自带 2=原网页抓取。 */
    @ColumnInfo(defaultValue = "0") val contentSource: Int = CONTENT_SOURCE_NONE,
    /** 是否已读。 */
    @ColumnInfo(defaultValue = "0") val isRead: Boolean = false,
    /** 是否收藏（星标）。 */
    @ColumnInfo(defaultValue = "0") val isStarred: Boolean = false,
    /** 是否加入"稍后读"书签。 */
    @ColumnInfo(defaultValue = "0") val isBookmarked: Boolean = false,
    /** 估算阅读时间（分钟），无值表示未估算。 */
    val readingMinutes: Int? = null,
    /** 封面图 URL。 */
    val coverUrl: String? = null,
    /**
     * 正文被判定为「不完整」的标记（ADR-0012）：抓取成功但正文过短 / 无段落 /
     * 疑似 JS 渲染 / 疑似付费墙时置 1。数据仍然写入（比空白页好），但 UI 必须如实告知用户。
     */
    @ColumnInfo(defaultValue = "0") val contentIncomplete: Boolean = false,
    /** AI 摘要：LLM 基于正文生成的内容概括。生成物语义同用户状态——刷新永不覆盖。见 ADR-0005。 */
    val aiSummary: String? = null,
) {
    companion object {
        const val CONTENT_SOURCE_NONE = 0
        const val CONTENT_SOURCE_FEED = 1
        const val CONTENT_SOURCE_WEB = 2
    }
}

/** 文章 + 所属订阅的扁平视图，方便 UI 直接渲染。 */
data class ArticleWithFeed(
    @Embedded val article: ArticleEntity,
    val feedTitle: String,
    val feedGroup: String,
    val feedIconUrl: String?,
)

/**
 * 列表流专用列清单：剔除 content / contentText 两列全文（每篇可达几十上百 KB）。
 * 列表卡片只用到 title/summary/coverUrl 等轻字段，全量物化几百篇会把 Java 堆吃满
 * （OOM 诊断：observeUnreadWithFeed 的 CursorWindow.nativeGetString 分配失败）。
 * 详情页仍走 getWithFeed 的 SELECT *，正文完整。
 *
 * 缺了 content/contentText 会让 Room 报 QUERY_MISMATCH（缺列），这是**有意为之**：
 * 列表查询统一带 `@Suppress("QUERY_MISMATCH")` 声明，
 * 两列由 ArticleEntity 的 Kotlin 默认值 null 兜底。
 * 反过来——新增列必须同步加进这里，否则列表页拿到的永远是默认值（静默出错）。
 */
private const val ARTICLE_LIST_COLUMNS =
    "articles.id, articles.feedId, articles.link, articles.title, articles.summary, " +
        "articles.publishedAt, articles.fetchedAt, articles.author, articles.contentSource, " +
        "articles.isRead, articles.isStarred, articles.isBookmarked, articles.readingMinutes, " +
        "articles.coverUrl, articles.aiSummary, articles.contentIncomplete"

/** 文章 id 与 link 的轻量对，供刷新时一次建 link→id 映射（#48 批量 upsert）。 */
data class ArticleIdLink(
    val id: Long,
    val link: String,
)

/** 文章 feedId 与 link 的轻量对，供删除前抓「将删文章」的名单写墓碑。 */
data class ArticleFeedLink(
    val feedId: Long,
    val link: String,
)

/**
 * 归档/清空墓碑（issue「归档后刷新文章复活」）：真删的文章在这里留一笔 (feedId, link)，
 * 刷新 upsert 见到墓碑就跳过——否则 feed XML 里还挂着的旧条目会被当成新文章插回来，
 * 用户看到「删了又回来」。单篇删除走撤销机制（restore），不在此列。
 *
 * 墓碑按 archivedAt 滚动清理（90 天）：feed 一般只挂最近几十条，更早的 link 不会再被重发。
 */
@Entity(
    tableName = "archived_article_tombstones",
    primaryKeys = ["feedId", "link"],
)
data class ArchivedArticleTombstoneEntity(
    val feedId: Long,
    val link: String,
    val archivedAt: Long,
)

/** 订阅 + 未读数（每条结果用 Flow 汇总）。 */
data class FeedUnreadCount(
    val feedId: Long,
    val cnt: Int,
)

@Dao
interface FeedDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(feed: FeedEntity): Long

    @Query("SELECT id FROM feeds WHERE url = :url LIMIT 1")
    suspend fun findIdByUrl(url: String): Long?

    @Query("SELECT * FROM feeds WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FeedEntity?

    @Query("SELECT * FROM feeds ORDER BY groupName ASC, title ASC")
    fun observeAll(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds")
    suspend fun getAll(): List<FeedEntity>

    @Query("UPDATE feeds SET groupName = :groupName WHERE id = :feedId")
    suspend fun updateGroup(feedId: Long, groupName: String)

    /** 批量移动订阅源到分组（issue #7）。 */
    @Query("UPDATE feeds SET groupName = :groupName WHERE id IN (:feedIds)")
    suspend fun updateGroupForFeeds(feedIds: List<Long>, groupName: String)

    /** 重命名分组：该组所有 feed 的 groupName 批量改。 */
    @Query("UPDATE feeds SET groupName = :newName WHERE groupName = :oldName")
    suspend fun renameGroup(oldName: String, newName: String)

    /** 删除分组：该组 feed 全部移回默认组（不删源）。 */
    @Query("UPDATE feeds SET groupName = :defaultGroup WHERE groupName = :groupName")
    suspend fun moveGroupToDefault(groupName: String, defaultGroup: String = DEFAULT_GROUP)

    @Query("UPDATE feeds SET title = :title WHERE id = :feedId")
    suspend fun updateTitle(feedId: Long, title: String)

    /** 站点图标回填（只在为 null 时抓，写入后不再覆盖，见 CONTEXT.md「站点图标」）。 */
    @Query("UPDATE feeds SET iconUrl = :iconUrl WHERE id = :feedId")
    suspend fun updateIconUrl(feedId: Long, iconUrl: String)

    /** 删除订阅源：articles 经外键 CASCADE 级联删除。 */
    @Query("DELETE FROM feeds WHERE id = :feedId")
    suspend fun deleteFeed(feedId: Long)

    /** 自动同步开关（issue #58）：屏蔽后不参与自动同步，手动刷新照常。 */
    @Query("UPDATE feeds SET syncEnabled = :enabled WHERE id = :feedId")
    suspend fun updateSyncEnabled(feedId: Long, enabled: Boolean)

    /** Feed 级预设：全文抓取开关（issue #9）。 */
    @Query("UPDATE feeds SET fullContentEnabled = :enabled WHERE id = :feedId")
    suspend fun updateFullContentEnabled(feedId: Long, enabled: Boolean)

    /** Feed 级通知开关（#31）。 */
    @Query("UPDATE feeds SET notificationsEnabled = :enabled WHERE id = :feedId")
    suspend fun updateNotificationsEnabled(feedId: Long, enabled: Boolean)

    /** 参与自动同步的源 id 清单（issue #58）。 */
    @Query("SELECT id FROM feeds WHERE syncEnabled = 1")
    suspend fun getSyncEnabledFeedIds(): List<Long>
}

@Dao
interface ArticleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<ArticleEntity>)

    // —— 信息流列表：轻量投影 + LIMIT/OFFSET 分页，四个 tab 统一 ——
    // 规模现实：订阅源 1000+、文章数万条。任何"全表 observe 全量物化"的列表流
    // 都会在每次 DB 写失效时重查数万行，内存与主线程都扛不住（OOM 诊断结论）。

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        ORDER BY articles.publishedAt IS NULL, articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadAllWithFeedPaged(limit: Int, offset: Int): List<ArticleWithFeed>

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.isRead = 0
        ORDER BY articles.publishedAt IS NULL, articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadUnreadWithFeedPaged(limit: Int, offset: Int): List<ArticleWithFeed>

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.isStarred = 1
        ORDER BY articles.publishedAt IS NULL, articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadStarredWithFeedPaged(limit: Int, offset: Int): List<ArticleWithFeed>

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.isBookmarked = 1
        ORDER BY articles.publishedAt IS NULL, articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadBookmarkedWithFeedPaged(limit: Int, offset: Int): List<ArticleWithFeed>

    /** 订阅源文章列表（CONTEXT.md「Feed article list」）：单源全部文章，新→旧，分页。 */
    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.feedId = :feedId
        ORDER BY articles.publishedAt IS NULL, articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadFeedWithFeedPaged(feedId: Long, limit: Int, offset: Int): List<ArticleWithFeed>

    @Query(
        """
        SELECT articles.*, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.id = :id
        LIMIT 1
        """,
    )
    suspend fun getWithFeed(id: Long): ArticleWithFeed?

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE (articles.title LIKE :query OR articles.summary LIKE :query
            OR articles.contentText LIKE :query OR feeds.title LIKE :query)
        ORDER BY articles.publishedAt IS NULL, articles.publishedAt DESC, articles.fetchedAt DESC
        """,
    )
    @Suppress("QUERY_MISMATCH")
    fun search(query: String): Flow<List<ArticleWithFeed>>

    @Query("SELECT COUNT(*) FROM articles")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query(
        """
        SELECT feedId AS feedId, COUNT(*) AS cnt
        FROM articles
        WHERE isRead = 0
        GROUP BY feedId
        """,
    )
    fun observeUnreadCountByFeed(): Flow<List<FeedUnreadCount>>

    @Query("SELECT id FROM articles WHERE feedId = :feedId AND link = :link LIMIT 1")
    suspend fun findIdByLink(feedId: Long, link: String): Long?

    /** 该源全部已有文章的 id/link 对，一次查询建映射（#48 批量 upsert）。 */
    @Query("SELECT id, link FROM articles WHERE feedId = :feedId")
    suspend fun getIdLinkPairsByFeed(feedId: Long): List<ArticleIdLink>

    /** 同源文章 id（列表序：新→旧），详情页上一篇/下一篇导航用。 */
    @Query(
        """
        SELECT id FROM articles WHERE feedId = :feedId
        ORDER BY publishedAt IS NULL, publishedAt DESC, fetchedAt DESC
        """,
    )
    suspend fun getFeedArticleIds(feedId: Long): List<Long>

    /**
     * 增量刷新：只更新内容状态（标题/时间/摘要/正文），绝不触碰用户状态
     * （isRead/isStarred/isBookmarked）。见 CONTEXT.md「用户状态」。
     */
    @Query(
        """
        UPDATE articles SET
            title = :title, summary = :summary, content = :content, contentText = :contentText,
            author = :author, publishedAt = :publishedAt, coverUrl = :coverUrl,
            readingMinutes = :readingMinutes, contentSource = :contentSource, fetchedAt = :fetchedAt
        WHERE id = :id
        """,
    )
    suspend fun updateContentState(
        id: Long,
        title: String,
        summary: String?,
        content: String?,
        contentText: String?,
        author: String?,
        publishedAt: Long?,
        coverUrl: String?,
        readingMinutes: Int?,
        contentSource: Int,
        fetchedAt: Long,
    )

    /**
     * 抓取原网页正文后回填。同样不触碰用户状态；封面只在原本没有时才补 og:image。
     * [contentIncomplete] 由抓取端判定（ADR-0012）：正文过短/无段落/JS 空壳/付费墙时置 1，
     * 内容照写，但 UI 必须如实提示"不完整"。
     */
    @Query(
        """
        UPDATE articles SET
            content = :content, contentText = :contentText, contentSource = :contentSource,
            readingMinutes = :readingMinutes, contentIncomplete = :contentIncomplete,
            coverUrl = COALESCE(coverUrl, :coverUrl)
        WHERE id = :id
        """,
    )
    suspend fun updateFetchedContent(
        id: Long,
        content: String?,
        contentText: String?,
        contentSource: Int,
        readingMinutes: Int?,
        coverUrl: String?,
        contentIncomplete: Boolean,
    )

    @Query("UPDATE articles SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    /** 已读/未读互切（长按菜单，issue #46）。 */
    @Query("UPDATE articles SET isRead = :read WHERE id = :id")
    suspend fun setRead(id: Long, read: Boolean)

    /**
     * 按条件批量标记已读（#10）：只更新未读行，返回真实影响行数（UI 如实汇报数字）。
     * 时间基准 = COALESCE(publishedAt, fetchedAt)，与归档清理、MarkAsReadCondition 一致。
     */
    @Query(
        "UPDATE articles SET isRead = 1 WHERE isRead = 0 " +
            "AND COALESCE(publishedAt, fetchedAt) < :cutoff",
    )
    suspend fun markReadOlderThan(cutoff: Long): Int

    /** 全部未读 → 已读，返回真实影响行数。 */
    @Query("UPDATE articles SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllUnreadRead(): Int

    /** 滚动自动标记已读（#11）用：只更新给定 id 里仍未读的行。 */
    @Query("UPDATE articles SET isRead = 1 WHERE isRead = 0 AND id IN (:ids)")
    suspend fun markReadBatch(ids: List<Long>): Int

    /**
     * 同步后新进库且未读的文章（#31 通知用）："入库时间 ≥ 本轮同步起点"即为新文章，
     * 逐个源的通知开关在这一层过滤——关掉的源一条都不进通知。
     * [limit] 只是通知里要展示的条数上限。
     */
    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.fetchedAt >= :since AND articles.isRead = 0 AND feeds.notificationsEnabled = 1
        ORDER BY articles.publishedAt IS NULL, articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadNewUnreadSince(since: Long, limit: Int): List<ArticleWithFeed>

    /** 删除单篇文章（撤销由 restore 带原 id 插回）。 */
    @Query("DELETE FROM articles WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 撤销删除：原样插回（REPLACE 保 id 不变；订阅源未动，外键不悬空）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restore(article: ArticleEntity)

    @Query("UPDATE articles SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("UPDATE articles SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun setBookmarked(id: Long, bookmarked: Boolean)

    @Query("UPDATE articles SET isRead = 1")
    suspend fun markAllRead()

    /** 写入 AI 摘要。生成物不参与内容状态刷新，只由 AI 功能写入/清空。 */
    @Query("UPDATE articles SET aiSummary = :aiSummary WHERE id = :id")
    suspend fun updateAiSummary(id: Long, aiSummary: String?)

    /**
     * 归档清理（issue #57）：删除早于 cutoff 的文章，真删。
     * 豁免 = 用户主动标记（收藏/稍后读）永不自动删除；已读状态不豁免。
     * 保留期基准 = COALESCE(publishedAt, fetchedAt)，与 KeepArchived.cutoffMillis 一致。
     */
    @Query(
        "DELETE FROM articles WHERE isStarred = 0 AND isBookmarked = 0 " +
            "AND COALESCE(publishedAt, fetchedAt) < :cutoff",
    )
    suspend fun deleteExpiredArticles(cutoff: Long): Int

    // —— 墓碑（issue「归档后刷新文章复活」）：删除前抓名单，删除后刷新不再复活 ——

    /** 归档即将删除的文章名单（豁免规则与 deleteExpiredArticles 完全一致）。 */
    @Query(
        "SELECT feedId, link FROM articles WHERE isStarred = 0 AND isBookmarked = 0 " +
            "AND COALESCE(publishedAt, fetchedAt) < :cutoff",
    )
    suspend fun getExpiredArticleLinks(cutoff: Long): List<ArticleFeedLink>

    /** 清空单源前抓名单（豁免规则与 deleteByFeed 一致）。 */
    @Query(
        "SELECT feedId, link FROM articles WHERE feedId = :feedId " +
            "AND isStarred = 0 AND isBookmarked = 0",
    )
    suspend fun getArticleLinksByFeed(feedId: Long): List<ArticleFeedLink>

    /** 清空分组前抓名单（豁免规则与 deleteByGroup 一致）。 */
    @Query(
        "SELECT feedId, link FROM articles WHERE isStarred = 0 AND isBookmarked = 0 " +
            "AND feedId IN (SELECT id FROM feeds WHERE groupName = :groupName)",
    )
    suspend fun getArticleLinksByGroup(groupName: String): List<ArticleFeedLink>

    /** 写墓碑；同一篇重复删除时 IGNORE（保留首次时间，滚动清理按最早一笔算）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTombstones(items: List<ArchivedArticleTombstoneEntity>)

    /** 某 feed 下的墓碑 link 集合，刷新 upsert 用它过滤「删了又回来」。 */
    @Query("SELECT link FROM archived_article_tombstones WHERE feedId = :feedId")
    suspend fun getTombstonedLinks(feedId: Long): List<String>

    /** 墓碑滚动清理：早于 cutoff 的墓碑删除，防止表无限增长。 */
    @Query("DELETE FROM archived_article_tombstones WHERE archivedAt < :cutoff")
    suspend fun deleteTombstonesOlderThan(cutoff: Long): Int

    // —— 清空（issue #8）：只删文章不删源，豁免规则同归档清理 ——
    // 用户主动标记的（收藏/稍后读）不因批量清空丢失，这是「清空」与「删除订阅」的差别：
    // 后者走外键 CASCADE，一律真删。

    /** 清空单个订阅源的文章，返回删除条数。 */
    @Query("DELETE FROM articles WHERE feedId = :feedId AND isStarred = 0 AND isBookmarked = 0")
    suspend fun deleteByFeed(feedId: Long): Int

    /** 清空一个分组下所有订阅源的文章，返回删除条数。 */
    @Query(
        """
        DELETE FROM articles
        WHERE isStarred = 0 AND isBookmarked = 0
            AND feedId IN (SELECT id FROM feeds WHERE groupName = :groupName)
        """,
    )
    suspend fun deleteByGroup(groupName: String): Int

    /** 清空时被豁免保留的条数，供 UI 如实汇报（数字必须真实）。 */
    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId AND (isStarred = 1 OR isBookmarked = 1)")
    suspend fun countProtectedByFeed(feedId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM articles
        WHERE (isStarred = 1 OR isBookmarked = 1)
            AND feedId IN (SELECT id FROM feeds WHERE groupName = :groupName)
        """,
    )
    suspend fun countProtectedByGroup(groupName: String): Int
}

/**
 * v1 → v2：增加文章已读/收藏/稍后读/阅读时长/封面字段，给 feeds 加 groupName / iconUrl。
 * 旧库直接清空（开发期允许，生产前需要写真正的迁移脚本）。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE articles ADD COLUMN isStarred INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE articles ADD COLUMN isBookmarked INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE articles ADD COLUMN readingMinutes INTEGER")
        db.execSQL("ALTER TABLE articles ADD COLUMN coverUrl TEXT")
        db.execSQL("ALTER TABLE feeds ADD COLUMN groupName TEXT NOT NULL DEFAULT '默认'")
        db.execSQL("ALTER TABLE feeds ADD COLUMN iconUrl TEXT")
    }
}

/**
 * v2 → v3：文章增加正文列（content/contentText/author/contentSource）。
 * 依据 ADR-0001：正文与摘要分离，summary 回归"短摘要"语义。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN content TEXT")
        db.execSQL("ALTER TABLE articles ADD COLUMN contentText TEXT")
        db.execSQL("ALTER TABLE articles ADD COLUMN author TEXT")
        db.execSQL("ALTER TABLE articles ADD COLUMN contentSource INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v3 → v4：feeds 增加订阅源类型（sourceType）。
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feeds ADD COLUMN sourceType INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v4 → v5：articles 增加 AI 摘要列（aiSummary）。见 ADR-0005。
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN aiSummary TEXT")
    }
}

/**
 * v5 → v6：feeds 增加自动同步开关（syncEnabled，issue #58）。
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feeds ADD COLUMN syncEnabled INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * v6 → v7：feeds 增加 Feed 级预设——全文抓取开关（fullContentEnabled，issue #9）。
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feeds ADD COLUMN fullContentEnabled INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * v7 → v8：正文抓取可观测性。
 * - articles.contentIncomplete：正文被判定为「不完整」的标记（ADR-0012）。
 * - content_fetch_log：每次按需抓取留一条记录（链接/站点/状态码/重试次数/页数/原因），
 *   诊断页与"哪些站点抓不到正文"的归因都依赖它。
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN contentIncomplete INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS content_fetch_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                link TEXT NOT NULL,
                host TEXT NOT NULL,
                statusCode INTEGER,
                attempts INTEGER NOT NULL,
                pages INTEGER NOT NULL,
                ok INTEGER NOT NULL,
                failure TEXT,
                issue TEXT,
                contentChars INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_content_fetch_log_host ON content_fetch_log (host)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_content_fetch_log_createdAt ON content_fetch_log (createdAt)")
    }
}

/**
 * v8 → v9：归档/清空墓碑表（issue「归档后刷新文章复活」）。
 * 真删的文章留一笔 (feedId, link)，刷新 upsert 跳过墓碑，杜绝「删了又同步回来」。
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS archived_article_tombstones (
                feedId INTEGER NOT NULL,
                link TEXT NOT NULL,
                archivedAt INTEGER NOT NULL,
                PRIMARY KEY(feedId, link)
            )
            """.trimIndent(),
        )
    }
}

/** v9 → v10（#31）：feeds 增加 Feed 级通知开关列，默认开（存量源行为不变）。 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE feeds ADD COLUMN notificationsEnabled INTEGER NOT NULL DEFAULT 1",
        )
    }
}

@Database(
    entities = [FeedEntity::class, ArticleEntity::class, ContentFetchLogEntity::class, ArchivedArticleTombstoneEntity::class],
    version = 10,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao
    abstract fun contentFetchLogDao(): ContentFetchLogDao
}

/**
 * 一次「按需抓原文」的留痕（ADR-0012 可观测性）。
 *
 * 抓不到正文时旧实现只有一个静默的 null，既不知道是站点反爬、限流还是提取器没认出容器——
 * 有了这张表才能回答「哪些站点抓不到、为什么、重试了几次」。
 */
@Entity(
    tableName = "content_fetch_log",
    indices = [Index("host"), Index("createdAt")],
)
data class ContentFetchLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val link: String,
    val host: String,
    val statusCode: Int?,
    val attempts: Int,
    val pages: Int,
    /** 是否拿到了可写入的正文（false = 抓取/提取失败）。 */
    val ok: Boolean,
    /** [com.cycling.rssradar.data.parser.FetchFailure] 的 name，成功时 null。 */
    val failure: String?,
    /** [com.cycling.rssradar.data.parser.ExtractionIssue] 的 name，成功时非空。 */
    val issue: String?,
    val contentChars: Int,
    val durationMs: Long,
    val createdAt: Long,
)

/** 按站点聚合：总数 / 失败数 / 不完整数。诊断页的分组统计直接吃这个。 */
data class FetchHostStat(
    val host: String,
    val total: Int,
    val failures: Int,
    val incomplete: Int,
)

@Dao
interface ContentFetchLogDao {
    @Insert
    suspend fun insert(log: ContentFetchLogEntity)

    @Query("SELECT * FROM content_fetch_log ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): kotlinx.coroutines.flow.Flow<List<ContentFetchLogEntity>>

    /** 只看有问题的（失败或不完整），诊断页默认视图。 */
    @Query(
        """
        SELECT * FROM content_fetch_log
        WHERE ok = 0 OR (issue IS NOT NULL AND issue != 'NONE')
        ORDER BY createdAt DESC LIMIT :limit
        """,
    )
    fun observeProblems(limit: Int): kotlinx.coroutines.flow.Flow<List<ContentFetchLogEntity>>

    @Query(
        """
        SELECT host, COUNT(*) AS total,
               SUM(CASE WHEN ok = 0 THEN 1 ELSE 0 END) AS failures,
               SUM(CASE WHEN ok = 1 AND issue IS NOT NULL AND issue != 'NONE' THEN 1 ELSE 0 END) AS incomplete
        FROM content_fetch_log
        GROUP BY host
        ORDER BY (failures + incomplete) DESC, total DESC
        """,
    )
    fun observeHostStats(): kotlinx.coroutines.flow.Flow<List<FetchHostStat>>

    @Query("DELETE FROM content_fetch_log")
    suspend fun clear()

    /** 同一链接的历史：看重试与状态演变。limit 显式传入，不给 DAO 方法加默认参数（Room 代码生成边界情况）。 */
    @Query("SELECT * FROM content_fetch_log WHERE link = :link ORDER BY createdAt DESC LIMIT :limit")
    suspend fun historyOf(link: String, limit: Int): List<ContentFetchLogEntity>
}
