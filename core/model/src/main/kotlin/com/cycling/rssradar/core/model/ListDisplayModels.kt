package com.cycling.rssradar.core.model

/**
 * 列表描述档位（issue #56）。NONE 隐藏摘要，SHORT 两行，LONG 四行。
 * label 供设置页直接展示，与 ReadingFontFamily 同款做法。
 */
enum class ListDescMode(val lines: Int, val label: String) {
    NONE(0, "关"),
    SHORT(2, "短"),
    LONG(4, "长"),
}

/**
 * 文章列表视图模式（信息流与订阅源文章列表通用，全局生效）。
 * label 供视图切换器直接展示。CARD 是历史默认渲染，升级无感知。
 */
enum class ListViewMode(val label: String) {
    /** 单列紧凑：无缩略图，标题 + 摘要。 */
    LIST("列表"),
    /** 卡片：现有默认渲染（缩略图跟随「缩略图」开关）。 */
    CARD("卡片"),
    /** 杂志：图文混排，首篇大图突出。 */
    MAGAZINE("杂志"),
    /** 网格：多列自适应（按可用宽度自动定列数），手机两列、平板/横屏更多。 */
    GRID("网格"),
}

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
)
