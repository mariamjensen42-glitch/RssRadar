package com.cycling.rssradar.di

import android.util.Log
import com.cycling.rssradar.core.data.backup.BackupReader
import com.cycling.rssradar.core.data.backup.BackupWriter
import com.cycling.rssradar.core.data.backup.SettingsSnapshot
import com.cycling.rssradar.core.data.db.AppDatabase
import com.cycling.rssradar.core.data.filter.FilterRuleRepository
import com.cycling.rssradar.core.data.parser.RssParser
import com.cycling.rssradar.core.data.refresh.RefreshEngine
import com.cycling.rssradar.core.data.refresh.TransactionRunner
import com.cycling.rssradar.core.data.repository.FeedRepository
import com.cycling.rssradar.core.data.rss.BestIconFinder
import com.cycling.rssradar.core.domain.rss.ConditionalHttpFetcher
import com.cycling.rssradar.core.domain.rss.HttpFetcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppCoreModule {
    @Provides
    @Singleton
    fun provideRefreshEngine(
        db: AppDatabase,
        parser: RssParser,
        http: HttpFetcher,
        transactionRunner: TransactionRunner,
        iconFinder: BestIconFinder,
        externalScope: CoroutineScope,
        conditionalHttp: ConditionalHttpFetcher,
        filterRuleRepository: FilterRuleRepository,
    ): RefreshEngine = RefreshEngine(
        feedDao = db.feedDao(),
        articleDao = db.articleDao(),
        articleFtsDao = db.articleFtsDao(),
        parser = parser,
        http = http,
        transactionRunner = transactionRunner,
        iconFinder = iconFinder,
        externalScope = externalScope,
        conditionalHttp = conditionalHttp,
        // 过滤规则：新文章入库时判定（存量文章在规则保存时由 FeedRepository 重扫）
        filterRules = { filterRuleRepository.engine() },
        // 自愈会静默改写订阅地址：至少落一条日志，否则用户反馈「这个源内容变了」
        // 时查无可查（UI 级提示要等通知模块，见 docs）。
        onHealed = { feedId, oldUrl, newUrl ->
            Log.i("RssRadar", "feed $feedId healed: $oldUrl -> $newUrl")
        },
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
    fun provideBackupWriter(
        database: AppDatabase,
        settingsSnapshot: SettingsSnapshot,
    ): BackupWriter = BackupWriter(database, settingsSnapshot)

    @Provides
    @Singleton
    fun provideBackupReader(
        database: AppDatabase,
        settingsSnapshot: SettingsSnapshot,
    ): BackupReader = BackupReader(database, settingsSnapshot)
}
