package com.cycling.rssradar.core.data

import com.cycling.rssradar.core.data.db.ArchivedArticleTombstoneEntity
import com.cycling.rssradar.core.data.db.ArticleDao

/**
 * 清空文章结果（issue #8）：deleted = 真删条数，kept = 因收藏/稍后读豁免保留的条数。
 * 两个数字都来自数据库真实统计，UI 直接展示，不做估算。
 */
data class ClearArticlesResult(
    val deleted: Int,
    val kept: Int,
)

/**
 * 文章清理子系统深模块：归档清理与「清空文章」的统一入口。
 *
 * 规则（issue「归档后刷新文章复活」）：真删之前先把将删文章的 (feedId, link) 写进
 * 墓碑表 [ArchivedArticleTombstoneEntity]，再删。刷新 upsert（[RefreshEngine]）见到
 * 墓碑就跳过，否则 feed XML 里还挂着的旧条目会被当成新文章插回来。
 *
 * 豁免规则不变（starred/bookmarked 永不清理），名单查询与删除 SQL 的 WHERE 条款
 * 一一对应（ArticleDao.getExpiredArticleLinks ↔ deleteExpiredArticles 等）。
 *
 * 测试缝：DAO 可手写 fake，[transactionRunner] 注入后规则可纯 JVM 断言
 * （ArchiveReinsertTest 驱动真实 [RefreshEngine] + 本类闭环）。
 */
class ArticleCleaner(
    private val articleDao: ArticleDao,
    private val transactionRunner: TransactionRunner = DirectTransactionRunner,
) {

    companion object {
        /**
         * 墓碑保留时长：feed 一般只挂最近几十条，90 天前的 link 不会再被重发，
         * 墓碑到龄滚动删除，防止表无限增长。
         */
        const val TOMBSTONE_RETENTION_MILLIS: Long = 90L * 86_400_000L
    }

    /**
     * 归档清理：先写墓碑再真删（同事务）。cutoff 为 null（永久档）时不删文章，
     * 只做墓碑滚动清理。返回删除条数。
     */
    suspend fun archiveExpired(cutoff: Long?, now: Long): Int {
        val removed = if (cutoff == null) {
            0
        } else {
            transactionRunner.inTransaction {
                articleDao.insertTombstones(
                    articleDao.getExpiredArticleLinks(cutoff)
                        .map { ArchivedArticleTombstoneEntity(it.feedId, it.link, now) },
                )
                articleDao.deleteExpiredArticles(cutoff)
            }
        }
        articleDao.deleteTombstonesOlderThan(now - TOMBSTONE_RETENTION_MILLIS)
        return removed
    }

    /**
     * 清空单个订阅源的文章（issue #8 语义）：先写墓碑再删，返回删除条数。
     * kept（豁免统计）也在本类算：此前 kept 在 FeedRepository 数、deleted 在这里删，
     * 两条 SQL 的豁免口径隔文件人工对账、无单测锁死——现在口径一处定义。
     */
    suspend fun clearFeed(feedId: Long, now: Long): ClearArticlesResult =
        transactionRunner.inTransaction {
            val kept = articleDao.countProtectedByFeed(feedId)
            articleDao.insertTombstones(
                articleDao.getArticleLinksByFeed(feedId)
                    .map { ArchivedArticleTombstoneEntity(it.feedId, it.link, now) },
            )
            ClearArticlesResult(deleted = articleDao.deleteByFeed(feedId), kept = kept)
        }

    /** 清空一个分组下所有订阅源的文章：先写墓碑再删，豁免口径同 [clearFeed]。 */
    suspend fun clearGroup(groupName: String, now: Long): ClearArticlesResult =
        transactionRunner.inTransaction {
            val kept = articleDao.countProtectedByGroup(groupName)
            articleDao.insertTombstones(
                articleDao.getArticleLinksByGroup(groupName)
                    .map { ArchivedArticleTombstoneEntity(it.feedId, it.link, now) },
            )
            ClearArticlesResult(deleted = articleDao.deleteByGroup(groupName), kept = kept)
        }
}
