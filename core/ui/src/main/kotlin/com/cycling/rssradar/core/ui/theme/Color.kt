package com.cycling.rssradar.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/** 语义色：成功 / 警告 / 危险。M3 色板没有这三档，固定值，不随主题切换。 */
val Success = Color(0xFF34D399)
val Warning = Color(0xFFFBBF24)
val Danger = Color(0xFFEF4444)

/**
 * 背景是否偏亮：系统栏图标该用深色还是浅色，按底色算而不是按「是不是深色模式」——
 * 阅读主题可以让深色模式下出现米黄纸（#16），此时状态栏图标必须跟着翻。
 */
fun ColorScheme.isLightBackground(): Boolean = relativeLuminance(surface) >= 0.5f
