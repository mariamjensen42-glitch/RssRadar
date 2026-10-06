package com.cycling.rssradar.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.cycling.rssradar.core.model.ListDisplayState

/**
 * 信息流列表显示项（issue #56）：列表与设置页共享同一数据源。
 *
 * 声明放 core:ui —— 它只依赖 core:model 的 [ListDisplayState]，是纯粹的 UI 契约；
 * 而装配（从 ListDisplayStore 读持久化值）依赖 core:data，仍留在 app 的
 * CompositionLocalRoot。两者同文件时会被误判成「整块必须留 app」，
 * 但 feature:feed 要读同一个 Local，留在 app 包就等于 feature 依赖 app，故拆开。
 */
val LocalListDisplay = staticCompositionLocalOf { ListDisplayState() }
