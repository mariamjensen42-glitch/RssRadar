package com.cycling.rssradar.di

import android.app.Application
import androidx.room.Room
import com.cycling.rssradar.core.data.db.AppDatabase
import com.cycling.rssradar.core.data.db.FeedAiProfileDao
import com.cycling.rssradar.core.data.db.dao.ArticleDao
import com.cycling.rssradar.core.data.db.dao.FeedDao
import com.cycling.rssradar.core.data.db.migration.MIGRATION_10_11
import com.cycling.rssradar.core.data.db.migration.MIGRATION_11_12
import com.cycling.rssradar.core.data.db.migration.MIGRATION_12_13
import com.cycling.rssradar.core.data.db.migration.MIGRATION_13_14
import com.cycling.rssradar.core.data.db.migration.MIGRATION_14_15
import com.cycling.rssradar.core.data.db.migration.MIGRATION_15_16
import com.cycling.rssradar.core.data.db.migration.MIGRATION_16_17
import com.cycling.rssradar.core.data.db.migration.MIGRATION_1_2
import com.cycling.rssradar.core.data.db.migration.MIGRATION_2_3
import com.cycling.rssradar.core.data.db.migration.MIGRATION_3_4
import com.cycling.rssradar.core.data.db.migration.MIGRATION_4_5
import com.cycling.rssradar.core.data.db.migration.MIGRATION_5_6
import com.cycling.rssradar.core.data.db.migration.MIGRATION_6_7
import com.cycling.rssradar.core.data.db.migration.MIGRATION_7_8
import com.cycling.rssradar.core.data.db.migration.MIGRATION_8_9
import com.cycling.rssradar.core.data.db.migration.MIGRATION_9_10
import com.cycling.rssradar.core.data.refresh.TransactionRunner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(app: Application): AppDatabase =
        Room.databaseBuilder(app, AppDatabase::class.java, "rssradar.db")
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
            )
            .build()

    /** 真 Room 事务；JVM 测试用 DirectTransactionRunner 直跑。 */
    @Provides
    @Singleton
    fun provideTransactionRunner(db: AppDatabase): TransactionRunner =
        RoomTransactionRunner(db)

    // ── AI 智能功能模块（35 项） ────────────────────────────────────────────
    // 装配顺序即依赖顺序：Store → 限流 → 产物 → 队列 → 执行器 → 编排器。
    // 全部单例：限流器与预算必须是全局唯一，否则手动点击会各自记账、绕过日预算。

    /**
     * 订阅源级 AI 配置 DAO。
     *
     * 只有它需要做显式 @Provides：其余 AI 依赖都是我们自己写的类（构造注入或 @Provides 已覆盖），
     * 而 Room 生成的 DAO 只能从 [AppDatabase] 取，Dagger 不会自动认。
     * 注入方是 [com.cycling.rssradar.ui.subscriptions.SubscriptionsViewModel]——
     * 订阅源操作页要读写该源的摘要提示词与自动化开关。
     */
    @Provides
    @Singleton
    fun provideFeedAiProfileDao(db: AppDatabase): FeedAiProfileDao = db.feedAiProfileDao()

    /**
     * 订阅源 DAO。
     *
     * 与 [provideFeedAiProfileDao] 同理：提示词模板管理页（PromptTemplatesViewModel）
     * 要把 profile 的 feedId 翻成源名、并提供全部源供「新增覆盖」选择。
     */
    @Provides
    @Singleton
    fun provideFeedDao(db: AppDatabase): FeedDao = db.feedDao()

    /** 文章 DAO（「我的」页统计条直接读未读计数）。 */
    @Provides
    @Singleton
    fun provideArticleDao(db: AppDatabase): ArticleDao = db.articleDao()
}
