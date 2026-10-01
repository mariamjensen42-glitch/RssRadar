package com.cycling.rssradar.core.data.db.projection

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

/** 文章 id 与所属分组名的轻量对（推荐流按分组过滤推荐序用，issue #74）。 */
data class ArticleIdGroup(
    val id: Long,
    val groupName: String,
)

/** 文章 id 与所属源内容类型的轻量对（推荐流按分区过滤推荐序用，issue #75）。 */
data class ArticleIdContentType(
    val id: Long,
    val contentType: Int,
)
