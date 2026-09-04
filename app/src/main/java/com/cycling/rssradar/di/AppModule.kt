package com.cycling.rssradar.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.cycling.rssradar.data.ai.AiRepository
import com.cycling.rssradar.data.ai.DeepSeekClient
import com.cycling.rssradar.data.db.AppDatabase
import com.cycling.rssradar.data.parser.AndroidFetchLogger
import com.cycling.rssradar.data.parser.ContentFetcher
import com.cycling.rssradar.data.parser.FetchConfig
import com.cycling.rssradar.data.parser.FetchLogger
import com.cycling.rssradar.data.FeedRepository
import com.cycling.rssradar.data.OnDemandFetch
import com.cycling.rssradar.data.Recommendation
import com.cycling.rssradar.data.RefreshEngine
import com.cycling.rssradar.data.TransactionRunner
import com.cycling.rssradar.data.store.AiStore
import com.cycling.rssradar.data.store.ArchiveStore
import com.cycling.rssradar.data.store.GroupStore
import com.cycling.rssradar.data.store.LinkStore
import com.cycling.rssradar.data.store.ListDisplayStore
import com.cycling.rssradar.data.store.ReadingPrefsStore
import com.cycling.rssradar.data.store.RecommendationStore
import com.cycling.rssradar.data.store.SettingsPrefs
import com.cycling.rssradar.data.notify.NewArticleSummary
import com.cycling.rssradar.data.notify.NotificationHelper
import com.cycling.rssradar.data.store.NotificationStore
import com.cycling.rssradar.data.store.SyncStore
import com.cycling.rssradar.data.db.MIGRATION_1_2
import com.cycling.rssradar.data.db.MIGRATION_2_3
import com.cycling.rssradar.data.db.MIGRATION_3_4
import com.cycling.rssradar.data.db.MIGRATION_4_5
import com.cycling.rssradar.data.db.MIGRATION_5_6
import com.cycling.rssradar.data.db.MIGRATION_6_7
import com.cycling.rssradar.data.db.MIGRATION_7_8
import com.cycling.rssradar.data.db.MIGRATION_8_9
import com.cycling.rssradar.data.db.MIGRATION_9_10
import com.cycling.rssradar.data.db.MIGRATION_10_11
import com.cycling.rssradar.data.db.MIGRATION_11_12
import com.cycling.rssradar.data.rsshub.RssHubInstanceStore
import com.cycling.rssradar.data.parser.RssParser
import com.cycling.rssradar.data.rss.BestIconFinder
import com.cycling.rssradar.core.domain.rss.HttpFetcher
import com.cycling.rssradar.core.domain.rss.HttpUrlFetcher
import com.cycling.rssradar.data.store.ThemeStore
import com.cycling.rssradar.sync.AutoSync
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton


/**
 * Hilt 依赖图（ADR-0002）：原手写 AppContainer 的 Room DB / 解析器 / 各 Store
 * 全拆成单例 @Provides，单一 DI 真相源。AppDatabase 用 @ApplicationContext 构造，
 * 其余 Store 多数吃 Application。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(app: Application): AppDatabase =
        Room.databaseBuilder(app, AppDatabase::class.java, "rssradar.db")
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
            )
            .build()

    /** 抓取告警出口（ADR-0012）：正文不完整/放弃抓取都会落到 logcat 的 RssRadar/Fetch。 */
    @Provides
    @Singleton
    fun provideFetchLogger(): FetchLogger = AndroidFetchLogger()

    @Provides
    @Singleton
    fun provideFetchConfig(): FetchConfig = FetchConfig()

    @Provides
    @Singleton
    fun provideContentFetcher(
        app: Application,
        config: FetchConfig,
        logger: FetchLogger,
    ): ContentFetcher = ContentFetcher(
        cacheDir = app.cacheDir,
        config = config,
        logger = logger,
    )

    @Provides
    @Singleton
    fun provideRssParser(): RssParser = RssParser()

    @Provides
    @Singleton
    fun provideHttpFetcher(): HttpFetcher = HttpUrlFetcher()

    /** 真 Room 事务；JVM 测试用 DirectTransactionRunner 直跑。 */
    @Provides
    @Singleton
    fun provideTransactionRunner(db: AppDatabase): TransactionRunner =
        RoomTransactionRunner(db)

    @Provides
    @Singleton
    fun provideRefreshEngine(
        db: AppDatabase,
        parser: RssParser,
        http: HttpFetcher,
        transactionRunner: TransactionRunner,
        iconFinder: BestIconFinder,
        externalScope: CoroutineScope,
    ): RefreshEngine = RefreshEngine(
        feedDao = db.feedDao(),
        articleDao = db.articleDao(),
        parser = parser,
        http = http,
        transactionRunner = transactionRunner,
        iconFinder = iconFinder,
        externalScope = externalScope,
    )

    /** 应用级外部作用域：fire-and-forget 任务（站点图标抓取等）不随任何 ViewModel/刷新协程死亡。 */
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideBestIconFinder(): BestIconFinder = BestIconFinder()

    @Provides
    @Singleton
    fun provideFeedRepository(
        db: AppDatabase,
        engine: RefreshEngine,
        http: HttpFetcher,
    ): FeedRepository = FeedRepository(db, engine, http = http)

    /**
     * 按需抓取（ADR-0001 + ADR-0012）：抓取正文与写抓取日志是一个模块的两半，
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

    /** 各 Store 构造只吃 SharedPreferences：Hilt 在此取一次，测试塞内存实例即可。 */
    @Provides
    @Singleton
    fun provideGroupStore(@ApplicationContext context: Context): GroupStore =
        GroupStore(SettingsPrefs.of(context))

    @Provides
    @Singleton
    fun provideRssHubInstanceStore(@ApplicationContext context: Context): RssHubInstanceStore =
        RssHubInstanceStore(SettingsPrefs.of(context))

    @Provides
    @Singleton
    fun provideThemeStore(@ApplicationContext context: Context): ThemeStore =
        ThemeStore(SettingsPrefs.of(context))

    /**
     * 阅读偏好（排版 / 图片 / 渲染器 / 译文显示）合成一个模块：一份 state、一条 provide。
     * 此前四项各是一个 Store，每项都要重复 provides → EntryPoint → CompositionLocal
     * 九点接线；合成后接线只剩一条。
     */
    @Provides
    @Singleton
    fun provideReadingPrefsStore(@ApplicationContext context: Context): ReadingPrefsStore =
        ReadingPrefsStore(SettingsPrefs.of(context))

    @Provides
    @Singleton
    fun provideListDisplayStore(@ApplicationContext context: Context): ListDisplayStore =
        ListDisplayStore(SettingsPrefs.of(context))

    @Provides
    @Singleton
    fun provideArchiveStore(@ApplicationContext context: Context): ArchiveStore =
        ArchiveStore(SettingsPrefs.of(context))

    @Provides
    @Singleton
    fun provideSyncStore(@ApplicationContext context: Context): SyncStore =
        SyncStore(SettingsPrefs.of(context))

    @Provides
    @Singleton
    fun provideAiStore(@ApplicationContext context: Context): AiStore =
        AiStore(SettingsPrefs.of(context))

    /** Key 经 provider 惰性读取，保证 AiStore 里改完 Key 后下一次调用即刻生效。 */
    @Provides
    @Singleton
    fun provideDeepSeekClient(aiStore: AiStore): DeepSeekClient =
        DeepSeekClient(apiKeyProvider = { aiStore.apiKey })

    @Provides
    @Singleton
    fun provideAiRepository(db: AppDatabase, client: DeepSeekClient): AiRepository =
        AiRepository(db.articleDao(), client)

    @Provides
    @Singleton
    fun provideNotificationStore(@ApplicationContext context: Context): NotificationStore =
        NotificationStore(SettingsPrefs.of(context))

    @Provides
    @Singleton
    fun provideLinkStore(@ApplicationContext context: Context): LinkStore =
        LinkStore(SettingsPrefs.of(context))

    /**
     * 新文章通知（#31）：把「查新文章 → 汇总文案 → 发通知」串成一个 suspend 函数注入 AutoSync。
     * 全局开关关 / 没权限时静默不发；Feed 级开关在 SQL 查询里过滤。
     */
    @Provides
    @Singleton
    fun provideNotifyNewArticles(
        @ApplicationContext context: Context,
        notificationStore: NotificationStore,
        feedRepository: FeedRepository,
    ): NotifyNewArticles = NotifyNewArticles { since ->
        if (!notificationStore.state.value) return@NotifyNewArticles
        val articles = feedRepository.newUnreadSince(since, NOTIFY_SAMPLE_LIMIT)
        val summary = NewArticleSummary.build(articles) ?: return@NotifyNewArticles
        NotificationHelper.postNewArticles(context, summary)
    }

    /** 推荐流开关（#推荐，ADR-0013）。 */
    @Provides
    @Singleton
    fun provideRecommendationStore(@ApplicationContext context: Context): RecommendationStore =
        RecommendationStore(SettingsPrefs.of(context))

    /** 推荐流（ADR-0013）：候选池加载 + 打分 + 负反馈的家。 */
    @Provides
    @Singleton
    fun provideRecommendation(database: AppDatabase): Recommendation =
        Recommendation(database)

    @Provides
    @Singleton
    fun provideAutoSync(
        syncStore: SyncStore,
        archiveStore: ArchiveStore,
        feedRepository: FeedRepository,
        notifyNewArticles: NotifyNewArticles,
    ): AutoSync = AutoSync(
        syncStore = syncStore,
        archiveStore = archiveStore,
        refreshAutoSyncFeeds = feedRepository::refreshAutoSyncFeeds,
        archiveExpired = feedRepository::archiveExpired,
        notifyNewArticles = { since -> notifyNewArticles(since) },
    )

    /** 通知里取多少篇来汇总（真实总数由 [NewArticleSummary] 另行统计展示）。 */
    const val NOTIFY_SAMPLE_LIMIT = 6
}

/**
 * 供非 ViewModel 的 Composable（如主题宿主）取 Hilt 单例。
 * Hilt 的 hiltViewModel() 只覆盖 ViewModel 作用域，这里用 EntryPoint 取 ThemeStore。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun themeStore(): ThemeStore
    fun readingPrefsStore(): ReadingPrefsStore
    fun listDisplayStore(): ListDisplayStore
    fun archiveStore(): ArchiveStore
    fun syncStore(): SyncStore
    fun feedRepository(): FeedRepository
    fun autoSync(): AutoSync
    fun applicationScope(): CoroutineScope
}

/** 生产事务 adapter：委托 Room 的 withTransaction。 */
private class RoomTransactionRunner(private val db: AppDatabase) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        db.withTransaction { block() }
}
