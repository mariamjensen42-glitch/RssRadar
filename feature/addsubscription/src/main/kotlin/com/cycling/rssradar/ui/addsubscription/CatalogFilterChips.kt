package com.cycling.rssradar.ui.addsubscription

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.data.db.entity.FeedEntity
import com.cycling.rssradar.core.model.rsshub.RouteCategory
import com.cycling.rssradar.core.ui.theme.radarColors

@Composable

internal fun HotTag() {
    Box(
        modifier = Modifier
            .background(RssHubOrange.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.add_hot),
            color = RssHubOrange,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable

internal fun CategoryChips(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it }) { key ->
            FilterChipLight(
                label = RouteCategory.label(key),
                selected = key == selected,
                onClick = { onSelect(key) },
            )
        }
    }
}

/* --------------------------- 阶段二：填参数 --------------------------- */

@Composable

internal fun FilterChipLight(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) radarColors().accent else radarColors().surface2,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = if (selected) radarColors().onAccent else radarColors().textSecondary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
        )
    }
}

@Composable

internal fun GroupChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { name ->
            FilterChipLight(label = name, selected = name == selected, onClick = { onSelect(name) })
        }
    }
}

/**
 * 内容类型（ADR-0014）：决定这个源在列表里的浏览形态。
 *
 * [selected] 传 VM 的 `effectiveContentType`——用户没挑过时它等于地址预判的结果，
 * 于是这里天然显示「这个源会被当成什么」，而不是一个凭空默认的「文章」。
 */
@Composable

internal fun ContentTypeChips(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val options = listOf(
        FeedEntity.CONTENT_TYPE_ARTICLE to R.string.ctype_article,
        FeedEntity.CONTENT_TYPE_IMAGE to R.string.ctype_image,
        FeedEntity.CONTENT_TYPE_VIDEO to R.string.ctype_video,
        FeedEntity.CONTENT_TYPE_AUDIO to R.string.ctype_audio,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (type, labelRes) ->
            FilterChipLight(
                label = stringResource(labelRes),
                selected = type == selected,
                onClick = { onSelect(type) },
            )
        }
    }
}
