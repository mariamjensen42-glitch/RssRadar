package com.cycling.rssradar.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cycling.rssradar.core.data.db.dao.ArticleDao
import com.cycling.rssradar.core.data.db.dao.ContentFetchLogDao
import com.cycling.rssradar.core.data.db.dao.FeedDao
import com.cycling.rssradar.core.data.db.dao.RecommendationFeedbackDao
import com.cycling.rssradar.core.data.db.entity.ArchivedArticleTombstoneEntity
import com.cycling.rssradar.core.data.db.entity.ArticleEntity
import com.cycling.rssradar.core.data.db.entity.ContentFetchLogEntity
import com.cycling.rssradar.core.data.db.entity.FeedEntity
import com.cycling.rssradar.core.data.db.entity.RecommendationFeedbackEntity

@Database(
    entities = [
        FeedEntity::class,
        ArticleEntity::class,
        ContentFetchLogEntity::class,
        ArchivedArticleTombstoneEntity::class,
        RecommendationFeedbackEntity::class,
        // AI 智能功能模块（35 项）：产物 / 订阅源级配置 / 任务队列。见 AiSchema.kt。
        AiArtifactEntity::class,
        FeedAiProfileEntity::class,
        AiTaskEntity::class,
        // v17：本地过滤规则 / 正文标注 / 全文检索索引。见 FilterSchema.kt 与 AnnotationSchema.kt。
        FilterRuleEntity::class,
        ArticleAnnotationEntity::class,
        ArticleFtsEntity::class,
    ],
    version = 17,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao
    abstract fun contentFetchLogDao(): ContentFetchLogDao
    abstract fun recommendationFeedbackDao(): RecommendationFeedbackDao
    abstract fun aiArtifactDao(): AiArtifactDao
    abstract fun feedAiProfileDao(): FeedAiProfileDao
    abstract fun aiTaskDao(): AiTaskDao
    abstract fun aiSupportDao(): AiSupportDao
    abstract fun filterRuleDao(): FilterRuleDao
    abstract fun annotationDao(): ArticleAnnotationDao
    abstract fun articleFtsDao(): ArticleFtsDao
}
