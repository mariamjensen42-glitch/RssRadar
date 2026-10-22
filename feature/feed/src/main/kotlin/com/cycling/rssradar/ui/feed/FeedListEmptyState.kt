package com.cycling.rssradar.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.FileUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.cycling.rssradar.core.ui.components.tabBarBottomClearance

@Composable

internal fun EmptyState(
    selectedTab: FeedTab,
    /** 当前分区（issue #75）：空分区空态文案来源。 */
    selectedContentType: ContentTypeFilter,
    /** 空分区空态：选中分区且库里没有任何该类型订阅源（区别于「有源但没文章」）。 */
    partitionEmpty: Boolean,
    onAddFeed: () -> Unit = {},
    onOpenSubscriptions: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 分区空态（issue #75）优先：有源没文章走原 tab 空态，无源才走分区引导——
    // 如实区分两种空。分区行仍在上方，用户随时可切到别的分区，不阻塞。
    val (title, hint) = if (partitionEmpty) {
        val typeName = stringResource(selectedContentType.labelRes())
        stringResource(R.string.ctype_empty_title, typeName) to
            stringResource(R.string.ctype_empty_desc, typeName)
    } else {
        when (selectedTab) {
            FeedTab.All ->
                stringResource(R.string.feed_empty_no_feeds) to
                    stringResource(R.string.feed_empty_no_feeds_desc)
            FeedTab.Unread ->
                stringResource(R.string.feed_empty_unread) to
                    stringResource(R.string.feed_empty_unread_desc)
            FeedTab.Starred ->
                stringResource(R.string.feed_empty_starred) to
                    stringResource(R.string.feed_empty_starred_desc)
            FeedTab.Bookmarked ->
                stringResource(R.string.feed_empty_readlater) to
                    stringResource(R.string.feed_empty_readlater_desc)
            // 推荐流空态：候选池 = 未读 + 14 天窗，读完就没了——如实说，不编内容
            FeedTab.Recommended ->
                stringResource(R.string.feed_empty_recommended) to
                    stringResource(R.string.feed_empty_recommended_desc)
        }
    }
    // verticalScroll 让空态页也能响应下拉刷新手势
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(
                start = 32.dp,
                end = 32.dp,
                top = 32.dp,
                // 底部让位导航栏，空态提示文字不被压住
                bottom = 32.dp + tabBarBottomClearance(),
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            hint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        // 「全部」为空 = 一篇文章都没有，必然是还没订阅。给双入口：
        // 添加订阅源（直达添加抽屉） / 导入 OPML（老用户迁移最常见的第一个动作），
        // 让用户自己去找入口，是新用户流失最快的一步。
        if (selectedTab == FeedTab.All) {
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onAddFeed) {
                Icon(
                    Lucide.Plus,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.feed_empty_add))
            }
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = onOpenSubscriptions) {
                Icon(
                    Lucide.FileUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.feed_empty_import_opml))
            }
        }
    }
}

/** 推荐流首屏打分中的占位（候选池加载 + 打分在 IO 线程，通常一闪而过）。 */
@Composable

internal fun RecommendationLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(bottom = tabBarBottomClearance()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.feed_ranking), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}
