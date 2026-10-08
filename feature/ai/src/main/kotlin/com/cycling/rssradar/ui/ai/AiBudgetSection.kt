package com.cycling.rssradar.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.cycling.rssradar.core.model.AiBudgetState
import com.cycling.rssradar.core.ui.text.formatCount

/**
 * 用量：只显示真实统计到的次数与字数，**不换算金额**。
 * DeepSeek 的单价会调整，硬编码一个系数就是给用户一个看起来精确实则过期的数字。
 */
@Composable
internal fun UsageCard(budget: AiBudgetState) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(14.dp), color = colors.surfaceContainerLowest) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Lucide.Sparkles,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.usage_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))

            val limitText = if (budget.dailyLimit <= 0) stringResource(R.string.unlimited) else budget.dailyLimit.toString()
            UsageRow(stringResource(R.string.calls_today), "${budget.usedToday} / $limitText")
            if (budget.dailyLimit > 0) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(colors.surfaceContainerHighest, RoundedCornerShape(3.dp)),
                ) {
                    val ratio = (budget.usedToday.toFloat() / budget.dailyLimit).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth(ratio)
                            .height(6.dp)
                            .background(colors.primary, RoundedCornerShape(3.dp)),
                    )
                }
                // 空进度条状态不明（UI 审计 M4）：0 时直接说明
                if (budget.usedToday == 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.not_used_today), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            UsageRow(stringResource(R.string.io_today), stringResource(R.string.io_today_chars, formatCount(budget.inputCharsToday), formatCount(budget.outputCharsToday)))
            UsageRow(stringResource(R.string.calls_total), stringResource(R.string.calls_suffix, formatCount(budget.totalCalls)))
            UsageRow(stringResource(R.string.failed_total), stringResource(R.string.calls_suffix, formatCount(budget.totalFailed)))
        }
    }
}

@Composable
private fun UsageRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
    }
}

// ── 预算设置 ────────────────────────────────────────────────────────────────

@Composable
internal fun BudgetSection(
    budget: AiBudgetState,
    onIntent: (AiFeaturesIntent) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(14.dp), color = colors.surfaceContainerLowest) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.budget_section),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.budget_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            val unlimitedLabel = stringResource(R.string.unlimited)
            ChipChoiceRow(
                label = stringResource(R.string.daily_limit),
                options = listOf(0, 50, 100, 200, 500),
                selected = budget.dailyLimit,
                labelOf = { if (it == 0) unlimitedLabel else it.toString() },
                onSelect = { onIntent(AiFeaturesIntent.SetDailyLimit(it)) },
            )
            Spacer(Modifier.height(10.dp))
            ChipChoiceRow(
                label = stringResource(R.string.concurrency),
                options = listOf(1, 2, 3, 4),
                selected = budget.concurrentLimit,
                labelOf = { it.toString() },
                onSelect = { onIntent(AiFeaturesIntent.SetConcurrent(it)) },
            )
            Spacer(Modifier.height(10.dp))
            ChipChoiceRow(
                label = stringResource(R.string.min_interval),
                options = listOf(0L, 500L, 1_200L, 3_000L),
                selected = budget.minIntervalMs,
                labelOf = { if (it == 0L) unlimitedLabel else "${it}ms" },
                onSelect = { onIntent(AiFeaturesIntent.SetMinInterval(it)) },
            )
        }
    }
}

@Composable
private fun <T> ChipChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) colors.primary else colors.surfaceContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onSelect(option) },
                ) {
                    Text(
                        text = labelOf(option),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── 任务队列 ────────────────────────────────────────────────────────────────
