package com.cycling.rssradar.core.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

private const val CN_HUNDRED_MILLION = 100_000_000.0
private const val CN_TEN_THOUSAND = 10_000.0
private const val EN_MILLION = 1_000_000.0
private const val EN_THOUSAND = 1_000.0

/**
 * 计数缩写：中文 万/亿，英文 K/M，口径对齐（10^4 / 10^8 ↔ 10^3 / 10^6 之上取整）。
 *
 * 小数分隔符固定 [Locale.US]：系统语言为德语/法语时默认 locale 会输出 "1,2万"，
 * 数字里的逗号与千分位混淆。
 *
 * 原住 `app/i18n`：消费方分布在 feature:ai（用量与产物统计）与 feature:me（诊断）两处，
 * 留在 app 则两个 feature 都够不着 ⇒ 沉 core.ui.text。
 */
fun formatCount(value: Long, chinese: Boolean): String = if (chinese) {
    when {
        value >= CN_HUNDRED_MILLION.toLong() -> String.format(Locale.US, "%.1f亿", value / CN_HUNDRED_MILLION)
        value >= CN_TEN_THOUSAND.toLong() -> String.format(Locale.US, "%.1f万", value / CN_TEN_THOUSAND)
        else -> value.toString()
    }
} else {
    when {
        value >= EN_MILLION.toLong() -> String.format(Locale.US, "%.1fM", value / EN_MILLION)
        value >= EN_THOUSAND.toLong() -> String.format(Locale.US, "%.1fK", value / EN_THOUSAND)
        else -> value.toString()
    }
}

/** 当前界面是否为中文；与 LocaleManager / attachBaseContext 覆盖后的 configuration 同源。 */
@Composable
@ReadOnlyComposable
fun isChineseUi(): Boolean = LocalConfiguration.current.locales[0].language == "zh"

/** 组合语境下的计数缩写，按当前界面语言取值。 */
@Composable
@ReadOnlyComposable
fun formatCount(value: Long): String = formatCount(value, isChineseUi())
