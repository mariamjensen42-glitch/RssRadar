package com.cycling.rssradar.core.model

/**
 * 列表描述档位（issue #56）。NONE 隐藏摘要，SHORT 两行，LONG 四行。
 */
enum class ListDescMode(val lines: Int) {
    NONE(0),
    SHORT(2),
    LONG(4),
}

/**
 * 文章列表视图模式（信息流与订阅源文章列表通用，全局生效）。
 * CARD 是历史默认渲染，升级无感知。
 */
enum class ListViewMode {
    /** 单列紧凑：无缩略图，标题 + 摘要。 */
    LIST,
    /** 卡片：现有默认渲染（缩略图跟随「缩略图」开关）。 */
    CARD,
    /** 杂志：图文混排，首篇大图突出。 */
    MAGAZINE,
    /** 网格：多列自适应（按可用宽度自动定列数），手机两列、平板/横屏更多。 */
    GRID,
}

/**
 * 列表「AI 价值」档位（排序与筛选共用一个开关）。
 *
 * 刻意做成三档而不是「排序」「隐藏低价值」两个独立布尔：两者共用同一条带 JOIN
 * 与阈值谓词的查询，拆成两个布尔就有 4 种组合、查询族得跟着翻四倍
 * （DAO 里已经是 8 条加载 + 5 条计数）。三档是这两个需求的最少表达。
 */
enum class FeedValueMode {
    /** 默认：时间倒序。不 JOIN 产物表，照走 index_articles_publishedAt_fetchedAt。 */
    OFF,

    /** 按 AI 的信息价值排序。未评估的（没有分数）沉底，不隐藏任何东西。 */
    BY_VALUE,

    /** 按价值排序并隐藏低于 [MIN_GOOD_VALUE] 的。未评估的保留——它们只是"还没判"。 */
    ONLY_GOOD,
    ;

    /** 是否需要产物表参与（即是否走 value 查询族）。 */
    val usesAiValue: Boolean get() = this != OFF

    /** 该档位的筛选阈值；null = 不过滤。 */
    val minValue: Int? get() = if (this == ONLY_GOOD) MIN_GOOD_VALUE else null
}

/**
 * 「只看值得读」的阈值（0~100，两类分数同量纲）。
 *
 * 取 40：那是"信息价值/质量总分"两档描述里的明确低分区，低于它的内容在
 * ReadYou 一类产品里通常被归为水文/广告。写死一个常数是刻意的——
 * 让用户调一个 0~100 的滑杆，绝大多数人只会把它拖到两端，等于白给一个决策负担。
 */
const val MIN_GOOD_VALUE: Int = 40

/**
 * 信息流列表显示项状态（issue #56）。纯数据类。
 * 默认值 = 功能引入前的固定渲染，升级无感知。
 */
data class ListDisplayState(
    val showFeedIcon: Boolean = true,
    val showFeedName: Boolean = true,
    val showDate: Boolean = true,
    val showThumbnail: Boolean = true,
    val descMode: ListDescMode = ListDescMode.SHORT,
    val stickyDateHeader: Boolean = true,
    val dimRead: Boolean = false,
    /**
     * 滚动时自动标记已读（#11）：卡片滚出视口顶部即标记为已读。
     * 默认关——这是会改变用户数据的行为，必须显式选择。
     */
    val markReadOnScroll: Boolean = false,
    /** 列表视图模式（列表/卡片/杂志/网格），默认卡片。 */
    val viewMode: ListViewMode = ListViewMode.CARD,
    /**
     * AI 价值档位（时间倒序 / 按信息价值 / 只看值得读），默认时间倒序。
     *
     * 非 OFF 时列表改走另一条 SQL（带产物表 JOIN 与阈值谓词），**默认那条一行不改**——
     * ORDER BY 里出现表达式会让 index_articles_publishedAt_fetchedAt 失效。
     */
    val valueMode: FeedValueMode = FeedValueMode.OFF,
)
