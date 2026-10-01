package com.cycling.rssradar.core.data.store.model

/**
 * 订阅列表排序方式（订阅管理页）。切换即生效并持久化，重启后保持。
 * label 供排序选择器直接展示，与 [ListDescMode] 同款做法。
 */
enum class FeedSortMode(val label: String) {
    BY_NAME("按名称"),
    BY_RECENT("按最近更新"),
    BY_UNREAD("按未读数"),
}
