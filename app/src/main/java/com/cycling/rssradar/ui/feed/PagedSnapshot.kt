package com.cycling.rssradar.ui.feed

import com.cycling.rssradar.core.data.db.ArticleWithFeed
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 分页快照纯函数模块（ADR-0006）：OFFSET 分页累积快照的全部规则，脱离 ViewModel
 * 即可 JVM 测试。背景规模：源 1000+、文章数万条，四 tab 统一 LIMIT/OFFSET 分页。
 *
 * 核心规则：**追加必去重**。任何 DB 删除（归档清理/单篇删除的本地移除）都会让
 * OFFSET 位移，下一页可能与快照尾部重叠；重复 id 会让 LazyColumn 的 key 冲突
 * 直接崩溃（实测 "Key 50442 was already used"）。ADR-0006 的 OFFSET 快照模型缺口，
 * 根治方向是 keyset 分页，追加边界先在此兜住。
 */
object PagedSnapshot {

    /**
     * 追加一页并按 key 去重：与快照已有项重复、以及页内自重复的项都会被丢弃。
     * 返回新快照；调用方据 `page.size == pageSize` 判 hasMore。
     */
    fun <T, K> append(current: List<T>, page: List<T>, keyOf: (T) -> K): List<T> {
        val seen = current.mapTo(HashSet()) { keyOf(it) }
        return current + page.filter { seen.add(keyOf(it)) }
    }

    /** 原地更新单条（key 命中的项经 [transform] 替换，其余原样）。 */
    fun <T, K> mutate(list: List<T>, keyOf: (T) -> K, key: K, transform: (T) -> T): List<T> =
        list.map { if (keyOf(it) == key) transform(it) else it }

    /** 移除单条。 */
    fun <T, K> remove(list: List<T>, keyOf: (T) -> K, key: K): List<T> =
        list.filterNot { keyOf(it) == key }
}

/**
 * 滚动自动标记已读（#11）的纯逻辑，从 FeedListScreen 的 LaunchedEffect 里抽出：
 * 把「列表槽位 → 文章 id」铺平（粘性日期头也占一个槽位，用 null 占位），
 * 滚过视口顶部的槽位即视为已读。槽位表与列表结构严格同构，
 * 否则粘性头开启时索引会错位。
 *
 * 与 [dayGroups] 配对使用：开启粘性头时 [groups] 必须来自同一个 dayGroups 结果。
 */
fun scrollSlots(
    articles: List<ArticleWithFeed>,
    stickyDateHeader: Boolean,
    groups: List<DayGroup> = emptyList(),
): List<Long?> = if (stickyDateHeader) {
    buildList {
        groups.forEach { group ->
            add(null) // 日期头占一个槽位
            group.items.forEach { add(it.article.id) }
        }
    }
} else {
    articles.map { it.article.id }
}

/** 首屏可见索引 [firstVisibleIndex] 之前的槽位里，仍未读的文章 id（保持列表序）。 */
fun passedUnreadIds(
    slots: List<Long?>,
    firstVisibleIndex: Int,
    unreadIds: Set<Long>,
): List<Long> = slots.subList(0, firstVisibleIndex.coerceAtMost(slots.size))
    .filterNotNull()
    .filter { it in unreadIds }

/** 粘性日期头分组（issue #56）：一个自然日一组。 */
data class DayGroup(
    /** 自然日的 epochDay，作 LazyColumn key；无日期组为 [UNDATED_DAY_KEY]。 */
    val key: Long,
    /** 粘性头文案，恒有值——包括无发布日期的沉底组。 */
    val label: String,
    val items: List<ArticleWithFeed>,
)

/** 无发布日期组的 key 哨兵：真实 epochDay 可正可负，用极小值避免撞车。 */
const val UNDATED_DAY_KEY = Long.MIN_VALUE

/**
 * 按自然日分组（issue #56）。无发布日期的文章沉底为「未知日期」组。
 * 输入须已按时间倒序（DAO 排序保证，SQLite 视 NULL 最小故沉底）；
 * groupBy 保持首次出现顺序，无需再排。
 * [labelOf] 是标签缝：入参为 epochDay，生产用日历日文案，JVM 测试注入固定文案。
 */
fun dayGroups(
    articles: List<ArticleWithFeed>,
    labelOf: (Long) -> String = { day -> calendarDayLabel(day) },
): List<DayGroup> {
    val dated = mutableListOf<Pair<Long, ArticleWithFeed>>()
    val undated = mutableListOf<ArticleWithFeed>()
    articles.forEach { a ->
        val ts = a.article.publishedAt
        if (ts == null) undated.add(a) else dated.add(ts to a)
    }
    val groups = dated
        .groupBy { (ts, _) ->
            Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        }
        .map { (day, list) ->
            DayGroup(key = day, label = labelOf(day), items = list.map { it.second })
        }
    // 沉底组也必须带日期头：没有头的话，这批文章会一直挂在最后一个日期头下面，
    // 滚多久吸顶的日期都不变，看着就像粘性头坏了。
    return if (undated.isEmpty()) groups else groups + DayGroup(UNDATED_DAY_KEY, "未知日期", undated)
}

private val WEEKDAYS = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 粘性日期头文案：按**日历日**给，不用相对时长。
 *
 * 这里踩过坑：原先拿组内第一条的时间戳喂 `DateUtils.getRelativeTimeSpanString`，
 * 那是「距今多久」而不是「哪一天」——duration 不足 24h 一律显示「N 小时前」，
 * 于是昨天 23:00 的组和今天凌晨的组会顶着同一句话；排序一乱（推荐 tab 按分数排、
 * 日期不是单调的）撞车更多。日期头要回答的是「这一天是哪天」，只能按日历算。
 *
 * 纯 java.time，无 Android 依赖，JVM 可测。
 * 注意：跨零点后已渲染的标签不会自己变，要下一次重组（翻页/刷新/切 tab）才更新。
 */
fun calendarDayLabel(day: Long, today: Long = LocalDate.now().toEpochDay()): String {
    when (today - day) {
        0L -> return "今天"
        1L -> return "昨天"
        2L -> return "前天"
    }
    val date = LocalDate.ofEpochDay(day)
    // 一周内带周几，读起来最快；超出一周只有日期。diff 为负（源的时间戳超前，
    // 时区或源站时钟问题）不给「明天」这种假答案，一律落到绝对日期。
    return if (today - day in 3..6) {
        "${date.monthValue}月${date.dayOfMonth}日 ${WEEKDAYS[date.dayOfWeek.value - 1]}"
    } else if (date.year == LocalDate.ofEpochDay(today).year) {
        "${date.monthValue}月${date.dayOfMonth}日"
    } else {
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
}
