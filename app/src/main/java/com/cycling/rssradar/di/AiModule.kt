package com.cycling.rssradar.di

import android.content.Context
import com.cycling.rssradar.core.data.ai.AiArtifactRepository
import com.cycling.rssradar.core.data.ai.AiBatchProcessor
import com.cycling.rssradar.core.data.ai.AiFeatureRunner
import com.cycling.rssradar.core.data.ai.AiFilterRuleDrafter
import com.cycling.rssradar.core.data.ai.AiRateLimiter
import com.cycling.rssradar.core.data.ai.AiRepository
import com.cycling.rssradar.core.data.ai.AiTaskQueue
import com.cycling.rssradar.core.data.ai.DeepSeekClient
import com.cycling.rssradar.core.data.db.AppDatabase
import com.cycling.rssradar.core.data.store.prefs.AiBudgetStore
import com.cycling.rssradar.core.data.store.prefs.AiFeatureStore
import com.cycling.rssradar.core.data.store.prefs.AiStore
import com.cycling.rssradar.core.data.store.prefs.SettingsPrefs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides
    @Singleton
    fun provideAiStore(@ApplicationContext context: Context): AiStore {
        // API Key 存独立的 secrets 文件（已排除出云备份）；装配点顺带把老位置的值搬过来。
        val secrets = SettingsPrefs.ofSecret(context)
        AiStore.migrateFromLegacy(SettingsPrefs.of(context), secrets)
        return AiStore(secrets)
    }

    /** Key 经 provider 惰性读取，保证 AiStore 里改完 Key 后下一次调用即刻生效。 */
    @Provides
    @Singleton
    fun provideDeepSeekClient(aiStore: AiStore): DeepSeekClient =
        DeepSeekClient(apiKeyProvider = { aiStore.apiKey })

    @Provides
    @Singleton
    fun provideAiRepository(
        db: AppDatabase,
        client: DeepSeekClient,
        limiter: AiRateLimiter,
        featureStore: AiFeatureStore,
    ): AiRepository = AiRepository(db.articleDao(), client, limiter, featureStore)

    /** 16 项功能的独立开关。 */
    @Provides
    @Singleton
    fun provideAiFeatureStore(@ApplicationContext context: Context): AiFeatureStore =
        AiFeatureStore(SettingsPrefs.of(context))

    /** 日预算与用量统计。 */
    @Provides
    @Singleton
    fun provideAiBudgetStore(@ApplicationContext context: Context): AiBudgetStore =
        AiBudgetStore(SettingsPrefs.of(context))

    @Provides
    @Singleton
    fun provideAiRateLimiter(budgetStore: AiBudgetStore): AiRateLimiter =
        AiRateLimiter(budgetStore)

    /**
     * 产物门面。
     *
     * 除了产物 DAO 还带上订阅源与文章 DAO：产物中心要把 subjectId 翻成人能读的标题，
     * 而数据库里只有一串 id。标题解析放在仓储层批量做，UI 侧不碰 DAO。
     */
    @Provides
    @Singleton
    fun provideAiArtifactRepository(db: AppDatabase): AiArtifactRepository =
        AiArtifactRepository(
            dao = db.aiArtifactDao(),
            feedDao = db.feedDao(),
            supportDao = db.aiSupportDao(),
        )

    @Provides
    @Singleton
    fun provideAiTaskQueue(db: AppDatabase): AiTaskQueue =
        AiTaskQueue(db.aiTaskDao())

    @Provides
    @Singleton
    fun provideAiFeatureRunner(
        db: AppDatabase,
        client: DeepSeekClient,
        artifacts: AiArtifactRepository,
        limiter: AiRateLimiter,
        featureStore: AiFeatureStore,
    ): AiFeatureRunner = AiFeatureRunner(
        client = client,
        articleDao = db.articleDao(),
        feedDao = db.feedDao(),
        supportDao = db.aiSupportDao(),
        profileDao = db.feedAiProfileDao(),
        artifacts = artifacts,
        limiter = limiter,
        featureStore = featureStore,
    )

    /**
     * 过滤规则提案。
     *
     * 与批处理器分开提供一个入口，是因为它**不进队列**：用户在界面上等着结果，
     * 走排程只会多一次「入队→消化」的往返与其间的状态不确定性。
     */
    @Provides
    @Singleton
    fun provideAiFilterRuleDrafter(
        db: AppDatabase,
        runner: AiFeatureRunner,
    ): AiFilterRuleDrafter = AiFilterRuleDrafter(
        runner = runner,
        supportDao = db.aiSupportDao(),
        feedDao = db.feedDao(),
        articleDao = db.articleDao(),
    )

    @Provides
    @Singleton
    fun provideAiBatchProcessor(
        db: AppDatabase,
        queue: AiTaskQueue,
        runner: AiFeatureRunner,
        artifacts: AiArtifactRepository,
        budget: AiBudgetStore,
        featureStore: AiFeatureStore,
    ): AiBatchProcessor = AiBatchProcessor(
        queue = queue,
        runner = runner,
        supportDao = db.aiSupportDao(),
        profileDao = db.feedAiProfileDao(),
        feedDao = db.feedDao(),
        artifacts = artifacts,
        budget = budget,
        featureStore = featureStore,
    )
}
