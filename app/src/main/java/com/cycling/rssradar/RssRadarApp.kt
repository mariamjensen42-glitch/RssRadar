package com.cycling.rssradar

import android.app.Application
import androidx.room.Room
import com.cycling.rssradar.data.AppDatabase
import com.cycling.rssradar.data.FeedRepository
import com.cycling.rssradar.data.MIGRATION_1_2
import com.cycling.rssradar.data.MIGRATION_2_3
import com.cycling.rssradar.data.RssParser

/** 轻量手写 DI 容器，后续规模上来再考虑引入 Hilt。 */
class AppContainer(app: Application) {
    val database: AppDatabase = Room.databaseBuilder(
        app,
        AppDatabase::class.java,
        "rssradar.db",
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()

    val repository: FeedRepository = FeedRepository(
        database = database,
        parser = RssParser(),
    )
}

class RssRadarApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
