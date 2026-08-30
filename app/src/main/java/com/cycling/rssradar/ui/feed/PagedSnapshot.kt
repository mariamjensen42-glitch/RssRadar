package com.cycling.rssradar.ui.feed

import android.text.format.DateUtils
import com.cycling.rssradar.data.db.ArticleWithFeed
import java.time.Instant
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

/** 粘性日期头分组（issue #56）：一个自然日一组。 */
data class DayGroup(
    /** 自然日的 epochDay，作 LazyColumn key；无日期组为负数哨兵。 */
    val key: Long,
    /** 粘性头文案；无发布日期的文章组不带日期头。 */
    val label: String?,
    val items: List<ArticleWithFeed>,
)

/**
 * 按自然日分组（issue #56）。无发布日期的文章沉底为独立组，不参与日期头。
 * 输入须已按时间倒序（DAO 排序保证）；groupBy 保持首次出现顺序，无需再排。
 * [labelOf] 是标签缝：生产用 DateUtils 相对时间，JVM 测试注入固定文案。
 */
fun dayGroups(
    articles: List<ArticleWithFeed>,
    labelOf: (Long) -> String = ::relativeDayLabel,
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
            DayGroup(
                key = day,
                label = labelOf(list.first().first),
                items = list.map { it.second },
            )
        }
    return if (undated.isEmpty()) groups else groups + DayGroup(key = -1L, label = null, items = undated)
}

/** 生产标签：Android 相对时间文案。 */
private fun relativeDayLabel(ts: Long): String =
    DateUtils.getRelativeTimeSpanString(ts).toString()
