package com.cycling.rssradar.ui.feed

import com.cycling.rssradar.core.data.db.ArticleEntity
import com.cycling.rssradar.core.data.db.ArticleWithFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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

/** 滚动自动标记已读（#11）的槽位规则：槽位表与列表结构（含日期头占位）严格同构。 */
class ScrollSlotsTest {

    private fun article(id: Long, read: Boolean = false) = ArticleWithFeed(
        article = ArticleEntity(id = id, feedId = 1, link = "l$id", title = "t$id", summary = null, publishedAt = null, fetchedAt = 0, isRead = read),
        feedTitle = "f", feedGroup = "g", feedIconUrl = null,
    )

    @Test
    fun `不开粘性头时槽位就是文章 id 列表`() {
        val slots = scrollSlots(listOf(article(1), article(2)), stickyDateHeader = false)
        assertEquals(listOf(1L, 2L), slots)
    }

    @Test
    fun `开粘性头时每组先占一个 null 槽位——错位即标错文章`() {
        val groups = listOf(
            DayGroup(1, "D1", listOf(article(1), article(2))),
            DayGroup(2, "D2", listOf(article(3))),
        )
        val slots = scrollSlots(emptyList(), stickyDateHeader = true, groups = groups)
        assertEquals(listOf<Long?>(null, 1L, 2L, null, 3L), slots)
    }

    @Test
    fun `passedUnreadIds 只取首屏之前且仍未读的 id`() {
        val slots = listOf<Long?>(null, 1L, 2L, null, 3L)
        val unread = setOf(2L, 3L, 4L)
        val passed = passedUnreadIds(slots, firstVisibleIndex = 3, unreadIds = unread)
        assertEquals(listOf(2L), passed)
    }

    @Test
    fun `firstVisibleIndex 越界按槽位表长度截断`() {
        val passed = passedUnreadIds(listOf(1L, 2L), firstVisibleIndex = 99, unreadIds = setOf(1L, 2L))
        assertEquals(listOf(1L, 2L), passed)
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
    fun `无日期文章沉底为独立组，且必须带日期头`() {
        val groups = dayGroups(
            listOf(article(1, 5_000_000L), article(2, null)),
            labelOf = { "D" },
        )

        assertEquals(2, groups.size)
        assertEquals(UNDATED_DAY_KEY, groups[1].key)
        // 沉底组不带日期头的话，这批文章会一直挂在最后一个日期头下面，
        // 滚到底吸顶的日期都不变，看着就像粘性头坏了。
        assertEquals("未知日期", groups[1].label)
        assertEquals(listOf(2L), groups[1].items.map { it.article.id })
    }

    @Test
    fun `全部无日期则只有沉底组`() {
        val groups = dayGroups(listOf(article(1, null), article(2, null)), labelOf = { "D" })
        assertEquals(1, groups.size)
        assertEquals(UNDATED_DAY_KEY, groups[0].key)
    }

    @Test
    fun `每组都有非空标签——存在无头组就等于粘性头会卡住不动`() {
        val groups = dayGroups(
            listOf(article(1, 5_000_000L), article(2, null), article(3, 9_000_000L)),
        )
        assertTrue(groups.all { it.label.isNotBlank() })
        assertEquals(groups.size, groups.map { it.label }.distinct().size)
    }
}

/** 日期头文案：按日历日算，不按相对时长算（回归用例见「跨午夜」）。 */
class CalendarDayLabelTest {

    private val today = LocalDate.of(2026, 9, 2).toEpochDay()

    @Test
    fun `最近三天给相对词`() {
        assertEquals("今天", calendarDayLabel(today, today))
        assertEquals("昨天", calendarDayLabel(today - 1, today))
        assertEquals("前天", calendarDayLabel(today - 2, today))
    }

    @Test
    fun `一周内带周几`() {
        // 2026-08-30 是周日
        assertEquals("8月30日 周日", calendarDayLabel(LocalDate.of(2026, 8, 30).toEpochDay(), today))
    }

    @Test
    fun `超过一周只给日期，跨年补年份`() {
        assertEquals("7月15日", calendarDayLabel(LocalDate.of(2026, 7, 15).toEpochDay(), today))
        assertEquals("2025年12月1日", calendarDayLabel(LocalDate.of(2025, 12, 1).toEpochDay(), today))
    }

    @Test
    fun `跨午夜的两个自然日标签必须不同——相对时长会撞车`() {
        // 昨天 23:50 与今天 00:10 只差 20 分钟，getRelativeTimeSpanString 会给同一句
        // 「N 小时前」；按日历日算则必然是「昨天」与「今天」。
        val labels = listOf(today - 1, today).map { calendarDayLabel(it, today) }
        assertEquals(listOf("昨天", "今天"), labels)
    }

    @Test
    fun `时间戳超前的文章不谎称明天，直接报日期`() {
        assertEquals("9月3日", calendarDayLabel(today + 1, today))
    }
}
