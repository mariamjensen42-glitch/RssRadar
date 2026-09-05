package com.cycling.rssradar

import android.app.Application
import com.cycling.rssradar.core.data.CrashLog
import com.cycling.rssradar.sync.FeedHealthScheduler
import com.cycling.rssradar.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@HiltAndroidApp
class RssRadarApp : Application() {

    @Inject
    lateinit var externalScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // 崩溃兜底（issue #61）：必须最先装，之后任何启动期崩溃都有记录。
        // release 构建才出现的问题（R8 断裂等）在用户手上是静默的，这是唯一证据源。
        CrashLog.install(this)
        // 启动副作用收归 Application（原 MainActivity 职责）：自动同步 + 归档清理
        // （issue #57/#58）。顺序规则由 AutoSync 承载，这里只管「应用启动时触发一次」。
        SyncScheduler.onAppStart(this, externalScope)
        // 失效源每日复探（#82）：固定每日一次，无需开关——
        // 没有伤员时 Worker 查一次库即返回，成本可忽略
        FeedHealthScheduler.schedule(this)
    }
}
