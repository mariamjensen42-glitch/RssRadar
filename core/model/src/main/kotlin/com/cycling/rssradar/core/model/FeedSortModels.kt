package com.cycling.rssradar.core.model

/**
 * 订阅列表排序方式（订阅管理页）。切换即生效并持久化，重启后保持。
 */
enum class FeedSortMode {
    BY_NAME,
    BY_RECENT,
    BY_UNREAD,
}
