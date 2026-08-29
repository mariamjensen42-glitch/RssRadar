package com.cycling.rssradar.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.cycling.rssradar.data.AppDatabase
import com.cycling.rssradar.data.ContentFetcher
import com.cycling.rssradar.data.FeedRepository
import com.cycling.rssradar.data.GroupStore
import com.cycling.rssradar.data.MIGRATION_1_2
import com.cycling.rssradar.data.MIGRATION_2_3
import com.cycling.rssradar.data.MIGRATION_3_4
import com.cycling.rssradar.data.RssHubInstanceStore
import com.cycling.rssradar.data.RssParser
import com.cycling.rssradar.data.ThemeStore
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
    fun provideApplication(@ApplicationContext context: Context): Application =
        context.applicationContext as Application

    @Provides
    @Singleton
    fun provideAppDatabase(app: Application): AppDatabase =
        Room.databaseBuilder(app, AppDatabase::class.java, "rssradar.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
    fun provideFeedRepository(
        db: AppDatabase,
        parser: RssParser,
        contentFetcher: ContentFetcher,
    ): FeedRepository = FeedRepository(db, parser, contentFetcher = contentFetcher)

    @Provides
    @Singleton
    fun provideGroupStore(@ApplicationContext context: Context): GroupStore = GroupStore(context)

    @Provides
    @Singleton
    fun provideRssHubInstanceStore(app: Application): RssHubInstanceStore = RssHubInstanceStore(app)

    @Provides
    @Singleton
    fun provideThemeStore(app: Application): ThemeStore = ThemeStore(app)
}

/**
 * 供非 ViewModel 的 Composable（如主题宿主）取 Hilt 单例。
 * Hilt 的 hiltViewModel() 只覆盖 ViewModel 作用域，这里用 EntryPoint 取 ThemeStore。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun themeStore(): ThemeStore
}
