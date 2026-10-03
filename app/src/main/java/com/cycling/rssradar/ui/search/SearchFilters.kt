package com.cycling.rssradar.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.db.entity.FeedEntity
import com.cycling.rssradar.core.model.library.LibraryRange
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.i18n.labelRes
import com.cycling.rssradar.ui.settings.SegmentedChips

/**
 * 二次筛选条：时间范围 / 未读·收藏·稍后读 / 来源。
 *
 * 为什么必须有：FTS 只负责"哪几篇里有这个词"，而数万篇的库里这个词往往命中几百篇，
 * 用户真正要的是"最近一周我还没读的那几篇"。没有二次筛选，搜索结果的可用性就只有一半。
 */
@Composable
internal fun SearchFilterRow(
    state: SearchUiState,
    feeds: List<FeedEntity>,
    onIntent: (SearchIntent) -> Unit,
) {
    val rangeLabels = LibraryRange.entries.associateWith { stringResource(it.labelRes()) }
    val feedLabels = buildMap {
        put(null, stringResource(R.string.search_filter_all_feeds))
        feeds.forEach { feed -> put(feed.id, feed.title) }
    }
    val feedOptions = remember(feeds) { listOf<Long?>(null) + feeds.map { it.id } }
    // SegmentedChips / 自绘 chip 的文案都得先在组合作用域取好（label 是普通 lambda）
    val unreadLabel = stringResource(R.string.search_filter_unread)
    val starredLabel = stringResource(R.string.search_filter_starred)
    val bookmarkedLabel = stringResource(R.string.search_filter_bookmarked)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentedChips(
                options = LibraryRange.entries.toList(),
                selected = state.range,
                label = { range -> rangeLabels[range].orEmpty() },
                onSelect = { onIntent(SearchIntent.SetRange(it)) },
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterToggle(
                label = unreadLabel,
                active = state.filters.unreadOnly,
                onClick = { onIntent(SearchIntent.ToggleUnreadOnly) },
            )
            FilterToggle(
                label = starredLabel,
                active = state.filters.starredOnly,
                onClick = { onIntent(SearchIntent.ToggleStarredOnly) },
            )
            FilterToggle(
                label = bookmarkedLabel,
                active = state.filters.bookmarkedOnly,
                onClick = { onIntent(SearchIntent.ToggleBookmarkedOnly) },
            )
            if (!state.filters.isDefault) {
                TextButton(onClick = { onIntent(SearchIntent.ClearFilters) }) {
                    Text(
                        text = stringResource(R.string.search_filter_clear),
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentedChips(
                options = feedOptions,
                selected = state.filters.feedId,
                label = { id -> feedLabels[id].orEmpty() },
                onSelect = { onIntent(SearchIntent.SetFeedFilter(it)) },
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun FilterToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) radarColors().accent else radarColors().surface2,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = if (active) radarColors().onAccent else radarColors().textPrimary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
