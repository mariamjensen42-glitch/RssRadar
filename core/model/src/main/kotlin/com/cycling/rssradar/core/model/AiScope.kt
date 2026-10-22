package com.cycling.rssradar.core.model

/** 产物挂在什么主体上，决定 ai_artifacts 的 subjectKind 与孤儿清理方式。 */
enum class AiScope(val dbValue: Int, val label: String) {
    /** 文章级：随文章一起归档清理。 */
    ARTICLE(dbValue = 0, label = "文章"),

    /** 订阅源级：随订阅源删除而清理。 */
    FEED(dbValue = 1, label = "订阅源"),

    /** 全局级：不属于任何单篇文章或订阅源（源推荐、过滤规则、用量看板等）。 */
    GLOBAL(dbValue = 2, label = "全局"),
    ;

    companion object {
        fun fromDbValue(value: Int): AiScope? = entries.firstOrNull { it.dbValue == value }
    }
}
