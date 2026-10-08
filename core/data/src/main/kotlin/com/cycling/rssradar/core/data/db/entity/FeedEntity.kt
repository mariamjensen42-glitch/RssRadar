package com.cycling.rssradar.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cycling.rssradar.core.data.db.DEFAULT_GROUP

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
     * Feed 级预设（issue #9）：详情页是否自动抓取该源的原网页正文（按需抓取）。
     * 关闭后详情页只显示 feed 自带内容，不再联网抓全文。
     */
    @ColumnInfo(defaultValue = "1") val fullContentEnabled: Boolean = true,
    /**
     * Feed 级通知开关（#31）：关闭后该源的新文章不进系统通知，其他行为不变。
     * 默认开（与全局通知开关默认关不冲突：全局关时一条都不发）。
     */
    @ColumnInfo(defaultValue = "1") val notificationsEnabled: Boolean = true,
    /**
     * 内容类型：feed 级主导的分类，决定列表用什么形态浏览。
     * 0=文章（含社媒源，默认）、1=图片（画廊）、2=视频、3=音频。
     * 订阅时按信号预判，用户在订阅操作页可改。
     */
    @ColumnInfo(defaultValue = "0") val contentType: Int = CONTENT_TYPE_ARTICLE,
    /**
     * HTTP 协商缓存凭证（增量刷新）：上次成功响应的 ETag，下次刷新作 If-None-Match。
     * 304 = 源未更新，跳过下载解析写库。null = 尚无凭证（首次刷新走普通请求）。
     */
    val etag: String? = null,
    /** 同 [etag]：上次成功响应的 Last-Modified，下次刷新作 If-Modified-Since。 */
    val lastModified: String? = null,
    /**
     * 连续刷新失败计数（#82 失效源检测）。任何一次成功清零（feedDao.recordRefreshSuccess）。
     * 判失效 = 计数达到失败分类的阈值（core/domain FeedHealth，#80 双阈值）。
     */
    @ColumnInfo(defaultValue = "0") val consecutiveFailures: Int = 0,
    /** 最后一次失败的分类名（core/domain FeedFailureCategory.stored），成功清零时一并置 null。 */
    val failureReason: String? = null,
    /** 最近一次「从失败中恢复」的成功刷新时间；从未失败过为 null（健康源零额外写）。 */
    val lastSuccessAt: Long? = null,
) {
    companion object {
        const val SOURCE_TYPE_RSS = 0
        const val SOURCE_TYPE_RSSHUB = 1

        const val CONTENT_TYPE_ARTICLE = 0
        const val CONTENT_TYPE_IMAGE = 1
        const val CONTENT_TYPE_VIDEO = 2
        const val CONTENT_TYPE_AUDIO = 3
    }
}
