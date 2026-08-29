package com.cycling.rssradar.data

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

    @Query("UPDATE feeds SET groupName = :groupName WHERE id = :feedId")
    suspend fun updateGroup(feedId: Long, groupName: String)

    /** 重命名分组：该组所有 feed 的 groupName 批量改。 */
    @Query("UPDATE feeds SET groupName = :newName WHERE groupName = :oldName")
    suspend fun renameGroup(oldName: String, newName: String)

    /** 删除分组：该组 feed 全部移回默认组（不删源）。 */
    @Query("UPDATE feeds SET groupName = :defaultGroup WHERE groupName = :groupName")
    suspend fun moveGroupToDefault(groupName: String, defaultGroup: String = DEFAULT_GROUP)

    @Query("UPDATE feeds SET title = :title WHERE id = :feedId")
    suspend fun updateTitle(feedId: Long, title: String)

    /** 删除订阅源：articles 经外键 CASCADE 级联删除。 */
    @Query("DELETE FROM feeds WHERE id = :feedId")
    suspend fun deleteFeed(feedId: Long)
}

@Dao
interface ArticleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<ArticleEntity>)

    @Query(
        """
        SELECT articles.*, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        ORDER BY articles.publishedAt IS NULL, articles.publishedAt DESC, articles.fetchedAt DESC
        """,
    )
    fun observeAllWithFeed(): Flow<List<ArticleWithFeed>>

    @Query(
        """
        SELECT articles.*, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.isRead = 0
        ORDER BY articles.publishedAt IS NULL, articles.publishedAt DESC, articles.fetchedAt DESC
        """,
    )
    fun observeUnreadWithFeed(): Flow<List<ArticleWithFeed>>

    @Query(
        """
        SELECT articles.*, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.isStarred = 1
        ORDER BY articles.publishedAt IS NULL, articles.publishedAt DESC, articles.fetchedAt DESC
        """,
    )
    fun observeStarredWithFeed(): Flow<List<ArticleWithFeed>>

    @Query(
        """
        SELECT articles.*, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE articles.isBookmarked = 1
        ORDER BY articles.publishedAt IS NULL, articles.publishedAt DESC, articles.fetchedAt DESC
        """,
    )
    fun observeBookmarkedWithFeed(): Flow<List<ArticleWithFeed>>

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
        SELECT articles.*, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE (articles.title LIKE :query OR articles.summary LIKE :query
            OR articles.contentText LIKE :query OR feeds.title LIKE :query)
        ORDER BY articles.publishedAt IS NULL, articles.publishedAt DESC, articles.fetchedAt DESC
        """,
    )
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

    /** 抓取原网页正文后回填。同样不触碰用户状态；封面只在原本没有时才补 og:image。 */
    @Query(
        """
        UPDATE articles SET
            content = :content, contentText = :contentText, contentSource = :contentSource,
            readingMinutes = :readingMinutes,
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
    )

    @Query("UPDATE articles SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE articles SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("UPDATE articles SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun setBookmarked(id: Long, bookmarked: Boolean)

    @Query("UPDATE articles SET isRead = 1")
    suspend fun markAllRead()
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

@Database(
    entities = [FeedEntity::class, ArticleEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao
}
