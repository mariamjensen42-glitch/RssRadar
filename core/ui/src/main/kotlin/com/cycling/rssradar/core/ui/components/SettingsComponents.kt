package com.cycling.rssradar.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.cycling.rssradar.core.ui.R
import com.cycling.rssradar.core.ui.theme.radarColors

/**
 * 设置页通用行组件（二级页骨架 / 分组标题 / 开关行 / 跳转行 / 分段选择器）。
 *
 * 为什么在 core:ui 而不是某个 feature 内：这 6 个组件被 5 个包共用
 * （me · settings · library · player · search），留在 settings 里会让
 * me 与 settings 互相依赖成环——而 Gradle 模块间不允许循环依赖。
 *
 * 文案取自 core:ui 自己的 res（back / select / enter）：core 模块拿不到 app 的 R
 * （`nonTransitiveRClass` 默认 true，每个模块的 R 只含自身资源）。
 */

/** 二级页骨架：返回顶栏 + 滚动内容，与 InterestProfileScreen 同款形态。 */
@Composable
fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(radarColors().bgRoot)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.back), tint = radarColors().textPrimary)
            }
            Text(
                text = title,
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            content()
        }
    }
}

/** 分组小标题 + 说明文案（原长页同款）。 */
@Composable
fun SectionHeader(title: String, description: String? = null) {
    Text(
        text = title,
        color = radarColors().textSecondary,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    if (description != null) {
        Text(
            text = description,
            color = radarColors().textTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
    } else {
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * 开关行。[subtitle] 为说明文案；[enabled] 为 false 时整行置灰——
 * UI 铁律：禁用必须配解释文案，所以两者成对出现，别只传 enabled。
 */
@Composable
fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (enabled) radarColors().textPrimary else radarColors().textTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = radarColors().onAccent,
                checkedTrackColor = radarColors().accent,
            ),
        )
    }
}

/** 「标签 + 当前值 + 箭头」的跳转行（归档保留期同款形态，链接/分享偏好复用）。 */
@Composable
fun OptionRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = radarColors().textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = radarColors().accent,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Lucide.ChevronRight,
            contentDescription = stringResource(R.string.select),
            tint = radarColors().textTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 跳转行（无当前值），如「全文抓取诊断」「崩溃日志」。 */
@Composable
fun NavigateRow(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = radarColors().surface1,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Lucide.ChevronRight,
                contentDescription = stringResource(R.string.enter),
                tint = radarColors().textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 通用分段选择器：胶囊 chip 一排，选中态 accent 填充。设置页与列表页多处共用，保证样式一致。 */
@Composable
fun <T> SegmentedChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) radarColors().accent else radarColors().surface2,
                modifier = Modifier.clickable { onSelect(option) },
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) radarColors().onAccent else radarColors().textPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
