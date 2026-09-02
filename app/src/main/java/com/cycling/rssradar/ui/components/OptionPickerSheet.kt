package com.cycling.rssradar.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.Surface1
import com.cycling.rssradar.ui.theme.TextPrimary
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide

/**
 * 通用档位选择弹层（原 RssHubSettingsScreen 内的私有组件抽出，供设置页与信息流页共用）。
 * 归档保留期、同步间隔、标记已读条件等"一组互斥选项 + 当前选中"的场景都用它。
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(option)
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label(option),
                            color = if (option == selected) Accent else TextPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        subtitle?.invoke(option)?.let { note ->
                            Text(
                                text = note,
                                color = com.cycling.rssradar.ui.theme.TextTertiary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (option == selected) {
                        Icon(
                            imageVector = Lucide.Check,
                            contentDescription = "已选",
                            tint = Accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
