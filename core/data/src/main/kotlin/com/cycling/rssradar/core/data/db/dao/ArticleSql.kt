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
 * 内容分区（issue #75）的 WHERE 片段：按 feeds.contentType 过滤。
 * 保留 :contentType 可空（传 null 时谓词恒真）是因为仓库层是通用入口、别处仍可能传 null；
 * **UI 侧已不含「不过滤」那一档** —— 2026-10-06 用「文章」替掉「全部」，四个分区各自都带
 * 确定的类型值，所以首页这条查询总会带上这个条件、拿不到空短路的省。
 * 绑定参数由仓库层从 UI 枚举换算（ContentTypeFilter.dbValue），DAO 不依赖 UI 类型。
 */
internal const val CONTENT_TYPE_FILTER_PREDICATE =
    "(:contentType IS NULL OR feeds.contentType = :contentType)"

/**
 * 「按信息价值排序」专用：LEFT JOIN 产物表 + 取值列。
 *
 * **只被 by-value 变体的查询使用，默认时间排序的查询一行都不带。**
 * 原因是本文件上方那条已经被踩出来的教训：ORDER BY 里一旦出现表达式
 * （`CASE WHEN :sortByValue THEN score END`），SQLite 就再也走不了
 * `index_articles_publishedAt_fetchedAt`，整条查询退化成全表外排序——
 * 而这条查询是全 App 最热的一条。所以宁可多一组查询，也不把排序做成参数塞进热路径。
 *
 * 这个 JOIN 落在 ai_artifacts 的**主键位置**（主键 = subjectKind/subjectId/kind），
 * 每行一次 PK 探测，不产生额外扫描。
 *
 * `ponytail:` ⚠️ 硬编码的 0 / 12 必须与 `AiScope.ARTICLE.dbValue` / `AiFeature.NOISE.dbValue` 一致
 * （@Query 只吃编译期常量，用不了枚举的构造属性）；
 * `AiValueSqlContractTest` 把这条绑定关系变成了会变红的测试。
 * 天花板：枚举的 dbValue 一旦重排，这里不会自动跟着变（只有测试拦）；
 * 升级路径：测试已经把它变成编译后的硬约束，暂时没有更便宜的做法。
 */
internal const val AI_VALUE_JOIN =
    " LEFT JOIN ai_artifacts AS aiNoise ON aiNoise.subjectKind = 0 AND aiNoise.subjectId = articles.id AND aiNoise.kind = 12"

/** 取值表达式：降噪写下的信息价值分。列、谓词、排序共用这一份，避免写出三种形态。 */
internal const val AI_VALUE_EXPR = "aiNoise.score"

/** 取值列：null = 这篇还没被评估过。 */
internal const val AI_VALUE_COLUMN = "$AI_VALUE_EXPR AS aiValue"

/** 按信息价值排序时的 ORDER BY：分数高的在前；没评估过的（NULL）在 SQLite 里天然沉底。 */
internal const val AI_VALUE_ORDER_BY =
    "ORDER BY $AI_VALUE_EXPR DESC, articles.publishedAt DESC, articles.fetchedAt DESC"

/**
 * 「只看值得读」的 WHERE 片段：低于阈值的不进列表。
 *
 * **未评估的（score 为 null）保留**——它们不是"低价值"而是"还没判"。
 * 一起藏掉的话，刚打开这个开关列表会瞬间空掉，看起来像坏了。
 * `:minValue` 传 null 时整个谓词恒真（「按信息价值排序」那一档不需要过滤）。
 */
internal const val AI_VALUE_FILTER_PREDICATE =
    "(:minValue IS NULL OR $AI_VALUE_EXPR IS NULL OR $AI_VALUE_EXPR >= :minValue)"

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
