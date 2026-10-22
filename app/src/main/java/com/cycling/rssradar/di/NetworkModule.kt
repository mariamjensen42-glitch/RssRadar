package com.cycling.rssradar.di

import android.app.Application
import android.content.Context
import com.cycling.rssradar.core.data.parser.AndroidFetchLogger
import com.cycling.rssradar.core.data.parser.ContentFetcher
import com.cycling.rssradar.core.data.parser.FetchConfig
import com.cycling.rssradar.core.data.parser.FetchLogger
import com.cycling.rssradar.core.data.parser.RssParser
import com.cycling.rssradar.core.data.rsshub.RssHubInstanceStore
import com.cycling.rssradar.core.data.store.prefs.SettingsPrefs
import com.cycling.rssradar.core.data.update.UpdateChecker
import com.cycling.rssradar.core.domain.rss.ConditionalHttpFetcher
import com.cycling.rssradar.core.domain.rss.HttpFetcher
import com.cycling.rssradar.core.domain.rss.HttpUrlFetcher
import com.cycling.rssradar.core.domain.rsshub.HttpHealthzProber
import com.cycling.rssradar.core.domain.rsshub.InstanceProber
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    /** 抓取告警出口：正文不完整/放弃抓取都会落到 logcat 的 RssRadar/Fetch。 */
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

    /** 条件请求（ETag 协商）与普通抓取共用同一个 adapter 实例（超时/UA 配置一致）。 */
    @Provides
    @Singleton
    fun provideConditionalHttpFetcher(): ConditionalHttpFetcher = HttpUrlFetcher()

    /** 检查更新（#35）：复用同一条抓取缝，超时与 UA 与 feed 抓取一致。 */
    @Provides
    @Singleton
    fun provideUpdateChecker(http: HttpFetcher): UpdateChecker = UpdateChecker(http)

    @Provides
    @Singleton
    fun provideInstanceProber(): InstanceProber = HttpHealthzProber()

    @Provides
    @Singleton
    fun provideRssHubInstanceStore(
        @ApplicationContext context: Context,
        prober: InstanceProber,
    ): RssHubInstanceStore = RssHubInstanceStore(SettingsPrefs.of(context), prober)
}
