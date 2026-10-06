package com.cycling.rssradar.core.ui.paging

import com.cycling.rssradar.core.data.db.entity.ArticleEntity
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.ui.feed.FeedListViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分页快照规则（ADR-0006）：追加必去重（OFFSET 位移兜底，防 LazyColumn key 冲突崩溃）。 */
class PagedSnapshotTest {

    private fun item(id: Long, read: Boolean = false) = ArticleWithFeed(
        article = ArticleEntity(id = id, feedId = 1, link = "l$id", title = "t$id", summary = null, publishedAt = null, fetchedAt = 0, isRead = read),
        feedTitle = "f", feedGroup = "g", feedIconUrl = null,
    )

    private val idOf: (ArticleWithFeed) -> Long = { it.article.id }

    @Test
    fun `追加去重——快照尾部与新页重叠时丢弃重复项`() {
        val current = listOf(item(1), item(2), item(3))
        val page = listOf(item(3), item(4), item(5))

        val result = PagedSnapshot.append(current, page, idOf)

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), result.map { it.article.id })
    }

    @Test
    fun `追加去重——新页内部自重复也只留一份`() {
        val result = PagedSnapshot.append(emptyList(), listOf(item(7), item(7), item(8)), idOf)
        assertEquals(listOf(7L, 8L), result.map { it.article.id })
    }

    @Test
    fun `hasMore 由页满判定`() {
        val full = PagedSnapshot.append(emptyList(), (1L..30L).map(::item), idOf)
        assertEquals(FeedListViewModel.PAGE_SIZE, full.size)
    }

    @Test
    fun `mutate 原地更新命中项，其余不动`() {
        val list = listOf(item(1, read = false), item(2, read = false))

        val result = PagedSnapshot.mutate(list, idOf, 2L) { it.copy(article = it.article.copy(isRead = true)) }

        assertTrue(result[0].article.isRead.not())
        assertTrue(result[1].article.isRead)
    }

    @Test
    fun `remove 移除命中项`() {
        val list = listOf(item(1), item(2))
        val result = PagedSnapshot.remove(list, idOf, 1L)
        assertEquals(listOf(2L), result.map { it.article.id })
    }
}
