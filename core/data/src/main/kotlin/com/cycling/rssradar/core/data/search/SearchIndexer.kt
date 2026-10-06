package com.cycling.rssradar.core.data.search

import com.cycling.rssradar.core.data.db.AppDatabase
import com.cycling.rssradar.core.data.db.projection.ArticleSearchSourceRow
import com.cycling.rssradar.core.domain.search.SearchTextBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchIndexer(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val articleDao = database.articleDao()
    private val ftsDao = database.articleFtsDao()

    suspend fun ensureIndexed(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int =
        withContext(ioDispatcher) {
            val total = articleDao.countAll()
            var indexed = 0
            while (true) {
                val rows = articleDao.articlesMissingSearchText(BATCH)
                if (rows.isEmpty()) break
                indexRows(rows)
                indexed += rows.size
                onProgress(indexed, total)
            }
            indexed
        }

    suspend fun rebuild(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int =
        withContext(ioDispatcher) {
            articleDao.clearSearchText()
            ftsDao.clear()
            ensureIndexedInternal(onProgress)
        }

    suspend fun isConsistent(): Boolean = withContext(ioDispatcher) {
        articleDao.countIndexed() == ftsDao.count()
    }

    private suspend fun ensureIndexedInternal(onProgress: (Int, Int) -> Unit): Int {
        val total = articleDao.countAll()
        var indexed = 0
        while (true) {
            val rows = articleDao.articlesMissingSearchText(BATCH)
            if (rows.isEmpty()) break
            indexRows(rows)
            indexed += rows.size
            onProgress(indexed, total)
        }
        return indexed
    }

    private suspend fun indexRows(rows: List<ArticleSearchSourceRow>) {
        rows.forEach { row ->
            val source = listOfNotNull(row.title, row.summary, row.contentText)
                .filter { it.isNotBlank() }
                .joinToString("\n")
            val text = if (source.isBlank()) "" else SearchTextBuilder.segment(source)
            articleDao.setSearchText(row.id, text)
            if (text.isNotEmpty()) {
                ftsDao.delete(row.id)
                ftsDao.insert(row.id, text)
            }
        }
    }

    companion object {
        const val BATCH = 200
    }
}
