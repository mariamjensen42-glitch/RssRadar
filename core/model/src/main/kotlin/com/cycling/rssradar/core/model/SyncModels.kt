package com.cycling.rssradar.core.model

/**
 * 自动同步间隔档位（issue #58）。minutes = 周期分钟数，0 表示仅手动。
 * 纯 JVM 枚举，可被单测。
 * 15/30 分钟档明确不做：1000+ 源一轮刷新可能超半小时，短档位是虚假承诺。
 */
enum class SyncInterval(val minutes: Long) {
    MANUALLY(0),
    EVERY_1_HOUR(60),
    EVERY_3_HOURS(180),
    EVERY_6_HOURS(360),
    EVERY_12_HOURS(720),
    EVERY_1_DAY(1440),
}

/** 自动同步状态（issue #58）。默认值 = 引入前行为：全手动，升级无感知。 */
data class SyncState(
    val interval: SyncInterval = SyncInterval.MANUALLY,
    /** 仅在不计费网络（WiFi）下自动同步。1000+ 源一轮流量可观，默认开。 */
    val onlyOnWifi: Boolean = true,
    /** 仅充电时自动同步，默认关。 */
    val onlyWhenCharging: Boolean = false,
    /** 打开应用时自动同步一次（带去抖），默认开。 */
    val syncOnStart: Boolean = true,
    /** 上次自动同步的时间戳，启动同步去抖用。 */
    val lastAutoSyncAt: Long = 0L,
)
