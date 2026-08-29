package com.cycling.rssradar.data.rsshub

import com.cycling.rssradar.data.db.DEFAULT_GROUP
import com.cycling.rssradar.data.db.GROUP_DEV
import com.cycling.rssradar.data.db.GROUP_TECH
import java.net.URLEncoder



/**
 * RSSHub 路由目录。
 *
 * 这是「以 RSSHub 为核心」的地基：应用内置一份常用路由表，用户选路由 → 填参数 →
 * 拼出完整 URL → 走与普通 RSS 完全相同的校验 / 订阅流程。
 * 目录本身是静态数据，不联网；联网的只有最后那一步 probe。
 */

/** 路由里的单个参数占位，对应 path 模板中的 `:key`。 */
data class RouteParam(
    /** 与 path 模板中 `:key` 的名字一致。 */
    val key: String,
    /** 表单里显示的中文名。 */
    val label: String,
    /** 输入框提示，同时作为「未填写时」的兜底示例值。 */
    val placeholder: String,
)

data class RssHubRoute(
    val id: String,
    val name: String,
    val category: String,
    /** 形如 `/bilibili/user/dynamic/:uid`，参数以 `:key` 占位。 */
    val pathTemplate: String,
    val params: List<RouteParam> = emptyList(),
    val suggestedGroup: String = DEFAULT_GROUP,
    /** 是否列入「热门」。 */
    val featured: Boolean = false,
) {
    val hasParams: Boolean get() = params.isNotEmpty()
}

object RouteCategory {
    const val ALL = "全部"
    const val VIDEO = "视频"
    const val SOCIAL = "社交"
    const val TECH = "技术"
    const val NEWS = "资讯"
    const val GALLERY = "图库"
}

object RssHubRoutes {

    /** 官方公共实例。后续接入自定义实例时替换这里。 */
    const val DEFAULT_HOST = "https://rsshub.app"

    val all: List<RssHubRoute> = listOf(
        RssHubRoute(
            id = "bili-dynamic",
            name = "B站 UP 主动态",
            category = RouteCategory.VIDEO,
            pathTemplate = "/bilibili/user/dynamic/:uid",
            params = listOf(RouteParam("uid", "UP 主 UID", "946974")),
            featured = true,
        ),
        RssHubRoute(
            id = "bili-video",
            name = "B站 视频投稿",
            category = RouteCategory.VIDEO,
            pathTemplate = "/bilibili/user/video/:uid",
            params = listOf(RouteParam("uid", "UP 主 UID", "946974")),
        ),
        RssHubRoute(
            id = "weibo-user",
            name = "微博 用户时间线",
            category = RouteCategory.SOCIAL,
            pathTemplate = "/weibo/user/:uid",
            params = listOf(RouteParam("uid", "用户 UID", "1669879400")),
            featured = true,
        ),
        RssHubRoute(
            id = "zhihu-daily",
            name = "知乎日报",
            category = RouteCategory.NEWS,
            pathTemplate = "/zhihu/daily",
            suggestedGroup = GROUP_TECH,
        ),
        RssHubRoute(
            id = "zhihu-collection",
            name = "知乎 收藏夹",
            category = RouteCategory.NEWS,
            pathTemplate = "/zhihu/collection/:id",
            params = listOf(RouteParam("id", "收藏夹 ID", "26459656")),
            suggestedGroup = GROUP_TECH,
        ),
        // 注意：RSSHub 没有 /github/repos/:owner/:repo/releases 路由——该路径会命中
        // /repos/:user/:type?/:sort?（User Repo），返回用户仓库列表。若要订阅单个仓库的
        // releases，直接用 GitHub 原生 https://github.com/:owner/:repo/releases.atom。
        RssHubRoute(
            id = "github-repos",
            name = "GitHub 用户仓库",
            category = RouteCategory.TECH,
            pathTemplate = "/github/repos/:user",
            params = listOf(RouteParam("user", "用户名", "DIYgod")),
            suggestedGroup = GROUP_DEV,
            featured = true,
        ),
        RssHubRoute(
            id = "github-trending",
            name = "GitHub Trending",
            category = RouteCategory.TECH,
            pathTemplate = "/github/trending/:since/:language",
            params = listOf(
                RouteParam("since", "时间范围", "daily"),
                RouteParam("language", "语言", "kotlin"),
            ),
            suggestedGroup = GROUP_DEV,
        ),
        RssHubRoute(
            id = "v2ex-node",
            name = "V2EX 节点主题",
            category = RouteCategory.TECH,
            pathTemplate = "/v2ex/topics/:node",
            params = listOf(RouteParam("node", "节点名", "programmer")),
            suggestedGroup = GROUP_DEV,
        ),
        RssHubRoute(
            id = "sspai-index",
            name = "少数派 最新",
            category = RouteCategory.NEWS,
            pathTemplate = "/sspai/index",
            suggestedGroup = GROUP_TECH,
        ),
        RssHubRoute(
            id = "douban-group",
            name = "豆瓣小组",
            category = RouteCategory.SOCIAL,
            pathTemplate = "/douban/group/:id",
            params = listOf(RouteParam("id", "小组 ID", "726094")),
        ),
        RssHubRoute(
            id = "youtube-channel",
            name = "YouTube 频道",
            category = RouteCategory.VIDEO,
            pathTemplate = "/youtube/channel/:id",
            params = listOf(RouteParam("id", "Channel ID", "UCBR8-60-B28hp2BmDPdntcQ")),
        ),
        RssHubRoute(
            id = "twitter-user",
            name = "X / Twitter 用户",
            category = RouteCategory.SOCIAL,
            pathTemplate = "/twitter/user/:id",
            params = listOf(RouteParam("id", "用户名", "elonmusk")),
        ),
        RssHubRoute(
            id = "pixiv-ranking",
            name = "Pixiv 排行榜",
            category = RouteCategory.GALLERY,
            pathTemplate = "/pixiv/ranking/:mode",
            params = listOf(RouteParam("mode", "模式", "daily")),
        ),
        RssHubRoute(
            id = "hackernews",
            name = "Hacker News",
            category = RouteCategory.TECH,
            pathTemplate = "/hackernews/:section",
            params = listOf(RouteParam("section", "板块", "front")),
            suggestedGroup = GROUP_DEV,
        ),
    )

    /** 筛选栏用的分类序列，「全部」永远排第一。 */
    val categories: List<String> = listOf(RouteCategory.ALL) +
        all.map { it.category }.distinct()

    fun byId(id: String): RssHubRoute? = all.firstOrNull { it.id == id }

    /**
     * 按查询词 + 分类过滤。匹配名称与 path，都是小写包含匹配。
     */
    fun search(query: String, category: String): List<RssHubRoute> {
        val q = query.trim().lowercase()
        return all.filter { route ->
            val inCategory = category == RouteCategory.ALL || route.category == category
            val inQuery = q.isBlank() ||
                route.name.lowercase().contains(q) ||
                route.pathTemplate.lowercase().contains(q)
            inCategory && inQuery
        }
    }

    /** 还没填的参数。为空表示可以生成 URL。 */
    fun missingParams(route: RssHubRoute, values: Map<String, String>): List<RouteParam> =
        route.params.filter { values[it.key].isNullOrBlank() && it.placeholder.isBlank() }

    /**
     * 把参数值填进 path 模板，拼成完整订阅地址。
     * 缺失的参数用 placeholder 兜底，保证任何时刻都能给出一个可预览的 URL。
     */
    fun buildUrl(
        route: RssHubRoute,
        values: Map<String, String>,
        host: String = DEFAULT_HOST,
    ): String {
        var path = route.pathTemplate
        route.params.forEach { param ->
            val raw = values[param.key].takeIf { !it.isNullOrBlank() } ?: param.placeholder
            path = path.replace(":${param.key}", encodePathSegment(raw))
        }
        return host.trimEnd('/') + path
    }

    /** 只编码参数值，path 的 `/` 结构必须原样保留。 */
    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
