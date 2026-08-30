package com.cycling.rssradar

import android.app.Application
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
        // 启动副作用收归 Application（原 MainActivity 职责）：自动同步 + 归档清理
        // （issue #57/#58）。顺序规则由 AutoSync 承载，这里只管「应用启动时触发一次」。
        SyncScheduler.onAppStart(this, externalScope)
    }
}
