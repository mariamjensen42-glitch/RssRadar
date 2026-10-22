package com.cycling.rssradar.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 品牌化控件配色的事实来源。
 *
 * 为什么收在 core:ui：同一份 [OutlinedTextFieldDefaults.colors] / [SwitchDefaults.colors]
 * 曾在 8 个 feature 包里逐字复制了 20 次，改一次主色要动 20 个文件；更糟的是复制过程中
 * 已经漂移出三种「同源不同值」的变体（surfaceContainer / surfaceContainerLowest 面色、透明描边 / surfaceContainer 描边），
 * 单看某个文件无法判断哪种才是设计稿本意。品牌外观只应有一处可改。
 */

/**
 * 输入框配色：`containerColor` 面色 + accent 聚焦描边与光标。
 *
 * [borderColor] 是**失焦**描边色，默认透明（面与背景同为 surfaceContainer 时无需收边）；
 * 搜索框这类「面与背景同色」的场景传 surfaceContainer 让静止态也能看出边界。
 */
@Composable
fun radarOutlinedTextFieldColors(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    borderColor: Color = Color.Transparent,
): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = containerColor,
    unfocusedContainerColor = containerColor,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = borderColor,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary,
)

/** 开关配色：primary 轨道 + onPrimary 滑块。 */
@Composable
fun radarSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
)
