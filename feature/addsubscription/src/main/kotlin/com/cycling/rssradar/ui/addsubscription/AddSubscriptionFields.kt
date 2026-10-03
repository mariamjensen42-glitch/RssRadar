package com.cycling.rssradar.ui.addsubscription

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Link2
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Rss
import com.composables.icons.lucide.Search
import com.cycling.rssradar.core.data.service.DiscoveredFeed
import com.cycling.rssradar.core.ui.theme.radarColors

@Composable
internal fun UrlField(
    value: String,
    onChange: (String) -> Unit,
    isLoading: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                "https://rsshub.app/zhihu/daily",
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        singleLine = true,
        leadingIcon = {
            Icon(Lucide.Link2, contentDescription = null, tint = radarColors().textTertiary)
        },
        trailingIcon = {
            if (isLoading) {
                CircularProgressIndicator(
                    color = radarColors().accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = radarColors().surface2,
            unfocusedContainerColor = radarColors().surface2,
            focusedBorderColor = radarColors().accent,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = radarColors().textPrimary,
            unfocusedTextColor = radarColors().textPrimary,
            cursorColor = radarColors().accent,
        ),
    )
}

@Composable
internal fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text("搜索 3800 条路由，如 b站 / github / 日报", color = radarColors().textTertiary, style = MaterialTheme.typography.bodyMedium)
        },
        singleLine = true,
        leadingIcon = {
            Icon(Lucide.Search, contentDescription = null, tint = radarColors().textTertiary)
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = radarColors().surface2,
            unfocusedContainerColor = radarColors().surface2,
            focusedBorderColor = radarColors().accent,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = radarColors().textPrimary,
            unfocusedTextColor = radarColors().textPrimary,
            cursorColor = radarColors().accent,
        ),
    )
}

/** 自动发现（#5）的一条候选：标题 + 地址 + 真实文章数，点一下即采用。 */
@Composable
internal fun DiscoveredFeedRow(
    feed: DiscoveredFeed,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = radarColors().surface1,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Lucide.Rss,
                contentDescription = null,
                tint = radarColors().accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feed.title.ifBlank { feed.url },
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = feed.url,
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${feed.articleCount} 篇",
                color = radarColors().textSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
