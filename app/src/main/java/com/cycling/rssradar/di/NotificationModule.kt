package com.cycling.rssradar.di

import android.content.Context
import com.cycling.rssradar.core.data.filter.FilterRuleRepository
import com.cycling.rssradar.core.data.notify.NewArticleSummary
import com.cycling.rssradar.core.data.notify.NotificationHelper
import com.cycling.rssradar.core.data.repository.FeedRepository
import com.cycling.rssradar.core.data.store.prefs.ArchiveStore
import com.cycling.rssradar.core.data.store.prefs.NotificationStore
import com.cycling.rssradar.core.data.store.prefs.SyncStore
import com.cycling.rssradar.core.domain.filter.RuleTarget
import com.cycling.rssradar.core.domain.notify.DndWindow
import com.cycling.rssradar.core.domain.notify.NotifyDecision
import com.cycling.rssradar.sync.AutoSync
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {
    /**
     * 新文章通知（#31）：查新文章 → 勿扰 / 关键词 / 过滤规则判定 → 汇总文案 → 发通知。
     * 判定链统一走 NotifyDecision，避免"设置页一套、发送侧另一套"；Feed 级开关在 SQL 里过滤。
     */
    @Provides
    @Singleton
    fun provideNotifyNewArticles(
        @ApplicationContext context: Context,
        notificationStore: NotificationStore,
        feedRepository: FeedRepository,
        filterRuleRepository: FilterRuleRepository,
    ): NotifyNewArticles = NotifyNewArticles { since ->
        val prefs = notificationStore.state.value
        if (!prefs.enabled) return@NotifyNewArticles
        val articles = feedRepository.newUnreadSince(since, NOTIFY_SAMPLE_LIMIT)
        val now = System.currentTimeMillis()
        val nowMinute = DndWindow.minuteOfDay(now, java.util.TimeZone.getDefault().getOffset(now))
        val engine = filterRuleRepository.engine()
        val kept = articles.filter { item ->
            NotifyDecision.shouldNotify(
                prefs = prefs,
                feedEnabled = true,
                title = item.article.title,
                summary = item.article.summary.orEmpty(),
                nowMinute = nowMinute,
                suppressedByRule = engine.suppressesNotify(
                    RuleTarget(
                        feedId = item.article.feedId,
                        groupName = item.feedGroup,
                        title = item.article.title,
                        summary = item.article.summary.orEmpty(),
                    ),
                ),
            )
        }
        val summary = NewArticleSummary.build(kept) ?: return@NotifyNewArticles
        NotificationHelper.postNewArticles(context, summary)
    }

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
