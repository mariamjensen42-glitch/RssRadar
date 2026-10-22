package com.cycling.rssradar.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 推荐负反馈（「减少此类」）：一个订阅源一行，penalty 是推荐流里的降权系数。
 * 只作用于推荐流，不影响常规信息流与订阅本身；撤销即删行。
 */
@Entity(tableName = "recommendation_feedback")
data class RecommendationFeedbackEntity(
    @PrimaryKey val feedId: Long,
    /** 推荐流降权系数 (0,1]，越小越靠后。1 = 不降权。 */
    val penalty: Double,
    val updatedAt: Long,
)
