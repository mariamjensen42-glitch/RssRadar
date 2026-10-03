package com.cycling.rssradar.ui.addsubscription

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.cycling.rssradar.core.model.rsshub.CatalogSource
import com.cycling.rssradar.core.ui.theme.radarColors

/** 目录状态：条数 + 数据时间 + 更新入口。让用户知道目录是活的可更新，而不是死的 14 条。 */
@Composable

internal fun CatalogStatusBar(
    routeCount: Int,
    generatedAtMillis: Long?,
    source: CatalogSource,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = catalogStatusText(routeCount, generatedAtMillis, source),
            color = radarColors().textTertiary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = radarColors().surface2,
            modifier = Modifier.clickable(enabled = !refreshing, onClick = onRefresh),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (refreshing) {
                    CircularProgressIndicator(color = radarColors().accent, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                } else {
                    Icon(
                        imageVector = Lucide.RefreshCw,
                        contentDescription = "更新路由目录",
                        tint = radarColors().textSecondary,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (refreshing) "更新中" else "更新目录",
                    color = radarColors().textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private fun catalogStatusText(routeCount: Int, generatedAtMillis: Long?, source: CatalogSource): String {
    if (routeCount == 0) return ""
    val count = "$routeCount 条路由"
    val origin = if (source == CatalogSource.UPDATED) "已更新" else "内置"
    val date = generatedAtMillis?.let { formatCatalogDate(it) }
    return listOfNotNull(count, origin, date).joinToString(" · ")
}

/** 目录时间只到日期：路由表不需要精确到时分，短一点更省地方。 */
private fun formatCatalogDate(millis: Long): String {
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(millis))
}
