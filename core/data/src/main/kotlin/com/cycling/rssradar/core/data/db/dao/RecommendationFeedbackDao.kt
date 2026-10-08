package com.cycling.rssradar.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cycling.rssradar.core.data.db.entity.RecommendationFeedbackEntity

@Dao
interface RecommendationFeedbackDao {
    @Query("SELECT * FROM recommendation_feedback")
    suspend fun getAll(): List<RecommendationFeedbackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecommendationFeedbackEntity)

    /** 撤销「减少此类」：整行删除，降权归零。 */
    @Query("DELETE FROM recommendation_feedback WHERE feedId = :feedId")
    suspend fun clear(feedId: Long)
}
