package com.cycling.rssradar.core.data.db.dao

import androidx.room.Dao

/**
 * 文章表的 DAO 门面：原先 891 行的接口体按关注点拆到同包的 7 个子接口，
 * 这里只做组合，不新增查询。
 *
 * 为什么拆：单文件 981 行、89 个方法横跨「信息流分页 / 搜索 / 收藏 / 写入 / 清理归档 /
 * 统计观察」六件事，改列表分页要滚过搜索与墓碑逻辑；Room 生成实现时也不利于定位。
 *
 * 为什么拆成继承而不是拆成多个独立 DAO：`ArticleDao` 被仓库层、ViewModel 与 5 个单测
 * 直接注入（`daoProxy<ArticleDao>`），拆成多个 DAO 意味着十几处注入点全改签名。
 * Room 支持 `@Dao` 接口继承父接口的查询方法——生成实现时会把整条继承链上的方法
 * 一并实现，于是「文件变短」与「调用点零改动」可以同时成立。
 *
 * 各子接口职责：
 * - [ArticleFeedListDao]  信息流四 tab 的分页查询与计数（含分组 × 分区过滤变体）
 * - [ArticleBrowseDao]    单源列表、推荐/相关候选、详情与打开记录
 * - [ArticleSearchDao]    搜索（FTS 与 LIKE 两条路）与检索索引维护
 * - [ArticleLibraryDao]   收藏 / 稍后读列表
 * - [ArticleWriteDao]     写入与用户状态（已读 / 收藏 / 稍后读 / 正文与 AI 摘要）
 * - [ArticleCleanupDao]   归档清理、墓碑、清空订阅源
 * - [ArticleStatsDao]     阅读统计、观察流与维护辅助查询
 *
 * 共享的 SQL 片段（列清单、过滤谓词）在 [ArticleSql.kt]。
 */
@Dao
interface ArticleDao :
    ArticleFeedListDao,
    ArticleBrowseDao,
    ArticleSearchDao,
    ArticleLibraryDao,
    ArticleWriteDao,
    ArticleCleanupDao,
    ArticleStatsDao
