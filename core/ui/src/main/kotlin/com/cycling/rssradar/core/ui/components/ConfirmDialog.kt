package com.cycling.rssradar.core.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cycling.rssradar.core.ui.theme.Danger
import com.cycling.rssradar.core.ui.theme.radarColors

/**
 * 全应用统一确认对话框：卡片形态，确认按钮默认 accent 强调；
 * [destructive] = true 时确认按钮用 Danger 色（删除 / 清空等破坏性操作）。
 * 破坏性操作的确认文案必须写清后果（UI 铁律：不许静默误删）。
 */
@Composable
fun ConfirmDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    dismissText: String? = null,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = text?.let { body -> { Text(body) } },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
            ) {
                Text(
                    text = confirmText,
                    color = if (destructive) Danger else radarColors().accent,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = dismissText?.let { label ->
            {
                TextButton(onClick = onDismiss) {
                    Text(label, color = radarColors().textSecondary)
                }
            }
        },
    )
}
