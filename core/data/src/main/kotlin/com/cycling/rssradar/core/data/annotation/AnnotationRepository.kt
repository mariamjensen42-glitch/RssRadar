package com.cycling.rssradar.core.data.annotation

import com.cycling.rssradar.core.data.db.ArticleAnnotationDao
import com.cycling.rssradar.core.data.db.ArticleAnnotationEntity
import com.cycling.rssradar.core.data.db.AnnotationWithArticle
import kotlinx.coroutines.flow.Flow

class AnnotationRepository(private val dao: ArticleAnnotationDao) {

    fun observeOfArticle(articleId: Long): Flow<List<ArticleAnnotationEntity>> =
        dao.observeOfArticle(articleId)

    fun observeAll(): Flow<List<ArticleAnnotationEntity>> = dao.observeAll()

    /** 全库标注 + 所属文章/订阅源标题，供标注列表页。 */
    fun observeAllWithArticle(): Flow<List<AnnotationWithArticle>> = dao.observeAllWithArticle()

    suspend fun ofArticle(articleId: Long): List<ArticleAnnotationEntity> = dao.ofArticle(articleId)

    suspend fun getAll(): List<ArticleAnnotationEntity> = dao.getAll()

    suspend fun add(
        articleId: Long,
        quote: String,
        prefix: String,
        suffix: String,
        startOffset: Int,
        endOffset: Int,
        color: Int,
        note: String? = null,
        contentHash: String? = null,
    ): Long {
        val now = System.currentTimeMillis()
        val trimmed = note?.trim().takeUnless { it.isNullOrEmpty() }
        return dao.upsert(
            ArticleAnnotationEntity(
                articleId = articleId,
                kind = if (trimmed == null) {
                    ArticleAnnotationEntity.KIND_HIGHLIGHT
                } else {
                    ArticleAnnotationEntity.KIND_NOTE
                },
                quote = quote,
                prefix = prefix,
                suffix = suffix,
                startOffset = startOffset,
                endOffset = endOffset,
                color = color,
                note = trimmed,
                contentHash = contentHash,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun updateNote(id: Long, note: String?, color: Int) =
        dao.updateNote(id, note?.trim().takeUnless { it.isNullOrEmpty() }, color, System.currentTimeMillis())

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun deleteOfArticle(articleId: Long): Int = dao.deleteOfArticle(articleId)

    suspend fun countOfArticle(articleId: Long): Int = dao.countOfArticle(articleId)

    suspend fun cleanupOrphans(): Int = dao.deleteOrphans()
}
