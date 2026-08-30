package com.cycling.rssradar.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.cycling.rssradar.data.ai.AiRepository
import com.cycling.rssradar.data.ai.DeepSeekClient
import com.cycling.rssradar.data.db.AppDatabase
import com.cycling.rssradar.data.parser.ContentFetcher
import com.cycling.rssradar.data.FeedRepository
import com.cycling.rssradar.data.store.AiStore
import com.cycling.rssradar.data.store.ArchiveStore
import com.cycling.rssradar.data.store.GroupStore
import com.cycling.rssradar.data.store.ListDisplayStore
import com.cycling.rssradar.data.store.ReadingStyleStore
import com.cycling.rssradar.data.store.SyncStore
import com.cycling.rssradar.data.db.MIGRATION_1_2
import com.cycling.rssradar.data.db.MIGRATION_2_3
import com.cycling.rssradar.data.db.MIGRATION_3_4
import com.cycling.rssradar.data.db.MIGRATION_4_5
import com.cycling.rssradar.data.db.MIGRATION_5_6
import com.cycling.rssradar.data.rsshub.RssHubInstanceStore
import com.cycling.rssradar.data.parser.RssParser
import com.cycling.rssradar.data.rss.BestIconFinder
import com.cycling.rssradar.data.store.ThemeStore
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
        parser: RssParser,
        contentFetcher: ContentFetcher,
        iconFinder: BestIconFinder,
        externalScope: CoroutineScope,
    ): FeedRepository = FeedRepository(
        db,
        parser,
        contentFetcher = contentFetcher,
        iconFinder = iconFinder,
        externalScope = externalScope,
    )

    @Provides
    @Singleton
    fun provideGroupStore(@ApplicationContext context: Context): GroupStore = GroupStore(context)

    @Provides
    @Singleton
    fun provideRssHubInstanceStore(app: Application): RssHubInstanceStore = RssHubInstanceStore(app)

    @Provides
    @Singleton
    fun provideThemeStore(app: Application): ThemeStore = ThemeStore(app)

    @Provides
    @Singleton
    fun provideReadingStyleStore(@ApplicationContext context: Context): ReadingStyleStore =
        ReadingStyleStore(context)

    @Provides
    @Singleton
    fun provideListDisplayStore(@ApplicationContext context: Context): ListDisplayStore =
        ListDisplayStore(context)

    @Provides
    @Singleton
    fun provideArchiveStore(@ApplicationContext context: Context): ArchiveStore =
        ArchiveStore(context)

    @Provides
    @Singleton
    fun provideSyncStore(@ApplicationContext context: Context): SyncStore =
        SyncStore(context)

    @Provides
    @Singleton
    fun provideAiStore(app: Application): AiStore = AiStore(app)

    /** Key 经 provider 惰性读取，保证 AiStore 里改完 Key 后下一次调用即刻生效。 */
    @Provides
    @Singleton
    fun provideDeepSeekClient(aiStore: AiStore): DeepSeekClient =
        DeepSeekClient(apiKeyProvider = { aiStore.apiKey })

    @Provides
    @Singleton
    fun provideAiRepository(db: AppDatabase, client: DeepSeekClient): AiRepository =
        AiRepository(db.articleDao(), client)
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
    fun applicationScope(): CoroutineScope
}
