package com.cycling.rssradar.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.cycling.rssradar.core.data.db.projection.ArticleSearchSourceRow
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleSearchDao {
    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE (articles.title LIKE :query OR articles.summary LIKE :query
            OR articles.contentText LIKE :query OR feeds.title LIKE :query)
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        """,
    )
    @Suppress("QUERY_MISMATCH")
    fun search(query: String): Flow<List<ArticleWithFeed>>

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        JOIN articles_fts ON articles_fts.rowid = articles.id
        WHERE articles_fts MATCH :match AND $SEARCH_FILTER_PREDICATE
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun searchFts(
        match: String,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
        unreadOnly: Boolean,
        starredOnly: Boolean,
        bookmarkedOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<ArticleWithFeed>

    @Query(
        """
        SELECT COUNT(*)
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        JOIN articles_fts ON articles_fts.rowid = articles.id
        WHERE articles_fts MATCH :match AND $SEARCH_FILTER_PREDICATE
        """,
    )
    suspend fun countSearchFts(
        match: String,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
        unreadOnly: Boolean,
        starredOnly: Boolean,
        bookmarkedOnly: Boolean,
    ): Int

    @Query(
        """
        SELECT $ARTICLE_LIST_COLUMNS, feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE (articles.title LIKE :pattern ESCAPE '\' OR articles.summary LIKE :pattern ESCAPE '\'
            OR articles.contentText LIKE :pattern ESCAPE '\' OR feeds.title LIKE :pattern ESCAPE '\')
            AND $SEARCH_FILTER_PREDICATE
        ORDER BY articles.publishedAt DESC, articles.fetchedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    @Suppress("QUERY_MISMATCH")
    suspend fun searchLike(
        pattern: String,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
        unreadOnly: Boolean,
        starredOnly: Boolean,
        bookmarkedOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<ArticleWithFeed>

    @Query(
        """
        SELECT COUNT(*)
        FROM articles
        JOIN feeds ON articles.feedId = feeds.id
        WHERE (articles.title LIKE :pattern ESCAPE '\' OR articles.summary LIKE :pattern ESCAPE '\'
            OR articles.contentText LIKE :pattern ESCAPE '\' OR feeds.title LIKE :pattern ESCAPE '\')
            AND $SEARCH_FILTER_PREDICATE
        """,
    )
    suspend fun countSearchLike(
        pattern: String,
        feedId: Long?,
        fromMillis: Long?,
        toMillis: Long?,
        unreadOnly: Boolean,
        starredOnly: Boolean,
        bookmarkedOnly: Boolean,
    ): Int

    @Query("SELECT id, title, summary, contentText FROM articles WHERE searchText IS NULL OR searchText = '' ORDER BY id ASC LIMIT :limit")
    suspend fun articlesMissingSearchText(limit: Int): List<ArticleSearchSourceRow>

    @Query("UPDATE articles SET searchText = :searchText WHERE id = :id")
    suspend fun setSearchText(id: Long, searchText: String)

    @Query("UPDATE articles SET searchText = NULL")
    suspend fun clearSearchText()

    @Query("SELECT COUNT(*) FROM articles WHERE searchText IS NOT NULL AND searchText != ''")
    suspend fun countIndexed(): Int
}
