package com.cycling.rssradar.ui.settings

/**
 * 设置域的零散工具函数。
 *
 * 原先与通用行组件同在 SettingsComponents.kt；那些组件（SettingsSubPage / SectionHeader /
 * SettingSwitchRow / OptionRow / NavigateRow / SegmentedChips）已下沉到 core.ui.components——
 * 它们被 me / settings / library / player / search 五个包共用，留在本包会让
 * ui.me 与 ui.settings 互相依赖成环。
 */

/** 目录数据时间精确到分钟：更新完能一眼看出「确实换了」。 */
internal fun formatCatalogTimestamp(millis: Long?): String {
    if (millis == null) return "—"
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(millis))
}

/** Android 13（API 33）起通知是运行时权限；低版本由系统默认授予。 */
internal fun needsNotificationPermission(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
