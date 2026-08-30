package com.cycling.rssradar.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.cycling.rssradar.data.ai.AiRepository
import com.cycling.rssradar.data.ai.DeepSeekClient
import com.cycling.rssradar.data.db.AppDatabase
import com.cycling.rssradar.data.parser.ContentFetcher
import com.cycling.rssradar.data.FeedRepository
import com.cycling.rssradar.data.RefreshEngine
import com.cycling.rssradar.data.TransactionRunner
import com.cycling.rssradar.data.store.AiStore
import com.cycling.rssradar.data.store.ArchiveStore
import com.cycling.rssradar.data.store.GroupStore
import com.cycling.rssradar.data.store.ListDisplayStore
import com.cycling.rssradar.data.store.ReadingStyleStore
import com.cycling.rssradar.data.store.SettingsPrefs
import com.cycling.rssradar.data.store.SyncStore
import com.cycling.rssradar.data.db.MIGRATION_1_2
import com.cycling.rssradar.data.db.MIGRATION_2_3
import com.cycling.rssradar.data.db.MIGRATION_3_4
import com.cycling.rssradar.data.db.MIGRATION_4_5
import com.cycling.rssradar.data.db.MIGRATION_5_6
import com.cycling.rssradar.data.rsshub.RssHubInstanceStore
import com.cycling.rssradar.data.parser.RssParser
import com.cycling.rssradar.data.rss.BestIconFinder
import com.cycling.rssradar.data.rss.HttpFetcher
import com.cycling.rssradar.data.rss.HttpUrlFetcher
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides
    @Singleton
    fun provideContentFetcher(app: Application): ContentFetcher =
        ContentFetcher(cacheDir = app.cacheDir)

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
        contentFetcher: ContentFetcher,
    ): FeedRepository = FeedRepository(
        db,
        engine,
        contentFetcher = contentFetcher,
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

    @Provides
    @Singleton
    fun provideReadingStyleStore(@ApplicationContext context: Context): ReadingStyleStore =
        ReadingStyleStore(SettingsPrefs.of(context))

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
    fun provideAutoSync(
        syncStore: SyncStore,
        archiveStore: ArchiveStore,
        feedRepository: FeedRepository,
    ): AutoSync = AutoSync(
        syncStore = syncStore,
        archiveStore = archiveStore,
        refreshAutoSyncFeeds = feedRepository::refreshAutoSyncFeeds,
        archiveExpired = feedRepository::archiveExpired,
    )
}

/**
 * 供非 ViewModel 的 Composable（如主题宿主）取 Hilt 单例。
 * Hilt 的 hiltViewModel() 只覆盖 ViewModel 作用域，这里用 EntryPoint 取 ThemeStore。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun themeStore(): ThemeStore
    fun readingStyleStore(): ReadingStyleStore
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
        androidx.room.withTransaction(db) { block() }
}
