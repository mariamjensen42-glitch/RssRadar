package com.cycling.rssradar.core.ui.paging

/**
 * 分页快照纯函数模块：OFFSET 分页累积快照的全部规则，脱离 ViewModel
 * 即可 JVM 测试。背景规模：源 1000+、文章数万条，四 tab 统一 LIMIT/OFFSET 分页。
 *
 * 核心规则：**追加必去重**。任何 DB 删除（归档清理/单篇删除的本地移除）都会让
 * OFFSET 位移，下一页可能与快照尾部重叠；重复 id 会让 LazyColumn 的 key 冲突
 * 直接崩溃（实测 "Key 50442 was already used"）。OFFSET 快照模型缺口，
 * 根治方向是 keyset 分页，追加边界先在此兜住。
 *
 * 为什么在 core:ui 而不在某个 feature 里：信息流 / 收藏整理 / 搜索三个页面共用同一条
 * 规则，留在其中任何一个包里都会逼着另外两个反向依赖它（feature 之间禁止互依）。
 * 泛型、无 Android 依赖，也不违反 core:ui 不碰 data·domain 的铁律。
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
