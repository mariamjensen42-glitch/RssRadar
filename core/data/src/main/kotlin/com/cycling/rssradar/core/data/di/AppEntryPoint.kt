package com.cycling.rssradar.core.data.di

import com.cycling.rssradar.core.data.ai.AiBatchProcessor
import com.cycling.rssradar.core.data.repository.FeedRepository
import com.cycling.rssradar.core.data.search.SearchIndexer
import com.cycling.rssradar.core.data.store.prefs.AiBudgetStore
import com.cycling.rssradar.core.data.store.prefs.AiFeatureStore
import com.cycling.rssradar.core.data.store.prefs.ArchiveStore
import com.cycling.rssradar.core.data.store.prefs.LanguageStore
import com.cycling.rssradar.core.data.store.prefs.ListDisplayStore
import com.cycling.rssradar.core.data.store.prefs.ReadingPrefsStore
import com.cycling.rssradar.core.data.store.prefs.SyncStore
import com.cycling.rssradar.core.data.store.prefs.ThemeStore
import com.cycling.rssradar.core.data.sync.AutoSync
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

/**
 * 供非 ViewModel 的 Composable（如主题宿主）取 Hilt 单例。
 * Hilt 的 hiltViewModel() 只覆盖 ViewModel 作用域，这里用 EntryPoint 取 ThemeStore。
 */
@EntryPoint

@InstallIn(SingletonComponent::class)

interface AppEntryPoint {
    fun themeStore(): ThemeStore
    fun languageStore(): LanguageStore
    fun readingPrefsStore(): ReadingPrefsStore
    fun listDisplayStore(): ListDisplayStore
    fun archiveStore(): ArchiveStore
    fun syncStore(): SyncStore
    fun feedRepository(): FeedRepository
    fun autoSync(): AutoSync
    fun applicationScope(): CoroutineScope
    fun aiBatchProcessor(): AiBatchProcessor
    fun aiFeatureStore(): AiFeatureStore
    fun aiBudgetStore(): AiBudgetStore
    fun searchIndexer(): SearchIndexer
}
