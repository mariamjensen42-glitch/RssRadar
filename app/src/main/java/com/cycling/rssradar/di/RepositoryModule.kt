package com.cycling.rssradar.di

import android.content.Context
import com.cycling.rssradar.core.data.annotation.AnnotationRepository
import com.cycling.rssradar.core.data.db.AppDatabase
import com.cycling.rssradar.core.data.db.FeedAiProfileDao
import com.cycling.rssradar.core.data.db.dao.ArticleDao
import com.cycling.rssradar.core.data.db.dao.FeedDao
import com.cycling.rssradar.core.data.filter.FilterRuleRepository
import com.cycling.rssradar.core.data.parser.ContentFetcher
import com.cycling.rssradar.core.data.parser.FetchLogger
import com.cycling.rssradar.core.data.recommend.Recommendation
import com.cycling.rssradar.core.data.refresh.RefreshEngine
import com.cycling.rssradar.core.data.refresh.TransactionRunner
import com.cycling.rssradar.core.data.repository.FeedPromptOverrideRepository
import com.cycling.rssradar.core.data.repository.FeedRepository
import com.cycling.rssradar.core.data.repository.ReadingStatsRepository
import com.cycling.rssradar.core.data.search.SearchIndexer
import com.cycling.rssradar.core.data.service.OnDemandFetch
import com.cycling.rssradar.core.data.service.SubscriptionFlow
import com.cycling.rssradar.core.data.store.prefs.LibraryStore
import com.cycling.rssradar.core.data.store.prefs.RecommendationStore
import com.cycling.rssradar.core.data.store.prefs.SettingsPrefs
import com.cycling.rssradar.core.domain.rss.HttpFetcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideFeedRepository(
        db: AppDatabase,
        engine: RefreshEngine,
        transactionRunner: TransactionRunner,
    ): FeedRepository = FeedRepository(db, engine, transactionRunner)

    /** 阅读统计取数：UI 侧不再直连 ArticleDao。 */
    @Provides
    @Singleton
    fun provideReadingStatsRepository(articleDao: ArticleDao): ReadingStatsRepository =
        ReadingStatsRepository(articleDao)

    /** 订阅源级提示词覆盖：UI 侧不再直连 FeedAiProfileDao / FeedDao。 */
    @Provides
    @Singleton
    fun provideFeedPromptOverrideRepository(
        profileDao: FeedAiProfileDao,
        feedDao: FeedDao,
    ): FeedPromptOverrideRepository = FeedPromptOverrideRepository(profileDao, feedDao)

    /** 订阅链路（发现/预览/落库/OPML）：AddSubscription、FeedList、Subscriptions 三个 VM 直连。 */
    @Provides
    @Singleton
    fun provideSubscriptionFlow(
        db: AppDatabase,
        engine: RefreshEngine,
        http: HttpFetcher,
    ): SubscriptionFlow = SubscriptionFlow(db, engine, http = http)

    /**
     * 按需抓取：抓取正文与写抓取日志是一个模块的两半，
     * 诊断页与详情页都直连它，不经过 FeedRepository 转发。
     */
    @Provides
    @Singleton
    fun provideOnDemandFetch(
        db: AppDatabase,
        contentFetcher: ContentFetcher,
        logger: FetchLogger,
    ): OnDemandFetch = OnDemandFetch(
        articleDao = db.articleDao(),
        feedDao = db.feedDao(),
        contentFetchLogDao = db.contentFetchLogDao(),
        fetchOutcome = { link -> contentFetcher.fetch(link) },
        logger = logger,
    )

    @Provides
    @Singleton
    fun provideLibraryStore(@ApplicationContext context: Context): LibraryStore =
        LibraryStore(SettingsPrefs.of(context))

    @Provides
    @Singleton
    fun provideFilterRuleRepository(database: AppDatabase): FilterRuleRepository =
        FilterRuleRepository(database.filterRuleDao())

    @Provides
    @Singleton
    fun provideAnnotationRepository(database: AppDatabase): AnnotationRepository =
        AnnotationRepository(database.annotationDao())

    @Provides
    @Singleton
    fun provideSearchIndexer(database: AppDatabase): SearchIndexer = SearchIndexer(database)

    /** 推荐流开关（#推荐）。 */
    @Provides
    @Singleton
    fun provideRecommendationStore(@ApplicationContext context: Context): RecommendationStore =
        RecommendationStore(SettingsPrefs.of(context))

    /** 推荐流：候选池加载 + 打分 + 负反馈的家。 */
    @Provides
    @Singleton
    fun provideRecommendation(database: AppDatabase): Recommendation =
        Recommendation(database)
}
