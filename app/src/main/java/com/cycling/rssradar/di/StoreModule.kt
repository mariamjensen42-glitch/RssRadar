package com.cycling.rssradar.di

import android.content.Context
import com.cycling.rssradar.core.data.backup.SettingsSnapshot
import com.cycling.rssradar.core.data.store.prefs.ArchiveStore
import com.cycling.rssradar.core.data.store.prefs.FeedSortStore
import com.cycling.rssradar.core.data.store.prefs.GroupStore
import com.cycling.rssradar.core.data.store.prefs.LanguageStore
import com.cycling.rssradar.core.data.store.prefs.LinkStore
import com.cycling.rssradar.core.data.store.prefs.ListDisplayStore
import com.cycling.rssradar.core.data.store.prefs.NotificationStore
import com.cycling.rssradar.core.data.store.prefs.ReadingPrefsStore
import com.cycling.rssradar.core.data.store.prefs.SettingsPrefs
import com.cycling.rssradar.core.data.store.prefs.SyncStore
import com.cycling.rssradar.core.data.store.prefs.ThemeStore
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StoreModule {
    /** 各 Store 构造只吃 SharedPreferences：Hilt 在此取一次，测试塞内存实例即可。 */
    @Provides
    @Singleton
    fun provideGroupStore(@ApplicationContext context: Context): GroupStore =
        GroupStore(SettingsPrefs.of(context))

    @Provides
    @Singleton
    fun provideThemeStore(@ApplicationContext context: Context): ThemeStore =
        ThemeStore(SettingsPrefs.of(context))

    /** 界面语言偏好（ADR-0017）。 */
    @Provides
    @Singleton
    fun provideLanguageStore(@ApplicationContext context: Context): LanguageStore =
        LanguageStore(SettingsPrefs.of(context))

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

    /** 订阅列表排序偏好（订阅管理页顶栏切换，持久化）。 */
    @Provides
    @Singleton
    fun provideFeedSortStore(@ApplicationContext context: Context): FeedSortStore =
        FeedSortStore(SettingsPrefs.of(context))

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
    fun provideNotificationStore(@ApplicationContext context: Context): NotificationStore =
        NotificationStore(SettingsPrefs.of(context))

    @Provides
    @Singleton
    fun provideSettingsSnapshot(@ApplicationContext context: Context): SettingsSnapshot =
        SettingsSnapshot(context)

    @Provides
    @Singleton
    fun provideLinkStore(@ApplicationContext context: Context): LinkStore =
        LinkStore(SettingsPrefs.of(context))
}
