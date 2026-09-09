package com.cycling.rssradar.ui.me

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.res.stringResource
import com.cycling.rssradar.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.ui.components.openUrl

/**
 * 检查更新（ReadYou 差距表第 35 项）。
 *
 * 三态都要说人话：已是最新 / 有新版本（给 Release 页链接）/ 失败（给原因）。
 * 「检查失败」这种无信息文案等于没做——用户不知道是该重试还是该换网络。
 */
@Composable
internal fun UpdateCheckRow(modifier: Modifier = Modifier) {
    val viewModel: UpdateViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    // AGP 8+ 默认不生成 BuildConfig（且不打算为此动构建脚本），版本走 PackageManager。
    val version = remember(context) { context.appVersionName() }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.update_current_version),
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = version ?: stringResource(R.string.unknown),
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                onClick = { viewModel.check(version.orEmpty()) },
                enabled = state !is UpdateState.Checking,
            ) {
                Text(
                    text = if (state is UpdateState.Checking) stringResource(R.string.checking) else stringResource(R.string.check_updates),
                    color = radarColors().accent,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        when (val current = state) {
            UpdateState.Idle, UpdateState.Checking -> Unit
            UpdateState.UpToDate -> ResultLine(stringResource(R.string.up_to_date))
            is UpdateState.Failed -> ResultLine(current.message)
            is UpdateState.Available -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.new_version_found, current.version),
                    color = radarColors().accent,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { context.openUrl(current.url) }) {
                    Text(
                        text = stringResource(R.string.go_download),
                        color = radarColors().accent,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultLine(text: String) {
    Text(
        text = text,
        color = radarColors().textTertiary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

/** 已安装包版本名；拿不到返回 null（不猜、不显示占位版本）。 */
private fun Context.appVersionName(): String? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionName
    }
} catch (_: Exception) {
    null
}
