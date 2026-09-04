package com.cycling.rssradar.core.model.rsshub

/** 目录数据来自哪里：随包的内置快照，还是用户后来更新的缓存。 */
enum class CatalogSource {
    /** APK 内置快照（assets）。 */
    BUILTIN,

    /** 在线更新后写入本地的缓存。 */
    UPDATED,
}

/**
 * 一份可用的路由目录。
 *
 * [routes] 已按热度降序：默认展示「最热的 N 条」就是顺序取前 N 条，不必再排序。
 */
data class RouteCatalog(
    val routes: List<RssHubRoute>,
    /** 数据生成时刻（epoch millis）；null 表示来源没给。 */
    val generatedAtMillis: Long?,
    val source: CatalogSource,
) {
    val isEmpty: Boolean get() = routes.isEmpty()
}

/**
 * 目录检索：纯函数，不联网、不碰 Android API。
 *
 * 3800 条全在内存里，直接线性打分即可——上 FTS / 倒排索引属于过度设计，
 * 一次全量扫描在中端机上是个位毫秒级。
 */
object RouteCatalogQuery {

    /**
     * 单次检索最多返回多少条。
     *
     * 宽查询（如搜「a」）会命中上千条，全塞进 LazyColumn 既卡又没意义——
     * 按分数截断，用户继续输入自然收敛。
     */
    const val RESULT_LIMIT = 200

    fun search(routes: List<RssHubRoute>, query: String, category: String): List<RssHubRoute> {
        val q = query.trim().lowercase()
        val anyCategory = category == RouteCategory.ALL

        if (q.isBlank()) {
            val matched = ArrayList<RssHubRoute>()
            for (route in routes) {
                if (!anyCategory && !route.categories.contains(category)) continue
                matched.add(route)
            }
            // 这里不假设入参已排好序：目录数据是降序的，但调用方未必是人肉保证的那一处，
            // 全量排序 3800 条是个位毫秒级，比埋一个隐式契约便宜。
            matched.sortByDescending { it.heat }
            return if (matched.size <= RESULT_LIMIT) matched else matched.subList(0, RESULT_LIMIT)
        }

        val scored = ArrayList<Pair<Int, RssHubRoute>>()
        for (route in routes) {
            if (!anyCategory && !route.categories.contains(category)) continue
            val score = score(route, q)
            if (score > 0) scored.add(score to route)
        }
        scored.sortWith(compareByDescending<Pair<Int, RssHubRoute>> { it.first }.thenByDescending { it.second.heat })
        return if (scored.size <= RESULT_LIMIT) {
            scored.map { it.second }
        } else {
            scored.subList(0, RESULT_LIMIT).map { it.second }
        }
    }

    /** 命中权重：名字前缀 > 名字包含 > 命名空间 > path > 示例标题。热度只作同分排序。 */
    private fun score(route: RssHubRoute, q: String): Int {
        val name = route.name.lowercase()
        val source = route.sourceName.lowercase()
        val namespace = route.namespace.lowercase()
        var score = 0
        if (name.startsWith(q) || source.startsWith(q)) score += 8
        if (name.contains(q) || source.contains(q)) score += 4
        if (namespace.startsWith(q)) score += 4
        else if (namespace.contains(q)) score += 2
        if (route.path.lowercase().contains(q)) score += 1
        if (route.examples.any { it.title.lowercase().contains(q) }) score += 2
        return score
    }
}
