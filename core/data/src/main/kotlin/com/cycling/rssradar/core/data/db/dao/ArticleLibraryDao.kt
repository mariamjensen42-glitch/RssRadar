package com.cycling.rssradar.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed

@Dao
interface ArticleLibraryDao {
    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE $LIBRARY_CONDITION
        ORDER BY CASE WHEN :starred = 1 THEN articles.starredAt ELSE articles.bookmarkedAt END DESC,
                 articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadLibraryByAddedTime(
        starred: Boolean,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
        limit: Int,
        offset: Int,
    ): List<ArticleWithFeed>

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE $LIBRARY_CONDITION
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadLibraryByPublishedAt(
        starred: Boolean,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
        limit: Int,
        offset: Int,
    ): List<ArticleWithFeed>

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE $LIBRARY_CONDITION
        ORDER BY feeds.title ASC, articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun loadLibraryByFeed(
        starred: Boolean,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
        limit: Int,
        offset: Int,
    ): List<ArticleWithFeed>

    @Query("SELECT COUNT(*) FROM articles WHERE $LIBRARY_CONDITION")
    suspend fun countLibrary(
        starred: Boolean,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
    ): Int
}
