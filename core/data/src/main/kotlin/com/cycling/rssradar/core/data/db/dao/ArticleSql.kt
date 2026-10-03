package com.cycling.rssradar.core.data.db.dao

/*
 * 文章表查询共用的 SQL 片段。
 *
 * 拆出独立文件的原因：这些常量原先定义在 `ArticleDao.kt` 顶部，与 891 行的接口体挤在
 * 一个文件里。DAO 按关注点拆成 7 个子接口后，常量必须能被多个接口共享，
 * 于是从 `private const` 提升为 `internal const`——`private` 是文件级可见性，
 * 拆分后各子接口就取不到了。
 */

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
internal const val ARTICLE_LIST_COLUMNS =
    "articles.id, articles.feedId, articles.link, articles.title, articles.summary, " +
        "articles.publishedAt, articles.fetchedAt, articles.author, articles.contentSource, " +
        "articles.isRead, articles.isStarred, articles.isBookmarked, articles.readingMinutes, " +
        "articles.coverUrl, articles.aiSummary, articles.contentIncomplete, articles.lastOpenedAt, " +
        "articles.mediaKind, articles.starredAt, articles.bookmarkedAt, articles.mediaUrl"

/**
 * 分组筛选（issue #74，issue #75 升级为可空版）的 WHERE 片段：选中分组时按 feeds.groupName 过滤。
 * 关键语义：**默认组要同时命中空串**——历史/导入数据的 feeds.groupName 可能是 ''，
 * UI 上显示为 [DEFAULT_GROUP]，只写 `groupName = :group` 会漏掉这些行
 * （与内存侧 `groupName.ifBlank { DEFAULT_GROUP }` 兜底是同一语义，见 SubscriptionsViewModel）。
 *
 * 可空短路（issue #75）：分组筛选为「全部」时 :group 传 null，谓词恒真，不加过滤开销——
 * 分组 × 分区两个维度合成同一条查询（Filtered 变体），DAO 查询总数不随维度组合膨胀。
 *
 * 绑定参数：group = 所选分组名或 null；isDefaultGroup = (group == DEFAULT_GROUP)，
 * 由仓库层判定后传入，DAO 不依赖常量字符串比较。
 *
 * 性能：JOIN 仍是逐文章行按 feeds 主键探测、在探测行上追加这个过滤条件，
 * ORDER BY 不变（publishedAt DESC, fetchedAt DESC），照走 index_articles_publishedAt_fetchedAt，
 * 不会退化成全表外排序——排序表达式零新增。
 */
internal const val GROUP_FILTER_PREDICATE_NULLABLE =
    "(:group IS NULL OR (feeds.groupName = :group OR (:isDefaultGroup = 1 AND feeds.groupName = '')))"

/**
 * 内容分区（issue #75）的 WHERE 片段：选中分区时按 feeds.contentType 过滤。
 * 「全部」分区 = :contentType 传 null，谓词恒真，不加过滤开销（与分组谓词同一思路）。
 * 绑定参数由仓库层从 UI 枚举换算（ContentTypeFilter.dbValue），DAO 不依赖 UI 类型。
 */
internal const val CONTENT_TYPE_FILTER_PREDICATE =
    "(:contentType IS NULL OR feeds.contentType = :contentType)"

/**
 * 搜索的二次筛选谓词（源 / 时间范围 / 未读 / 收藏 / 稍后读）。
 * 时间基准沿用 `COALESCE(publishedAt, fetchedAt)`，与归档清理、批量标已读一致——
 * 否则无发布日期的文章会在筛选里凭空消失。
 */
internal const val SEARCH_FILTER_PREDICATE =
    "(:feedId IS NULL OR articles.feedId = :feedId) " +
        "AND (:fromMillis IS NULL OR COALESCE(articles.publishedAt, articles.fetchedAt) >= :fromMillis) " +
        "AND (:toMillis IS NULL OR COALESCE(articles.publishedAt, articles.fetchedAt) <= :toMillis) " +
        "AND (:unreadOnly = 0 OR articles.isRead = 0) " +
        "AND (:starredOnly = 0 OR articles.isStarred = 1) " +
        "AND (:bookmarkedOnly = 0 OR articles.isBookmarked = 1)"

/**
 * 收藏页（收藏 / 稍后读同一套排序与筛选）的通用条件：:starred 选集合，时间范围与来源同搜索。
 */
internal const val LIBRARY_CONDITION =
    "(CASE WHEN :starred = 1 THEN articles.isStarred ELSE articles.isBookmarked END) = 1 " +
        "AND (:feedId IS NULL OR articles.feedId = :feedId) " +
        "AND (:fromMillis IS NULL OR COALESCE(articles.publishedAt, articles.fetchedAt) >= :fromMillis) " +
        "AND (:toMillis IS NULL OR COALESCE(articles.publishedAt, articles.fetchedAt) <= :toMillis)"
