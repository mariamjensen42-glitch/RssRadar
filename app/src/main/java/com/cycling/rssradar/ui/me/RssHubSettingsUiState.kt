package com.cycling.rssradar.ui.me

import com.cycling.rssradar.core.data.store.model.AppLanguage
import com.cycling.rssradar.core.data.store.model.KeepArchived
import com.cycling.rssradar.core.data.store.model.LinkShareState
import com.cycling.rssradar.core.data.store.model.ListDisplayState
import com.cycling.rssradar.core.data.store.model.SyncState
import com.cycling.rssradar.core.data.store.model.ThemeMode
import com.cycling.rssradar.core.model.rsshub.CatalogSource
import com.cycling.rssradar.i18n.UiText

data class RssHubSettingsUiState(
    /** 当前生效的实例。 */
    val activeHost: String = "",
    /** 用户自定义实例输入。 */
    val customInput: String = "",
    val probing: Boolean = false,
    /** 最近一次探测的提示文案；null 表示没有要展示的提示。 */
    val probeMessage: UiText? = null,
    /** 当前主题模式。 */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    /** Material You 动态取色（#27）：开则强调色跟随系统壁纸，表面阶梯不变。 */
    val dynamicColor: Boolean = false,
    /** 自定义强调色 ARGB（#29）；null = 默认紫。与动态取色互斥。 */
    val customAccent: Long? = null,
    /** DeepSeek API Key 输入（issue #44）。 */
    val aiKeyInput: String = "",
    /** 是否已配置 Key（用于状态展示，不回显完整 Key）。 */
    val aiKeyConfigured: Boolean = false,
    /** AI Key 保存的提示文案。 */
    val aiMessage: UiText? = null,
    /** 信息流列表显示项（issue #56）。 */
    val listDisplay: ListDisplayState = ListDisplayState(),
    /** 归档保留档位（issue #57）。 */
    val keepArchived: KeepArchived = KeepArchived.ALWAYS,
    /** 自动同步状态（issue #58）。 */
    val sync: SyncState = SyncState(),
    /** 外链打开方式与分享格式（#26）。 */
    val linkShare: LinkShareState = LinkShareState(),
    /** 推荐流开关（ADR-0013）。 */
    val recommendationEnabled: Boolean = true,
    /** 新文章通知总开关（#31）。 */
    val notifyEnabled: Boolean = false,
    /** 系统通知权限是否已授予（Android 13+）；true = 低版本无需权限。 */
    val notifyPermissionGranted: Boolean = true,
    /** 通知设置的提示文案（权限被拒时说明原因）。 */
    val notifyMessage: UiText? = null,
    /** 路由目录（issue #59）：条数 / 数据时间 / 来源。 */
    val catalogRouteCount: Int = 0,
    val catalogGeneratedAt: Long? = null,
    val catalogSource: CatalogSource = CatalogSource.BUILTIN,
    val catalogRefreshing: Boolean = false,
    /** 目录更新结果的提示文案。 */
    val catalogMessage: UiText? = null,
    /** 「我的」页统计条：订阅源数 / 未读文章数（全部来自 DB 真实计数，禁止编造）。 */
    val feedCount: Int = 0,
    val unreadCount: Int = 0,
)
