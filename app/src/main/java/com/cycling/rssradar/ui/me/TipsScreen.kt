package com.cycling.rssradar.ui.me

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.cycling.rssradar.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lightbulb
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Wrench
import com.cycling.rssradar.core.ui.theme.radarColors

/**
 * 使用提示 / 疑难解答（ReadYou 差距表 #36）。
 *
 * **文案必须来自代码里真实存在的行为**——这页是给人排障用的，写一条不存在的
 * 入口或 wrong 的默认开关状态，比没有这页更糟。每条都对应一个能点到的地方。
 */
/** 条目只带资源 id；翻译在 UI 层按当前语言取（ADR-0017）。 */
private data class HelpItem(@StringRes val titleRes: Int, @StringRes val bodyRes: Int)

/** 先知道这几件事：不是故障，是设计如此。 */
private val TIPS = listOf(
    HelpItem(R.string.tip1_title, R.string.tip1_body),
    HelpItem(R.string.tip2_title, R.string.tip2_body),
    HelpItem(R.string.tip3_title, R.string.tip3_body),
    HelpItem(R.string.tip4_title, R.string.tip4_body),
    HelpItem(R.string.tip5_title, R.string.tip5_body),
)

/** 出问题先看这里：症状 → 原因 → 去哪处理。 */
private val TROUBLESHOOTING = listOf(
    HelpItem(R.string.ts1_title, R.string.ts1_body),
    HelpItem(R.string.ts2_title, R.string.ts2_body),
    HelpItem(R.string.ts3_title, R.string.ts3_body),
    HelpItem(R.string.ts4_title, R.string.ts4_body),
    HelpItem(R.string.ts5_title, R.string.ts5_body),
    HelpItem(R.string.ts6_title, R.string.ts6_body),
)

@Composable
fun TipsScreen(onBack: () -> Unit = {}) {
    SettingsSubPage(title = stringResource(R.string.tips_screen_title), onBack = onBack) {
        SectionHeader(stringResource(R.string.tips_section_basics))
        HelpCard(icon = { Icon(Lucide.Lightbulb, contentDescription = null, tint = radarColors().accent) }, items = TIPS)
        Spacer(Modifier.height(24.dp))
        SectionHeader(stringResource(R.string.tips_section_trouble))
        HelpCard(icon = { Icon(Lucide.Wrench, contentDescription = null, tint = radarColors().accent) }, items = TROUBLESHOOTING)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HelpCard(icon: @Composable () -> Unit, items: List<HelpItem>) {
    Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
        Column(Modifier.padding(14.dp)) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    Spacer(Modifier.height(14.dp))
                }
                Row(verticalAlignment = Alignment.Top) {
                    icon()
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(item.titleRes),
                            color = radarColors().textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(item.bodyRes),
                            color = radarColors().textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
