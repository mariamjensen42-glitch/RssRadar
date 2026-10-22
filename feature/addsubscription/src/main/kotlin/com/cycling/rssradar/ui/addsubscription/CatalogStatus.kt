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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.cycling.rssradar.core.model.rsshub.CatalogSource

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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(enabled = !refreshing, onClick = onRefresh),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (refreshing) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                } else {
                    Icon(
                        imageVector = Lucide.RefreshCw,
                        contentDescription = stringResource(R.string.add_catalog_refresh_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResource(
                        if (refreshing) R.string.add_catalog_refreshing else R.string.add_catalog_refresh,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun catalogStatusText(routeCount: Int, generatedAtMillis: Long?, source: CatalogSource): String {
    if (routeCount == 0) return ""
    val count = stringResource(R.string.add_catalog_count, routeCount)
    val origin = stringResource(
        if (source == CatalogSource.UPDATED) R.string.add_catalog_updated else R.string.add_catalog_builtin,
    )
    val date = generatedAtMillis?.let { formatCatalogDate(it) }
    return listOfNotNull(count, origin, date).joinToString(" · ")
}

/** 目录时间只到日期：路由表不需要精确到时分，短一点更省地方。 */
private fun formatCatalogDate(millis: Long): String {
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(millis))
}
