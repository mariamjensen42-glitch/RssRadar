package com.cycling.rssradar.core.domain.rsshub

import com.cycling.rssradar.core.model.rsshub.RssHubRoute

/**
 * 「猜你想订」的建议路径 → 内置路由目录的匹配。
 *
 * 为什么需要它：模型给的是一句人话的路径（`/bilibili/user/video`），而目录里那条路由是
 * `/bilibili/user/video/:uid/:embed?`——参数占位符让它永远不可能逐字相等。不做这层匹配，
 * 一条建议就只剩"填进地址栏碰运气"这一种落点，而目录里明明已经有现成的一条可直接填参预览。
 *
 * 纯函数：路由表由调用方取好（目录是动态数据，本对象不认识它的来源）。
 */
object RouteSuggestion {

    /**
     * 在 [routes] 里找出建议指向的那条路由；找不到返回 null（调用方退到 URL / 搜索两条路）。
     *
     * 两级匹配：
     * 1. **整体相等**——建议里已经带了参数占位符（或目录里就是无参路由），逐字对上最可信。
     * 2. **字面骨架相等**——`/a/:x` 与 `/a/:x/:y` 骨架相同，此时取**热度最高**那条。
     *    骨架相同意味着"模型想说的是这个栏目"，热度高的那条至少是更常被订阅的那一个；
     *    而且它与目录默认排序（热度）口径一致，用户看到的结果不会与目录里的第一名打架。
     */
    fun resolve(suggested: String, routes: List<RssHubRoute>): RssHubRoute? {
        val raw = suggested.trim()
        if (raw.isEmpty() || routes.isEmpty()) return null

        val wanted = normalize(raw)
        routes.firstOrNull { normalize(it.path) == wanted }?.let { return it }

        val wantedBase = RoutePath.base(wanted)
        if (wantedBase.isEmpty()) return null

        return routes
            .filter { RoutePath.base(it.path) == wantedBase }
            .maxWithOrNull(
                compareBy(
                    { it.heat },
                    // 热度相同时取更短的那条：参数更少 = 更容易填、更可能被直接订阅
                    { -it.path.length },
                    // 兜底保证确定性：同热度同长度时按 path 字典序，绝不依赖表的顺序
                    { it.path },
                ),
            )
    }

    /** 补上开头的 `/` 并去掉尾斜杠：建议的写法常常缺头少尾，而目录里的 path 永远是规范形。 */
    private fun normalize(path: String): String = "/" + path.trim().trim('/')
}
