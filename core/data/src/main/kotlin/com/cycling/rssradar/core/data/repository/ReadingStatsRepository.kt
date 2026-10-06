package com.cycling.rssradar.core.data.repository

import com.cycling.rssradar.core.data.db.dao.ArticleDao
import com.cycling.rssradar.core.data.db.projection.FeedOpenStat
import com.cycling.rssradar.core.data.db.projection.FeedUnreadCount
import com.cycling.rssradar.core.data.db.projection.ReadingWindowStat
import kotlinx.coroutines.flow.Flow

/**
 * 阅读统计取数门面。
 *
 * 统计口径的装配在 core/domain 的 ReadingStatsDashboard（纯函数、JVM 可测），
 * 这里只负责把原料取出来——原先这些查询由 UI 层的 ViewModel 直接打 ArticleDao，
 * 绕过仓储层，等于把 SQL 布局泄进了界面（AppModule 的注释写着「UI 侧不碰 DAO」，
 * 规则被自己破了）。
 */
class ReadingStatsRepository(private val articleDao: ArticleDao) {

    suspend fun windowStat(since: Long): ReadingWindowStat = articleDao.readingWindowStat(since)

    suspend fun allOpenedTimestamps(): List<Long> = articleDao.allOpenedTimestamps()

    suspend fun openedCountsByFeedSince(since: Long): List<FeedUnreadCount> = articleDao.openedCountsByFeedSince(since)

    suspend fun topOpenedFeeds(since: Long, limit: Int): List<FeedOpenStat> = articleDao.topOpenedFeeds(since, limit)

    suspend fun starredCount(): Int = articleDao.starredCount()

    suspend fun bookmarkedCount(): Int = articleDao.bookmarkedCount()

    fun observeUnreadCount(): Flow<Int> = articleDao.observeUnreadCount()
}
