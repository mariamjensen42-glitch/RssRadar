package com.cycling.rssradar.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.ui.theme.radarColors

/**
 * 通用档位选择弹层（原 RssHubSettingsScreen 内的私有组件抽出，供设置页与信息流页共用）。
 * 归档保留期、同步间隔、标记已读条件等"一组互斥选项 + 当前选中"的场景都用它。
 * 外壳用官方 [ModalBottomSheet]，选项行用官方 [ListItem] + [RadioButton]，
 * 不再手写选项行与自绘勾选标记。整行可点（[ListItem] 自身不可点，故显式 clickable）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OptionPickerSheet(
    title: String,
    options: List<T>,
    /** 当前选中项；null = 没有"当前档位"（一次性动作，如标记已读的条件选择）。 */
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    /** 选项下方的说明（如"1 天前 = 早于该时间的未读文章"），可空。 */
    subtitle: ((T) -> String?)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = radarColors().surface1) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = title,
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            options.forEach { option ->
                val isSelected = option == selected
                ListItem(
                    headlineContent = {
                        Text(
                            text = label(option),
                            color = if (isSelected) radarColors().accent else radarColors().textPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = subtitle?.invoke(option)?.let { note ->
                        {
                            Text(
                                text = note,
                                color = radarColors().textTertiary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    trailingContent = {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = radarColors().surface1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(option)
                            onDismiss()
                        },
                )
            }
        }
    }
}
