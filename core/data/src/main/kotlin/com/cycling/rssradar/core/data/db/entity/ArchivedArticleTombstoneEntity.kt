package com.cycling.rssradar.core.data.db.entity

import androidx.room.Entity

/**
 * 归档/清空墓碑（issue「归档后刷新文章复活」）：真删的文章在这里留一笔 (feedId, link)，
 * 刷新 upsert 见到墓碑就跳过——否则 feed XML 里还挂着的旧条目会被当成新文章插回来，
 * 用户看到「删了又回来」。单篇删除走撤销机制（restore），不在此列。
 *
 * 墓碑按 archivedAt 滚动清理（90 天）：feed 一般只挂最近几十条，更早的 link 不会再被重发。
 */
@Entity(
    tableName = "archived_article_tombstones",
    primaryKeys = ["feedId", "link"],
)
data class ArchivedArticleTombstoneEntity(
    val feedId: Long,
    val link: String,
    val archivedAt: Long,
)
