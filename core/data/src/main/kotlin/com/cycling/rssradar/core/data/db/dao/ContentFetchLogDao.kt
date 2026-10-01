package com.cycling.rssradar.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cycling.rssradar.core.data.db.entity.ContentFetchLogEntity
import com.cycling.rssradar.core.data.db.projection.FetchHostStat
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentFetchLogDao {
    @Insert
    suspend fun insert(log: ContentFetchLogEntity)

    @Query("SELECT * FROM content_fetch_log ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): kotlinx.coroutines.flow.Flow<List<ContentFetchLogEntity>>

    /** 只看有问题的（失败或不完整），诊断页默认视图。 */
    @Query(
        """
        SELECT * FROM content_fetch_log
        WHERE ok = 0 OR (issue IS NOT NULL AND issue != 'NONE')
        ORDER BY createdAt DESC LIMIT :limit
        """,
    )
    fun observeProblems(limit: Int): kotlinx.coroutines.flow.Flow<List<ContentFetchLogEntity>>

    @Query(
        """
        SELECT host, COUNT(*) AS total,
               SUM(CASE WHEN ok = 0 THEN 1 ELSE 0 END) AS failures,
               SUM(CASE WHEN ok = 1 AND issue IS NOT NULL AND issue != 'NONE' THEN 1 ELSE 0 END) AS incomplete
        FROM content_fetch_log
        GROUP BY host
        ORDER BY (failures + incomplete) DESC, total DESC
        """,
    )
    fun observeHostStats(): kotlinx.coroutines.flow.Flow<List<FetchHostStat>>

    @Query("DELETE FROM content_fetch_log")
    suspend fun clear()

    /** 同一链接的历史：看重试与状态演变。limit 显式传入，不给 DAO 方法加默认参数（Room 代码生成边界情况）。 */
    @Query("SELECT * FROM content_fetch_log WHERE link = :link ORDER BY createdAt DESC LIMIT :limit")
    suspend fun historyOf(link: String, limit: Int): List<ContentFetchLogEntity>
}
