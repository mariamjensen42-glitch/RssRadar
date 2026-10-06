package com.cycling.rssradar.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "article_annotations",
    indices = [Index("articleId"), Index("createdAt")],
)
data class ArticleAnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val articleId: Long,
    @ColumnInfo(defaultValue = "0") val kind: Int = KIND_HIGHLIGHT,
    val quote: String,
    val prefix: String,
    val suffix: String,
    @ColumnInfo(defaultValue = "0") val startOffset: Int = 0,
    @ColumnInfo(defaultValue = "0") val endOffset: Int = 0,
    @ColumnInfo(defaultValue = "0") val color: Int = 0,
    val note: String? = null,
    val contentHash: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val KIND_HIGHLIGHT = 0
        const val KIND_NOTE = 1
    }
}

@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "articles_fts")
data class ArticleFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val articleId: Long,
    val searchText: String,
)

/**
 * 标注 + 它所属文章/订阅源的标题。标注列表页要显示「这条标在哪篇文章里」，
 * 没有标题就只能给个 articleId——对读者毫无意义。
 */
data class AnnotationWithArticle(
    @Embedded val annotation: ArticleAnnotationEntity,
    val articleTitle: String?,
    val feedTitle: String?,
)

@Dao
interface ArticleAnnotationDao {

    @Query("SELECT * FROM article_annotations WHERE articleId = :articleId ORDER BY startOffset ASC, id ASC")
    fun observeOfArticle(articleId: Long): Flow<List<ArticleAnnotationEntity>>

    @Query("SELECT * FROM article_annotations WHERE articleId = :articleId ORDER BY startOffset ASC, id ASC")
    suspend fun ofArticle(articleId: Long): List<ArticleAnnotationEntity>

    @Query("SELECT * FROM article_annotations ORDER BY createdAt DESC")
    suspend fun getAll(): List<ArticleAnnotationEntity>

    @Query("SELECT * FROM article_annotations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ArticleAnnotationEntity>>

    @Query(
        "SELECT article_annotations.*, articles.title AS articleTitle, feeds.title AS feedTitle " +
            "FROM article_annotations " +
            "LEFT JOIN articles ON articles.id = article_annotations.articleId " +
            "LEFT JOIN feeds ON feeds.id = articles.feedId " +
            "ORDER BY article_annotations.createdAt DESC",
    )
    fun observeAllWithArticle(): Flow<List<AnnotationWithArticle>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(annotation: ArticleAnnotationEntity): Long

    @Query("UPDATE article_annotations SET note = :note, color = :color, updatedAt = :now WHERE id = :id")
    suspend fun updateNote(id: Long, note: String?, color: Int, now: Long)

    @Query("DELETE FROM article_annotations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM article_annotations WHERE articleId = :articleId")
    suspend fun deleteOfArticle(articleId: Long): Int

    @Query("SELECT COUNT(*) FROM article_annotations WHERE articleId = :articleId")
    suspend fun countOfArticle(articleId: Long): Int

    @Query("DELETE FROM article_annotations WHERE articleId NOT IN (SELECT id FROM articles)")
    suspend fun deleteOrphans(): Int
}

@Dao
interface ArticleFtsDao {

    @Query("INSERT INTO articles_fts(rowid, searchText) VALUES (:articleId, :searchText)")
    suspend fun insert(articleId: Long, searchText: String)

    @Query("DELETE FROM articles_fts WHERE rowid = :articleId")
    suspend fun delete(articleId: Long)

    @Query("DELETE FROM articles_fts")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM articles_fts")
    suspend fun count(): Int
}
