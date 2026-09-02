package com.cycling.rssradar.data.rsshub

import com.cycling.rssradar.data.db.GROUP_DESIGN
import com.cycling.rssradar.data.db.GROUP_DEV
import com.cycling.rssradar.data.db.GROUP_TECH

/**
 * 路由目录的领域模型。
 *
 * 数据来自 RSSHub 官方路由元数据（docs.rsshub.app/routes.json，3800 条），
 * 由 [RouteCatalogFile] 反序列化后映射而来，见 ADR-0010。
 *
 * 这里是「以 RSSHub 为核心」的地基：选路由 → 填参数 → 拼出完整 URL →
 * 走与普通 RSS 完全相同的校验 / 订阅流程。目录本身可离线浏览（内置快照），
 * 联网只发生在「更新目录」和最后那一步 probe。
 */

/** 参数的可选值（RSSHub 元数据里部分参数给了枚举，如 trending 的时间范围）。 */
data class ParamOption(
    val value: String,
    val label: String,
)

/** 路由里的单个参数，对应 path 模板中的 `:key`。 */
data class RouteParam(
    /** 与 path 模板中 `:key` 的名字一致。 */
    val key: String,
    /** 表单标签：参数说明的首句；没有说明时回落参数名。 */
    val label: String,
    /** 完整说明（可能较长，表单里作为辅助文案）。 */
    val description: String = "",
    /** 可选参数（path 里带 `?`）留空即整段省略。 */
    val optional: Boolean = false,
    /** 正则约束原文，例如 `[0-9]{2}`；无约束时为 null。 */
    val pattern: String? = null,
    val options: List<ParamOption> = emptyList(),
    val defaultValue: String? = null,
) {
    /** 用户没填时的兜底值：默认值 → 首个可选值。为空表示必须由用户填写。 */
    val fallback: String? get() = defaultValue?.takeIf { it.isNotBlank() } ?: options.firstOrNull()?.value
}

/** 可直接订阅的示例：来自官方 example，或实例上真实被订阅的 feed（带标题）。 */
data class RouteExample(
    /** 相对路径，如 `/bilibili/user/video/2267573`。 */
    val path: String,
    /** 示例标题；官方 example 没有标题时为空。 */
    val title: String = "",
)

data class RssHubRoute(
    /** 形如 `/bilibili/user/video/:uid/:embed?`，参数以 `:key`（可带 `?` 与 `{正则}`）占位。 */
    val path: String,
    /** 路由名，如「UP 主投稿」。 */
    val name: String,
    /** 命名空间键，如 `bilibili`。 */
    val namespace: String,
    /** 来源站点中文名，如「哔哩哔哩」。 */
    val sourceName: String,
    /** 来源站点域名，如 `www.bilibili.com`。 */
    val sourceUrl: String,
    /** RSSHub 原始分类键（英文）。 */
    val categories: List<String>,
    /** 热度：RSSHub 统计的订阅量级，用作默认排序与「热门」角标依据。 */
    val heat: Long,
    val description: String = "",
    val params: List<RouteParam> = emptyList(),
    val examples: List<RouteExample> = emptyList(),
) {
    /** 全目录唯一：path 即身份。 */
    val id: String get() = path

    val requiredParams: List<RouteParam> get() = params.filter { !it.optional }

    /** 列表副标题：源名 + 路径，路径是用户核对参数对不对的唯一依据。 */
    val subtitle: String get() = if (sourceName.isBlank()) path else "$sourceName · $path"

    /** 建议分组：按分类粗分，用户可在填参后改。 */
    val suggestedGroup: String get() = RouteCategory.suggestedGroup(categories)

    companion object {
        /** 热度达到这个量级才打「热门」角标。 */
        const val FEATURED_HEAT = 1_000L
    }
}

/**
 * 目录筛选用的分类。键用 RSSHub 原始英文 key，展示才转中文——
 * 避免中文标签与数据里的 key 对不上，也让新增分类只需补一处映射。
 */
object RouteCategory {

    const val ALL = "全部"
    const val POPULAR = "popular"
    const val SOCIAL_MEDIA = "social-media"
    const val NEW_MEDIA = "new-media"
    const val TRADITIONAL_MEDIA = "traditional-media"
    const val PROGRAMMING = "programming"
    const val PROGRAM_UPDATE = "program-update"
    const val MULTIMEDIA = "multimedia"
    const val GAME = "game"
    const val ANIME = "anime"
    const val BBS = "bbs"
    const val READING = "reading"
    const val BLOG = "blog"
    const val STUDY = "study"
    const val FINANCE = "finance"
    const val SHOPPING = "shopping"
    const val JOURNAL = "journal"
    const val PICTURE = "picture"
    const val TRAVEL = "travel"
    const val DESIGN = "design"
    const val UNIVERSITY = "university"
    const val GOVERNMENT = "government"
    const val FORECAST = "forecast"
    const val LIVE = "live"
    const val SPORT = "sport"
    const val OTHER = "other"

    /** 筛选栏顺序：常用在前，长尾在后。 */
    val ORDER: List<String> = listOf(
        ALL,
        POPULAR,
        SOCIAL_MEDIA,
        NEW_MEDIA,
        TRADITIONAL_MEDIA,
        PROGRAMMING,
        PROGRAM_UPDATE,
        MULTIMEDIA,
        GAME,
        ANIME,
        BBS,
        READING,
        BLOG,
        STUDY,
        FINANCE,
        SHOPPING,
        JOURNAL,
        PICTURE,
        TRAVEL,
        DESIGN,
        UNIVERSITY,
        GOVERNMENT,
        FORECAST,
        LIVE,
        SPORT,
        OTHER,
    )

    fun label(key: String): String = when (key) {
        ALL -> ALL
        POPULAR -> "热门"
        SOCIAL_MEDIA -> "社交媒体"
        NEW_MEDIA -> "新媒体"
        TRADITIONAL_MEDIA -> "传统媒体"
        PROGRAMMING -> "编程"
        PROGRAM_UPDATE -> "程序更新"
        MULTIMEDIA -> "视频"
        GAME -> "游戏"
        ANIME -> "动漫"
        BBS -> "论坛"
        READING -> "阅读"
        BLOG -> "博客"
        STUDY -> "学习"
        FINANCE -> "财经"
        SHOPPING -> "购物"
        JOURNAL -> "期刊"
        PICTURE -> "图片"
        TRAVEL -> "出行"
        DESIGN -> "设计"
        UNIVERSITY -> "高校"
        GOVERNMENT -> "政务"
        FORECAST -> "预测"
        LIVE -> "直播"
        SPORT -> "体育"
        OTHER -> "其他"
        else -> key
    }

    /**
     * 建议分组：开发类进「开发」，设计类进「设计」，其余进「科技」。
     * 只是默认值，用户在订阅前可改。
     */
    fun suggestedGroup(categories: List<String>): String = when {
        categories.contains(PROGRAMMING) || categories.contains(PROGRAM_UPDATE) -> GROUP_DEV
        categories.contains(DESIGN) -> GROUP_DESIGN
        else -> GROUP_TECH
    }
}

/** 路由目录的装配与 URL 拼装。 */
object RssHubRoutes {

    /** 官方公共实例；实际使用以 RssHubInstanceStore 的探测 / 自定义结果为准。 */
    const val DEFAULT_HOST = "https://rsshub.app"

    /**
     * 把参数值填进 path 模板，拼成完整订阅地址。
     * 必填参数缺失时返回 null——与手填 URL 不同，路由拼不出来就是拼不出来。
     */
    fun buildUrl(route: RssHubRoute, values: Map<String, String>, host: String): String? =
        RoutePath.build(route.path, values)?.let { host.trimEnd('/') + it }

    /** 填了所有必填参数才能生成预览。 */
    fun canBuild(route: RssHubRoute, values: Map<String, String>): Boolean =
        route.requiredParams.all { values[it.key]?.isNotBlank() == true }
}
