package com.cycling.rssradar.ui.feed

import com.cycling.rssradar.data.db.ArticleEntity
import com.cycling.rssradar.data.db.ArticleWithFeed
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

/** 粘性日期头分组规则（issue #56）：无日期沉底、自然日分组、标签经缝注入。 */
class DayGroupsTest {

    private fun article(id: Long, publishedAt: Long?) = ArticleWithFeed(
        article = ArticleEntity(id = id, feedId = 1, link = "l$id", title = "t", summary = null, publishedAt = publishedAt, fetchedAt = 0),
        feedTitle = "f", feedGroup = "g", feedIconUrl = null,
    )

    @Test
    fun `同日合并为一组，不同日分开，顺序保持输入序`() {
        // 同一天 09:00 与 10:00（GMT+8）
        val day1a = 1_000_000L
        val day1b = day1a + 3_600_000L
        val day2 = day1a + 86_400_000L

        val groups = dayGroups(
            listOf(article(1, day1b), article(2, day1a), article(3, day2)),
            labelOf = { "D$it" },
        )

        assertEquals(2, groups.size)
        assertEquals(listOf(1L, 2L), groups[0].items.map { it.article.id })
        assertEquals(listOf(3L), groups[1].items.map { it.article.id })
    }

    @Test
    fun `无日期文章沉底为独立组且不带标签`() {
        val groups = dayGroups(
            listOf(article(1, 5_000_000L), article(2, null)),
            labelOf = { "D" },
        )

        assertEquals(2, groups.size)
        assertEquals(-1L, groups[1].key)
        assertEquals(null, groups[1].label)
        assertEquals(listOf(2L), groups[1].items.map { it.article.id })
    }

    @Test
    fun `全部无日期则只有沉底组`() {
        val groups = dayGroups(listOf(article(1, null), article(2, null)), labelOf = { "D" })
        assertEquals(1, groups.size)
        assertEquals(-1L, groups[0].key)
    }
}
