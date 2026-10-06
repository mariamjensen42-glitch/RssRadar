package com.cycling.rssradar.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    indices = [
        // upsert 去重：同一订阅源下 link 唯一。同时也是 JOIN feeds 与 `WHERE feedId = ?` 的索引。
        Index(value = ["feedId", "link"], unique = true),
        // 列表排序（#65）：主列表五个查询统一按「发布日期倒序、无日期沉底」取页。
        // 没有它时，ORDER BY 里的 `publishedAt IS NULL` 是表达式，SQLite 只能全表扫描 + 外排序，
        // 数万行下每次翻页都要重排一次全表。有了它，翻页只跳索引项，只回表当前页那几十行。
        Index(value = ["publishedAt", "fetchedAt"]),
    ],
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
    /**
     * 最近一次打开详情页的时间（推荐流画像的唯一采集信号，ADR-0013）。
     * 每次打开都更新——只记首开时间就无法区分"最近常看"和"三个月前看过一次"，
     * 源亲和度的时间衰减也就无从算起。null = 从未打开过。
     */
    val lastOpenedAt: Long? = null,
    /**
     * 条目级媒体种类（ADR-0014）：enclosure 是 video/audio 时覆盖 feed 的内容类型——
     * 图文源里偶尔夹一条播客或视频，feed 级分类解释不了它。null/0=跟随 feed。
     */
    @ColumnInfo(defaultValue = "0") val mediaKind: Int = MEDIA_KIND_NONE,
    val starredAt: Long? = null,
    val bookmarkedAt: Long? = null,
    val mediaUrl: String? = null,
    /** 预分词后的检索语料，只服务 FTS 索引；不进列表查询（每篇可达数十 KB）。 */
    val searchText: String? = null,
) {
    companion object {
        const val CONTENT_SOURCE_NONE = 0
        const val CONTENT_SOURCE_FEED = 1
        const val CONTENT_SOURCE_WEB = 2

        const val MEDIA_KIND_NONE = 0
        const val MEDIA_KIND_VIDEO = 1
        const val MEDIA_KIND_AUDIO = 2
    }
}
